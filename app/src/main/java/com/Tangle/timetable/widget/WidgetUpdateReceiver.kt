package com.Tangle.timetable.widget

import android.util.Log

import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.Tangle.timetable.AppDatabase
import com.Tangle.timetable.R
import com.Tangle.timetable.SplashActivity
import com.Tangle.timetable.today_appwidget.TodayCourseAppWidget
import com.Tangle.timetable.utils.AppWidgetUtils
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.getPrefer
import com.Tangle.timetable.utils.goAsync
import com.Tangle.timetable.next_appwidget.NextCourseAppWidget

/**
 * 小部件统一更新接收器：
 * 闹钟（课程开始/结束/课前提醒）、开机、时间/时区/日期变化、亮屏、手动刷新
 * 全部汇集到这里 → 刷新所有小部件 + 必要时重建当天闹钟 + 发课前提醒通知。
 */
class WidgetUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            // 课前提醒通知
            WidgetScheduler.ACTION_REMIND -> goAsync {
                showReminderNotification(context, intent)
                refreshAllWidgets(context)
                WidgetScheduler.scheduleAll(context)
            }
            // 需要重算（开机/跨天/时间/时区变化、每日00:05）
            WidgetScheduler.ACTION_RECOMPUTE,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_SCREEN_ON,
            WidgetScheduler.ACTION_COURSE_START,
            WidgetScheduler.ACTION_COURSE_END -> goAsync {
                refreshAllWidgets(context)
                WidgetScheduler.scheduleAll(context)
            }
            // 手动刷新
            WidgetScheduler.ACTION_REFRESH -> goAsync {
                refreshAllWidgets(context)
            }
            // 点击小部件：先刷新数据，再打开 App 主界面
            "com.Tangle.timetable.action.WIDGET_OPEN_APP" -> {
                goAsync {
                    refreshAllWidgets(context)
                }
                val open = Intent(context, SplashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                try {
                    context.startActivity(open)
                } catch (e: Exception) {
                    Log.e(TAG, "Widget error", e)
                }
            }
        }
    }

    private fun showReminderNotification(context: Context, intent: Intent) {
        // 提醒总开关：关了就不再弹（旧闹钟残留时也不能弹出来）
        if (!context.getPrefer().getBoolean(Const.KEY_COURSE_REMIND, false)) return
        try {
            val name = intent.getStringExtra("courseName") ?: "课程"
            val room = intent.getStringExtra("room") ?: ""
            val time = intent.getStringExtra("time") ?: ""
            val index = intent.getIntExtra("index", 0)

            val openIntent = Intent(context, SplashActivity::class.java)
            val openPi = PendingIntent.getActivity(context, 9000 + index, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, "schedule_reminder")
                    .setContentTitle("$time $name 即将上课")
                    .setContentText(if (room.isNotEmpty()) "地点：$room" else "记得提前做好准备~")
                    .setStyle(NotificationCompat.BigTextStyle()
                            .setSummaryText("上课提醒")
                            .bigText("$name\n时间：$time\n地点：${if (room.isNotEmpty()) room else "未知"}"))
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.drawable.wakeup)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setVibrate(longArrayOf(0, 300, 200, 300))
                    .setContentIntent(openPi)
                    .build()
            nm.notify(9000 + index, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Widget error", e)
        }
    }

    companion object {
        private const val TAG = "WidgetUpdateReceiver"
        /** 刷新全部类型的小部件（周课表 / 今日课程 / 下一节课） */
        fun refreshAllWidgets(context: Context) {
            try {
                val awm = AppWidgetManager.getInstance(context)
                val db = AppDatabase.getDatabase(context)
                val widgetDao = db.appWidgetDao()
                val tableDao = db.tableDao()
                val defaultTable = tableDao.getDefaultTableSync()

                // 今日课程：按组件名找全部实例，不依赖数据库登记
                defaultTable?.let { table ->
                    awm.getAppWidgetIds(ComponentName(context, TodayCourseAppWidget::class.java))
                            .forEach { id -> AppWidgetUtils.refreshTodayWidget(context, awm, id, table) }
                }
                // 周课表：DB 里登记的实例各自可能绑定不同课表，按登记信息取表
                for (w in widgetDao.getWidgetsByTypesSync(0, 0)) {
                    val t = if (w.info.isEmpty()) defaultTable
                            else tableDao.getTableByIdSync(w.info.toIntOrNull() ?: -1)
                    if (t != null) AppWidgetUtils.refreshScheduleWidget(context, awm, w.id, t)
                }
                // 下一节课（按组件名找全部实例，无需登记 DB）
                val nextIds = awm.getAppWidgetIds(ComponentName(context, NextCourseAppWidget::class.java))
                nextIds.forEach { id ->
                    AppWidgetUtils.refreshNextWidget(context, awm, id)
                }
                // 说明：今日课程与周课表小部件都已改成静态行布局，没有 AdapterView，
                // 因此这里不再需要 notifyAppWidgetViewDataChanged。
            } catch (e: Exception) {
                Log.e(TAG, "Widget error", e)
            }
        }
    }
}


