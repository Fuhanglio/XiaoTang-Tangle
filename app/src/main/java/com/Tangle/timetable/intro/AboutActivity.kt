package com.Tangle.timetable.intro

import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import com.Tangle.timetable.R
import com.Tangle.timetable.base_view.BaseBlurTitleActivity
import com.Tangle.timetable.utils.AppInfoUtils
import com.Tangle.timetable.update.DownloadProgressDialog
import com.Tangle.timetable.update.DownloadState
import com.Tangle.timetable.update.UpdateDownloadBus
import com.Tangle.timetable.update.UpdateManager

class AboutActivity : BaseBlurTitleActivity() {
    override val layoutId: Int
        get() = R.layout.activity_about

    override fun onSetupSubButton(tvButton: AppCompatTextView): AppCompatTextView? {
        return null
    }

    /** 「下载中 42% · 查看」常驻行：只在状态非 Idle 时出现（可选增强项） */
    private var downloadRow: AppCompatTextView? = null

    private val downloadRowListener = UpdateDownloadBus.Listener { state -> updateDownloadRow(state) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val name = AppInfoUtils.getVersionName(this)
        val code = AppInfoUtils.getVersionCode(this)
        // 双口径：名称（3.700）与 versionCode（169）同时展示，避免与 release tag 再次分叉
        tv_version.text = "版本号：$name（$code）"

        findViewById<androidx.appcompat.widget.AppCompatTextView>(R.id.tv_check_update)
            ?.setOnClickListener { UpdateManager.checkManual(this) }

        addDownloadRow()
    }

    /** 状态行挂在 tv_check_update 下方：用代码插入，避免动 activity_about.xml 的整体排版 */
    private fun addDownloadRow() {
        val anchor = findViewById<AppCompatTextView>(R.id.tv_check_update) ?: return
        val parent = anchor.parent as? LinearLayoutCompat ?: return
        val row = AppCompatTextView(this).apply {
            layoutParams = LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.WRAP_CONTENT,
                LinearLayoutCompat.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setPadding(dp(16), dp(6), dp(16), dp(6))
            setTextColor(ContextCompat.getColor(this@AboutActivity, R.color.colorPrimary))
            textSize = 13f
            visibility = View.GONE
            setOnClickListener { DownloadProgressDialog.showSaved(this@AboutActivity) }
        }
        parent.addView(row, parent.indexOfChild(anchor) + 1)
        downloadRow = row
        updateDownloadRow(UpdateDownloadBus.current())
    }

    private fun updateDownloadRow(state: DownloadState) {
        val row = downloadRow ?: return
        when (state) {
            is DownloadState.Idle -> row.visibility = View.GONE
            is DownloadState.Running -> {
                row.visibility = View.VISIBLE
                row.text = getString(R.string.update_dl_row_running, state.pct.coerceAtLeast(0))
            }
            is DownloadState.Verifying -> {
                row.visibility = View.VISIBLE
                row.text = getString(R.string.update_dl_row_verifying)
            }
            is DownloadState.Ready -> {
                row.visibility = View.VISIBLE
                row.text = getString(R.string.update_dl_row_ready)
            }
            is DownloadState.Failed -> {
                row.visibility = View.VISIBLE
                row.text = getString(R.string.update_dl_row_failed)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        UpdateDownloadBus.addListener(downloadRowListener)
        updateDownloadRow(UpdateDownloadBus.current())
    }

    override fun onStop() {
        UpdateDownloadBus.removeListener(downloadRowListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // 退出关于页不中断下载：回来后若仍在下载/校验，自动恢复进度弹窗
        DownloadProgressDialog.restoreIfNeeded(this)
    }

    override fun onDestroy() {
        // 只解绑进度弹窗并关窗，下载任务与 Bus 状态不受影响
        DownloadProgressDialog.onHostDestroyed(this)
        UpdateDownloadBus.removeListener(downloadRowListener)
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
