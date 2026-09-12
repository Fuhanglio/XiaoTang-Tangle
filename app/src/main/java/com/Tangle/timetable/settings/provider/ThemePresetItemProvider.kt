package com.Tangle.timetable.settings.provider

import android.graphics.Color
import android.graphics.Typeface
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
import com.Tangle.timetable.settings.items.SettingType
import com.Tangle.timetable.settings.items.ThemePresetItem
import com.Tangle.timetable.utils.ThemeManager
import splitties.dimensions.dip

/**
 * 预设主题快速选择区：横向 6 个圆形选项（跟随系统 + 5 个主题色），
 * 当前选中项有主色调外圈高亮边框。
 */
class ThemePresetItemProvider : BaseItemProvider<BaseSettingItem>() {

    override val itemViewType: Int
        get() = SettingType.THEME_PRESET

    override val layoutId: Int
        get() = 0

    private val optionKeys = arrayListOf(
            ThemeManager.PRESET_SYSTEM,
            ThemeManager.PRESET_SKY,
            ThemeManager.PRESET_MINT,
            ThemeManager.PRESET_TARO,
            ThemeManager.PRESET_CORAL,
            ThemeManager.PRESET_BERRY,
            ThemeManager.PRESET_MOCHA
    )
    private val circleViews = arrayListOf<View>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val ctx = parent.context
        // 两行排布：第一行 4 个（含默认），第二行 3 个
        val container = LinearLayoutCompat(ctx).apply {
            id = R.id.anko_layout
            orientation = LinearLayoutCompat.VERTICAL
            setPadding(ctx.dip(8), ctx.dip(12), ctx.dip(8), ctx.dip(12))
        }
        val rows = listOf(
                optionKeys.subList(0, 4),
                optionKeys.subList(4, optionKeys.size))

        rows.forEach { rowKeys ->
            val row = LinearLayoutCompat(ctx).apply {
                orientation = LinearLayoutCompat.HORIZONTAL
            }
            rowKeys.forEach { key ->
                val option = LinearLayoutCompat(ctx).apply {
                    orientation = LinearLayoutCompat.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    isClickable = true
                    isFocusable = true
                    val outValue = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
                    setBackgroundResource(outValue.resourceId)
                }

                val circle = View(ctx).apply {
                    val d = GradientDrawable()
                    d.shape = GradientDrawable.OVAL
                    d.setSize(ctx.dip(36), ctx.dip(36))
                    if (key == ThemeManager.PRESET_SYSTEM) {
                        // 默认 iOS：半蓝半灰渐变圆表示昼夜自适应
                        d.orientation = GradientDrawable.Orientation.LEFT_RIGHT
                        d.colors = intArrayOf(0xFF007AFF.toInt(), 0xFF3C3C43.toInt())
                    } else {
                        d.setColor(ThemeManager.PRESET_COLORS[key] ?: Color.GRAY)
                    }
                    background = d
                }
                circleViews.add(circle)

                option.addView(circle, LinearLayoutCompat.LayoutParams(ctx.dip(36), ctx.dip(36)))

                option.addView(AppCompatTextView(ctx).apply {
                    text = ThemeManager.PRESET_NAMES[key]
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    gravity = Gravity.CENTER
                    setTextColor(ThemeManager.getColor(ctx, ThemeManager.TEXT))
                }, LinearLayoutCompat.LayoutParams(
                        LinearLayoutCompat.LayoutParams.WRAP_CONTENT,
                        LinearLayoutCompat.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = ctx.dip(6) })

                option.setOnClickListener {
                    ThemeManager.setPreset(ctx, key)
                    // 沿 View 树向上找到 RecyclerView 刷新选中态
                    var p: View? = option
                    while (p != null && p !is androidx.recyclerview.widget.RecyclerView) p = p.parent as? View
                    (p as? androidx.recyclerview.widget.RecyclerView)?.adapter?.notifyDataSetChanged()
                }

                row.addView(option, LinearLayoutCompat.LayoutParams(
                        0, LinearLayoutCompat.LayoutParams.WRAP_CONTENT
                ).apply { weight = 1f })
            }
            container.addView(row, LinearLayoutCompat.LayoutParams(
                    LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                    LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = ctx.dip(4)
            })
        }

        container.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return BaseViewHolder(container)
    }

    override fun convert(helper: BaseViewHolder, data: BaseSettingItem?) {
        if (data == null) return
        data as ThemePresetItem
        val ctx = helper.itemView.context
        val selected = ThemeManager.getSelection(ctx)
        val ringColor = ThemeManager.getColor(ctx, ThemeManager.PRIMARY)

        optionKeys.forEachIndexed { index, key ->
            val view = circleViews[index]
            val d = view.background as? GradientDrawable ?: return@forEachIndexed
            val isSelected = selected == key
            if (isSelected) {
                d.setStroke(ctx.dip(3), ringColor)
            } else {
                d.setStroke(0, Color.TRANSPARENT)
            }
            // 标签字重区分
            val label = (view.parent as? LinearLayoutCompat)?.getChildAt(1) as? AppCompatTextView
            label?.setTypeface(label.typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        }
    }
}
