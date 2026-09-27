package com.Tangle.timetable.utils

import android.content.Context
import androidx.core.content.edit
import java.util.Calendar

/**
 * 生日提醒：只记录「月 + 日」，不记年份。
 *
 * 年份推导规则（用户明确要求）：
 * - 设置的月日如果**已经过去**（例如现在是 10 月，设的是 9 月），就理解为**明年**的那个月日；
 * - 如果**还没到**（例如现在 10 月，设的是 12 月），就是**今年**的那个月日；
 * - 正好是今天，算今年（也就是"就是今天"）。
 */
object BirthdayUtils {

    /**
     * W7-16：一个**闰年**，只用于取"该月最多有多少天"。
     * 2 月要允许选到 29（生日每年循环，闰年才真的落在 2/29），所以不能用"当前年份"去问月长。
     */
    private const val LEAP_PROBE_YEAR = 2024

    /** 是否已经设置过 */
    fun isSet(context: Context): Boolean =
            context.getPrefer().getInt(Const.KEY_BIRTHDAY_MONTH, 0) in 1..12

    fun month(context: Context): Int =
            context.getPrefer().getInt(Const.KEY_BIRTHDAY_MONTH, 1).coerceIn(1, 12)

    /**
     * 该月最大天数。
     *
     * W7-16：生日是「每年循环」的，2 月必须允许选到 **29**（闰年才有 2/29；平年按 3/1 提醒，
     * 见 [daysUntil]）。原实现用**当前年份**取月长 —— 2026 年的 2 月只给到 28，滚轮根本
     * 选不了 29，用户在非闰年连"我是 2/29 生的"都设置不了。
     * 故这里改用**闰年**（[LEAP_PROBE_YEAR]）取月长，与年份解耦。
     */
    fun maxDay(month: Int): Int = maxDay(LEAP_PROBE_YEAR, month)

    fun maxDay(year: Int, month: Int): Int {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month.coerceIn(1, 12) - 1, 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun day(context: Context): Int {
        val m = month(context)
        return context.getPrefer().getInt(Const.KEY_BIRTHDAY_DAY, 1).coerceIn(1, maxDay(m))
    }

    fun text(context: Context): String =
            context.getPrefer().getString(Const.KEY_BIRTHDAY_TEXT, "") ?: ""

    fun dateText(context: Context): String = "${month(context)}月${day(context)}日"

    fun save(context: Context, month: Int, day: Int, text: String) {
        val m = month.coerceIn(1, 12)
        context.getPrefer().edit {
            putInt(Const.KEY_BIRTHDAY_MONTH, m)
            putInt(Const.KEY_BIRTHDAY_DAY, day.coerceIn(1, maxDay(m)))
            putString(Const.KEY_BIRTHDAY_TEXT, text)
        }
    }

    /** 下一次发生的年份：月日已过去 → 明年；还没到或就是今天 → 今年 */
    fun nextYear(month: Int, day: Int): Int {
        val now = Calendar.getInstance()
        val curMonth = now.get(Calendar.MONTH) + 1
        val curDay = now.get(Calendar.DAY_OF_MONTH)
        val passed = month < curMonth || (month == curMonth && day < curDay)
        return if (passed) now.get(Calendar.YEAR) + 1 else now.get(Calendar.YEAR)
    }

    /** 距下一次那天还有几天；今天就是的话返回 0 */
    fun daysUntil(month: Int, day: Int): Int {
        val year = nextYear(month, day)
        val m = month.coerceIn(1, 12)
        // W7-16：生日 2/29 在**平年**没有对应的那一天。
        // 原来靠 `day.coerceIn(1, maxDay(year, month))` 兜底，会把它**静默降级成 2/28** ——
        // 提醒日从 2/29 悄悄漂到 2/28，用户完全无从察觉。
        // 通行约定是「闰年 2/29、平年 3/1」，这里显式这么做。
        var ty = year
        var tm = m
        var td = day
        if (m == 2 && day == 29 && !isLeapYear(ty)) {
            tm = 3
            td = 1
        }
        val target = Calendar.getInstance().apply {
            clear()
            set(ty, tm - 1, td.coerceIn(1, maxDay(ty, tm)), 0, 0, 0)
        }
        // 注意：clear() 会把字段清成 1970，所以今天的年月日必须先取出来再 clear
        val now = Calendar.getInstance()
        val nowYear = now.get(Calendar.YEAR)
        val nowMonth = now.get(Calendar.MONTH)
        val nowDay = now.get(Calendar.DAY_OF_MONTH)
        val today = Calendar.getInstance().apply {
            clear()
            set(nowYear, nowMonth, nowDay, 0, 0, 0)
        }
        return ((target.timeInMillis - today.timeInMillis) / 86400000L).toInt()
    }

    /** 下一次发生的完整日期，如「2027年9月21日」 */
    fun nextDateText(month: Int, day: Int): String {
        val year = nextYear(month, day)
        val m = month.coerceIn(1, 12)
        // W7-16：平年的 2/29 实际提醒在 3/1，文案必须跟着走，
        // 否则会显示「2027年2月29日」这种**不存在的日期**
        return if (m == 2 && day == 29 && !isLeapYear(year)) "${year}年3月1日"
        else "${year}年${m}月${day}日"
    }

    /** W7-16：闰年判定（GregorianCalendar 自带，比手写 %4/%100/%400 更省心） */
    private fun isLeapYear(year: Int): Boolean =
            java.util.GregorianCalendar().isLeapYear(year)

    // ---- 便捷版：直接读已保存的设置 ----

    fun daysUntil(context: Context): Int = daysUntil(month(context), day(context))

    fun nextDateText(context: Context): String = nextDateText(month(context), day(context))
}
