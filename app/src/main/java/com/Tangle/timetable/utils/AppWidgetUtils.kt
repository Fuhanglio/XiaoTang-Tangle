package com.Tangle.timetable.utils

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import android.content.Intent
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun BroadcastReceiver.goAsync(
        coroutineScope: CoroutineScope = GlobalScope,
        block: suspend () -> Unit
) {
    val result = goAsync()
    coroutineScope.launch {
        try {
            block()
        } catch (t: Throwable) {
            // 关键保险：刷新里的任何异常都不能变成未捕获异常杀掉进程，
            // 否则小部件会停在布局默认的「加载中…」，直到下一个触发点才自愈
            Log.e("WidgetAsync", "widget refresh failed", t)
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
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val cardHeightDp = when {
            maxHeight > 0 -> maxHeight
            minHeight > 0 -> minHeight
            else -> 200
        }
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

        // 点击标题或列表区域：打开 App
        val intent = Intent(context, SplashActivity::class.java)
        val pIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        mRemoteViews.setOnClickPendingIntent(R.id.tv_weekTitle, pIntent)
        mRemoteViews.setOnClickPendingIntent(R.id.ll_week, pIntent)

        // 上一周 / 下一周
        val nextIntent = Intent(context, ScheduleAppWidget::class.java)
        nextIntent.action = "WAKEUP_NEXT_WEEK"
        val pi = PendingIntent.getBroadcast(context, 1, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        mRemoteViews.setOnClickPendingIntent(R.id.iv_next, pi)

        val backIntent = Intent(context, ScheduleAppWidget::class.java)
        backIntent.action = "WAKEUP_BACK_WEEK"
        val backPi = PendingIntent.getBroadcast(context, 2, backIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        mRemoteViews.setOnClickPendingIntent(R.id.iv_back, backPi)

        // 刷新图标
        val refreshPi = PendingIntent.getBroadcast(context, 4,
                Intent(context, WidgetUpdateReceiver::class.java).setAction(WidgetScheduler.ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT)
        mRemoteViews.setOnClickPendingIntent(R.id.iv_refresh, refreshPi)

        // “+”：直接打开添加课程页，临时加一两节课不用再走教务导入
        val addIntent = Intent(context, AddCourseActivity::class.java).apply {
            putExtra("tableId", tableBean.id)
            putExtra("maxWeek", tableBean.maxWeek)
            putExtra("nodes", tableBean.nodes)
            putExtra("id", -1)
        }
        val addPi = PendingIntent.getActivity(context, 5, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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
    fun refreshTodayWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, tableBean: TableBean, nextDay: Boolean = false, manual: Boolean = false) {
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
        // 竖屏下 OPTION_APPWIDGET_MIN_HEIGHT 就是卡片当前高度（官方语义），MAX_HEIGHT 是横屏时的值，
        // 所以优先用 MIN_HEIGHT；有的桌面 resize 后不回传，才退回 MAX_HEIGHT，都没有就按三格估。
        // 一行课程的实际消耗：上下内边距 20 + 标题区 36 + 列表上间距 4 + 行高 44 = 104，
        // 之后每多一行加 48（行高 44 + 行距 4）；两种排列的行高与间距一致，共用同一个公式。
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        val reportedHeight = if (minHeight > 0) minHeight else maxHeight
        val cardHeightDp = if (reportedHeight in 1..2000) reportedHeight else 240
        val compact = context.getPrefer().getInt(Const.KEY_TODAY_CARD_LAYOUT, 0) == 1
        val maxRows = ((cardHeightDp - 56) / 48).coerceIn(1, 4)
        val maxGridRows = ((cardHeightDp - 56) / 48).coerceIn(1, 3)
        val maxItems = if (compact) maxGridRows * 2 else maxRows

        val hideEnded = context.getPrefer().getBoolean(Const.KEY_HIDE_ENDED_COURSE, false)
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
        val todayPending = WidgetData.getCoursesForOffset(context, 0, tableBean)
                .any { it.status != WidgetData.STATUS_FINISHED }
        var showBirthday = birthdayDays in 0..30 && !todayPending
        var itemLimit = maxItems
        if (showBirthday) {
            while (itemLimit > 1 && 104 + 48 * (itemLimit - 1) + 103 > cardHeightDp) {
                itemLimit--
            }
            if (104 + 103 > cardHeightDp) showBirthday = false
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
            mRemoteViews.setTextViewText(R.id.tv_headerSub, WidgetData.getIdlePhrase(context))
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
        mRemoteViews.setInt(R.id.iv_refresh, "setColorFilter", subColor)

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

                    // 右侧填时间：开始时间加粗，结束时间在下（进行中时换成「正在上」小胶囊）
                    mRemoteViews.setTextViewText(timeIds[i], item.startText)
                    mRemoteViews.setTextColor(timeIds[i], nameColor)
                    mRemoteViews.setTextViewText(endIds[i], item.endText)
                    mRemoteViews.setTextColor(endIds[i], subColor)

                    // “正在上”小胶囊：只有进行中的课显示
                    if (ongoing) {
                        mRemoteViews.setViewVisibility(endIds[i], View.GONE)
                        mRemoteViews.setViewVisibility(badgeIds[i], View.VISIBLE)
                        mRemoteViews.setTextColor(badgeIds[i], courseColor)
                        mRemoteViews.setInt(badgeIds[i], "setColorFilter",
                                android.graphics.Color.argb(0x2E,
                                        android.graphics.Color.red(courseColor),
                                        android.graphics.Color.green(courseColor),
                                        android.graphics.Color.blue(courseColor)))
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

        val intent = Intent(context, SplashActivity::class.java)
        val pIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        mRemoteViews.setOnClickPendingIntent(R.id.tv_headerTitle, pIntent)

        // 点击整个列表区域：先刷新数据再打开 App
        val openPi = PendingIntent.getBroadcast(context, 3,
                Intent(context, WidgetUpdateReceiver::class.java).setAction("com.Tangle.timetable.action.WIDGET_OPEN_APP"),
                PendingIntent.FLAG_UPDATE_CURRENT)
        mRemoteViews.setOnClickPendingIntent(R.id.ll_course, openPi)
        // 空态按钮：同样先刷新再打开 App；显隐由本次有没有课决定
        mRemoteViews.setOnClickPendingIntent(R.id.tv_emptyAction, openPi)
        mRemoteViews.setViewVisibility(R.id.tv_emptyAction, if (empty) View.VISIBLE else View.GONE)

        // 右上角刷新图标：立即更新（回到自动模式）
        val refreshPi = PendingIntent.getBroadcast(context, 4,
                Intent(context, WidgetUpdateReceiver::class.java).setAction(WidgetScheduler.ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT)
        mRemoteViews.setOnClickPendingIntent(R.id.iv_refresh, refreshPi)

        // 手动临时查看明日/今日（闹钟触发的自动刷新会覆盖回智能模式）
        val i = Intent(context, TodayCourseAppWidget::class.java)
        i.action = "WAKEUP_NEXT_DAY"
        val pi = PendingIntent.getBroadcast(context, 1, i, PendingIntent.FLAG_UPDATE_CURRENT)
        mRemoteViews.setOnClickPendingIntent(R.id.iv_next, pi)

        val backIntent = Intent(context, TodayCourseAppWidget::class.java)
        backIntent.action = "WAKEUP_BACK_TIME"
        val backPi = PendingIntent.getBroadcast(context, 2, backIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        mRemoteViews.setOnClickPendingIntent(R.id.iv_back, backPi)

        appWidgetManager.updateAppWidget(appWidgetId, mRemoteViews)
        } catch (t: Throwable) {
            // 渲染抛异常时也要把布局默认的「加载中…」顶掉：推一张最小空态卡，
            // 等下一次闹钟 / 亮屏 / 30 分钟兜底触发时再自愈
            try {
                val rv = RemoteViews(context.packageName, R.layout.today_course_app_widget)
                rv.setTextViewText(R.id.tv_headerTitle, "今日课程")
                rv.setTextViewText(R.id.tv_headerSub, "刷新失败，点这里重试")
                for (rid in intArrayOf(R.id.row_0, R.id.row_1, R.id.row_2, R.id.row_3,
                        R.id.grow_0, R.id.grow_1, R.id.grow_2, R.id.ll_birthday)) {
                    rv.setViewVisibility(rid, View.GONE)
                }
                rv.setViewVisibility(R.id.tv_emptyAction, View.VISIBLE)
                val refreshPi = PendingIntent.getBroadcast(context, 4,
                        Intent(context, WidgetUpdateReceiver::class.java).setAction(WidgetScheduler.ACTION_REFRESH),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                rv.setOnClickPendingIntent(R.id.iv_refresh, refreshPi)
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

        // 点击：先刷新再打开 App
        val openPi = PendingIntent.getBroadcast(context, appWidgetId,
                Intent(context, WidgetUpdateReceiver::class.java).setAction("com.Tangle.timetable.action.WIDGET_OPEN_APP"),
                PendingIntent.FLAG_UPDATE_CURRENT)
        rv.setOnClickPendingIntent(R.id.next_root, openPi)

        appWidgetManager.updateAppWidget(appWidgetId, rv)
    }
}
