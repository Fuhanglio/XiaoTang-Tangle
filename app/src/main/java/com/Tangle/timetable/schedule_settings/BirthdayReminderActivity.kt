package com.Tangle.timetable.schedule_settings

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.NumberPicker
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import com.Tangle.timetable.AppDatabase
import com.Tangle.timetable.R
import com.Tangle.timetable.base_view.BaseBlurTitleActivity
import com.Tangle.timetable.today_appwidget.TodayCourseAppWidget
import com.Tangle.timetable.utils.AppWidgetUtils
import com.Tangle.timetable.utils.BirthdayUtils
import com.Tangle.timetable.utils.CrashLogger
import es.dmoral.toasty.Toasty
import splitties.resources.color

/**
 * 生日提醒设置页：滚轮选「月 + 日」+ 写一段话。
 *
 * 小部件里放不了输入框（RemoteViews 不支持 EditText），所以编辑界面放在这里，
 * 今日课表小部件只负责在「今天没课」时把这个板块显示出来。
 */
class BirthdayReminderActivity : BaseBlurTitleActivity() {

    companion object {
        private const val MAX_DAYS_IN_MONTH = 31   // onCreate 会带着“保存的日”进 bindDays，可能是 31；
                                                   // 预置值必须 >= 31，否则 ① 的 setValue 会被钳掉，
                                                   // 引出 index=-1 那条崩溃
    }

    override val layoutId: Int
        get() = R.layout.activity_birthday_reminder

    private lateinit var npMonth: NumberPicker
    private lateinit var npDay: NumberPicker
    private lateinit var tvPreview: AppCompatTextView
    private lateinit var etText: AppCompatEditText

    // ============ v156 修「快速甩月份滚轮跨多月 → 整页跳走」 ============
    //
    // 现象：在「月」滚轮上快速甩动（跨度超过两个月）时，整页自动跳走（退回课表设置）。
    //   慢速逐月拨动则无恙。
    //
    // 根因：跨度超过两个月 = NumberPicker 进入惯性滚动（fling），onValueChanged 高频连发；
    //   旧代码在【每次回调】里同步重建「日」滚轮（setMaxValue 31→30→29 往返 + setDisplayedValues
    //   数组重建 + setValue + 重挂 listener）。系统 NumberPicker 在滚动中被外部高频改写
    //   range/displayedValues 是文档化反模式，内部选择索引/输入状态的边界窗口就在这里，
    //   异常打穿主线程 → CrashLogger 落盘 → 进程退出 → 叠在课表设置之上的本页消失，
    //   表象即「跳页」。v123 修的边缘手势只是伴生触发器（起手靠左缘才命中），正主是本条。
    //
    // 修复（三件套，见下）：
    //   ① 去抖：月份回调不再立即重建，postDelayed 120ms 合并——滚动中完全不动「日」滚轮，
    //      停稳后只按最终月份重建一次；连发的回调全部合并，重入窗口不复存在。
    //   ② 保险丝：重建逻辑包 try/catch + CrashLogger.logCaught 落盘（xiaotang_caught_*.txt），
    //      任何残留异常只丢一次联动，绝不再杀页面。
    //   ③ 关 wrap：两滚轮关环形滚动（生日月日本就不该环形），收紧 wrap 模式下索引跨界路径。
    private val uiHandler = Handler(Looper.getMainLooper())

    /** 待重建的月份；-1 表示没有待处理的重置 */
    private var pendingMonth = -1

