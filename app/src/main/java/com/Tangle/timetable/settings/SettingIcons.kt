package com.Tangle.timetable.settings

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.Tangle.timetable.R
import com.Tangle.timetable.utils.ThemeManager
import splitties.dimensions.dip

/**
 * 设置行图标统一管理：按「条目标题 → 白色线性图标 + 柔和彩色圆角块」映射。
 * 只做表现层映射，不改动任何 Item 数据类与条目构造代码。
 */
object SettingIcons {

    data class IconSpec(val iconRes: Int, val blockColorRes: Int)

    private val map = mapOf(
            // ===== 统一设置页（SettingsActivity） =====
            "设置当前课表" to IconSpec(R.drawable.s_cal, R.color.icon_blue),
            "节数栏显示具体时间" to IconSpec(R.drawable.s_clock, R.color.icon_teal),
            "课表下方留白" to IconSpec(R.drawable.s_padding, R.color.icon_indigo),
            "页面预加载" to IconSpec(R.drawable.s_flash, R.color.icon_purple),
            "主题颜色" to IconSpec(R.drawable.s_palette, R.color.icon_pink),
            "显示主题" to IconSpec(R.drawable.s_halfmoon_filled, R.color.icon_indigo),
            "显示空视图图片" to IconSpec(R.drawable.s_image, R.color.icon_green),
            "显示日视图背景" to IconSpec(R.drawable.s_layers, R.color.icon_cyan),
            "主界面虚拟键沉浸" to IconSpec(R.drawable.s_corners, R.color.icon_slate),
            "开启上课提醒" to IconSpec(R.drawable.s_bell, R.color.icon_orange),
            "提前几分钟提醒" to IconSpec(R.drawable.s_alarm, R.color.icon_orange),
            "今日小部件隐藏已结束课程" to IconSpec(R.drawable.s_eye, R.color.icon_slate),
            "提醒通知常驻" to IconSpec(R.drawable.s_pin, R.color.icon_indigo),
            "小部件不更新？点此修复" to IconSpec(R.drawable.s_widget_fix, R.color.icon_green),

            // ===== 课表设置页（ScheduleSettingsActivity） =====
            "课表名称" to IconSpec(R.drawable.s_cal_edit, R.color.icon_blue),
            "上课时间" to IconSpec(R.drawable.s_clock, R.color.icon_teal),
            "学期开始日期" to IconSpec(R.drawable.s_cal, R.color.icon_indigo),
            "当前周" to IconSpec(R.drawable.s_weeks, R.color.icon_purple),
            "管理已添加课程" to IconSpec(R.drawable.q_list, R.color.icon_pink),
            "一天课程节数" to IconSpec(R.drawable.s_nodes, R.color.icon_green),
            "学期周数" to IconSpec(R.drawable.s_cal, R.color.icon_cyan),
            "周日为每周第一天" to IconSpec(R.drawable.s_halfmoon, R.color.icon_slate),
            "显示周六" to IconSpec(R.drawable.s_grid, R.color.icon_blue),
            "显示周日" to IconSpec(R.drawable.s_grid, R.color.icon_teal),
            "在格子内显示上课时间" to IconSpec(R.drawable.s_clock, R.color.icon_indigo),
            "课程表背景图片" to IconSpec(R.drawable.s_bg_image, R.color.icon_purple),
            "课程格子高度" to IconSpec(R.drawable.s_slide, R.color.icon_pink),
            "课程格子不透明度" to IconSpec(R.drawable.s_opacity, R.color.icon_green),
            "课程显示文字大小" to IconSpec(R.drawable.s_text_size, R.color.icon_cyan),
            "显示非本周课程" to IconSpec(R.drawable.s_eye, R.color.icon_slate),
            "小部件格子高度" to IconSpec(R.drawable.s_widget_fix, R.color.icon_blue),
            "小部件格子不透明度" to IconSpec(R.drawable.s_opacity, R.color.icon_teal),
            "小部件显示文字大小" to IconSpec(R.drawable.s_text_size, R.color.icon_purple),
            "小部件标题颜色" to IconSpec(R.drawable.s_drop, R.color.icon_pink),
            "小部件课程颜色" to IconSpec(R.drawable.s_drop, R.color.icon_green),
            "小部件格子边框颜色" to IconSpec(R.drawable.s_drop, R.color.icon_cyan),
            "课表背景不透明度" to IconSpec(R.drawable.s_opacity, R.color.icon_indigo),
            "恢复默认颜色" to IconSpec(R.drawable.s_reset, R.color.icon_gray),
            "高级功能" to IconSpec(R.drawable.s_advanced, R.color.icon_orange)
    )

    fun specOf(title: String): IconSpec? = map[title]

    /**
     * 构造 36dp 圆角 10dp 柔和彩色图标块（内嵌 20dp 白色线性图标）。
     * 未映射的标题返回 null（调用方按需跳过，行布局保持原样）。
     */
    fun buildIconBlock(context: Context, title: String): View? {
        val spec = specOf(title) ?: return null
        val block = LinearLayout(context)
        block.layoutParams = LinearLayout.LayoutParams(
                context.dip(36), context.dip(36)).apply {
            marginEnd = context.dip(12)
        }
        block.gravity = Gravity.CENTER
        block.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dip(10).toFloat()
            setColor(ContextCompat.getColor(context, spec.blockColorRes))
        }
        val icon = ImageView(context)
        icon.setImageResource(spec.iconRes)
        icon.layoutParams = ViewGroup.LayoutParams(context.dip(20), context.dip(20))
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        block.addView(icon)
        return block
    }

    /** 开关打开时的轨道色：默认 iOS 主题用 iOS 绿；选中彩色预设后跟随预设主色 */
    fun getSwitchOnColor(context: Context): Int {
        return if (ThemeManager.getSelection(context) == ThemeManager.PRESET_SYSTEM) {
            ContextCompat.getColor(context, R.color.switch_on)
        } else {
            ThemeManager.getColor(context, ThemeManager.PRIMARY)
        }
    }

    /** 开关关闭时的轨道色（深浅色自适应） */
    fun getSwitchOffColor(context: Context): Int =
            ContextCompat.getColor(context, R.color.switch_track_off)

    /**
     * 统一为 iOS 胶囊开关样式（主设置页与课表设置页共用同一实现）：
     * 白色圆滑块（switch_thumb）+ StateListDrawable 轨道（开=getSwitchOnColor，关=getSwitchOffColor），
     * 同时置空 trackTintList / thumbTintList，避免主题 tint 覆盖 drawable 配色。
     * 只改外观，不改任何开关的 id 与绑定逻辑。
     */
    fun applyIosSwitchStyle(switchView: SwitchCompat) {
        val ctx = switchView.context
        switchView.setThumbResource(R.drawable.switch_thumb)
        val onColor = getSwitchOnColor(ctx)
        val offColor = getSwitchOffColor(ctx)
        val trackOn = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = ctx.dip(15).toFloat()
            setColor(onColor)
        }
        val trackOff = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = ctx.dip(15).toFloat()
            setColor(offColor)
        }
        switchView.trackDrawable = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), trackOn)
            addState(intArrayOf(), trackOff)
        }
        switchView.trackTintList = null
        switchView.thumbTintList = null
    }
}
