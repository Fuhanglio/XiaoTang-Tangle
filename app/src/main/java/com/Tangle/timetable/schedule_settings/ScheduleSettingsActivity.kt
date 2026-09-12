package com.Tangle.timetable.schedule_settings

import android.app.DatePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.Tangle.timetable.R
import com.Tangle.timetable.base_view.BaseListActivity
import com.Tangle.timetable.bean.TableBean
import com.Tangle.timetable.bean.TableSelectBean
import com.Tangle.timetable.schedule_manage.ScheduleManageActivity
import com.Tangle.timetable.settings.SettingItemAdapter
import com.Tangle.timetable.settings.SettingsCardDecoration
import com.Tangle.timetable.settings.SettingsActivity
import com.Tangle.timetable.settings.TimeSettingsActivity
import com.Tangle.timetable.settings.items.*
import com.Tangle.timetable.utils.AppWidgetUtils
import com.Tangle.timetable.utils.BirthdayUtils
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.ThemeManager
import com.Tangle.timetable.utils.getPrefer
import com.Tangle.timetable.widget.ColorWheelDialogFragment
import com.Tangle.timetable.widget.colorpicker.ColorPickerFragment
import es.dmoral.toasty.Toasty
import splitties.activities.start
import splitties.dimensions.dip
import splitties.snackbar.longSnack

private const val TITLE_COLOR = 1
private const val COURSE_TEXT_COLOR = 2
private const val STROKE_COLOR = 3
private const val WIDGET_TITLE_COLOR = 4
private const val WIDGET_COURSE_TEXT_COLOR = 5
private const val WIDGET_STROKE_COLOR = 6

class ScheduleSettingsActivity : BaseListActivity(), ColorPickerFragment.ColorPickerDialogListener {

    override fun onColorSelected(dialogId: Int, color: Int) {
        when (dialogId) {
            TITLE_COLOR -> viewModel.table.textColor = color
            COURSE_TEXT_COLOR -> viewModel.table.courseTextColor = color
            STROKE_COLOR -> viewModel.table.strokeColor = color
            WIDGET_TITLE_COLOR -> viewModel.table.widgetTextColor = color
            WIDGET_COURSE_TEXT_COLOR -> viewModel.table.widgetCourseTextColor = color
            WIDGET_STROKE_COLOR -> viewModel.table.widgetStrokeColor = color
        }
    }

    override fun onSetupSubButton(tvButton: AppCompatTextView): AppCompatTextView? {
        return null
    }

    private val viewModel by viewModels<ScheduleSettingsViewModel>()
    private val mAdapter = SettingItemAdapter()
    private val REQUEST_CODE_CHOOSE_BG = 23
    private val REQUEST_CODE_CHOOSE_TABLE = 21
    private val REQUEST_CODE_BIRTHDAY = 24
    private val allItems = mutableListOf<BaseSettingItem>()
    private val showItems = mutableListOf<BaseSettingItem>()

