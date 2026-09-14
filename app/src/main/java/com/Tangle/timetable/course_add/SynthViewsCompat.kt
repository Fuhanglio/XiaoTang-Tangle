@file:Suppress("unused")

package com.Tangle.timetable.course_add

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

val SelectTimeFragment.btn_cancel: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.btn_cancel)

val SelectTimeFragment.btn_save: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.btn_save)

val SelectTimeFragment.wp_day: cn.carbswang.android.numberpickerview.library.NumberPickerView
    get() = findViewCompat(R.id.wp_day)

val SelectTimeFragment.wp_end: cn.carbswang.android.numberpickerview.library.NumberPickerView
    get() = findViewCompat(R.id.wp_end)

val SelectTimeFragment.wp_start: cn.carbswang.android.numberpickerview.library.NumberPickerView
    get() = findViewCompat(R.id.wp_start)

val SelectWeekFragment.btn_cancel: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.btn_cancel)

val SelectWeekFragment.btn_save: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.btn_save)

val SelectWeekFragment.rv_week: com.Tangle.timetable.widget.SelectedRecyclerView
    get() = findViewCompat(R.id.rv_week)

val SelectWeekFragment.tv_all: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_all)

val SelectWeekFragment.tv_type1: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_type1)

val SelectWeekFragment.tv_type2: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_type2)

