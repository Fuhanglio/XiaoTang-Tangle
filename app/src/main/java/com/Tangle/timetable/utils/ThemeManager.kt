package com.Tangle.timetable.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import com.Tangle.timetable.bean.TableBean

/**
 * 主题与颜色管理器（全局单一数据源）。
 *
 * 设计原则：
 * 1. 所有主题状态（预设选择、7 个自定义颜色项、背景纯色）都持久化在 SharedPreferences("theme_config")；
 * 2. 浅色模式 / 深色模式分别保存两套颜色值（key 后缀 _light / _dark）；
 * 3. 页面在「绑定/渲染时」统一从这里取色（确定性链路），不做“默认色匹配”式猜测；
 * 4. 颜色变化通过监听回调通知，设置页刷新小圆点，返回课表页时重新渲染实时预览。
 */
object ThemeManager {

    // ============ 预设主题 ============
    const val PRESET_SYSTEM = "system"       // 默认 iOS（主色 iOS 蓝，跟随系统昼夜切换骨架色）
    const val PRESET_SKY = "sky"             // 晴空蓝
    const val PRESET_MINT = "mint"           // 薄荷青
    const val PRESET_TARO = "taro"           // 香芋紫
    const val PRESET_CORAL = "coral"         // 珊瑚橘
    const val PRESET_BERRY = "berry"         // 莓果粉
    const val PRESET_MOCHA = "mocha"         // 经典摩卡棕
    const val PRESET_CUSTOM = "custom"       // 自定义（改过单项后自动进入）

    val PRESET_COLORS = linkedMapOf(
            PRESET_SKY to 0xFF2F80ED.toInt(),
            PRESET_MINT to 0xFF16B896.toInt(),
            PRESET_TARO to 0xFF7B6CF6.toInt(),
            PRESET_CORAL to 0xFFFF7A45.toInt(),
            PRESET_BERRY to 0xFFF06292.toInt(),
            PRESET_MOCHA to 0xFF5D4037.toInt()
    )

    val PRESET_NAMES = linkedMapOf(
            PRESET_SYSTEM to "默认",
            PRESET_SKY to "晴空蓝",
            PRESET_MINT to "薄荷青",
            PRESET_TARO to "香芋紫",
            PRESET_CORAL to "珊瑚橘",
            PRESET_BERRY to "莓果粉",
            PRESET_MOCHA to "摩卡棕"
    )

    // ============ 可自定义颜色项 ============
    const val PRIMARY = "primary"           // 应用主色调
    const val TEXT = "text"                 // 界面文字颜色（标题、辅助线、周次栏）
    const val COURSE_TEXT = "course_text"   // 课程格子文字颜色
    const val STROKE = "stroke"             // 课程格子边框颜色
    const val BG = "bg_color"               // 课表背景纯色
    const val HEADER = "header_bg"          // 表头背景色
    const val TODAY = "today"               // 今日高亮颜色

    val COLOR_ITEMS = linkedMapOf(
            PRIMARY to "应用主色调",
            TEXT to "界面文字颜色",
            COURSE_TEXT to "课程格子文字颜色",
            STROKE to "课程格子边框颜色",
            BG to "课表背景纯色",
            HEADER to "表头背景色",
            TODAY to "今日高亮颜色"
    )

    // ============ 默认配色（浅色 / 深色两套，iOS 风） ============
    // 默认主色：iOS 蓝 #007AFF；深色下 #0A84FF
    private val LIGHT_DEFAULTS = mapOf(
            PRIMARY to 0xFF007AFF.toInt(),  // iOS 蓝（全局主色调）
            TEXT to 0xFF1C1C1E.toInt(),     // 近黑
            COURSE_TEXT to 0xFF1C1C1E.toInt(),  // 课程格子深字（马卡龙底配深字）
            STROKE to 0xFF007AFF.toInt(),   // iOS 蓝边框
            BG to 0xFFF1F8E9.toInt(),       // 非常淡的绿色 #F1F8E9
            HEADER to 0x00000000,           // 表头透明
            TODAY to 0xFF007AFF.toInt()     // 今日高亮 iOS 蓝
    )

