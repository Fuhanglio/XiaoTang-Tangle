package com.Tangle.timetable.settings.provider

import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import com.chad.library.adapter.base.provider.BaseItemProvider
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.Tangle.timetable.R
import com.Tangle.timetable.settings.items.BaseSettingItem
import com.Tangle.timetable.settings.items.ColorItem
import com.Tangle.timetable.settings.items.SettingType
import com.Tangle.timetable.utils.ThemeManager
import splitties.dimensions.dip

/**
 * 自定义颜色项行：左侧颜色名称，右侧当前颜色预览小圆点。
 */
class ColorItemProvider : BaseItemProvider<BaseSettingItem>() {

    override val itemViewType: Int
        get() = SettingType.COLOR

    override val layoutId: Int
        get() = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val ctx = parent.context
        val row = LinearLayoutCompat(ctx).apply {
            id = R.id.anko_layout
            orientation = LinearLayoutCompat.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(ctx.dip(16), ctx.dip(14), ctx.dip(16), ctx.dip(14))
            isClickable = true
            isFocusable = true
        }

        row.addView(AppCompatTextView(ctx).apply {
            id = R.id.anko_text_view
            textSize = 16f
            setTextColor(ThemeManager.getColor(ctx, ThemeManager.TEXT))
        }, LinearLayoutCompat.LayoutParams(0, LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply {
            weight = 1f
        })

        row.addView(View(ctx).apply {
            id = R.id.anko_color_dot
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(ctx.dip(1), 0x55000000)
            }
        }, LinearLayoutCompat.LayoutParams(ctx.dip(22), ctx.dip(22)))

        row.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return BaseViewHolder(row)
    }

    override fun convert(helper: BaseViewHolder, data: BaseSettingItem?) {
        if (data == null) return
        val item = data as ColorItem
        val ctx = helper.itemView.context
        helper.setText(R.id.anko_text_view, item.title)

        val color = when (item.colorKey) {
            ThemeManager.BG -> ThemeManager.getSolidBackgroundColor(ctx)
                    ?: ThemeManager.getColor(ctx, ThemeManager.BG)
            else -> ThemeManager.getColor(ctx, item.colorKey)
        }
        val dot = helper.getView<View>(R.id.anko_color_dot)
        (dot.background as? GradientDrawable)?.setColor(color)
    }
}
