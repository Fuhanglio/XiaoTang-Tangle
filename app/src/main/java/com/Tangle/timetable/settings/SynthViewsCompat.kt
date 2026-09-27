
package com.Tangle.timetable.settings

/*
 * W1-12：本文件是**生成物**（原 kotlinx.android.synthetic 的类型化只读访问器）。
 *
 * 剪枝策略（2026-09-26 首次执行）：
 *   · 只保留**有引用点**的访问器；全工程零引用的已删除（本轮 schedule_import 删了 10 个）
 *   · 因此这里**不再有**文件级 `@file:Suppress("unused")` ——
 *     留着它会让"以后新出现的未用属性"永远报不出来；现在由 lint 兜底
 *   · 访问器本身还是"每次 findViewById"的语义（无缓存），
 *     在**高频回调**里使用前请先缓存到字段（见 W1-06 / W1-07 / W1-08）
 *
 * ⚠ 增删本文件后必须重新编译：访问器少一个、而调用点还在，编译期就能发现；
 *   反过来（调用点没了、访问器还在）没有任何报错，只能靠定期剪枝。
 */
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

val SelectTimeDetailFragment.btn_cancel: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.btn_cancel)

val SelectTimeDetailFragment.btn_save: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.btn_save)

val SelectTimeDetailFragment.tv_title: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_title)

val SelectTimeDetailFragment.wp_end: cn.carbswang.android.numberpickerview.library.NumberPickerView
    get() = findViewCompat(R.id.wp_end)

val SelectTimeDetailFragment.wp_start: cn.carbswang.android.numberpickerview.library.NumberPickerView
    get() = findViewCompat(R.id.wp_start)

val TimeSettingsActivity.ll_root: androidx.appcompat.widget.LinearLayoutCompat
    get() = findViewCompat(R.id.ll_root)

val TimeSettingsActivity.nav_fragment: android.view.View
    get() = findViewCompat(R.id.nav_fragment)

