package com.Tangle.timetable.next_appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.Tangle.timetable.utils.AppWidgetUtils
import com.Tangle.timetable.utils.goAsync
import com.Tangle.timetable.widget.WidgetScheduler

/**
 * “下一节课”小部件：只显示接下来要上的那节课（名称、时间+教室、倒计时）。
 * 今天没课则显示明天第一节；明天也没课显示“近期无课程”。
 */
class NextCourseAppWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        goAsync {
            appWidgetIds.forEach { id ->
                AppWidgetUtils.refreshNextWidget(context, appWidgetManager, id)
            }
            // W3-14：本卡原先只刷新自己，**不注册调度**（对比 TodayCourseAppWidget.onUpdate 有调）。
            // 调度注册被绑在「今日卡 onUpdate」这一条路径上 → 若用户只添加了「下一节课」卡
            // （没加今日卡），在「下次打开 App / 重启」之前，精确闹钟与 30 分钟兜底 Worker
            // 都没有被注册过 → 倒计时文案（还有N分钟上课）不会随上课时刻变化。
            // scheduleAll 内部对已注册的 PI 用 FLAG_UPDATE_CURRENT 覆盖，幂等，重复调用无副作用。
            WidgetScheduler.scheduleAll(context)
        }
    }

    /**
     * W3-2：本卡内容不按高度算行数（只显示一节课），但实现本回调可让系统在 resize 后
     * 立刻重绘一次，避免停留在旧布局快照上。与另两个 Provider 保持同一实现口径。
     */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager,
                                          appWidgetId: Int, newOptions: Bundle) {
        goAsync {
            AppWidgetUtils.refreshNextWidget(context, appWidgetManager, appWidgetId)
        }
    }
}
