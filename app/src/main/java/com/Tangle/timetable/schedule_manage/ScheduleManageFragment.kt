package com.Tangle.timetable.schedule_manage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.Tangle.timetable.R
import com.Tangle.timetable.base_view.BaseFragment
import com.Tangle.timetable.AppDatabase
import com.Tangle.timetable.bean.TableSelectBean
import com.Tangle.timetable.utils.CalendarSyncUtils
import com.Tangle.timetable.schedule_settings.ScheduleSettingsActivity
import es.dmoral.toasty.Toasty
import splitties.activities.start
import splitties.dimensions.dip

class ScheduleManageFragment : BaseFragment() {

    private val viewModel by activityViewModels<ScheduleManageViewModel>()
    private lateinit var adapter: TableListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_list_manage, container, false)
        val rvTableList = view.findViewById<RecyclerView>(R.id.rv_list)
        launch {
            initTableRecyclerView(view, rvTableList, viewModel.initTableSelectList())
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fab_add.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.setting_schedule_name)
                    .setView(R.layout.dialog_edit_text)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.sure, null)
                    .create()
            dialog.show()
            val inputLayout = dialog.findViewById<TextInputLayout>(R.id.text_input_layout)
            val editText = dialog.findViewById<TextInputEditText>(R.id.edit_text)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = editText?.text
                if (value.isNullOrBlank()) {
                    inputLayout?.error = "名称不能为空哦>_<"
                } else {
                    launch {
                        try {
                            val tableName = editText.text.toString()
                            val tableId = viewModel.addBlankTable(tableName)
                            adapter.addData(TableSelectBean(id = tableId.toInt(), tableName = tableName))
                            Toasty.success(context!!, "新建成功~").show()
                        } catch (e: Exception) {
                            Toasty.error(context!!, "操作失败>_<").show()
                        }
                        dialog.dismiss()
                    }
                }
            }
        }
    }

    private fun initTableRecyclerView(fragmentView: View, rvTableList: RecyclerView, data: MutableList<TableSelectBean>) {
        rvTableList.layoutManager = LinearLayoutManager(context)
        adapter = TableListAdapter(R.layout.item_table_list, data)
        adapter.setOnItemClickListener { _, _, position ->
            val bundle = Bundle()
            bundle.putParcelable("selectedTable", data[position])
            Navigation.findNavController(fragmentView).navigate(R.id.scheduleManageFragment_to_courseManageFragment, bundle)
        }
        adapter.addChildClickViewIds(R.id.ib_share, R.id.ib_edit, R.id.ib_delete)
        adapter.setOnItemChildClickListener { _, view, position ->
            when (view.id) {
                R.id.ib_share -> {
                }
                R.id.ib_edit -> {
                    launch {
                        val task = viewModel.getTableById(data[position].id)
                        if (task != null) {
                            activity!!.start<ScheduleSettingsActivity> {
                                putExtra("tableData", task)
                            }
                        } else {
                            Toasty.error(context!!.applicationContext, "读取课表异常>_<")
                        }
                    }
                }
                R.id.ib_delete -> {
                    Toasty.info(activity!!.applicationContext, "长按删除课程表哦~").show()
                }
            }
        }
        adapter.addChildLongClickViewIds(R.id.ib_delete)
        adapter.setOnItemChildLongClickListener { _, view, position ->
            when (view.id) {
                R.id.ib_delete -> {
                    // 删除是不可恢复操作：先确认，再连带清理日历日程与小部件绑定
                    // 注意：本工程 Kotlin 1.3 不允许在字符串模板 ${} 里再用双引号，名字先取出来
                    val deleteName = data[position].tableName.ifEmpty { "未命名课表" }
                    MaterialAlertDialogBuilder(activity!!)
                            .setTitle("删除课表")
                            .setMessage("删除「$deleteName」？\n该课表的全部课程将一并删除，且无法恢复。")
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton("删除") { _, _ ->
                                launch {
                                    val tid = data[position].id
                                    CalendarSyncUtils.deleteSyncedEventsAllProviders(
                                            activity!!.applicationContext.contentResolver, tid)
                                    AppDatabase.getDatabase(activity!!.applicationContext)
                                            .appWidgetDao().deleteAppWidgetByInfo(tid.toString())
                                    viewModel.deleteTable(tid)
                                    adapter.remove(position)
                                    // 所有小部件按各自绑定重新取数（被删表的部件回落到默认表/空态）
                                    context!!.sendBroadcast(
                                            Intent(context!!, com.Tangle.timetable.widget.WidgetUpdateReceiver::class.java)
                                                    .setAction(com.Tangle.timetable.widget.WidgetScheduler.ACTION_REFRESH))
                                    Toasty.success(context!!, "删除成功~").show()
                                }
                            }
                            .show()
                    return@setOnItemChildLongClickListener true
                }
                else -> {
                    return@setOnItemChildLongClickListener false
                }
            }

        }
        adapter.addFooterView(View(activity).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dip(240))
        })
        adapter.addHeaderView(AppCompatTextView(context!!).apply {
            text = "点击卡片查看该课表的课程"
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, dip(8), 0, dip(8))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        rvTableList.adapter = adapter
    }

}
