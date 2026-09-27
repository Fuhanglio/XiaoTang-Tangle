package com.Tangle.timetable.update

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.getPrefer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import es.dmoral.toasty.Toasty

object UpdateDialog {

    /**
     * 展示「发现新版本」弹窗（立即更新 / 稍后 / 跳过此版本）。
     *
     * @param alreadySkipped W6-07：该版本此前已被用户点过「跳过此版本」。
     *   只有**手动**检查才会传 true —— 手动检查时用户明确想看结果，不该被拿「已是最新版本」糊弄，
     *   所以照常弹窗，只把标题与中性按钮改成"你之前跳过的是这个版本"的口径，
     *   避免用户误以为自己没点过、或以为点了跳过就再也装不上。
     */
    fun showUpdate(context: Context, info: UpdateInfo, alreadySkipped: Boolean = false) {
        if (context !is AppCompatActivity) return
        if (context.isFinishing) return
        val note = if (info.notes.isBlank()) "修复若干已知问题，提升稳定性。" else info.notes.take(800)
        MaterialAlertDialogBuilder(context)
            .setTitle(if (alreadySkipped) "v${info.versionName}（此前已跳过）"
            else "发现新版本 v${info.versionName}")
            .setMessage(note)
            .setCancelable(true)
            .setPositiveButton("立即更新") { _, _ ->
                // W6-03：先确认「安装未知应用」已授权（API 26 以下不需要该权限，helper 里已判版本），
                // 否则会白下几十 MB 才在安装阶段失败，用户还得再下一遍
                if (UpdateDownloadService.canRequestInstall(context)) {
                    UpdateDownloadService.start(context, info.apkUrl, info.versionName, info.versionCode.toLong(), info.sha256)
                } else {
                    MaterialAlertDialogBuilder(context)
                        .setTitle("需要先允许安装应用")
                        .setMessage("系统默认不允许本应用安装其他应用。请先在设置里为「小唐Tangle」打开「安装未知应用」，" +
                                "再回来重新点「立即更新」即可（已下载的包不用重复下）。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("去开启") { _, _ ->
                            try {
                                context.startActivity(android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    android.net.Uri.parse("package:${context.packageName}"))
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Exception) {
                            }
                        }
                        .show()
                }
            }
            .setNegativeButton("稍后") { _, _ -> }
            // W6-07：已跳过过的版本把按钮文案改成「保持跳过」——
            // 行为不变（仍是写入同一个 KEY_UPDATE_SKIP_VERSION，幂等），
            // 但用户能立刻意识到"我上次跳过的就是这个版本"，而不是看到一个陌生的「跳过此版本」。
            .setNeutralButton(if (alreadySkipped) "保持跳过" else "跳过此版本") { _, _ ->
                val e = context.getPrefer().edit()
                e.putString(Const.KEY_UPDATE_SKIP_VERSION, info.versionName)
                e.apply()
            }
            .show()
    }

    fun toastLatest(context: Context) =
        Toasty.success(context, "已是最新版本", Toasty.LENGTH_SHORT).show()

    fun toastNoNetwork(context: Context) =
        Toasty.warning(context, "网络不可用，无法检查更新", Toasty.LENGTH_SHORT).show()

    fun toastError(context: Context, msg: String) =
        Toasty.error(context, "检查更新失败：$msg", Toasty.LENGTH_LONG).show()
}
