package com.Tangle.timetable.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.Tangle.timetable.AppDatabase
import com.Tangle.timetable.R
import com.Tangle.timetable.base_view.BaseListActivity
import com.Tangle.timetable.dao.AppWidgetDao
import com.Tangle.timetable.dao.TableDao
import com.Tangle.timetable.schedule_settings.ScheduleSettingsActivity
import com.Tangle.timetable.settings.items.*
import com.Tangle.timetable.utils.AppWidgetUtils
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.getPrefer
import com.Tangle.timetable.widget.WidgetScheduler
import com.Tangle.timetable.widget.colorpicker.ColorPickerFragment
import splitties.activities.start
import splitties.dimensions.dip
import splitties.resources.color
import splitties.snackbar.longSnack
import splitties.snackbar.snack

class SettingsActivity : BaseListActivity(), ColorPickerFragment.ColorPickerDialogListener {

    private lateinit var dataBase: AppDatabase
    private lateinit var tableDao: TableDao
    private lateinit var widgetDao: AppWidgetDao
    private val dayNightTheme by lazy(LazyThreadSafetyMode.NONE) {
        resources.getStringArray(R.array.day_night_setting)
    }
    private var dayNightIndex = 2

    private val mAdapter = SettingItemAdapter()

    override fun onColorSelected(dialogId: Int, color: Int) {
        getPrefer().edit {
            putInt(Const.KEY_THEME_COLOR, color)
        }
        mRecyclerView.longSnack("重启App后生效哦~")
    }

