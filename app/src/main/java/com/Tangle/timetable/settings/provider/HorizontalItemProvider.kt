package com.Tangle.timetable.settings.provider

import android.view.Gravity
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.provider.BaseItemProvider
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.Tangle.timetable.R
import com.Tangle.timetable.settings.SettingIcons
import com.Tangle.timetable.settings.items.BaseSettingItem
import com.Tangle.timetable.settings.items.HorizontalItem
import com.Tangle.timetable.settings.items.SettingType
import splitties.dimensions.dip

class HorizontalItemProvider : BaseItemProvider<BaseSettingItem>() {

    override val itemViewType: Int
        get() = SettingType.HORIZON

    override val layoutId: Int
        get() = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val view = LinearLayoutCompat(parent.context).apply {
            id = R.id.anko_layout
            setBackgroundColor(ContextCompat.getColor(context, R.color.card_bg))

            gravity = Gravity.CENTER_VERTICAL

            // 左侧柔和彩色图标块（convert 时按标题填入）
            addView(LinearLayoutCompat(context).apply {
                id = R.id.anko_icon_block
            }, LinearLayoutCompat.LayoutParams(dip(36), dip(36)).apply {
                marginStart = dip(16)
                marginEnd = dip(12)
            })

            addView(AppCompatTextView(context).apply {
                id = R.id.anko_text_view
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setLines(1)
            }, LinearLayoutCompat.LayoutParams(0, LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply {
                weight = 1f
            })

            // 右侧当前值 + 20dp 柔和 chevron
            addView(AppCompatTextView(context).apply {
                id = R.id.anko_tv_value
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                gravity = Gravity.CENTER_VERTICAL
                textSize = 13f
            }, LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.WRAP_CONTENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dip(2)
            })

            addView(AppCompatImageView(context).apply {
                id = R.id.anko_iv_chevron
                setImageResource(R.drawable.ic_chevron_right)
                scaleType = android.widget.ImageView.ScaleType.CENTER
            }, LinearLayoutCompat.LayoutParams(dip(28), LinearLayoutCompat.LayoutParams.MATCH_PARENT).apply {
                marginEnd = dip(4)
            })
        }
        view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.settings_row_height))
        return BaseViewHolder(view)
    }

    override fun convert(helper: BaseViewHolder, data: BaseSettingItem?) {
        if (data == null) return
        val item = data as HorizontalItem
        val ctx = helper.itemView.context
        helper.setText(R.id.anko_text_view, item.title)
        helper.setText(R.id.anko_tv_value, item.value)

        val block = helper.getView<LinearLayoutCompat>(R.id.anko_icon_block)
        block.removeAllViews()
        SettingIcons.buildIconBlock(ctx, item.title)?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.layoutParams = LinearLayoutCompat.LayoutParams(
                    LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                    LinearLayoutCompat.LayoutParams.MATCH_PARENT)
            block.addView(it)
        }
        block.visibility = if (block.childCount > 0) android.view.View.VISIBLE else android.view.View.INVISIBLE
    }


}
