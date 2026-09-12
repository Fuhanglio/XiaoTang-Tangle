package com.Tangle.timetable.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import splitties.dimensions.dp

@SuppressLint("ViewConstructor")
class TipTextView(context: Context) : View(context) {

    var tipVisibility = 0
        set(value) {
            field = value
            invalidate()
        }

    private var text = ""
    private var mStaticLayout: StaticLayout? = null
    private lateinit var mTextPaint: TextPaint
    private lateinit var mPaint: Paint
    private lateinit var bgPaint: Paint
    private lateinit var strokePaint: Paint
    private val path = Path()
    private val rect = RectF()
    private val dpUnit = dp(1)
    private var otherWeekTextAlpha = 255
    private var otherWeekBgAlpha = 255
    private var otherWeekStrokeAlpha = 255

    // 马卡龙色+深棕边框需要深色文字，这里自动判断
    private fun getContrastTextColor(bgColor: Int): Int {
        val r = (bgColor shr 16) and 0xff
        val g = (bgColor shr 8) and 0xff
        val b = bgColor and 0xff
        val brightness = (0.213 * r + 0.715 * g + 0.072 * b) / 255
        return if (brightness > 0.6) 0xFF333333.toInt() else 0xFFFFFFFF.toInt()
    }

    fun init(text: String, txtSize: Int, txtColor: Int, bgColor: Int, bgAlpha: Int, stroke: Int) {
        this.text = text
        mTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = txtSize * dpUnit
            typeface = Typeface.DEFAULT_BOLD
            // 马卡龙色系浅色底用深灰文字更可爱
            color = if (txtColor == 0xFFFFFFFF.toInt()) getContrastTextColor(bgColor) else txtColor
        }
        mPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = txtColor
            isDither = true
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = 2 * dpUnit
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            isDither = true
            style = Paint.Style.FILL
            alpha = bgAlpha
        }
        strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke
            isDither = true
            style = Paint.Style.STROKE
            strokeWidth = 2 * dpUnit
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        otherWeekTextAlpha = (mTextPaint.alpha * 0.3).toInt()
        otherWeekBgAlpha = (bgPaint.alpha * 0.3).toInt()
        otherWeekStrokeAlpha = (strokePaint.alpha * 0.3).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        rect.left = dpUnit
        rect.right = width.toFloat() - dpUnit
        rect.top = dpUnit
        rect.bottom = height.toFloat() - dpUnit
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (tipVisibility == TIP_OTHER_WEEK) {
            mTextPaint.alpha = otherWeekTextAlpha
            mPaint.alpha = otherWeekTextAlpha
            strokePaint.alpha = otherWeekStrokeAlpha
            bgPaint.alpha = otherWeekBgAlpha
        }
        if (mStaticLayout == null) {
            val centerX = width - paddingRight - paddingLeft
            mStaticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout
                        .Builder
                        .obtain(text, 0, text.length, mTextPaint, centerX)
                        .setIncludePad(false)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .build()
            } else {
                StaticLayout(
                        text,
                        mTextPaint,
                        centerX,
                        Layout.Alignment.ALIGN_CENTER,
                        1.0f,
                        0f,
                        false
                )
            }
        }
        // 圆角从 4dp 升级到 14dp（可爱卡片风格）
        val radius = 14 * dpUnit
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
        canvas.clipRect(rect)
        canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        mStaticLayout!!.draw(canvas)
        canvas.restore()
        if (tipVisibility == 1) {
            path.moveTo(width - 12 * dpUnit, height - 6 * dpUnit)
            path.lineTo(width - 6 * dpUnit, height - 6 * dpUnit)
            path.lineTo(width - 6 * dpUnit, height - 12 * dpUnit)
            path.close()
            canvas.drawPath(path, mPaint)
        } else if (tipVisibility == -1) {
            canvas.drawLine(width - 12 * dpUnit,
                    height - 6 * dpUnit,
                    width - 6 * dpUnit,
                    height - 12 * dpUnit, mPaint)
            canvas.drawLine(width - 6 * dpUnit,
                    height - 6 * dpUnit,
                    width - 12 * dpUnit,
                    height - 12 * dpUnit, mPaint)
        }
    }

    companion object {
        const val TIP_INVISIBLE = 0
        const val TIP_VISIBLE = 1
        const val TIP_ERROR = -1
        const val TIP_OTHER_WEEK = 2
    }
}
