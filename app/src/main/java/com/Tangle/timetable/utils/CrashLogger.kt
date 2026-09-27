package com.Tangle.timetable.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志落盘：未捕获异常写入公共「下载」目录（API29+ 走 MediaStore，无需权限），
 * 文件名形如 xiaotang_crash_<时间戳>.txt，方便用户取回发给开发者定位。
 * 注意：整个流程必须自兜底，绝不能在崩溃处理里再抛异常。
 */
object CrashLogger {

    /** 由 install() 注入的应用级 Context：让 logCaught() 无需再传 Context */
    private var appContext: Context? = null

    private const val TAG = "CrashLogger"

    /**
     * `logCaught` 专用的后台落盘作用域（W8-11）。
     *
     * `logCaught` 被设计成"无 Context、随取随用"，原来在**调用线程**同步落盘
     * （MediaStore `insert` + `openOutputStream` + `write`，失败还要退到私有目录再写一次）。
     * 而它的触发点之一 `WidgetScheduler.setAlarm / canScheduleExact` 的 catch 位于
     * **引导页主线程路径**上（用户点「完成」时 `setAlarm` 抛错）→ 变成"主线程做 ContentResolver IO"。
     *
     * 改为丢到自己的 IO 作用域；`SupervisorJob` 保证落盘失败不影响其它日志任务。
     * 注意：崩溃处理器 `install()` 里的 `write` **保持同步**，必须保证进程退出前写完。
     */
    private val io = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun install(context: Context) {
        try {
            val app = context.applicationContext
            appContext = app
            val prev = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    write(app, thread, throwable)
                } catch (t: Throwable) {
                    Log.e(TAG, "写崩溃日志失败", t)
                }
                prev?.uncaughtException(thread, throwable)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "安装崩溃处理器失败", t)
        }
    }

    /**
     * 记录「被 try/catch 吞掉」的异常（不崩溃但会影响功能），写进「下载」目录便于取回。
     *
     * W8-11：写盘动作丢到 [io] 后台作用域，**不在调用线程同步落盘**。
     * 「调用线程名」在切线程**之前**取好，否则记下来的是 IO 线程名，失去排查价值。
     */
    fun logCaught(tag: String, throwable: Throwable) {
        try {
            val ctx = appContext ?: return
            val thread = Thread.currentThread()
            io.launch {
                try {
                    write(ctx, thread, throwable, "caught_" + tag)
                } catch (t: Throwable) {
                    // 后台落盘失败也不能影响主流程
                    Log.e(TAG, "后台写日志失败", t)
                }
            }
        } catch (t: Throwable) {
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable, prefix: String = "crash") {
        val sb = StringBuilder()
        sb.append("time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())).append('\n')
        sb.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" / Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(')').append('\n')
        sb.append("thread: ").append(thread.name).append('\n')
        try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            // W6-14：统一 longVersionCode（API 28+）；低系统读废弃 int 字段转 Long（B14 的 E14）
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi.longVersionCode
            } else {
                @Suppress("DEPRECATION") pi.versionCode.toLong()
            }
            sb.append("version: ").append(pi.versionName).append(" (").append(versionCode).append(')').append('\n')
        } catch (t: Throwable) {
        }
        sb.append('\n').append(Log.getStackTraceString(throwable))

        val text = sb.toString()
        val name = "xiaotang_" + prefix + "_" + System.currentTimeMillis() + ".txt"

        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                    Log.e(TAG, "崩溃日志已写入 下载/$name")
                    return
                }
            } catch (t: Throwable) {
                Log.e(TAG, "MediaStore 写崩溃日志失败，退回私有目录", t)
            }
        }
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        File(dir, name).writeText(text)
        Log.e(TAG, "崩溃日志已写入 " + File(dir, name).absolutePath)
    }
}
