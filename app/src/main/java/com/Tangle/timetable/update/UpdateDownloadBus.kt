package com.Tangle.timetable.update

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 下载链路的唯一状态源（A）。
 *
 * 历史问题：整条下载链路只有通知栏一种反馈，而 App 从未在下载入口申请 POST_NOTIFICATIONS，
 * Android 13+ 默认拒绝 → startForeground 照常执行但通知不可见，用户点「立即更新」后
 * 看不到任何东西（下载其实在跑）。
 *
 * 现在 Service 只往这里 publish 阶段，UI（进度弹窗 / 关于页常驻行）订阅同一份状态：
 *  - 进程被杀 → object 重新初始化 → 天然回到 Idle，不会残留「正在下载」；
 *  - Activity 退出只 remove 监听，状态留在总线上，重进可恢复。
 */
sealed class DownloadState {
    /** 空闲：进程刚启动、用户取消、或服务被清理 */
    object Idle : DownloadState()

    /**
     * 下载中。
     * @param total 总字节数；<= 0 表示服务端没给 Content-Length
     * @param pct   0..100；total <= 0 时为 -1（界面显示不确定进度）
     * @param speedBps 最近 2 秒滑动窗口的平均速度（字节/秒），0 表示还没算出来
     */
    data class Running(
        val versionName: String,
        val bytes: Long,
        val total: Long,
        val speedBps: Long,
        val pct: Int
    ) : DownloadState()

    /** 下载完成，正在算 sha256 / 验签 */
    data class Verifying(val versionName: String) : DownloadState()

    /** 全部校验通过，可以安装 */
    data class Ready(val versionName: String, val apkPath: String) : DownloadState()

    /** 失败（reason 必须是已经映射好的中文文案，不允许把 e.message 直接抛给用户） */
    data class Failed(val versionName: String, val reason: String) : DownloadState()
}

/**
 * 极简状态总线：@Volatile 当前状态 + CopyOnWriteArrayList 监听器 + 主线程 Handler 分发。
 * 不引入 lifecycle-runtime-ktx，也不新增任何依赖。
 *
 * 约定：add / remove 必须成对；Activity 销毁时必须 removeListener，避免持有已销毁的宿主。
 */
object UpdateDownloadBus {

    fun interface Listener {
        fun onState(state: DownloadState)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<Listener>()

    @Volatile
    private var state: DownloadState = DownloadState.Idle

    fun current(): DownloadState = state

    /** 注册监听并立即回放一次当前状态（新打开的界面无需等到下一次 publish 才有内容） */
    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
        val snapshot = state
        mainHandler.post { listener.onState(snapshot) }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /** Service 侧调用：状态立刻写入，分发统一 post 到主线程 */
    fun publish(newState: DownloadState) {
        state = newState
        mainHandler.post {
            for (listener in listeners) {
                runCatching { listener.onState(newState) }
            }
        }
    }
}
