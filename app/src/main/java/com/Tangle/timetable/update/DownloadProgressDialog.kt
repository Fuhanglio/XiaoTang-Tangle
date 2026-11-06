package com.Tangle.timetable.update

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.Tangle.timetable.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.Locale

/**
 * 「下载进度」弹窗（D）：DownloadState 的四个阶段在这里完成全部呈现。
 *
 * 设计要点：
 *  - 订阅 [UpdateDownloadBus]，Activity 退出时只 remove 监听，下载本身不受影响；
 *  - 按钮放在 dialog_download.xml 里自己管理，不用 AlertDialog 的 positive/negative
 *    （Material 会把它们固定在右/左并重排，文案在「取消 / 立即安装 / 重试」间切换时顺序会乱）；
 *  - 「立即安装」走 Activity 的用户手势启动安装器，不受后台 BAL 限制；
 *  - 返回键在下载中必须二次确认「停止下载？」。
 */
object DownloadProgressDialog {

    /** 主色 / 失败色：失败时百分比与进度条一起转红，保留已下载比例 */
    private val colorFail = 0xFFE53935.toInt()

    private var dialog: androidx.appcompat.app.AlertDialog? = null
    private var confirmDialog: androidx.appcompat.app.AlertDialog? = null
    private var host: AppCompatActivity? = null
    private var views: Views? = null
    private var currentState: DownloadState = DownloadState.Idle
    private var lastRunning: DownloadState.Running? = null
    private var lastInfo: UpdateInfo? = null

    /** 监听器必须固定同一实例，add/remove 才能成对 */
    private val listener = UpdateDownloadBus.Listener { state -> render(state) }

    private class Views(
        val title: TextView,
        val notice: TextView,
        val percent: TextView,
        val progress: ProgressBar,
        val detail: TextView,
        val state: TextView,
        val primary: MaterialButton,
        val secondary: MaterialButton
    )

    /** 入口：点「立即更新」后立刻调用（1 秒内必须让用户看到进度） */
    fun show(activity: AppCompatActivity, info: UpdateInfo) {
        lastInfo = info
        showInternal(activity)
    }

    /**
     * 只按 Bus 状态弹窗（不依赖 UpdateInfo）。
     * 关于页常驻行/重进恢复走这里；retry 与浏览器下载所需的 apkUrl 仍来自 [lastInfo]。
     */
    private fun showInternal(activity: AppCompatActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (dialog != null && host === activity) {
            render(UpdateDownloadBus.current())
            return
        }
        if (dialog != null) dismissInternal()

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_download, null)
        val v = Views(
            view.findViewById(R.id.tv_dl_title),
            view.findViewById(R.id.tv_dl_notice),
            view.findViewById(R.id.tv_dl_percent),
            view.findViewById(R.id.pb_dl),
            view.findViewById(R.id.tv_dl_detail),
            view.findViewById(R.id.tv_dl_state),
            view.findViewById(R.id.btn_dl_primary),
            view.findViewById(R.id.btn_dl_secondary)
        )
        v.notice.setOnClickListener { openNotificationSettings(activity) }

