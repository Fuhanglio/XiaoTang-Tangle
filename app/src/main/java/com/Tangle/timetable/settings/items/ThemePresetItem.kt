package com.Tangle.timetable.settings.items

/**
 * 预设主题快速选择区（跟随系统 + 5 个预设色）。
 * 数据直接读取 ThemeManager，本类只负责标识类型。
 */
class ThemePresetItem : BaseSettingItem("主题配色", listOf("主题", "配色", "颜色", "预设", "深色", "夜间", "跟随系统")) {
    override fun getType() = SettingType.THEME_PRESET
}
