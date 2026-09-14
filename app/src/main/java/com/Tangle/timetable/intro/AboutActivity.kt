package com.Tangle.timetable.intro

import android.os.Bundle
import androidx.appcompat.widget.AppCompatTextView
import com.Tangle.timetable.R
import com.Tangle.timetable.base_view.BaseBlurTitleActivity
import com.Tangle.timetable.utils.UpdateUtils

class AboutActivity : BaseBlurTitleActivity() {
    override val layoutId: Int
        get() = R.layout.activity_about

    override fun onSetupSubButton(tvButton: AppCompatTextView): AppCompatTextView? {
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            tv_version.text = "版本号：${UpdateUtils.getVersionName(this)}"
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }
}
