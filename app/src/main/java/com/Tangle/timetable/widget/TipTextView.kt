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
import kotlin.math.ceil

/**
 * 课表格子里的一块课程。
 *
 * 排版规则（长课名修复）：
 * 格子里的内容按重要性从高到低分成四段
 *     课名  >  教室  >  开始时间  >  单双周
 * 先按**实际排版出来的行数**把「教室 / 单双周 / 开始时间」的高度算清楚（教室名长了自己也会换行，
 * 必须按真实行数留位，否则课名会把教室顶出格子），剩下的高度才给课名：
 *   1. 教室那一行一定显示（它是最容易被长课名挤掉的信息）；
 *   2. 课名做**限行 + 省略号**，最多 [MAX_NAME_LINES] 行，不再任其换行把后面的行顶出格子；
 *   3. 只剩「课名 1 行 + 教室」都放不下的极端情况，才依次丢掉「单双周」和「开始时间」。
 *
 * 之所以由 View 自己排版而不是在拼字符串时截断：格子高度随"课程格子高度"设置、节次（step）、
 * 文字大小三处变化，只有拿到实际宽高后才能算出到底能排几行。
 */
@SuppressLint("ViewConstructor")
class TipTextView(context: Context) : View(context) {

    var tipVisibility = 0
        set(value) {
            field = value
            invalidate()
        }

    // ===== 显示内容（init 时写入，布局在第一次绘制时按实际宽高构建）=====
    private var courseName = ""
    private var roomText = ""
    private var weekText = ""
    private var timeText = ""

    private var headLayout: StaticLayout? = null
    private var mainLayout: StaticLayout? = null
    private var tailLayout: StaticLayout? = null

    private lateinit var mTextPaint: TextPaint
    private lateinit var mPaint: Paint
    private lateinit var bgPaint: Paint
    private lateinit var strokePaint: Paint
    private val path = Path()
    private val rect = RectF()
    private val dpUnit = dp(1)
    private var baseTextAlpha = 255
    private var baseBgAlpha = 255
    private var baseStrokeAlpha = 255
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

    /**
     * @param courseName 课名（可多行，放不下时自动限行加省略号）
     * @param room       教室（不含 @，空串表示没有）
     * @param weekTip    单双周 / [非本周] 提示（空串表示没有）
     * @param timeText   本节的开始时间（仅在课表开启"显示时间"时传入，否则给空串）
     */
    fun init(courseName: String, room: String, weekTip: String, timeText: String,
             txtSize: Int, txtColor: Int, bgColor: Int, bgAlpha: Int, stroke: Int) {
        this.courseName = courseName
        this.roomText = room
        this.weekText = weekTip
        this.timeText = timeText
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
        baseTextAlpha = mTextPaint.alpha
        baseBgAlpha = bgPaint.alpha
        baseStrokeAlpha = strokePaint.alpha
        otherWeekTextAlpha = (mTextPaint.alpha * 0.3).toInt()
        otherWeekBgAlpha = (bgPaint.alpha * 0.3).toInt()
        otherWeekStrokeAlpha = (strokePaint.alpha * 0.3).toInt()
        clearLayouts()
    }

    private fun clearLayouts() {
        headLayout = null
        mainLayout = null
        tailLayout = null
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 格子尺寸变了（改设置、转屏、切横竖排）要按新宽高重排
        if (w != oldw || h != oldh) clearLayouts()
    }

