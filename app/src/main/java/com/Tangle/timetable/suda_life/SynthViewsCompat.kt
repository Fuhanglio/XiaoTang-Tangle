@file:Suppress("unused")

package com.Tangle.timetable.suda_life

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

val BathFragment.cv_female: androidx.cardview.widget.CardView
    get() = findViewCompat(R.id.cv_female)

val BathFragment.cv_male: androidx.cardview.widget.CardView
    get() = findViewCompat(R.id.cv_male)

val BathFragment.tv_female_rate: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_female_rate)

val BathFragment.tv_female_stay: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_female_stay)

val BathFragment.tv_male_rate: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_male_rate)

val BathFragment.tv_male_stay: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_male_stay)

val EmptyRoomFragment.rv_room: androidx.recyclerview.widget.RecyclerView
    get() = findViewCompat(R.id.rv_room)

val EmptyRoomFragment.spinner_building: androidx.appcompat.widget.AppCompatSpinner
    get() = findViewCompat(R.id.spinner_building)

val EmptyRoomFragment.spinner_campus: androidx.appcompat.widget.AppCompatSpinner
    get() = findViewCompat(R.id.spinner_campus)

val EmptyRoomFragment.spinner_date: androidx.appcompat.widget.AppCompatSpinner
    get() = findViewCompat(R.id.spinner_date)

