package com.Tangle.timetable.schedule

import androidx.appcompat.widget.AppCompatImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.Tangle.timetable.R
import com.Tangle.timetable.bean.TableSelectBean
import splitties.dimensions.dip

class TableNameAdapter(layoutResId: Int, data: MutableList<TableSelectBean>) :
        BaseQuickAdapter<TableSelectBean, BaseViewHolder>(layoutResId, data) {

    override fun convert(helper: BaseViewHolder, item: TableSelectBean?) {
        if (item == null) return
        helper.setGone(R.id.menu_setting, item.type != 1)

        if (item.tableName != "") {
            helper.setText(R.id.tv_table_name, item.tableName)
        } else {
            helper.setText(R.id.tv_table_name, "我的课表")
        }
        val imageView = helper.getView<AppCompatImageView>(R.id.iv_table_bg)
        if (item.background != "") {
            Glide.with(context)
                    .load(item.background)
                    .override(200, 300)
                    .transform(CenterCrop(), RoundedCorners(context.dip(14)))
                    .into(imageView)
        } else {
            // 无自定义背景：浅蓝圆角卡片 + 蓝色课表线性图标（替代旧黑色方块图）
            Glide.with(context).clear(imageView)
            imageView.background = null
            imageView.setPadding(0, 0, 0, 0)
            imageView.scaleType = android.widget.ImageView.ScaleType.CENTER
            imageView.setImageResource(R.drawable.s_timetable_blue)
            imageView.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = context.dip(14).toFloat()
                setColor(0x14007AFF)
            }
        }
    }

}