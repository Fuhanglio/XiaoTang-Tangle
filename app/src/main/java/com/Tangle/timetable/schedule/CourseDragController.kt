package com.Tangle.timetable.schedule

import android.content.ClipData
import android.graphics.Color
import android.graphics.Point
import android.view.DragEvent
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.Tangle.timetable.R
import com.Tangle.timetable.bean.CourseBean
import kotlin.math.floor
import kotlin.math.roundToInt

/** 一次拖拽落定的结果 */
data class CourseDragResult(
        /** 原位置：周几 */
        val srcDay: Int,
        /** 原位置：第几节（起始节） */
        val srcNode: Int,
        /** 新位置：周几 */
        val dstDay: Int,
        /** 新位置：第几节（起始节） */
        val dstNode: Int,
        /** 一起移动的课程明细：同一格同一门课的所有周次分段 */
        val group: List<CourseBean>
)

/**
 * 课表网格内「长按拖动课程块」的控制器。
 *
 * 为什么不用 DragEvent 之外的方案：本项目网格是自建 ConstraintLayout + 绝对定位的 TipTextView，
 * ItemTouchHelper 用不上；而系统拖拽（startDrag）自带跟手、且天然不会和 ScrollView / ViewPager
 * 的滚动手势打架（长按触发后触摸序列转成拖拽通道），是最省事也最稳的路子。
 *
 * 坐标基准：拖拽事件由 [container]（即 ui.content，滚动内容本身）接收，所以 DragEvent 的
 * x/y 与课程块 topMargin 处在同一个坐标系里，不受 ScrollView 滚动偏移影响。
 */
