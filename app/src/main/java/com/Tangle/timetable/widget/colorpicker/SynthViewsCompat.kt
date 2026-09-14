@file:Suppress("unused")

package com.Tangle.timetable.widget.colorpicker

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

val ColorPickerFragment.btn_cancel: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.btn_cancel)

val ColorPickerFragment.btn_save: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.btn_save)

val ColorPickerFragment.cpv_color: com.Tangle.timetable.widget.colorpicker.ColorPickerView
    get() = findViewCompat(R.id.cpv_color)

val ColorPickerFragment.et_color: com.google.android.material.textfield.TextInputEditText
    get() = findViewCompat(R.id.et_color)

val ColorPickerFragment.v_color: android.view.View
    get() = findViewCompat(R.id.v_color)

