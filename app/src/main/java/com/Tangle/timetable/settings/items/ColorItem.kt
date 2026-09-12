package com.Tangle.timetable.settings.items

import com.Tangle.timetable.utils.ThemeManager

/**
 * 可自定义颜色项（点击弹出圆盘调色板）。
 * @param colorKey ThemeManager 中的颜色 key
 */
data class ColorItem(val colorKey: String) :
        BaseSettingItem(ThemeManager.COLOR_ITEMS[colorKey] ?: colorKey,
                listOf("颜色", "配色", "色号", "调色板", ThemeManager.COLOR_ITEMS[colorKey] ?: "")) {
    override fun getType() = SettingType.COLOR
}
