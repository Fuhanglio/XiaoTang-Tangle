package com.Tangle.timetable.next_appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.Tangle.timetable.utils.AppWidgetUtils
import com.Tangle.timetable.utils.goAsync

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
        }
    }
}
