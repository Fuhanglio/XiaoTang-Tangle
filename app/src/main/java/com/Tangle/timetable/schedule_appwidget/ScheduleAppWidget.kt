package com.Tangle.timetable.schedule_appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.Tangle.timetable.AppDatabase
import com.Tangle.timetable.utils.AppWidgetUtils
import com.Tangle.timetable.utils.UpdateUtils
import com.Tangle.timetable.utils.goAsync

/**
 * Implementation of App Widget functionality.
 */
class ScheduleAppWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "WAKEUP_NEXT_WEEK") {
            val dataBase = AppDatabase.getDatabase(context)
            val widgetDao = dataBase.appWidgetDao()
            val tableDao = dataBase.tableDao()
            goAsync {
                for (appWidget in widgetDao.getWidgetsByTypes(0, 0)) {
                    val table = if (appWidget.info.isEmpty()) {
                        tableDao.getDefaultTable()
                    } else {
                        tableDao.getTableById(appWidget.info.toIntOrNull() ?: -1)
                    }
                    if (table != null) {
                        AppWidgetUtils.refreshScheduleWidget(context, AppWidgetManager.getInstance(context), appWidget.id, table, true)
                    }
                }
            }
        }
        if (intent.action == "WAKEUP_BACK_WEEK") {
            val dataBase = AppDatabase.getDatabase(context)
            val widgetDao = dataBase.appWidgetDao()
            val tableDao = dataBase.tableDao()
            goAsync {
                for (appWidget in widgetDao.getWidgetsByTypes(0, 0)) {
                    val table = if (appWidget.info.isEmpty()) {
                        tableDao.getDefaultTable()
                    } else {
                        tableDao.getTableById(appWidget.info.toIntOrNull() ?: -1)
                    }
                    if (table != null) {
                        AppWidgetUtils.refreshScheduleWidget(context, AppWidgetManager.getInstance(context), appWidget.id, table)
                    }
                }
            }
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // There may be multiple widgets active, so update all of them
        val dataBase = AppDatabase.getDatabase(context)
        val widgetDao = dataBase.appWidgetDao()
        val tableDao = dataBase.tableDao()
        goAsync {
            UpdateUtils.tranOldData(context.applicationContext)
            for (appWidget in widgetDao.getWidgetsByTypes(0, 0)) {
                val table = if (appWidget.info.isEmpty()) {
                    tableDao.getDefaultTable()
                } else {
                    tableDao.getTableById(appWidget.info.toInt())
                }
                if (table != null) {
                    AppWidgetUtils.refreshScheduleWidget(context, AppWidgetManager.getInstance(context), appWidget.id, table)
                }
            }
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

