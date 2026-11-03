package com.Tangle.timetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.Tangle.timetable.next_appwidget.NextCourseAppWidget
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.CrashLogger
import com.Tangle.timetable.utils.getPrefer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 小部件更新调度中心：
 * - 每天 00:05 重算当天所有课程的 开始/结束/课前提醒 精确闹钟（自我重排）；
 * - 每节课开始时间点精确刷新（未开始→进行中）；
 * - 每节课结束时间点精确刷新（进行中→已结束/消失）；
 * - 课前 X 分钟精确闹钟发通知；
 * - WorkManager 每 30 分钟兜底刷新，防精确闹钟被系统杀掉；
 * - Android 12+ 无精确闹钟权限时自动降级为可延迟闹钟（setAndAllowWhileIdle）。
 */
object WidgetScheduler {

    const val ACTION_REFRESH = "com.Tangle.timetable.action.WIDGET_REFRESH"
    const val ACTION_RECOMPUTE = "com.Tangle.timetable.action.WIDGET_RECOMPUTE"
    const val ACTION_COURSE_START = "com.Tangle.timetable.action.COURSE_START"
    const val ACTION_COURSE_END = "com.Tangle.timetable.action.COURSE_END"
    const val ACTION_REMIND = "com.Tangle.timetable.action.COURSE_REMIND"
    /** 20:00 课程预告放行点：到点刷新一次，让卡片从「今天」切到「预告」 */
    const val ACTION_PREVIEW_START = "com.Tangle.timetable.action.WIDGET_PREVIEW_START"
    /** 下一节课卡倒计时刷新：由可延迟闹钟（AllowIdle）驱动，不占精确闹钟配额 */
    const val ACTION_COUNTDOWN = "com.Tangle.timetable.action.WIDGET_COUNTDOWN"

    private const val RC_RECOMPUTE = 1000
    private const val RC_BASE_START = 2000
    private const val RC_BASE_END = 4000
    private const val RC_BASE_REMIND = 6000
    private const val RC_PREVIEW_START = 8000
    private const val RC_COUNTDOWN_BASE = 9000
    private const val MAX_COUNTDOWN_TICKS = 12

    /** 单日闹钟槽位上限：一天最多 12 节课足够覆盖，取消旧闹钟时按这个范围扫 */
    private const val MAX_COURSE_ALARMS = 32

    private const val WORK_NAME = "widget_fallback_refresh"

    /** 反射结果缓存：canScheduleExactAlarms 每次调度会被调用近百次，缓存避免重复反射与重复落盘 */
    @Volatile
    private var canExactCached: Boolean? = null

    /**
     * W8-02：让 [canScheduleExact] 的进程级缓存失效。
     *
     * 这个缓存是为省反射开销加的（每次整体调度会被调用近百次），但原来是**只写不清**：
     * 用户第一次进引导页时未授权 → 缓存 `false`；随后按引导去系统「精确闹钟」页开启、返回，
     * `onResume → rebuild()` 读到的还是 `false` → 状态胶囊永远显示「未开启」、`allSet` 恒 false、
     * 底部按钮永远停在「稍后再说」、`scheduleAll` 不被调用 ——
     * **精确闹钟在本次进程内永不启用**（连 `WidgetGuideHelper.shouldShowGuide` 也跟着恒真）。
     *
     * 所以在"用户可能刚改过系统权限"的时机必须清掉它：引导页 `onResume`，以及每次整体调度之前。
     */
    fun invalidateExactCache() {
        canExactCached = null
    }

