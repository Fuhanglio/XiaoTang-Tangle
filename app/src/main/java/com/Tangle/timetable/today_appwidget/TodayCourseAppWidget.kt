package com.Tangle.timetable.today_appwidget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.Tangle.timetable.AppDatabase
import com.Tangle.timetable.R
import com.Tangle.timetable.bean.AppWidgetBean
import com.Tangle.timetable.utils.*


/**
 * Implementation of App Widget functionality.
 */
class TodayCourseAppWidget : AppWidgetProvider() {

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
            //
            // W5-04：但**不能无条件相信**入参里的 appWidgetIds，更不该顺手写库。
            // 旧实现直接 `widgetDao.insertAppWidget(...)` —— 一旦收到带伪造 id 的广播
            // （W3-10 修复前任意应用都能发），或系统传入刚被删除的实例 id，
            // 这张表里就会留下**孤儿登记行**；而它还参与「上课提醒」判定与刷新调度识别。
            // 现在两道关：① 只处理「入参 ∩ 系统当前真实实例」；
            //            ② 写库前再用 getAppWidgetInfo(id) 确认实例确实存在。
            // 正常路径下入参本就等于系统实例集合，交集不改变任何行为。
            val live = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, TodayCourseAppWidget::class.java)).toHashSet()
            for (id in appWidgetIds) {
                if (id !in live) continue
                if (appWidgetManager.getAppWidgetInfo(id) == null) continue
                // 补登记，供「上课提醒」判定与刷新调度识别（只登记真实存在的实例）
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

    /**
     * W3-2：resize / 横竖屏切换 / 桌面重建后系统把真实尺寸经本回调交进来，原先全工程无实现 →
     * 尺寸档位要等下一个精确闹钟（最长一节课）或 30 分钟兜底 Worker 才更新，
     * v162 的「竖屏 MAX 优先」修复在真机上首次添加时就用不上。
     * 这里把系统给的 newOptions **直接透传**给 refreshTodayWidget：回调与
     * `getAppWidgetOptions()` 的内部更新之间存在时序窗口，透传可避免读到尚未同步的旧值。
     */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager,
                                          appWidgetId: Int, newOptions: Bundle) {
        val tableDao = AppDatabase.getDatabase(context).tableDao()
        goAsync {
            val table = tableDao.getDefaultTable() ?: return@goAsync
            AppWidgetUtils.refreshTodayWidget(context, appWidgetManager, appWidgetId, table,
                    overrideOptions = newOptions)
        }
    }
}