    override fun onSetupSubButton(tvButton: AppCompatTextView): AppCompatTextView? {
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dataBase = AppDatabase.getDatabase(application)
        tableDao = dataBase.tableDao()
        widgetDao = dataBase.appWidgetDao()
        dayNightIndex = getPrefer().getInt(Const.KEY_DAY_NIGHT_THEME, 2)

        val items = mutableListOf<BaseSettingItem>()
        onItemsCreated(items)
        mAdapter.data = items
        mRecyclerView.layoutManager = LinearLayoutManager(this)
        mRecyclerView.itemAnimator?.changeDuration = 250
        mRecyclerView.adapter = mAdapter
        mRecyclerView.addItemDecoration(SettingsCardDecoration(this))
        // 底部留出 24dp，承载版本号页尾
        mRecyclerView.setPadding(0, 0, 0, dip(24))
        mRecyclerView.clipToPadding = false
        addVersionFooter()

        mAdapter.addChildClickViewIds(R.id.anko_check_box)
        mAdapter.setOnItemChildClickListener { _, view, position ->
            when (val item = items[position]) {
                is SwitchItem -> onSwitchItemCheckChange(item, view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.anko_check_box).isChecked)
            }
        }
        mAdapter.setOnItemClickListener { _, view, position ->
            when (val item = items[position]) {
                is HorizontalItem -> onHorizontalItemClick(item, position)
                is VerticalItem -> onVerticalItemClick(item)
                is SeekBarItem -> onSeekBarItemClick(item, position)
                is SwitchItem -> view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.anko_check_box).performClick()
            }
        }

        refreshCurrentTableName()
    }

    private fun onItemsCreated(items: MutableList<BaseSettingItem>) {
        // ■ 课表
        items.add(CategoryItem("课表", true))
        items.add(HorizontalItem("设置当前课表", "管理"))
        items.add(SwitchItem("节数栏显示具体时间", getPrefer().getBoolean(Const.KEY_SCHEDULE_DETAIL_TIME, true), ""))
        items.add(SwitchItem("课表下方留白", getPrefer().getBoolean(Const.KEY_SCHEDULE_BLANK_AREA, true),
                "便于把底部课程滑到屏幕中间"))
        items.add(SwitchItem("页面预加载", getPrefer().getBoolean(Const.KEY_SCHEDULE_PRE_LOAD, true),
                "开启后滑动切页更快，关闭更省内存"))

        // ■ 外观
        items.add(CategoryItem("外观", false))
        items.add(VerticalItem("主题颜色", "自定义主题色"))
        items.add(HorizontalItem("显示主题", dayNightTheme[dayNightIndex]))
        items.add(SwitchItem("显示空视图图片", getPrefer().getBoolean(Const.KEY_SHOW_EMPTY_VIEW, true)))
        items.add(SwitchItem("显示日视图背景", getPrefer().getBoolean(Const.KEY_DAY_WIDGET_COLOR, false)))
        items.add(SwitchItem("主界面虚拟键沉浸", getPrefer().getBoolean(Const.KEY_HIDE_NAV_BAR, false)))

        // ■ 课程提醒
        items.add(CategoryItem("课程提醒", false))
        items.add(SwitchItem("开启上课提醒", getPrefer().getBoolean(Const.KEY_COURSE_REMIND, false),
                "国产机型需允许后台运行，设置后可能需等待一段时间生效"))
        items.add(SeekBarItem("提前几分钟提醒", getPrefer().getInt(Const.KEY_REMINDER_TIME, 10), 0, 90, "分钟"))
        items.add(SwitchItem("今日小部件隐藏已结束课程", getPrefer().getBoolean(Const.KEY_HIDE_ENDED_COURSE, false),
                "开启后已下课的课程从小部件消失，关闭则置灰"))
        items.add(SwitchItem("提醒通知常驻", getPrefer().getBoolean(Const.KEY_REMINDER_ON_GOING, false)))

        // ■ 桌面小部件
        items.add(CategoryItem("桌面小部件", false))
        items.add(VerticalItem("小部件不更新？点此修复", "开启自启动/电池/通知/闹钟权限",
                keys = listOf("小部件", "插件", "不更新", "不刷新", "权限", "修复", "闹钟")))
    }

    private fun addVersionFooter() {
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrDefault("")
        val footerView = AppCompatTextView(this).apply {
            text = "小唐Tangle v$versionName"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(0, dip(12), 0, 0)
        }
        mAdapter.addFooterView(footerView)
    }

    private fun refreshCurrentTableName() {
        launch {
            val table = tableDao.getDefaultTable()
            val name = table?.tableName
            if (!name.isNullOrEmpty()) {
                val target = (mAdapter.data as? List<*>)?.filterIsInstance<HorizontalItem>()
                        ?.firstOrNull { it.title == "设置当前课表" }
                if (target != null) {
                    target.value = name
                    mAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun onSwitchItemCheckChange(item: SwitchItem, isChecked: Boolean) {
        when (item.title) {
            "页面预加载" -> {
                getPrefer().edit {
                    putBoolean(Const.KEY_SCHEDULE_PRE_LOAD, isChecked)
                }
                mRecyclerView.snack("重启App后生效哦")
            }
            "课表下方留白" -> {
                getPrefer().edit {
                    putBoolean(Const.KEY_SCHEDULE_BLANK_AREA, isChecked)
                }
                mRecyclerView.snack("重启App后生效哦")
            }
            "节数栏显示具体时间" -> {
                getPrefer().edit {
                    putBoolean(Const.KEY_SCHEDULE_DETAIL_TIME, isChecked)
                }
                mRecyclerView.snack("重启App后生效哦")
            }
            "显示空视图图片" -> {
                getPrefer().edit {
                    putBoolean(Const.KEY_SHOW_EMPTY_VIEW, isChecked)
                }
                mRecyclerView.snack("切换页面后生效哦")
            }
            "显示日视图背景" -> {
                getPrefer().edit {
                    putBoolean(Const.KEY_DAY_WIDGET_COLOR, isChecked)
                }
                mRecyclerView.longSnack("请点击小部件右上角的「切换按钮」查看效果~")
            }
            "主界面虚拟键沉浸" -> {
                getPrefer().edit {
                    putBoolean(Const.KEY_HIDE_NAV_BAR, isChecked)
                }
                mRecyclerView.longSnack("重启App后生效哦~")
            }
            "开启上课提醒" -> {
                launch {
                    val task = widgetDao.getWidgetsByTypes(0, 1)
                    if (task.isEmpty()) {
                        mRecyclerView.longSnack("好像还没有设置日视图小部件呢>_<")
                        getPrefer().edit {
                            putBoolean(Const.KEY_COURSE_REMIND, false)
                        }
                        item.checked = false
                        mAdapter.notifyDataSetChanged()
                    } else {
                        getPrefer().edit {
                            putBoolean(Const.KEY_COURSE_REMIND, isChecked)
                        }
                        AppWidgetUtils.updateWidget(applicationContext)
                        // 提醒开关变化后重建当天闹钟
                        WidgetScheduler.scheduleAll(applicationContext)
                        item.checked = isChecked
                    }
                }
                return
            }
            "今日小部件隐藏已结束课程" -> {
                getPrefer().edit {
                    putBoolean(Const.KEY_HIDE_ENDED_COURSE, isChecked)
                }
                AppWidgetUtils.updateWidget(applicationContext)
            }
            "提醒通知常驻" -> {
                getPrefer().edit {
                    putBoolean(Const.KEY_REMINDER_ON_GOING, isChecked)
                }
                mRecyclerView.longSnack("对下一次提醒通知生效哦")
            }
        }
        item.checked = isChecked
    }

    private fun onHorizontalItemClick(item: HorizontalItem, position: Int) {
        when (item.title) {
            "设置当前课表" -> {
                launch {
                    val table = tableDao.getDefaultTable()
                    startActivityForResult(
                            Intent(this@SettingsActivity, ScheduleSettingsActivity::class.java).apply {
                                putExtra("tableData", table)
                            }, 180)
                }
            }
            "显示主题" -> {
                MaterialAlertDialogBuilder(this)
                        .setTitle("显示主题")
                        .setPositiveButton("确定") { _, _ ->
                            getPrefer().edit {
                                putInt(Const.KEY_DAY_NIGHT_THEME, dayNightIndex)
                            }
                            item.value = dayNightTheme[dayNightIndex]
                            mAdapter.notifyItemChanged(position)
                            when (dayNightIndex) {
                                0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                                1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                                2 -> {
                                    when {
                                        Build.VERSION.SDK_INT >= 29 -> {
                                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                                        }
                                        Build.VERSION.SDK_INT >= 23 -> {
                                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
                                        }
                                        else -> {
                                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                                        }
                                    }
                                }
                            }
                        }
                        .setSingleChoiceItems(dayNightTheme, dayNightIndex) { _, which ->
                            dayNightIndex = which.coerceIn(0, dayNightTheme.size - 1)
                        }
                        .show()
            }
        }
    }

    private fun onVerticalItemClick(item: VerticalItem) {
        when (item.title) {
            "主题颜色" -> {
                ColorPickerFragment.newBuilder()
                        .setShowAlphaSlider(true)
                        .setColor(getPrefer().getInt(Const.KEY_THEME_COLOR, color(R.color.colorAccent)))
                        .show(this)
            }
            "小部件不更新？点此修复" -> {
                start<com.Tangle.timetable.widget.PermissionGuideActivity>()
                WidgetScheduler.scheduleAll(this)
            }
        }
    }

    private fun onSeekBarItemClick(item: SeekBarItem, position: Int) {
        val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(item.title)
                .setView(R.layout.dialog_edit_text)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.sure, null)
                .create()
        dialog.show()
        val inputLayout = dialog.findViewById<TextInputLayout>(R.id.text_input_layout)
        val editText = dialog.findViewById<TextInputEditText>(R.id.edit_text)
        inputLayout?.helperText = "范围 ${item.min} ~ ${item.max}"
        inputLayout?.suffixText = item.unit
        editText?.inputType = InputType.TYPE_CLASS_NUMBER
        val valueStr = item.valueInt.toString()
        editText?.setText(valueStr)
        editText?.setSelection(valueStr.length)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = editText?.text
            if (value.isNullOrBlank()) {
                inputLayout?.error = "数值不能为空哦>_<"
                return@setOnClickListener
            }
            val valueInt = try {
                value.toString().toInt()
            } catch (e: Exception) {
                inputLayout?.error = "输入异常>_<"
                return@setOnClickListener
            }
            if (valueInt < item.min || valueInt > item.max) {
                inputLayout?.error = "注意范围 ${item.min} ~ ${item.max}"
                return@setOnClickListener
            }
            when (item.title) {
                "提前几分钟提醒" -> {
                    getPrefer().edit {
                        putInt(Const.KEY_REMINDER_TIME, valueInt)
                    }
                    AppWidgetUtils.updateWidget(applicationContext)
                }
            }
            item.valueInt = valueInt
            mAdapter.notifyItemChanged(position)
            dialog.dismiss()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 180) {
            setResult(RESULT_OK)
            refreshCurrentTableName()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        AppWidgetUtils.updateWidget(applicationContext)
        super.onDestroy()
    }
}

