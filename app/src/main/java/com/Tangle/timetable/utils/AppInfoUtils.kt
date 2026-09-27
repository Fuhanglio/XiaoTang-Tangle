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

    @Throws(Exception::class)
    fun getVersionName(context: Context): String {
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }

    @Throws(Exception::class)
    fun getVersionCode(context: Context): Long {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
        }
    }
}