    private val currentWeekItem by lazy(LazyThreadSafetyMode.NONE) {
        SeekBarItem("当前周", viewModel.getCurrentWeek(), 1, viewModel.table.maxWeek, "周", "第", keys = listOf("学期", "周", "日期", "开学", "开始", "时间"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        showSearch = true
        textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                showItems.clear()
                if (s.isNullOrBlank() || s.isEmpty()) {
                    showItems.addAll(allItems)
                } else {
                    showItems.add(CategoryItem("搜索结果", true))
                    showItems.addAll(allItems.filter {
                        val k = it.keyWords
                        k?.contains(s.toString()) ?: false
                    })
                }
                mRecyclerView.adapter?.notifyDataSetChanged()
                if (showItems.size == 1) {
                    mRecyclerView.longSnack("找不到哦，换个关键词试试看，或者请仔细找找啦，一般都能找到的。")
                }
            }
        }
        super.onCreate(savedInstanceState)
        viewModel.table = intent.extras!!.getParcelable<TableBean>("tableData") as TableBean

        // 渲染前把主题颜色同步到课表（单一数据源：ThemeManager）
        ThemeManager.applyToTable(this, viewModel.table)
        ThemeManager.addListener(themeChangedListener)

        //onAdapterCreated(mAdapter)

        onItemsCreated(allItems)
        showItems.addAll(allItems)
        mAdapter.data = showItems
        mRecyclerView.layoutManager = LinearLayoutManager(this)
        mRecyclerView.itemAnimator?.changeDuration = 250
        mRecyclerView.adapter = mAdapter
        mRecyclerView.addItemDecoration(SettingsCardDecoration(this))
        // 底部留白 24dp（替代旧的空白占位条目）
        mRecyclerView.setPadding(0, 0, 0, dip(24))
        mRecyclerView.clipToPadding = false
        mAdapter.addChildClickViewIds(R.id.anko_check_box)
        mAdapter.setOnItemChildClickListener { _, view, position ->
            when (val item = showItems[position]) {
                is SwitchItem -> onSwitchItemCheckChange(item, view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.anko_check_box).isChecked)
            }
        }
        mAdapter.setOnItemClickListener { _, view, position ->
            when (val item = showItems[position]) {
                is HorizontalItem -> onHorizontalItemClick(item, position)
                is VerticalItem -> onVerticalItemClick(item)
                is SwitchItem -> view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.anko_check_box).performClick()
                is SeekBarItem -> onSeekBarItemClick(item, position)
                is ColorItem -> onColorItemClick(item)
            }
        }
        mAdapter.setOnItemLongClickListener { _, _, position ->
            when (val item = showItems[position]) {
                is VerticalItem -> onVerticalItemLongClick(item)
            }
            true
        }
        viewModel.termStartList = viewModel.table.startDate.split("-")
        viewModel.mYear = Integer.parseInt(viewModel.termStartList[0])
        viewModel.mMonth = Integer.parseInt(viewModel.termStartList[1])
        viewModel.mDay = Integer.parseInt(viewModel.termStartList[2])
        val settingItem = intent?.extras?.getString("settingItem")
        if (settingItem != null) {
            mRecyclerView.postDelayed({
                try {
                    val i = showItems.indexOfFirst {
                        it.title == settingItem
                    }
                    (mRecyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(i, dip(64))
                    when (showItems[i]) {
                        is HorizontalItem -> onHorizontalItemClick(showItems[i] as HorizontalItem, i)
                        is VerticalItem -> onVerticalItemClick(showItems[i] as VerticalItem)
                        is SeekBarItem -> onSeekBarItemClick(showItems[i] as SeekBarItem, i)
                    }
                } catch (e: Exception) {

                }
            }, 100)
        }
    }

    private fun onItemsCreated(items: MutableList<BaseSettingItem>) {
        // ===== 主题配色（预设 + 自定义） =====
        items.add(CategoryItem("主题配色", true))
        items.add(ThemePresetItem())

        items.add(CategoryItem("自定义颜色", false))
        ThemeManager.COLOR_ITEMS.keys.forEach { key ->
            items.add(ColorItem(key))
        }
        items.add(SeekBarItem("课表背景不透明度", ThemeManager.getBgAlpha(this), 0, 100, "%",
                keys = listOf("背景", "透明", "透明度", "纯色")))
        items.add(VerticalItem("恢复默认颜色", "一键重置所有颜色，主题恢复为「跟随系统」",
                keys = listOf("恢复", "重置", "默认", "颜色")))

        // ===== 课程数据 =====
        items.add(CategoryItem("课程数据", false))
        items.add(HorizontalItem("课表名称", viewModel.table.tableName, listOf("名称", "名字", "名", "课表")))
        items.add(HorizontalItem("上课时间", "点击此处更改", listOf("时间")))
        items.add(HorizontalItem("学期开始日期", viewModel.table.startDate, listOf("学期", "周", "日期", "开学", "开始", "时间")))
        items.add(currentWeekItem)
        items.add(HorizontalItem("管理已添加课程", "", keys = listOf("课程", "课")))
        items.add(SeekBarItem("一天课程节数", viewModel.table.nodes, 1, 30, "节", keys = listOf("节数", "数量", "数")))
        items.add(SeekBarItem("学期周数", viewModel.table.maxWeek, 1, 30, "周", keys = listOf("学期", "周", "时间")))
        items.add(SwitchItem("周日为每周第一天", viewModel.table.sundayFirst, keys = listOf("周日", "第一天", "起始", "星期天", "天")))
        items.add(SwitchItem("显示周六", viewModel.table.showSat, keys = listOf("周六", "显示", "星期六", "六")))
        items.add(SwitchItem("显示周日", viewModel.table.showSun, keys = listOf("周日", "显示", "星期日", "日", "星期天", "周天")))

        items.add(CategoryItem("课表外观", false))
        items.add(SwitchItem("在格子内显示上课时间", viewModel.table.showTime, keys = listOf("时间", "显示", "格子", "上课时间")))
        items.add(VerticalItem("课程表背景图片", "点击选择图片，长按恢复默认\n纯色背景请在「自定义颜色」中设置", keys = listOf("背景", "显示", "图片")))
        items.add(SeekBarItem("课程格子高度", viewModel.table.itemHeight, 32, 96, "dp", keys = listOf("格子", "高度", "格子高度", "显示")))
        items.add(SeekBarItem("课程格子不透明度", viewModel.table.itemAlpha, 0, 100, "%", keys = listOf("格子", "透明", "格子高度", "显示")))
        items.add(SeekBarItem("课程显示文字大小", viewModel.table.itemTextSize, 8, 16, "sp", keys = listOf("文字", "大小", "文字大小")))
        items.add(SwitchItem("显示非本周课程", viewModel.table.showOtherWeekCourse, keys = listOf("非本周")))
        items.add(SwitchItem("显示网格虚线", getPrefer().getBoolean(Const.KEY_SHOW_DASHED_GRID, true), keys = listOf("网格", "虚线", "网格虚线", "显示")))

        items.add(CategoryItem("桌面小部件外观", false))
        items.add(SeekBarItem("小部件格子高度", viewModel.table.widgetItemHeight, 32, 96, "dp", keys = listOf("格子", "高度", "格子高度", "显示", "小部件", "小", "插件", "桌面")))
        items.add(SeekBarItem("小部件格子不透明度", viewModel.table.widgetItemAlpha, 0, 100, "%", keys = listOf("格子", "透明", "格子高度", "显示", "小部件", "小", "插件", "桌面")))
        items.add(SeekBarItem("小部件显示文字大小", viewModel.table.widgetItemTextSize, 8, 16, "sp", keys = listOf("文字", "大小", "文字大小", "小部件", "小", "插件", "桌面")))
        items.add(HorizontalItem("课程预告范围", previewDaysText(), keys = listOf("预告", "提前", "天数", "课程预告", "小部件", "小", "插件", "桌面", "日视图")))
        items.add(HorizontalItem("生日提醒", birthdayText(), keys = listOf("生日", "提醒", "祝福", "祝愿", "留言", "小部件", "小", "插件", "桌面", "日视图")))
        items.add(VerticalItem("小部件标题颜色", "指标题等字体的颜色\n对于日视图则是全部文字的颜色\n还可以调颜色的透明度哦 (●ﾟωﾟ●)", keys = listOf("颜色", "显示", "文字", "文字颜色", "小部件", "小", "插件", "桌面")))
        items.add(VerticalItem("小部件课程颜色", "指课程格子内的文字颜色\n还可以调颜色的透明度哦 (●ﾟωﾟ●)", keys = listOf("颜色", "显示", "文字", "文字颜色", "小部件", "小", "插件", "桌面")))
        items.add(VerticalItem("小部件格子边框颜色", "将不透明度调到最低就可以隐藏边框了哦~", keys = listOf("边框", "显示", "边框颜色", "格子", "边", "小部件", "小", "插件", "桌面")))

        items.add(CategoryItem("高级", false))
        items.add(VerticalItem("高级功能", "主题颜色、上课提醒、小部件等全局设置", keys = listOf("高级", "设置", "提醒", "主题")))
    }

    /** 「课程预告范围」当前值文案：只允许 2 天或 7 天 */
    private fun previewDaysText(): String =
            if (getPrefer().getInt(Const.KEY_WIDGET_PREVIEW_DAYS, 7) == 2) "提前 2 天" else "提前 7 天"

    /** 「生日提醒」当前值文案：显示已设的月日，没设过就提示去设 */
    private fun birthdayText(): String =
            if (BirthdayUtils.isSet(this)) BirthdayUtils.dateText(this) else "未设置"

    private fun onSwitchItemCheckChange(item: SwitchItem, isChecked: Boolean) {
        when (item.title) {
            "周日为每周第一天" -> viewModel.table.sundayFirst = isChecked
            "显示周六" -> viewModel.table.showSat = isChecked
            "显示周日" -> viewModel.table.showSun = isChecked
            "在格子内显示上课时间" -> viewModel.table.showTime = isChecked
            "显示非本周课程" -> viewModel.table.showOtherWeekCourse = isChecked
            "显示网格虚线" -> getPrefer().edit { putBoolean(Const.KEY_SHOW_DASHED_GRID, isChecked) }
        }
        item.checked = isChecked
    }

    private fun onSeekBarItemClick(item: SeekBarItem, position: Int) {
        val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(item.title)
                .setView(R.layout.dialog_edit_text)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.sure, null)
                .setCancelable(false)
                .create()
        dialog.show()
        val inputLayout = dialog.findViewById<TextInputLayout>(R.id.text_input_layout)
        val editText = dialog.findViewById<TextInputEditText>(R.id.edit_text)
        inputLayout?.helperText = "范围 ${item.min} ~ ${item.max}"
        if (item.prefix.isNotEmpty()) {
            inputLayout?.prefixText = item.prefix
        }
        inputLayout?.suffixText = item.unit
        editText?.inputType = InputType.TYPE_CLASS_NUMBER
        if (item.valueInt < item.min) {
            item.valueInt = item.min
        }
        if (item.valueInt > item.max) {
            item.valueInt = item.max
        }
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
                "一天课程节数" -> viewModel.table.nodes = valueInt
                "学期周数" -> {
                    currentWeekItem.max = valueInt
                    viewModel.table.maxWeek = valueInt
                }
                "当前周" -> {
                    viewModel.setCurrentWeek(valueInt)
                    item.valueInt = valueInt
                    (mAdapter.data[position - 1] as HorizontalItem).value = viewModel.table.startDate
                    mAdapter.notifyItemChanged(position - 1)
                    mAdapter.notifyItemChanged(position)
                    dialog.dismiss()
                }
                "课程格子高度" -> viewModel.table.itemHeight = valueInt
                "课程格子不透明度" -> viewModel.table.itemAlpha = valueInt
                "课程显示文字大小" -> viewModel.table.itemTextSize = valueInt
                "小部件格子高度" -> viewModel.table.widgetItemHeight = valueInt
                "小部件格子不透明度" -> viewModel.table.widgetItemAlpha = valueInt
                "小部件显示文字大小" -> viewModel.table.widgetItemTextSize = valueInt
                "课表背景不透明度" -> ThemeManager.setBgAlpha(this@ScheduleSettingsActivity, valueInt)
            }
            item.valueInt = valueInt
            mAdapter.notifyItemChanged(position)
            dialog.dismiss()
        }
    }

    private fun onHorizontalItemClick(item: HorizontalItem, position: Int) {
        when (item.title) {
            "课表名称" -> {
                val dialog = MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.setting_schedule_name)
                        .setView(R.layout.dialog_edit_text)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.sure, null)
                        .create()
                dialog.show()
                val inputLayout = dialog.findViewById<TextInputLayout>(R.id.text_input_layout)
                val editText = dialog.findViewById<TextInputEditText>(R.id.edit_text)
                editText?.setText(item.value)
                editText?.setSelection(item.value.length)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val value = editText?.text
                    if (value.isNullOrBlank()) {
                        inputLayout?.error = "名称不能为空哦>_<"
                        return@setOnClickListener
                    }
                    viewModel.table.tableName = value.toString()
                    item.value = value.toString()
                    mAdapter.notifyItemChanged(position)
                    dialog.dismiss()
                }
            }
            "学期开始日期" -> {
                DatePickerDialog(this, DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
                    viewModel.mYear = year
                    viewModel.mMonth = monthOfYear + 1
                    viewModel.mDay = dayOfMonth
                    val mDate = "${viewModel.mYear}-${viewModel.mMonth}-${viewModel.mDay}"
                    item.value = mDate
                    viewModel.table.startDate = mDate
                    currentWeekItem.valueInt = viewModel.getCurrentWeek()
                    mAdapter.notifyItemChanged(position)
                    mAdapter.notifyItemChanged(position + 1)
                }, viewModel.mYear, viewModel.mMonth - 1, viewModel.mDay).show()
                if (viewModel.table.sundayFirst) {
                    Toasty.success(this, "为了周数计算准确，建议选择周日哦", Toast.LENGTH_LONG).show()
                } else {
                    Toasty.success(this, "为了周数计算准确，建议选择周一哦", Toast.LENGTH_LONG).show()
                }
            }
            "上课时间" -> {
                startActivityForResult(Intent(this, TimeSettingsActivity::class.java).apply {
                    putExtra("selectedId", viewModel.table.timeTable)
                }, REQUEST_CODE_CHOOSE_TABLE)
            }
            "管理已添加课程" -> {
                start<ScheduleManageActivity> {
                    putExtra("selectedTable", TableSelectBean(
                            id = viewModel.table.id,
                            background = viewModel.table.background,
                            tableName = viewModel.table.tableName,
                            maxWeek = viewModel.table.maxWeek,
                            nodes = viewModel.table.nodes,
                            type = viewModel.table.type
                    ))
                }
            }
            "课程预告范围" -> {
                // 两个选项互斥，永远只有一个生效（存在同一个 prefs 键里）
                val options = arrayOf(
                        "提前 2 天：只看今天和明天",
                        "提前 7 天：看今天起 7 天内")
                val current = if (getPrefer().getInt(Const.KEY_WIDGET_PREVIEW_DAYS, 7) == 2) 0 else 1
                MaterialAlertDialogBuilder(this)
                        .setTitle("课程预告范围")
                        .setSingleChoiceItems(options, current) { dialog, which ->
                            val days = if (which == 0) 2 else 7
                            getPrefer().edit { putInt(Const.KEY_WIDGET_PREVIEW_DAYS, days) }
                            item.value = if (days == 2) "提前 2 天" else "提前 7 天"
                            mAdapter.notifyItemChanged(position)
                            // 按新范围立刻刷新今日小部件
                            launch {
                                val awm = AppWidgetManager.getInstance(applicationContext)
                                viewModel.getScheduleWidgetIds().forEach {
                                    if (it.detailType == 1) {
                                        AppWidgetUtils.refreshTodayWidget(applicationContext, awm, it.id, viewModel.table)
                                    }
                                }
                            }
                            dialog.dismiss()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
            }
            "生日提醒" -> {
                startActivityForResult(Intent(this, BirthdayReminderActivity::class.java), REQUEST_CODE_BIRTHDAY)
            }
        }
    }

    private fun onVerticalItemClick(item: VerticalItem) {
        when (item.title) {
            "课程表背景图片" -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                try {
                    startActivityForResult(intent, REQUEST_CODE_CHOOSE_BG)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            "恢复默认颜色" -> {
                ThemeManager.reset(this@ScheduleSettingsActivity)
                ThemeManager.applyToTable(this@ScheduleSettingsActivity, viewModel.table)
                mAdapter.notifyDataSetChanged()
                Toasty.success(applicationContext, "已恢复默认颜色~").show()
            }
            "小部件标题颜色" -> {
                buildColorPickerDialogBuilder(viewModel.table.widgetTextColor, WIDGET_TITLE_COLOR)
            }
            "小部件课程颜色" -> {
                buildColorPickerDialogBuilder(viewModel.table.widgetCourseTextColor, WIDGET_COURSE_TEXT_COLOR)
            }
            "小部件格子边框颜色" -> {
                buildColorPickerDialogBuilder(viewModel.table.widgetStrokeColor, WIDGET_STROKE_COLOR)
            }
            "高级功能" -> {
                start<SettingsActivity>()
            }
        }
    }

    private fun onVerticalItemLongClick(item: VerticalItem): Boolean {
        return when (item.title) {
            "课程表背景图片" -> {
                viewModel.table.background = ""
                ThemeManager.setSolidBgEnabled(this, false)
                Toasty.success(applicationContext, "恢复默认壁纸成功~").show()
                true
            }
            else -> false
        }
    }

    /**
     * 颜色项点击：弹出圆盘调色板，确认后写入 ThemeManager 并同步课表实时预览。
     */
    private fun onColorItemClick(item: ColorItem) {
        val initColor = when (item.colorKey) {
            ThemeManager.BG -> ThemeManager.getSolidBackgroundColor(this)
                    ?: ThemeManager.getColor(this, ThemeManager.BG)
            else -> ThemeManager.getColor(this, item.colorKey)
        }
        ColorWheelDialogFragment.newInstance(initColor) { color ->
            ThemeManager.setColor(this, item.colorKey, color)
            if (item.colorKey == ThemeManager.BG) {
                // 使用纯色背景时关闭图片背景，避免互相遮挡
                ThemeManager.setSolidBgEnabled(this, true)
                viewModel.table.background = ""
            }
            ThemeManager.applyToTable(this, viewModel.table)
            mAdapter.notifyDataSetChanged()
        }.show(supportFragmentManager, "color_wheel")
    }

    /** 主题变化（预设切换/颜色修改/重置）后刷新本页小圆点与选中态 */
    private val themeChangedListener: () -> Unit = {
        runOnUiThread {
            ThemeManager.applyToTable(this, viewModel.table)
            mAdapter.notifyDataSetChanged()
        }
    }

    override fun onDestroy() {
        ThemeManager.removeListener(themeChangedListener)
        super.onDestroy()
    }

    private fun buildColorPickerDialogBuilder(color: Int, id: Int) {
        ColorPickerFragment.newBuilder()
                .setShowAlphaSlider(true)
                .setColor(color)
                .setDialogId(id)
                .show(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CHOOSE_BG && resultCode == RESULT_OK) {
            //viewModel.table.background = Matisse.obtainResult(data)[0].toString()
            val uri = data?.data
            if (uri != null) {
                viewModel.table.background = uri.toString()
                // 使用图片背景时关闭纯色背景
                ThemeManager.setSolidBgEnabled(this, false)
            }
        }
        if (requestCode == REQUEST_CODE_CHOOSE_TABLE && resultCode == RESULT_OK) {
            viewModel.table.timeTable = data!!.getIntExtra("selectedId", 1)
        }
        if (requestCode == REQUEST_CODE_BIRTHDAY) {
            // 生日可能刚设好，回来把这一项的显示同步一下
            val idx = showItems.indexOfFirst { it.title == "生日提醒" }
            if (idx >= 0 && showItems[idx] is HorizontalItem) {
                (showItems[idx] as HorizontalItem).value = birthdayText()
                mAdapter.notifyItemChanged(idx)
            }
        }
    }

    override fun onBackPressed() {
        launch {
            AppWidgetUtils.updateWidget(applicationContext)
            viewModel.saveSettings()
            val list = viewModel.getScheduleWidgetIds()
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            list.forEach {
                when (it.detailType) {
                    0 -> {
                        if (it.info == viewModel.table.id.toString()) {
                            AppWidgetUtils.refreshScheduleWidget(applicationContext, appWidgetManager, it.id, viewModel.table)
                        }
                    }
                    1 -> AppWidgetUtils.refreshTodayWidget(applicationContext, appWidgetManager, it.id, viewModel.table, false)
                }
            }
            setResult(RESULT_OK)
            finish()
        }
    }
}

