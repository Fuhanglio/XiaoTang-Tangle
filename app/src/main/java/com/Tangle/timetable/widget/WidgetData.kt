package com.Tangle.timetable.widget
import android.util.Log

import android.content.Context
import com.Tangle.timetable.AppDatabase
import com.Tangle.timetable.R
import com.Tangle.timetable.bean.CourseBean
import com.Tangle.timetable.bean.TableBean
import com.Tangle.timetable.bean.TimeDetailBean
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.CourseUtils
import com.Tangle.timetable.utils.getPrefer
import java.util.Calendar

/**
 * 小部件课程数据计算：按时间计算今天/明天每节课的状态（已结束/进行中/未开始）、
 * 上课/下课时间戳、距下一节课分钟数等。全部使用同步 DAO，可在 Receiver/Service 中调用。
 */
object WidgetData {
    private const val TAG = "WidgetData"

    const val STATUS_FINISHED = 0
    const val STATUS_ONGOING = 1
    const val STATUS_UPCOMING = 2

    data class Item(
            val courseName: String,
            val room: String,
            val teacher: String,
            val startText: String,
            val endText: String,
            val startNode: Int,
            val step: Int,
            val color: String,
            val startMillis: Long,
            val endMillis: Long,
            val status: Int,
            /** 距上课还有多少分钟（仅未开始有意义） */
            val minutesUntilStart: Int
    )

    /**
     * 获取某天（今天或明天）的课程列表，带时间状态。
     * @param nextDay true=明天
     * @param table   指定课表；为 null 时取默认课表（保持既有调用处兼容）
     */
    fun getDayCourses(context: Context, nextDay: Boolean = false, table: TableBean? = null): List<Item> {
        return getCoursesForOffset(context, if (nextDay) 1 else 0, table)
    }

