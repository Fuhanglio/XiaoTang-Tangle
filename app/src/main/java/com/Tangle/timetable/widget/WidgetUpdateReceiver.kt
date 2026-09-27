package com.Tangle.timetable.widget

import android.os.SystemClock
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
import com.Tangle.timetable.schedule_appwidget.ScheduleAppWidget
import com.Tangle.timetable.today_appwidget.TodayCourseAppWidget
import com.Tangle.timetable.utils.AppWidgetUtils
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.CrashLogger
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
            // W3-13①：这里原本还列着 Intent.ACTION_SCREEN_ON —— 那是**死分支**：
            //   亮屏广播无法在 Manifest 里静态注册（系统不给隐式广播），App.kt 是**动态注册**
            //   自己的 receiver 来处理亮屏。留在这里只会让后人误判"亮屏链路走这里"。
            WidgetScheduler.ACTION_RECOMPUTE,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            // 20:00 预告放行点：到点切「明天预告」，同时重排（把下一天的 20:00 闹钟接上）
            WidgetScheduler.ACTION_PREVIEW_START,
            WidgetScheduler.ACTION_COURSE_START,
            WidgetScheduler.ACTION_COURSE_END -> goAsync {
                // W3-13③：refresh + reschedule 是"幂等但昂贵"的操作（近百次 cancelAlarm +
                // PendingIntent 注册 + 同步 Room 查询），而上面这一组 action 可能在同一秒内
                // 连着来好几个（例如开机时 BOOT_COMPLETED 与 DATE_CHANGED 常挨在一起）。
                // 这里做 60 秒节流。用 `in 0 until X` 而不是 `< X`：万一时钟基准出现负差值，
                // 应放行而不是吞掉（宁可多跑一次，不能漏掉开机这种必须执行的事件）。
                // 手动刷新（ACTION_REFRESH）与课前提醒（ACTION_REMIND）**不受此限制**。
                val now = SystemClock.elapsedRealtime()
                if (now - lastRecomputeAt in 0 until RECOMPUTE_MIN_INTERVAL_MS) return@goAsync
                lastRecomputeAt = now
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

        /** W3-13③：「刷新 + 重排」这类重算操作的最小间隔（毫秒） */
        private const val RECOMPUTE_MIN_INTERVAL_MS = 60_000L

        /**
         * W3-13③：上一次执行重算的单调时间戳（0 = 本进程还没跑过）。
         *
         * 用 `SystemClock.elapsedRealtime()`（单调时钟）而不是墙钟：调时间/改时区
         * 会让墙钟跳变，单调时钟不会，正好匹配"节流"这个语义。
         * 进程重启后该字段自然归零，所以开机广播不会被上一次进程的节流误吞。
         */
        @Volatile
        private var lastRecomputeAt = 0L
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
                            .forEach { id ->
                                // 单个实例刷新失败只记日志，不阻断其余部件（v133 残留缺口收尾）
                                try {
                                    AppWidgetUtils.refreshTodayWidget(context, awm, id, table)
                                } catch (t: Throwable) {
                                    Log.e(TAG, "refresh today widget failed", t)
                                    CrashLogger.logCaught("widget", t)
                                }
                            }
                }
                // 周课表：实例来源改为**按组件名枚举**（W3-1），不再依赖 DB 登记行。
                // 旧实现是 `for (w in getWidgetsByTypesSync(0, 0))`：登记行一旦不存在
                // （它绑定的课表被删除时 ScheduleManageFragment 会调 deleteAppWidgetByInfo
                // 删掉登记行），该实例就永远进不了这个循环 → 部件冻结在旧内容，不再自愈。
                // 现在：实例全集来自 AppWidgetManager，DB 登记行只用来决定「绑哪张课表」；
                // 登记行缺失、或它指向的课表已不存在 → 回落默认表。
                val scheduleBindings = widgetDao.getWidgetsByTypesSync(0, 0).associateBy { it.id }
                val scheduleIds = awm.getAppWidgetIds(
                        ComponentName(context, ScheduleAppWidget::class.java))
                for (widgetId in scheduleIds) {
                    try {
                        val info = scheduleBindings[widgetId]?.info.orEmpty()
                        val t = if (info.isEmpty()) defaultTable
                                else tableDao.getTableByIdSync(info.toIntOrNull() ?: -1)
                        val bindTable = t ?: defaultTable
                        if (bindTable != null) {
                            AppWidgetUtils.refreshScheduleWidget(context, awm, widgetId, bindTable)
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "refresh schedule widget failed", t)
                        CrashLogger.logCaught("widget", t)
                    }
                }
                // 下一节课（按组件名找全部实例，无需登记 DB）
                val nextIds = awm.getAppWidgetIds(ComponentName(context, NextCourseAppWidget::class.java))
                nextIds.forEach { id ->
                    try {
                        AppWidgetUtils.refreshNextWidget(context, awm, id)
                    } catch (t: Throwable) {
                        Log.e(TAG, "refresh next widget failed", t)
                        CrashLogger.logCaught("widget", t)
                    }
                }
                // 说明：今日课程与周课表小部件都已改成静态行布局，没有 AdapterView，
                // 因此这里不再需要 notifyAppWidgetViewDataChanged。
            } catch (e: Exception) {
                Log.e(TAG, "Widget error", e)
            }
        }
    }
}


