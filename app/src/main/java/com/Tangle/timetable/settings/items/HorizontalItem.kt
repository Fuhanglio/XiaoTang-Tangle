package com.Tangle.timetable.settings.items

data class HorizontalItem(
        val name: String,
        var value: String,
        val keys: List<String>? = null) : BaseSettingItem(name, keys) {
    override fun getType(): Int {
        return SettingType.HORIZON
    }
}