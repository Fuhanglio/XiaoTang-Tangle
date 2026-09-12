package com.Tangle.timetable.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseProviderMultiAdapter
import com.Tangle.timetable.R
import com.Tangle.timetable.settings.items.BaseSettingItem
import com.Tangle.timetable.settings.items.SettingType

/**
 * 设置页 RecyclerView ItemDecoration：
 * - 非 Category 类型的 item 自动按 Category 分组，组内用卡片色圆角卡片包裹
 * - 卡片左右 16dp 边距、组间 12dp 间距、圆角 16dp（均取自 dimens 语义变量）
 * - 卡片/分割线颜色取自 color 资源（card_bg / divider），深色模式自动适配
 * - Category 和 ThemePreset 本身不加卡片（自有样式）
 * - 设置页整体背景由 styles.xml windowBackground 统一为 page_bg
 */
class SettingsCardDecoration(private val context: Context) : RecyclerView.ItemDecoration() {

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int): Float = value * density
    private fun dpInt(value: Int): Int = (value * density).toInt()

    private val cardColor = ContextCompat.getColor(context, R.color.card_bg)
    private val cornerRadius = context.resources.getDimension(R.dimen.settings_card_radius)
    private val cardSideMargin = context.resources.getDimensionPixelSize(R.dimen.settings_card_margin)
    private val groupSpacing = context.resources.getDimensionPixelSize(R.dimen.settings_group_spacing)

    private val cardTop = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(cardColor)
        cornerRadius = cornerRadius
    }

    private val cardBottom = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(cardColor)
        cornerRadius = cornerRadius
    }

    private val cardMiddle = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(cardColor)
    }

    private val cardSingle = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(cardColor)
        cornerRadius = cornerRadius
    }

    private val dividerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.divider)
        strokeWidth = dp(1)
    }

    /** 卡片极淡投影（深浅色自适应） */
    private val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.card_shadow)
    }
    private val shadowDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(ContextCompat.getColor(context, R.color.card_shadow))
        cornerRadius = cornerRadius
    }

    private fun getType(data: List<BaseSettingItem>, pos: Int): Int? {
        if (pos < 0 || pos >= data.size) return null
        return data[pos].getType()
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDraw(c, parent, state)
        val adapter = parent.adapter as? BaseProviderMultiAdapter<*> ?: return
        val data = adapter.data as? List<BaseSettingItem> ?: return
        if (data.isEmpty()) return

        val childCount = parent.childCount
        for (visibleIdx in 0 until childCount) {
            val view = parent.getChildAt(visibleIdx) ?: continue
            val adapterPos = parent.getChildAdapterPosition(view)
            if (adapterPos == RecyclerView.NO_POSITION || adapterPos >= data.size) continue
            val item = data[adapterPos]

            val itemType = item.getType()
            if (itemType == SettingType.CATEGORY || itemType == SettingType.THEME_PRESET) {
                continue
            }

            val prevType = getType(data, adapterPos - 1)
            val nextType = getType(data, adapterPos + 1)

            val isGroupFirst = prevType == null ||
                    prevType == SettingType.CATEGORY ||
                    prevType == SettingType.THEME_PRESET
            val isGroupLast = nextType == null ||
                    nextType == SettingType.CATEGORY ||
                    nextType == SettingType.THEME_PRESET

            val drawable = when {
                isGroupFirst && isGroupLast -> cardSingle
                isGroupFirst -> cardTop
                isGroupLast -> cardBottom
                else -> cardMiddle
            }

            val rect = Rect()
            parent.getDecoratedBoundsWithMargins(view, rect)
            // 卡片底部极淡投影（组末行才画，避免组内阴影条）
            if (isGroupLast) {
                shadowDrawable.setBounds(rect.left, rect.top, rect.right, rect.bottom + dp(1).toInt())
                shadowDrawable.draw(c)
            }
            drawable.setBounds(rect)
            drawable.draw(c)

            // 组内中间项画 1dp 分割线（左端对齐到文字 ≈ 64dp，组末不画）
            if (!isGroupLast) {
                val dividerTop = rect.bottom - dividerPaint.strokeWidth / 2
                c.drawLine(
                        (rect.left + dp(64)).toInt().toFloat(),
                        dividerTop,
                        (rect.right - dp(16)).toInt().toFloat(),
                        dividerTop,
                        dividerPaint
                )
            }
        }
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)
        val adapter = parent.adapter as? BaseProviderMultiAdapter<*> ?: return
        val data = adapter.data as? List<BaseSettingItem> ?: return
        val pos = parent.getChildAdapterPosition(view)
        if (pos == RecyclerView.NO_POSITION || pos >= data.size) return

        val itemType = data[pos].getType()
        if (itemType == SettingType.CATEGORY || itemType == SettingType.THEME_PRESET) {
            outRect.left = cardSideMargin
            outRect.right = cardSideMargin
            return
        }

        val nextType = getType(data, pos + 1)

        val isGroupLast = nextType == null ||
                nextType == SettingType.CATEGORY ||
                nextType == SettingType.THEME_PRESET

        outRect.left = cardSideMargin
        outRect.right = cardSideMargin

        if (isGroupLast) outRect.bottom = groupSpacing
    }
}
