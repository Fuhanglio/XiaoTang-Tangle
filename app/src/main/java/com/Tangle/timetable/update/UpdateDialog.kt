package com.Tangle.timetable.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.Tangle.timetable.R
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.getPrefer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import es.dmoral.toasty.Toasty

object UpdateDialog {

    /** 弹窗宽度占屏宽比例（K）：默认 Material 宽度在小屏只有 248dp，一行 11 字 */
    private const val WIDTH_RATIO = 0.92f

    /** 正文最大高度占屏高比例（K）：剩余空间留给标题与按钮，末行不会被裁掉 */
    private const val MAX_CONTENT_HEIGHT_RATIO = 0.55f

    /** 当前正在展示的弹窗：连点/重复触发时先关旧的，保证单实例 */
    private var currentDialog: AlertDialog? = null
    /** 当前弹窗挂靠的宿主 Activity：旋转重建时据此判断是否需要先关窗 */
    private var hostActivity: AppCompatActivity? = null
    /** 最近一次展示的内容：宿主重建后据此恢复，保证旋转后屏幕上仍只有一个弹窗 */
    private var lastInfo: UpdateInfo? = null
    private var lastSkipped = false
    private var pendingReShow = false

    /**
     * 展示「发现新版本」弹窗（立即更新 / 稍后 / 跳过此版本）。
     *
     * @param alreadySkipped W6-07：该版本此前已被用户点过「跳过此版本」。
     *   只有**手动**检查才会传 true —— 手动检查时用户明确想看结果，不该被拿「已是最新版本」糊弄，
     *   所以照常弹窗，只把标题与中性按钮改成"你之前跳过的是这个版本"的口径。
     */
    fun showUpdate(context: Context, info: UpdateInfo, alreadySkipped: Boolean = false) {
        val activity = context as? AppCompatActivity ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        // 单实例：旧的先关，连点 10 次也只留最后这一个
        currentDialog?.let { old ->
            runCatching { if (old.isShowing) old.dismiss() }
        }
        currentDialog = null

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_update, null)
        val tvNotes = view.findViewById<TextView>(R.id.tv_update_notes)
        val svNotes = view.findViewById<NestedScrollView>(R.id.sv_update_notes)

        val note = ReleaseNotesFormatter.format(info.notes)
            .ifBlank { "修复若干已知问题，提升稳定性。" }
        tvNotes.text = note

        val tvFullNotes = view.findViewById<TextView>(R.id.tv_full_notes)
        tvFullNotes.setOnClickListener {
            openReleasesPage(activity)
        }

        val title = if (alreadySkipped) "新版本 ${info.shownName}（此前已跳过）"
        else "新版本 ${info.shownName}"
        lastInfo = info
        lastSkipped = alreadySkipped
        pendingReShow = false

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(view)
            .setCancelable(true)
            .setPositiveButton("立即更新") { _, _ -> startUpdate(activity, info) }
            .setNegativeButton("稍后") { _, _ -> }
            // W6-07：已跳过过的版本把按钮文案改成「保持跳过」——
            // 行为不变（仍是写入同一个 KEY_UPDATE_SKIP_VERSION，幂等），
            // 但用户能立刻意识到"我上次跳过的就是这个版本"，而不是看到一个陌生的「跳过此版本」。
            .setNeutralButton(if (alreadySkipped) "保持跳过" else "跳过此版本") { _, _ ->
                val e = activity.getPrefer().edit()
                e.putString(Const.KEY_UPDATE_SKIP_VERSION, info.versionName)
                e.apply()
            }
            .create()

        dialog.show()
        currentDialog = dialog
        hostActivity = activity
        dialog.setOnDismissListener {
            if (currentDialog === dialog) currentDialog = null
        }

