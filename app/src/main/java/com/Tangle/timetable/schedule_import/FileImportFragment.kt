package com.Tangle.timetable.schedule_import

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.Tangle.timetable.R
import com.Tangle.timetable.base_view.BaseFragment
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.ViewUtils

class FileImportFragment : BaseFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_file_import, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewUtils.resizeStatusBar(context!!.applicationContext, v_status)

        tv_self.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            try {
                activity?.startActivityForResult(intent, Const.REQUEST_CODE_IMPORT_FILE)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        ib_back.setOnClickListener {
            activity!!.finish()
        }
    }
}
