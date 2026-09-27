package com.Tangle.timetable.suda_life

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.Tangle.timetable.R
import com.Tangle.timetable.base_view.BaseFragment
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import splitties.dimensions.dip

class EmptyRoomFragment : BaseFragment() {

    private lateinit var viewModel: SudaLifeViewModel

    /**
     * W1-04：本 Fragment 原先用 `BaseFragment.launch`，它绑的是 **Fragment** 的生命周期
     * （`lifecycleScope` + `lifecycle.whenStarted`）。问题：挂起返回时视图可能已经销毁，
     * 此时访问 `spinner_campus` 这类合成属性会抛 `IllegalStateException`
     * （它内部就是 `findViewById(...)!!`），异常又被下面的 `catch (e: Exception)` 吞掉、
     * 弹一句误导性提示 → **校区下拉框永远拿不到 adapter，空教室查询整体不可用**
     * （静默功能损坏，比崩溃更难发现）。
     *
     * 改为绑 **viewLifecycleOwner**：视图销毁即取消协程，挂起返回后不会再碰已销毁的视图。
     */
    private fun launchWithView(block: suspend CoroutineScope.() -> Unit): Job =
            viewLifecycleOwner.lifecycleScope.launch(block = block)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // W4-12：`ViewModelProviders` → `ViewModelProvider`（lifecycle 2.5.0 起的新 API）
        viewModel = ViewModelProvider(activity!!).get(SudaLifeViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_empty_room, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        spinner_campus.dropDownVerticalOffset = view.dip(48)
        spinner_building.dropDownVerticalOffset = view.dip(48)
        spinner_date.dropDownVerticalOffset = view.dip(48)

        initData()
        initEvent()

        rv_room.adapter = RoomAdapter(R.layout.item_suda_room, viewModel.roomData)
        rv_room.layoutManager = LinearLayoutManager(activity!!)
    }

    private fun initData() {
        launchWithView {
            try {
                viewModel.getBuildingData()
                spinner_campus.adapter = ArrayAdapter(activity!!, android.R.layout.simple_spinner_item, viewModel.buildingData.keys.toList())
                        .apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
            } catch (e: CancellationException) {
                // 协程取消不是业务异常：必须继续向上抛，否则会被下面当成"发生异常"弹误导提示
                throw e
            } catch (e: Exception) {
                if (isAdded && view != null) {
                    Toasty.error(activity!!, "发生异常>_<${e.message}").show()
                }
            }
        }

        launchWithView {
            spinner_date.adapter = ArrayAdapter(activity!!, android.R.layout.simple_spinner_item, viewModel.getDateList())
                    .apply {
                        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    }
        }
    }

    private fun initEvent() {
        spinner_campus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {

            }

            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                spinner_building.adapter = ArrayAdapter(activity!!, android.R.layout.simple_spinner_item,
                        viewModel.buildingData[(v as TextView).text]!!)
                        .apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
            }

        }

        spinner_building.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {

            }

            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                queryRoomData()
            }

        }

        spinner_date.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {

            }

            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                queryRoomData()
            }

        }

    }

    private fun queryRoomData() {
        val building = spinner_building.selectedItem as String?
        val date = spinner_date.selectedItem as String?
        if (building != null && date != null) {
            launchWithView {
                try {
                    viewModel.getRoomData(building, date)
                    rv_room.adapter?.notifyDataSetChanged()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isAdded && view != null) {
                        Toasty.error(activity!!, "发生异常>_<${e.message}").show()
                    }
                }
            }
        }
    }

}
