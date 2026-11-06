package com.Tangle.timetable.intro

import android.os.Bundle
import androidx.appcompat.widget.AppCompatTextView
import com.Tangle.timetable.R
import com.Tangle.timetable.base_view.BaseBlurTitleActivity
import com.Tangle.timetable.utils.AppInfoUtils
import com.Tangle.timetable.update.UpdateManager

class AboutActivity : BaseBlurTitleActivity() {
    override val layoutId: Int
        get() = R.layout.activity_about

    override fun onSetupSubButton(tvButton: AppCompatTextView): AppCompatTextView? {
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val name = AppInfoUtils.getVersionName(this)
        val code = AppInfoUtils.getVersionCode(this)
        // 双口径：名称（3.699）与 versionCode（168）同时展示，避免与 release tag 再次分叉
        tv_version.text = "版本号：$name（$code）"

        findViewById<androidx.appcompat.widget.AppCompatTextView>(R.id.tv_check_update)
            ?.setOnClickListener { UpdateManager.checkManual(this) }

    }
}