    // ============ R3-02：滚轮未停稳绝不触碰「日」轮的 range/displayedValues ============
    //
    // v156 的 120ms 去抖只能合并高频回调，压不住惯性滚动超过 120ms 的长甩：
    // bindRunnable 落在 fling 进行中 → setMaxValue 撞上 NumberPicker 内部数组的
    // 边界窗口 → ArrayIndexOutOfBoundsException（此前被保险丝接住，但每次都抛，
    // 与 9/16 v155 的崩溃同源 —— 保险丝只降概率，没治根）。
    // 治根：OnScrollListener 跟踪两个滚轮的滚动状态，只要任何一个还在滚
    // （TOUCH_SCROLL / FLING），「日」轮重建就整体推迟；停稳（IDLE）由监听器
    // 主动补执行，bindRunnable 自身再挂一个自重试兜底，防个别 ROM 漏发 IDLE。
    private var pickersScrolling = false

    // 逻辑放进成员函数而非 lambda：lambda 内自引用（自重试兜底）在
    // 本工程的 Kotlin 版本里会报 "must be initialized"，函数内引用无此限制
    private val bindRunnable: Runnable = Runnable { runBind() }

    private fun runBind() {
        // R3-02：滚轮未停稳 → 本轮不重建、不消费 pendingMonth；
        // 停稳由 pickerScrollListener 补触发，这里自重试兜底防漏发 IDLE
        if (pickersScrolling) {
            uiHandler.removeCallbacks(bindRunnable)
            uiHandler.postDelayed(bindRunnable, 200)
            return
        }
        val m = pendingMonth
        if (m == -1) return
        pendingMonth = -1
        try {
            bindDays(m, 1)
            updatePreview()
        } catch (t: Throwable) {
            // 保险丝：重建失败只记日志（下载/xiaotang_caught_birthday_bind_*.txt），
            // 页面保持可用，用户仍可手动滚动「日」滚轮完成设置
            CrashLogger.logCaught("birthday_bind", t)
        }
    }

    private val pickerScrollListener = NumberPicker.OnScrollListener { _, scrollState ->
        val scrolling = scrollState != NumberPicker.OnScrollListener.SCROLL_STATE_IDLE
        if (scrolling) {
            pickersScrolling = true
        } else if (pickersScrolling) {
            pickersScrolling = false
            // 刚停稳：滚动期间积压的月份联动现在补执行
            if (pendingMonth != -1) {
                uiHandler.removeCallbacks(bindRunnable)
                uiHandler.postDelayed(bindRunnable, 80)
            }
        }
    }
    // ================================================================================

    /** 月份变化 → 去抖重建「日」滚轮（连发合并，只认最后一个值） */
    private fun scheduleBindDays(month: Int) {
        pendingMonth = month
        uiHandler.removeCallbacks(bindRunnable)
        uiHandler.postDelayed(bindRunnable, 120)
    }

    /** 保存前把挂起的月份重建立即落定，保证日滚轮 range 与所选月份一致 */
    private fun flushPendingBind() {
        uiHandler.removeCallbacks(bindRunnable)
        val m = pendingMonth
        if (m == -1) return
        pendingMonth = -1
        try {
            bindDays(m, 1)
            updatePreview()
        } catch (t: Throwable) {
            CrashLogger.logCaught("birthday_bind", t)
        }
    }
    // ==================================================================

