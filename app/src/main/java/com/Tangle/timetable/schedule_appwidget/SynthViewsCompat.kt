@file:Suppress("unused")

package com.Tangle.timetable.schedule_appwidget

import com.Tangle.timetable.R

// ===== 自动生成：原 kotlinx.android.synthetic 的类型化只读访问器 =====
// AGP8 / Kotlin 1.9 起 kotlin-android-extensions 已移除，这里为各页面类生成同名只读属性，
// 行为与原 synthetics 等价（内部走 findViewById）。

// 兼容 Activity 与 Fragment 的统一取视图入口（Fragment 没有直接可用的 findViewById）
@Suppress("UNCHECKED_CAST")
private fun <T : android.view.View> android.app.Activity.findViewCompat(id: Int): T =
        findViewById<T>(id)!!

@Suppress("UNCHECKED_CAST")
private fun <T : android.view.View> androidx.fragment.app.Fragment.findViewCompat(id: Int): T =
        (view ?: throw IllegalStateException("Fragment view is null")).findViewById<T>(id)!!

val WeekScheduleAppWidgetConfigActivity.iv_tip: androidx.appcompat.widget.AppCompatImageView
    get() = findViewCompat(R.id.iv_tip)

val WeekScheduleAppWidgetConfigActivity.ll_root: androidx.appcompat.widget.LinearLayoutCompat
    get() = findViewCompat(R.id.ll_root)

val WeekScheduleAppWidgetConfigActivity.rv_list: androidx.recyclerview.widget.RecyclerView
    get() = findViewCompat(R.id.rv_list)

val WeekScheduleAppWidgetConfigActivity.tv_got_it: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_got_it)

