@file:Suppress("unused")

package com.Tangle.timetable.schedule_import

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

val ExcelImportFragment.ib_back: androidx.appcompat.widget.AppCompatImageButton
    get() = findViewCompat(R.id.ib_back)

val ExcelImportFragment.tv_self: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_self)

val ExcelImportFragment.tv_template: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_template)

val ExcelImportFragment.tv_title: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_title)

val ExcelImportFragment.v_status: android.view.View
    get() = findViewCompat(R.id.v_status)

val FileImportFragment.ib_back: androidx.appcompat.widget.AppCompatImageButton
    get() = findViewCompat(R.id.ib_back)

val FileImportFragment.tv_self: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_self)

val FileImportFragment.tv_title: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_title)

val FileImportFragment.v_status: android.view.View
    get() = findViewCompat(R.id.v_status)

val HtmlImportFragment.cg_qz: com.google.android.material.chip.ChipGroup
    get() = findViewCompat(R.id.cg_qz)

val HtmlImportFragment.cg_zf: com.google.android.material.chip.ChipGroup
    get() = findViewCompat(R.id.cg_zf)

val HtmlImportFragment.chip_qz1: com.google.android.material.chip.Chip
    get() = findViewCompat(R.id.chip_qz1)

val HtmlImportFragment.chip_qz2: com.google.android.material.chip.Chip
    get() = findViewCompat(R.id.chip_qz2)

val HtmlImportFragment.chip_qz3: com.google.android.material.chip.Chip
    get() = findViewCompat(R.id.chip_qz3)

val HtmlImportFragment.chip_qz4: com.google.android.material.chip.Chip
    get() = findViewCompat(R.id.chip_qz4)

val HtmlImportFragment.chip_zf1: com.google.android.material.chip.Chip
    get() = findViewCompat(R.id.chip_zf1)

val HtmlImportFragment.chip_zf2: com.google.android.material.chip.Chip
    get() = findViewCompat(R.id.chip_zf2)

val HtmlImportFragment.cp_gbk: com.google.android.material.chip.Chip
    get() = findViewCompat(R.id.cp_gbk)

val HtmlImportFragment.cp_utf: com.google.android.material.chip.Chip
    get() = findViewCompat(R.id.cp_utf)

val HtmlImportFragment.fab_import: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.fab_import)

val HtmlImportFragment.ib_back: androidx.appcompat.widget.AppCompatImageButton
    get() = findViewCompat(R.id.ib_back)

val HtmlImportFragment.ll_bar: androidx.appcompat.widget.LinearLayoutCompat
    get() = findViewCompat(R.id.ll_bar)

val HtmlImportFragment.sv_content: android.widget.ScrollView
    get() = findViewCompat(R.id.sv_content)

val HtmlImportFragment.tv_self: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_self)

val HtmlImportFragment.tv_title: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_title)

val HtmlImportFragment.tv_type: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_type)

val HtmlImportFragment.tv_way: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_way)

val HtmlImportFragment.v_status: android.view.View
    get() = findViewCompat(R.id.v_status)

val ImageImportFragment.ib_back: androidx.appcompat.widget.AppCompatImageButton
    get() = findViewCompat(R.id.ib_back)

val ImageImportFragment.iv_preview: androidx.appcompat.widget.AppCompatImageView
    get() = findViewCompat(R.id.iv_preview)

val ImageImportFragment.tv_import: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_import)

val ImageImportFragment.tv_pick: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_pick)

val ImageImportFragment.tv_result: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_result)

val ImageImportFragment.tv_status: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_status)

val ImageImportFragment.tv_title: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_title)

val ImageImportFragment.v_status: android.view.View
    get() = findViewCompat(R.id.v_status)

val ImportSettingFragment.tv_cancel: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_cancel)

val ImportSettingFragment.tv_cover: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_cover)

val ImportSettingFragment.tv_new: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.tv_new)

val LoginWebActivity.btg_ports: com.google.android.material.button.MaterialButtonToggleGroup
    get() = findViewCompat(R.id.btg_ports)

val LoginWebActivity.btn_cancel: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.btn_cancel)

val LoginWebActivity.btn_port1: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.btn_port1)

val LoginWebActivity.btn_port2: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.btn_port2)

val LoginWebActivity.btn_to_schedule: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.btn_to_schedule)

val LoginWebActivity.et_code: com.google.android.material.textfield.TextInputEditText
    get() = findViewCompat(R.id.et_code)

val LoginWebActivity.et_id: com.google.android.material.textfield.TextInputEditText
    get() = findViewCompat(R.id.et_id)

val LoginWebActivity.et_pwd: com.google.android.material.textfield.TextInputEditText
    get() = findViewCompat(R.id.et_pwd)

