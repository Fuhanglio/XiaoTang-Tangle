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
            val last = prefs.getLong(Const.KEY_UPDATE_LAST_AUTO_CHECK, 0L)
            // 6 小时内只自动查一次，省流量并规避 GitHub API 限流
            if (System.currentTimeMillis() - last > 6 * 3600 * 1000L) {
                prefs.edit().putLong(Const.KEY_UPDATE_LAST_AUTO_CHECK, System.currentTimeMillis()).apply()
                runCheck(app, manual = false)
            }
        }
    }

    /** 关于页「检查更新」手动入口 */
    fun checkManual(context: Context) {
        scope.launch { runCheck(context, manual = true) }
    }

    private suspend fun runCheck(context: Context, manual: Boolean) {
        val state = UpdateChecker.check(context)
        mainHandler.post {
            when (state) {
                is UpdateState.Available -> {
                    if (manual) {
                        UpdateDialog.showUpdate(context, state.info)
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
    }
}