    /**
     * W8-01：`scheduleAll` 的后台入口。
     *
     * `scheduleAll` 内含近百次 `cancelAlarm` + `PendingIntent` 注册 + 反射 + **同步 Room 查询**
     * （`AppDataBase` 全局开了 `allowMainThreadQueries`，主线程不抛异常而是**直接做 SQLite IO**）。
     * `App.onCreate` 已刻意把它挪到 `appScope(IO)`，引导页「完成」按钮却留在主线程。
     *
     * 这里的作用域是**进程级**的，刻意不绑任何 Activity —— 不能绑 `lifecycleScope`，
     * 因为调用点接着就 `finish()`，绑上会被一起取消，闹钟排到一半就断了。
     * `SupervisorJob` 保证单次调度失败不污染后续调度。
     */
    private val schedulerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun scheduleAllAsync(context: Context) {
        val app = context.applicationContext
        schedulerScope.launch {
            try {
                scheduleAll(app)
            } catch (t: Throwable) {
                CrashLogger.logCaught("widget_schedule_async", t)
            }
        }
    }

    /** Android 12+ 是否有精确闹钟权限（低版本恒为 true）。用反射兼容 compileSdk 29。 */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true   // Build.VERSION_CODES.S = 31
        canExactCached?.let { return it }
        return try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val m = AlarmManager::class.java.getMethod("canScheduleExactAlarms")
            ((m.invoke(am) as? Boolean) ?: true).also { canExactCached = it }
        } catch (e: ReflectiveOperationException) {
            // 反射失败按无权限降级：setExactAndAllowWhileIdle 在无权限时抛 SecurityException，
            // 并不会"系统自行降级"；缓存降级结果，其余异常类型细分后不再统一静默吞掉
            CrashLogger.logCaught("widget_schedule", e)
            false.also { canExactCached = it }
        } catch (e: SecurityException) {
            false.also { canExactCached = it }
        }
    }

    /** 注册全部调度（开机/时间变化/进 App/设置改动后调用） */
    @Synchronized
    fun scheduleAll(context: Context) {
        // W3-12：本方法是 cancel-all-then-set 的非原子序列，而它的调用点很分散
        // （App.onCreate / 各接收器 / 设置页 / 引导页，彼此可能并发）。
        // 两个线程交错时会互相取消对方的闹钟（`PendingIntent` 的身份只看
        // requestCode + Intent(action)，不看 extras，所以 cancel 会命中新建实例）。
        // 加类级锁把整段串行化 —— 这是台账明确推荐的做法：
        // **只做串行化，不动 cancel 机制**（改成"显式 id 列表"要额外维护状态，成本高收益低）。
        // 顺带把 canExactCached 的读写也纳入同一把锁。
        //
        // W8-02：整体调度前让精确闹钟缓存失效一次 ——
        // 同一次 scheduleAll 内部的近百次查询仍走缓存（只多一次反射），
        // 但保证不会拿着**上一次调度**的过期结果做整轮决策
        // （例如用户曾在后台去系统页开启了权限，随后一次开机广播触发重排）。
        invalidateExactCache()
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleDailyRecompute(context, am)
        schedulePreviewStart(context, am)
        rescheduleCourseAlarms(context, am)
        enqueueFallbackWork(context)
    }

    /**
     * 仅重排「课程闹钟 + 倒计时 tick」：课程开始/结束、WorkManager 兜底跑时调用，
     * 省掉 00:05 重算、20:00 预告、WorkManager 本身这三样时间轴没变的东西。
     */
    @Synchronized
    fun rescheduleCourseAlarms(context: Context, am: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager) {
        scheduleCourseAlarms(context, am)
        scheduleCountdownTicks(context, am)
    }

    /**
     * 当天 20:00 的预告放行闹钟：
     * 20 点前今日卡停在「今天」，到点必须主动刷一次，卡片才会切到「第二天预告」。
     * 不注册的话只能等每 30 分钟的兜底 Worker（最坏晚约半小时才翻）。
     * 已过 20 点则排到明天 20:00（每天 20:00 都会由 ACTION_RECOMPUTE 或本闹钟自身重排）。
     *
     * v167 改：① 加闸门 —— 明天起预览窗口内没课（周末/长假）时，刷了内容也不变，直接跳过；
     *          ② 去精确闹钟 —— 晚几十分钟无感，凌晨精确闹钟会打破 Doze。
     */
    private fun schedulePreviewStart(context: Context, am: AlarmManager) {
        if (!WidgetRefreshPolicy.hasCourseInPreviewWindow(context)) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, WidgetData.PREVIEW_START_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // 今天 20:00 已过 → 排到明天 20:00
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        setAlarmAllowIdle(context, am, RC_PREVIEW_START, cal.timeInMillis, ACTION_PREVIEW_START)
    }

    /**
     * 每天 00:05 重算（跨天后重建当天闹钟）。
     *
     * v167 改：去掉精确闹钟，无条件走可延迟 `setAlarmAllowIdle`。
     * 凌晨无人看卡片，晚几十分钟无感；而精确闹钟会强制打破 Doze，是夜间耗电的直接来源。
     */
    private fun scheduleDailyRecompute(context: Context, am: AlarmManager) {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        setAlarmAllowIdle(context, am, RC_RECOMPUTE, cal.timeInMillis, ACTION_RECOMPUTE)
    }

    /** 为今天所有课程注册 开始/结束/课前提醒 闹钟 */
    private fun scheduleCourseAlarms(context: Context, am: AlarmManager) {
        // 先撤掉旧数据（昨天/删课/缩课/关提醒）留下的全部课程闹钟，再按今天的课重排。
        // requestCode 按序复用而从不 cancel，会让关掉提醒/删课后的旧闹钟继续弹通知。
        for (rc in 0 until MAX_COURSE_ALARMS) {
            cancelAlarm(context, am, RC_BASE_START + rc, ACTION_COURSE_START)
            cancelAlarm(context, am, RC_BASE_END + rc, ACTION_COURSE_END)
            cancelAlarm(context, am, RC_BASE_REMIND + rc, ACTION_REMIND)
        }
        val courses = WidgetData.getDayCourses(context, false)
        val now = System.currentTimeMillis()
        val remindBefore = context.getPrefer().getInt(Const.KEY_REMINDER_TIME, 10)
        val remindEnabled = context.getPrefer().getBoolean(Const.KEY_COURSE_REMIND, false)

        courses.forEachIndexed { index, c ->
            if (index >= MAX_COURSE_ALARMS) return@forEachIndexed
            // 开始点刷新
            if (c.startMillis > now) {
                setAlarm(context, am, RC_BASE_START + index, c.startMillis,
                        ACTION_COURSE_START, c.courseName, c.room, c.startText, index)
            }
            // 结束点刷新
            if (c.endMillis > now) {
                setAlarm(context, am, RC_BASE_END + index, c.endMillis,
                        ACTION_COURSE_END, c.courseName, c.room, c.endText, index)
            }
            // 课前提醒
            if (remindEnabled) {
                val remindAt = c.startMillis - remindBefore * 60000L
                if (remindAt > now) {
                    setAlarm(context, am, RC_BASE_REMIND + index, remindAt,
                            ACTION_REMIND, c.courseName, c.room, "${c.startText} 上课", index)
                }
            }
        }
    }

    /** 取消一个已注册的闹钟（requestCode + action 相同即视为同一条）；没有就不做 */
    private fun cancelAlarm(context: Context, am: AlarmManager, requestCode: Int, action: String) {
        val intent = Intent(context, WidgetUpdateReceiver::class.java).setAction(action)
        val pi = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pi != null) am.cancel(pi)
    }

    private fun setAlarm(context: Context, am: AlarmManager, requestCode: Int,
                         triggerAt: Long, action: String,
                         name: String, room: String, time: String, index: Int) {
        val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            this.action = action
            putExtra("courseName", name)
            putExtra("room", room)
            putExtra("time", time)
            putExtra("index", index)
        }
        val pi = PendingIntent.getBroadcast(
                context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                // 无精确闹钟权限时降级（仍能在 Doze 下被唤醒，只是可能有几分钟延迟）
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            // 仅对无精确闹钟权限降级为可延迟闹钟，单条失败不中断整批
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } catch (e2: Exception) {
                CrashLogger.logCaught("widget_schedule", e2)
            }
        } catch (e: RuntimeException) {
            // 其余异常（个别 ROM 限制等）落盘记录，不再静默吞掉；不中断整批注册
            CrashLogger.logCaught("widget_schedule", e)
        }
    }

    /**
     * WorkManager 兜底周期任务。
     *
     * v167 改：30 → 180 分钟，KEEP → UPDATE。
     * KEEP 语义是「已存在同名周期任务就原样保留」，改了周期参数对老用户不生效（原注释已点出此坑）。
     * UPDATE 让升级时新的 180 分钟周期生效；Worker 内部还有触发闸门（WidgetRefreshPolicy.shouldRefresh），
     * 无课日 / 课后 / 24h 内已刷过时会秒退，180 分钟兜底加上闸门后实际刷新频率远低于标称值。
     */
    private fun enqueueFallbackWork(context: Context) {
        val request = androidx.work.PeriodicWorkRequest.Builder(
                WidgetWorker::class.java, 180, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * 「下一节课」卡倒计时刷新：只在开课前 120 分钟内、按 10 分钟粒度排 tick。
     * 用可延迟闹钟（不精确、可被 Doze 合并），不占用精确闹钟配额。
     * 窗口外（周末、课后、长假）一条都不排 —— 这是本卡从 30 分钟轮询降到 0 的关键。
     *
     * ⚠️ 关键补充：若下一节课还在 120 分钟之外（典型：00:05 重算时，第一节课在 8 小时之后），
     * 不能什么都不排 —— 否则永远进不了窗口。此时只排「窗口开启」一条闹钟，到点后再由
     * rescheduleCourseAlarms 排真正的 tick 链。
     */
    private fun scheduleCountdownTicks(context: Context, am: AlarmManager) {
        for (i in 0..MAX_COUNTDOWN_TICKS) {
            cancelAlarm(context, am, RC_COUNTDOWN_BASE + i, ACTION_COUNTDOWN)
        }
        // 没放「下一节课」部件就不排
        val hasNextWidget = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, NextCourseAppWidget::class.java)).isNotEmpty()
        if (!hasNextWidget) return

        val next = WidgetData.getNextCourse(context) ?: return
        val now = System.currentTimeMillis()
        if (next.startMillis <= now) return

        val windowStart = next.startMillis -
                WidgetRefreshPolicy.COUNTDOWN_WINDOW_MIN * 60_000L
        if (windowStart > now) {
            // 还没进窗口：只排一条「窗口开启」信号（windowOpen=true），到点后再展开 tick 链
            setAlarmAllowIdle(context, am, RC_COUNTDOWN_BASE, windowStart,
                    ACTION_COUNTDOWN, windowOpen = true)
            return
        }

        // 已在窗口内：按 10 分钟粒度铺到开课时刻
        var triggerAt = now + WidgetRefreshPolicy.COUNTDOWN_STEP_MIN * 60_000L
        var i = 0
        while (triggerAt < next.startMillis && i < MAX_COUNTDOWN_TICKS) {
            setAlarmAllowIdle(context, am, RC_COUNTDOWN_BASE + i, triggerAt, ACTION_COUNTDOWN)
            triggerAt += WidgetRefreshPolicy.COUNTDOWN_STEP_MIN * 60_000L
            i++
        }
    }

    /**
     * 可延迟闹钟（允许 Doze 合并、不精确）。API 21/22 没有 setAndAllowWhileIdle，降级为 set()。
     * 用于「晚几分钟无感」的场景：跨天重算、20:00 预告、下一节课倒计时。
     *
     * @param windowOpen 仅 ACTION_COUNTDOWN 使用：true = 这条是「倒计时窗口开启」信号，
     *                   接收方需要把 tick 链展开。
     */
    private fun setAlarmAllowIdle(context: Context, am: AlarmManager,
                                  requestCode: Int, triggerAt: Long, action: String,
                                  windowOpen: Boolean = false) {
        val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            this.action = action
            putExtra("windowOpen", windowOpen)
        }
        val pi = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            runCatching { am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
                    .onFailure { CrashLogger.logCaught("widget_schedule", it) }
        } catch (e: RuntimeException) {
            CrashLogger.logCaught("widget_schedule", e)
        }
    }
}

