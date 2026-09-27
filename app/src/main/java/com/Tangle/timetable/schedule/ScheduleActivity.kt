package com.Tangle.timetable.schedule

import android.appwidget.AppWidgetManager
import android.content.ContentValues
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.lifecycle.Observer
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.Tangle.timetable.R
import com.Tangle.timetable.base_view.BaseActivity
import com.Tangle.timetable.bean.TableBean
import com.Tangle.timetable.bean.TableSelectBean
import com.Tangle.timetable.course_add.AddCourseActivity
import com.Tangle.timetable.intro.AboutActivity
import com.Tangle.timetable.intro.IntroYoungActivity
import com.Tangle.timetable.schedule_manage.ScheduleManageActivity
import com.Tangle.timetable.schedule_settings.ScheduleSettingsActivity
import com.Tangle.timetable.settings.SettingsActivity
import com.Tangle.timetable.suda_life.SudaLifeActivity
import com.Tangle.timetable.utils.*
import es.dmoral.toasty.Toasty
import it.sephiroth.android.library.xtooltip.Tooltip
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.activities.start
import splitties.dimensions.dip
import splitties.resources.styledDimenPxSize
import splitties.snackbar.action
import splitties.snackbar.longSnack
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.text.ParseException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

class ScheduleActivity : BaseActivity() {

    companion object {
        private const val TAG = "ScheduleActivity"
    }

    private val viewModel by viewModels<ScheduleViewModel>()

