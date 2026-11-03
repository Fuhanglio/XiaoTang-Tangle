package com.Tangle.timetable.widget

import android.content.Context
import com.Tangle.timetable.utils.AppInfoUtils

/**
 * 小部件内容指纹：内容没变就不下发 RemoteViews。
 * 存在独立 SharedPreferences（不与用户配置混在一起），丢失只会多刷一次，无副作用。
 */
object WidgetFingerprint {

    private const val PREF = "widget_fp"
    private const val KEY_LAST_REFRESH = "__last_refresh_at"

    @Volatile private var versionSalt = -1L
    private fun salt(context: Context): Long {
        if (versionSalt < 0) {
            versionSalt = runCatching { AppInfoUtils.getVersionCode(context) }.getOrDefault(0L)
        }
        return versionSalt
    }

    fun hashOf(context: Context, vararg parts: Any?): Int {
        val sb = StringBuilder(256)
        sb.append(salt(context)).append('\u0001')
        for (p in parts) sb.append(p).append('\u0001')
        return sb.toString().hashCode()
    }

    /** true = 与上次渲染结果完全一致，可以跳过本次 updateAppWidget */
    fun isUnchanged(context: Context, key: String, hash: Int): Boolean {
        val p = prefs(context)
        // 用 contains 而不是拿 MIN_VALUE 当哨兵：String.hashCode 理论上可能返回 MIN_VALUE
        return p.contains(key) && p.getInt(key, 0) == hash
    }

    fun remember(context: Context, key: String, hash: Int) {
        prefs(context).edit().putInt(key, hash).apply()
    }

    fun lastRefreshAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_REFRESH, 0L)

    fun markRefreshed(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_REFRESH, System.currentTimeMillis()).apply()
    }

    /** 版本升级 / 手动强制刷新 / 主题切换后清空，保证下一轮必定重绘（当前版本不需要主动调用，留给未来入口） */
    fun invalidateAll(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
