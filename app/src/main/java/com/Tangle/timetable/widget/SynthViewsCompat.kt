@file:Suppress("unused")

package com.Tangle.timetable.widget

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

val EditDetailFragment.cg_details: com.google.android.material.chip.ChipGroup
    get() = findViewCompat(R.id.cg_details)

val EditDetailFragment.et_detail: androidx.appcompat.widget.AppCompatEditText
    get() = findViewCompat(R.id.et_detail)

val EditDetailFragment.sv_details: android.widget.ScrollView
    get() = findViewCompat(R.id.sv_details)

val EditDetailFragment.tv_save: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_save)

val EditDetailFragment.tv_title: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_title)

