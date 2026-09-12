package com.Tangle.timetable

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import splitties.dimensions.dip

/**
 * 课表网格虚线分隔线（浅灰色）。
 *
 * 用途：在课程网格里画"按节次分隔的横线"和"按星期分隔的竖线"，参考 iOS 日历/课表观感。
 *
 * 约束：
 * - 只做视觉分隔，**不拦截触摸事件**（isClickable=false / isFocusable=false），
 *   不影响课程胶囊的点击、拖拽、颜色与文字逻辑。
 * - 入场层级由调用方保证：先加入容器 → 位于课程胶囊之下、网格背景之上。
 * - 位置由调用方按现有网格公式计算（节次行高 = 课程格子高度 itemHeight + weekItemMarTop），
 *   因此"课程格子高度 / 一天课程节数"变化时会跟随重算，不写死。
 */
class DashedLineView @JvmOverloads constructor(
        context: Context,
        orientation: Int = HORIZONTAL,
        attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        /** 横向虚线（按节次分隔） */
        const val HORIZONTAL = 0

        /** 纵向虚线（按星期分隔） */
        const val VERTICAL = 1
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dip(1).toFloat()
        color = ContextCompat.getColor(context, R.color.grid_dashed)
        pathEffect = DashPathEffect(
                floatArrayOf(context.dip(4).toFloat(), context.dip(4).toFloat()), 0f)
    }

    /** 虚线方向：HORIZONTAL / VERTICAL */
    var orientation: Int = orientation
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    /** 虚线颜色，默认取 @color/grid_dashed (#DDDDDD) */
    var lineColor: Int
        get() = paint.color
        set(value) {
            paint.color = value
            invalidate()
        }

    /** 虚线线宽（px），默认 1dp */
    var lineWidth: Float
        get() = paint.strokeWidth
        set(value) {
            paint.strokeWidth = value
            requestLayout()
            invalidate()
        }

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        if (orientation == HORIZONTAL) {
            val y = h / 2f
            canvas.drawLine(0f, y, w, y, paint)
        } else {
            val x = w / 2f
            canvas.drawLine(x, 0f, x, h, paint)
        }
    }
}
