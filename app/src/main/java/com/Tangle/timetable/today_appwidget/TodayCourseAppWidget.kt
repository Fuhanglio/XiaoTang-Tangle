package com.Tangle.timetable.today_appwidget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.Tangle.timetable.AppDatabase
import com.Tangle.timetable.R
import com.Tangle.timetable.bean.AppWidgetBean
import com.Tangle.timetable.utils.*
import java.util.*


/**
 * Implementation of App Widget functionality.
 */
class TodayCourseAppWidget : AppWidgetProvider() {

    private var calendar = Calendar.getInstance()

    @SuppressLint("NewApi")
    override fun onReceive(context: Context, intent: Intent) {
        // 注意：不处理任何带 extras 的自定义通知类 action。
        // 旧的 WAKEUP_REMIND_COURSE 通道全工程无发送方（死代码），却因 Provider 隐式导出
        // 可被任意第三方应用伪造 extras 直接弹 PRIORITY_MAX 通知（钓鱼面），已整段移除。
        if (intent.action == "WAKEUP_NEXT_DAY") {
            val tableDao = AppDatabase.getDatabase(context).tableDao()
            val awm = AppWidgetManager.getInstance(context)
            goAsync {
                val table = tableDao.getDefaultTable() ?: return@goAsync
                for (id in awm.getAppWidgetIds(ComponentName(context, TodayCourseAppWidget::class.java))) {
                    // 手动临时查看明日（下一次闹钟/每日重算刷新会回到智能模式）
                    AppWidgetUtils.refreshTodayWidget(context, awm, id, table, true, manual = true)
                }
            }
        }
        if (intent.action == "WAKEUP_BACK_TIME") {
            val tableDao = AppDatabase.getDatabase(context).tableDao()
            val awm = AppWidgetManager.getInstance(context)
            goAsync {
                val table = tableDao.getDefaultTable() ?: return@goAsync
                for (id in awm.getAppWidgetIds(ComponentName(context, TodayCourseAppWidget::class.java))) {
                    // 手动临时回到今日（下一次闹钟/每日重算刷新会回到智能模式）
                    AppWidgetUtils.refreshTodayWidget(context, awm, id, table, false, manual = true)
                }
            }
        }
        super.onReceive(context, intent)
    }

    @SuppressLint("NewApi")
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val dataBase = AppDatabase.getDatabase(context)
        val widgetDao = dataBase.appWidgetDao()
        val tableDao = dataBase.tableDao()

        goAsync {
            val table = tableDao.getDefaultTable() ?: return@goAsync
            // 以系统给的实例 id 为准刷新，不再依赖数据库登记。
            // （今日小部件没有配置页，历史实现因此从未被登记，导致卡片一直停在布局的默认文案上）
            for (id in appWidgetIds) {
                // 顺手补登记，供「上课提醒」判定与刷新调度识别
                widgetDao.insertAppWidget(AppWidgetBean(id, 0, 1, ""))
                AppWidgetUtils.refreshTodayWidget(context, appWidgetManager, id, table)
            }
            // 统一注册：课程开始/结束精确刷新、课前提醒、每日重算、WorkManager 兜底
            com.Tangle.timetable.widget.WidgetScheduler.scheduleAll(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val dataBase = AppDatabase.getDatabase(context)
        val widgetDao = dataBase.appWidgetDao()
        goAsync {
            for (id in appWidgetIds) {
                widgetDao.deleteAppWidget(id)
            }
        }
    }
}