    override fun onSetupSubButton(tvButton: AppCompatTextView): AppCompatTextView? {
        tvButton.text = "保存"
        tvButton.typeface = Typeface.DEFAULT_BOLD
        tvButton.setTextColor(color(R.color.colorAccent))
        tvButton.setOnClickListener {
            // v156：先把去抖挂起的「日」滚轮重建立即落定，保证 range 与所选月份一致；
            // 即便此处出错，BirthdayUtils.save 内部还有 day.coerceIn(1, maxDay) 二次兜底
            flushPendingBind()
            BirthdayUtils.save(this, npMonth.value, npDay.value, etText.text.toString().trim())
            setResult(Activity.RESULT_OK)
            refreshTodayWidget()
            Toasty.success(this, "已保存").show()
            finish()
        }
        return tvButton
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        npMonth = findViewById(R.id.np_month)
        npDay = findViewById(R.id.np_day)
        tvPreview = findViewById(R.id.tv_preview)
        etText = findViewById(R.id.et_text)

        // 月：1 ~ 12
        // ⚠️ 月轮的顺序与 bindDays 保持一致（displayedValues 先行、maxValue 次之、
        // value 最后），别改回去 —— 顺序原理见 bindDays 内注释。
        // 实测：本 ROM 下 fresh NumberPicker 未预置 min/max 时直接 setValue(10)，value 会被钳到 0
        //（低于 minValue=1 的非法态），随后 setDisplayedValues 内部以 -1 打索引 → length=12; index=-1 FATAL。
        // 所以 value 必须放在最后一位，且 npDay 要先预置 range。
        npMonth.minValue = 1
        npMonth.displayedValues = (1..12).map { "${it}月" }.toTypedArray()
        npMonth.maxValue = 12
        npMonth.value = BirthdayUtils.month(this)
        // v156：生日月日不该环形滚动（1月上面不该出现12月），关 wrap 同时收紧
        // wrap 模式下选择索引跨界的路径
        npMonth.wrapSelectorWheel = false

        // 日：随月份联动，2 月按今年闰年情况给 28 或 29
        // ⚠️ 首次 bindDays 前必须先给 npDay 预置 min/max（实测理由见上方月轮注释）：
        // 不预置则 bindDays ① 的 setValue 段会落到“value 被钳到 0 的非法态”，② setDisplayedValues
        // 内部以 -1 打索引 → length=29; index=-1 FATAL。fresh picker 此时还没有 displayedValues，
        // setMaxValue 不会走崩溃路径，安全。预置上限取 MAX_DAYS_IN_MONTH（31），见常量注释。
        npDay.minValue = 1
        npDay.maxValue = MAX_DAYS_IN_MONTH
        bindDays(npMonth.value, BirthdayUtils.day(this))
        npDay.wrapSelectorWheel = false
        // v156：月份回调改为去抖重建（见类头部说明）——fling 连发时只认最终值
        npMonth.setOnValueChangedListener { _, _, newValue ->
            scheduleBindDays(newValue)
        }
        // R3-02：跟踪两轮滚动状态，惯性滚动期间整体跳过「日」轮重建（见类头说明）
        npMonth.setOnScrollListener(pickerScrollListener)
        npDay.setOnScrollListener(pickerScrollListener)

        etText.setText(BirthdayUtils.text(this))
        etText.setSelection(etText.text?.length ?: 0)
        updatePreview()

        // NumberPicker 要垂直滚动，而外层是 ScrollView，会被抢走手势，这里显式要回控制权
        val keepGesture = View.OnTouchListener { v, _ ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
        npMonth.setOnTouchListener(keepGesture)
        npDay.setOnTouchListener(keepGesture)

        // ⚠️ 关掉系统「边缘返回」手势对这一块的抢占（v123 修）
        //
        // 现象：在「月」滑轮上快速拨动（连着拨过两个月以上）时，整页会莫名退回上一级课表设置。
        // 原因：两个滑轮里「月」在左侧。手指在靠近屏幕左缘处起手竖滑时，Android 10+ 的
        //   「边缘返回」手势会把这串动作判成返回；而生日页是叠在课表设置之上的
        //   （父页用 startActivityForResult 拉起），一被判成返回，本页 finish() 就直接落回课表设置。
        //   「日」滑轮在右半边、离边缘远，所以拨它不会触发 —— 与实测现象一致。
        //   （v119 修的是另一件事：重建时 intent 里 settingItem 这个 extra 被重放导致的误跳，
        //     机制完全不同，所以那个修复对这个症状无效。）
        // 处理：把这排滑轮所在的整条横向区域（左右都拓到屏幕边缘）从手势判定区里排除。
        //   注意矩形用的是屏幕坐标；本页是全屏 window（BaseActivity 设了 LAYOUT_FULLSCREEN），
        //   global rect 与 window 坐标一致。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val row = findViewById<View>(R.id.ll_pickers)
            val applyExclusion = {
                val r = Rect()
                if (row.getGlobalVisibleRect(r)) {
                    val screenW = resources.displayMetrics.widthPixels
                    row.systemGestureExclusionRects = listOf(Rect(0, r.top, screenW, r.bottom))
                }
            }
            row.post { applyExclusion() }
            // 键盘弹收、从子页面返回后布局会变，重算一次，保证排除区始终贴着滑轮
            row.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyExclusion() }
        }
    }

    /** 按当前月份重建「日」滚轮，尽量保留原来的选择 */
    private fun bindDays(month: Int, preferDay: Int) {
        val maxDay = BirthdayUtils.maxDay(month)
        val target = preferDay.coerceIn(1, maxDay)
        npDay.minValue = 1
        // ⚠️ 这三行的顺序不能改，改了就是 length=29; index=29 那个崩溃。
        //
        // 框架 NumberPicker$AccessibilityNodeProviderImpl.getVirtualIncrementButtonText 取「加号」
        // 按钮文本的方式是 displayedValues[value - minValue + 1]，而它**只在 value < maxValue 时
        // 才真的去取索引** —— 范围判断看的是 maxValue，索引却打在 displayedValues 上。
        // 两个字段分开赋值，中间那一瞬就互相矛盾：
        //   2 月 29 天、日轮 value = 29 时切到 3 月：setMaxValue(31) 先执行（29 < 31 成立，加号路径
        //   被放行），而 displayedValues 还是 2 月那个 29 元素的旧数组
        //   → 索引 29 打在长度 29 的数组上 → ArrayIndexOutOfBoundsException: length=29; index=29
        //   → 主线程崩，且发生在框架 a11y 回调里，try/catch 与 CrashLogger 都接不住。
        //   （旧顺序下 setMaxValue(31) 自身在 ensureCachedScrollSelectorValue 里就会先抛一次，
        //     被 runBind 的保险丝接住后控件状态已永久损坏，随后任意 a11y 预取仍必崩 —— 实测如此。）
        //
        // 唯一能同时挡住「范围变大」和「范围变小」两个方向的顺序：
        //   ① value 先夹进新范围  ② displayedValues 换成长度正确的数组  ③ maxValue 最后改
        // 好处：三步中任意一步之后被打断（异常/调度），状态都是自洽的；旧顺序打断在第①步后永久损坏。
        npDay.value = target                                                   // ①
        npDay.displayedValues = (1..maxDay).map { "${it}日" }.toTypedArray()    // ②
        npDay.maxValue = maxDay                                                // ③
        npDay.setOnValueChangedListener { _, _, _ -> updatePreview() }
    }

    private fun updatePreview() {
        val m = npMonth.value
        val d = npDay.value
        val days = BirthdayUtils.daysUntil(m, d)
        val whenText = when (days) {
            0 -> "就是今天"
            1 -> "就在明天"
            else -> "还有 $days 天"
        }
        tvPreview.text = "将于 ${BirthdayUtils.nextDateText(m, d)} 提醒你 · $whenText"
        tvPreview.setTextColor(color(R.color.colorAccent))
    }

    /** 保存后立刻刷新今日小部件，回到桌面就能看到 */
    private fun refreshTodayWidget() {
        // 刷新内部为同步 DB 查询，移到后台线程，不在保存按钮的主线程做 IO
        val appContext = applicationContext
        Thread {
            try {
                val awm = AppWidgetManager.getInstance(appContext)
                val table = AppDatabase.getDatabase(appContext).tableDao().getDefaultTableSync() ?: return@Thread
                val ids = awm.getAppWidgetIds(ComponentName(appContext, TodayCourseAppWidget::class.java))
                for (id in ids) {
                    AppWidgetUtils.refreshTodayWidget(appContext, awm, id, table)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    override fun onDestroy() {
        // v156：清掉去抖回调，防止持有已销毁视图的 Runnable 滞留
        uiHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
