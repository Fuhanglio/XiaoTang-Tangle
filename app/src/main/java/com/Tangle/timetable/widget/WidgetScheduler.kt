package com.Tangle.timetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.getPrefer
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

    private const val RC_RECOMPUTE = 1000
    private const val RC_BASE_START = 2000
    private const val RC_BASE_END = 4000
    private const val RC_BASE_REMIND = 6000

    private const val WORK_NAME = "widget_fallback_refresh"

    /** Android 12+ 是否有精确闹钟权限（低版本恒为 true）。用反射兼容 compileSdk 29。 */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true   // Build.VERSION_CODES.S = 31
        return try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val m = AlarmManager::class.java.getMethod("canScheduleExactAlarms")
            m.invoke(am) as? Boolean ?: true
        } catch (e: Exception) {
            true   // 反射失败时不阻断，按允许处理（无权限时系统会自行降级）
        }
    }

    /** 注册全部调度（开机/时间变化/进 App/设置改动后调用） */
    fun scheduleAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleDailyRecompute(context, am)
        scheduleCourseAlarms(context, am)
        enqueueFallbackWork(context)
    }

    /** 每天 00:05 重算（跨天后重建当天闹钟） */
    private fun scheduleDailyRecompute(context: Context, am: AlarmManager) {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val pi = PendingIntent.getBroadcast(
                context, RC_RECOMPUTE,
                Intent(context, WidgetUpdateReceiver::class.java).setAction(ACTION_RECOMPUTE),
                PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }

    /** 为今天所有课程注册 开始/结束/课前提醒 闹钟 */
    private fun scheduleCourseAlarms(context: Context, am: AlarmManager) {
        val courses = WidgetData.getDayCourses(context, false)
        val now = System.currentTimeMillis()
        val remindBefore = context.getPrefer().getInt(Const.KEY_REMINDER_TIME, 10)
        val remindEnabled = context.getPrefer().getBoolean(Const.KEY_COURSE_REMIND, false)

        courses.forEachIndexed { index, c ->
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
                context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            // 无精确闹钟权限时降级（仍能在 Doze 下被唤醒，只是可能有几分钟延迟）
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    /** WorkManager 30 分钟兜底周期任务 */
    private fun enqueueFallbackWork(context: Context) {
        val request = androidx.work.PeriodicWorkRequest.Builder(
                WidgetWorker::class.java, 30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
