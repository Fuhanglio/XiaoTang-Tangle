package com.Tangle.timetable.widget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * WorkManager 兜底任务：每 30 分钟刷新一次所有小部件，
 * 防止精确闹钟被系统杀掉后小部件长时间不更新。
 *
 * W3-14：原来只做 `refreshAllWidgets`，不补种调度 → 闹钟链一旦被杀，
 * 兜底 Worker 能救 UI，但**永远救不回闹钟**（刷新会一直靠 30 分钟级的 Worker 撑着）。
 * 现在顺手调 `scheduleAll`，让每一次兜底同时把精确闹钟链重新注册回来（幂等）。
 */
class WidgetWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        WidgetUpdateReceiver.refreshAllWidgets(applicationContext)
        // 兜底同时补种闹钟链；scheduleAll 内部逐个 schedule 都有异常兜底，不会让本 Worker 失败
        WidgetScheduler.scheduleAll(applicationContext)
        return Result.success()
    }
}
