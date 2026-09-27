package com.Tangle.timetable.course_add

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.BaseDialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.Tangle.timetable.R
import com.Tangle.timetable.widget.SelectedRecyclerView
import es.dmoral.toasty.Toasty
import splitties.resources.styledColor

class SelectWeekFragment : BaseDialogFragment() {

    override val layoutId: Int
        get() = R.layout.fragment_select_week

    var position = -1
    private val viewModel by activityViewModels<AddCourseViewModel>()
    private val liveData = MutableLiveData<ArrayList<Int>>()
    private val result = ArrayList<Int>()
    private var colorSurface: Int = Color.BLACK

    /**
     * W1-07：把三个高频访问的 TextView 缓存到字段。
     * 原实现用合成属性（每次访问都是一次全树 `findViewById`），而
     * `SelectedRecyclerView.changeState` **每次手指移动**都会 `liveData.value = result`，
     * observer 立即执行 → 三个 TextView 各取 2 次 ≈ 每次移动 6 次全树查找。
     * 缓存后 observer 内的查找次数为 0。
     */
    private var tvAll: AppCompatTextView? = null
    private var tvType1: AppCompatTextView? = null
    private var tvType2: AppCompatTextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            position = it.getInt("position")
        }
        colorSurface = context!!.styledColor(R.attr.colorOnSurface)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // W1-07：① observer 原先注册在 onCreate 且 owner 传的是 **this（Fragment 本身）**：
        //   视图销毁后 observer 仍处活跃状态，一旦收到 liveData 通知就直接读 tvAll/tvType1/tvType2，
        //   抛 "Fragment xxx not attached to a context" / 视图为空类异常；
        //   ② 改为 viewLifecycleOwner —— 视图销毁即自动移除 observer，且能安全访问视图。
        //   注意：viewLifecycleOwner 只能在 onViewCreated 之后取用，所以注册点必须从 onCreate 挪过来。
        tvAll = view.findViewById(R.id.tv_all)
        tvType1 = view.findViewById(R.id.tv_type1)
        tvType2 = view.findViewById(R.id.tv_type2)
        liveData.observe(viewLifecycleOwner, Observer {
            if (it?.size == viewModel.maxWeek) {
                tvAll!!.setTextColor(Color.WHITE)
                tvAll!!.background = ContextCompat.getDrawable(context!!, R.drawable.select_textview_bg)
            }
            if (it?.size != viewModel.maxWeek) {
                tvAll!!.setTextColor(colorSurface)
                tvAll!!.background = null
            }
            val flag = viewModel.judgeType(it!!)
            if (flag == 1) {
                tvType1!!.setTextColor(Color.WHITE)
                tvType1!!.background = ContextCompat.getDrawable(context!!, R.drawable.select_textview_bg)
            }
            if (flag != 1) {
                tvType1!!.setTextColor(colorSurface)
                tvType1!!.background = null
            }
            if (flag == 2) {
                tvType2!!.setTextColor(Color.WHITE)
                tvType2!!.background = ContextCompat.getDrawable(context!!, R.drawable.select_textview_bg)
            }
            if (flag != 2) {
                tvType2!!.setTextColor(colorSurface)
                tvType2!!.background = null
            }
        })
        liveData.value = viewModel.editList[position].weekList.value
        result.addAll(liveData.value!!)
        showWeeks()
        initEvent()
    }

    /** 视图销毁时释放缓存引用，避免回调（若有）持有已销毁的 View */
    override fun onDestroyView() {
        tvAll = null
        tvType1 = null
        tvType2 = null
        super.onDestroyView()
    }

    private fun showWeeks() {
        val adapter = SelectWeekAdapter(R.layout.item_select_week, viewModel.maxWeek, result)
        rv_week.adapter = adapter
        rv_week.layoutManager = StaggeredGridLayoutManager(6, StaggeredGridLayoutManager.VERTICAL)
        var prePos = -1
        rv_week.positionChangedListener = object : SelectedRecyclerView.PositionChangedListener {
            override fun changeState(pos: Int, isDown: Boolean) {
                if (prePos != pos || isDown) {
                    if (pos in 0 until viewModel.maxWeek) {
                        // item 尚未挂载（快速滑动被回收）时 getViewByPosition 返回 null，统一 as? 判空
                        val tv = adapter.getViewByPosition(pos, R.id.tv_num) as? AppCompatTextView
                        if (!result.contains(pos + 1)) {
                            result.add(pos + 1)
                            tv?.setBackgroundResource(R.drawable.week_selected_bg)
                            tv?.setTextColor(Color.WHITE)
                        } else {
                            result.remove(pos + 1)
                            tv?.background = null
                            tv?.setTextColor(colorSurface)
                        }
                        liveData.value = result
                    }
                    if (prePos != pos) {
                        prePos = pos
                    }
                }
            }
        }
    }

    private fun initEvent() {
        tvAll!!.setOnClickListener {
            if (tvAll!!.background == null) {
                result.clear()
                for (i in 1..viewModel.maxWeek) {
                    result.add(i)
                }
                showWeeks()
                liveData.value = result
            } else {
                result.clear()
                showWeeks()
                liveData.value = result
            }
        }

        tvType1!!.setOnClickListener {
            if (tvType1!!.background == null) {
                result.clear()
                for (i in 1..viewModel.maxWeek step 2) {
                    result.add(i)
                }
                showWeeks()
                liveData.value = result
            }
        }

        tvType2!!.setOnClickListener {
            if (tvType2!!.background == null) {
                result.clear()
                for (i in 2..viewModel.maxWeek step 2) {
                    result.add(i)
                }
                showWeeks()
                liveData.value = result
            }
        }

        btn_cancel.setOnClickListener {
            dismiss()
        }

        btn_save.setOnClickListener {
            if (result.size == 0) {
                Toasty.error(context!!.applicationContext, "请至少选择一周").show()
            } else {
                viewModel.editList[position].weekList.value = result
                dismiss()
            }
        }
    }

    companion object {

        @JvmStatic
        fun newInstance(arg: Int) =
                SelectWeekFragment().apply {
                    arguments = Bundle().apply {
                        putInt("position", arg)
                    }
                }
    }
}
