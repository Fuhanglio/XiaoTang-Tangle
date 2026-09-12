package com.Tangle.timetable.settings.provider

import android.content.res.ColorStateList
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.chad.library.adapter.base.provider.BaseItemProvider
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.Tangle.timetable.R
import com.Tangle.timetable.settings.SettingIcons
import com.Tangle.timetable.settings.items.BaseSettingItem
import com.Tangle.timetable.settings.items.SettingType
import com.Tangle.timetable.settings.items.SwitchItem
import splitties.dimensions.dip

class SwitchItemProvider : BaseItemProvider<BaseSettingItem>() {

    override val itemViewType: Int
        get() = SettingType.SWITCH

    override val layoutId: Int
        get() = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val ctx = parent.context
        val view = LinearLayoutCompat(ctx).apply {
            id = R.id.anko_layout
            orientation = LinearLayoutCompat.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.card_bg))
            setPadding(dip(16), dip(10), dip(16), dip(10))
            minimumHeight = ctx.resources.getDimensionPixelSize(R.dimen.settings_row_height)

            // 左侧 36dp 柔和彩色图标块（convert 时按标题填入）
            addView(LinearLayoutCompat(ctx).apply {
                id = R.id.anko_icon_block
            }, LinearLayoutCompat.LayoutParams(dip(36), dip(36)).apply {
                marginEnd = dip(12)
            })

            // 中部：标题 + 副标题
            addView(LinearLayoutCompat(ctx).apply {
                id = R.id.anko_mid
                orientation = LinearLayoutCompat.VERTICAL

                addView(AppCompatTextView(ctx).apply {
                    id = R.id.anko_text_view
                    setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                    textSize = 16f
                }, LinearLayoutCompat.LayoutParams(
                        LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                        LinearLayoutCompat.LayoutParams.WRAP_CONTENT))

                addView(AppCompatTextView(ctx).apply {
                    id = R.id.anko_tv_description
                    setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                    textSize = 13f
                }, LinearLayoutCompat.LayoutParams(
                        LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                        LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dip(2)
                })
            }, LinearLayoutCompat.LayoutParams(0, LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply {
                weight = 1f
            })

            // 右侧 iOS 胶囊开关（保持 anko_check_box id，点击回调逻辑不变）
            val switch = SwitchCompat(ctx).apply {
                id = R.id.anko_check_box
                isClickable = false
                isFocusable = false
                // 与主设置页 / 课表设置页共用的统一 iOS 样式
                SettingIcons.applyIosSwitchStyle(this)
            }
            addView(switch, LinearLayoutCompat.LayoutParams(
                    LinearLayoutCompat.LayoutParams.WRAP_CONTENT,
                    LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dip(12)
            })
        }
        view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
        return BaseViewHolder(view)
    }

    override fun convert(helper: BaseViewHolder, data: BaseSettingItem?) {
        if (data == null) return
        val item = data as SwitchItem
        val ctx = helper.itemView.context
        helper.setText(R.id.anko_text_view, item.title)

        // 填充图标块
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

        val sw = helper.getView<SwitchCompat>(R.id.anko_check_box)
        // 每次绑定都重新套用 iOS 样式，确保主设置页与课表设置页渲染完全一致
        SettingIcons.applyIosSwitchStyle(sw)
        sw.isChecked = item.checked

        if (data.desc.isEmpty()) {
            helper.setGone(R.id.anko_tv_description, true)
        } else {
            helper.setText(R.id.anko_tv_description, item.desc)
            helper.setGone(R.id.anko_tv_description, false)
        }
    }

}
