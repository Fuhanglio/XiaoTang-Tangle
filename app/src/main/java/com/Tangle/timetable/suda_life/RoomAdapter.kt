package com.Tangle.timetable.suda_life

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.Tangle.timetable.R
import com.Tangle.timetable.bean.SudaRoomData
import com.Tangle.timetable.widget.RoomView

class RoomAdapter(layoutResId: Int, data: MutableList<SudaRoomData>) :
        BaseQuickAdapter<SudaRoomData, BaseViewHolder>(layoutResId, data) {

    override fun convert(helper: BaseViewHolder, item: SudaRoomData?) {
        if (item == null) return
        helper.getView<RoomView>(R.id.room_view).list = item.kfj.split(',')
        helper.setText(R.id.tv_room_name, item.jsbh)
    }

}