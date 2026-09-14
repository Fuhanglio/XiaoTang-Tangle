package com.Tangle.timetable.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
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

    private const val TAG = "CrashLogger"

    fun install(context: Context) {
        try {
            val app = context.applicationContext
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

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val sb = StringBuilder()
        sb.append("time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())).append('\n')
        sb.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" / Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(')').append('\n')
        sb.append("thread: ").append(thread.name).append('\n')
        try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            sb.append("version: ").append(pi.versionName).append(" (").append(pi.versionCode).append(')').append('\n')
        } catch (t: Throwable) {
        }
        sb.append('\n').append(Log.getStackTraceString(throwable))

        val text = sb.toString()
        val name = "xiaotang_crash_" + System.currentTimeMillis() + ".txt"

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
