package com.Tangle.timetable.utils

import android.content.Context
import android.os.Build

/**
 * 本机 App 信息读取（W6-14 版本读取统一）。
 *
 * 全 App 读本机版本号只走这里：[getVersionCode] 统一使用
 * `PackageInfo.longVersionCode`（API 28+ 为 Long；API 21..27 走废弃的 int 字段并转 Long），
 * 与 [getVersionName] 一样取自同一个 PackageInfo，避免多处各写一套口径。
 */
object AppInfoUtils {

    /**
     * 本机版本名。
     *
     * API 28+ 的 `PackageInfo.versionName` 允许为 null；原实现直接把它当非空 String 返回，
     * 调用方拼字符串时会得到 "null" 或抛异常（取决于调用点推断的可空性）。
     * 现在读不到 / 为空一律兜底 "unknown"，任何调用点都不再需要自己 try/catch。
     */
    fun getVersionName(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"

    /**
     * 本机 versionCode。读取失败兜底 0，语义是"未知"，调用方需自行判 0。
     * 保持 Long 返回类型：PendingUpdateStore / WidgetFingerprint 等既有调用点按 Long 使用。
     */
    fun getVersionCode(context: Context): Long =
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
            }
        }.getOrDefault(0L)
}
