package com.Tangle.timetable.schedule

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.Tangle.timetable.AppDatabase
import com.Tangle.timetable.R
import com.Tangle.timetable.base_view.BaseFragment
import com.Tangle.timetable.bean.CourseBean
import com.Tangle.timetable.bean.CourseDetailBean
import com.Tangle.timetable.bean.TableBean
import com.Tangle.timetable.utils.AppWidgetUtils
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.CourseUtils
import com.Tangle.timetable.utils.ViewUtils
import com.Tangle.timetable.utils.getPrefer
import com.Tangle.timetable.widget.TipTextView
import es.dmoral.toasty.Toasty
import splitties.dimensions.dip
import splitties.snackbar.action
import splitties.snackbar.longSnack

class ScheduleFragment : BaseFragment() {

    private var week = 0
    private var preLoad = true
    private var weekDay = 1
    private lateinit var weekDate: List<String>
    private val viewModel by activityViewModels<ScheduleViewModel>()
    private lateinit var ui: ScheduleUI
    private var isLoaded = false
    private var dragController: CourseDragController? = null
    private lateinit var showCourseNumber: LiveData<Int>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            week = it.getInt("week")
            preLoad = it.getBoolean("preLoad")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        weekDay = CourseUtils.getWeekdayInt()
        ui = ScheduleUI(context!!, viewModel.table, if (week == viewModel.currentWeek) weekDay else -1)
        ui.showTimeDetail = context!!.getPrefer().getBoolean(Const.KEY_SCHEDULE_DETAIL_TIME, true)
        showCourseNumber = viewModel.getShowCourseNumber(week)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 网格内长按拖动课程。拖拽事件统一由 content（滚动内容本身）接收，
        // 这样 DragEvent 的坐标与课程块 topMargin 处在同一坐标系，不受 ScrollView 滚动偏移影响。
        dragController = CourseDragController(
                ui.content,
                ui.dayMap,
                ui.itemHeight,
                viewModel.marTop,
                viewModel.table.nodes
        ) { onCourseMoved(it) }

