package com.Tangle.timetable.schedule

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.view.View
import androidx.core.content.edit
import androidx.fragment.app.BaseDialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.Tangle.timetable.R
import com.Tangle.timetable.utils.CalendarSyncUtils
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.Utils
import com.Tangle.timetable.utils.getPrefer
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExportSettingsFragment : BaseDialogFragment() {

    override val layoutId: Int
        get() = R.layout.fragment_export_settings

    private val viewModel by activityViewModels<ScheduleViewModel>()

    val tableName by lazy(LazyThreadSafetyMode.NONE) {
        if (viewModel.table.tableName == "") {
            "我的课表"
        } else {
            viewModel.table.tableName
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false

        tv_export.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, "$tableName.wakeup_schedule")
            }
            Toasty.info(activity!!, "请自行选择导出的地方\n不要修改文件的扩展名哦", Toasty.LENGTH_LONG).show()
            activity?.startActivityForResult(intent, Const.REQUEST_CODE_EXPORT)
            dismiss()
        }

        tv_export_ics.setOnLongClickListener {
            Utils.openUrl(activity!!, "https://www.jianshu.com/p/de3524cbe8aa")
            return@setOnLongClickListener true
        }

        tv_export_ics.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/calendar"
                putExtra(Intent.EXTRA_TITLE, "日历-$tableName")
            }
            Toasty.info(activity!!, "请自行选择导出的地方\n不要修改文件的扩展名哦", Toasty.LENGTH_LONG).show()
            activity?.startActivityForResult(intent, Const.REQUEST_CODE_EXPORT_ICS)
            dismiss()
        }

        tv_sync_calendar.setOnClickListener {
            val act = activity ?: return@setOnClickListener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !CalendarSyncUtils.hasPermission(act)) {
                requestPermissions(
                        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                        Const.REQUEST_CODE_CALENDAR_PERMISSION
                )
            } else {
                startSync()
            }
        }

        tv_cancel.setOnClickListener {
            dismiss()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != Const.REQUEST_CODE_CALENDAR_PERMISSION) return
        val act = activity ?: return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startSync()
        } else {
            Toasty.error(act, "没给日历权限，就写不进去啦", Toasty.LENGTH_LONG).show()
        }
    }

    /** 先找出往哪个日历写，只有一个就直接写，多个就弹出来让主人挑 */
    private fun startSync() {
        val act = activity ?: return
        launch {
            var calendars = try {
                withContext(Dispatchers.IO) {
                    CalendarSyncUtils.queryWritableCalendars(act.contentResolver)
                }
            } catch (e: Exception) {
                Toasty.error(act, "读日历列表失败：${e.message}", Toasty.LENGTH_LONG).show()
                return@launch
            }
            if (calendars.isEmpty()) {
                // 一个可写日历都没有（ColorOS 私有库 / 权限受限时常见）：
                // 不再要求用户先去日历 App 里手建，本应用自己建一个本地日历来用
                val created = withContext(Dispatchers.IO) {
                    CalendarSyncUtils.ensureLocalCalendar(act.contentResolver)
                }
                if (created != null) {
                    calendars = listOf(created)
                } else {
                    Toasty.error(act, "没找到可以写入的日历，自动创建也没成功\n请到 系统设置 → 应用 → 小唐Tangle → 日历权限，改成「允许全部」（「仅允许创建」会读不到、也建不了日历）", Toasty.LENGTH_LONG).show()
                    return@launch
                }
            }
            // v132 起记忆「authority|id」组合键：不同日历库的 id 各自从 1 编号，
            // 只按 id 匹配会在错误的库里命中同号日历，把日程写错地方
            val savedKey = act.getPrefer().getString(Const.KEY_SYNC_CALENDAR_KEY, null)
            val remembered = savedKey?.let { key -> calendars.firstOrNull { "${it.authority}|${it.id}" == key } }
            when {
                remembered != null -> doSync(act, remembered)
                calendars.size == 1 -> doSync(act, calendars[0])
                else -> showCalendarPicker(act, calendars)
            }
        }
    }

    private fun showCalendarPicker(act: Activity, calendars: List<CalendarSyncUtils.SyncCalendar>) {
        val labels = calendars.map { it.displayName }.toTypedArray()
        MaterialAlertDialogBuilder(act)
                .setTitle("写进哪个日历？")
                .setItems(labels) { _, which ->
                    doSync(act, calendars[which])
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    private fun doSync(act: Activity, target: CalendarSyncUtils.SyncCalendar) {
        launch {
            val courseCount = viewModel.allCourseList.sumBy { it.value?.size ?: 0 }
            if (courseCount == 0) {
                Toasty.info(act, "这张课表还没有课程呢", Toasty.LENGTH_LONG).show()
                return@launch
            }
            val written = try {
                val courses = viewModel.allCourseList.flatMap { it.value ?: emptyList() }
                withContext(Dispatchers.IO) {
                    CalendarSyncUtils.syncTable(
                            act, viewModel.table, viewModel.timeList, courses, target.id,
                            target.authority)
                }
            } catch (e: Exception) {
                Toasty.error(act, "同步失败：${e.message}", Toasty.LENGTH_LONG).show()
                return@launch
            }
            act.getPrefer().edit {
                putString(Const.KEY_SYNC_CALENDAR_KEY, "${target.authority}|${target.id}")
                putLong(Const.KEY_SYNC_CALENDAR_ID, target.id)
            }
            showFinishDialog(act, written, target)
            dismiss()
        }
    }

    private fun showFinishDialog(act: Activity, count: Int, target: CalendarSyncUtils.SyncCalendar) {
        MaterialAlertDialogBuilder(act)
                .setTitle("同步好啦")
                .setMessage("已经把「$tableName」的 $count 条日程写进「${target.displayName}」。\n\n" +
                        "· 每节课一条，提前 15 分钟提醒；\n" +
                        "· 每天一条「今日课表」，这天几节课、都是什么，一眼看到。\n\n" +
                        "打开系统日历，或者负一屏 / 桌面的「日历」「日程」卡片都能看到。\n" +
                        "以后改完课表再点一次这里就行，会自动覆盖上一次，不会重复。")
                .setNegativeButton("知道了", null)
                .setPositiveButton("打开日历") { _, _ -> openCalendar(act) }
                .setCancelable(false)
                .show()
    }

    private fun openCalendar(act: Activity) {
        val candidates = listOf(
                Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI),
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
        )
        candidates.forEach {
            try {
                act.startActivity(it)
                return
            } catch (ignored: Exception) {
            }
        }
        Toasty.info(act, "没能打开日历，自己在手机里找找日历 App 吧").show()
    }
}