        val d = MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setCancelable(false)
            .create()
        d.show()
        d.setCanceledOnTouchOutside(false)
        // 返回键自己接管：下载中必须二次确认
        d.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                onBackPressed()
                true
            } else {
                false
            }
        }
        dialog = d
        host = activity
        views = v

        val screenWidth = activity.resources.displayMetrics.widthPixels
        d.window?.setLayout((screenWidth * WIDTH_RATIO).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)

        UpdateDownloadBus.addListener(listener)
        render(UpdateDownloadBus.current())
    }

    /** 关于页重进时恢复：只在下载/校验中才自动弹，避免已完成/失败反复打扰 */
    fun restoreIfNeeded(activity: AppCompatActivity) {
        val state = UpdateDownloadBus.current()
        if (state !is DownloadState.Running && state !is DownloadState.Verifying) return
        if (dialog != null) return
        showInternal(activity)
    }

    /** 关于页常驻行 / 下载完成后的再次查看 */
    fun showSaved(activity: AppCompatActivity) {
        if (UpdateDownloadBus.current() is DownloadState.Idle) return
        showInternal(activity)
    }

    /** 通知权限变化后（系统弹窗关闭 / 用户从设置返回）刷新顶部黄条 */
    fun refreshBanner() {
        val activity = host ?: return
        val v = views ?: return
        updateNotice(activity, v)
    }

    /** 宿主 Activity 销毁：只解绑并关窗，绝不取消下载（状态留在 Bus，重进可恢复） */
    fun onHostDestroyed(activity: AppCompatActivity) {
        if (host !== activity) return
        dismissInternal()
    }

    private fun render(state: DownloadState) {
        currentState = state
        val activity = host ?: return
        val v = views ?: return
        if (activity.isFinishing || activity.isDestroyed) {
            dismissInternal()
            return
        }
        // 下载已经结束（完成/失败/取消）时，还挂着的「停止下载？」二次确认必须一起收掉
        if (state !is DownloadState.Running && state !is DownloadState.Verifying) dismissConfirm()
        updateNotice(activity, v)
        when (state) {
            is DownloadState.Idle -> dismissInternal()
            is DownloadState.Running -> renderRunning(v, state)
            is DownloadState.Verifying -> renderVerifying(v, state)
            is DownloadState.Ready -> renderReady(v, state, activity)
            is DownloadState.Failed -> renderFailed(v, state, activity)
        }
    }

    private fun renderRunning(v: Views, state: DownloadState.Running) {
        lastRunning = state
        v.title.text = host?.getString(R.string.update_dl_title_running, state.versionName).orEmpty()
        v.percent.setTextColor(ContextCompat.getColor(host ?: return, R.color.text_primary))
        v.progress.progressTintList = ColorStateList.valueOf(
            ContextCompat.getColor(host ?: return, R.color.colorPrimary))

        if (state.pct < 0) {
            v.percent.text = host?.getString(R.string.update_dl_percent_unknown).orEmpty()
            v.progress.isIndeterminate = true
        } else {
            v.percent.text = host?.getString(R.string.update_dl_percent_value, state.pct).orEmpty()
            v.progress.isIndeterminate = false
            v.progress.progress = state.pct
        }
        v.detail.text = buildDetail(state)
        v.state.setText(R.string.update_dl_state_running)

        v.primary.isEnabled = true
        v.primary.setText(R.string.update_dl_btn_cancel)
        v.primary.setOnClickListener { confirmStop() }
        v.secondary.visibility = View.VISIBLE
        v.secondary.isEnabled = true
        v.secondary.setText(R.string.update_dl_btn_background)
        v.secondary.setOnClickListener { dismissInternal() }
    }

    private fun renderVerifying(v: Views, state: DownloadState.Verifying) {
        val activity = host ?: return
        v.title.text = activity.getString(R.string.update_dl_title_running, state.versionName)
        v.percent.setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
        v.percent.setText(R.string.update_dl_percent_verifying)
        v.progress.progressTintList = ColorStateList.valueOf(
            ContextCompat.getColor(activity, R.color.colorPrimary))
        v.progress.isIndeterminate = true
        v.detail.text = lastRunning?.let {
            activity.getString(R.string.update_dl_detail_size, sizeText(it.bytes), sizeText(it.total))
        }.orEmpty()
        v.state.setText(R.string.update_dl_state_verifying)

        // 校验阶段不可取消（几秒内就会出结果），只留「后台下载」
        v.primary.isEnabled = false
        v.primary.setText(R.string.update_dl_btn_wait)
        v.primary.setOnClickListener(null)
        v.secondary.visibility = View.VISIBLE
        v.secondary.isEnabled = true
        v.secondary.setText(R.string.update_dl_btn_background)
        v.secondary.setOnClickListener { dismissInternal() }
    }

    private fun renderReady(v: Views, state: DownloadState.Ready, activity: AppCompatActivity) {
        v.title.text = activity.getString(R.string.update_dl_title_ready, state.versionName)
        v.percent.setTextColor(ContextCompat.getColor(activity, R.color.colorPrimary))
        v.percent.text = activity.getString(R.string.update_dl_percent_value, 100)
        v.progress.progressTintList = ColorStateList.valueOf(
            ContextCompat.getColor(activity, R.color.colorPrimary))
        v.progress.isIndeterminate = false
        v.progress.progress = 100
        v.detail.text = lastRunning?.let {
            activity.getString(R.string.update_dl_detail_size, sizeText(it.total), sizeText(it.total))
        }.orEmpty()
        v.state.setText(R.string.update_dl_state_ready)

        v.primary.isEnabled = true
        v.primary.setText(R.string.update_dl_btn_install)
        v.primary.setOnClickListener { installNow(state) }
        v.secondary.visibility = View.VISIBLE
        v.secondary.isEnabled = true
        v.secondary.setText(R.string.update_dl_btn_later)
        v.secondary.setOnClickListener { dismissInternal() }
    }

    private fun renderFailed(v: Views, state: DownloadState.Failed, activity: AppCompatActivity) {
        v.title.text = activity.getString(R.string.update_dl_title_failed, state.versionName)
        // 保留已下载比例，但整体转红
        val pct = lastRunning?.pct ?: -1
        v.percent.setTextColor(colorFail)
        if (pct >= 0) {
            v.percent.text = activity.getString(R.string.update_dl_percent_value, pct)
        } else {
            v.percent.setText(R.string.update_dl_percent_unknown)
        }
        v.progress.progressTintList = ColorStateList.valueOf(colorFail)
        v.progress.isIndeterminate = pct < 0
        if (pct >= 0) v.progress.progress = pct
        v.detail.text = lastRunning?.let {
            activity.getString(R.string.update_dl_detail_size, sizeText(it.bytes), sizeText(it.total))
        }.orEmpty()
        v.state.setTextColor(colorFail)
        v.state.text = state.reason

        v.primary.isEnabled = true
        v.primary.setText(R.string.update_dl_btn_retry)
        v.primary.setOnClickListener { retry() }
        v.secondary.visibility = View.VISIBLE
        v.secondary.isEnabled = true
        v.secondary.setText(R.string.update_dl_btn_browser)
        v.secondary.setOnClickListener { openBrowser() }
    }

    private fun updateNotice(activity: AppCompatActivity, v: Views) {
        val enabled = NotificationManagerCompat.from(activity).areNotificationsEnabled()
        if (enabled) {
            v.notice.visibility = View.GONE
        } else {
            v.notice.setText(R.string.update_dl_notice_closed)
            v.notice.visibility = View.VISIBLE
        }
        // Failed / Ready 时状态行颜色需要恢复（重复进入不同状态时避免残留红字）
        if (currentState !is DownloadState.Failed) {
            v.state.setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
        }
    }

    private fun buildDetail(state: DownloadState.Running): String {
        val activity = host ?: return ""
        val total = if (state.total > 0) sizeText(state.total) else "?"
        val eta = if (state.total > 0 && state.speedBps > 0) {
            ((state.total - state.bytes).coerceAtLeast(0L) / state.speedBps).toInt()
        } else {
            null
        }
        return if (eta != null) {
            // 剩余时间过百秒时换成分钟，避免详情行被撑成两行
            val etaText = if (eta >= 60) {
                activity.getString(R.string.update_dl_eta_minutes, eta / 60)
            } else {
                activity.getString(R.string.update_dl_eta_seconds, eta)
            }
            activity.getString(R.string.update_dl_detail_full,
                sizeText(state.bytes), total, sizeText(state.speedBps), etaText)
        } else {
            activity.getString(R.string.update_dl_detail_no_eta,
                sizeText(state.bytes), total, sizeText(state.speedBps))
        }
    }

    private fun onBackPressed() {
        when (currentState) {
            is DownloadState.Running, is DownloadState.Verifying -> confirmStop()
            else -> dismissInternal()
        }
    }

    private fun confirmStop() {
        val activity = host ?: return
        if (currentState !is DownloadState.Running && currentState !is DownloadState.Verifying) return
        val d = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_dl_confirm_title)
            .setMessage(R.string.update_dl_confirm_message)
            .setNegativeButton(R.string.update_dl_confirm_continue, null)
            .setPositiveButton(R.string.update_dl_confirm_stop) { _, _ ->
                UpdateDownloadService.cancel(activity)
                dismissInternal()
            }
            .create()
        confirmDialog = d
        d.setOnDismissListener { confirmDialog = null }
        d.show()
    }

    private fun installNow(state: DownloadState.Ready) {
        val activity = host ?: return
        val file = File(state.apkPath)
        if (!file.exists()) {
            toast(activity.getString(R.string.update_dl_fail_generic))
            return
        }
        val err = UpdateDownloadService.launchInstaller(activity, file)
        if (err != null) {
            toast(err)
        } else {
            dismissInternal()
        }
    }

    private fun retry() {
        val activity = host ?: return
        val info = lastInfo
        if (info != null) {
            UpdateDownloadService.start(activity, info.apkUrl, info.versionName,
                info.versionCode.toLong(), info.sha256)
            return
        }
        // 从关于页常驻行打开时没有 UpdateInfo，复用服务记下的最近一次请求
        val req = UpdateDownloadService.lastRequest ?: return
        UpdateDownloadService.start(activity, req.apkUrl, req.versionName, req.versionCode, req.sha256)
    }

    private fun openBrowser() {
        val activity = host ?: return
        val url = lastInfo?.apkUrl ?: UpdateDownloadService.lastRequest?.apkUrl ?: return
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            toast(activity.getString(R.string.update_dl_browser_missing))
        }
    }

    private fun openNotificationSettings(activity: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${activity.packageName}"))
        }
        runCatching { activity.startActivity(intent) }
    }

    private fun toast(text: String) {
        val activity = host ?: return
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
    }

    private fun dismissInternal() {
        UpdateDownloadBus.removeListener(listener)
        dismissConfirm()
        val d = dialog
        dialog = null
        views = null
        host = null
        runCatching { if (d?.isShowing == true) d.dismiss() }
    }

    private fun dismissConfirm() {
        val c = confirmDialog
        confirmDialog = null
        runCatching { if (c?.isShowing == true) c.dismiss() }
    }

    private fun sizeText(bytes: Long): String = when {
        bytes <= 0L -> "0 B"
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private const val WIDTH_RATIO = 0.92f
}
