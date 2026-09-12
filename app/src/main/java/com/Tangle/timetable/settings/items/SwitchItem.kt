package com.Tangle.timetable.settings.items

data class SwitchItem(
        val name: String,
        var checked: Boolean,
        var desc: String = "",
        val keys: List<String>? = null) : BaseSettingItem(name, keys) {
    override fun getType(): Int {
        return SettingType.SWITCH
    }
}