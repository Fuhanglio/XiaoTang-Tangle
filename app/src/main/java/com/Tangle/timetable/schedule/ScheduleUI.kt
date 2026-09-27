package com.Tangle.timetable.schedule

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.Tangle.timetable.DashedLineView
import com.Tangle.timetable.R
import com.Tangle.timetable.base_view.Ui
import com.Tangle.timetable.bean.TableBean
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.ThemeManager
import com.Tangle.timetable.utils.getPrefer
import splitties.dimensions.dip
import splitties.dimensions.dp

class ScheduleUI(override val ctx: Context, table: TableBean, day: Int, forWidget: Boolean = false) : Ui {

    companion object {
        /** ids.xml 里 node 系列的**声明顺序**（1-based：NODE_IDS[1] 是第 1 行容器）。
         *  顺序 = ids.xml 声明序，与名字里的数字无关。改 ids.xml 时同步改这张表。 */
        private val NODE_IDS = intArrayOf(
            0,                                  // 索引 0 占位不用
            R.id.anko_tv_node1, R.id.anko_tv_node2, R.id.anko_tv_node3,
            R.id.anko_tv_node4, R.id.anko_tv_node5, R.id.anko_tv_node6,
            R.id.anko_tv_node7, R.id.anko_tv_node8, R.id.anko_tv_node9,
            R.id.anko_tv_node91, R.id.anko_tv_node92, R.id.anko_tv_node93,
            R.id.anko_tv_node94, R.id.anko_tv_node95, R.id.anko_tv_node96,
            R.id.anko_tv_node97, R.id.anko_tv_node98, R.id.anko_tv_node99,
            R.id.anko_tv_node991, R.id.anko_tv_node992, R.id.anko_tv_node993,
            R.id.anko_tv_node994, R.id.anko_tv_node995, R.id.anko_tv_node996,
            R.id.anko_tv_node997, R.id.anko_tv_node998, R.id.anko_tv_node999,
            R.id.anko_tv_node9991, R.id.anko_tv_node9992, R.id.anko_tv_node9993
        )

        /** 别写死 30：可用行号上限由表长推出 */
        private val MAX_NODES: Int get() = NODE_IDS.size - 1

        /** row 为 1-based 行号；越界夹紧并报错，绝不静默别名到别的控件 */
        internal fun nodeId(row: Int): Int {
            if (row in 1..MAX_NODES) return NODE_IDS[row]
            Log.e("ScheduleUI", "nodeId out of range: $row (1..$MAX_NODES)")
            return NODE_IDS[row.coerceIn(1, MAX_NODES)]
        }
    }

    private var col = 6

    var showTimeDetail = true

    val dayMap = IntArray(8)
    val itemHeight = ctx.dip(if (forWidget) table.widgetItemHeight else table.itemHeight)
    val textColor = if (forWidget) table.widgetTextColor else table.textColor

    // 课程行间距（与 ScheduleViewModel.marTop 同源：R.dimen.weekItemMarTop）
    private val marTop = ctx.resources.getDimensionPixelSize(R.dimen.weekItemMarTop)

    // 所有网格虚线 View（保留引用用于显隐切换，不 remove）
    private val dashedViews = mutableListOf<View>()

    /**
     * W9-01：本文件里的 `nodes` **不只是业务数据，还直接当行号用**（nodeId(row) 按声明序
     * 取 id，见 NODE_IDS）。node 系列只备了 [MAX_NODES] 个，`nodes` 超出会触发 nodeId 的
     * “日志 + 夹紧”而不是静默落到别的控件上。设置页 SeekBar 已限 1..30，但**数据迁移与
     * 文件导入**进来的课表没有这层保护，所以在 UI 侧统一夹紧一次，作为最后一道防线。
     */
    private val nodes = table.nodes.coerceIn(1, MAX_NODES)

    init {
        for (i in 1..7) {
            if (!table.sundayFirst || !table.showSun) {
                if (!table.showSat && i == 7) {
                    dayMap[i] = 6
                } else {
                    dayMap[i] = i
                }
            } else {
                if (i == 7) {
                    dayMap[i] = 1
                } else {
                    dayMap[i] = i + 1
                }
            }
        }
        if (table.showSat) {
            col++
        } else {
            dayMap[6] = -1
        }
        if (table.showSun) {
            col++
        } else {
            dayMap[7] = -1
        }
    }

