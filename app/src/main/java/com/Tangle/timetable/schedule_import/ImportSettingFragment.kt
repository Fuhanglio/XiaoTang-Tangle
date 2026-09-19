package com.Tangle.timetable.schedule_import

import android.os.Bundle
import android.view.View
import androidx.fragment.app.BaseDialogFragment
import androidx.fragment.app.activityViewModels
import com.Tangle.timetable.R

class ImportSettingFragment : BaseDialogFragment() {
    override val layoutId: Int
        get() = R.layout.fragment_import_setting

    private val viewModel by activityViewModels<ImportViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tv_cover.setOnClickListener {
            // extras 缺 tableId 时不能以 -1 覆盖导入（外键违反导致导入必失败），改为新建课表
            val tid = activity?.intent?.extras?.getInt("tableId", -1) ?: -1
            if (tid > 0) {
                viewModel.importId = tid
                viewModel.newFlag = false
                dismiss()
            } else {
                launch {
                    viewModel.importId = viewModel.getNewId()
                    viewModel.newFlag = true
                    dismiss()
                }
            }
        }

        tv_new.setOnClickListener {
            launch {
                viewModel.importId = viewModel.getNewId()
                viewModel.newFlag = true
                dismiss()
            }
        }

        tv_cancel.setOnClickListener {
            dismiss()
            activity!!.finish()
        }
    }
}
