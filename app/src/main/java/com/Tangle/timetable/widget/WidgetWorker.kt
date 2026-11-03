package com.Tangle.timetable.widget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * WorkManager 兜底任务。
 *
 * v167 改：周期 30→180 分钟 + 触发闸门前置。
 * 无课日 / 课后 / 24h 内已刷过时，闸门返回 false → 直接秒退，不做任何渲染；
 * 真跑时也只重排课程闹钟 + 倒计时 tick，不再全量 scheduleAll。
 */
class WidgetWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        // 闸门先进：无课日 / 课后 / 24h 内已刷过 → 直接秒退
        if (!WidgetRefreshPolicy.shouldRefresh(ctx, WidgetRefreshPolicy.Trigger.WORKER)) {
            return Result.success()
        }
        WidgetUpdateReceiver.refreshAllWidgets(ctx)
        // 兜底补种闹钟链：只补课程闹钟，不再全量重排（省 96 次 cancel + 2 次 alarm set）
        WidgetScheduler.rescheduleCourseAlarms(ctx)
        return Result.success()
    }
}
