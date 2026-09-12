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

    /** 是否已经设置过 */
    fun isSet(context: Context): Boolean =
            context.getPrefer().getInt(Const.KEY_BIRTHDAY_MONTH, 0) in 1..12

    fun month(context: Context): Int =
            context.getPrefer().getInt(Const.KEY_BIRTHDAY_MONTH, 1).coerceIn(1, 12)

    /** 该月最大天数（按今年算，2 月能给到 29） */
    fun maxDay(month: Int): Int = maxDay(Calendar.getInstance().get(Calendar.YEAR), month)

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
        val target = Calendar.getInstance().apply {
            clear()
            set(year, month.coerceIn(1, 12) - 1, day.coerceIn(1, maxDay(year, month)), 0, 0, 0)
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
    fun nextDateText(month: Int, day: Int): String =
            "${nextYear(month, day)}年${month}月${day}日"

    // ---- 便捷版：直接读已保存的设置 ----

    fun daysUntil(context: Context): Int = daysUntil(month(context), day(context))

    fun nextDateText(context: Context): String = nextDateText(month(context), day(context))
}
