package com.Tangle.timetable

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.Tangle.timetable.schedule.ScheduleActivity
import com.Tangle.timetable.utils.UpdateUtils
import kotlinx.coroutines.launch
import splitties.activities.start

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            UpdateUtils.tranOldData(applicationContext)
            start<ScheduleActivity>()
            //startActivity<SudaLifeActivity>("type" to "澡堂")
            finish()
        }
    }

    override fun onBackPressed() {

    }
}