    /**
     * 获取距今 dayOffset 天（0=今天、1=明天、可往后任意天）的课程列表，带时间状态。
     * 会按目标日所属周次重新判定单双周；周次越界（未开学或超出学期总周数）时返回空列表。
     */
    fun getCoursesForOffset(context: Context, dayOffset: Int, table: TableBean? = null): List<Item> {
        return try {
            val db = AppDatabase.getDatabase(context)
            val t: TableBean = table ?: db.tableDao().getDefaultTableSync() ?: return emptyList()
            val week = weekForOffset(context, dayOffset, t)
            // 周次越界：直接返回空，不再查数据库
            if (week <= 0 || week > t.maxWeek) return emptyList()
            val day = weekdayIntForOffset(dayOffset)
            val type = if (week % 2 == 0) 2 else 1
            val courses: List<CourseBean> = db.courseDao()
                    .getCourseByDayOfTableSync(day, week, type, t.id)
                    .sortedBy { it.startNode }
            if (courses.isEmpty()) return emptyList()

            val times: List<TimeDetailBean> = db.timeDetailDao().getTimeListSync(t.timeTable)
            val now = System.currentTimeMillis()
            val future = dayOffset > 0
            courses.mapNotNull { c ->
                val startNode = c.startNode
                if (startNode <= 0 || startNode > times.size) return@mapNotNull null
                val endNode = (startNode + c.step - 1).coerceAtMost(times.size)
                val start = times[startNode - 1].startTime
                val end = times[endNode - 1].endTime
                val startMillis = timeToMillis(start, dayOffset) ?: run { Log.w(TAG, "无法解析时间: $start"); return@mapNotNull null }
                val endMillis = timeToMillis(end, dayOffset) ?: run { Log.w(TAG, "无法解析时间: $end"); return@mapNotNull null }
                val status = when {
                    now >= endMillis -> STATUS_FINISHED
                    now in startMillis..endMillis -> STATUS_ONGOING
                    else -> STATUS_UPCOMING
                }
                Item(
                        courseName = c.courseName,
                        room = c.room ?: "",
                        teacher = c.teacher ?: "",
                        startText = start,
                        endText = end,
                        startNode = startNode,
                        step = c.step,
                        color = if (c.color.isEmpty()) "#007AFF" else c.color,
                        startMillis = startMillis,
                        endMillis = endMillis,
                        status = if (future) STATUS_UPCOMING else status,
                        minutesUntilStart = ((startMillis - now) / 60000L).toInt()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 距今 dayOffset 天所在那一周的周次（与 CourseUtils.countWeek 同口径，跨周自动进位） */
    fun weekForOffset(context: Context, dayOffset: Int, table: TableBean? = null): Int {
        return try {
            val t: TableBean = table ?: AppDatabase.getDatabase(context).tableDao().getDefaultTableSync()
                    ?: return -1
            // daysBetween 给出「本周首日 与 startDate 相差的天数」，再按目标日在一周里的位置补周
            val startDiff = CourseUtils.daysBetween(t.startDate, false, t.sundayFirst)
            val cal = Calendar.getInstance()
            cal.firstDayOfWeek = if (t.sundayFirst) Calendar.SUNDAY else Calendar.MONDAY
            val todayIdxInWeek = ((cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek) + 7) % 7
            val weekShift = (todayIdxInWeek + dayOffset) / 7
            startDiff / 7 + 1 + weekShift
        } catch (e: Exception) {
            -1
        }
    }

    /** 距今 dayOffset 天的星期序号：1=周一 ... 7=周日 */
    fun weekdayIntForOffset(dayOffset: Int): Int {
        return (((CourseUtils.getWeekdayInt() - 1 + dayOffset) % 7) + 7) % 7 + 1
    }

    /** 距今 dayOffset 天的星期文字，如「周四」 */
    fun weekdayTextForOffset(dayOffset: Int): String = CourseUtils.getDayStr(weekdayIntForOffset(dayOffset))

    /** 距今 dayOffset 天的日期文字，如「9月11日」 */
    fun dateTextForOffset(dayOffset: Int): String {
        return try {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, dayOffset)
            java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA).format(cal.time)
        } catch (e: Exception) {
            ""
        }
    }

    /** 今日视图：进行中 + 接下来未开始的课（最多 4 节）；已结束的根据设置隐藏或置灰 */
    fun getTodayVisible(context: Context, hideEnded: Boolean): List<Item> {
        val all = getDayCourses(context, false)
        val result = arrayListOf<Item>()
        for (c in all) {
            if (c.status == STATUS_FINISHED) {
                if (!hideEnded) result.add(c)
            } else {
                result.add(c)
            }
            if (result.size >= 4 && c.status != STATUS_FINISHED) break
        }
        return result.take(4)
    }

    /** 下一节课：今天还没上的第一节约；今天没有则明天第一节；都没有返回 null */
    fun getNextCourse(context: Context, table: TableBean? = null): Item? {
        val today = getDayCourses(context, false, table).firstOrNull { it.status == STATUS_UPCOMING }
        if (today != null) return today
        val tomorrow = getDayCourses(context, true, table).firstOrNull()
        return tomorrow
    }

    /**
     * 今日小部件空态文案：
     * 今明暂无课程，好好休息~ / 今日没有课哦 / 明天没有课哦 / 今日课程已结束。
     * @param nextDay 当前展示的是否为明天
     */
    fun getTodayEmptyText(context: Context, nextDay: Boolean): String {
        val all = getDayCourses(context, nextDay)
        val tomorrowEmpty = nextDay || getDayCourses(context, true).isEmpty()
        val todayEmpty = !nextDay && all.isEmpty()
        return when {
            todayEmpty && tomorrowEmpty -> "今明暂无课程，好好休息~"
            all.isEmpty() && !nextDay -> "今日没有课哦"
            nextDay && all.isEmpty() -> "明天没有课哦"
            all.none { it.status != STATUS_FINISHED } && !nextDay -> "今日课程已结束"
            else -> ""
        }
    }

    // ============ 今日/明日/之后若干天的智能判定 ============

    enum class DayMode { TODAY, TOMORROW, LATER, BOTH_EMPTY }

    /**
     * 课程预告（自动跳到明天及以后）的放行时刻：当天 20 点前一律停在今天。
     * public 供 WidgetScheduler 注册同一时刻的放行闹钟（单一事实来源，别在两处各写一个 20）。
     */
    const val PREVIEW_START_HOUR = 20

    /**
     * 智能日计划：今天还有没上完的课 → 显示今天；
     * 否则往后找最近一个有课的日子（跳过没课的日子，可跨周，最多 7 天）；
     * 7 天内都没有 → 空态（dayOffset = -1）。同步方法，可在 Receiver 中调用。
     *
     * 预告门槛（2026-09-15 加）：**当天 20 点前不允许预告**，只能停在今天。
     * 原因：原实现只看「今天还有没有没上完的课」，当天本来就没课时 count 直接为 0，
     * 于是上午/中午就把卡片切去了明天甚至更远，把「今日无课程 + 预设提示短语」的空态盖掉，
     * 违背「当天无课要显示提示短语」的设计。加门槛后：
     * - 20 点前：循环只在 d=0 上找，找不到就回落空态 → 显示提示短语；
     * - 20 点后：当天课必然都已结束（count 为 0），才允许往后找最近有课的一天做预告。
     */
    data class DayPlan(
            val mode: DayMode,
            /** 最终应展示的天：0=今天、1=明天、N=N 天后、-1=今起 7 天都没课 */
            val dayOffset: Int,
            /** 展示当天的课程总数（供「共N门课」标题） */
            val total: Int,
            /** 展示当天的周次（越界记 -1） */
            val week: Int,
            /** 展示当天星期文字（周X） */
            val weekDayText: String,
            /** 展示当天日期（M月D日） */
            val dateText: String
    )

    fun getSmartDayPlan(context: Context, table: TableBean? = null): DayPlan {
        return try {
            val db = AppDatabase.getDatabase(context)
            val t: TableBean = table ?: db.tableDao().getDefaultTableSync()
                    ?: return DayPlan(DayMode.BOTH_EMPTY, -1, 0, -1,
                            CourseUtils.getWeekday(false), CourseUtils.getTodayDate())

            // 今天还有没上完的课 → 今天；否则往后找最近一个有课的日子（可跨周）。
            // 「提前 N 天」= 从今天起一共看 N 天，所以往后最多看 N-1 天。
            // 例：提前 2 天 = 只看今天和明天；提前 7 天 = 今天 ~ 6 天后。
            val previewDays = if (context.getPrefer().getInt(Const.KEY_WIDGET_PREVIEW_DAYS, 7) == 2) 2 else 7
            val maxOffset = previewDays - 1
            // 20 点前不给预告：canPreview = false 时循环退化为「只看今天」，
            // 今天没课（或课上完）就自然落到下面的 offset < 0 空态，交给调用方显示提示短语。
            val canPreview = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= PREVIEW_START_HOUR
            val scanMax = if (canPreview) maxOffset else 0
            var offset = -1
            var total = 0
            for (d in 0..scanMax) {
                val list = getCoursesForOffset(context, d, t)
                val count = if (d == 0) list.count { it.status != STATUS_FINISHED } else list.size
                if (count > 0) {
                    offset = d
                    total = count
                    break
                }
            }
            if (offset < 0) {
                return DayPlan(DayMode.BOTH_EMPTY, -1, 0, -1,
                        CourseUtils.getWeekday(false), CourseUtils.getTodayDate())
            }

            // 周次按 maxWeek 钳制：越界记 -1，交给副标显示提示文案，绝不显示“第340周”这种值
            val rawWeek = weekForOffset(context, offset, t)
            val week = if (rawWeek <= 0 || rawWeek > t.maxWeek) -1 else rawWeek

            DayPlan(
                    mode = when (offset) {
                        0 -> DayMode.TODAY
                        1 -> DayMode.TOMORROW
                        else -> DayMode.LATER
                    },
                    dayOffset = offset,
                    total = total,
                    week = week,
                    weekDayText = weekdayTextForOffset(offset),
                    dateText = dateTextForOffset(offset)
            )
        } catch (e: Exception) {
            DayPlan(DayMode.BOTH_EMPTY, -1, 0, -1, CourseUtils.getWeekday(false), CourseUtils.getTodayDate())
        }
    }

    // ============ 周课表小部件：一周概览（按天列表） ============

    /** 一周概览里的一天 */
    data class WeekRow(
            /** 1=周一 ... 7=周日 */
            val dayIndex: Int,
            /** "周一" ~ "周日" */
            val dayLabel: String,
            /** "9.7" */
            val dateText: String,
            /** 当天课程名，多个用「、」连接；无课为空串 */
            val courses: String,
            /** 当天第一门课的颜色；无课为空串 */
            val color: String,
            val isToday: Boolean
    )

    data class WeekPlan(
            val rows: List<WeekRow>,
            /** "9月7日 - 9月13日" */
            val rangeText: String,
            /** 实际取到的周次 */
            val week: Int,
            /** 还没开学（传入的周次小于等于 0） */
            val notStarted: Boolean,
            /** 已超出学期总周数，视为假期 */
            val over: Boolean
    )

    /**
     * 一周概览：把一周里每天的课程名汇总成一行，供周课表小部件渲染列表。
     * week <= 0 时按第 1 周处理并标记 notStarted；week > table.maxWeek 时标记 over 并返回空列表（假期不查库）。
     * 行顺序固定为周一 → 周日，是否出现周六/周日由 table.showSat / table.showSun 决定。
     */
    fun getWeekPlan(context: Context, week: Int, table: TableBean? = null): WeekPlan {
        return try {
            val db = AppDatabase.getDatabase(context)
            val t: TableBean = table ?: db.tableDao().getDefaultTableSync()
                    ?: return WeekPlan(emptyList(), "", week, false, false)
            val notStarted = week <= 0
            val real = if (notStarted) 1 else week
            if (real > t.maxWeek) {
                return WeekPlan(emptyList(), "", real, notStarted, true)
            }
            val type = if (real % 2 == 0) 2 else 1
            val curWeek = CourseUtils.countWeek(t.startDate, t.sundayFirst)
            val dates = CourseUtils.getDateStringFromWeek(curWeek, real, t.sundayFirst)
            // dates[0] 是本周首日的月份，dates[1..7] 是这七天的「日」
            val dayNumbers = IntArray(8)
            for (i in 1..7) {
                dayNumbers[i] = dates.getOrNull(i)?.toIntOrNull() ?: 0
            }
            var month = dates.getOrNull(0)?.toIntOrNull() ?: 1
            val months = IntArray(8)
            months[1] = month
            for (i in 2..7) {
                if (dayNumbers[i] < dayNumbers[i - 1]) month += 1
                if (month > 12) month -= 12   // 跨年：12 月之后是 1 月，不能显示 13 月
                months[i] = month
            }
            val todayIndex = CourseUtils.getWeekdayInt()
            val rows = arrayListOf<WeekRow>()
            for (d in 1..7) {
                if (d == 6 && !t.showSat) continue
                if (d == 7 && !t.showSun) continue
                val idx = if (t.sundayFirst) {
                    if (d == 7) 1 else d + 1
                } else {
                    d
                }
                val courses = db.courseDao()
                        .getCourseByDayOfTableSync(d, real, type, t.id)
                        .sortedBy { it.startNode }
                rows.add(WeekRow(
                        dayIndex = d,
                        dayLabel = CourseUtils.getDayStr(d),
                        dateText = "${months[idx]}.${dayNumbers[idx]}",
                        courses = courses.joinToString("、") { it.courseName },
                        color = courses.firstOrNull()?.let {
                            if (it.color.isEmpty()) "#007AFF" else it.color
                        } ?: "",
                        isToday = d == todayIndex
                ))
            }
            WeekPlan(
                    rows = rows,
                    rangeText = "${months[1]}月${dayNumbers[1]}日 - ${months[7]}月${dayNumbers[7]}日",
                    week = real,
                    notStarted = notStarted,
                    over = false
            )
        } catch (e: Exception) {
            WeekPlan(emptyList(), "", week, false, false)
        }
    }

    /**
     * 今日小部件空态（当天课全上完、或当天本来没课）时显示的短句。
     * 取自 res/values/strings.xml 的 widget_idle_phrases，每句都在 10 字以内。
     * 用「年 + 年内第几天」做种子：同一天内刷新多少次都是同一句，跨天才换，避免跳字。
     */
    fun getIdlePhrase(context: Context): String {
        return try {
            val phrases = context.resources.getStringArray(R.array.widget_idle_phrases)
            if (phrases.isEmpty()) return ""
            val cal = Calendar.getInstance()
            val seed = cal.get(Calendar.YEAR) * 400 + cal.get(Calendar.DAY_OF_YEAR)
            phrases[seed % phrases.size]
        } catch (e: Exception) {
            ""
        }
    }

    /** hhmm 转成「距今 dayOffset 天」那天的毫秒时间戳 */
    private fun timeToMillis(hhmm: String, dayOffset: Int): Long? {
        return try {
            val parts = hhmm.split(":")
            val cal = Calendar.getInstance()
            if (dayOffset != 0) cal.add(Calendar.DAY_OF_YEAR, dayOffset)
            cal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            cal.set(Calendar.MINUTE, parts[1].toInt())
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (e: Exception) {
            null
        }
    }
}









