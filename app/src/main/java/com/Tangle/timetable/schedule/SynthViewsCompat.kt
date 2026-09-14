@file:Suppress("unused")

package com.Tangle.timetable.schedule

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

val CourseDetailFragment.div_teacher: android.view.View
    get() = findViewCompat(R.id.div_teacher)

val CourseDetailFragment.div_time: android.view.View
    get() = findViewCompat(R.id.div_time)

val CourseDetailFragment.div_weeks: android.view.View
    get() = findViewCompat(R.id.div_weeks)

val CourseDetailFragment.et_room: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.et_room)

val CourseDetailFragment.et_teacher: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.et_teacher)

val CourseDetailFragment.et_time: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.et_time)

val CourseDetailFragment.et_weeks: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.et_weeks)

val CourseDetailFragment.ib_delete: androidx.appcompat.widget.AppCompatImageView
    get() = findViewCompat(R.id.ib_delete)

val CourseDetailFragment.ib_delete_course: androidx.appcompat.widget.AppCompatImageView
    get() = findViewCompat(R.id.ib_delete_course)

val CourseDetailFragment.ib_edit: androidx.appcompat.widget.AppCompatImageView
    get() = findViewCompat(R.id.ib_edit)

val CourseDetailFragment.ll_room: androidx.appcompat.widget.LinearLayoutCompat
    get() = findViewCompat(R.id.ll_room)

val CourseDetailFragment.ll_teacher: androidx.appcompat.widget.LinearLayoutCompat
    get() = findViewCompat(R.id.ll_teacher)

val CourseDetailFragment.ll_time: androidx.appcompat.widget.LinearLayoutCompat
    get() = findViewCompat(R.id.ll_time)

val CourseDetailFragment.ll_weeks: androidx.appcompat.widget.LinearLayoutCompat
    get() = findViewCompat(R.id.ll_weeks)

val CourseDetailFragment.tv_item: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_item)

val CourseDetailFragment.tv_tips: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_tips)

val ExportSettingsFragment.tv_cancel: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_cancel)

val ExportSettingsFragment.tv_export: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_export)

val ExportSettingsFragment.tv_export_ics: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_export_ics)

val ExportSettingsFragment.tv_sync_calendar: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_sync_calendar)

val ImportChooseFragment.ib_close: androidx.appcompat.widget.AppCompatImageView
    get() = findViewCompat(R.id.ib_close)

val ImportChooseFragment.tv_excel: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_excel)

val ImportChooseFragment.tv_file: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_file)

val ImportChooseFragment.tv_html: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_html)

val ImportChooseFragment.tv_image: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_image)

val ImportChooseFragment.tv_school: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_school)

val MultiCourseFragment.viewpager: androidx.viewpager.widget.ViewPager
    get() = findViewCompat(R.id.viewpager)

