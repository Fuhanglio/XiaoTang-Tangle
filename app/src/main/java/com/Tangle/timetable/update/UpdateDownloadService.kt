package com.Tangle.timetable.update

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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 前台 Service：下载 APK 并通过 FileProvider 调起系统安装器。
 * 前台类型 dataSync（Android 14 要求），作用域存储下无需 WRITE_EXTERNAL_STORAGE。
 *
 * B5 在「下载 → 安装」链路上补了五道关，历史事故「下载成功却装不上」的根因都在这条链上：
 *  - W6-01 完整性：Content-Type / Content-Length / sha256 三重校验。三方镜像（ghproxy 等）
 *           缓存未命中时常返回 **HTTP 200 的 HTML 错误页**，或把响应截断，原来一律原样写进 .apk
 *  - W6-02 来源可信：装之前比对待装包与已装应用的**包名 + 签名**，不一致直接删包终止
 *           （否则系统安装向导会引导用户"卸载旧版再装"，卸载即清空 Room 数据库 → 课表全丢）
 *  - W6-03 权限前置：没开「安装未知应用」就引导去开，不再白下几十 MB
 *  - W6-04 并发单飞：同一时刻只允许一个下载任务，避免两个协程同时写同一个文件把 APK 写坏
 *  - W6-09 残留清理：下载到 .part 临时名，全部校验通过才改名；失败/取消路径一律删干净
 *
 * B18 在「调起安装器」与「服务收尾」两段补了四道关：
 *  - W6-10 错误语义：安装失败的 catch 不再把**任意异常**都当成"未授权安装未知来源"
 *  - W6-11 文案与行为一致：失败通知写「点击重试」，contentIntent 就真的能重试下载
 *  - W6-12 后台启动 Activity 限制：通知才是可靠的安装入口，Service 内 startActivity 只当尽力而为
 *  - W6-13 生命周期收尾：取消时移除前台通知、onDestroy/onTaskRemoved 取消协程并清 .part
 */
class UpdateDownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cancelled = AtomicBoolean(false)

    /** W6-04：下载单飞守卫。cancelled 只表达"取消"，不表达"已有任务在跑"，两者不能复用 */
    private val running = AtomicBoolean(false)

    // W6-11：把本次任务的入参记下来，供 fail() 构造"真的能重试"的 PendingIntent。
    // 用字段而不是给 fail() 加三个参数，是为了不动那十来处 fail(...) 调用点。
    private var currentApkUrl = ""
    private var currentVersionName = ""
    private var currentSha256 = ""

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

    /**
     * W6-13②：Service 被系统回收时必须把协程也收干净。
     *
     * 原来 `CoroutineScope(SupervisorJob())` **永不 cancel**，服务销毁后协程可能仍在写 .part 文件，
     * 写完再去 `getSystemService` / `nm.notify` 就有拿到已销毁 Context 的风险。
     * 这里一次性收三样：置取消标志（让下载循环下一轮就退出）、cancel 作用域、清掉 .part 残留。
     * 正式名 `XiaoTang-Tangle-update.apk` 仍刻意保留：它可能正被系统安装器引用。
     */
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

    /**
     * W6-13②：用户划掉最近任务时也立即收尾。
     *
     * 本 Service 是 START_NOT_STICKY 的下载任务，没有任何理由在"用户明确清掉了这个 App"之后偷偷续跑。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        cancelled.set(true)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelled.set(true)
            // W6-13①：原来只 stopSelf()，没有 stopForeground → 部分 ROM 上"正在下载"的前台通知
            // 会短暂残留。用户主动取消，进度通知必须立刻消失，所以用 REMOVE 而不是 DETACH。
            removeForeground()
            stopSelf()
            return START_NOT_STICKY
        }
        val apkUrl = intent?.getStringExtra(EXTRA_APK_URL)
        val versionName = intent?.getStringExtra(EXTRA_VERSION_NAME) ?: ""
        val versionCode = intent?.getLongExtra(EXTRA_VERSION_CODE, 0L) ?: 0L
        val sha256 = intent?.getStringExtra(EXTRA_SHA256) ?: ""
        // W6-11：记下本次入参 —— 失败通知里的「点击重试」要靠它把同一个任务重新拉起来。
        // （ACTION_RETRY 用的就是这套 extras，所以下面不需要为它单开分支）
        currentApkUrl = apkUrl.orEmpty()
        currentVersionName = versionName
        currentSha256 = sha256
        if (apkUrl.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        // W6-04：并发门锁。重复点「立即更新」时不再并存第二个任务
        if (!running.compareAndSet(false, true)) {
            // ⚠️ 提前返回的分支也必须先 startForeground：本 Service 由 startForegroundService 拉起，
            // 5 秒内不调用 startForeground 会被系统判 RemoteServiceException 直接崩
            startForeground(NOTIF_ID, buildMessageNotification("已在下载中", "更新包正在下载，请稍候", null))
            // 一次性告知用通知要留在通知栏（用户得看得见），语义 = DETACH
            detachForeground()
            stopSelf()
            return START_NOT_STICKY
        }
        // W6-03：先把「安装未知应用」权限问清楚，避免下完几十 MB 才在安装阶段失败
        if (!canRequestInstall(this)) {
            running.set(false)
            val pi = PendingIntent.getActivity(this, PI_REQUEST_SETTINGS,
                    Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT))
            startForeground(NOTIF_ID, buildMessageNotification("需要先允许安装应用",
                    "点此开启「安装未知应用」后再试更新", pi))
            // 一次性告知用通知要留在通知栏（用户得看得见），语义 = DETACH
            detachForeground()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification(0, true, versionName))
        scope.launch { download(apkUrl, versionName, versionCode, sha256) }
        return START_NOT_STICKY
    }

    private suspend fun download(apkUrl: String, versionName: String, versionCode: Long, sha256: String) {
        // W6-04/W6-09：下载到带版本号的 .part 临时文件，校验全过再原子改名到正式名。
        // 这样既不会有"半截包"被当成可安装的 APK，两个任务也不会互相覆盖（配合上面的单飞门锁）
        val dir = getExternalFilesDir("updates") ?: filesDir
        val part = File(dir, "update-${versionName.ifBlank { "unknown" }}$PART_SUFFIX")
        val target = File(dir, LEGACY_FILE_NAME)
        try {
            part.parentFile?.mkdirs()
            part.delete()
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            val resp = client.newCall(Request.Builder().url(apkUrl).build()).execute()
            if (cancelled.get()) { stopSelf(); return }
            if (!resp.isSuccessful) { fail(versionName, "HTTP ${resp.code}"); return }
            // W6-01①：先看 Content-Type。三方镜像命中失败时返回的 HTML 错误页/JSON 报错，
            // 状态码仍是 200，原样写进 .apk 必然装不上，这里直接拦掉
            val contentType = resp.header("Content-Type").orEmpty()
            if (contentType.contains("text/html", true) || contentType.contains("application/json", true)) {
                fail(versionName, "下载源返回的不是安装包")
                return
            }
            val body = resp.body ?: run { fail(versionName, "空响应"); return }
            val total = body.contentLength()
            var downloaded = 0L
            part.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(8192)
                    var read: Int
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
            // W6-01②：长度对不上 = 截断响应（连接中断但状态码早已是 200），原来这种情况会被当成成功
            if (total > 0 && downloaded != total) {
                fail(versionName, "下载不完整（$downloaded/$total 字节）")
                return
            }
            if (part.length() <= 0L) {
                fail(versionName, "下载内容为空")
                return
            }
            // W6-01③：release asset 带 sha256 时做哈希校验；不带就退化为"只做长度校验"
            if (sha256.isNotEmpty() && !sha256.equals(sha256Hex(part), true)) {
                fail(versionName, "更新包校验失败（sha256 不匹配）")
                return
            }
            // 全部校验过半，才落到系统安装器看得懂的正式名
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                fail(versionName, "更新包重命名失败")
                return
            }
            // W6-02：签名/包名校验。必须在真正调起安装器之前做，
            // 否则一旦走到系统安装向导，用户很可能顺着提示"卸载旧版再装"→ 课表数据全丢
            if (!samePackageAndSignature(target)) {
                target.delete()
                fail(versionName, "更新包签名校验失败，已终止")
                return
            }
            // W6-13③：改名成功 + 全部校验通过（与发「点此安装」通知同一处），落待装记录。
            // 安装靠用户点通知；用户当时没装/重启手机后，SplashActivity 冷启动会凭这条记录补提示。
            PendingUpdateStore.put(this, versionName, versionCode, target.absolutePath, sha256)
            install(target, versionName)
        } catch (e: Exception) {
            if (cancelled.get()) { stopSelf(); return }
            fail(versionName, e.message ?: "下载失败")
        } finally {
            running.set(false)
            // W6-09②：任何失败/取消路径都不留半截包（成功改名后 part 已不存在，这里天然 no-op）
            if (part.exists()) part.delete()
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

    private fun install(file: File, versionName: String) {
        val intent = buildInstallIntent(this, file)
        val pi = PendingIntent.getActivity(this, PI_INSTALL, intent,
                pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT))
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // W6-12：「下载完成自动调起安装器」在 Android 10(API 29)+ 上靠不住 ——
        //   从 Service 里 startActivity 属于**后台启动 Activity（BAL）**，用户离开 App / 锁屏时
        //   会被系统静默拦截（只打 log，**不抛异常**，所以下面连 catch 都进不去），
        //   CHANGELOG 承诺的"自动调起"在真机 ColorOS 16 上会落空。
        //   可靠路径只有一条：**用户点通知**。点通知属于"用户手势启动 Activity"，不受 BAL 限制。
        //   因此通知升级为主入口，下面的 startActivity 降级为"App 恰好在前台时"的尽力而为；
        //   文案也相应从"已自动调起"改成"点此安装"，不再承诺做不到的事。
        notifyInstallReady(nm, versionName, pi, null)
        // W6-13③：SplashActivity 冷启动补提示后点「安装」与这里走的是同一个入口
        val installError = launchInstaller(this, file)
        if (installError != null) {
            // W6-10：只有真被策略挡住（未授权安装未知来源）才引导设置；其余情况保留通知等用户点
            notifyInstallReady(nm, versionName, pi, installError)
        }
        // W6-12 / W6-13①：DETACH 让「更新已下载完成 / 点此安装」这条通知在服务结束后
        // **继续留在通知栏**；否则 stopSelf 会把它一起带走，用户反而失去了唯一的安装入口
        detachForeground()
        stopSelf()
    }

    /** 统一构造「可以点了安装」的通知；extraHint 非空时把原因一并写进正文 */
    private fun notifyInstallReady(nm: NotificationManager, versionName: String,
                                   pi: PendingIntent, extraHint: String?) {
        nm.notify(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("更新已下载完成")
            .setContentText(extraHint ?: "点此安装 v$versionName")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                    if (extraHint == null) "更新包已下载并校验通过，点此安装 v$versionName"
                    else "$extraHint（安装包已保留，可稍后再装）"))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build())
    }

    private fun fail(versionName: String, msg: String) {
        // W6-11：文案写着「点击重试」，contentIntent 就必须**真的**能重试。
        // 原实现指向 packageManager.getLaunchIntentForPackage(packageName)（= SplashActivity），
        // 点下去只是打开 App 首页 —— 既不重试下载，也不重查更新，承诺的行为不存在。
        // 现在直接回到本 Service 重跑**同一个任务**：extras 用的就是本次的 apkUrl / versionName / sha256，
        // onStartCommand 读到它们后会自然走一遍完整下载流程（无需为 ACTION_RETRY 单开分支）。
        val retry = Intent(this, UpdateDownloadService::class.java).apply {
            action = ACTION_RETRY
            putExtra(EXTRA_APK_URL, currentApkUrl)
            putExtra(EXTRA_VERSION_NAME, currentVersionName)
            putExtra(EXTRA_SHA256, currentSha256)
        }
        val pi = PendingIntent.getService(this, PI_RETRY, retry,
                pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT))
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("更新下载失败")
            .setContentText("$msg，点击重试")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build())
        // W6-13①：同样用 DETACH —— 失败通知必须留在通知栏，否则用户连"重试"这个入口都没有
        detachForeground()
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

    /** 一次性告知用通知（"已在下载中"/"需要先允许安装应用"），带可选点击跳转 */
    private fun buildMessageNotification(title: String, text: String, contentIntent: PendingIntent?): android.app.Notification {
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
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "应用更新", NotificationManager.IMPORTANCE_LOW))
            }
        }
    }

    private fun pendingFlags(extra: Int): Int {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return extra or base
    }

    /**
     * W6-13①：停止前台并**保留**通知（「更新已下载完成」与「更新下载失败」都靠它继续可点）。
     * 用 `STOP_FOREGROUND_DETACH` 而不是直接 `stopSelf()` —— 后者会把通知一起带走，
     * 用户就失去了安装 / 重试的入口。该常量需 API 24，低版本用等价的 `stopForeground(false)`。
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

    companion object {
        const val EXTRA_APK_URL = "apk_url"
        const val EXTRA_VERSION_NAME = "version_name"
        const val EXTRA_VERSION_CODE = "version_code"
        const val EXTRA_SHA256 = "sha256"
        const val ACTION_CANCEL = "cancel_update"

        /**
         * W6-11：失败通知「点击重试」用的 action。
         * 处理路径与普通启动**完全一致**（同一套 extras 走同一条下载流程，无需单开分支），
         * 单独给个 action 名只是为了日志/调试时能区分这次启动是用户点的重试。
         */
        const val ACTION_RETRY = "retry_update"

        const val NOTIF_ID = 9090
        const val CHANNEL_ID = "app_update"

        private const val LEGACY_FILE_NAME = "XiaoTang-Tangle-update.apk"
        private const val PART_SUFFIX = ".apk.part"
        private const val PI_REQUEST_SETTINGS = 3

        /** getActivity 命名空间的槽位（与 PI_REQUEST_SETTINGS 同空间，故取 4 与 3 错开） */
        private const val PI_INSTALL = 4

        /** getService 命名空间的槽位（与取消下载用的 1 错开） */
        private const val PI_RETRY = 5

        /**
         * W6-03：是否已获得「安装未知应用」权限。
         * API 26 以下不存在这个开关（那时装 APK 只看"未知来源"全局设置），故恒为 true。
         */
        fun canRequestInstall(context: Context): Boolean =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                        context.packageManager.canRequestPackageInstalls()

        fun start(context: Context, apkUrl: String, versionName: String, versionCode: Long, sha256: String = "") {
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                putExtra(EXTRA_APK_URL, apkUrl)
                putExtra(EXTRA_VERSION_NAME, versionName)
                putExtra(EXTRA_VERSION_CODE, versionCode)
                putExtra(EXTRA_SHA256, sha256)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * W6-13③：构造系统安装器 Intent（FileProvider + ACTION_INSTALL_PACKAGE）。
         * Service 的「点此安装」通知 PendingIntent 与 SplashActivity 冷启动补提示共用这一个入口。
         */
        fun buildInstallIntent(context: Context, file: File): Intent {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            return Intent(Intent.ACTION_INSTALL_PACKAGE, uri).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        /**
         * 直接调起系统安装器（前台用户手势场景：点通知 / 点冷启动对话框）。
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