    private fun makeLayout(text: String, w: Int): StaticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        StaticLayout
                .Builder
                .obtain(text, 0, text.length, mTextPaint, w)
                .setIncludePad(false)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
    } else {
        StaticLayout(text, mTextPaint, w, Layout.Alignment.ALIGN_CENTER, 1.0f, 0f, false)
    }

    /**
     * 把 [text] 压进 [maxLines] 行以内：超出的部分截掉并补一个省略号。
     * 直接用 StaticLayout 逐次回退试排，是因为 API 21 没有 setMaxLines/setEllipsize，
     * 而且中英文混排下"按字符数估算"根本不靠谱。
     */
    private fun makeEllipsizedLayout(text: String, maxLines: Int, w: Int): StaticLayout {
        val full = makeLayout(text, w)
        if (full.lineCount <= maxLines) return full
        var keep = (full.getLineEnd(maxLines - 1) - 1).coerceAtLeast(1)
        while (keep > 1) {
            val layout = makeLayout(text.substring(0, keep) + "…", w)
            if (layout.lineCount <= maxLines) return layout
            keep--
        }
        return makeLayout("…", w)
    }

    /** 按当前宽高算出四段内容各自能排几行，并构建好布局 */
    private fun ensureLayouts() {
        if (mainLayout != null) return
        if (!::mTextPaint.isInitialized) return
        val contentW = (width - paddingLeft - paddingRight).coerceAtLeast(1)
        val availH = (height - paddingTop - paddingBottom).coerceAtLeast(1)
        // 单行高度（往上取整，宁可少算一行也别让最后一行被裁掉）
        val lineH = ceil((mTextPaint.descent() - mTextPaint.ascent()).toDouble()).toInt().coerceAtLeast(1)
        val availLines = (availH / lineH).coerceAtLeast(1)

        var showTime = timeText.isNotEmpty()
        var showWeek = weekText.isNotEmpty()
        val showRoom = roomText.isNotEmpty()

        fun buildHead(time: Boolean): StaticLayout? =
                if (time) makeLayout(timeText, contentW) else null

        /** 尾部 = 「@教室」一行，可能再跟一行单双周；教室名长了自己也会换行 */
        fun buildTail(room: Boolean, week: Boolean): StaticLayout? {
            val sb = StringBuilder()
            if (room) sb.append("@").append(roomText)
            if (week) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(weekText)
            }
            return if (sb.isEmpty()) null else makeLayout(sb.toString(), contentW)
        }

        fun linesOf(layout: StaticLayout?): Int = layout?.lineCount ?: 0

        var head = buildHead(showTime)
        var tail = buildTail(showRoom, showWeek)
        // 关键：教室/单双周要占几行，按**实际排出来的行数**算，不能固定按 1 行估。
        // 旧写法固定按 1 行算，教室名稍微长一点就换行成 2 行，课名占满剩余行后
        // 尾部整体超出格子被裁掉 —— 这就是「长课名把教室名顶出格子」的真因。
        // 超容量时按「单双周 -> 开始时间」的顺序丢弃；两者都丢了还放不下就只能让尾部被裁。
        while (linesOf(head) + linesOf(tail) + 1 > availLines) {
            if (showWeek) {
                showWeek = false
                tail = buildTail(showRoom, false)
            } else if (showTime) {
                showTime = false
                head = buildHead(false)
            } else {
                break
            }
        }

        // 课名最多给 4 行：再长的课名排成 5、6 行既看不清也挤压别的信息
        val roomForName = (availLines - linesOf(head) - linesOf(tail)).coerceAtLeast(1)
        val mainLines = roomForName.coerceAtMost(MAX_NAME_LINES)

        headLayout = head
        mainLayout = makeEllipsizedLayout(courseName, mainLines, contentW)
        tailLayout = tail
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!::mTextPaint.isInitialized) return
        // [非本周]的浅色提示块整体降透明度；每次都显式赋值，避免状态切换后残留
        val otherWeek = tipVisibility == TIP_OTHER_WEEK
        mTextPaint.alpha = if (otherWeek) otherWeekTextAlpha else baseTextAlpha
        mPaint.alpha = if (otherWeek) otherWeekTextAlpha else baseTextAlpha
        strokePaint.alpha = if (otherWeek) otherWeekStrokeAlpha else baseStrokeAlpha
        bgPaint.alpha = if (otherWeek) otherWeekBgAlpha else baseBgAlpha

        ensureLayouts()

        // 圆角从 4dp 升级到 14dp（可爱卡片风格）
        val radius = 14 * dpUnit
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
        canvas.clipRect(rect)
        canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        // 四段内容自上而下顺次叠加，每画完一段把画布原点下移该段高度
        headLayout?.let {
            it.draw(canvas)
            canvas.translate(0f, it.height.toFloat())
        }
        mainLayout?.let {
            it.draw(canvas)
            canvas.translate(0f, it.height.toFloat())
        }
        tailLayout?.let { it.draw(canvas) }
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

        /** 课名最多排几行（超出加省略号）：再长也读不清，还会挤压教室等其它信息 */
        private const val MAX_NAME_LINES = 4
    }
}