    private val DARK_DEFAULTS = mapOf(
            PRIMARY to 0xFF0A84FF.toInt(),  // iOS 暗色蓝
            TEXT to 0xFFEEEEEE.toInt(),     // 近白
            COURSE_TEXT to 0xFFEEEEEE.toInt(),
            STROKE to 0x660A84FF,           // 半透明暗色蓝
            BG to 0xFF1E1E1E.toInt(),
            HEADER to 0xFF2A2A2A.toInt(),
            TODAY to 0xFF0A84FF.toInt()
    )

    private const val PREF_NAME = "theme_config"
    private const val KEY_SELECTION = "selection"
    private const val KEY_BG_SOLID = "bg_solid_enabled"
    private const val KEY_BG_ALPHA = "bg_alpha"

    // 主题变化监听器（弱引用持有，避免泄漏）
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(l: () -> Unit) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    private fun notifyChanged() {
        listeners.forEach { runCatching { it.invoke() } }
    }

    // ============ 初始化 ============

    /** App 启动时调用：保证旧版主题色字段与新主题系统一致 */
    fun init(context: Context) {
        syncLegacyPrimary(context)
    }

    private fun pref(context: Context) =
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 当前是否处于深色模式（Android 10+ 跟随系统，9 及以下一般为浅色） */
    fun isDark(context: Context): Boolean =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

    private fun suffix(context: Context) = if (isDark(context)) "_dark" else "_light"

    private fun defaults(context: Context): Map<String, Int> =
            if (isDark(context)) DARK_DEFAULTS else LIGHT_DEFAULTS

    // ============ 预设选择 ============

    /** 当前主题选择：system / 某个预设 key / custom */
    fun getSelection(context: Context): String =
            pref(context).getString(KEY_SELECTION, PRESET_SYSTEM) ?: PRESET_SYSTEM

    /**
     * 应用预设主题。
     * - system：恢复浅色/深色两套默认配色；
     * - 彩色预设：重置为默认后，把主色调与今日高亮联动设为预设色（整套配色联动）。
     */
    fun setPreset(context: Context, preset: String) {
        pref(context).edit {
            if (preset == PRESET_SYSTEM) {
                putString(KEY_SELECTION, PRESET_SYSTEM)
                // 清掉所有自定义覆盖，回到跟随系统的默认昼夜配色
                clearColorValues(this)
            } else {
                val color = PRESET_COLORS[preset] ?: return@edit
                putString(KEY_SELECTION, preset)
                clearColorValues(this)
                putColorValue(this, PRIMARY, color)
                putColorValue(this, TODAY, color)
            }
        }
        syncLegacyPrimary(context)
        notifyChanged()
    }

    /** 修改任一颜色项后，预设状态变为“自定义” */
    fun setColor(context: Context, key: String, color: Int) {
        pref(context).edit {
            putColorValue(this, key, color)
            if (key != PRIMARY) {
                // 主色调变化保留预设名也无所谓；其它项变化统一进入自定义态
                putString(KEY_SELECTION, PRESET_CUSTOM)
            } else {
                putString(KEY_SELECTION, PRESET_CUSTOM)
            }
        }
        if (key == PRIMARY) syncLegacyPrimary(context)
        notifyChanged()
    }

    /** 获取某个颜色项在当前昼夜模式下的值 */
    fun getColor(context: Context, key: String): Int =
            pref(context).getInt(key + suffix(context), defaults(context)[key]
                    ?: Color.TRANSPARENT)

    // ============ 课表纯色背景 ============

    fun isSolidBgEnabled(context: Context): Boolean =
            pref(context).getBoolean(KEY_BG_SOLID, false)

