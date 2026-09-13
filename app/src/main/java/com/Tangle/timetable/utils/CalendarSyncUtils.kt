package com.Tangle.timetable.utils

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.Tangle.timetable.bean.CourseBean
import com.Tangle.timetable.bean.TableBean
import com.Tangle.timetable.bean.TimeDetailBean
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 把整学期课表写进系统日历，一共写两类日程：
 *
 * 1. **每节课**：每周循环的日程，带「提前 15 分钟」提醒；
 * 2. **每日课表**：每天一条全天日程（标题「今日课表 · N 节」，备注列出当天所有课），
 *    让系统日历按天看、负一屏的「日程」卡片，一眼就能看到这一天要上什么。
 *
 * 几个关键设计：
 * 1. 每条日程的备注第一行是标记（每节课 `[小唐Tangle#课表id]`、日课表 `[小唐Tangle#日课#课表id]`），
 *    每次同步前先按标记把上一次写进去的整批删掉，所以反复同步不会堆重复，天然幂等。
 * 2. 连续周用一条 `FREQ=WEEKLY;COUNT=n` 的循环日程表示，单双周用 `FREQ=WEEKLY;INTERVAL=2;COUNT=n`，
 *    一门课通常只占 1 条日程，不会把日历塞满。
 * 3. 安卓规定：**循环日程必须用 DURATION，不能给 DTEND**（给了也会被忽略），
 *    所以这里做了分支，只有「只上一周」的日程才走 DTEND。
 * 4. 全天日程（每日课表）必须用 **UTC 时区 + 午夜边界**，否则日历里日期会错位一天。
 * 5. **新版 ColorOS/OPPO 日历把数据搬进了私有 Provider**（AUTHORITY = com.coloros.calendar），
 *    原生 com.android.calendar 在新机型上基本是空库，直接查会得到「没有可写入的日历」。
 *    所以这里先探测用哪个库：OPPO 私有库优先、原生兜底，读写全程走同一个库。
 */
object CalendarSyncUtils {

    /** 提前多少分钟提醒上课 */
    private const val REMINDER_MINUTES = 15

    /**
     * 日历 Provider 的 AUTHORITY 候选，按优先级排：
     * OPPO 官方文档（open.oppomobile.com）：新版日历（versionCode >= 7001000）数据在
     * com.coloros.calendar 私有库，原生的只剩 exchange 同步数据，所以私有库必须排在原生前面。
     */
    private val AUTHORITY_CANDIDATES = listOf(
            "com.coloros.calendar",     // OPPO / 一加 ColorOS 新版日历私有库
            "com.oplus.calendar",       // OPlus 系新包名兜底
            "com.oneplus.calendar",     // 一加旧包名兜底
            CalendarContract.AUTHORITY  // 原生 com.android.calendar（其他品牌走这里）
    )

    /** 某个 AUTHORITY 的日历表 Uri */
    private fun calendarsUri(auth: String): Uri =
            if (auth == CalendarContract.AUTHORITY) CalendarContract.Calendars.CONTENT_URI
            else Uri.parse("content://$auth/calendars")

    /** 某个 AUTHORITY 的日程表 Uri */
    private fun eventsUri(auth: String): Uri =
            if (auth == CalendarContract.AUTHORITY) CalendarContract.Events.CONTENT_URI
            else Uri.parse("content://$auth/events")

    /** 某个 AUTHORITY 的提醒表 Uri */
    private fun remindersUri(auth: String): Uri =
            if (auth == CalendarContract.AUTHORITY) CalendarContract.Reminders.CONTENT_URI
            else Uri.parse("content://$auth/reminders")

    /** 可写入的日历 */
    data class SyncCalendar(val id: Long, val name: String, val accountName: String,
                            /** 这条日历来自哪个 Provider（新版 ColorOS 是 com.coloros.calendar） */
                            val authority: String) {
        /** 弹选择框时显示的文字 */
        val displayName: String
            get() = if (accountName.isBlank() ||
                    accountName.equals("local", true) ||
                    accountName.equals("本地", true)) {
                name
            } else {
                "$name（$accountName）"
            }
    }

    /** 一条待写入的日程 */
    private data class EventItem(
            val title: String,
            val description: String,
            val location: String,
            val startMillis: Long,
            val endMillis: Long,
            val recurring: Boolean,
            val duration: String,
            val rrule: String?,
            /** 全天日程（「每日课表」走这个，显示在当天最上面，而不是某个时间点） */
            val allDay: Boolean = false,
            /** 要不要带「提前 15 分钟」提醒（每日课表是汇总，不需要提醒） */
            val remind: Boolean = true
    )

