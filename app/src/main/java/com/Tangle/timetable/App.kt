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
        // 内存吃紧时释放 OCR 引擎（约几十 MB native 内存；下次识别会自动重新初始化）
        if (level >= TRIM_MEMORY_COMPLETE) {
            Thread { com.Tangle.timetable.utils.TessOcrUtils.release() }.start()
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
                        } catch (t: Throwable) {
                            com.Tangle.timetable.utils.CrashLogger.logCaught("screen_on", t)
                        } finally {
                            screenRefreshing.set(false)
                            result.finish()
                        }
                    }
                }
            }
            registerReceiver(receiver, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_ON))
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