        weekDate = CourseUtils.getDateStringFromWeek(CourseUtils.countWeek(viewModel.table.startDate, viewModel.table.sundayFirst), week, viewModel.table.sundayFirst)
        ((view as ConstraintLayout).getViewById(R.id.anko_tv_title0) as AppCompatTextView).text = weekDate[0] + "\n月"
        var textView: AppCompatTextView?
        for (i in 1..7) {
            if (ui.dayMap[i] == -1) continue
            textView = view.getViewById(R.id.anko_tv_title0 + ui.dayMap[i]) as AppCompatTextView
            if (i == 7 && !viewModel.table.showSat && !viewModel.table.sundayFirst) {
                textView.text = viewModel.daysArray[i] + "\n${weekDate[7]}"
            } else if (!viewModel.table.showSun && viewModel.table.sundayFirst && i != 7) {
                textView.text = viewModel.daysArray[i] + "\n${weekDate[ui.dayMap[i] + 1]}"
            } else {
                textView.text = viewModel.daysArray[i] + "\n${weekDate[ui.dayMap[i]]}"
            }
        }
        // 节数可能被用户设置得大于时间段数量，直接按 nodes 索引会越界崩溃
        if (viewModel.timeList.isNotEmpty() && ui.showTimeDetail) {
            for (i in 0 until minOf(viewModel.table.nodes, viewModel.timeList.size)) {
                (ui.content.getViewById(R.id.anko_tv_node1 + i) as FrameLayout).apply {
                    findViewById<AppCompatTextView>(R.id.tv_start).text = viewModel.timeList[i].startTime
                    findViewById<AppCompatTextView>(R.id.tv_end).text = viewModel.timeList[i].endTime
                }
            }
        }
        if (preLoad) {
            for (i in 1..7) {
                viewModel.allCourseList[i - 1].observe(viewLifecycleOwner, Observer {
                    initWeekPanel(it, i, viewModel.table)
                })
            }
        }
        showCourseNumber.observe(viewLifecycleOwner, Observer {
            if (it == 0) {
                ui.content.visibility = View.GONE
                if (ui.root.getViewById(R.id.anko_empty_view) != null) {
                    return@Observer
                }
                val img = AppCompatImageView(context!!).apply {
                    setImageResource(R.drawable.ic_schedule_empty)
                }
                ui.root.addView(LinearLayoutCompat(context!!).apply {
                    id = R.id.anko_empty_view
                    orientation = LinearLayoutCompat.VERTICAL
                    if (context.getPrefer().getBoolean(Const.KEY_SHOW_EMPTY_VIEW, true)) {
                        addView(img, LinearLayoutCompat.LayoutParams.WRAP_CONTENT, dip(240))
                    }
                    addView(AppCompatTextView(context).apply {
                        text = "本周没有课程哦"
                        textSize = 15f
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                        gravity = Gravity.CENTER
                    }, LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.MATCH_PARENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dip(16)
                    })
                }, ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                        ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
                    startToStart = ConstraintSet.PARENT_ID
                    endToEnd = ConstraintSet.PARENT_ID
                    topToBottom = R.id.anko_tv_title0
                    bottomToBottom = ConstraintSet.PARENT_ID
                    marginStart = context!!.dip(32)
                    marginEnd = context!!.dip(32)
                })
            } else {
                ui.content.visibility = View.VISIBLE
                ui.root.getViewById(R.id.anko_empty_view)?.let { emptyView ->
                    ui.root.removeView(emptyView)
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // 从设置页返回时重新读偏好，立即应用网格虚线显隐（无需重进页面）
        ui.applyDashedGridVisibility()
        if (preLoad) return
        if (!isLoaded) {
            for (i in 1..7) {
                viewModel.allCourseList[i - 1].observe(viewLifecycleOwner, Observer {
                    initWeekPanel(it, i, viewModel.table)
                })
            }
            isLoaded = true
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(week: Int, preLoad: Boolean) =
                ScheduleFragment().apply {
                    arguments = Bundle().apply {
                        putInt("week", week)
                        putBoolean("preLoad", preLoad)
                    }
                }
    }

    private fun initWeekPanel(data: List<CourseBean>?, day: Int, table: TableBean) {
        val ll = ui.content.getViewById(R.id.anko_ll_week_panel_0 + ui.dayMap[day] - 1) as FrameLayout?
                ?: return
        ll.removeAllViews()
        // 从这里起 data 保证非空（也让下面的长按回调能直接捕获）
        val courses = data ?: return
        if (courses.isEmpty()) return

        // 同名+同节次视为同一门课的多个周次分段（如"2;4;6;8;13-16周"被拆成多条）。
        // 本周有课则只保留覆盖本周的分段（实色）；全组都不在本周只保留 startWeek 最小的一条（浅色），
        // 避免同格重复堆叠多个浅色块，以及散周课被误判为[非本周]。
        val keepIdx = mutableSetOf<Int>()
        val groups = HashMap<String, MutableList<Int>>()
        for (i in data.indices) {
            val c = data[i]
            val key = "${c.courseName}|${c.startNode}|${c.step}"
            groups.getOrPut(key) { mutableListOf() }.add(i)
        }
        for (idxList in groups.values) {
            val pick = idxList.firstOrNull { data[it].inWeek(week) }
                    ?: idxList.filter { data[it].endWeek >= week }.minBy { data[it].startWeek }
            if (pick != null) keepIdx.add(pick)
        }

        var isCovered = false
        var pre = data[0]
        for (i in data.indices) {
            if (i !in keepIdx) continue
            val c = data[i]

            // 过期的不显示
            if (c.endWeek < week) {
                continue
            }

            val isOtherWeek = !c.inWeek(week)

            if (!table.showOtherWeekCourse && isOtherWeek) continue

            var isError = false

            if (c.step <= 0) {
                c.step = 1
                isError = true
                Toasty.info(context!!, R.string.error_course_data, Toast.LENGTH_LONG).show()
            }
            if (c.startNode <= 0) {
                c.startNode = 1
                isError = true
                Toasty.info(context!!, R.string.error_course_data, Toast.LENGTH_LONG).show()
            }
            if (c.startNode > table.nodes) {
                c.startNode = table.nodes
                isError = true
                Toasty.info(context!!, R.string.error_course_node, Toast.LENGTH_LONG).show()
            }
            if (c.startNode + c.step - 1 > table.nodes) {
                c.step = table.nodes - c.startNode + 1
                isError = true
                Toasty.info(context!!, R.string.error_course_node, Toast.LENGTH_LONG).show()
            }

            val textView = TipTextView(context!!)

            if (ll.childCount != 0) {
                isCovered = (pre.startNode == c.startNode)
            }

            textView.setPadding(context!!.dip(6))

            if (c.color.isEmpty()) {
                c.color = "#${Integer.toHexString(ViewUtils.getCustomizedColor(activity!!, c.id % 9))}"
            }

            if (isOtherWeek) {
                textView.visibility = View.VISIBLE
            }

            if (isCovered) {
                val tv = ll.getChildAt(ll.childCount - 1) as TipTextView?
                if (tv != null) {
                    if (tv.tipVisibility == TipTextView.TIP_OTHER_WEEK) {
                        tv.visibility = View.INVISIBLE
                    }
                }
            }

            val tv = ll.findViewWithTag<TipTextView?>(c.startNode)
            if (tv != null) {
                textView.visibility = View.INVISIBLE
                if (tv.tipVisibility != TipTextView.TIP_VISIBLE && !isOtherWeek) {
                    if (tv.tipVisibility != TipTextView.TIP_ERROR) {
                        tv.tipVisibility = TipTextView.TIP_VISIBLE
                    }
                    tv.setOnClickListener {
                        MultiCourseFragment.newInstance(week, c.day, c.startNode).show(parentFragmentManager, "multi")
                    }
                }
            }

            if (isError) {
                textView.tipVisibility = TipTextView.TIP_ERROR
            }

            if (!isOtherWeek) {
                textView.tag = c.startNode
            } else {
                textView.tipVisibility = TipTextView.TIP_OTHER_WEEK
            }

            // 课名 / 教室 / 开始时间 / 单双周 四段分开交给 TipTextView 自行排版：
            // 格子放不下时它会给课名限行加省略号，并保证"@教室"那一行优先显示，
            // 不会再出现"课名太长把教室名顶出格子"的情况（详见 TipTextView 注释）。
            val weekTip = buildString {
                when (c.type) {
                    1 -> append("单周")
                    2 -> append("双周")
                }
                if (isOtherWeek) append("[非本周]")
            }
            val startTime = if (table.showTime && viewModel.timeList.isNotEmpty()) {
                viewModel.timeList.getOrNull(c.startNode - 1)?.startTime ?: ""
            } else {
                ""
            }
            textView.init(
                    courseName = c.courseName,
                    room = c.room ?: "",
                    weekTip = weekTip,
                    timeText = startTime,
                    txtSize = table.itemTextSize,
                    txtColor = table.courseTextColor,
                    bgColor = Color.parseColor(c.color),
                    bgAlpha = viewModel.alphaInt,
                    stroke = table.strokeColor
            )

            textView.setOnClickListener {
                try {
                    val detailFragment = CourseDetailFragment.newInstance(c)
                    detailFragment.show(parentFragmentManager, "courseDetail")
                } catch (e: Exception) {
                    //TODO: 提示是否要删除异常的数据
                    Toasty.error(activity!!.applicationContext, "哎呀>_<差点崩溃了").show()
                }
            }

            // 长按拖动：把这一块课挪到别的格子。只改 day 与 startNode，
            // 周次、时长、单双周一律原样保留；松手给可撤销的提示。
            // [非本周]的浅色提示块不给拖：它本来就不在本周，移动语义会乱。
            if (!isOtherWeek) {
                textView.setOnLongClickListener {
                    dragController?.startDrag(textView, c, courses, day)
                    true
                }
            }

            ll.addView(textView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    viewModel.itemHeight * c.step + viewModel.marTop * (c.step - 1)).apply {
                gravity = Gravity.TOP
                topMargin = (c.startNode - 1) * (viewModel.itemHeight + viewModel.marTop) + viewModel.marTop
            })

            pre = c
        }
    }

    /**
     * 拖动落定后写库：只改 day 与 startNode，周次/时长/单双周一律原样搬运。
     * 写库后 Room 的 LiveData 会自动让课表重绘，这里只需再推一下桌面小部件，并给一个可撤销的提示。
     */
    private fun onCourseMoved(result: CourseDragResult) {
        val ctx = context ?: return
        // CourseDetailBean 的主键里含 day 与 startNode，改位置等于换主键，
        // 所以必须留一份"旧"的用于删除、一份"新"的用于插入（详见 CourseDao.moveCourseDetails）
        val oldList = result.group.map { CourseUtils.courseBean2DetailBean(it) }
        val dayShift = result.dstDay - result.srcDay
        val nodeShift = result.dstNode - result.srcNode
        val newList = oldList.map {
            CourseDetailBean(
                    id = it.id,
                    day = it.day + dayShift,
                    room = it.room,
                    teacher = it.teacher,
                    startNode = it.startNode + nodeShift,
                    step = it.step,
                    startWeek = it.startWeek,
                    endWeek = it.endWeek,
                    type = it.type,
                    tableId = it.tableId
            )
        }

        val dao = AppDatabase.getDatabase(ctx).courseDao()
        this@ScheduleFragment.launch {
            dao.moveCourseDetails(oldList, newList)
            AppWidgetUtils.updateWidget(ctx)
        }

        ui.content.longSnack("已移到${CourseUtils.getDayStr(result.dstDay)}第${result.dstNode}节") {
            action("撤销") {
                this@ScheduleFragment.launch {
                    dao.moveCourseDetails(newList, oldList)
                    AppWidgetUtils.updateWidget(ctx)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isLoaded = false
    }

}