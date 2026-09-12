package com.Tangle.timetable.settings.items

abstract class BaseSettingItem(val title: String, val keyWords: List<String>?) {
    abstract fun getType(): Int
}

object SettingType {
    const val CATEGORY = 0
    const val HORIZON = 1
    const val SEEKBAR = 2
    const val SWITCH = 3
    const val VERTICAL = 4
    const val THEME_PRESET = 5
    const val COLOR = 6
}