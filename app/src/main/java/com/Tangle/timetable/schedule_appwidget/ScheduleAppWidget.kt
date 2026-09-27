package com.Tangle.timetable.schedule_appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.Tangle.timetable.AppDatabase
import com.Tangle.timetable.utils.AppWidgetUtils
import com.Tangle.timetable.utils.CrashLogger
import com.Tangle.timetable.utils.MigrationUtils
import com.Tangle.timetable.utils.goAsync

/**
 * Implementation of App Widget functionality.
 *
 * W3-1：本类原先的**实例来源**一律是 DB 登记表（appwidgetbean），只有
 * `getWidgetsByTypes(0, 0)` 查出来的实例才会被渲染/刷新。一旦某个实例的登记行不存在
 * （典型路径：它绑定的课表被删除时 `deleteAppWidgetByInfo(tid)` 会删掉登记行），
 * 该实例就再也进不了刷新循环，**永久冻结**在旧内容上，除非用户重新添加部件。
 *
 * 现统一改为：**实例来源用 AppWidgetManager（或 onUpdate 直接给的 appWidgetIds）**，
 * DB 登记行只用于决定「这个实例绑哪张课表」；登记行缺失、或它指向的课表已不存在 → 回落默认表。
 */
class ScheduleAppWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        // W5-05：下面两个是**私有 action**，曾经"任何应用都能发" ——
        // 因为本 provider 之前是 `android:exported="true"`，第三方只要构造
        // `setComponent(本组件) + setAction("WAKEUP_NEXT_WEEK")` 就能远程翻动别人桌面的周次。
        // 现在 manifest 里三个 widget provider 已全部改成 `exported="false"`（W3-10）：
        // 跨应用的显式意图会被系统拦掉，而应用内 PendingIntent（同 UID）与系统投递都不受影响。
        // ⇒ 所以这里**不需要**再加"包名/permission 校验"这类补丁，那只增加维护面。
        // ⚠ 反过来：若将来有人把 exported 改回 true，这两个 action 会立刻重新变成公开入口。
        when (intent.action) {
            ACTION_NEXT_WEEK -> refreshAllInstances(context, nextWeek = true)
            ACTION_BACK_WEEK -> refreshAllInstances(context, nextWeek = false)
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // 直接用系统传进来的 appWidgetIds（它的语义就是「这次需要更新的实例」），
        // 不再回查 DB 登记表；旧实现在这里丢掉参数、改查 DB，导致未登记的实例永远显示「加载中…」
        goAsync {
            MigrationUtils.tranOldData(context.applicationContext)
            refreshInstances(context, appWidgetIds, nextWeek = false)
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
     * W3-2：尺寸档位原先没有消费者。新增部件时 `getAppWidgetOptions()` 通常还是空 → 走兜底值渲染一次；
     * 用户 resize / 横竖屏切换 / 桌面重建后，系统把真实尺寸经本回调交进来，但三个 Provider 都没实现
     * 它（全工程 grep 0 处）→ 只能等下一个精确闹钟（最长一节课）或 30 分钟兜底 Worker 才按新尺寸重绘。
     * 这里按实例立即重绘（detailType = 0 → 周课表卡），不再依赖下一次闹钟。
     */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager,
                                          appWidgetId: Int, newOptions: Bundle) {
        goAsync {
            AppWidgetUtils.refreshWidgetById(context.applicationContext, appWidgetManager, appWidgetId, 0)
        }
    }

    /**
     * 「上一周 / 下一周」：按组件名枚举本类型全部实例。
     * 这两个广播由 RemoteViews 的 PendingIntent 发出，本身不携带 appWidgetId，
     * 所以只能按组件名取全集；这也保持了「点一个卡的两个周部件一起翻页」的既有行为。
     */
    private fun refreshAllInstances(context: Context, nextWeek: Boolean) {
        val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, ScheduleAppWidget::class.java))
        goAsync {
            refreshInstances(context, ids, nextWeek)
        }
    }

    /** 逐个实例刷新；取表口径：登记行的 info → 该课表；取不到（无登记行 / 表已删除）→ 默认表。 */
    private suspend fun refreshInstances(context: Context, ids: IntArray, nextWeek: Boolean) {
        val dataBase = AppDatabase.getDatabase(context)
        val tableDao = dataBase.tableDao()
        val awm = AppWidgetManager.getInstance(context)
        // 登记表整体读一次并按实例 id 建索引，避免逐实例查询
        val bindings = dataBase.appWidgetDao().getWidgetsByTypes(0, 0).associateBy { it.id }
        val defaultTable = tableDao.getDefaultTable()
        for (widgetId in ids) {
            try {
                val info = bindings[widgetId]?.info.orEmpty()
                val bound = if (info.isEmpty()) null
                        else tableDao.getTableById(info.toIntOrNull() ?: -1)
                val table = bound ?: defaultTable ?: continue
                AppWidgetUtils.refreshScheduleWidget(context, awm, widgetId, table, nextWeek)
            } catch (t: Throwable) {
                // 单个实例失败不阻断其余实例（沿用 v133 的既有口径）
                Log.e(TAG, "refresh schedule widget failed", t)
                CrashLogger.logCaught("widget", t)
            }
        }
    }

    companion object {
        private const val TAG = "ScheduleAppWidget"
        private const val ACTION_NEXT_WEEK = "WAKEUP_NEXT_WEEK"
        private const val ACTION_BACK_WEEK = "WAKEUP_BACK_WEEK"
    }
}
