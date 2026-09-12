package com.Tangle.timetable.widget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * WorkManager 兜底任务：每 30 分钟刷新一次所有小部件，
 * 防止精确闹钟被系统杀掉后小部件长时间不更新。
 */
class WidgetWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        WidgetUpdateReceiver.refreshAllWidgets(applicationContext)
        return Result.success()
    }
}
