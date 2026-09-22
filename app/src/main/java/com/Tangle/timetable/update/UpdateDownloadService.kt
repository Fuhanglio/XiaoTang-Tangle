package com.Tangle.timetable.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 前台 Service：下载 APK 并通过 FileProvider 调起系统安装器。
 * 前台类型 dataSync（Android 14 要求），作用域存储下无需 WRITE_EXTERNAL_STORAGE。
 */
class UpdateDownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cancelled = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelled.set(true)
            stopSelf()
            return START_NOT_STICKY
        }
        val apkUrl = intent?.getStringExtra(EXTRA_APK_URL)
        val versionName = intent?.getStringExtra(EXTRA_VERSION_NAME) ?: ""
        if (apkUrl.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification(0, true, versionName))
        scope.launch { download(apkUrl, versionName) }
        return START_NOT_STICKY
    }

    private suspend fun download(apkUrl: String, versionName: String) {
        val file = File(getExternalFilesDir("updates"), "XiaoTang-Tangle-update.apk").also {
            it.parentFile?.mkdirs()
        }
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            val resp = client.newCall(Request.Builder().url(apkUrl).build()).execute()
            if (cancelled.get()) { stopSelf(); return }
            if (!resp.isSuccessful) { fail(versionName, "HTTP ${resp.code()}"); return }
            val body = resp.body() ?: run { fail(versionName, "空响应"); return }
            val total = body.contentLength()
            file.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(8192)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        if (cancelled.get()) { stopSelf(); return }
                        out.write(buf, 0, read)
                        downloaded += read
                        val pct = if (total > 0) ((downloaded * 100) / total).toInt() else -1
                        updateProgress(pct, versionName)
                    }
                }
            }
            if (cancelled.get()) { stopSelf(); return }
            install(file, versionName)
        } catch (e: Exception) {
            if (cancelled.get()) { stopSelf(); return }
            fail(versionName, e.message ?: "下载失败")
        }
    }

    private fun install(file: File, versionName: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE, uri).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(this, 0, intent, pendingFlags(0))
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("下载完成")
            .setContentText("点击安装 v$versionName")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build())
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // 未授权「安装未知来源」时跳转设置页引导开启
            try {
                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) { }
        }
        stopSelf()
    }

    private fun fail(versionName: String, msg: String) {
        val open = packageManager.getLaunchIntentForPackage(packageName)
        val pi = PendingIntent.getActivity(this, 0, open, pendingFlags(0))
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("更新下载失败")
            .setContentText("$msg，点击重试")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build())
        stopSelf()
    }

    private fun updateProgress(pct: Int, versionName: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(pct, false, versionName))
    }

    private fun buildNotification(progress: Int, indeterminate: Boolean, versionName: String): android.app.Notification {
        val cancelIntent = Intent(this, UpdateDownloadService::class.java).apply { action = ACTION_CANCEL }
        val cancelPi = PendingIntent.getService(this, 1, cancelIntent, pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT))
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在下载更新 v$versionName")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancelPi)
        if (indeterminate) builder.setProgress(0, 0, true)
        else builder.setProgress(100, progress, false).setContentText("已下载 $progress%")
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "应用更新", NotificationManager.IMPORTANCE_LOW))
            }
        }
    }

    private fun pendingFlags(extra: Int): Int {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return extra or base
    }

    companion object {
        const val EXTRA_APK_URL = "apk_url"
        const val EXTRA_VERSION_NAME = "version_name"
        const val ACTION_CANCEL = "cancel_update"
        const val NOTIF_ID = 9090
        const val CHANNEL_ID = "app_update"

        fun start(context: Context, apkUrl: String, versionName: String) {
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                putExtra(EXTRA_APK_URL, apkUrl)
                putExtra(EXTRA_VERSION_NAME, versionName)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
