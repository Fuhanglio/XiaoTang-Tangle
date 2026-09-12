package com.Tangle.timetable.settings.provider

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.provider.BaseItemProvider
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.Tangle.timetable.R
import com.Tangle.timetable.settings.items.BaseSettingItem
import com.Tangle.timetable.settings.items.CategoryItem
import com.Tangle.timetable.settings.items.SettingType
import com.Tangle.timetable.utils.ViewUtils
import splitties.dimensions.dip

/**
 * 分组标题：去旧版紫色块背景，改为黑色加粗纯文字标题。
 */
class CategoryItemProvider : BaseItemProvider<BaseSettingItem>() {

    override val itemViewType: Int
        get() = SettingType.CATEGORY

    override val layoutId: Int
        get() = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val view = LinearLayoutCompat(parent.context).apply {
            id = R.id.anko_layout
            orientation = LinearLayoutCompat.VERTICAL
            setBackgroundColor(Color.TRANSPARENT) // 页面浅灰背景透出
            addView(View(context).apply {
                id = R.id.anko_view
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                }
            }, LinearLayoutCompat.LayoutParams(
                    LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                    ViewUtils.getStatusBarHeight(context) + dip(0))) // 顶栏后不留额外空间

            addView(AppCompatTextView(context).apply {
                id = R.id.anko_text_view
                textSize = 13f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayoutCompat.LayoutParams(
                    LinearLayoutCompat.LayoutParams.WRAP_CONTENT,
                    LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dip(24)
                bottomMargin = dip(8)
                marginStart = dip(0)
            })
        }
        view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
        return BaseViewHolder(view)
    }

    override fun convert(helper: BaseViewHolder, data: BaseSettingItem?) {
        if (data == null) return
        val item = data as CategoryItem
        helper.setText(R.id.anko_text_view, item.name)
        helper.setGone(R.id.anko_view, !item.hasMarginTop)
    }
}
