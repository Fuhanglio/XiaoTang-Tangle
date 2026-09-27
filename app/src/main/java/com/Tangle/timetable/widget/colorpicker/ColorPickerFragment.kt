package com.Tangle.timetable.widget.colorpicker

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.annotation.ColorInt
import androidx.fragment.app.BaseDialogFragment
import androidx.fragment.app.FragmentActivity
import com.Tangle.timetable.R
import com.google.android.material.textfield.TextInputEditText
import splitties.resources.color

class ColorPickerFragment : BaseDialogFragment(), ColorPickerView.OnColorChangedListener, TextWatcher {

    @ColorInt
    private var color: Int = 0
    private var showAlphaSlider = false
    private var fromEditText = false
    private var dialogId: Int = 0

    /**
     * W1-06：把三个高频访问的视图缓存到字段。
     *
     * 本类原先用的是同包 `SynthViewsCompat.kt` 生成的只读属性 —— 它们**没有缓存**，
     * 每次读取都是一次全树 `findViewById`（且 `view` 为 null 时会直接抛 IllegalStateException）。
     * 而 `onColorChanged` 是取色盘**每帧**触发的回调：
     *   `et_color` 取值 3 次 + `v_color` 1 次（`setHex` 内再 1 次）≈ **每帧 5 次全树查找**；
     *   `et_color.setText()` 又会反向触发 `afterTextChanged`（再 3 次遍历）。
     * 60fps 下就是 300+ 次/秒，拖拽期间主线程被拖慢会直接掉帧。
     * 缓存后：`onColorChanged` 内 `findViewById` 次数 = 0。
     */
    private var etColor: TextInputEditText? = null
    private var cpvColor: ColorPickerView? = null
    private var vColor: View? = null

