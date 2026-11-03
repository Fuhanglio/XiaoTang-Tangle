package com.Tangle.timetable.widget

import android.os.SystemClock
import android.util.Log

import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.Tangle.timetable.AppDatabase
import com.Tangle.timetable.R
import com.Tangle.timetable.SplashActivity
import com.Tangle.timetable.next_appwidget.NextCourseAppWidget
import com.Tangle.timetable.schedule_appwidget.ScheduleAppWidget
import com.Tangle.timetable.today_appwidget.TodayCourseAppWidget
import com.Tangle.timetable.utils.AppWidgetUtils
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.CrashLogger
import com.Tangle.timetable.utils.getPrefer
import com.Tangle.timetable.utils.goAsync

/**
 * 小部件统一更新接收器：
 * 闹钟（课程开始/结束/课前提醒）、开机、时间/时区/日期变化、亮屏、手动刷新
 * 全部汇集到这里 → 刷新所有小部件 + 必要时重建当天闹钟 + 发课前提醒通知。
 */
class WidgetUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            // ===== 新增：下一节课卡倒计时刷新 =====
            WidgetScheduler.ACTION_COUNTDOWN -> goAsync {
                // 倒计时只影响 next 卡，刷它一个就够
                val awm = AppWidgetManager.getInstance(context)
                awm.getAppWidgetIds(ComponentName(context, NextCourseAppWidget::class.java))
                        .forEach { id ->
                            runCatching { AppWidgetUtils.refreshNextWidget(context, awm, id, force = true) }
                        }
                // 若是「窗口开启」信号，还要把 tick 链铺开（否则永远只有一条闹钟）
                if (intent.getBooleanExtra("windowOpen", false)) {
                    WidgetScheduler.rescheduleCourseAlarms(context)
                }
            }
            // 课前提醒通知：只发通知 + 刷卡片，不再全量 scheduleAll。
            // 提醒本身只是一条通知，10 分钟后有课程开始闹钟会刷；此处重排纯浪费。
            WidgetScheduler.ACTION_REMIND -> goAsync {
                showReminderNotification(context, intent)
                refreshAllWidgets(context)
            }
            // ===== 课程边界：独立分支，不带节流、只重排课程闹钟 =====
            // 与 ACTION_RECOMPUTE 等事件分离 —— 课程开始/结束是时间点严格事件，
            // 不能被 60 秒节流吞掉；且只改变「当前状态」，时间轴本身没变，
            // 所以只 rescheduleCourseAlarms 而不是全量 scheduleAll。
            WidgetScheduler.ACTION_COURSE_START,
            WidgetScheduler.ACTION_COURSE_END -> goAsync {
                refreshAllWidgets(context)
                WidgetScheduler.rescheduleCourseAlarms(context)
            }
            // ===== 重算 / 开机 / 时间变化 / 20:00 预告：保留原 60 秒节流 + 全量 scheduleAll =====
            // W3-13③：refresh + reschedule 是"幂等但昂贵"的操作（近百次 cancelAlarm +
            // PendingIntent 注册 + 同步 Room 查询），而上面这一组 action 可能在同一秒内
            // 连着来好几个（例如开机时 BOOT_COMPLETED 与 DATE_CHANGED 常挨在一起）。
            // 这里做 60 秒节流。用 `in 0 until X` 而不是 `< X`：万一时钟基准出现负差值，
            // 应放行而不是吞掉（宁可多跑一次，不能漏掉开机这种必须执行的事件）。
            // 手动刷新（ACTION_REFRESH）与课前提醒（ACTION_REMIND）**不受此限制**。
            WidgetScheduler.ACTION_RECOMPUTE,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            WidgetScheduler.ACTION_PREVIEW_START -> goAsync {
                // v167+：AtomicLong + CAS 代替 @Volatile，保证同秒并发 goAsync 只有一条真正执行。
                // 原来的 @Volatile 没锁，两条几乎同时到达时都能读到「now - prev ≈ 0 < 60s」
                // 通过后一起执行（同秒最多 2 次 scheduleAll）。
                val now = SystemClock.elapsedRealtime()
                val prev = lastRecomputeAt.get()
                if (now - prev in 0 until RECOMPUTE_MIN_INTERVAL_MS) return@goAsync
                // CAS 抢占窗口：prev 已变说明别人刚过了节流，这条放弃
                if (!lastRecomputeAt.compareAndSet(prev, now)) return@goAsync
                refreshAllWidgets(context)
                WidgetScheduler.scheduleAll(context)
            }
            // 手动刷新：force=true，跳过指纹闸门，即使内容没变也重绘（符合用户预期）
            WidgetScheduler.ACTION_REFRESH -> goAsync {
                refreshAllWidgets(context, force = true)
            }
            // 点击小部件：先刷新数据，再打开 App 主界面
            "com.Tangle.timetable.action.WIDGET_OPEN_APP" -> {
                goAsync {
                    refreshAllWidgets(context)
                }
                val open = Intent(context, SplashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                try {
                    context.startActivity(open)
                } catch (e: Exception) {
                    Log.e(TAG, "Widget error", e)
                }
            }
        }
    }

    private fun showReminderNotification(context: Context, intent: Intent) {
        // 提醒总开关：关了就不再弹（旧闹钟残留时也不能弹出来）
        if (!context.getPrefer().getBoolean(Const.KEY_COURSE_REMIND, false)) return
        try {
            val name = intent.getStringExtra("courseName") ?: "课程"
            val room = intent.getStringExtra("room") ?: ""
            val time = intent.getStringExtra("time") ?: ""
            val index = intent.getIntExtra("index", 0)

            val openIntent = Intent(context, SplashActivity::class.java)
            val openPi = PendingIntent.getActivity(context, 9000 + index, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, "schedule_reminder")
                    .setContentTitle("$time $name 即将上课")
                    .setContentText(if (room.isNotEmpty()) "地点：$room" else "记得提前做好准备~")
                    .setStyle(NotificationCompat.BigTextStyle()
                            .setSummaryText("上课提醒")
                            .bigText("$name\n时间：$time\n地点：${if (room.isNotEmpty()) room else "未知"}"))
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.drawable.wakeup)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setVibrate(longArrayOf(0, 300, 200, 300))
                    .setContentIntent(openPi)
                    .build()
            nm.notify(9000 + index, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Widget error", e)
        }
    }

    companion object {

        /** W3-13③：「刷新 + 重排」这类重算操作的最小间隔（毫秒） */
        private const val RECOMPUTE_MIN_INTERVAL_MS = 60_000L

        /**
         * W3-13③ / v167+：上一次执行重算的单调时间戳（0 = 本进程还没跑过）。
         *
         * 改用 `AtomicLong` + CAS，保证同秒并发的多条 goAsync 只有第一条真正执行 scheduleAll。
         * 原来的 `@Volatile` 没锁，两条几乎同时到达时都能读到「now - prev ≈ 0 < 60s」
         * 从而一起放行（同秒最多 2 次重算）—— 节流形同虚设。
         *
         * 用 `SystemClock.elapsedRealtime()`（单调时钟）而不是墙钟：调时间/改时区
         * 会让墙钟跳变，单调时钟不会，正好匹配"节流"这个语义。
         * 进程重启后该字段自然归零，所以开机广播不会被上一次进程的节流误吞。
         */
        private val lastRecomputeAt = java.util.concurrent.atomic.AtomicLong(0L)
        private const val TAG = "WidgetUpdateReceiver"

        /**
         * 刷新全部类型的小部件（周课表 / 今日课程 / 下一节课）。
         *
         * v167 改：① 新增 force 参数（手动刷新/resize 时传 true 跳过指纹）；
         *          ② 开头 markRefreshed —— 供 WorkManager 的 24h 兜底闸门计数；
         *          ③ 只写 logcat 不写 SharedPreferences（历史 SP 会无限增长，年 2920 条全量序列化越跑越慢）。
         */
        fun refreshAllWidgets(context: Context, force: Boolean = false) {
            WidgetFingerprint.markRefreshed(context)   // 闸门 lastRefreshAt 依赖
            Log.i("WidgetRefresh", "refreshAllWidgets force=$force ts=${System.currentTimeMillis()}")
            try {
                val awm = AppWidgetManager.getInstance(context)
                val db = AppDatabase.getDatabase(context)
                val widgetDao = db.appWidgetDao()
                val tableDao = db.tableDao()
                val defaultTable = tableDao.getDefaultTableSync()

                // 今日课程：按组件名找全部实例，不依赖数据库登记
                defaultTable?.let { table ->
                    awm.getAppWidgetIds(ComponentName(context, TodayCourseAppWidget::class.java))
                            .forEach { id ->
                                // 单个实例刷新失败只记日志，不阻断其余部件（v133 残留缺口收尾）
                                try {
                                    AppWidgetUtils.refreshTodayWidget(context, awm, id, table,
                                            force = force)
                                } catch (t: Throwable) {
                                    Log.e(TAG, "refresh today widget failed", t)
                                    CrashLogger.logCaught("widget", t)
                                }
                            }
                }
                // 周课表：实例来源改为**按组件名枚举**（W3-1），不再依赖 DB 登记行。
                val scheduleBindings = widgetDao.getWidgetsByTypesSync(0, 0).associateBy { it.id }
                val scheduleIds = awm.getAppWidgetIds(
                        ComponentName(context, ScheduleAppWidget::class.java))
                for (widgetId in scheduleIds) {
                    try {
                        val info = scheduleBindings[widgetId]?.info.orEmpty()
                        val t = if (info.isEmpty()) defaultTable
                                else tableDao.getTableByIdSync(info.toIntOrNull() ?: -1)
                        val bindTable = t ?: defaultTable
                        if (bindTable != null) {
                            AppWidgetUtils.refreshScheduleWidget(context, awm, widgetId, bindTable,
                                    force = force)
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "refresh schedule widget failed", t)
                        CrashLogger.logCaught("widget", t)
                    }
                }
                // 下一节课（按组件名找全部实例，无需登记 DB）
                val nextIds = awm.getAppWidgetIds(ComponentName(context, NextCourseAppWidget::class.java))
                nextIds.forEach { id ->
                    try {
                        AppWidgetUtils.refreshNextWidget(context, awm, id, force = force)
                    } catch (t: Throwable) {
                        Log.e(TAG, "refresh next widget failed", t)
                        CrashLogger.logCaught("widget", t)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Widget error", e)
            }
        }
    }
}
