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
            // 启动数据迁移失败也必须继续进主页，不能在协程里抛出未捕获异常导致闪退
            try {
                UpdateUtils.tranOldData(applicationContext)
            } catch (e: Exception) {
                android.util.Log.e("SplashActivity", "启动数据迁移异常（已忽略）", e)
            }
            try {
                start<ScheduleActivity>()
            } catch (e: Exception) {
                android.util.Log.e("SplashActivity", "进入主页失败", e)
            }
            //startActivity<SudaLifeActivity>("type" to "澡堂")
            finish()
        }
    }

    override fun onBackPressed() {

    }
}
