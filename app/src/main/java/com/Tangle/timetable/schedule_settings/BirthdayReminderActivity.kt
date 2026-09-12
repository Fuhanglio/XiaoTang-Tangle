package com.Tangle.timetable.schedule_settings

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Typeface
import android.os.Bundle
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
import es.dmoral.toasty.Toasty
import splitties.resources.color

/**
 * 生日提醒设置页：滚轮选「月 + 日」+ 写一段话。
 *
 * 小部件里放不了输入框（RemoteViews 不支持 EditText），所以编辑界面放在这里，
 * 今日课表小部件只负责在「今天没课」时把这个板块显示出来。
 */
class BirthdayReminderActivity : BaseBlurTitleActivity() {

    override val layoutId: Int
        get() = R.layout.activity_birthday_reminder

    private lateinit var npMonth: NumberPicker
    private lateinit var npDay: NumberPicker
    private lateinit var tvPreview: AppCompatTextView
    private lateinit var etText: AppCompatEditText

    override fun onSetupSubButton(tvButton: AppCompatTextView): AppCompatTextView? {
        tvButton.text = "保存"
        tvButton.typeface = Typeface.DEFAULT_BOLD
        tvButton.setTextColor(color(R.color.colorAccent))
        tvButton.setOnClickListener {
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
        npMonth.minValue = 1
        npMonth.maxValue = 12
        npMonth.displayedValues = (1..12).map { "${it}月" }.toTypedArray()
        npMonth.value = BirthdayUtils.month(this)

        // 日：随月份联动，2 月按今年闰年情况给 28 或 29
        bindDays(npMonth.value, BirthdayUtils.day(this))
        npMonth.setOnValueChangedListener { _, _, newValue ->
            bindDays(newValue, 1)
            updatePreview()
        }

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
    }

    /** 按当前月份重建「日」滚轮，尽量保留原来的选择 */
    private fun bindDays(month: Int, preferDay: Int) {
        val maxDay = BirthdayUtils.maxDay(month)
        npDay.minValue = 1
        npDay.maxValue = maxDay
        npDay.displayedValues = (1..maxDay).map { "${it}日" }.toTypedArray()
        npDay.value = preferDay.coerceIn(1, maxDay)
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
        try {
            val awm = AppWidgetManager.getInstance(this)
            val table = AppDatabase.getDatabase(this).tableDao().getDefaultTableSync() ?: return
            val ids = awm.getAppWidgetIds(ComponentName(this, TodayCourseAppWidget::class.java))
            for (id in ids) {
                AppWidgetUtils.refreshTodayWidget(this, awm, id, table)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
