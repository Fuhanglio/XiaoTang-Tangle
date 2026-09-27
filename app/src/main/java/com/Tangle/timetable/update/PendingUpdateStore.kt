package com.Tangle.timetable.update

import android.content.Context
import android.util.Log
import com.Tangle.timetable.utils.AppInfoUtils
import java.io.File

/**
 * W6-13③：「下载已完成但没装」的可恢复记录。
 *
 * 场景：[UpdateDownloadService] 把 APK 下载、校验、原子改名都做完后，安装只能靠
 * **用户点通知**（Android 10+ 后台 startActivity 会被静默拦截）。用户当时没点、
 * 直接划掉通知或重启手机，更新入口就丢了 —— 下次冷启动时由 SplashActivity 调
 * [peek] 把这个入口补回来，弹一次「现在安装？」。
 *
 * 存储：一个独立 SharedPreferences（记录极小：版本名/版本号/APK 绝对路径/sha256）。
 * 全部方法吞异常并降级为「没有待装更新」：冷启动路径不允许被它拖慢或拖崩。
 */
object PendingUpdateStore {

    private const val TAG = "PendingUpdate"
    private const val PREFS = "pending_update"
    private const val KEY_VERSION_NAME = "versionName"
    private const val KEY_VERSION_CODE = "versionCode"
    private const val KEY_APK_PATH = "apkPath"
    private const val KEY_SHA256 = "sha256"

    data class Pending(
        val versionName: String,
        val versionCode: Long,
        val apkPath: String,
        val sha256: String
    )

    /** 下载服务在「原子改名成功 + 全部校验通过」后调用；与发「点此安装」通知同一处 */
    fun put(context: Context, versionName: String, versionCode: Long, apkPath: String, sha256: String) {
        try {
            prefs(context).edit()
                .putString(KEY_VERSION_NAME, versionName)
                .putLong(KEY_VERSION_CODE, versionCode)
                .putString(KEY_APK_PATH, apkPath)
                .putString(KEY_SHA256, sha256)
                .apply()
        } catch (t: Throwable) {
            Log.e(TAG, "put failed (ignored)", t)
        }
    }

    /**
     * 有待装更新才返回非空，三个条件缺一不可：
     *  ① 记录存在；② 记录里的 APK 文件仍在；③ versionCode 高于当前已装版本。
     * 任一不满足（含文件被系统清理、已经装过同版/更新版、读取异常）都清记录后返回 null。
     */
    fun peek(context: Context): Pending? {
        return try {
            val sp = prefs(context)
            val apkPath = sp.getString(KEY_APK_PATH, null)
            if (apkPath.isNullOrBlank()) {
                null
            } else {
                val versionCode = sp.getLong(KEY_VERSION_CODE, 0L)
                val file = File(apkPath)
                val current = try {
                    AppInfoUtils.getVersionCode(context)
                } catch (t: Throwable) {
                    Log.e(TAG, "current versionCode unreadable (ignored)", t)
                    Long.MAX_VALUE   // 读不到当前版本就不提示，宁可不弹也别误弹
                }
                when {
                    !file.exists() -> {
                        Log.d(TAG, "pending apk missing, clearing: $apkPath")
                        clear(context)
                        null
                    }
                    versionCode <= current -> {
                        Log.d(TAG, "pending code $versionCode <= current $current, clearing")
                        clear(context)
                        null
                    }
                    else -> Pending(
                        sp.getString(KEY_VERSION_NAME, "") ?: "",
                        versionCode,
                        apkPath,
                        sp.getString(KEY_SHA256, "") ?: ""
                    )
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "peek failed (treat as none)", t)
            null
        }
    }

    fun clear(context: Context) {
        try {
            prefs(context).edit().clear().apply()
        } catch (t: Throwable) {
            Log.e(TAG, "clear failed (ignored)", t)
        }
    }

    /**
     * 清更新目录里的 *.apk.part 半截包（与 UpdateDownloadService 的目录/后缀保持一致）。
     * 冷启动时调一次：正式名 .apk 刻意保留（可能正被系统安装器引用）。
     */
    fun cleanPartFiles(context: Context) {
        try {
            val dir = context.getExternalFilesDir("updates") ?: context.filesDir
            dir.listFiles()
                ?.filter { it.name.endsWith(PART_SUFFIX) }
                ?.forEach { it.delete() }
        } catch (t: Throwable) {
            Log.e(TAG, "cleanPartFiles failed (ignored)", t)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PART_SUFFIX = ".apk.part"
}