    val content = ConstraintLayout(ctx).apply {
        id = R.id.anko_cl_content_panel
        val timeSize = when (col) {
            7 -> 9f
            6 -> 10f
            else -> 8f
        }
        for (i in 1..nodes) {
            addView(FrameLayout(context).apply {
                id = nodeId(i)
                if (showTimeDetail) {
                    addView(AppCompatTextView(context).apply {
                        id = R.id.tv_start
                        setTextColor(textColor)
                        //gravity = Gravity.CENTER
                        //textAlignment = View.TEXT_ALIGNMENT_CENTER
                        setSingleLine()
                        setTextSize(TypedValue.COMPLEX_UNIT_DIP, timeSize)
                    }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                    })
                    addView(AppCompatTextView(context).apply {
                        id = R.id.tv_end
                        setTextColor(textColor)
                        setSingleLine()
                        setTextSize(TypedValue.COMPLEX_UNIT_DIP, timeSize)
                    }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                    })
                }
                addView(AppCompatTextView(context).apply {
                    setTextColor(textColor)
                    text = i.toString()
                    textSize = 12f
                    setSingleLine()
                }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                })
            }, ConstraintLayout.LayoutParams(0, itemHeight).apply {
                topMargin = dip(2)
                endToStart = R.id.anko_ll_week_panel_0
                horizontalWeight = 0.5f
                startToStart = ConstraintSet.PARENT_ID
                when (i) {
                    1 -> {
                        bottomToTop = nodeId(i + 1)
                        topToTop = ConstraintSet.PARENT_ID
                        verticalBias = 0f
                        verticalChainStyle = ConstraintSet.CHAIN_PACKED
                    }
                    nodes -> {
                        //bottomToTop = R.id.anko_navigation_bar_view
                        bottomToBottom = ConstraintSet.PARENT_ID
                        topToBottom = nodeId(i - 1)
                    }
                    else -> {
                        bottomToTop = nodeId(i + 1)
                        topToBottom = nodeId(i - 1)
                    }
                }
            })
        }

        if (!forWidget && context.getPrefer().getBoolean(Const.KEY_SCHEDULE_BLANK_AREA, true)) {
            addView(View(context), ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, itemHeight * 4).apply {
                topToBottom = nodeId(nodes)
                bottomToBottom = ConstraintSet.PARENT_ID
                startToStart = ConstraintSet.PARENT_ID
                endToEnd = ConstraintSet.PARENT_ID
            })
        }

        // ===== 浅灰色虚线网格：横线按节次、竖线按星期 =====
        // 必须加在星期列容器（anko_ll_week_panel_*）之前：ConstraintLayout 的绘制顺序 = 子 View 加入顺序，
        // 这样虚线位于课程胶囊之下、网格背景之上，且不拦截触摸（见 DashedLineView）。
        // 位置全部由 itemHeight + marTop 现算，因此"课程格子高度/一天课程节数"变化时会跟随重算。
        if (!forWidget) {
            val dashColor = ContextCompat.getColor(ctx, R.color.grid_dashed)
            val thickness = ctx.dip(1)
            val pitch = itemHeight + marTop
            for (n in 1 until nodes) {
                val boundaryTop = (n - 1) * pitch + marTop + itemHeight
                val boundaryBottom = n * pitch + marTop
                val line = DashedLineView(ctx, DashedLineView.HORIZONTAL).apply {
                    lineColor = dashColor
                }
                dashedViews.add(line)
                addView(line, ConstraintLayout.LayoutParams(0, thickness).apply {
                    startToStart = R.id.anko_ll_week_panel_0
                    endToEnd = ConstraintSet.PARENT_ID
                    topToTop = ConstraintSet.PARENT_ID
                    topMargin = (boundaryTop + boundaryBottom) / 2
                })
            }
            for (b in 0 until col - 1) {
                val line = DashedLineView(ctx, DashedLineView.VERTICAL).apply {
                    lineColor = dashColor
                }
                dashedViews.add(line)
                addView(line, ConstraintLayout.LayoutParams(thickness, 0).apply {
                    startToStart = R.id.anko_ll_week_panel_0 + b
                    topToTop = nodeId(1)
                    bottomToBottom = nodeId(nodes)
                })
            }
            // 构造时即读取偏好应用显隐（课表重建后开关依然生效）
            applyDashedGridVisibility()
        }

        for (i in 0 until col - 1) {
            addView(FrameLayout(context).apply { id = R.id.anko_ll_week_panel_0 + i }, ConstraintLayout.LayoutParams(0,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dip(1)
                marginEnd = dip(1)
                horizontalWeight = 1f
                when (i) {
                    0 -> {
                        startToEnd = nodeId(1)
                        endToStart = R.id.anko_ll_week_panel_0 + i + 1
                    }
                    col - 2 -> {
                        startToEnd = R.id.anko_ll_week_panel_0 + i - 1
                        endToEnd = ConstraintSet.PARENT_ID
                        if (!forWidget) {
                            marginEnd = if (col < 8) {
                                dip(8)
                            } else {
                                dip(4)
                            }
                        }
                    }
                    else -> {
                        startToEnd = R.id.anko_ll_week_panel_0 + i - 1
                        endToStart = R.id.anko_ll_week_panel_0 + i + 1
                    }
                }
            })
        }
    }

    /**
     * 读取 show_dashed_grid 偏好并应用网格虚线显隐。
     * false 时把所有虚线 View 设为 GONE（保留对象，便于再开启时秒切回）。
     */
    fun applyDashedGridVisibility() {
        if (dashedViews.isEmpty()) return
        val visible = ctx.getPrefer().getBoolean(Const.KEY_SHOW_DASHED_GRID, true)
        val vis = if (visible) View.VISIBLE else View.GONE
        dashedViews.forEach { it.visibility = vis }
    }

    val scrollView = ScrollView(ctx).apply {
        id = R.id.anko_sv_schedule
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        addView(content)
    }

    override val root = ConstraintLayout(ctx).apply {
        val textAlphaColor = ColorUtils.setAlphaComponent(textColor, (0.32 * (textColor shr 24 and 0xff)).toInt())
        val headerBgColor = if (forWidget) Color.TRANSPARENT else ThemeManager.getColor(ctx, ThemeManager.HEADER)
        val todayColor = if (forWidget) Color.TRANSPARENT else ThemeManager.getColor(ctx, ThemeManager.TODAY)
        val todayBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(todayColor)
            cornerRadius = dp(8).toFloat()
        }
        for (i in 0 until col) {
            addView(AppCompatTextView(context).apply {
                id = R.id.anko_tv_title_col0 + i
                setPadding(dip(4), dip(8), dip(4), dip(8))
                textSize = 12f
                gravity = Gravity.CENTER
                setLineSpacing(dip(2).toFloat(), 1f)
                val isTodayCol = day > 0 && i == dayMap[day]
                if (i == 0 || isTodayCol) {
                    typeface = Typeface.DEFAULT_BOLD
                } else {
                    setTextColor(textAlphaColor)
                }
                if (i == 0) {
                    // 时间列：加粗 + 深棕主色调
                    setTextColor(ThemeManager.getColor(ctx, ThemeManager.PRIMARY))
                    typeface = Typeface.DEFAULT_BOLD
                } else if (isTodayCol && !forWidget) {
                    // 今天日期列：深棕色圆角方块 + 白色文字
                    background = todayBg
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                } else if (!forWidget) {
                    when {
                        Color.alpha(headerBgColor) != 0 -> setBackgroundColor(headerBgColor)
                    }
                }
            }, ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
                when (i) {
                    0 -> {
                        horizontalWeight = 0.5f
                        startToStart = ConstraintSet.PARENT_ID
                        topToTop = ConstraintSet.PARENT_ID
                        endToStart = R.id.anko_tv_title_col0 + i + 1
                    }
                    col - 1 -> {
                        horizontalWeight = 1f
                        startToEnd = R.id.anko_tv_title_col0 + i - 1
                        endToEnd = ConstraintSet.PARENT_ID
                        baselineToBaseline = R.id.anko_tv_title_col0 + i - 1
                        if (!forWidget) {
                            marginEnd = if (col < 8) {
                                dip(8)
                            } else {
                                dip(4)
                            }
                        }
                    }
                    else -> {
                        horizontalWeight = 1f
                        startToEnd = R.id.anko_tv_title_col0 + i - 1
                        endToStart = R.id.anko_tv_title_col0 + i + 1
                        baselineToBaseline = R.id.anko_tv_title_col0 + i - 1
                    }
                }
            })
        }

        addView(scrollView, ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT).apply {
            bottomToBottom = ConstraintSet.PARENT_ID
            topToBottom = R.id.anko_tv_title_col0
            startToStart = ConstraintSet.PARENT_ID
            endToEnd = ConstraintSet.PARENT_ID
        })
    }
}
