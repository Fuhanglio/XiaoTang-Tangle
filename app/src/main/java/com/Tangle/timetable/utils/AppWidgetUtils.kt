package com.Tangle.timetable.utils

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.Tangle.timetable.AppDatabase
import com.Tangle.timetable.R
import com.Tangle.timetable.SplashActivity
import com.Tangle.timetable.bean.TableBean
import com.Tangle.timetable.course_add.AddCourseActivity
import com.Tangle.timetable.schedule_appwidget.ScheduleAppWidget
import com.Tangle.timetable.schedule_settings.BirthdayReminderActivity
import com.Tangle.timetable.today_appwidget.TodayCourseAppWidget
import com.Tangle.timetable.widget.WidgetData
import com.Tangle.timetable.widget.WidgetScheduler
import com.Tangle.timetable.widget.WidgetUpdateReceiver
import com.Tangle.timetable.utils.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 小部件刷新协程的**应用级单例作用域**（W8-09）。
 *
 * 原来 `goAsync` 的默认参数是 `GlobalScope`，各接收器都不传 scope → 刷新任务挂在全局
 * 顶层作用域上，协程寿命与组件**完全脱钩**（虽然 try/catch(Throwable) + finally 的
 * `result.finish()` 兜住了 crash 与广播超时，但归属是模糊的）。
 *
 * 换成显式声明的应用级作用域，语义上等价于「进程存活期」但**归属明确**：
 * - `SupervisorJob()`：单个实例刷新失败不牵连同一 scope 上的其它任务
 * - `Dispatchers.IO`：`refreshWidgetById` 内部是**同步 Room 查询**，
 *   原来落在 `Dispatchers.Default`（CPU 池）上跑阻塞 IO 是错配，改到 IO 池
 *
 * 注：本文件里的 `goAsync` 是**顶层扩展函数**（不在 `object AppWidgetUtils` 内），
 * 所以这个 val 也必须是顶层 private。
 */
private val widgetScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

/**
 * W3-11：广播兜底的**整段时间预算**（毫秒）。
 *
 * 接收器的 onReceive 必须在约 10 秒内返回，这里留 8 秒给刷新，
 * 超时抛 `TimeoutCancellationException`（CancellationException 的子类），
 * 由下面的 `catch (t: Throwable)` 一并兜住，`finally` 里的 `result.finish()` 照常收尾。
 */
private const val GO_ASYNC_TIMEOUT_MS = 8_000L

fun BroadcastReceiver.goAsync(
        coroutineScope: CoroutineScope = widgetScope,
        block: suspend () -> Unit
) {
    val result = goAsync()
    coroutineScope.launch {
        try {
            // W3-11：原来只有异常兜底，**没有时间预算** ——
            // 一旦 refreshWidgetById 里某个 DAO 查询卡住（大库 / 首次建库迁移 / 磁盘忙），
            // 协程会一直挂着，广播既没 finish 也没结束。加一层超时把它变成"有界的失败"。
            withTimeout(GO_ASYNC_TIMEOUT_MS) { block() }
        } catch (t: Throwable) {
            // 关键保险：刷新里的任何异常都不能变成未捕获异常杀掉进程，
            // 否则小部件会停在布局默认的「加载中…」，直到下一个触发点才自愈
            Log.e("WidgetAsync", "widget refresh failed", t)
            // 顺带落盘：这里会把异常吞掉，只写 logcat 容易错过（真机排查时缓冲区常已翻篇）
            CrashLogger.logCaught("widget", t)
        } finally {
            // Always call finish(), even if the coroutineScope was cancelled
            result.finish()
        }
    }
}

object AppWidgetUtils {