val LoginWebActivity.fab_login: com.google.android.material.floatingactionbutton.FloatingActionButton
    get() = findViewCompat(R.id.fab_login)

val LoginWebActivity.input_code: com.google.android.material.textfield.TextInputLayout
    get() = findViewCompat(R.id.input_code)

val LoginWebActivity.input_id: com.google.android.material.textfield.TextInputLayout
    get() = findViewCompat(R.id.input_id)

val LoginWebActivity.input_pwd: com.google.android.material.textfield.TextInputLayout
    get() = findViewCompat(R.id.input_pwd)

val LoginWebActivity.iv_code: androidx.appcompat.widget.AppCompatImageView
    get() = findViewCompat(R.id.iv_code)

val LoginWebActivity.iv_error: androidx.appcompat.widget.AppCompatImageView
    get() = findViewCompat(R.id.iv_error)

val LoginWebActivity.iv_mask: androidx.appcompat.widget.AppCompatImageView
    get() = findViewCompat(R.id.iv_mask)

val LoginWebActivity.ll_dialog: androidx.constraintlayout.widget.ConstraintLayout
    get() = findViewCompat(R.id.ll_dialog)

val LoginWebActivity.pb_loading: android.widget.ProgressBar
    get() = findViewCompat(R.id.pb_loading)

val LoginWebActivity.progress_bar: android.widget.ProgressBar
    get() = findViewCompat(R.id.progress_bar)

val LoginWebActivity.rl_code: android.widget.RelativeLayout
    get() = findViewCompat(R.id.rl_code)

val LoginWebActivity.scrim: androidx.appcompat.widget.AppCompatImageView
    get() = findViewCompat(R.id.scrim)

val LoginWebActivity.sheet: com.google.android.material.transformation.TransformationChildCard
    get() = findViewCompat(R.id.sheet)

val LoginWebActivity.tv_dialog_title: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_dialog_title)

val LoginWebActivity.tv_thanks: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_thanks)

val LoginWebActivity.tv_tip: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_tip)

val LoginWebActivity.tv_title: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_title)

val LoginWebActivity.wp_term: cn.carbswang.android.numberpickerview.library.NumberPickerView
    get() = findViewCompat(R.id.wp_term)

val LoginWebActivity.wp_years: cn.carbswang.android.numberpickerview.library.NumberPickerView
    get() = findViewCompat(R.id.wp_years)

val LoginWebFragment.btg_ports: com.google.android.material.button.MaterialButtonToggleGroup
    get() = findViewCompat(R.id.btg_ports)

val LoginWebFragment.btn_cancel: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.btn_cancel)

val LoginWebFragment.btn_port1: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.btn_port1)

val LoginWebFragment.btn_port2: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.btn_port2)

val LoginWebFragment.btn_to_schedule: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.btn_to_schedule)

val LoginWebFragment.et_code: com.google.android.material.textfield.TextInputEditText
    get() = findViewCompat(R.id.et_code)

val LoginWebFragment.et_id: com.google.android.material.textfield.TextInputEditText
    get() = findViewCompat(R.id.et_id)

val LoginWebFragment.et_pwd: com.google.android.material.textfield.TextInputEditText
    get() = findViewCompat(R.id.et_pwd)

val LoginWebFragment.fab_login: com.google.android.material.floatingactionbutton.FloatingActionButton
    get() = findViewCompat(R.id.fab_login)

val LoginWebFragment.input_code: com.google.android.material.textfield.TextInputLayout
    get() = findViewCompat(R.id.input_code)

val LoginWebFragment.input_id: com.google.android.material.textfield.TextInputLayout
    get() = findViewCompat(R.id.input_id)

val LoginWebFragment.input_pwd: com.google.android.material.textfield.TextInputLayout
    get() = findViewCompat(R.id.input_pwd)

val LoginWebFragment.iv_code: androidx.appcompat.widget.AppCompatImageView
    get() = findViewCompat(R.id.iv_code)

val LoginWebFragment.iv_error: androidx.appcompat.widget.AppCompatImageView
    get() = findViewCompat(R.id.iv_error)

val LoginWebFragment.iv_mask: androidx.appcompat.widget.AppCompatImageView
    get() = findViewCompat(R.id.iv_mask)

val LoginWebFragment.ll_dialog: androidx.constraintlayout.widget.ConstraintLayout
    get() = findViewCompat(R.id.ll_dialog)

val LoginWebFragment.pb_loading: android.widget.ProgressBar
    get() = findViewCompat(R.id.pb_loading)

val LoginWebFragment.progress_bar: android.widget.ProgressBar
    get() = findViewCompat(R.id.progress_bar)

