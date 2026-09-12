package com.Tangle.timetable.widget

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * 判断是否需要弹出权限引导（关键权限任一未就绪时返回 true）。
 */
object WidgetGuideHelper {

    fun shouldShowGuide(context: Context): Boolean {
        val batteryOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
        } else true
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()
        } else true
        val exactOk = WidgetScheduler.canScheduleExact(context)
        return !batteryOk || !notifOk || !exactOk
    }
}
