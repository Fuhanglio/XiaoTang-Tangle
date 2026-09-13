package com.Tangle.timetable.today_appwidget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.Tangle.timetable.AppDatabase
import com.Tangle.timetable.R
import com.Tangle.timetable.SplashActivity
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
        if (intent.action == "WAKEUP_REMIND_COURSE") {
            if (context.getPrefer().getBoolean(Const.KEY_COURSE_REMIND, false)) {
                val courseName = intent.getStringExtra("courseName")
                var room = intent.getStringExtra("room")
                val time = intent.getStringExtra("time")
                val weekDay = intent.getStringExtra("weekDay")
                val index = intent.getIntExtra("index", PendingIntent.FLAG_IMMUTABLE)

                if (room.isNullOrEmpty()) {
                    room = "未知"
                }

                val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                val cancelIntent = Intent(context, TodayCourseAppWidget::class.java).apply {
                    action = "WAKEUP_CANCEL_REMINDER"
                    putExtra("index", index)
                }
                val cancelPendingIntent: PendingIntent = PendingIntent.getBroadcast(context, index, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                val openIntent = Intent(context, SplashActivity::class.java)
                val openPendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)

                val notification = NotificationCompat.Builder(context, "schedule_reminder")
                        .setContentTitle("$time $courseName")
                        .setSubText("上课提醒")
                        .setContentText("$weekDay  地点：$room")
                        .setWhen(System.currentTimeMillis())
                        .setSmallIcon(R.drawable.wakeup)
                        .setAutoCancel(false)
                        .setOngoing(context.getPrefer().getBoolean(Const.KEY_REMINDER_ON_GOING, false))
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                        .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                        .setVibrate(longArrayOf(0, 300, 200, 300))
                        .addAction(R.drawable.wakeup, "记得给手机静音哦", cancelPendingIntent)
                        .addAction(R.drawable.wakeup, "我知道啦", cancelPendingIntent)
                        .setContentIntent(openPendingIntent)
                manager.notify(index, notification.build())
            }
        }
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
        if (intent.action == "WAKEUP_CANCEL_REMINDER") {
            val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(intent.getIntExtra("index", PendingIntent.FLAG_IMMUTABLE))
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




