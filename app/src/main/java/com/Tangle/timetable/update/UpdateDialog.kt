package com.Tangle.timetable.update

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.getPrefer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import es.dmoral.toasty.Toasty

object UpdateDialog {

    /** 展示「发现新版本」弹窗（立即更新 / 稍后 / 跳过此版本） */
    fun showUpdate(context: Context, info: UpdateInfo) {
        if (context !is AppCompatActivity) return
        if (context.isFinishing) return
        val note = if (info.notes.isBlank()) "修复若干已知问题，提升稳定性。" else info.notes.take(800)
        MaterialAlertDialogBuilder(context)
            .setTitle("发现新版本 v${info.versionName}")
            .setMessage(note)
            .setCancelable(true)
            .setPositiveButton("立即更新") { _, _ ->
                UpdateDownloadService.start(context, info.apkUrl, info.versionName)
            }
            .setNegativeButton("稍后") { _, _ -> }
            .setNeutralButton("跳过此版本") { _, _ ->
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
