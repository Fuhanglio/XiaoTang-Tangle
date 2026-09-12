package com.Tangle.timetable.suda_life

import android.os.Bundle
import com.Tangle.timetable.R
import com.Tangle.timetable.base_view.BaseTitleActivity

class SudaLifeActivity : BaseTitleActivity() {

    override val layoutId: Int
        get() = R.layout.activity_suda_life

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when {
            intent.getStringExtra("type") == "空教室" -> {
                mainTitle.text = "空教室查询"
                val fragment = EmptyRoomFragment()
                val transaction = supportFragmentManager.beginTransaction()
                transaction.add(R.id.fl_fragment, fragment, null)
                transaction.commit()
            }
            intent.getStringExtra("type") == "澡堂" -> {
                mainTitle.text = "澡堂拥挤度"
                val fragment = BathFragment()
                val transaction = supportFragmentManager.beginTransaction()
                transaction.add(R.id.fl_fragment, fragment, null)
                transaction.commit()
            }
        }
    }
}