    /** 一段「等间隔」的周次，例如 1~15 周的连续周，或者 1,3,5..15 的单周 */
    private data class WeekSegment(val firstWeek: Int, val interval: Int, val count: Int)

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * 列出系统里所有允许写入的日历。
     * 自动探测 Provider：新版 ColorOS 数据在 com.coloros.calendar 私有库，其他品牌走原生。
     * 国内 ROM 一般至少有「本地日历」和一个云同步账户的日历。
     */
    fun queryWritableCalendars(resolver: ContentResolver): List<SyncCalendar> {
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? " +
                "AND ${CalendarContract.Calendars.VISIBLE} = 1"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        // 1) 先按「可写 + 可见」严格查，哪个库有结果就用哪个
        for (auth in AUTHORITY_CANDIDATES) {
            val list = queryCalendars(resolver, calendarsUri(auth), selection, args, auth)
            if (list.isNotEmpty()) return list
        }
        // 2) 兜底：有的 ROM 把本地日历的 access / visible 标得很低，放宽条件再扫一遍
        for (auth in AUTHORITY_CANDIDATES) {
            val list = queryCalendars(resolver, calendarsUri(auth), null, null, auth)
            if (list.isNotEmpty()) return list
        }
        return emptyList()
    }

    /** 按 Uri 查询日历列表；Provider 不存在（如非 OPPO 机型查私有库）会抛异常，静默换下一个 */
    private fun queryCalendars(resolver: ContentResolver, uri: Uri,
                               selection: String?, args: Array<String>?,
                               authority: String): List<SyncCalendar> {
        val result = arrayListOf<SyncCalendar>()
        val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME
        )
        try {
            resolver.query(uri, projection, selection, args, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val nameIndex = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountIndex = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                if (idIndex < 0) return@use
                while (cursor.moveToNext()) {
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    val account = if (accountIndex >= 0) cursor.getString(accountIndex) else null
                    result.add(SyncCalendar(
                            id = cursor.getLong(idIndex),
                            name = if (name.isNullOrBlank()) "未命名日历" else name,
                            accountName = account ?: "",
                            authority = authority
                    ))
                }
            }
        } catch (ignored: Exception) {
            // 该 AUTHORITY 不存在或被拦截 → 当作没有，换下一个候选
        }
        return result
    }

    /**
     * 把一张课表同步进指定日历。
     *
     * @return 一共写入了多少条日程
     */
    fun syncTable(context: Context,
                  table: TableBean,
                  timeList: List<TimeDetailBean>,
                  courseList: List<CourseBean>,
                  calendarId: Long,
                  authority: String): Int {
        val resolver = context.contentResolver
        val termStart = parseDate(table.startDate)
                ?: throw IllegalArgumentException("学期开始日期不对：${table.startDate}")

        val events = arrayListOf<EventItem>()
        courseList.forEach { course ->
            try {
                events.addAll(buildEventItems(course, timeList, table.maxWeek, termStart, table.id))
            } catch (ignored: Exception) {
                // 单门课算不出来就跳过，不影响其它课
            }
        }
        // 再补上「每日课表」：每天一条，这一天几节课、都是什么、在哪儿，一眼看到
        try {
            events.addAll(buildDailyItems(courseList, timeList, table.maxWeek, termStart, table.id))
        } catch (ignored: Exception) {
            // 日课表算不出来也不影响每节课的日程
        }
        if (events.isEmpty()) return 0

        // 先清掉上一次同步进来的日程，避免叠加
        deleteSyncedEvents(resolver, table.id, authority)

        try {
            resolver.applyBatch(authority, buildOps(events, calendarId, true, authority))
        } catch (e: Exception) {
            // 有些 ROM 的提醒表批量插入会失败，退一步：只写日程、不带提醒
            deleteSyncedEvents(resolver, table.id, authority)
            resolver.applyBatch(authority, buildOps(events, calendarId, false, authority))
        }
        return events.size
    }

    /** 清掉本 App 之前同步进日历的日程（只清这一张课表的，含每节课和每日课表两类） */
    fun deleteSyncedEvents(resolver: ContentResolver, tableId: Int, authority: String): Int {
        var count = resolver.delete(
                eventsUri(authority),
                "${CalendarContract.Events.DESCRIPTION} LIKE ?",
                arrayOf("${descMark(tableId)}%")
        )
        count += resolver.delete(
                eventsUri(authority),
                "${CalendarContract.Events.DESCRIPTION} LIKE ?",
                arrayOf("${descDailyMark(tableId)}%")
        )
        return count
    }

    private fun descMark(tableId: Int) = "[小唐Tangle#$tableId]"

    private fun descDailyMark(tableId: Int) = "[小唐Tangle#日课#$tableId]"

    private fun buildOps(events: List<EventItem>, calendarId: Long,
                         withReminder: Boolean, authority: String): ArrayList<ContentProviderOperation> {
        val ops = arrayListOf<ContentProviderOperation>()
        val timeZone = TimeZone.getDefault().id
        events.forEach { e ->
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, e.title)
                put(CalendarContract.Events.DESCRIPTION, e.description)
                put(CalendarContract.Events.EVENT_LOCATION, e.location)
                put(CalendarContract.Events.DTSTART, e.startMillis)
                if (e.allDay) {
                    // 全天日程：安卓规定必须用 UTC 时区 + 午夜边界，否则日历里日期会错位一天
                    put(CalendarContract.Events.ALL_DAY, 1)
                    put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                } else {
                    put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
                }
                if (e.recurring && e.rrule != null) {
                    put(CalendarContract.Events.DURATION, e.duration)
                    put(CalendarContract.Events.RRULE, e.rrule)
                } else {
                    put(CalendarContract.Events.DTEND, e.endMillis)
                }
            }
            val eventIndex = ops.size
            ops.add(ContentProviderOperation.newInsert(eventsUri(authority))
                    .withValues(values)
                    .build())
            if (withReminder && e.remind) {
                ops.add(ContentProviderOperation.newInsert(remindersUri(authority))
                        .withValueBackReference(CalendarContract.Reminders.EVENT_ID, eventIndex)
                        .withValue(CalendarContract.Reminders.MINUTES, REMINDER_MINUTES)
                        .withValue(CalendarContract.Reminders.METHOD,
                                CalendarContract.Reminders.METHOD_ALERT)
                        .build())
            }
        }
        return ops
    }

    /** 把一门课拆成若干条日程 */
    private fun buildEventItems(course: CourseBean,
                                timeList: List<TimeDetailBean>,
                                maxWeek: Int,
                                termStart: Date,
                                tableId: Int): List<EventItem> {
        val startNode = timeList.firstOrNull { it.node == course.startNode } ?: return emptyList()
        val endNode = timeList.firstOrNull { it.node == course.startNode + course.step - 1 }
                ?: return emptyList()

        // 真正要上课的周次（inWeek 已经处理了单双周）
        val weeks = (course.startWeek..course.endWeek)
                .filter { it in 1..maxWeek && course.inWeek(it) }
        if (weeks.isEmpty()) return emptyList()

        val mark = descMark(tableId)
        val description = StringBuilder(mark)
                .append('\n').append(course.getNodeString())
                .also { sb ->
                    course.room?.takeIf { it.isNotBlank() }?.let { sb.append('\n').append(it) }
                    course.teacher?.takeIf { it.isNotBlank() }?.let { sb.append('\n').append(it) }
                }
                .toString()
        val location = listOfNotNull(course.room, course.teacher)
                .filter { it.isNotBlank() }
                .joinToString(" ")

        val result = arrayListOf<EventItem>()
        splitEvenSegments(weeks).forEach { seg ->
            val startCal = weekTime(termStart, seg.firstWeek, course.day, startNode.startTime)
            val endCal = weekTime(termStart, seg.firstWeek, course.day, endNode.endTime)
            val durationMs = endCal.timeInMillis - startCal.timeInMillis
            if (durationMs <= 0) return@forEach

            val recurring = seg.count > 1
            result.add(EventItem(
                    title = course.courseName,
                    description = description,
                    location = location,
                    startMillis = startCal.timeInMillis,
                    endMillis = endCal.timeInMillis,
                    recurring = recurring,
                    duration = formatDuration(durationMs),
                    rrule = if (recurring) buildRRule(seg.interval, seg.count) else null
            ))
        }
        return result
    }

    /**
     * 生成「每日课表」：每天一条全天日程。
     *
     * 标题写「今日课表 · N 节」，备注里把当天每节课的「时间 课名 @教室」列出来。
     * 这样系统日历按天看、负一屏的「日程」卡片，每天都能直接看到一整天的课，
     * 和「一屏就是一周」的周课表正好互补。
     */
    private fun buildDailyItems(courseList: List<CourseBean>,
                                timeList: List<TimeDetailBean>,
                                maxWeek: Int,
                                termStart: Date,
                                tableId: Int): List<EventItem> {
        val result = arrayListOf<EventItem>()
        for (day in 1..7) {
            val dayCourses = courseList.filter { it.day == day }.sortedBy { it.startNode }
            if (dayCourses.isEmpty()) continue

            // 这一天有课的周次：把所有课在这一天的周次并起来
            val weekSet = sortedSetOf<Int>()
            dayCourses.forEach { course ->
                (course.startWeek..course.endWeek).forEach { week ->
                    if (week in 1..maxWeek && course.inWeek(week)) weekSet.add(week)
                }
            }
            if (weekSet.isEmpty()) continue

            // 备注 = 当天每节课的「时间 课名 @教室」
            val desc = StringBuilder(descDailyMark(tableId))
            dayCourses.forEach { course ->
                val start = timeList.firstOrNull { it.node == course.startNode }
                val end = timeList.firstOrNull { it.node == course.startNode + course.step - 1 }
                desc.append('\n')
                if (start != null && end != null) {
                    desc.append(start.startTime).append('-').append(end.endTime).append(' ')
                }
                desc.append(course.courseName)
                course.room?.takeIf { it.isNotBlank() }?.let { desc.append(" @").append(it) }
            }
            val title = "今日课表 · ${dayCourses.size} 节"

            splitEvenSegments(weekSet.toList()).forEach { seg ->
                val recurring = seg.count > 1
                result.add(EventItem(
                        title = title,
                        description = desc.toString(),
                        location = "",
                        startMillis = weekAllDayTime(termStart, seg.firstWeek, day, 0),
                        endMillis = weekAllDayTime(termStart, seg.firstWeek, day, 1),
                        recurring = recurring,
                        duration = "P1D",
                        rrule = if (recurring) buildRRule(seg.interval, seg.count) else null,
                        allDay = true,
                        remind = false
                ))
            }
        }
        return result
    }

    /**
     * 第 week 周、星期 day 的「全天」时刻（offsetDay：0 = 当天，1 = 次日）。
     *
     * 安卓规定全天日程要用 UTC 时区 + 午夜边界，所以这里取「本地看到的那个日期」的 UTC 零点。
     */
    private fun weekAllDayTime(termStart: Date, week: Int, day: Int, offsetDay: Int): Long {
        val local = Calendar.getInstance()
        local.time = termStart
        local.set(Calendar.HOUR_OF_DAY, 0)
        local.set(Calendar.MINUTE, 0)
        local.set(Calendar.SECOND, 0)
        local.set(Calendar.MILLISECOND, 0)
        local.add(Calendar.DAY_OF_MONTH, (week - 1) * 7 + (day - 1) + offsetDay)

        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.clear()
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        return utc.timeInMillis
    }

    /**
     * 把周次列表切成「等间隔」的段。
     * 全周上课的课 -> [[1,15] 间隔1]，单周上课的课 -> [[1,15] 间隔2]，理论上一门课只有一段。
     */
    private fun splitEvenSegments(weeks: List<Int>): List<WeekSegment> {
        val result = arrayListOf<WeekSegment>()
        var firstWeek = weeks[0]
        var interval = 0
        var count = 1
        var prev = weeks[0]

        fun flush() {
            result.add(WeekSegment(firstWeek, if (interval == 0) 1 else interval, count))
        }

        for (i in 1 until weeks.size) {
            val diff = weeks[i] - prev
            val sameStep = (diff == 1 || diff == 2) && (interval == 0 || diff == interval)
            if (sameStep) {
                if (interval == 0) interval = diff
                count++
            } else {
                flush()
                firstWeek = weeks[i]
                interval = 0
                count = 1
            }
            prev = weeks[i]
        }
        flush()
        return result
    }

    /** 第 week 周、星期 day、time(HH:mm) 对应的时刻。前提：startDate 填的是第 1 周第一天 */
    private fun weekTime(termStart: Date, week: Int, day: Int, time: String): Calendar {
        val cal = Calendar.getInstance()
        cal.time = termStart
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_MONTH, (week - 1) * 7 + (day - 1))
        val parts = time.split(":")
        cal.set(Calendar.HOUR_OF_DAY, parts[0].trim().toInt())
        cal.set(Calendar.MINUTE, if (parts.size > 1) parts[1].trim().toInt() else 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    private fun buildRRule(interval: Int, count: Int): String {
        val sb = StringBuilder("FREQ=WEEKLY;")
        if (interval > 1) {
            sb.append("INTERVAL=").append(interval).append(';')
        }
        sb.append("COUNT=").append(count).append(';')
        sb.append("WKST=MO")
        return sb.toString()
    }

    /** RFC2445 的时长写法，例如 PT1H30M */
    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val sb = StringBuilder("P")
        if (hours > 0 || minutes > 0 || seconds > 0) sb.append('T')
        if (hours > 0) sb.append(hours).append('H')
        if (minutes > 0) sb.append(minutes).append('M')
        if (seconds > 0) sb.append(seconds).append('S')
        if (sb.length == 1) sb.append("T0S")
        return sb.toString()
    }

    private fun parseDate(s: String): Date? {
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(s)
        } catch (e: Exception) {
            null
        }
    }
}