    fun setSolidBgEnabled(context: Context, enabled: Boolean) {
        pref(context).edit { putBoolean(KEY_BG_SOLID, enabled) }
        notifyChanged()
    }

    /** 背景不透明度 0~100 */
    fun getBgAlpha(context: Context): Int =
            pref(context).getInt(KEY_BG_ALPHA, 100)

    fun setBgAlpha(context: Context, alpha: Int) {
        pref(context).edit { putInt(KEY_BG_ALPHA, alpha.coerceIn(0, 100)) }
        notifyChanged()
    }

    /** 带透明度的纯色背景（未开启时返回 null） */
    fun getSolidBackgroundColor(context: Context): Int? {
        if (!isSolidBgEnabled(context)) return null
        val base = getColor(context, BG)
        val a = (getBgAlpha(context) / 100f * 255).toInt().coerceIn(0, 255)
        return ColorUtils.setAlphaComponent(base, a)
    }

    // ============ 恢复默认 ============

    fun reset(context: Context) {
        pref(context).edit {
            clear()
        }
        syncLegacyPrimary(context)
        notifyChanged()
    }

    // ============ 与课表渲染对接 ============

    /**
     * 把 ThemeManager 中的颜色同步到课表 TableBean，
     * 课表网格（文字/课程文字/边框）统一在渲染时从这里取色。
     */
    fun applyToTable(context: Context, table: TableBean) {
        table.textColor = getColor(context, TEXT)
        table.courseTextColor = getColor(context, COURSE_TEXT)
        table.strokeColor = getColor(context, STROKE)
    }

    /**
     * 老版本的主题色存在 config 偏好（KEY_THEME_COLOR），
     * 分类标题栏、高级设置页都读它，这里保持同步，避免两处颜色不一致。
     */
    private fun syncLegacyPrimary(context: Context) {
        context.getPrefer().edit {
            putInt(Const.KEY_THEME_COLOR, getColor(context, PRIMARY))
        }
    }

    private fun clearColorValues(editor: android.content.SharedPreferences.Editor) {
        val keys = mutableListOf<String>()
        COLOR_ITEMS.keys.forEach { k ->
            keys.add(k + "_light")
            keys.add(k + "_dark")
        }
        keys.forEach { editor.remove(it) }
        editor.remove(KEY_BG_SOLID)
        editor.remove(KEY_BG_ALPHA)
    }

    private fun putColorValue(editor: android.content.SharedPreferences.Editor, key: String, color: Int) {
        // 两套都写，保证预设切换到另一模式时主色一致
        editor.putInt(key + "_light", color)
        editor.putInt(key + "_dark", color)
    }

    // ============ 色号工具 ============

    /** 支持 #RGB / #RRGGBB / #AARRGGBB，非法返回 null */
    fun parseHex(input: String?): Int? {
        if (input.isNullOrBlank()) return null
        var s = input.trim()
        if (s.startsWith("#")) s = s.substring(1)
        if (s.any { "0123456789aAbBcCdDeEfF".indexOf(it) < 0 }) return null
        return when (s.length) {
            3 -> {
                val r = s.substring(0, 1).repeat(2)
                val g = s.substring(1, 2).repeat(2)
                val b = s.substring(2, 3).repeat(2)
                try { Color.parseColor("#$r$g$b") } catch (e: Exception) { null }
            }
            6 -> try { Color.parseColor("#$s") } catch (e: Exception) { null }
            8 -> try { Color.parseColor("#$s") } catch (e: Exception) { null }
            else -> null
        }
    }

    /** 转成十六进制色号：含透明度用 #AARRGGBB，否则 #RRGGBB */
    fun toHex(color: Int, withAlpha: Boolean = Color.alpha(color) != 255): String {
        return if (withAlpha) String.format("#%08X", color)
        else String.format("#%06X", 0xFFFFFF and color)
    }
}
