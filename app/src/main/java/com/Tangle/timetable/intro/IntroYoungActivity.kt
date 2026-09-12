package com.Tangle.timetable.intro

import android.os.Bundle
import androidx.appcompat.widget.AppCompatTextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.Tangle.timetable.R
import com.Tangle.timetable.base_view.BaseBlurTitleActivity
import com.Tangle.timetable.utils.Utils
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.activity_intro_young.*

class IntroYoungActivity : BaseBlurTitleActivity() {
    override val layoutId: Int
        get() = R.layout.activity_intro_young

    override fun onSetupSubButton(tvButton: AppCompatTextView): AppCompatTextView? {
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Glide.with(this)
                .load("https://ws1.sinaimg.cn/large/006tNbRwgy1fxto1a67fej305c05cwen.jpg")
                .error(R.drawable.net_work_error)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(iv_logo)

        tv_download.setOnClickListener {
            Toasty.info(this, "此功能暂未开放").show()
        }
    }
}