val LoginWebFragment.rl_code: android.widget.RelativeLayout
    get() = findViewCompat(R.id.rl_code)

val LoginWebFragment.scrim: androidx.appcompat.widget.AppCompatImageView
    get() = findViewCompat(R.id.scrim)

val LoginWebFragment.sheet: com.google.android.material.transformation.TransformationChildCard
    get() = findViewCompat(R.id.sheet)

val LoginWebFragment.tv_dialog_title: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_dialog_title)

val LoginWebFragment.tv_thanks: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_thanks)

val LoginWebFragment.tv_tip: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_tip)

val LoginWebFragment.tv_title: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_title)

val LoginWebFragment.wp_term: cn.carbswang.android.numberpickerview.library.NumberPickerView
    get() = findViewCompat(R.id.wp_term)

val LoginWebFragment.wp_years: cn.carbswang.android.numberpickerview.library.NumberPickerView
    get() = findViewCompat(R.id.wp_years)

val SchoolListActivity.ll_root: androidx.appcompat.widget.LinearLayoutCompat
    get() = findViewCompat(R.id.ll_root)

val SchoolListActivity.quickSideBarTipsView: com.bigkoo.quicksidebar.QuickSideBarTipsView
    get() = findViewCompat(R.id.quickSideBarTipsView)

val SchoolListActivity.quickSideBarView: com.bigkoo.quicksidebar.QuickSideBarView
    get() = findViewCompat(R.id.quickSideBarView)

val SchoolListActivity.recyclerView: androidx.recyclerview.widget.RecyclerView
    get() = findViewCompat(R.id.recyclerView)

val WebViewLoginFragment.btn_back: androidx.appcompat.widget.AppCompatImageButton
    get() = findViewCompat(R.id.btn_back)

val WebViewLoginFragment.cg_old_qz: com.google.android.material.chip.ChipGroup
    get() = findViewCompat(R.id.cg_old_qz)

val WebViewLoginFragment.cg_qz: com.google.android.material.chip.ChipGroup
    get() = findViewCompat(R.id.cg_qz)

val WebViewLoginFragment.cg_zf: com.google.android.material.chip.ChipGroup
    get() = findViewCompat(R.id.cg_zf)

val WebViewLoginFragment.chip_mode: com.google.android.material.chip.Chip
    get() = findViewCompat(R.id.chip_mode)

val WebViewLoginFragment.chip_old_qz1: com.google.android.material.chip.Chip
    get() = findViewCompat(R.id.chip_old_qz1)

val WebViewLoginFragment.chip_old_qz2: com.google.android.material.chip.Chip
    get() = findViewCompat(R.id.chip_old_qz2)

val WebViewLoginFragment.chip_qz1: com.google.android.material.chip.Chip
    get() = findViewCompat(R.id.chip_qz1)

val WebViewLoginFragment.chip_qz2: com.google.android.material.chip.Chip
    get() = findViewCompat(R.id.chip_qz2)

val WebViewLoginFragment.chip_qz3: com.google.android.material.chip.Chip
    get() = findViewCompat(R.id.chip_qz3)

val WebViewLoginFragment.chip_qz4: com.google.android.material.chip.Chip
    get() = findViewCompat(R.id.chip_qz4)

val WebViewLoginFragment.chip_zf1: com.google.android.material.chip.Chip
    get() = findViewCompat(R.id.chip_zf1)

val WebViewLoginFragment.chip_zf2: com.google.android.material.chip.Chip
    get() = findViewCompat(R.id.chip_zf2)

val WebViewLoginFragment.chip_zoom: com.google.android.material.chip.Chip
    get() = findViewCompat(R.id.chip_zoom)

val WebViewLoginFragment.et_url: androidx.appcompat.widget.AppCompatEditText
    get() = findViewCompat(R.id.et_url)

val WebViewLoginFragment.fab_import: com.google.android.material.button.MaterialButton
    get() = findViewCompat(R.id.fab_import)

val WebViewLoginFragment.ll_error: androidx.appcompat.widget.LinearLayoutCompat
    get() = findViewCompat(R.id.ll_error)

val WebViewLoginFragment.pb_load: android.widget.ProgressBar
    get() = findViewCompat(R.id.pb_load)

val WebViewLoginFragment.tv_go: androidx.appcompat.widget.AppCompatTextView
    get() = findViewCompat(R.id.tv_go)

val WebViewLoginFragment.v_status: android.view.View
    get() = findViewCompat(R.id.v_status)

val WebViewLoginFragment.wv_course: android.webkit.WebView
    get() = findViewCompat(R.id.wv_course)