class CourseDragController(
        private val container: ConstraintLayout,
        private val dayMap: IntArray,
        /** 一节课格子的高度（px，含内容） */
        private val itemHeight: Int,
        /** 相邻两节课之间的间距（px） */
        private val marTop: Int,
        /** 一天最多几节课 */
        private val nodes: Int,
        private val onResult: (CourseDragResult) -> Unit
) {

    /** 一天的列在 container 坐标系里的水平范围 */
    private class Col(val day: Int, val left: Float, val right: Float)

    private var cols: List<Col> = emptyList()

    /** 网格顶边（第 1 节所在行的上沿）在 container 坐标系里的 y */
    private var gridTop = 0f

    private var srcDay = 0
    private var srcNode = 0
    private var step = 1
    private var group: List<CourseBean> = emptyList()

    private var highlight: View? = null
    private var tagDay = -1
    private var tagNode = -1

    /**
     * 拖拽落点的接收层：与 [container] 左上角严格重合、但位于最上层。
     *
     * 它存在的唯一理由是**把 DragEvent 的坐标原点钉死**。DragEvent 的 x/y 是相对
     * "接收并处理该事件的 View" 的，而事件到底先给父还是先给子、中间的坐标偏移怎么回滚，
     * 属于系统实现细节，不该被依赖。让一个与网格同原点的 View 专门接收，坐标就必然
     * 与课程块 topMargin 落在同一个坐标系里。
     *
     * 它是透明且不可点击的：普通点击/长按会穿透到下面的课程块（ViewGroup 在子 View
     * 返回 false 时会继续尝试更下层的子 View），所以不影响原有交互。
     */
    private val dropZone = View(container.context).apply {
        setBackgroundColor(Color.TRANSPARENT)
    }

    /** 相邻两节起始位置之间的间距 */
    private val pitch: Int get() = itemHeight + marTop

    init {
        // 尺寸与 container 完全一致：左右撑满、上下撑满（container 高度由内容撑开）
        container.addView(dropZone, ConstraintLayout.LayoutParams(0, 0).apply {
            startToStart = ConstraintSet.PARENT_ID
            endToEnd = ConstraintSet.PARENT_ID
            topToTop = ConstraintSet.PARENT_ID
            bottomToBottom = ConstraintSet.PARENT_ID
        })
        dropZone.setOnDragListener { _, event -> handleDrag(event) }
        // container 自身也监听一份：两者原点重合，坐标口径一致，属于兜底
        container.setOnDragListener { _, event -> handleDrag(event) }
    }

    /**
     * 开始拖动。
     * @param source    被拖的课程块（用作拖影）
     * @param bean      这一块显示的课程
     * @param dayCourses 该天的全部课程（用来找出同组的其它周次分段）
     * @param day       该天是周几（1=周一 … 7=周日）
     */
    @Suppress("DEPRECATION")
    fun startDrag(source: View, bean: CourseBean, dayCourses: List<CourseBean>, day: Int) {
        srcDay = day
        srcNode = bean.startNode
        step = if (bean.step <= 0) 1 else bean.step
        // 同名 + 同起始节 + 同时长 = 课表上显示为同一块的多个周次分段（单双周、散周），
        // 一起移动，否则只拖走其中一条会在原处留下浅色残块。
        group = dayCourses.filter {
            it.courseName == bean.courseName && it.startNode == bean.startNode && it.step == bean.step
        }.ifEmpty { listOf(bean) }

        tagDay = -1
        tagNode = -1
        measureGrid()

        source.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        // 用 startDrag（API 11+）而不是 startDragAndDrop（API 24+），保住 minSdk 21
        source.startDrag(
                ClipData.newPlainText("course", bean.courseName),
                CourseDragShadow(source), null, 0)
    }

    private fun handleDrag(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> return true

            DragEvent.ACTION_DRAG_LOCATION -> {
                // 第一次收到位置事件时网格已完成布局，此时量取列边界最准
                if (cols.isEmpty()) measureGrid()
                previewAt(event.x, event.y)
                return true
            }

            DragEvent.ACTION_DROP -> {
                hideHighlight()
                val day = dayAt(event.x)
                val node = nodeAt(event.y)
                // 原地放下不算移动，别白写一次库
                if (day != srcDay || node != srcNode) {
                    onResult(CourseDragResult(srcDay, srcNode, day, node, group))
                }
                return true
            }

            DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                hideHighlight()
                return true
            }
        }
        return true
    }

    /** 量取每天的列范围与网格顶边（都在 container 坐标系下） */
    private fun measureGrid() {
        val base = IntArray(2)
        container.getLocationInWindow(base)
        val list = mutableListOf<Col>()
        var top = 0f
        var firstCol = true
        for (d in 1..7) {
            val idx = dayMap[d]
            if (idx <= 0) continue
            val panel = container.findViewById<View>(R.id.anko_ll_week_panel_0 + idx - 1) ?: continue
            if (panel.width <= 0) continue
            val loc = IntArray(2)
            panel.getLocationInWindow(loc)
            val left = (loc[0] - base[0]).toFloat()
            if (firstCol) {
                // 各天的列容器都在同一水平线上，取第一个的顶边作为网格顶
                top = (loc[1] - base[1]).toFloat()
                firstCol = false
            }
            list.add(Col(d, left, left + panel.width))
        }
        cols = list
        gridTop = top
    }

    /** 手指横向落在哪天；落在列外的边距/时间列上时吸附到最近的一列 */
    private fun dayAt(x: Float): Int {
        if (cols.isEmpty()) return srcDay
        cols.firstOrNull { x >= it.left && x < it.right }?.let { return it.day }
        return if (x < cols.first().left) cols.first().day else cols.last().day
    }

    /** 手指纵向落在第几节（按起始节算），并保证整块放得下 */
    private fun nodeAt(y: Float): Int {
        var n = floor((y - gridTop - marTop) / pitch).toInt() + 1
        if (n < 1) n = 1
        if (n > nodes) n = nodes
        val maxStart = nodes - step + 1
        if (maxStart >= 1 && n > maxStart) n = maxStart
        return n
    }

    /** 实时把落点高亮块挪到目标格子 */
    private fun previewAt(x: Float, y: Float) {
        val day = dayAt(x)
        val node = nodeAt(y)
        if (day == tagDay && node == tagNode) return
        tagDay = day
        tagNode = node

        val col = cols.firstOrNull { it.day == day } ?: return
        val view = ensureHighlight()
        view.layoutParams = ConstraintLayout.LayoutParams(
                (col.right - col.left).roundToInt(),
                itemHeight * step + marTop * (step - 1)
        ).apply {
            startToStart = ConstraintSet.PARENT_ID
            topToTop = ConstraintSet.PARENT_ID
            marginStart = col.left.roundToInt()
            topMargin = (gridTop + (node - 1) * pitch + marTop).roundToInt()
        }
        view.visibility = View.VISIBLE
    }

    /** 高亮块懒创建：加在 container 最后 → 绘制在所有课程块之上 */
    private fun ensureHighlight(): View {
        highlight?.let { return it }
        val v = View(container.context).apply {
            setBackgroundResource(R.drawable.course_drag_highlight)
            isClickable = false
            isFocusable = false
        }
        container.addView(v)
        highlight = v
        return v
    }

    private fun hideHighlight() {
        highlight?.visibility = View.GONE
        tagDay = -1
        tagNode = -1
    }

    /** 拖影：比原卡片略小一圈，手指抓在中心，看着像把卡片拎起来了 */
    private class CourseDragShadow(private val src: View) : View.DragShadowBuilder(src) {
        override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
            val w = (src.width * 0.92f).roundToInt().coerceAtLeast(1)
            val h = (src.height * 0.92f).roundToInt().coerceAtLeast(1)
            outShadowSize.set(w, h)
            outShadowTouchPoint.set(w / 2, h / 2)
        }
    }
}
