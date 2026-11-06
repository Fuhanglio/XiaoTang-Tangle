package com.Tangle.timetable.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.Tangle.timetable.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLException

/**
 * 前台 Service：下载 APK → 校验 → 通过文件 + 通知交还用户安装。
 *
 * 本版（v169）针对「点立即更新没反应」的根因做了三件事：
 *  1. 进度不再只存在于通知栏：每个阶段都 publish 到 [UpdateDownloadBus]，
 *     弹窗与关于页都订阅它，通知权限被拒也照样能看到进度；
 *  2. 通知刷新从「每 8 KB 一次」降到「≥200ms 或 ≥1% 一次」，17 MB 包不再打两千多次 IPC；
 *  3. 失败原因统一映射成中文（DNS / 超时 / SSL / HTTP / 空间 / 长度 / 校验），
 *     不再把 `e.message` 直接甩给用户；下载源按「直连 → ghproxy.net → gh-proxy.com」回退，
 *     但校验（Content-Type / Content-Length / sha256 / 包名 + 签名）一道都不减。
 *
 * 历史底线（B5 / B18，仍然有效）：
 *  - W6-01 完整性：Content-Type / Content-Length / sha256 三重校验；
 *  - W6-02 来源可信：装之前比对待装包与已装应用的包名 + 签名，不一致直接删包；
 *  - W6-03 权限前置：没开「安装未知应用」就引导，不再白下几十 MB；
 *  - W6-04 并发单飞：同一时刻只允许一个下载任务；
 *  - W6-09 残留清理：下载到 .part，全部校验通过才原子改名，失败/取消一律删干净；
 *  - W6-12 安装入口：通知才是可靠入口，Service 内不再 startActivity（后台被 BAL 静默拦截）。
 */
class UpdateDownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cancelled = AtomicBoolean(false)

    /** W6-04：下载单飞守卫。cancelled 只表达"取消"，不表达"已有任务在跑"，两者不能复用 */
    private val running = AtomicBoolean(false)

    /** 本次任务入参，供 fail() 构造"真的能重试"的 PendingIntent，以及进度弹窗重试时复用 */
    private var currentApkUrl = ""
    private var currentVersionName = ""
    private var currentVersionCode = 0L
    private var currentSha256 = ""

    /** 正在跑的任务拿到的 startId：重复点击时用它调 stopSelf（旧 id）来"不停服务" */
    private var activeStartId = 0

    /**
     * 最新一次 onStartCommand 的 startId。
     * stopSelf(id) 只有在 id 等于"最近一次 startId"时才会真正停服务，
     * 所以重复点击的 id 绝不能拿来 stopSelf，否则正在跑的下载会被一起干掉；
     * 收尾时则必须用最新 id，否则会被重复点击顶高的 startId 挡住、服务退不掉。
     */
    private var latestStartId = 0

    /** 最近一次通知引用：重复点击"立即更新"时直接复用它，不覆盖正在显示的进度 */
    private var lastNotification: Notification? = null
    private var lastPct = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // W6-09：清掉上次留下的 .part 半截包。
        // 正式名 `XiaoTang-Tangle-update.apk` 刻意不删：它可能正被系统安装器引用着
        getExternalFilesDir("updates")?.listFiles()
                ?.filter { it.name.endsWith(PART_SUFFIX) }
                ?.forEach { it.delete() }
    }

    /** W6-13②：Service 被系统回收时必须把协程也收干净 */
    override fun onDestroy() {
        cancelled.set(true)
        scope.cancel()
        runCatching {
            getExternalFilesDir("updates")?.listFiles()
                    ?.filter { it.name.endsWith(PART_SUFFIX) }
                    ?.forEach { it.delete() }
        }
        super.onDestroy()
    }

    /** W6-13②：用户划掉最近任务时也立即收尾 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        cancelled.set(true)
        UpdateDownloadBus.publish(DownloadState.Idle)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action == ACTION_CANCEL) {
            cancelled.set(true)
            // W6-13①：用户主动取消 → 进度通知必须立刻消失
            removeForeground()
            UpdateDownloadBus.publish(DownloadState.Idle)
            Log.i(TAG, "stage=cancel|startId=$startId")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val apkUrl = intent?.getStringExtra(EXTRA_APK_URL)
        val versionName = intent?.getStringExtra(EXTRA_VERSION_NAME) ?: ""
        val versionCode = intent?.getLongExtra(EXTRA_VERSION_CODE, 0L) ?: 0L
        val sha256 = intent?.getStringExtra(EXTRA_SHA256) ?: ""
        currentApkUrl = apkUrl.orEmpty()
        currentVersionName = versionName
        currentVersionCode = versionCode
        currentSha256 = sha256
        lastRequest = LastRequest(currentApkUrl, versionName, versionCode, sha256)
        if (apkUrl.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // W6-04：并发门锁。重复点「立即更新」时不再并存第二个任务。
        // ⚠ 收尾只能用 activeStartId（旧 id）。stopSelf(id) 的语义是"仅当 id 是最近一次 startId 才停服务"，
        // 这里传新来的 startId 会真的把服务停掉 → onDestroy 置 cancelled + scope.cancel，
        // 正在跑的下载被静默杀掉，表现就是"点了第二次，进度直接没了"。
        if (!running.compareAndSet(false, true)) {
            startForeground(NOTIF_ID, lastNotification
                    ?: buildMessageNotification(getString(R.string.update_dl_notif_duplicate),
                            getString(R.string.update_dl_notif_duplicate_text), null))
            Log.i(TAG, "stage=duplicate|startId=$startId|ignored=ongoing")
            stopSelf(activeStartId)
            return START_NOT_STICKY
        }
        activeStartId = startId
        // W6-03：先把「安装未知应用」权限问清楚，避免下完几十 MB 才在安装阶段失败
        if (!canRequestInstall(this)) {
            running.set(false)
            val pi = PendingIntent.getActivity(this, PI_REQUEST_SETTINGS,
                    Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT))
            startForeground(NOTIF_ID, buildMessageNotification(
                    getString(R.string.update_dl_notif_install_title),
                    getString(R.string.update_dl_notif_install_text), pi))
            detachForeground()
            Log.i(TAG, "stage=blocked|reason=install_permission")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildProgressNotification(versionName, 0, 0, 0, -1))
        UpdateDownloadBus.publish(DownloadState.Running(versionName, 0, 0, 0, -1))
        Log.i(TAG, "stage=start|version=$versionName|host=${hostOf(apkUrl)}|sha256=${sha256.isNotEmpty()}")
        scope.launch { download(apkUrl, versionName, versionCode, sha256) }
        return START_NOT_STICKY
    }

    private suspend fun download(apkUrl: String, versionName: String, versionCode: Long, sha256: String) {
        // W6-04/W6-09：下载到带版本号的 .part 临时文件，校验全过再原子改名到正式名
        val dir = getExternalFilesDir("updates") ?: filesDir
        val part = File(dir, "update-${versionName.ifBlank { "unknown" }}$PART_SUFFIX")
        val target = File(dir, LEGACY_FILE_NAME)
        try {
            part.parentFile?.mkdirs()
            part.delete()
            val sources = buildSources(apkUrl, sha256)
            var lastReason: String? = null
            var downloaded: FetchOk? = null
            for ((index, source) in sources.withIndex()) {
                if (cancelled.get()) { stopCancelled(); return }
                part.delete()
                lastPct = -1
                UpdateDownloadBus.publish(DownloadState.Running(versionName, 0, 0, 0, -1))
                postNotification(buildProgressNotification(versionName, 0, 0, 0, -1))
                val startedAt = SystemClock.elapsedRealtime()
                Log.i(TAG, "stage=source|index=${index + 1}/${sources.size}|host=${hostOf(source)}|result=begin")
                try {
                    downloaded = fetchSource(source, part, versionName)
                    Log.i(TAG, "stage=download|index=${index + 1}/${sources.size}|host=${hostOf(source)}" +
                            "|http=${downloaded.httpCode}|bytes=${downloaded.bytes}|total=${downloaded.total}" +
                            "|cost=${SystemClock.elapsedRealtime() - startedAt}ms|result=ok")
                    break
                } catch (e: Exception) {
                    if (cancelled.get()) { stopCancelled(); return }
                    val mapped = mapFailure(e)
                    lastReason = mapped.reason
                    Log.i(TAG, "stage=download|index=${index + 1}/${sources.size}|host=${hostOf(source)}" +
                            "|http=-|bytes=${part.length()}|cost=${SystemClock.elapsedRealtime() - startedAt}ms" +
                            "|result=fail|retryable=${mapped.retryable}|reason=${mapped.reason}" +
                            "|ex=${e.javaClass.simpleName}")
                    part.delete()
                    if (!mapped.retryable) {
                        fail(versionName, mapped.reason)
                        return
                    }
                }
            }
            val pack = downloaded
            if (pack == null) {
                fail(versionName, lastReason ?: getString(R.string.update_dl_fail_generic))
                return
            }
            if (cancelled.get()) { stopCancelled(); return }
            if (part.length() <= 0L) {
                fail(versionName, getString(R.string.update_dl_fail_empty))
                return
            }
            // 阶段 2：校验（sha256 + 包名 + 签名），弹窗切到不确定进度
            UpdateDownloadBus.publish(DownloadState.Verifying(versionName))
            postNotification(buildVerifyingNotification(versionName))
            Log.i(TAG, "stage=verify|bytes=${part.length()}|sha256=${sha256.isNotEmpty()}|result=begin")
            // W6-01③：release asset 带 sha256 时做哈希校验；不带就退化为"只做长度校验"
            if (sha256.isNotEmpty() && !sha256.equals(sha256Hex(part), true)) {
                part.delete()
                fail(versionName, getString(R.string.update_dl_fail_checksum))
                return
            }
            // 全部校验过半，才落到系统安装器看得懂的正式名
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                fail(versionName, getString(R.string.update_dl_fail_rename))
                return
            }
            // W6-02：签名/包名校验。必须在真正调起安装器之前做，
            // 否则一旦走到系统安装向导，用户很可能顺着提示"卸载旧版再装"→ 课表数据全丢
            if (!samePackageAndSignature(target)) {
                target.delete()
                fail(versionName, getString(R.string.update_dl_fail_signature))
                return
            }
            Log.i(TAG, "stage=verify|signature=ok|result=ok")
            // W6-13③：改名成功 + 全部校验通过，落待装记录（SplashActivity 冷启动补提示）
            PendingUpdateStore.put(this, versionName, versionCode, target.absolutePath, sha256)
            UpdateDownloadBus.publish(DownloadState.Ready(versionName, target.absolutePath))
            Log.i(TAG, "stage=ready|apk=${target.absolutePath}")
            install(target, versionName)
        } catch (e: Exception) {
            if (cancelled.get()) { stopCancelled(); return }
            fail(versionName, mapFailure(e).reason)
        } finally {
            running.set(false)
            // W6-09②：任何失败/取消路径都不留半截包（成功改名后 part 已不存在，这里天然 no-op）
            if (part.exists()) part.delete()
        }
    }

    /**
     * 下载源候选（E）：
     *  - 只用 release 提供的直链；**仅当 sha256 非空**时才追加两个镜像，
     *    避免"无法校验却从镜像下载"的情况出现；
     *  - 镜像只改前缀，不改变校验：下载完仍走 Content-Type / 长度 / sha256 / 签名四道关。
     */
    private fun buildSources(apkUrl: String, sha256: String): List<String> {
        if (sha256.isEmpty()) return listOf(apkUrl)
        return listOf(apkUrl, MIRROR_PREFIX_1 + apkUrl, MIRROR_PREFIX_2 + apkUrl)
    }

    /** 单源下载；成功返回字节数，失败抛异常交由 [mapFailure] 归因 */
    private fun fetchSource(url: String, part: File, versionName: String): FetchOk {
        val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                // 读取阶段连续 12 秒没有任何新字节才判超时：足够容忍网络抖动，
                // 又能让用户在 15 秒内看到「连接 GitHub 超时」（而不是无限转圈）
                .readTimeout(12, TimeUnit.SECONDS)
                .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (cancelled.get()) throw CancelledException()
            val code = resp.code
            if (!resp.isSuccessful) throw HttpStatusException(code)
            // W6-01①：三方镜像缓存未命中时常返回 HTTP 200 的 HTML/JSON 错误页，直接拦掉
            val contentType = resp.header("Content-Type").orEmpty()
            if (contentType.contains("text/html", true) || contentType.contains("application/json", true)) {
                throw ContentTypeException()
            }
            val body = resp.body ?: throw EmptyBodyException()
            val total = body.contentLength()
            var downloaded = 0L
            val tracker = SlidingSpeedTracker()
            var lastPublishAt = 0L
            var lastPublishedPct = Int.MIN_VALUE
            part.delete()
            part.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(8192)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        if (cancelled.get()) throw CancelledException()
                        out.write(buf, 0, read)
                        downloaded += read
                        val now = SystemClock.elapsedRealtime()
                        val pct = if (total > 0) ((downloaded * 100) / total).toInt() else -1
                        // 进度节流：≥200ms 或进度变化 ≥1% 才刷新一次通知/弹窗
                        if (now - lastPublishAt >= PROGRESS_THROTTLE_MS || pct != lastPublishedPct) {
                            val speed = tracker.speedAt(now, downloaded)
                            lastPublishAt = now
                            lastPublishedPct = pct
                            lastPct = pct
                            UpdateDownloadBus.publish(
                                    DownloadState.Running(versionName, downloaded, total, speed, pct))
                            postNotification(buildProgressNotification(
                                    versionName, downloaded, total, speed, pct))
                        }
                    }
                }
            }
            if (cancelled.get()) throw CancelledException()
            // W6-01②：长度对不上 = 截断响应（连接中断但状态码早已是 200）
            if (total > 0 && downloaded != total) throw LengthMismatchException(downloaded, total)
            if (part.length() <= 0L) throw EmptyBodyException()
            return FetchOk(downloaded, total, code)
        }
    }

    /**
     * W6-02：待装 APK 与当前已安装应用是否同包名 + 同签名。
     * 解析不出来（不是合法 APK）一律判失败 —— 宁可不安，也不要在系统安装向导里被诱导卸载重装。
     */
    private fun samePackageAndSignature(apk: File): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            @Suppress("DEPRECATION")
            val archive = packageManager.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
            if (archive.packageName != packageName) return false
            @Suppress("DEPRECATION")
            val current = packageManager.getPackageInfo(packageName, flags)
            val a = signatureHashes(archive)
            val b = signatureHashes(current)
            a.isNotEmpty() && a == b
        } catch (e: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun signatureHashes(info: android.content.pm.PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return sigs?.map { sha256Hex(it.toByteArray()) }?.toSet() ?: emptySet()
    }

    /**
     * 下载完成：只发「点此安装」通知（W6-12）。
     * Service 内不再 startActivity —— 后台启动 Activity 会被系统静默拦截（BAL），
     * 真正可靠的安装入口是通知点击（用户手势）。弹窗里的「立即安装」由 Activity 发起。
     */
    private fun install(file: File, versionName: String) {
        val pi = PendingIntent.getActivity(this, PI_INSTALL, buildInstallIntent(this, file),
                pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT))
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildReadyNotification(versionName, pi))
        Log.i(TAG, "stage=install-ready|notify=ok")
        // W6-12 / W6-13①：DETACH 让「更新已下载完成 / 点此安装」继续留在通知栏，
        // 否则 stopSelf 会把它一起带走，用户反而失去了唯一的安装入口
        detachForeground()
        stopSelf(latestStartId)
    }

    private fun fail(versionName: String, msg: String) {
        // 先放开单飞门锁：允许用户在失败弹窗里立刻点「重试」，不被旧任务挡住
        running.set(false)
        val safeName = versionName.ifBlank { currentVersionName }
        UpdateDownloadBus.publish(DownloadState.Failed(safeName, msg))
        Log.i(TAG, "stage=failed|reason=$msg")
        // W6-11：文案写着「点击重试」，contentIntent 就必须真的能重试（复用本次入参）
        val retry = Intent(this, UpdateDownloadService::class.java).apply {
            action = ACTION_RETRY
            putExtra(EXTRA_APK_URL, currentApkUrl)
            putExtra(EXTRA_VERSION_NAME, currentVersionName)
            putExtra(EXTRA_VERSION_CODE, currentVersionCode)
            putExtra(EXTRA_SHA256, currentSha256)
        }
        val pi = PendingIntent.getService(this, PI_RETRY, retry,
                pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT))
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.update_dl_notif_failed_title))
                .setContentText(getString(R.string.update_dl_notif_failed_text, msg))
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build())
        // W6-13①：DETACH —— 失败通知必须留在通知栏，否则用户连"重试"入口都没有
        detachForeground()
        stopSelf(latestStartId)
    }

    private fun stopCancelled() {
        UpdateDownloadBus.publish(DownloadState.Idle)
        removeForeground()
        stopSelf(latestStartId)
    }

    private fun postNotification(notification: Notification) {
        lastNotification = notification
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notification)
    }

    private fun buildProgressNotification(versionName: String, bytes: Long, total: Long,
                                          speed: Long, pct: Int): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.update_dl_notif_downloading, versionName))
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.update_dl_notif_cancel), cancelPi())
        if (pct < 0) {
            builder.setProgress(0, 0, true)
                    .setContentText(getString(R.string.update_dl_notif_preparing))
        } else {
            builder.setProgress(100, pct, false)
            val text = if (total > 0) {
                getString(R.string.update_dl_notif_progress, pct,
                        sizeText(bytes), sizeText(total), sizeText(speed))
            } else {
                getString(R.string.update_dl_notif_progress_no_total, sizeText(bytes), sizeText(speed))
            }
            builder.setContentText(text)
        }
        return builder.build()
    }

    private fun buildVerifyingNotification(versionName: String): Notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(getString(R.string.update_dl_notif_downloading, versionName))
                    .setContentText(getString(R.string.update_dl_notif_verifying))
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setProgress(0, 0, true)
                    .build()

    private fun buildReadyNotification(versionName: String, pi: PendingIntent): Notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(getString(R.string.update_dl_notif_ready_title))
                    .setContentText(getString(R.string.update_dl_notif_ready_text, versionName))
                    .setStyle(NotificationCompat.BigTextStyle()
                            .bigText(getString(R.string.update_dl_notif_ready_big, versionName)))
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()

    private fun cancelPi(): PendingIntent {
        val cancelIntent = Intent(this, UpdateDownloadService::class.java).apply { action = ACTION_CANCEL }
        return PendingIntent.getService(this, 1, cancelIntent, pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT))
    }

    /** 一次性告知用通知（"已在下载中"/"需要先允许安装应用"），带可选点击跳转 */
    private fun buildMessageNotification(title: String, text: String,
                                         contentIntent: PendingIntent?): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setAutoCancel(true)
        if (contentIntent != null) builder.setContentIntent(contentIntent)
        return builder.build()
    }

    /** 下载内容的 sha256（小写十六进制），用于 W6-01 的哈希校验 */
    private fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(8192)
            var read: Int
            while (ins.read(buf).also { read = it } != -1) md.update(buf, 0, read)
        }
        return md.digest().toHex()
    }

    /** 字节串的 sha256（小写十六进制），用于 W6-02 的签名证书比对 */
    private fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String =
            joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(NotificationChannel(
                        CHANNEL_ID, getString(R.string.update_dl_notif_channel),
                        NotificationManager.IMPORTANCE_LOW))
            }
        }
    }

    private fun pendingFlags(extra: Int): Int {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return extra or base
    }

    /**
     * W6-13①：停止前台并**保留**通知（「更新已下载完成」与「更新下载失败」都靠它继续可点）。
     */
    private fun detachForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION") stopForeground(false)
        }
    }

    /** W6-13①：停止前台并**移除**通知（用户主动取消，不该留残影） */
    private fun removeForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
    }

    // ---------------- 失败归因（必须输出中文，不把 e.message 甩给用户） ----------------

    private data class MappedFailure(val reason: String, val retryable: Boolean)

    private fun mapFailure(e: Exception): MappedFailure {
        val msg = e.message.orEmpty()
        return when {
            e is HttpStatusException -> {
                val reason = getString(R.string.update_dl_fail_http, e.code)
                MappedFailure(reason, e.code >= 500)
            }
            e is LengthMismatchException ->
                MappedFailure(getString(R.string.update_dl_fail_interrupted,
                        sizeText(e.downloaded), sizeText(e.total)), true)
            e is ContentTypeException ->
                MappedFailure(getString(R.string.update_dl_fail_not_apk), true)
            e is EmptyBodyException ->
                MappedFailure(getString(R.string.update_dl_fail_empty), true)
            e is CancelledException ->
                MappedFailure(getString(R.string.update_dl_fail_generic), false)
            // ENOSPC：磁盘写满，换镜像也没用，直接判死
            msg.contains("ENOSPC") || msg.contains("No space left", true) ->
                MappedFailure(getString(R.string.update_dl_fail_no_space), false)
            e is SSLException || msg.contains("SSL", true) || msg.contains("certificate", true) ->
                MappedFailure(getString(R.string.update_dl_fail_ssl), true)
            e is UnknownHostException || msg.contains("Unable to resolve host", true) ->
                MappedFailure(getString(R.string.update_dl_fail_github), true)
            e is SocketTimeoutException || msg.contains("timeout", true) ->
                MappedFailure(getString(R.string.update_dl_fail_timeout), true)
            e is ConnectException || e is NoRouteToHostException ||
                    msg.contains("failed to connect", true) ->
                MappedFailure(getString(R.string.update_dl_fail_github), true)
            else -> MappedFailure(getString(R.string.update_dl_fail_generic), false)
        }
    }

    private fun sizeText(bytes: Long): String = when {
        bytes <= 0L -> "0 B"
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun hostOf(url: String): String =
            runCatching { java.net.URL(url).host }.getOrNull() ?: "-"

    /** 最近 2 秒滑动窗口字节差 → 速度（字节/秒） */
    private class SlidingSpeedTracker {
        private val times = ArrayDeque<Long>()
        private val bytes = ArrayDeque<Long>()

        fun speedAt(now: Long, totalBytes: Long): Long {
            while (times.isNotEmpty() && now - times.first() > SPEED_WINDOW_MS) {
                times.removeFirst()
                bytes.removeFirst()
            }
            times.addLast(now)
            bytes.addLast(totalBytes)
            if (times.size < 2) return 0L
            val elapsed = (times.last() - times.first()).coerceAtLeast(1L)
            return ((bytes.last() - bytes.first()) * 1000L) / elapsed
        }
    }

    private data class FetchOk(val bytes: Long, val total: Long, val httpCode: Int)

    private class HttpStatusException(val code: Int) : Exception("HTTP $code")
    private class LengthMismatchException(val downloaded: Long, val total: Long) : Exception("length")
    private class ContentTypeException : Exception("content-type")
    private class EmptyBodyException : Exception("empty-body")
    private class CancelledException : Exception("cancelled")

    companion object {
        private const val TAG = "UpdateFlow"

        const val EXTRA_APK_URL = "apk_url"
        const val EXTRA_VERSION_NAME = "version_name"
        const val EXTRA_VERSION_CODE = "version_code"
        const val EXTRA_SHA256 = "sha256"
        const val ACTION_CANCEL = "cancel_update"

        /**
         * W6-11：失败通知「点击重试」用的 action。
         * 处理路径与普通启动完全一致（同一套 extras 走同一条下载流程，无需单开分支）。
         */
        const val ACTION_RETRY = "retry_update"

        const val NOTIF_ID = 9090
        const val CHANNEL_ID = "app_update"

        /**
         * 最近一次下载请求。进度弹窗从关于页常驻行打开时没有 UpdateInfo，
         * 「重试」「浏览器下载」靠它复用入参，否则点了会没反应。
         */
        data class LastRequest(val apkUrl: String, val versionName: String,
                               val versionCode: Long, val sha256: String)

        @Volatile
        var lastRequest: LastRequest? = null
            private set

        private const val LEGACY_FILE_NAME = "XiaoTang-Tangle-update.apk"
        private const val PART_SUFFIX = ".apk.part"
        private const val PI_REQUEST_SETTINGS = 3

        /** getActivity 命名空间的槽位（与 PI_REQUEST_SETTINGS 同空间，故取 4 与 3 错开） */
        private const val PI_INSTALL = 4

        /** getService 命名空间的槽位（与取消下载用的 1 错开） */
        private const val PI_RETRY = 5

        /** 进度节流：≥200ms 才刷新一次（另有 ≥1% 变化也刷新的条件） */
        private const val PROGRESS_THROTTLE_MS = 200L

        /** 速度统计窗口 */
        private const val SPEED_WINDOW_MS = 2000L

        /** E：只在 sha256 非空时启用的下载镜像（只改前缀，不改变被代理的目标地址） */
        private const val MIRROR_PREFIX_1 = "https://ghproxy.net/"
        private const val MIRROR_PREFIX_2 = "https://gh-proxy.com/"

        /**
         * W6-03：是否已获得「安装未知应用」权限。
         * API 26 以下不存在这个开关（那时装 APK 只看"未知来源"全局设置），故恒为 true。
         */
        fun canRequestInstall(context: Context): Boolean =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                        context.packageManager.canRequestPackageInstalls()

        fun start(context: Context, apkUrl: String, versionName: String, versionCode: Long,
                  sha256: String = "") {
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                putExtra(EXTRA_APK_URL, apkUrl)
                putExtra(EXTRA_VERSION_NAME, versionName)
                putExtra(EXTRA_VERSION_CODE, versionCode)
                putExtra(EXTRA_SHA256, sha256)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** 用户主动取消（进度弹窗 / 二次确认后调用） */
        fun cancel(context: Context) {
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                action = ACTION_CANCEL
            }
            runCatching { context.startService(intent) }
        }

        /**
         * W6-13③：构造系统安装器 Intent（FileProvider + ACTION_INSTALL_PACKAGE）。
         * 弹窗「立即安装」（用户手势）、通知「点此安装」与 SplashActivity 冷启动补提示共用。
         */
        fun buildInstallIntent(context: Context, file: File): Intent {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            return Intent(Intent.ACTION_INSTALL_PACKAGE, uri).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        /**
         * 直接调起系统安装器（前台用户手势场景：点通知 / 弹窗按钮 / 冷启动对话框）。
         * 返回 null = 已成功调起；返回非空提示文案 = 需要引导（未授权/无安装器），调用方自行展示。
         * 内部已处理「安装未知应用」授权引导；任何异常都不外抛，绝不杀调用方。
         */
        fun launchInstaller(context: Context, file: File): String? {
            if (!canRequestInstall(context)) {
                runCatching {
                    context.startActivity(
                            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                return "需要先允许「安装未知应用」"
            }
            return try {
                context.startActivity(buildInstallIntent(context, file))
                null
            } catch (e: ActivityNotFoundException) {
                // W6-10：这是"机器上没有安装器"，不要错误引导去未知来源设置
                "未找到安装程序，请检查系统是否禁用了安装器"
            } catch (e: SecurityException) {
                runCatching {
                    context.startActivity(
                            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                "需要先允许「安装未知应用」"
            } catch (e: Exception) {
                "暂时无法调起安装器，可稍后从通知栏点击安装"
            }
        }
    }
}
