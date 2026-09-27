package com.Tangle.timetable

import android.annotation.TargetApi
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.Tangle.timetable.schedule_settings.ScheduleSettingsActivity
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.ThemeManager
import com.Tangle.timetable.utils.getPrefer
import com.Tangle.timetable.update.UpdateManager
import com.Tangle.timetable.widget.WidgetScheduler
import com.Tangle.timetable.widget.WidgetUpdateReceiver
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class App : Application() {

    var activityCount = 0

    /** D1：应用级后台协程作用域（替代裸 Thread，统一调度） */
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** D1：scheduleAll 防重入（Application.onCreate 正常只调一次，防御性兜底） */
    private val scheduleStarted = AtomicBoolean(false)

    /** D2：亮屏刷新防重入（连发亮屏广播只放一个刷新任务进后台） */
    private val screenRefreshing = AtomicBoolean(false)

    /** D2：亮屏 receiver 提为成员，支持反注册/重注册，避免系统长期持有泄漏 */
    @Volatile
    private var screenReceiver: android.content.BroadcastReceiver? = null
    private val screenReceiverRegistered = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        // 最先安装崩溃日志落盘，方便定位「打开即崩」这类问题
        try {
            com.Tangle.timetable.utils.CrashLogger.install(this)
        } catch (e: Throwable) {
        }
        Toasty.Config.getInstance()
                .setToastTypeface(Typeface.DEFAULT_BOLD)
                .setTextSize(12)
                .apply()
        // 初始化主题系统（同步主色调到旧配置项）
        ThemeManager.init(this)
        // 注册小部件更新调度（精确闹钟 + WorkManager 兜底）
        // scheduleAll 含 Room 首次建库/迁移与最多近百次 PendingIntent 注册，
        // 移到 appScope(IO) 后台执行，并用 AtomicBoolean 防重入，避免冷启动卡顿与重复初始化
        if (scheduleStarted.compareAndSet(false, true)) {
            appScope.launch {
                try {
                    WidgetScheduler.scheduleAll(this@App)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        // 亮屏广播（无法在 Manifest 注册，代码动态注册以增加刷新机会）
        // 刷新内部为同步 DB 查询：goAsync + appScope(IO)，AtomicBoolean 防连发重入
        registerScreenReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var channelId = "schedule_reminder"
            var channelName = "课程提醒"
            var importance = NotificationManager.IMPORTANCE_HIGH
            createNotificationChannel(this, channelId, channelName, importance)
            channelId = "news"
            channelName = "公告"
            importance = NotificationManager.IMPORTANCE_LOW
            createNotificationChannel(this, channelId, channelName, importance)
        }
        when (getPrefer().getInt(Const.KEY_DAY_NIGHT_THEME, 2)) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            2 -> {
                when {
                    Build.VERSION.SDK_INT >= 29 -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    }
                    Build.VERSION.SDK_INT >= 23 -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
                    }
                    else -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    }
                }
            }
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityPaused(activity: Activity) {
            }

            override fun onActivityResumed(activity: Activity) {
                // D2：回到前台恢复亮屏刷新（退后台时可能已反注册；内部有防重复注册守卫）
                registerScreenReceiver()
            }

            override fun onActivityStarted(activity: Activity) {
                activityCount++
            }

            override fun onActivityDestroyed(activity: Activity) {
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            }

            override fun onActivityStopped(activity: Activity) {
                activityCount--
                if (activity is ScheduleSettingsActivity && activityCount == 0) {
                    Toasty.info(applicationContext, "对小部件的编辑需要按「返回键」退出设置页面才能生效哦", Toast.LENGTH_LONG).show()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            }

        })
        // 内置自动更新：启动后后台静默检查，首个前台 Activity 弹窗（不拖慢启动）
        UpdateManager.onAppCreate(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == TRIM_MEMORY_UI_HIDDEN) {
            // D2：应用退到后台，反注册亮屏广播，避免系统长期持有 receiver 造成泄漏
            unregisterScreenReceiver()
        }
        // W8-12：原来只在 `level >= TRIM_MEMORY_COMPLETE`（=80，最极端的一档）才释放 OCR 引擎，
        // 而这一档在 Android 14 上**几乎不再投递** → 等于这段清理逻辑失效，
        // OCR engine 常驻的几十 MB native 内存一直不还。
        // 改为同时接受 TRIM_MEMORY_BACKGROUND（=40，应用进入 LRU 后台列表，是现代系统最常给的一档）
        // 与 TRIM_MEMORY_UI_HIDDEN。
        // 顺带把裸 `Thread{}` 换成 appScope：手写线程没有归属，也无法被统一取消。
        // TessOcrUtils.release() 是幂等且线程安全的（@Synchronized）。
        if (level >= TRIM_MEMORY_BACKGROUND || level == TRIM_MEMORY_UI_HIDDEN ||
                level >= TRIM_MEMORY_COMPLETE) {
            appScope.launch {
                runCatching { com.Tangle.timetable.utils.TessOcrUtils.release() }
            }
        }
    }

    /** D2：注册亮屏 receiver（幂等：已注册时直接返回） */
    private fun registerScreenReceiver() {
        if (screenReceiverRegistered.get()) return
        try {
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                    val result = goAsync()
                    if (!screenRefreshing.compareAndSet(false, true)) {
                        // 上一次亮屏刷新还在跑：直接收尾，避免任务堆积
                        result.finish()
                        return
                    }
                    appScope.launch {
                        try {
                            WidgetUpdateReceiver.refreshAllWidgets(this@App)
                            // W3-13②：解锁（ACTION_USER_PRESENT）比单纯亮屏更强地意味着
                            // "用户真的回来了"，此时顺带重排一次闹钟；SCREEN_ON 仍只刷新数据。
                            // scheduleAll 是幂等的，且 WidgetUpdateReceiver 侧已有 60 秒节流。
                            if (i?.action == android.content.Intent.ACTION_USER_PRESENT) {
                                WidgetScheduler.scheduleAll(this@App)
                            }
                        } catch (t: Throwable) {
                            com.Tangle.timetable.utils.CrashLogger.logCaught("screen_on", t)
                        } finally {
                            screenRefreshing.set(false)
                            result.finish()
                        }
                    }
                }
            }
            // W2-03：显式声明导出标志。全工程唯一的动态广播注册，原来不带 RECEIVER_* 标志。
            // 当前不崩溃（只注册 ACTION_SCREEN_ON 这一个系统广播，落在 Android 14 官方豁免内），
            // 但豁免是**隐式条件**而不是显式声明：一旦有人往同一个 IntentFilter 追加任何非系统
            // action，register 时立刻抛 SecurityException
            // （"One of RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED should be specified ..."）。
            // 用 NOT_EXPORTED：ACTION_SCREEN_ON 由 system_server 发出，NOT_EXPORTED 仍能收到；
            // 同时不把接收器暴露给任意应用（RECEIVER_EXPORTED 恰好会，故不用它）。
            // 走 ContextCompat 是为了在 API < 33 上自动回落成不带标志的旧注册方式。
            // W3-13②：三处对"亮屏"的认知原本没对齐：
            //   Manifest 里没有（系统不给隐式注册）、这里动态注册了 SCREEN_ON、
            //   而 WidgetUpdateReceiver 的 when 里还留着一个永远进不去的 SCREEN_ON 分支。
            // 现在：① 那个死分支已删（见 WidgetUpdateReceiver）；
            //       ② 这里补上 ACTION_USER_PRESENT（解锁后真正"用户回来了"的信号，
            //          比 SCREEN_ON 更贴近"用户在看手机"，锁屏亮屏时不会白刷）；
            //       ③ onReceive 里对 USER_PRESENT 额外 scheduleAll 一次（重排闹钟），
            //          SCREEN_ON 仍只刷新数据。
            ContextCompat.registerReceiver(this, receiver,
                    android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_ON).apply {
                        addAction(android.content.Intent.ACTION_USER_PRESENT)
                    },
                    ContextCompat.RECEIVER_NOT_EXPORTED)
            screenReceiver = receiver
            screenReceiverRegistered.set(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** D2：反注册亮屏 receiver（幂等：未注册时直接返回） */
    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered.getAndSet(false)) return
        val receiver = screenReceiver ?: return
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        screenReceiver = null
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(context: Context, channelId: String, channelName: String, importance: Int) {
        val channel = NotificationChannel(channelId, channelName, importance)
        channel.setShowBadge(true)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

}