package com.Tangle.timetable.settings

import com.chad.library.adapter.base.BaseProviderMultiAdapter
import com.Tangle.timetable.settings.items.BaseSettingItem
import com.Tangle.timetable.settings.provider.*

class SettingItemAdapter : BaseProviderMultiAdapter<BaseSettingItem>() {

    init {
        addItemProvider(CategoryItemProvider())
        addItemProvider(HorizontalItemProvider())
        addItemProvider(SeekBarItemProvider())
        addItemProvider(SwitchItemProvider())
        addItemProvider(VerticalItemProvider())
        addItemProvider(ThemePresetItemProvider())
        addItemProvider(ColorItemProvider())
    }

    override fun getItemType(data: List<BaseSettingItem>, position: Int): Int {
        return data[position].getType()
    }

}