    fun updateWidget(context: Context) {
        val intent = Intent()
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        // 显式限定本应用，否则 Android 8+ 隐式广播会被拒收/无人接收，改课后小部件实际不刷新
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    /**
     * 「点击卡片打开 App」的统一入口。
     * W2-05：周课表卡与今日课程卡原先各写一遍 `getActivity(rc=0, Intent(SplashActivity))`，
     * 两者的 filterEquals 完全相同，实际本来就是同一条 PendingIntent 记录。
     * 抽成同一个函数后，「两个部件共用一条记录」成为显式意图，而不是看起来像偶然撞在一起。
     */
    private fun openAppPi(context: Context): PendingIntent =
            PendingIntent.getActivity(context, PI_OPEN_APP,
                    Intent(context, SplashActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    // 固定 PendingIntent 槽位。getActivity 与 getBroadcast 是两套独立命名空间
    // （PI 身份 = 类型 + requestCode + filterEquals），下面三个互不冲突；
    // 逐实例的槽位统一从 PI_NEXT_OPEN_APP_BASE 起算，与固定值刻意错开（见 W2-06）。
    private const val PI_OPEN_APP = 0
    private const val PI_OPEN_APP_REFRESH = 3
    private const val PI_NEXT_OPEN_APP_BASE = 100_000

    /**
     * 卡片高度档位的**唯一事实来源**。
     *
     * W3-3：周课表卡与今日课程卡原先各写一套语义（周卡无条件 `MAX_HEIGHT` 优先；今日卡竖屏取
     * `MAX_HEIGHT`、横屏取 `MIN_HEIGHT`）。同一台设备、同一组 options 下两者在横屏必然取值不同，
     * 而 `minHeight != maxHeight`，**至少有一个是错的**，最多一个能对。统一到本函数后不再有两套口径。
     *
     * W3-4：**刻意不判断屏幕方向**。原实现用 `context.resources.configuration.orientation` 判定
     * 竖/横屏，但小部件刷新绝大多数发生在后台 Receiver / Worker 里，此时进程可能长时间没有 Activity，
     * AMS 不保证把旋转后的 Configuration 同步过来 → 读到的是上次有 UI 时的陈旧值，甚至
     * `ORIENTATION_UNDEFINED(0)`（会被判成竖屏）。拿进程级 Configuration 去解释宿主（launcher）
     * 给的 options，二者生命周期本就不同步，判方向必然失真。
     *
     * 取两者**较大值**：任一侧为 0 时自然回退到另一侧；两侧都不可用时落 `fallback`。
     * 越界值（<=0 或 >2000dp）也视为不可用，避免脏 options 把行数算飞。
     */
    private fun currentSizeDp(options: Bundle, fallback: Int): Int {
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        val h = maxOf(minHeight, maxHeight)
        return if (h in 1..2000) h else fallback
    }

    fun refreshScheduleWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, tableBean: TableBean, nextWeek: Boolean = false) {
        val mRemoteViews = RemoteViews(context.packageName, R.layout.schedule_app_widget)
        val isDark = ThemeManager.isDark(context)
        if (isDark) {
            mRemoteViews.setInt(R.id.week_root, "setBackgroundResource", R.drawable.widget_today_card_bg_dark)
        }
        val titleColor = if (isDark) 0xFFFFFFFF.toInt() else 0xFF1C1C1E.toInt()
        val subColor = 0xFF8E8E93.toInt()
        val primary = ThemeManager.getColor(context, ThemeManager.PRIMARY)

        var week = CourseUtils.countWeek(tableBean.startDate, tableBean.sundayFirst)
        if (nextWeek) {
            week++
        }
        if (tableBean.tableName.isEmpty()) {
            tableBean.tableName = "我的课表"
        }
        val plan = WidgetData.getWeekPlan(context, week, tableBean)

        // 标题：课表名 · 第N周；副标：本周日期范围（未开学 / 学期已结束 / 本周无课都给明确提示）
        if (plan.notStarted) {
            mRemoteViews.setTextViewText(R.id.tv_weekTitle, "${tableBean.tableName} · 还没有开学哦")
        } else {
            mRemoteViews.setTextViewText(R.id.tv_weekTitle, "${tableBean.tableName} · 第${plan.week}周")
        }
        mRemoteViews.setTextViewText(R.id.tv_weekRange, when {
            plan.notStarted -> "检查一下课表设置里的学期开始日期"
            plan.over -> "本学期已结束，好好休息~"
            plan.rows.isEmpty() -> "本周没有课哦"
            else -> plan.rangeText
        })
        mRemoteViews.setTextColor(R.id.tv_weekTitle, titleColor)
        mRemoteViews.setTextColor(R.id.tv_weekRange, subColor)

        // 操作图标
        mRemoteViews.setInt(R.id.iv_add, "setColorFilter", primary)
        mRemoteViews.setInt(R.id.iv_refresh, "setColorFilter", subColor)
        mRemoteViews.setInt(R.id.iv_next, "setColorFilter", subColor)
        mRemoteViews.setInt(R.id.iv_back, "setColorFilter", subColor)
        if (nextWeek) {
            mRemoteViews.setViewVisibility(R.id.iv_next, View.GONE)
            mRemoteViews.setViewVisibility(R.id.iv_back, View.VISIBLE)
        } else {
            mRemoteViews.setViewVisibility(R.id.iv_next, View.VISIBLE)
            mRemoteViews.setViewVisibility(R.id.iv_back, View.GONE)
        }

        // 显示几行：按卡片实际高度算（行高 24dp + 行距 2dp，标题区留 40dp，上下内边距共 24dp）
        // W3-3/W3-4：改走 currentSizeDp（maxOf + 与方向解耦），不再在这里内联一套取法
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val cardHeightDp = currentSizeDp(options, 200)
        val maxRows = ((cardHeightDp - 24 - 40 + 2) / 26).coerceIn(1, 7)

        // 每天一行，全部用显式 id 数组
        val wrowIds = intArrayOf(R.id.wrow_0, R.id.wrow_1, R.id.wrow_2, R.id.wrow_3,
                R.id.wrow_4, R.id.wrow_5, R.id.wrow_6)
        val wbgIds = intArrayOf(R.id.wrow_bg_0, R.id.wrow_bg_1, R.id.wrow_bg_2, R.id.wrow_bg_3,
                R.id.wrow_bg_4, R.id.wrow_bg_5, R.id.wrow_bg_6)
        val wbarIds = intArrayOf(R.id.wrow_bar_0, R.id.wrow_bar_1, R.id.wrow_bar_2, R.id.wrow_bar_3,
                R.id.wrow_bar_4, R.id.wrow_bar_5, R.id.wrow_bar_6)
        val wlabelIds = intArrayOf(R.id.wrow_label_0, R.id.wrow_label_1, R.id.wrow_label_2, R.id.wrow_label_3,
                R.id.wrow_label_4, R.id.wrow_label_5, R.id.wrow_label_6)
        val wcourseIds = intArrayOf(R.id.wrow_courses_0, R.id.wrow_courses_1, R.id.wrow_courses_2, R.id.wrow_courses_3,
                R.id.wrow_courses_4, R.id.wrow_courses_5, R.id.wrow_courses_6)

        val rows = plan.rows.take(maxRows)
        for (i in wrowIds.indices) {
            if (i < rows.size) {
                val row = rows[i]
                val hasCourse = row.courses.isNotEmpty()
                val courseColor = if (hasCourse) {
                    try {
                        android.graphics.Color.parseColor(row.color)
                    } catch (e: Exception) {
                        0xFF007AFF.toInt()
                    }
                } else {
                    0xFF8E8E93.toInt()
                }

                // 圆角底：有课用当天第一门课的课程色（浅），无课给极淡的灰
                mRemoteViews.setInt(wbgIds[i], "setColorFilter",
                        android.graphics.Color.argb(if (hasCourse) 0x24 else 0x12,
                                android.graphics.Color.red(courseColor),
                                android.graphics.Color.green(courseColor),
                                android.graphics.Color.blue(courseColor)))
                mRemoteViews.setInt(wbarIds[i], "setColorFilter",
                        if (hasCourse) courseColor else 0xFFD1D1D6.toInt())

                mRemoteViews.setTextViewText(wlabelIds[i], "${row.dayLabel} ${row.dateText}")
                // 今天那一行用主题主色标出来
                mRemoteViews.setTextColor(wlabelIds[i], if (row.isToday) primary else subColor)
                mRemoteViews.setTextViewText(wcourseIds[i], if (hasCourse) row.courses else "无课")
                mRemoteViews.setTextColor(wcourseIds[i], if (hasCourse) titleColor else subColor)

                mRemoteViews.setViewVisibility(wrowIds[i], View.VISIBLE)
            } else {
                mRemoteViews.setViewVisibility(wrowIds[i], View.GONE)
            }
        }

        // 点击标题或列表区域：打开 App（与今日课程卡共用同一条 PendingIntent，见 openAppPi）
        val pIntent = openAppPi(context)
        mRemoteViews.setOnClickPendingIntent(R.id.tv_weekTitle, pIntent)
        mRemoteViews.setOnClickPendingIntent(R.id.ll_week, pIntent)

        // 上一周 / 下一周
        val nextIntent = Intent(context, ScheduleAppWidget::class.java)
        nextIntent.action = "WAKEUP_NEXT_WEEK"
        val pi = PendingIntent.getBroadcast(context, 1, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        mRemoteViews.setOnClickPendingIntent(R.id.iv_next, pi)

        val backIntent = Intent(context, ScheduleAppWidget::class.java)
        backIntent.action = "WAKEUP_BACK_WEEK"
        val backPi = PendingIntent.getBroadcast(context, 2, backIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        mRemoteViews.setOnClickPendingIntent(R.id.iv_back, backPi)

        // 刷新图标
        val refreshPi = PendingIntent.getBroadcast(context, 4,
                Intent(context, WidgetUpdateReceiver::class.java).setAction(WidgetScheduler.ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        mRemoteViews.setOnClickPendingIntent(R.id.iv_refresh, refreshPi)

        // “+”：直接打开添加课程页，临时加一两节课不用再走教务导入。
        // W3-9（=W2-01）：requestCode 必须**逐实例唯一**。原来固定写 5，而 Intent 里带的是
        // 逐实例不同的 extras（tableId / maxWeek / nodes），但 PendingIntent 的身份比较
        // （Intent.filterEquals）**不含 extras** → 多实例绑不同课表时共用同一条记录，
        // FLAG_UPDATE_CURRENT 会让后刷新的实例把 extras 覆写掉，点「+」进的是别的课表。
        // 改为 rc = appWidgetId：与 rc=0（SplashActivity）/ rc=6（BirthdayReminderActivity）
        // 同处 getActivity 命名空间，但 component 不同，filterEquals 已天然隔离。
        val addIntent = Intent(context, AddCourseActivity::class.java).apply {
            putExtra("tableId", tableBean.id)
            putExtra("maxWeek", tableBean.maxWeek)
            putExtra("nodes", tableBean.nodes)
            putExtra("id", -1)
        }
        val addPi = PendingIntent.getActivity(context, appWidgetId, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        mRemoteViews.setOnClickPendingIntent(R.id.iv_add, addPi)

        appWidgetManager.updateAppWidget(appWidgetId, mRemoteViews)
    }

    /**
     * 按数据库登记的实例 id 刷新对应小部件。
     * 供 App 内增删课程后调用（detailType：0 = 周课表，其它 = 今日课程）。
     */
    fun refreshWidgetById(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, detailType: Int) {
        try {
            val db = AppDatabase.getDatabase(context)
            val tableDao = db.tableDao()
            // 优先按部件登记的 info（绑定课表 id）取表，与 refreshAllWidgets 的口径一致；
            // 旧实现一律取默认表，增删改课后会把绑定非默认表的周课表部件刷成默认表内容
            val boundId = db.appWidgetDao().getWidgetByIdSync(appWidgetId)
                    ?.info?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: -1
            val table = (if (boundId > 0) tableDao.getTableByIdSync(boundId) else null)
                    ?: tableDao.getDefaultTableSync() ?: return
            if (detailType == 0) {
                refreshScheduleWidget(context, appWidgetManager, appWidgetId, table)
            } else {
                refreshTodayWidget(context, appWidgetManager, appWidgetId, table)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 今日小部件（智能模式）：
     * - 不传 nextDay（自动模式）：按 WidgetData.getSmartDayPlan 决定显示今天或明天，
     *   今天课全上完时自动预告明天；课程开始/结束闹钟与每日重算触发的都是自动模式；
     * - manual=true：iv_next/iv_back 的临时查看（强制显示 nextDay 指定的一天）。
     */
    fun refreshTodayWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, tableBean: TableBean, nextDay: Boolean = false, manual: Boolean = false, overrideOptions: Bundle? = null) {
        try {
        val mRemoteViews = RemoteViews(context.packageName, R.layout.today_course_app_widget)

        val smart = WidgetData.getSmartDayPlan(context, tableBean)
        // 手动查看时只切今天/明天；自动模式跟随智能结果（可能跳到几天后，例如周五课全上完后预告下周一）
        val offset = if (manual) (if (nextDay) 1 else 0) else smart.dayOffset
        val showingToday = offset == 0
        val weekDayText = if (offset >= 0) WidgetData.weekdayTextForOffset(offset) else ""
        val dateText = if (offset >= 0) WidgetData.dateTextForOffset(offset) else ""
        val weekNo = if (offset >= 0) WidgetData.weekForOffset(context, offset, tableBean) else -1
        val isDark = ThemeManager.isDark(context)

        // 卡片背景：深色模式换深色卡
        if (isDark) {
            mRemoteViews.setInt(R.id.today_root, "setBackgroundResource", R.drawable.widget_today_card_bg_dark)
        }
        val titleColor = if (isDark) 0xFFFFFFFF.toInt() else 0xFF1C1C1E.toInt()
        val subColor = 0xFF8E8E93.toInt()

        // 本次显示几门课：按卡片实际高度算。
        // v162 曾按「竖屏取 MAX_HEIGHT / 横屏取 MIN_HEIGHT」修过一次行数被低估的问题，
        // 但那套口径与周课表卡（无条件 MAX 优先）互相矛盾，且判方向依赖进程级 Configuration。
        // W3-3/W3-4：统一改走 currentSizeDp（maxOf + 与方向解耦），两处口径合一。
        // W3-2：resize / 横竖屏切换时系统会把新 options 交给 onAppWidgetOptionsChanged，
        // overrideOptions 就是那条回调带进来的值；有它就用它，避免再去读可能尚未同步的
        // getAppWidgetOptions（回调 与 manager 内部更新之间存在时序窗口）。
        // 一行课程的实际消耗：上下内边距 20 + 标题区 36 + 列表上间距 4 + 行高 44 = 104，
        // 之后每多一行加 48（行高 44 + 行距 4）；两种排列的行高与间距一致，共用同一个公式。
        val options = overrideOptions ?: appWidgetManager.getAppWidgetOptions(appWidgetId)
        val cardHeightDp = currentSizeDp(options, 240)
        val compact = context.getPrefer().getInt(Const.KEY_TODAY_CARD_LAYOUT, 0) == 1
        val maxRows = ((cardHeightDp - 56) / 48).coerceIn(1, 4)
        val maxGridRows = ((cardHeightDp - 56) / 48).coerceIn(1, 3)
        val maxItems = if (compact) maxGridRows * 2 else maxRows

        // v157：与设置页同步改为默认 true——未手动配置时「当天课全上完后卡片转空态」默认生效
        val hideEnded = context.getPrefer().getBoolean(Const.KEY_HIDE_ENDED_COURSE, true)
        val all = if (offset >= 0) WidgetData.getCoursesForOffset(context, offset, tableBean) else emptyList()
        val unfinished = all.filter { it.status != WidgetData.STATUS_FINISHED }
        // 优先展示进行中与未开始；当天已全部上完时，按设置决定是否退化为展示已结束的课
        val showList = when {
            unfinished.isNotEmpty() -> unfinished
            hideEnded -> emptyList()
            else -> all
        }

        // 生日板块：只在「不是手动翻看别的日子 + 已设置生日 + 今天已无未上的课 + 生日在 30 天内」时出现。
        // 智能模式下「今天课全上完」会自动跳到明天，所以这里单独拿今天的课判断，不能只看 offset。
        // 连间距约要 103dp，空间不够就让课程行逐行让位，连一行课程都保不住时干脆不显示板块。
        val birthdayDays = if (!manual && BirthdayUtils.isSet(context)) BirthdayUtils.daysUntil(context) else -1
        // offset==0 时 all 就是今天的课，复用省一次同表同日查询
        val todayPending = if (offset == 0) all.any { it.status != WidgetData.STATUS_FINISHED }
                else WidgetData.getCoursesForOffset(context, 0, tableBean)
                        .any { it.status != WidgetData.STATUS_FINISHED }
        var showBirthday = birthdayDays in 0..30 && !todayPending
        var itemLimit = maxItems
        if (showBirthday) {
            while (itemLimit > 1 && 104 + 48 * (itemLimit - 1) + 103 > cardHeightDp) {
                itemLimit--
            }
            if (104 + 103 > cardHeightDp) {
                showBirthday = false
                // 生日板块最终放不下被隐藏：此前为它让位而压缩掉的课程行必须全部恢复，
                // 否则出现「生日没显示、课也少了一门」的双重丢行（v162 修：预告 2 门只显示 1 门的另一条根因）
                itemLimit = maxItems
            }
        }

        // 递补：showList 已经滤掉上完的课（除非当天全上完、且没开「隐藏已结束课程」），
        // 所以每节课结束的精确闹钟一响，卡片就会把下一门没上的课顶上来，
        // 永远只显示「还没上的前 N 门」；N 由排列方式、卡片当前高度与生日板块共同决定。
        val rowItems = showList.take(itemLimit)
        val empty = rowItems.isEmpty()
        val titleCount = maxOf(unfinished.size, rowItems.size)

        // 标题：今天「今日周四，还有N门课要上」/ 明天「明日周五，共N门课」/ 更远「周一，共N门课」
        if (empty) {
            mRemoteViews.setTextViewTextSize(R.id.tv_headerTitle, TypedValue.COMPLEX_UNIT_SP, 19f)
            mRemoteViews.setTextViewText(R.id.tv_headerTitle, when {
                offset == 1 -> "明日没有课哦"
                offset > 1 -> "这天没有课哦"
                else -> "今日无课程"
            })
            // v146：提示短语已挪到卡片中部的 ll_empty 大字区（v148 起 24sp 居中、casual 手写感字体），
            // 副标题这里必须清空，否则同一句话会在标题下再小字重复一遍。
            mRemoteViews.setTextViewText(R.id.tv_headerSub, "")
        } else {
            mRemoteViews.setTextViewTextSize(R.id.tv_headerTitle, TypedValue.COMPLEX_UNIT_SP, 15f)
            val nStr = "$titleCount"
            val prefix = when {
                offset == 0 -> "今日$weekDayText，还有"
                offset == 1 -> "明日$weekDayText，共"
                else -> "$weekDayText，共"
            }
            val suffix = if (offset == 0) "门课要上" else "门课"
            val primary = ThemeManager.getColor(context, ThemeManager.PRIMARY)
            val span = android.text.SpannableString("$prefix$nStr$suffix")
            val nStart = span.toString().indexOf(nStr)
            if (nStart >= 0) {
                span.setSpan(android.text.style.ForegroundColorSpan(primary), nStart,
                        nStart + nStr.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                span.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), nStart,
                        nStart + nStr.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            mRemoteViews.setTextViewText(R.id.tv_headerTitle, span)

            // 副标：M月D日 / 第N周
            if (weekNo > 0) {
                mRemoteViews.setTextViewText(R.id.tv_headerSub, "$dateText / 第${weekNo}周")
            } else {
                mRemoteViews.setTextViewText(R.id.tv_headerSub, "还没有开学哦")
            }
        }
        mRemoteViews.setTextColor(R.id.tv_headerTitle, titleColor)
        mRemoteViews.setTextColor(R.id.tv_headerSub, subColor)

        // 操作图标颜色
        mRemoteViews.setInt(R.id.iv_next, "setColorFilter", subColor)
        mRemoteViews.setInt(R.id.iv_back, "setColorFilter", subColor)
        // 右上角 Logo（v147）：这里是多彩的「小鹿」品牌符号，**不能**对它调 setColorFilter，
        // 否则整只鹿会被涂成单色灰块（旧版 v146 就是灰的线条图标，所以当时涂了色）。
        // 小鹿自带白色眼窝/耳朵，在白卡与深色卡上对比度都够，无需按主题改色。

        // 显示今天 → 出现"查看明天"；显示其它天 → 出现"返回今天"
        if (showingToday) {
            mRemoteViews.setViewVisibility(R.id.iv_next, View.VISIBLE)
            mRemoteViews.setViewVisibility(R.id.iv_back, View.GONE)
        } else {
            mRemoteViews.setViewVisibility(R.id.iv_next, View.GONE)
            mRemoteViews.setViewVisibility(R.id.iv_back, View.VISIBLE)
        }

        // 课程行：两套静态控件各对应一种排列，只渲染命中的那一套，另一套整体隐藏
        // 行 id 一律用显式数组，不要用 R.id.row_0 + i 这类算术递增
        val nameColor = if (isDark) 0xFFFFFFFF.toInt() else 0xFF1C1C1E.toInt()

        if (compact) {
            // ===== 排列二：紧凑两列（半宽并排，一行两门课，卡片能压到两格） =====
            mRemoteViews.setViewVisibility(R.id.ll_course, View.GONE)
            mRemoteViews.setViewVisibility(R.id.ll_grid, View.VISIBLE)

            val gridIds = intArrayOf(R.id.gcell_0, R.id.gcell_1, R.id.gcell_2,
                    R.id.gcell_3, R.id.gcell_4, R.id.gcell_5)
            val gBgIds = intArrayOf(R.id.gcell_bg_0, R.id.gcell_bg_1, R.id.gcell_bg_2,
                    R.id.gcell_bg_3, R.id.gcell_bg_4, R.id.gcell_bg_5)
            val gBarIds = intArrayOf(R.id.gcell_bar_0, R.id.gcell_bar_1, R.id.gcell_bar_2,
                    R.id.gcell_bar_3, R.id.gcell_bar_4, R.id.gcell_bar_5)
            val gNameIds = intArrayOf(R.id.gcell_name_0, R.id.gcell_name_1, R.id.gcell_name_2,
                    R.id.gcell_name_3, R.id.gcell_name_4, R.id.gcell_name_5)
            val gInfoIds = intArrayOf(R.id.gcell_info_0, R.id.gcell_info_1, R.id.gcell_info_2,
                    R.id.gcell_info_3, R.id.gcell_info_4, R.id.gcell_info_5)
            val gRowIds = intArrayOf(R.id.grow_0, R.id.grow_1, R.id.grow_2)

            for (i in gridIds.indices) {
                if (i < rowItems.size) {
                    val item = rowItems[i]
                    val gc = try {
                        android.graphics.Color.parseColor(item.color)
                    } catch (e: Exception) {
                        0xFF007AFF.toInt()
                    }
                    val ongoing = item.status == WidgetData.STATUS_ONGOING
                    mRemoteViews.setInt(gBgIds[i], "setColorFilter",
                            android.graphics.Color.argb(if (ongoing) 0x3D else 0x24,
                                    android.graphics.Color.red(gc),
                                    android.graphics.Color.green(gc),
                                    android.graphics.Color.blue(gc)))
                    mRemoteViews.setInt(gBarIds[i], "setColorFilter", gc)

                    mRemoteViews.setTextViewText(gNameIds[i], item.courseName)
                    mRemoteViews.setTextColor(gNameIds[i], nameColor)

                    // 一格只有半张卡宽，时间和地点缩成一行
                    val info = buildString {
                        append("${item.startText}-${item.endText}")
                        if (item.room.isNotEmpty()) append("  @${item.room}")
                    }
                    mRemoteViews.setTextViewText(gInfoIds[i], info)
                    mRemoteViews.setTextColor(gInfoIds[i], subColor)
                    mRemoteViews.setViewVisibility(gridIds[i], View.VISIBLE)
                } else {
                    // 空格子用 INVISIBLE 而不是 GONE：保留半宽占位，
                    // 否则同一行左边那一格会被 weight 撑成整行宽
                    mRemoteViews.setViewVisibility(gridIds[i],
                            if (i < itemLimit) View.INVISIBLE else View.GONE)
                }
            }
            // 整行都没课就把整行收起来，不留下多余的 6dp 行距
            for (r in gRowIds.indices) {
                mRemoteViews.setViewVisibility(gRowIds[r],
                        if (r * 2 < rowItems.size) View.VISIBLE else View.GONE)
            }
        } else {
            // ===== 排列一：竖排列表（一行一门课，左侧课名/地点，右侧上课时间） =====
            mRemoteViews.setViewVisibility(R.id.ll_grid, View.GONE)
            mRemoteViews.setViewVisibility(R.id.ll_course, View.VISIBLE)

            val rowIds = intArrayOf(R.id.row_0, R.id.row_1, R.id.row_2, R.id.row_3)
            val bgIds = intArrayOf(R.id.row_bg_0, R.id.row_bg_1, R.id.row_bg_2, R.id.row_bg_3)
            val barIds = intArrayOf(R.id.row_bar_0, R.id.row_bar_1, R.id.row_bar_2, R.id.row_bar_3)
            val nameIds = intArrayOf(R.id.row_name_0, R.id.row_name_1, R.id.row_name_2, R.id.row_name_3)
            val infoIds = intArrayOf(R.id.row_info_0, R.id.row_info_1, R.id.row_info_2, R.id.row_info_3)
            val timeIds = intArrayOf(R.id.row_time_0, R.id.row_time_1, R.id.row_time_2, R.id.row_time_3)
            val endIds = intArrayOf(R.id.row_end_0, R.id.row_end_1, R.id.row_end_2, R.id.row_end_3)
            val badgeIds = intArrayOf(R.id.row_badge_0, R.id.row_badge_1, R.id.row_badge_2, R.id.row_badge_3)

            for (i in rowIds.indices) {
                if (i < rowItems.size) {
                    val item = rowItems[i]
                    val courseColor = try {
                        android.graphics.Color.parseColor(item.color)
                    } catch (e: Exception) {
                        0xFF007AFF.toInt()
                    }
                    val ongoing = item.status == WidgetData.STATUS_ONGOING

                    // 胶囊底：正常 36/255 课程色，进行中更实一点用 61/255
                    mRemoteViews.setInt(bgIds[i], "setColorFilter",
                            android.graphics.Color.argb(if (ongoing) 0x3D else 0x24,
                                    android.graphics.Color.red(courseColor),
                                    android.graphics.Color.green(courseColor),
                                    android.graphics.Color.blue(courseColor)))
                    // 左侧小色条：纯课程色
                    mRemoteViews.setInt(barIds[i], "setColorFilter", courseColor)

                    mRemoteViews.setTextViewText(nameIds[i], item.courseName)
                    mRemoteViews.setTextColor(nameIds[i], nameColor)

                    // 课名下面那行：地点优先，没地点退到老师，都没有就显示节次
                    val subText = when {
                        item.room.isNotEmpty() -> "@${item.room}"
                        item.teacher.isNotEmpty() -> item.teacher
                        else -> "第${item.startNode}-${item.startNode + item.step - 1}节"
                    }
                    mRemoteViews.setTextViewText(infoIds[i], subText)
                    mRemoteViews.setTextColor(infoIds[i], subColor)

                    // 右侧填时间：未开始/已结束的课显示开始时间；
                    // v159 进行中的课改为显示下课（结束）时间——上课时刻已过去，
                    // 用户此刻关心的是「几点下课」
                    mRemoteViews.setTextViewText(timeIds[i],
                            if (ongoing) item.endText else item.startText)
                    mRemoteViews.setTextColor(timeIds[i], nameColor)
                    mRemoteViews.setTextViewText(endIds[i], item.endText)
                    mRemoteViews.setTextColor(endIds[i], subColor)

                    // “正在上”小胶囊：只有进行中的课显示
                    if (ongoing) {
                        mRemoteViews.setViewVisibility(endIds[i], View.GONE)
                        mRemoteViews.setViewVisibility(badgeIds[i], View.VISIBLE)
                        // v159 对比度修复：旧实现字色与底色同为半透明课程色，
                        // 浅色课程（如淡黄）时文字几乎隐形。现改为「课程色实底 +
                        // 亮度自适应字色」：底色亮（淡色系）配深字，底色暗配白字。
                        val lum = (0.299 * android.graphics.Color.red(courseColor) +
                                0.587 * android.graphics.Color.green(courseColor) +
                                0.114 * android.graphics.Color.blue(courseColor)) / 255.0
                        val badgeText = if (lum > 0.6) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()
                        mRemoteViews.setTextColor(badgeIds[i], badgeText)
                        // ★ v142 根因修复备忘：row_badge_* 是 TextView，不能用 setColorFilter(int)
                        //   （方法不存在时 launcher 会应用整张卡失败）。
                        // v160 药丸圆角：setBackgroundColor 只能产生直角底。API 31+ 用
                        // 「outline 圆角 + clipToOutline 裁剪」把直角底裁成药丸形：
                        //   setViewOutlinePreferredRadius（官方 addAction，API 31+）
                        //   setClipToOutline（View 公开方法，带 @RemotableViewMethod，反射安全）
                        // API 31 以下无此能力，保持直角实底（对比度由自适应字色保证）。
                        val badgeBg = android.graphics.Color.argb(0xF2,
                                android.graphics.Color.red(courseColor),
                                android.graphics.Color.green(courseColor),
                                android.graphics.Color.blue(courseColor))
                        if (android.os.Build.VERSION.SDK_INT >= 31) {
                            mRemoteViews.setInt(badgeIds[i], "setBackgroundColor", badgeBg)
                            // 10dp ≈ 胶囊高度一半，四角呈半圆（超出部分系统自动压到半高）
                            mRemoteViews.setViewOutlinePreferredRadius(badgeIds[i], 10f,
                                    android.util.TypedValue.COMPLEX_UNIT_DIP)
                            mRemoteViews.setBoolean(badgeIds[i], "setClipToOutline", true)
                        } else {
                            mRemoteViews.setInt(badgeIds[i], "setBackgroundColor", badgeBg)
                        }
                    } else {
                        mRemoteViews.setViewVisibility(endIds[i], View.VISIBLE)
                        mRemoteViews.setViewVisibility(badgeIds[i], View.GONE)
                    }

                    mRemoteViews.setViewVisibility(rowIds[i], View.VISIBLE)
                } else {
                    mRemoteViews.setViewVisibility(rowIds[i], View.GONE)
                }
            }
        }

        // 生日板块：与课程行二选一之外的“底部预告”，只在生日临近且今天课上完时出现
        if (showBirthday) {
            mRemoteViews.setViewVisibility(R.id.ll_birthday, View.VISIBLE)
            if (isDark) {
                mRemoteViews.setInt(R.id.ll_birthday, "setBackgroundResource", R.drawable.widget_birthday_bg_dark)
            }
            val bMonth = BirthdayUtils.month(context)
            val bDay = BirthdayUtils.day(context)
            mRemoteViews.setTextViewText(R.id.tv_birthday_date, when (birthdayDays) {
                0 -> "就在今天"
                1 -> "明天 · ${bMonth}月${bDay}日"
                else -> "${bMonth}月${bDay}日 · 还有${birthdayDays}天"
            })
            mRemoteViews.setTextColor(R.id.tv_birthday_title, nameColor)
            mRemoteViews.setTextColor(R.id.tv_birthday_date, subColor)
            mRemoteViews.setTextColor(R.id.tv_birthday_text, nameColor)
            mRemoteViews.setTextViewText(R.id.tv_birthday_text,
                    BirthdayUtils.text(context).ifBlank { "写一句给自己 / 给 TA 的话吧~" })
            // 点板块直接进设置页改日期或寄语
            val bPi = PendingIntent.getActivity(context, 6,
                    Intent(context, BirthdayReminderActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            mRemoteViews.setOnClickPendingIntent(R.id.ll_birthday, bPi)
        } else {
            mRemoteViews.setViewVisibility(R.id.ll_birthday, View.GONE)
        }

        // 点击标题：打开 App（与周课表卡共用同一条 PendingIntent，见 openAppPi）
        val pIntent = openAppPi(context)
        mRemoteViews.setOnClickPendingIntent(R.id.tv_headerTitle, pIntent)

        // 点击整个列表区域：先刷新数据再打开 App
        val openPi = PendingIntent.getBroadcast(context, PI_OPEN_APP_REFRESH,
                Intent(context, WidgetUpdateReceiver::class.java).setAction("com.Tangle.timetable.action.WIDGET_OPEN_APP"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        mRemoteViews.setOnClickPendingIntent(R.id.ll_course, openPi)
        // 头部「打开课表」胶囊（v146 由原底部 tv_emptyAction 大按钮迁移而来；
        // v153 定稿位置：夹在「翻页箭头」与「Logo」之间）：常驻显示，
        // 不再随有没有课切换显隐，这样任何时候都有一个明确的手动入口。
        // 功能与文字完全不变，只调展示顺序。
        mRemoteViews.setOnClickPendingIntent(R.id.tv_openApp, openPi)

        // 空态区（v146）：当天无课时，把提示短语放大居中显示。
        // 原来是塞在 tv_headerSub 里的 11sp 小字，现在走独立区域，
        // 字号 / 字距 / 行距由布局 XML 决定，代码侧只填文字与显隐，不碰字号字体。
        //
        // ⚠️ 关于「自定义字体」的最终结论（v150 查明，2026-09-15 真机复验）：
        //   小部件**无法**使用任何自定义字体，这不是漏改，是平台硬限制，三条路全试过：
        //     ① fontFamily="casual"  → 本机 fonts.xml 映射 ComingSoon.ttf，实测只有 227 个字符
        //     ② fontFamily="cursive" → 映射 DancingScript-Regular.ttf，实测只有 559 个字符
        //        （两者 CJK 区 U+4E00~U+9FFF 覆盖数均为 0，中文必然回落系统黑体）
        //     ③ 内嵌 @font/yozai_widget（悠哉手写体子集 3.77MB，aapt 已确认进包、fontFamily
        //        已编成资源引用）→ 真机仍渲染系统黑体。原因：Android 12 起 AppWidget 在
        //        launcher 独立进程渲染，会忽略 layout 里的自定义 fontFamily；
        //        且 RemoteViews 的 setTypeface **没有** @RemotableViewMethod 注解，代码侧也调不了。
        //   唯一理论出路是把文字用 Canvas 画进 Bitmap 再 setImageViewBitmap，
        //   但那会踩红线 2（>1MB 位图导致整次 updateAppWidget 被系统静默丢弃），故放弃。
        //   → 字体文件已删除（回收 3.9MB 包体），「随性」气质改由排版承担：
        //     24sp 大字 + letterSpacing 0.03 + lineSpacingExtra 6dp + 居中留白 + 不加粗。
        // 有课时整块隐藏，把高度让给课程行。
        mRemoteViews.setViewVisibility(R.id.ll_empty, if (empty) View.VISIBLE else View.GONE)
        if (empty) {
            mRemoteViews.setTextViewText(R.id.tv_emptyPhrase, WidgetData.getIdlePhrase(context))
            mRemoteViews.setTextColor(R.id.tv_emptyPhrase, titleColor)
        }

        // v146：右上角刷新图标已按用户要求删除，刷新改由以下三条路径覆盖：
        //   ① 每节课开始前 10 分钟 / 结束时刻的精确闹钟
        //   ② 每日 20:00 的预告放行闹钟（WidgetScheduler.schedulePreviewStart）
        //   ③ 30 分钟 WorkManager 兜底 + 点击卡片头部（pIntent）/点击「打开课表」

        // 手动临时查看明日/今日（闹钟触发的自动刷新会覆盖回智能模式）
        // v153：iv_next / iv_back 在布局中移到最左侧（Logo 已回到最右收边），
        // 但 id、点击行为、显隐逻辑与 PendingIntent 完全不变。
        val i = Intent(context, TodayCourseAppWidget::class.java)
        i.action = "WAKEUP_NEXT_DAY"
        val pi = PendingIntent.getBroadcast(context, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        mRemoteViews.setOnClickPendingIntent(R.id.iv_next, pi)

        val backIntent = Intent(context, TodayCourseAppWidget::class.java)
        backIntent.action = "WAKEUP_BACK_TIME"
        val backPi = PendingIntent.getBroadcast(context, 2, backIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        mRemoteViews.setOnClickPendingIntent(R.id.iv_back, backPi)

        appWidgetManager.updateAppWidget(appWidgetId, mRemoteViews)
        } catch (t: Throwable) {
            // 渲染抛异常时也要把布局默认的「加载中…」顶掉：推一张最小空态卡，
            // 等下一次闹钟 / 亮屏 / 30 分钟兜底触发时再自愈。
            // v146：原来靠「空态大按钮 + 刷新图标」做重试入口，两者都已被删除；
            // 现在改为：隐藏课程行/网格/生日块，显示 ll_empty 并把文字换成「刷新失败」。
            // W3-5：兜底卡是**新建**的 RemoteViews，不会继承正常路径的 click 设置，
            // 原来只写了「点这里重试」「点卡片头部重试」两行文案却**没有 setOnClickPendingIntent**，
            // 整张卡没有任何可点区域 → 文案与能力不一致。这里补上头部标题与空态区两条点击。
            // PI 复用 openAppPi（rc=PI_OPEN_APP 的 SplashActivity getActivity），
            // 与正常路径的「点标题打开 App」是同一条记录，不新增槽位。
            try {
                val rv = RemoteViews(context.packageName, R.layout.today_course_app_widget)
                rv.setTextViewText(R.id.tv_headerTitle, "今日课程")
                rv.setTextViewText(R.id.tv_headerSub, "点这里重试")
                for (rid in intArrayOf(R.id.row_0, R.id.row_1, R.id.row_2, R.id.row_3,
                        R.id.grow_0, R.id.grow_1, R.id.grow_2, R.id.ll_birthday)) {
                    rv.setViewVisibility(rid, View.GONE)
                }
                rv.setViewVisibility(R.id.ll_empty, View.VISIBLE)
                rv.setTextViewText(R.id.tv_emptyPhrase, "刷新失败")
                rv.setTextViewText(R.id.tv_emptyHint, "点卡片头部重试")
                rv.setViewVisibility(R.id.tv_emptyHint, View.VISIBLE)
                val retryPi = openAppPi(context)
                rv.setOnClickPendingIntent(R.id.tv_headerTitle, retryPi)
                rv.setOnClickPendingIntent(R.id.ll_empty, retryPi)
                appWidgetManager.updateAppWidget(appWidgetId, rv)
            } catch (t2: Throwable) {
                // 兜底也失败就只记日志，等下一次触发
            }
        }
    }

    /**
     * “下一节课”小部件渲染（静态 RemoteViews，随主题主色变色）。
     */
    fun refreshNextWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val rv = RemoteViews(context.packageName, R.layout.next_course_app_widget)
        val next = WidgetData.getNextCourse(context)
        val primary = ThemeManager.getColor(context, ThemeManager.PRIMARY)
        rv.setInt(R.id.next_root, "setBackgroundColor", primary)

        if (next == null) {
            rv.setTextViewText(R.id.tv_next_name, "近期无课程")
            rv.setTextViewText(R.id.tv_next_info, "好好休息~")
            rv.setTextViewText(R.id.tv_next_countdown, "")
        } else {
            rv.setTextViewText(R.id.tv_next_name, next.courseName)
            val info = buildString {
                append("${next.startText}-${next.endText}")
                if (next.room.isNotEmpty()) append("  @${next.room}")
            }
            rv.setTextViewText(R.id.tv_next_info, info)
            val mins = next.minutesUntilStart
            // 今天还有未开始的课时不再误标「明天」：按 startMillis 落在哪一天判断
            val nowCal = java.util.Calendar.getInstance()
            val startCal = java.util.Calendar.getInstance().apply { timeInMillis = next.startMillis }
            val sameDay = nowCal.get(java.util.Calendar.YEAR) == startCal.get(java.util.Calendar.YEAR) &&
                    nowCal.get(java.util.Calendar.DAY_OF_YEAR) == startCal.get(java.util.Calendar.DAY_OF_YEAR)
            rv.setTextViewText(R.id.tv_next_countdown,
                    when {
                        mins in 0..120 -> "还有${mins}分钟上课"
                        sameDay -> "今天 ${next.startText}"
                        else -> "明天 ${next.startText}"
                    })
        }

        // 点击：先刷新再打开 App。
        // W2-06：requestCode 由裸 appWidgetId 改为 PI_NEXT_OPEN_APP_BASE + appWidgetId。
        // 原来它与今日卡列表区的固定槽位 3 同处 getBroadcast 命名空间，且两条 Intent 完全相同
        // （WidgetUpdateReceiver + WIDGET_OPEN_APP）→ 只要某个「下一节课」实例的 appWidgetId
        // 恰好为 3 就命中同一条 PendingIntent 记录，任一方将来加 extras 就会静默串台。
        val openPi = PendingIntent.getBroadcast(context, PI_NEXT_OPEN_APP_BASE + appWidgetId,
                Intent(context, WidgetUpdateReceiver::class.java).setAction("com.Tangle.timetable.action.WIDGET_OPEN_APP"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.next_root, openPi)

        appWidgetManager.updateAppWidget(appWidgetId, rv)
    }
}
