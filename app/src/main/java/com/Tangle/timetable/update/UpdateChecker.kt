package com.Tangle.timetable.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object UpdateChecker {

    /**
     * W6-07：本函数**不再**用「跳过此版本」过滤结果。
     *
     * 原实现里 `Const.KEY_UPDATE_SKIP_VERSION` 被**无条件**用于过滤，而 manual 与 auto
     * 共用本函数 → 用户点过「跳过此版本」后，再去关于页手动点「检查更新」会拿到
     * [UpdateState.Latest]，弹一句「已是最新版本」，
     * **把"我主动跳过了"伪装成"服务端确实没有新版"**，属误导。
     *
     * 现在：只要确实有更新就返回 [UpdateState.Available]。"是否被用户跳过"是**展示层**的事，
     * 交给 UpdateManager / UpdateDialog 读偏好后再决定：自动检查静默跳过（不打扰），
     * 手动检查照常弹窗但把中性按钮从「跳过此版本」改成「保持跳过」。
     */
    suspend fun check(context: Context): UpdateState = withContext(Dispatchers.IO) {
        val (localCode, localName) = getLocalVersion(context)
        return@withContext try {
            val info = UpdateClient.fetchLatest()
            val newer = isNewer(localCode, localName, info.versionCode, info.versionName)
            if (newer) UpdateState.Available(info) else UpdateState.Latest
        } catch (e: Exception) {
            if (isNetworkError(e)) UpdateState.NoNetwork else UpdateState.Error(e.message ?: "检查失败")
        }
    }

    private fun getLocalVersion(context: Context): Pair<Int, String> {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION") info.versionCode
            }
            val name = info.versionName ?: ""
            code to name
        } catch (e: Exception) {
            0 to ""
        }
    }

    /** 优先用 versionCode 比较；tag 非纯数字（如 v3.693）时退回版本名语义比较 */
    private fun isNewer(localCode: Int, localName: String, remoteCode: Int, remoteName: String): Boolean {
        if (remoteCode > 0 && localCode > 0) return remoteCode > localCode
        return semanticGreater(remoteName, localName)
    }

    /** 版本名语义比较：3.9 < 3.10；完全相等返回 false */
    private fun semanticGreater(a: String, b: String): Boolean {
        val pa = a.removePrefix("v").removePrefix("V").split(".").mapNotNull { it.toIntOrNull() }
        val pb = b.removePrefix("v").removePrefix("V").split(".").mapNotNull { it.toIntOrNull() }
        if (pa.isEmpty() || pb.isEmpty()) return false
        val n = kotlin.math.max(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun isNetworkError(e: Exception): Boolean {
        return e is UnknownHostException || e is SocketTimeoutException || e is ConnectException ||
                (e.message?.contains("Unable to resolve host", true) == true) ||
                (e.message?.contains("failed to connect", true) == true)
    }
}