        // 宽度 92% 屏宽：必须放在 show() 之后，此时 window 才存在
        val screenWidth = activity.resources.displayMetrics.widthPixels
        dialog.window?.setLayout(
            (screenWidth * WIDTH_RATIO).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        // 限高 55% 屏高：post 到一次测量之后再做，避免拿到未测量的 0
        val maxContentHeight = (activity.resources.displayMetrics.heightPixels * MAX_CONTENT_HEIGHT_RATIO).toInt()
        val root = view as LinearLayout
        svNotes.post {
            if (!dialog.isShowing) return@post
            // 55% 屏高是整个自定义视图（含根内边距与「查看完整更新日志」）的总预算。
            // 不扣掉链接与内边距的话，滚动区会吃掉全部预算，底部链接被挤成 0 高直接消失。
            val linkLp = tvFullNotes.layoutParams as ViewGroup.MarginLayoutParams
            // 用 minHeight 而不是当前 height：首次布局时链接可能已被挤压，拿实测值会一路越算越小
            val linkHeight = maxOf(tvFullNotes.height, tvFullNotes.minHeight)
            val reserved = root.paddingTop + root.paddingBottom +
                    linkHeight + linkLp.topMargin + linkLp.bottomMargin
            val contentHeight = tvNotes.measuredHeight + svNotes.paddingTop + svNotes.paddingBottom
            val target = minOf(contentHeight, (maxContentHeight - reserved).coerceAtLeast(0))
            if (target > 0 && svNotes.height != target) {
                val lp = svNotes.layoutParams
                lp.height = target
                svNotes.layoutParams = lp
            }
        }
    }

    /** 宿主 Activity 被销毁（旋转重建等）：关掉挂在旧窗口上的弹窗，避免 WindowLeaked，并标记待重显 */
    fun onHostDestroyed(activity: Activity) {
        val dialog = currentDialog ?: return
        if (hostActivity !== activity) return
        pendingReShow = lastInfo != null
        runCatching { if (dialog.isShowing) dialog.dismiss() }
        currentDialog = null
        hostActivity = null
    }

    /** 新宿主已 resume：若刚因重建关过弹窗，在新窗口上恢复同一个，屏幕上始终只有 1 个 */
    fun onHostResumed(activity: AppCompatActivity) {
        if (!pendingReShow) return
        val info = lastInfo ?: return
        pendingReShow = false
        if (activity.isFinishing || activity.isDestroyed) return
        showUpdate(activity, info, lastSkipped)
    }

    private fun startUpdate(activity: AppCompatActivity, info: UpdateInfo) {
        // W6-03：先确认「安装未知应用」已授权（API 26 以下不需要该权限，helper 里已判版本），
        // 否则会白下几十 MB 才在安装阶段失败，用户还得再下一遍
        if (UpdateDownloadService.canRequestInstall(activity)) {
            UpdateDownloadService.start(
                activity, info.apkUrl, info.versionName, info.versionCode.toLong(), info.sha256
            )
        } else {
            MaterialAlertDialogBuilder(activity)
                .setTitle("需要先允许安装应用")
                .setMessage("系统默认不允许本应用安装其他应用。请先在设置里为「小唐Tangle」打开「安装未知应用」，" +
                        "再回来重新点「立即更新」即可（已下载的包不用重复下）。")
                .setNegativeButton("取消", null)
                .setPositiveButton("去开启") { _, _ ->
                    try {
                        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${activity.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                    }
                }
                .show()
        }
    }

    /** N：「完整日志」跳 GitHub Releases 页（不占用按钮位，按钮总数仍是 3） */
    private fun openReleasesPage(activity: AppCompatActivity) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateClient.RELEASES_PAGE_URL)))
        } catch (_: Exception) {
            Toasty.warning(activity, "没有可用的浏览器", Toasty.LENGTH_SHORT).show()
        }
    }

    fun toastLatest(context: Context) =
        Toasty.success(context, "已是最新版本", Toasty.LENGTH_SHORT).show()

    fun toastNoNetwork(context: Context) =
        Toasty.warning(context, "网络不可用，无法检查更新", Toasty.LENGTH_SHORT).show()

    fun toastError(context: Context, msg: String) =
        Toasty.error(context, "检查更新失败：$msg", Toasty.LENGTH_LONG).show()
}