    override val layoutId: Int
        get() = R.layout.fragment_color_picker

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        showAlphaSlider = arguments!!.getBoolean("alpha")
        dialogId = arguments!!.getInt("id")
        color = savedInstanceState?.getInt("color") ?: arguments!!.getInt("color")

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("color", color)
        super.onSaveInstanceState(outState)
    }

    /**
     * W1-06：视图销毁时释放缓存引用。
     * 取色盘与输入框的监听器是注册在**视图**上的，视图销毁后仍有极小的可能收到一次回调
     * （例如 dismiss 动画期间），那时若还持有旧引用、又已经过时，就会操作到已销毁的视图。
     */
    override fun onDestroyView() {
        etColor = null
        cpvColor = null
        vColor = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // W1-06：只在这里查一次，之后全部走缓存字段
        etColor = view.findViewById(R.id.et_color)
        cpvColor = view.findViewById(R.id.cpv_color)
        vColor = view.findViewById(R.id.v_color)

        cpvColor!!.setAlphaSliderVisible(showAlphaSlider)
        cpvColor!!.setColor(color, true)
        cpvColor!!.setOnColorChangedListener(this)

        if (!showAlphaSlider) {
            etColor!!.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(6))
        }

        view.setOnTouchListener { v, _ ->
            if (v != etColor!! && etColor!!.hasFocus()) {
                etColor!!.clearFocus()
                val imm = activity!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(etColor!!.windowToken, 0)
                etColor!!.clearFocus()
                return@setOnTouchListener true
            }
            false
        }

        setHex(color)
        vColor!!.setBackgroundColor(color)

        etColor!!.addTextChangedListener(this)

        etColor!!.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val imm = activity!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(etColor!!, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        btn_save.setOnClickListener {
            if (activity is ColorPickerDialogListener) {
                (activity as ColorPickerDialogListener).onColorSelected(dialogId, color)
                dismiss()
            } else {
                throw IllegalStateException("The activity must implement ColorPickerDialogListener")
            }
        }

        btn_cancel.setOnClickListener {
            dismiss()
        }
    }

    private fun setHex(color: Int) {
        if (showAlphaSlider) {
            etColor!!.setText(String.format("%08X", color))
        } else {
            etColor!!.setText(String.format("%06X", 0xFFFFFF and color))
        }
    }

    override fun onColorChanged(newColor: Int) {
        color = newColor
        // W1-06：视图销毁后回调仍可能到达（取色盘的回调不受 Fragment 视图生命周期约束），必须早退。
        // 否则 this 三个 `!!` 会直接 NPE —— 那是比原来的 IllegalStateException 更糟的结果。
        if (etColor == null || vColor == null) return
        if (!fromEditText) {
            setHex(newColor)
            if (etColor!!.hasFocus()) {
                val imm = activity!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(etColor!!.windowToken, 0)
                etColor!!.clearFocus()
            }
        }
        fromEditText = false
        vColor!!.setBackgroundColor(newColor)
    }

    override fun afterTextChanged(s: Editable?) {
        // W1-06：同上，视图已销毁直接返回
        if (etColor == null || cpvColor == null) return
        if (etColor!!.isFocused) {
            val color = try {
                parseColorString(s.toString())
            } catch (e: Exception) {
                color(R.color.colorAccent)
            }
            if (color != cpvColor!!.color) {
                fromEditText = true
                cpvColor!!.setColor(color, true)
            }
        }
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    @Throws(NumberFormatException::class)
    private fun parseColorString(color: String): Int {
        var colorString = color
        val a: Int
        var r: Int
        val g: Int
        var b = 0
        if (colorString.startsWith("#")) {
            colorString = colorString.substring(1)
        }
        when {
            colorString.isEmpty() -> {
                r = 0
                a = 255
                g = 0
            }
            colorString.length <= 2 -> {
                a = 255
                r = 0
                b = Integer.parseInt(colorString, 16)
                g = 0
            }
            colorString.length == 3 -> {
                a = 255
                r = Integer.parseInt(colorString.substring(0, 1), 16)
                g = Integer.parseInt(colorString.substring(1, 2), 16)
                b = Integer.parseInt(colorString.substring(2, 3), 16)
            }
            colorString.length == 4 -> {
                a = 255
                r = Integer.parseInt(colorString.substring(0, 2), 16)
                g = r
                r = 0
                b = Integer.parseInt(colorString.substring(2, 4), 16)
            }
            colorString.length == 5 -> {
                a = 255
                r = Integer.parseInt(colorString.substring(0, 1), 16)
                g = Integer.parseInt(colorString.substring(1, 3), 16)
                b = Integer.parseInt(colorString.substring(3, 5), 16)
            }
            colorString.length == 6 -> {
                a = 255
                r = Integer.parseInt(colorString.substring(0, 2), 16)
                g = Integer.parseInt(colorString.substring(2, 4), 16)
                b = Integer.parseInt(colorString.substring(4, 6), 16)
            }
            colorString.length == 7 -> {
                a = Integer.parseInt(colorString.substring(0, 1), 16)
                r = Integer.parseInt(colorString.substring(1, 3), 16)
                g = Integer.parseInt(colorString.substring(3, 5), 16)
                b = Integer.parseInt(colorString.substring(5, 7), 16)
            }
            colorString.length == 8 -> {
                a = Integer.parseInt(colorString.substring(0, 2), 16)
                r = Integer.parseInt(colorString.substring(2, 4), 16)
                g = Integer.parseInt(colorString.substring(4, 6), 16)
                b = Integer.parseInt(colorString.substring(6, 8), 16)
            }
            else -> {
                b = -1
                g = -1
                r = -1
                a = -1
            }
        }
        return Color.argb(a, r, g, b)
    }

    companion object {
        fun newBuilder(): Builder {
            return Builder()
        }
    }

    interface ColorPickerDialogListener {
        fun onColorSelected(dialogId: Int, @ColorInt color: Int)
    }

    class Builder internal constructor() {

        @ColorInt
        private var color = Color.BLACK
        private var dialogId = 0
        private var showAlphaSlider = false

        fun setColor(color: Int): Builder {
            this.color = color
            return this
        }

        fun setShowAlphaSlider(showAlphaSlider: Boolean): Builder {
            this.showAlphaSlider = showAlphaSlider
            return this
        }

        fun setDialogId(dialogId: Int): Builder {
            this.dialogId = dialogId
            return this
        }

        /**
         * Create the [ColorPickerDialog] instance.
         *
         * @return A new [ColorPickerDialog].
         * @see .show
         */
        fun create(): ColorPickerFragment {
            val dialog = ColorPickerFragment()
            val args = Bundle()
            args.putInt("color", color)
            args.putBoolean("alpha", showAlphaSlider)
            args.putInt("id", dialogId)
            dialog.arguments = args
            return dialog
        }

        fun show(activity: FragmentActivity) {
            create().show(activity.supportFragmentManager, "color-picker-dialog")
        }
    }
}