    /**
     * W7-01：把老库备份（wakeup_v*.db / -wal / -shm）导出成 zip，存到用户能访问的目录。
     * 为什么必须导出：备份落在 getExternalFilesDir(null)/db_backup/，而 Android 11+ 上
     * /Android/data/<包名>/ 对文件管理器与 SAF 都不可见 ——「备份只落盘、用户拿不到」
     * 才是台账所说"无恢复入口"的真实成因（真·一键回灌还需补 1~6→7 迁移，见 B3 验收报告）。
     */
    private val exportDbBackup = registerForActivityResult(
            ActivityResultContracts.CreateDocument()) { uri: Uri? ->
        val path = getPrefer().getString(Const.KEY_DB_BACKUP_PATH, "") ?: ""
        if (uri == null || path.isEmpty()) return@registerForActivityResult
        try {
            val db = File(path)
            if (!db.isFile) {
                Toasty.error(this, "备份文件不存在，可能已被清理>_<").show()
                return@registerForActivityResult
            }
            contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    // 主库 + -wal/-shm 一起打包，缺哪个跳哪个（-wal 里可能还有没落盘的改动）
                    for (f in arrayOf(db, File("$path-wal"), File("$path-shm"))) {
                        if (!f.isFile) continue
                        zip.putNextEntry(ZipEntry(f.name))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            Toasty.success(this, "备份已导出~可发给开发者协助恢复").show()
        } catch (e: Exception) {
            Toasty.error(this, "导出备份失败>_<\n${e.message}").show()
        }
    }

    private var mAdapter: SchedulePagerAdapter? = null

    private lateinit var ui: ScheduleActivityUI
    /** 课程 LiveData 观察者与其绑定的 tableId：切换默认课表后必须按新 id 重绑（修复 C3） */
    private var courseObserversTableId = -1
    private val courseObservers = arrayOfNulls<androidx.lifecycle.Observer<List<com.Tangle.timetable.bean.CourseBean>>>(7)
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private val preLoad by lazy(LazyThreadSafetyMode.NONE) {
        getPrefer().getBoolean(Const.KEY_SCHEDULE_PRE_LOAD, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (getPrefer().getBoolean(Const.KEY_HIDE_NAV_BAR, false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        }
        ui = ScheduleActivityUI(this)
        setContentView(ui.root)

        // B1：老库（DB 1~6）被 fallback 破坏性重建后的一次性告知提示（用户点「知道了」后置位，之后不再弹）
        // W7-01：文案原先写「已尝试自动迁移」，但 1~6 → 8 根本没有逐级迁移，实际是 DROP 后按新
        //        schema 重建空库，属误导；现如实说明「格式不兼容 / 已重建 / 原数据已备份至 …」
        val prefer = getPrefer()
        if (prefer.getBoolean(Const.KEY_DB_OLD_VERSION_DETECTED, false) &&
                !prefer.getBoolean(Const.KEY_DB_MIGRATED_V8_BACKUP, false)) {
            val backupPath = prefer.getString(Const.KEY_DB_BACKUP_PATH, "") ?: ""
            MaterialAlertDialogBuilder(this)
                    .setTitle("课表数据重建提示")
                    .setMessage("旧版本数据库格式不兼容，已按新格式重建课表；" +
                            "原数据已备份至：\n$backupPath\n" +
                            "如需找回旧课表，请联系开发者协助恢复")
                    // W7-01：补"恢复入口"——备份在应用私有目录里，用户自己进不去，给个导出按钮
                    .setNeutralButton("导出备份") { _, _ ->
                        val name = File(backupPath).name.ifEmpty { "wakeup_backup.db" }
                        exportDbBackup.launch("$name.zip")
                    }
                    .setCancelable(false)
                    .setPositiveButton("知道了") { _, _ ->
                        getPrefer().edit {
                            putBoolean(Const.KEY_DB_MIGRATED_V8_BACKUP, true)
                        }
                    }
                    .show()
        }

        val json = getPrefer().getString(Const.KEY_OLD_VERSION_COURSE, "")
        if (!json.isNullOrEmpty()) {
            launch {
                try {
                    viewModel.updateFromOldVer(json)
                    Toasty.success(applicationContext, "升级成功~").show()
                } catch (e: Exception) {
                    Toasty.error(applicationContext, "出现异常>_<\n${e.message}").show()
                }
            }
        }

        bottomSheetBehavior = BottomSheetBehavior.from(ui.bottomSheet)

        // 抽屉打开时自动收起底部弹层
        ui.drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: android.view.View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: android.view.View) {
                if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                }
            }

            override fun onDrawerClosed(drawerView: android.view.View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        lifecycleScope.launch {
            delay(500)
            // Activity 已销毁时不再弹新手引导（原 postDelayed 无法取消，可能挂到已销毁窗口）
            if (!isDestroyed && !isFinishing && !getPrefer().getBoolean(Const.KEY_HAS_INTRO, false)) {
                initIntro()
            }
        }

        initView()
        initNavView()

        // 权限引导：关键权限未就绪时提示（每次冷启动检查，避免打扰已授权用户）
        if (com.Tangle.timetable.widget.WidgetGuideHelper.shouldShowGuide(this)) {
            startActivity(Intent(this, com.Tangle.timetable.widget.PermissionGuideActivity::class.java))
        }

        viewModel.initTableSelectList().observe(this, Observer {
            if (it == null) return@Observer
            viewModel.tableSelectList.clear()
            viewModel.tableSelectList.addAll(it)
            if (ui.rvTableName.adapter == null) {
                initTableMenu(viewModel.tableSelectList)
            } else {
                ui.rvTableName.adapter?.notifyDataSetChanged()
            }
        })

        initBottomSheetAction()
    }

    private fun initTheme() {
        val x = (ViewUtils.getRealSize(this).x * 0.5).toInt()
        val y = (ViewUtils.getRealSize(this).y * 0.5).toInt()

        // 纯色背景模式（主题自定义颜色）优先，其次图片背景，最后默认背景
        val solidBg = ThemeManager.getSolidBackgroundColor(this)
        if (solidBg != null && viewModel.table.background == "") {
            ui.bg.setImageDrawable(null)
            ui.bg.setBackgroundColor(solidBg)
        } else if (viewModel.table.background != "") {
            ui.bg.background = null
            Glide.with(this)
                    .load(viewModel.table.background)
                    .override(x, y)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            Toasty.error(this@ScheduleActivity, "无法检索背景图片，可能是它为某个应用私有所致，可以尝试在文件管理器中将它移动到其他位置，或是选择其它图片", Toasty.LENGTH_LONG).show()
                            return false
                        }

                        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            return false
                        }
                    })
                    .error(R.drawable.main_background_2020_1)
                    .into(ui.bg)
        } else {
            ui.bg.background = null
            Glide.with(this)
                    .load(R.drawable.main_background_2020_1)
                    .override(x, y)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(ui.bg)
        }

        for (i in 0 until ui.content.childCount) {
            val view = ui.content.getChildAt(i)
            when (view) {
                is AppCompatTextView -> view.setTextColor(viewModel.table.textColor)
                is AppCompatImageButton -> view.setColorFilter(viewModel.table.textColor)
            }
        }

        if (ViewUtils.judgeColorIsLight(viewModel.table.textColor)) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
            }
        }

        viewModel.itemHeight = dip(viewModel.table.itemHeight)
    }

    private fun initTableMenu(data: MutableList<TableSelectBean>) {
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val adapter = TableNameAdapter(R.layout.item_table_select_main, data)
        adapter.addChildClickViewIds(R.id.menu_setting)
        adapter.setOnItemChildClickListener { _, view, _ ->
            when (view.id) {
                R.id.menu_setting -> {
                    startActivityForResult(Intent(this,
                            ScheduleSettingsActivity::class.java).apply {
                        putExtra("tableData", viewModel.table)
                    }, Const.REQUEST_CODE_SCHEDULE_SETTING)
                }
            }
        }
        adapter.setOnItemClickListener { _, _, position ->
            if (position < data.size) {
                if (data[position].id != viewModel.table.id) {
                    launch {
                        viewModel.changeDefaultTable(data[position].id)
                        initView()
                        val list = viewModel.getScheduleWidgetIds()
                        val table = viewModel.getDefaultTable() ?: return@launch
                        withContext(Dispatchers.Default) {
                            list.forEach {
                                when (it.detailType) {
                                    // 周课表部件（含绑定默认表的空 info 实例）也随切换刷新；
                                    // refreshWidgetById 内部为同步 DAO，移到后台线程执行
                                    0 -> AppWidgetUtils.refreshWidgetById(applicationContext, appWidgetManager, it.id, 0)
                                    1 -> AppWidgetUtils.refreshTodayWidget(applicationContext, appWidgetManager, it.id, table)
                                }
                            }
                        }
                    }
                }
            }
        }
        ui.rvTableName.adapter = adapter
    }

    private fun initBottomSheetAction() {
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {

            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    ui.weekScrollView.smoothScrollTo(if (viewModel.selectedWeek > 4) (viewModel.selectedWeek - 4) * dip(56) else 0, 0)
                    ui.selectWeekButton(viewModel.selectedWeek)
                }
            }
        })
        ui.createScheduleBtn.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(this)
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
                            viewModel.addBlankTable(editText.text.toString())
                            Toasty.success(this@ScheduleActivity, "新建成功~").show()
                        } catch (e: Exception) {
                            Toasty.error(this@ScheduleActivity, "操作失败>_<").show()
                        }
                        dialog.dismiss()
                    }
                }
            }
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
        ui.manageScheduleBtn.setOnClickListener {
            startActivityForResult(
                    Intent(this, ScheduleManageActivity::class.java), Const.REQUEST_CODE_SCHEDULE_SETTING)
        }
        ui.changeWeekBtn.setOnClickListener {
            startActivityForResult(Intent(this,
                    ScheduleSettingsActivity::class.java).apply {
                putExtra("tableData", viewModel.table)
                putExtra("settingItem", "当前周")
            }, Const.REQUEST_CODE_SCHEDULE_SETTING)
        }
        ui.timeBtn.setOnClickListener {
            startActivityForResult(Intent(this,
                    ScheduleSettingsActivity::class.java).apply {
                putExtra("tableData", viewModel.table)
                putExtra("settingItem", "上课时间")
            }, Const.REQUEST_CODE_SCHEDULE_SETTING)
        }
        ui.changeBgBtn.setOnClickListener {
            startActivityForResult(Intent(this,
                    ScheduleSettingsActivity::class.java).apply {
                putExtra("tableData", viewModel.table)
                putExtra("settingItem", "课程表背景")
            }, Const.REQUEST_CODE_SCHEDULE_SETTING)
        }
        ui.courseBtn.setOnClickListener {
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
        ui.qaBtn.setOnClickListener {
            Utils.openUrl(this, "https://support.qq.com/embed/97617/faqs-more")
        }
    }

    private fun initIntro() {
        val builder = Tooltip.Builder(this@ScheduleActivity)
                .overlay(true)
                .maxWidth(dip(240))
                .customView(R.layout.my_tooltip, R.id.tv_tip)
        val navTooltip = builder
                .text("点这里打开左栏")
                .anchor(ui.navBtn)
                .create()
        val jumpTooltip = builder
                .text("点这里快速回到当前周")
                .anchor(ui.weekDayView)
                .create()
        val addBtnTooltip = builder
                .text("点这里手动添加课程")
                .anchor(ui.addBtn)
                .create()
        val importTooltip = builder
                .text("点这里导入课表")
                .anchor(ui.importBtn)
                .create()
        val shareTooltip = builder
                .text("点这里导出、分享课表")
                .anchor(ui.shareBtn)
                .create()
        val moreTooltip = builder
                .text("点这里查看更多设置")
                .anchor(ui.moreBtn)
                .create()
        navTooltip.doOnHidden {
            jumpTooltip.doOnHidden {
                addBtnTooltip.doOnHidden {
                    importTooltip.doOnHidden {
                        shareTooltip.doOnHidden {
                            moreTooltip.doOnHidden {
                                getPrefer().edit {
                                    putBoolean(Const.KEY_HAS_INTRO, true)
                                }
                                showBottomSheetDialog()
                            }.show(ui.content, Tooltip.Gravity.LEFT)
                            moreTooltip.contentView?.findViewById<TextView>(R.id.btn_next)?.apply {
                                text = "完成教程"
                                setOnClickListener {
                                    moreTooltip.hide()
                                }
                            }
                        }.show(ui.content, Tooltip.Gravity.LEFT)
                        shareTooltip.contentView?.findViewById<TextView>(R.id.btn_next)?.setOnClickListener {
                            shareTooltip.hide()
                        }
                    }.show(ui.content, Tooltip.Gravity.LEFT)
                    importTooltip.contentView?.findViewById<TextView>(R.id.btn_next)?.setOnClickListener {
                        importTooltip.hide()
                    }
                }.show(ui.content, Tooltip.Gravity.BOTTOM)
                addBtnTooltip.contentView?.findViewById<TextView>(R.id.btn_next)?.setOnClickListener {
                    addBtnTooltip.hide()
                }
            }.show(ui.content, Tooltip.Gravity.BOTTOM)
            jumpTooltip.contentView?.findViewById<TextView>(R.id.btn_next)?.setOnClickListener {
                jumpTooltip.hide()
            }
        }.show(ui.content, Tooltip.Gravity.RIGHT)
        navTooltip.contentView?.findViewById<TextView>(R.id.btn_next)?.setOnClickListener {
            navTooltip.hide()
        }
    }

    override fun onStart() {
        super.onStart()
        ui.dateView.text = CourseUtils.getTodayDate()
    }

    private fun initNavView() {
        ui.navViewStart.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.nav_setting -> {
                    ui.drawerLayout.closeDrawer(GravityCompat.START)
                    ui.drawerLayout.postDelayed({
                        startActivityForResult(Intent(this, SettingsActivity::class.java), Const.REQUEST_CODE_SCHEDULE_SETTING)
                    }, 360)
                    return@setNavigationItemSelectedListener true
                }
                R.id.nav_course -> {
                    ui.drawerLayout.closeDrawer(GravityCompat.START)
                    ui.drawerLayout.postDelayed({
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
                    }, 360)
                    return@setNavigationItemSelectedListener true
                }
                R.id.nav_about -> {
                    ui.drawerLayout.closeDrawer(GravityCompat.START)
                    ui.drawerLayout.postDelayed({
                        start<AboutActivity>()
                    }, 360)
                    return@setNavigationItemSelectedListener true
                }
                else -> {
                    Toasty.info(this.applicationContext, "敬请期待").show()
                    return@setNavigationItemSelectedListener true
                }
            }
        }
    }

    private fun initViewPage(maxWeek: Int, table: TableBean) {
        if (mAdapter == null) {
            mAdapter = SchedulePagerAdapter(maxWeek, preLoad, supportFragmentManager)
            ui.viewPager.adapter = mAdapter
            ui.viewPager.offscreenPageLimit = 1
        }
        mAdapter!!.maxWeek = maxWeek
        mAdapter!!.notifyDataSetChanged()
        if (CourseUtils.countWeek(table.startDate, table.sundayFirst) > 0) {
            ui.viewPager.currentItem = CourseUtils.countWeek(table.startDate, table.sundayFirst) - 1
        } else {
            ui.viewPager.currentItem = 0
        }
    }

    private fun initEvent() {
    ui.viewPager.clearOnPageChangeListeners()
        ui.addBtn.setOnClickListener {
            start<AddCourseActivity> {
                putExtra("tableId", viewModel.table.id)
                putExtra("maxWeek", viewModel.table.maxWeek)
                putExtra("nodes", viewModel.table.nodes)
                putExtra("id", -1)
            }
        }

        ui.moreBtn.setOnClickListener {
            showBottomSheetDialog()
            //ui.drawerLayout.openDrawer(Gravity.END)
        }

        ui.bottomSheet.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        // 周数胶囊点击 → 切换 viewPager（等价于原 ToggleGroup 的 OnButtonCheckedListener）
        ui.onWeekSelected = { week ->
            ui.viewPager.currentItem = week - 1
        }

        ui.navBtn.setOnClickListener {
            // 打开抽屉前先收回底部弹层，避免两层浮层叠加
            if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
            ui.drawerLayout.openDrawer(GravityCompat.START)
        }

        ui.shareBtn.setOnClickListener {
            ExportSettingsFragment().show(supportFragmentManager, null)
        }

        ui.importBtn.setOnClickListener {
            ImportChooseFragment().show(supportFragmentManager, "importDialog")
        }

        ui.weekDayView.setOnClickListener {
            ui.weekDayView.text = CourseUtils.getWeekday()
            if (viewModel.currentWeek > 0) {
                ui.viewPager.currentItem = viewModel.currentWeek - 1
            } else {
                ui.viewPager.currentItem = 0
            }
        }

        ui.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {

            override fun onPageSelected(position: Int) {
                viewModel.selectedWeek = (position + 1).coerceIn(1, viewModel.table.maxWeek)
                // 滑动切页时同步高亮对应周数胶囊
                ui.selectWeekButton(viewModel.selectedWeek)
                try {
                    if (viewModel.currentWeek > 0) {
                        if (viewModel.selectedWeek == viewModel.currentWeek) {
                            ui.weekView.text = "第${viewModel.selectedWeek}周"
                            ui.weekDayView.text = CourseUtils.getWeekday()
                        } else {
                            ui.weekView.text = "第${viewModel.selectedWeek}周"
                            ui.weekDayView.text = "非本周"
                        }
                    } else {
                        ui.weekView.text = "第${viewModel.selectedWeek}周"
                        ui.weekDayView.text = "还没有开学哦"
                    }
                } catch (e: ParseException) {
                    e.printStackTrace()
                }
            }

            override fun onPageScrolled(a: Int, b: Float, c: Int) {

            }

            override fun onPageScrollStateChanged(state: Int) {

            }
        })

    }

    private fun showBottomSheetDialog() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED

    }

    private fun initView() {
        launch {
            // W8-05：整段初始化合并了 DB 取表、主题应用、周次计算、弹窗、观察者注册等
            // 数十次可能抛异常的操作（SQLite、lateinit、IllegalState…），此前无任何兜底，
            // 任一环节抛出都会沿 lifecycleScope 冒泡到 Thread 默认处理器 → 主界面直接崩溃。
            // 此处统一兜底：失败时给可感知提示，而不是让整个 Activity 挂掉。
            try {
                viewModel.table = viewModel.getDefaultTable() ?: run {
                    // 不再静默早退（原实现主界面按钮全部无响应），给出可感知提示
                    Toasty.error(this@ScheduleActivity, "未找到默认课表，请到「多课表管理」新建或导入", Toast.LENGTH_LONG).show()
                    return@launch
                }
                // 渲染前统一应用主题颜色（主题系统为单一数据源）
                ThemeManager.applyToTable(this@ScheduleActivity, viewModel.table)
                viewModel.currentWeek = CourseUtils.countWeek(viewModel.table.startDate, viewModel.table.sundayFirst)
                viewModel.selectedWeek = viewModel.currentWeek
                if (viewModel.currentWeek > 0) {
                    if (viewModel.currentWeek <= viewModel.table.maxWeek) {
                        ui.weekView.text = "第${viewModel.currentWeek}周"
                    } else {
                        ui.weekView.text = "当前周已超出设定范围"
                        MaterialAlertDialogBuilder(this@ScheduleActivity)
                                .setTitle("提示")
                                .setMessage("发现当前周已超出设定的周数范围，是否去设置修改「当前周」或「开学日期」？")
                                .setPositiveButton("打开设置") { _, _ ->
                                    startActivityForResult(Intent(this@ScheduleActivity,
                                            ScheduleSettingsActivity::class.java).apply {
                                        putExtra("tableData", viewModel.table)
                                    }, Const.REQUEST_CODE_SCHEDULE_SETTING)
                                }
                                .setNegativeButton(R.string.cancel, null)
                                .show()
                    }
                } else {
                    ui.weekView.text = "还没有开学哦"
                }

                // 周数改为独立胶囊（LinearLayout 手动选中态）
                ui.rebuildWeekButtons(viewModel.table.maxWeek)

                launch {
                    delay(1000)
                    ui.selectWeekButton(viewModel.selectedWeek)
                    ui.weekScrollView.smoothScrollTo(if (viewModel.selectedWeek > 4) (viewModel.selectedWeek - 4) * dip(56) else 0, 0)
                }

                ui.weekDayView.text = CourseUtils.getWeekday()

                initTheme()

                viewModel.timeList = viewModel.getTimeList(viewModel.table.timeTable)

                viewModel.alphaInt = (255 * (viewModel.table.itemAlpha.toFloat() / 100)).roundToInt()

                initViewPage(viewModel.table.maxWeek, viewModel.table)

                initEvent()

                // 课程 LiveData 观察者按 tableId 绑定：切默认课表后必须解绑旧表、重绑新表。
                // 旧的一次性注册守卫会把观察者绑死在首次加载的 tableId 上，
                // 切表后 allCourseList 永远收不到新表数据（网格渲染旧表课程）。
                if (courseObserversTableId != viewModel.table.id) {
                    if (courseObserversTableId > 0) {
                        courseObservers.forEachIndexed { idx, ob ->
                            ob?.let {
                                viewModel.getRawCourseByDay(idx + 1, courseObserversTableId)
                                        .removeObserver(it)
                            }
                        }
                    }
                    for (i in 1..7) {
                        val ob = androidx.lifecycle.Observer<List<com.Tangle.timetable.bean.CourseBean>> { list ->
                            if (list == null) return@Observer
                            viewModel.allCourseList[i - 1].value = list
                        }
                        courseObservers[i - 1] = ob
                        viewModel.getRawCourseByDay(i, viewModel.table.id).observe(this@ScheduleActivity, ob)
                    }
                    courseObserversTableId = viewModel.table.id
                }
            } catch (e: Exception) {
                Toasty.error(this@ScheduleActivity, "界面初始化失败>_<\n${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK) {
            when (requestCode) {
                Const.REQUEST_CODE_EXPORT -> {
                    ui.content.longSnack("导出是否遇到了问题？") {
                        action("查看教程") {
                            Utils.openUrl(this@ScheduleActivity, "https://support.qq.com/embed/phone/97617/faqs/59883")
                        }
                    }
                }
            }
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        when (requestCode) {
            Const.REQUEST_CODE_SCHEDULE_SETTING -> initView()
            Const.REQUEST_CODE_IMPORT -> {
                showBottomSheetDialog()
                //ui.drawerLayout.openDrawer(Gravity.END)
                MaterialAlertDialogBuilder(this)
                        .setTitle("温馨提示")
                        .setView(AppCompatTextView(this).apply {
                            text = ViewUtils.getHtmlSpannedString("记得<b><font color='#5D4037'>仔细检查</font></b>有没有少课、课程信息对不对哦，不要到时候<b><font color='#5D4037'>一不小心就翘课</font></b>啦<br>解析算法不是100%可靠的哦<br>但会朝这个方向努力")
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            val space = styledDimenPxSize(R.attr.dialogPreferredPadding)
                            setPadding(space, dip(8), space, 0)
                        })
                        .setCancelable(false)
                        .setPositiveButton("我知道啦", null)
                        .show()
            }
            Const.REQUEST_CODE_EXPORT -> {
                val uri = data?.data
                launch {
                    try {
                        // R3-01：exportData 返回导出内容；部分定制选择器（如 ColorOS）会按
                        // MIME 把标题里的 .wakeup_schedule 改写成 .bin，导出的文件从此无法
                        // 被 .wakeup_schedule 过滤器识别 → 这里核对并纠正文件名，保住
                        // 「导出→文件管理器点开→导入」回环
                        val payload = viewModel.exportData(uri)
                        val finalUri = uri?.let { fixExportFileName(it, payload) }
                        showShareDialog("分享课程文件", finalUri!!)
                    } catch (e: Exception) {
                        Toasty.error(this@ScheduleActivity, "导出失败>_<${e.message}").show()
                    }
                }
            }
            Const.REQUEST_CODE_EXPORT_ICS -> {
                val uri = data?.data
                launch {
                    try {
                        viewModel.exportICS(uri)
                        showShareDialog("分享日历文件", uri!!)
                    } catch (e: Exception) {
                        Toasty.error(this@ScheduleActivity, "导出失败>_<${e.message}").show()
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * R3-01：核对导出文件名，必要时纠正扩展名。
     *
     * ColorOS 等定制文件选择器会按 MIME 类型（octet-stream）重写 CREATE_DOCUMENT 的
     * 默认文件名 —— 标题里的 .wakeup_schedule 被替换成 .bin，导出的文件从此无法被
     * 文件管理器的 .wakeup_schedule 过滤器识别，「导出→导入」回环断裂
     * （原生 DocumentsUI 不改写，所以只有部分 ROM 的用户踩到）。
     *
     * 处理（写入完成后）：
     *  ① display name 本来就以 .wakeup_schedule 结尾 → 什么都不做（原生行为，不回归）；
     *  ② 否则用 DocumentsContract.renameDocument 改回「表名.wakeup_schedule」；
     *  ③ rename 不被该存储位置支持（个别 provider）→ 把导出内容以正确文件名另存到
     *     应用目录（Android/data/<包名>/files/Download/），走 FileProvider 分享并明确告知；
     *  ④ 连另存也失败（无外部存储等）→ 提示手动改后缀，导出的原文件仍在。
     *
     * @return 最终用于分享的 Uri（rename 成功时是新 Uri）
     */
    private suspend fun fixExportFileName(uri: Uri, payload: ByteArray): Uri =
            withContext(Dispatchers.IO) {
                val expected = viewModel.table.tableName.ifEmpty { "我的课表" } + ".wakeup_schedule"
                val current = queryDisplayName(uri)
                Log.i(TAG, "R3-01 fix name: uri=$uri current=$current expected=$expected")
                if (current != null && current.endsWith(".wakeup_schedule", ignoreCase = true)) {
                    // 原生 DocumentsUI 等不改写扩展名，直接返回
                    return@withContext uri
                }
                // 通道一：DocumentsContract.renameDocument —— 只对 documents 型 uri 有效
                //（content://com.android.externalstorage.documents/…）
                val renamed = try {
                    DocumentsContract.renameDocument(contentResolver, uri, expected)
                } catch (e: Exception) {
                    Log.w(TAG, "R3-01 renameDocument failed", e)
                    null
                }
                val renamedName = renamed?.let { queryDisplayName(it) }
                if (renamed != null && renamedName != null &&
                        renamedName.endsWith(".wakeup_schedule", ignoreCase = true)) {
                    withContext(Dispatchers.Main) {
                        Toasty.info(this@ScheduleActivity,
                                "系统曾把文件名改成「$current」，已纠正为「$renamedName」",
                                Toasty.LENGTH_SHORT).show()
                    }
                    return@withContext renamed
                }
                // 通道二：MediaStore 改名 —— ColorOS 等选择器返回的是 media.documents 型
                // 门面 uri（content://com.android.providers.media.documents/…），它
                // 既不支持 renameDocument 也不支持 update；先转成 MediaStore 原生
                // uri（content://media/external/file/<id>）再改 DISPLAY_NAME
                val mediaRenamed = try {
                    val target = if (uri.authority == "com.android.providers.media.documents") {
                        val id = DocumentsContract.getDocumentId(uri)
                                .substringAfterLast(':').toLongOrNull()
                        if (id != null) MediaStore.Files.getContentUri("external", id) else null
                    } else uri
                    if (target == null) null else {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, expected)
                        }
                        val rows = contentResolver.update(target, values, null, null)
                        if (rows > 0) queryDisplayName(uri) else null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "R3-01 mediaStore rename failed", e)
                    null
                }
                if (mediaRenamed != null && mediaRenamed.endsWith(".wakeup_schedule", ignoreCase = true)) {
                    withContext(Dispatchers.Main) {
                        Toasty.info(this@ScheduleActivity,
                                "系统曾把文件名改成「$current」，已纠正为「$mediaRenamed」",
                                Toasty.LENGTH_SHORT).show()
                    }
                    return@withContext uri
                }
                // 通道三（兜底）：应用目录自己控制文件名另存一份 + FileProvider 分享
                val fallback = try {
                    val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    if (dir == null) null else {
                        val file = File(dir, expected)
                        file.outputStream().use { it.write(payload) }
                        FileProvider.getUriForFile(this@ScheduleActivity,
                                "$packageName.fileprovider", file)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "R3-01 fallback export failed", e)
                    null
                }
                if (fallback != null) {
                    withContext(Dispatchers.Main) {
                        Toasty.warning(this@ScheduleActivity,
                                "该存储位置不支持改名，系统把文件存成了「$current」\n" +
                                "已在应用目录另存一份正确的「$expected」，可用分享把它存到任意位置",
                                Toasty.LENGTH_LONG).show()
                    }
                    return@withContext fallback
                }
                withContext(Dispatchers.Main) {
                    Toasty.error(this@ScheduleActivity,
                            "已保存，但系统把文件名改成了「$current」\n" +
                            "请手动把后缀改回 .wakeup_schedule 才能再导入",
                            Toasty.LENGTH_LONG).show()
                }
                uri
            }

    private fun queryDisplayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    private fun showShareDialog(title: String, uri: Uri) {
        MaterialAlertDialogBuilder(this)
                .setTitle("分享")
                .setMessage("成功导出至你指定的路径啦，是否还要分享出去呢？")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("分享") { _, _ ->
                    val shareIntent = ShareCompat.IntentBuilder.from(this)
                            .setChooserTitle(title)
                            .setStream(uri)
                            .setType("*/*")
                            .createChooserIntent()
                            // R3-01：补 URI 读授权 —— SAF 与 FileProvider 的 content Uri
                            // 交给其它应用读都必须带这个旗标（FileProvider 路径硬性要求）
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(shareIntent)
                }
                .setCancelable(false)
                .show()
    }

    override fun onBackPressed() {
        when {
            ui.drawerLayout.isDrawerOpen(GravityCompat.START) -> ui.drawerLayout.closeDrawer(GravityCompat.START)
            ui.drawerLayout.isDrawerOpen(GravityCompat.END) -> ui.drawerLayout.closeDrawer(GravityCompat.END)
            bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        ui.viewPager.clearOnPageChangeListeners()
        AppWidgetUtils.updateWidget(applicationContext)
        super.onDestroy()
    }

}



