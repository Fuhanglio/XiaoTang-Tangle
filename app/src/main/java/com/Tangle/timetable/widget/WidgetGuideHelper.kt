package com.Tangle.timetable.widget

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.getPrefer

/**
 * 判断是否需要弹出权限引导（关键权限任一未就绪时返回 true）。
 */
object WidgetGuideHelper {

    fun shouldShowGuide(context: Context): Boolean {
        // W8-07：用户在引导页点「完成 / 稍后再说」时会写入 KEY_PERMISSION_GUIDE_SHOWN，
        // 但原实现**只写不读**（全工程唯一的写入点在 PermissionGuideActivity，读取点为 0），
        // 本函数也不看这个键 → 只要电池优化没加白名单（绝大多数用户的默认状态），
        // 每次冷启动（ScheduleActivity.onCreate）都会自动拉起引导页，「稍后再说」形同虚设。
        // 补上短路：已确认过就不再主动弹。
        // 默认取 false（全新安装没有该键 → 首次仍然引导一次，引导完才不再打扰）。
        if (context.getPrefer().getBoolean(Const.KEY_PERMISSION_GUIDE_SHOWN, false)) {
            return false
        }
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
