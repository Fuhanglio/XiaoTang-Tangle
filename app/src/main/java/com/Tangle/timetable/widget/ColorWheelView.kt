package com.Tangle.timetable.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.Tangle.timetable.widget.colorpicker.AlphaPatternDrawable
import splitties.dimensions.dip
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 圆盘调色板：
 * - 外圈是色相环（Hue），顺时针、红色在正上方；
 * - 内圈圆盘调节饱和度（距圆心越远越高）与亮度（上亮下暗）；
 * - 圆心显示当前选中颜色预览；
 * - 手指在环上拖动改色相，在圆盘内拖动改饱和度/亮度。
 */
class ColorWheelView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var hue = 300f
    private var sat = 0.5f
    private var value = 0.9f
    private var alpha = 0xFF

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var discCache: Bitmap? = null
    private var cachedHue = -1f

    var onColorChanged: ((Int) -> Unit)? = null

    val currentColor: Int
        get() = Color.HSVToColor(alpha, floatArrayOf(hue, sat, value))

    /** 设置当前颜色并刷新选择器位置 */
    fun setColor(color: Int, notify: Boolean = false) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        alpha = Color.alpha(color)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        discCache = null
        invalidate()
        if (notify) onColorChanged?.invoke(currentColor)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = min(
                if (width > 0) width else dip(280),
                if (height > 0) height else dip(280)
        )
        setMeasuredDimension(size, size)
    }

    private fun geom(): FloatArray {
        val cx = width / 2f
        val cy = height / 2f
        val outerR = min(cx, cy) - dip(6)
        val ringW = outerR * 0.15f
        val innerR = outerR - ringW - dip(4)
        return floatArrayOf(cx, cy, outerR, ringW, innerR)
    }

    override fun onDraw(canvas: Canvas) {
        val g = geom()
        val cx = g[0]; val cy = g[1]; val outerR = g[2]; val ringW = g[3]; val innerR = g[4]
        if (width <= 0 || height <= 0 || innerR <= 0f) return

        drawHueRing(canvas, cx, cy, outerR, ringW)
        drawSvDisc(canvas, cx, cy, innerR)
        drawSelectors(canvas, cx, cy, outerR, ringW, innerR)
        drawCenterPreview(canvas, cx, cy, innerR)
    }

    private fun drawHueRing(canvas: Canvas, cx: Float, cy: Float, outerR: Float, ringW: Float) {
        val hueColors = IntArray(361) { i ->
            Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f))
        }
        ringPaint.shader = SweepGradient(cx, cy, hueColors, null)
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = ringW
        canvas.save()
        canvas.rotate(-90f, cx, cy)  // 让红色（hue=0）转到正上方
        canvas.drawCircle(cx, cy, outerR - ringW / 2f, ringPaint)
        canvas.restore()
    }

    private fun drawSvDisc(canvas: Canvas, cx: Float, cy: Float, innerR: Float) {
        if (discCache == null || cachedHue != hue) {
            val size = (innerR * 2).toInt()
        if (size <= 0) return
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val pure = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))

            // 底层：圆心白 → 边缘纯色（饱和度径向衰减）
            val radial = RadialGradient(
                    innerR, innerR, innerR,
                    Color.WHITE, pure, Shader.TileMode.CLAMP
            )
            val p1 = Paint(Paint.ANTI_ALIAS_FLAG)
            p1.shader = radial
            c.drawCircle(innerR, innerR, innerR, p1)

            // 上层：顶部透明 → 底部黑色（亮度纵向衰减），与底层相乘
            val black = LinearGradient(
                    0f, 0f, 0f, innerR * 2f,
                    Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP
            )
            val p2 = Paint(Paint.ANTI_ALIAS_FLAG)
            p2.shader = black
            p2.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            c.drawCircle(innerR, innerR, innerR, p2)

            discCache?.recycle()
            discCache = bmp
            cachedHue = hue
        }
        val rect = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
        canvas.drawBitmap(discCache!!, null, rect, null)
    }

    private fun drawSelectors(canvas: Canvas, cx: Float, cy: Float, outerR: Float, ringW: Float, innerR: Float) {
        // 色相环上的选择器
        val ringRad = Math.toRadians((hue - 90).toDouble())
        val rx = cx + (outerR - ringW / 2f) * cos(ringRad).toFloat()
        val ry = cy + (outerR - ringW / 2f) * sin(ringRad).toFloat()
        selectorPaint.color = Color.WHITE
        selectorPaint.style = Paint.Style.FILL
        canvas.drawCircle(rx, ry, ringW * 0.42f, selectorPaint)
        selectorPaint.color = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        canvas.drawCircle(rx, ry, ringW * 0.28f, selectorPaint)

        // 圆盘内的选择器
        val theta = Math.toRadians((hue - 90).toDouble())
        var mx = cx + sat * innerR * cos(theta).toFloat()
        var my = cy - innerR + (1f - value) * 2f * innerR
        // 限制在圆盘内
        val dx = mx - cx
        val dy = my - cy
        val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        if (dist > innerR - dip(2)) {
            val scale = (innerR - dip(2)) / dist
            mx = cx + dx * scale
            my = cy + dy * scale
        }
        selectorPaint.color = currentColor
        selectorPaint.style = Paint.Style.FILL
        canvas.drawCircle(mx, my, dip(7).toFloat(), selectorPaint)
        selectorPaint.color = Color.WHITE
        selectorPaint.style = Paint.Style.STROKE
        selectorPaint.strokeWidth = dip(2).toFloat()
        canvas.drawCircle(mx, my, dip(7).toFloat(), selectorPaint)
    }

    private fun drawCenterPreview(canvas: Canvas, cx: Float, cy: Float, innerR: Float) {
        val r = innerR * 0.30f
        centerPaint.color = currentColor
        centerPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, r, centerPaint)
        centerRingPaint.color = Color.WHITE
        centerRingPaint.style = Paint.Style.STROKE
        centerRingPaint.strokeWidth = dip(2).toFloat()
        canvas.drawCircle(cx, cy, r, centerRingPaint)
        centerRingPaint.color = 0x66000000
        centerRingPaint.strokeWidth = 1f
        canvas.drawCircle(cx, cy, r + dip(2), centerRingPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val g = geom()
        val cx = g[0]; val cy = g[1]; val outerR = g[2]; val ringW = g[3]; val innerR = g[4]
        if (width <= 0 || height <= 0 || innerR <= 0f) return super.onTouchEvent(event)
        val dx = event.x - cx
        val dy = event.y - cy
        val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (dist > innerR + dip(2) && dist <= outerR + dip(6)) {
                    // 色相环
                    val ang = Math.toDegrees(atan2(dy, dx).toDouble())
                    hue = ((ang + 90 + 360) % 360).toFloat()
                    discCache = null
                } else if (dist <= innerR + dip(8)) {
                    // 饱和度 / 亮度盘
                    val cx2 = dx.coerceIn(-innerR, innerR)
                    val cy2 = dy.coerceIn(-innerR, innerR)
                    val d = Math.sqrt((cx2 * cx2 + cy2 * cy2).toDouble()).toFloat()
                    sat = (d / innerR).coerceIn(0f, 1f)
                    value = (1f - (cy2 + innerR) / (2f * innerR)).coerceIn(0f, 1f)
                } else {
                    return super.onTouchEvent(event)
                }
                invalidate()
                onColorChanged?.invoke(currentColor)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

/**
 * 透明度滑块：棋盘格底 + 左透明右不透明渐变 + 可拖动圆点。
 */
class AlphaSliderView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var color = Color.RED
    private var alphaValue = 255

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pattern: AlphaPatternDrawable? = null

    var onAlphaChanged: ((Int) -> Unit)? = null

    fun setColor(c: Int) {
        color = c
        invalidate()
    }

    fun setAlpha(a: Int, notify: Boolean = false) {
        alphaValue = a.coerceIn(0, 255)
        invalidate()
        if (notify) onAlphaChanged?.invoke(alphaValue)
    }

    fun getAlphaValue(): Int = alphaValue

    private fun barRect(): RectF {
        val h = dip(20).toFloat()
        val top = (height - h) / 2f
        return RectF(dip(8).toFloat(), top, width - dip(8).toFloat(), top + h)
    }

    override fun onDraw(canvas: Canvas) {
        val rect = barRect()
        val radius = rect.height() / 2f

        if (pattern == null) {
            pattern = AlphaPatternDrawable(dip(4))
        }
        pattern?.let {
            it.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
            it.draw(canvas)
        }

        val opaque = Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
        barPaint.shader = LinearGradient(
                rect.left, 0f, rect.right, 0f,
                intArrayOf(Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)), opaque),
                null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, barPaint)

        // 滑块圆点
        val tx = rect.left + alphaValue / 255f * rect.width()
        thumbPaint.color = Color.WHITE
        thumbPaint.style = Paint.Style.FILL
        canvas.drawCircle(tx, rect.centerY(), dip(10).toFloat(), thumbPaint)
        thumbPaint.color = opaque
        canvas.drawCircle(tx, rect.centerY(), dip(6).toFloat(), thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val rect = barRect()
                val ratio = ((event.x - rect.left) / rect.width()).coerceIn(0f, 1f)
                alphaValue = (ratio * 255).toInt()
                invalidate()
                onAlphaChanged?.invoke(alphaValue)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
