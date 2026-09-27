package com.Tangle.timetable.update

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.Tangle.timetable.SplashActivity
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.getPrefer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object UpdateManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingUpdate: UpdateInfo? = null
    private var dialogShown = false
    private var currentActivity: AppCompatActivity? = null

    /** 自动检查的最小间隔（6 小时）：省流量并规避 GitHub API 的匿名限流 */
    private const val UPDATE_AUTO_CHECK_INTERVAL_MS = 6 * 3600 * 1000L

    /** 在 App.onCreate 中调用：注册生命周期回调 + 后台静默检查（不拖慢启动） */
    fun onAppCreate(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is AppCompatActivity) currentActivity = activity
                if (!dialogShown && pendingUpdate != null && activity is AppCompatActivity
                    && activity !is SplashActivity && !activity.isFinishing) {
                    dialogShown = true
                    UpdateDialog.showUpdate(activity, pendingUpdate!!)
                }
            }
            override fun onActivityPaused(activity: Activity) {
                if (activity === currentActivity) currentActivity = null
            }
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityDestroyed(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: android.os.Bundle) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivityCreated(a: Activity, b: android.os.Bundle?) {}
        })
        scope.launch {
            val prefs = app.getPrefer()
            val lastOk = prefs.getLong(Const.KEY_UPDATE_LAST_AUTO_CHECK, 0L)
            val now = System.currentTimeMillis()
            // W6-06：原实现有两个坑 ——
            //   ① 用 System.currentTimeMillis() 的**绝对**时间戳做限流：用户手动回拨系统时间
            //      （或某些国产 ROM 的「自动校时」跳变）会让 now - last 变负，
            //      于是自动检查被**静默关闭很久**；
            //   ② 时间戳在**检查开始之前**就写盘，断网导致的失败也算"这 6 小时查过了"
            //      → 一次失败之后 6 小时内不再自动重试。
            // 现在：时间戳改为**检查成功拿到确定性结果之后才写**；负差值单独保护
            //（视为"时间被回拨"，照常检查，而不是误判成"刚查过"）。
            val delta = now - lastOk
            val shouldCheck = lastOk == 0L || delta < 0 || delta > UPDATE_AUTO_CHECK_INTERVAL_MS
            if (shouldCheck && runCheck(app, manual = false)) {
                prefs.edit().putLong(Const.KEY_UPDATE_LAST_AUTO_CHECK, System.currentTimeMillis()).apply()
            }
        }
    }

    /** 关于页「检查更新」手动入口 */
    fun checkManual(context: Context) {
        scope.launch { runCheck(context, manual = true) }
    }

    /**
     * 执行一次检查，并把结果反映到 UI。
     *
     * @return 是否拿到**确定性结果**（Latest / Available）。
     *   只有这种结果才值得写入「上次检查时间」（W6-06）：NoNetwork / Error 属可恢复失败，
     *   不写时间戳，下次冷启动还会再试，而不是被 6 小时限流关在门外。
     */
    private suspend fun runCheck(context: Context, manual: Boolean): Boolean {
        val state = UpdateChecker.check(context)
        mainHandler.post {
            when (state) {
                is UpdateState.Available -> {
                    // W6-07：这个版本是否被用户点过「跳过此版本」
                    val skipped = context.getPrefer()
                            .getString(Const.KEY_UPDATE_SKIP_VERSION, "") ?: ""
                    val isSkipped = skipped.isNotEmpty() && skipped == state.info.versionName
                    if (manual) {
                        // 手动检查：照常弹窗（标题与中性按钮换成"已跳过"口径），
                        // 不再把"我主动跳过了"伪装成"已是最新版本"。
                        UpdateDialog.showUpdate(context, state.info, alreadySkipped = isSkipped)
                    } else if (isSkipped) {
                        // 自动检查：用户明确跳过过的版本不再主动打扰（静默）
                    } else {
                        val cur = currentActivity
                        if (cur != null && cur !is SplashActivity && !cur.isFinishing) {
                            dialogShown = true
                            UpdateDialog.showUpdate(cur, state.info)
                        } else {
                            pendingUpdate = state.info
                        }
                    }
                }
                is UpdateState.Latest -> if (manual) UpdateDialog.toastLatest(context)
                is UpdateState.NoNetwork -> if (manual) UpdateDialog.toastNoNetwork(context)
                is UpdateState.Error -> if (manual) UpdateDialog.toastError(context, state.message)
            }
        }
        return state is UpdateState.Available || state is UpdateState.Latest
    }
}
