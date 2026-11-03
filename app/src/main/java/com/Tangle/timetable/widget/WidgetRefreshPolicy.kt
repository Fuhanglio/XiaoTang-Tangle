package com.Tangle.timetable.widget

import android.content.Context
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.getPrefer
import java.util.concurrent.TimeUnit

/**
 * 刷新闸门：统一回答「这次触发要不要真的醒来干活」。
 * 只做只读查询，不写库、不发广播、不注册闹钟。
 */
object WidgetRefreshPolicy {

    enum class Trigger {
        MANUAL, DATA_CHANGED,
        ALARM_COURSE, ALARM_DAILY, ALARM_PREVIEW, ALARM_COUNTDOWN,
        SCREEN_ON, USER_PRESENT, WORKER
    }

    /** 亮屏类刷新的最小间隔 */
    private const val SCREEN_MIN_INTERVAL_MS = 30 * 60 * 1000L
    /** 兜底 Worker 的「至少每天跑一次」周期 */
    private const val FORCE_DAILY_MS = 24 * 60 * 60 * 1000L

    /** 下一节课卡倒计时窗口：开课前 120 分钟内才排 tick */
    const val COUNTDOWN_WINDOW_MIN = 120
    /** 倒计时刷新粒度（分钟）。120 / 10 = 最多 12 条闹钟 */
    const val COUNTDOWN_STEP_MIN = 10

    fun shouldRefresh(context: Context, trigger: Trigger): Boolean {
        val result = when (trigger) {
            Trigger.MANUAL, Trigger.DATA_CHANGED -> true
            Trigger.ALARM_COURSE, Trigger.ALARM_DAILY -> true
            Trigger.ALARM_PREVIEW -> hasCourseInPreviewWindow(context)
            Trigger.ALARM_COUNTDOWN -> nextCourseWithinMinutes(context, COUNTDOWN_WINDOW_MIN)
            Trigger.SCREEN_ON, Trigger.USER_PRESENT ->
                System.currentTimeMillis() - WidgetFingerprint.lastRefreshAt(context) >= SCREEN_MIN_INTERVAL_MS
            // Worker：0:00-5:59 静默，只挡 WorkManager，不影响任何闹钟、亮屏、手动刷新。
            // 00:05 跨天重算已重排好，凌晨两次没有增量价值；6:00 之后 tomorrowStartsWithin(12) 会放行。
            Trigger.WORKER -> !isQuietHours() && (
                hasRemainingCourseToday(context) ||
                tomorrowStartsWithin(context, 12) ||
                System.currentTimeMillis() - WidgetFingerprint.lastRefreshAt(context) >= FORCE_DAILY_MS
            )
        }
        return result
    }

    /** Worker 专用：凌晨 0:00-5:59 静默窗口 */
    private fun isQuietHours(): Boolean {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return h in 0..5
    }

    /** 今天是否还有没上完的课（跨 0 点课已在 WidgetData 里算作今天） */
    fun hasRemainingCourseToday(context: Context): Boolean =
        WidgetData.getDayCourses(context, false).any { it.status != WidgetData.STATUS_FINISHED }

    fun hasCourseTomorrow(context: Context): Boolean =
        WidgetData.getDayCourses(context, true).isNotEmpty()

    /** 明天的第一节课是否在 hours 小时内开课（用于「明天要上课，今晚该把闹钟准备好」） */
    fun tomorrowStartsWithin(context: Context, hours: Int): Boolean {
        val first = WidgetData.getDayCourses(context, true).firstOrNull() ?: return false
        val diff = first.startMillis - System.currentTimeMillis()
        return diff in 0..(hours * 60L * 60_000L)
    }

    /**
     * 20:00 预告是否有意义：明天起、预览天数内是否有课。
     * 与 WidgetData.getSmartDayPlan 的 previewDays 口径保持一致（默认 2 天，配置为 7 时按 7 天）。
     */
    fun hasCourseInPreviewWindow(context: Context): Boolean {
        val days = if (context.getPrefer().getInt(Const.KEY_WIDGET_PREVIEW_DAYS, 2) == 2) 2 else 7
        for (offset in 1 until days) {
            if (WidgetData.getCoursesForOffset(context, offset).isNotEmpty()) return true
        }
        return false
    }

    /** 下一节课是否在 minutes 分钟内开课 */
    fun nextCourseWithinMinutes(context: Context, minutes: Int): Boolean {
        val next = WidgetData.getNextCourse(context) ?: return false
        val diff = next.startMillis - System.currentTimeMillis()
        return diff in 0..(minutes * 60_000L)
    }
}
