package com.Tangle.timetable.settings.provider

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.provider.BaseItemProvider
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.Tangle.timetable.R
import com.Tangle.timetable.settings.SettingIcons
import com.Tangle.timetable.settings.items.BaseSettingItem
import com.Tangle.timetable.settings.items.SettingType
import com.Tangle.timetable.settings.items.VerticalItem
import com.Tangle.timetable.utils.ViewUtils
import splitties.dimensions.dip

class VerticalItemProvider : BaseItemProvider<BaseSettingItem>() {

    override val itemViewType: Int
        get() = SettingType.VERTICAL

    override val layoutId: Int
        get() = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val view = LinearLayoutCompat(parent.context).apply {
            id = R.id.anko_layout
            orientation = LinearLayoutCompat.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.card_bg))
            setPadding(dip(16), dip(12), dip(4), dip(12))

            addView(LinearLayoutCompat(context).apply {
                orientation = LinearLayoutCompat.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                // 左侧柔和彩色图标块（convert 时按标题填入）
                addView(LinearLayoutCompat(context).apply {
                    id = R.id.anko_icon_block
                }, LinearLayoutCompat.LayoutParams(dip(36), dip(36)))

                addView(AppCompatTextView(context).apply {
                    id = R.id.anko_text_view
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    textSize = 16f
                }, LinearLayoutCompat.LayoutParams(0, LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dip(12)
                    marginEnd = dip(4)
                    weight = 1f
                })

                addView(AppCompatImageView(context).apply {
                    id = R.id.anko_iv_chevron
                    setImageResource(R.drawable.ic_chevron_right)
                    scaleType = android.widget.ImageView.ScaleType.CENTER
                }, LinearLayoutCompat.LayoutParams(dip(32), dip(32)))
            }, LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                    LinearLayoutCompat.LayoutParams.WRAP_CONTENT))

            addView(AppCompatTextView(context).apply {
                id = R.id.anko_tv_description
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 13f
            }, LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.WRAP_CONTENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dip(4)
                marginStart = dip(48)
                marginEnd = dip(16)
            })
        }
        view.layoutParams =
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT)
        return BaseViewHolder(view)
    }

    override fun convert(helper: BaseViewHolder, data: BaseSettingItem?) {
        if (data == null) return
        val item = data as VerticalItem
        val ctx: Context = helper.itemView.context
        helper.setText(R.id.anko_text_view, item.title)

        val block = helper.getView<LinearLayoutCompat>(R.id.anko_icon_block)
        block.removeAllViews()
        SettingIcons.buildIconBlock(ctx, item.title)?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.layoutParams = LinearLayoutCompat.LayoutParams(
                    LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                    LinearLayoutCompat.LayoutParams.MATCH_PARENT)
            block.addView(it)
        }
        block.layoutParams = LinearLayoutCompat.LayoutParams(ctx.dip(36), ctx.dip(36))

        val desc = helper.getView<AppCompatTextView>(R.id.anko_tv_description)
        if (item.description.isEmpty()) {
            desc.visibility = View.GONE
        } else {
            desc.visibility = View.VISIBLE
            if (item.isSpanned) {
                desc.text = ViewUtils.getHtmlSpannedString(item.description)
            } else {
                desc.text = item.description
            }
        }
    }

}
