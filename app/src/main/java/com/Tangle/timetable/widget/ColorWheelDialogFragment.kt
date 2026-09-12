package com.Tangle.timetable.widget

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.fragment.app.DialogFragment
import com.Tangle.timetable.utils.ThemeManager
import es.dmoral.toasty.Toasty
import splitties.dimensions.dip

/**
 * 圆盘调色板对话框：
 * - 圆盘色轮 + 透明度滑块 + 中心颜色预览；
 * - 十六进制色号输入/复制（支持 #RGB / #RRGGBB / #AARRGGBB）；
 * - 输入非法时标红、不应用；确认 / 取消按钮。
 */
class ColorWheelDialogFragment : DialogFragment() {

    private var initColor: Int = Color.MAGENTA
    var onConfirmed: ((Int) -> Unit)? = null

    private lateinit var wheel: ColorWheelView
    private lateinit var alphaSlider: AlphaSliderView
    private lateinit var preview: View
    private lateinit var hexEdit: EditText

    private var fromHexEdit = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        initColor = arguments?.getInt(ARG_COLOR) ?: Color.MAGENTA
        val act = requireActivity()
        val dialog = Dialog(act)

        val root = LinearLayoutCompat(act).apply {
            orientation = LinearLayoutCompat.VERTICAL
            setPadding(act.dip(20), act.dip(20), act.dip(20), act.dip(12))
            setBackgroundColor(surfaceColor(act))
        }

        // 标题
        root.addView(AppCompatTextView(act).apply {
            text = "自定义颜色"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ThemeManager.getColor(act, ThemeManager.TEXT))
        }, LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                LinearLayoutCompat.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = act.dip(8) })

        // 圆盘色轮
        wheel = ColorWheelView(act).apply {
            setColor(initColor)
        }
        root.addView(wheel, LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                act.dip(260)
        ).apply { bottomMargin = act.dip(8) })

        // 透明度滑块
        alphaSlider = AlphaSliderView(act).apply {
            setColor(initColor)
            setAlpha(Color.alpha(initColor))
        }
        root.addView(alphaSlider, LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                act.dip(36)
        ).apply { bottomMargin = act.dip(12) })

        // 色号行：预览圆点 + 输入框 + 复制按钮
        val hexRow = LinearLayoutCompat(act).apply {
            orientation = LinearLayoutCompat.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        preview = View(act).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(initColor)
                setStroke(act.dip(1), 0x66000000)
            }
        }
        hexRow.addView(preview, LinearLayoutCompat.LayoutParams(act.dip(32), act.dip(32)).apply {
            marginEnd = act.dip(10)
        })

        hexEdit = EditText(act).apply {
            setText(ThemeManager.toHex(initColor))
            hint = "#RRGGBB 或 #AARRGGBB"
            inputType = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_CLASS_TEXT
            setSingleLine()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(ThemeManager.getColor(act, ThemeManager.TEXT))
            setHintTextColor(0x80808080.toInt())
            setPadding(act.dip(10), act.dip(6), act.dip(10), act.dip(6))
            background = GradientDrawable().apply {
                cornerRadius = act.dip(8).toFloat()
                setColor(0x14000000)
            }
        }
        hexRow.addView(hexEdit, LinearLayoutCompat.LayoutParams(
                0, LinearLayoutCompat.LayoutParams.WRAP_CONTENT
        ).apply {
            weight = 1f
            marginEnd = act.dip(10)
        })

        val copyBtn = makeButton(act, "复制", false)
        hexRow.addView(copyBtn, LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.WRAP_CONTENT,
                act.dip(40)
        ))
        root.addView(hexRow, LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                LinearLayoutCompat.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = act.dip(12) })

        // 按钮行
        val btnRow = LinearLayoutCompat(act).apply {
            orientation = LinearLayoutCompat.HORIZONTAL
        }
        val cancelBtn = makeButton(act, "取消", false)
        val okBtn = makeButton(act, "确定", true)
        btnRow.addView(cancelBtn, LinearLayoutCompat.LayoutParams(0, act.dip(42)).apply {
            weight = 1f
            marginEnd = act.dip(8)
        })
        btnRow.addView(okBtn, LinearLayoutCompat.LayoutParams(0, act.dip(42)).apply {
            weight = 1f
        })
        root.addView(btnRow, LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                LinearLayoutCompat.LayoutParams.WRAP_CONTENT
        ))

        // ===== 事件联动 =====
        wheel.onColorChanged = { c ->
            refreshFromWheel(c)
        }
        alphaSlider.onAlphaChanged = { a ->
            val hsv = FloatArray(3)
            Color.colorToHSV(wheel.currentColor, hsv)
            wheel.setColor(Color.HSVToColor(a, hsv))
        }
        hexEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!hexEdit.isFocused) return
                val parsed = ThemeManager.parseHex(s?.toString())
                if (parsed != null) {
                    fromHexEdit = true
                    hexEdit.setTextColor(ThemeManager.getColor(act, ThemeManager.TEXT))
                    wheel.setColor(parsed)
                    alphaSlider.setColor(parsed)
                    alphaSlider.setAlpha(Color.alpha(parsed))
                    (preview.background as? GradientDrawable)?.setColor(parsed)
                } else if (!s.isNullOrBlank()) {
                    hexEdit.setTextColor(Color.RED)  // 格式错误标红，不应用
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        copyBtn.setOnClickListener {
            val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("color", ThemeManager.toHex(wheel.currentColor)))
            Toasty.success(act.applicationContext, "已复制色号").show()
        }
        cancelBtn.setOnClickListener { dismiss() }
        okBtn.setOnClickListener {
            onConfirmed?.invoke(wheel.currentColor)
            dismiss()
        }

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        // 宽度自适应屏幕，两侧留边
        val margin = requireActivity().dip(24)
        dialog?.window?.setLayout(
                resources.displayMetrics.widthPixels - margin * 2,
                ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun refreshFromWheel(c: Int) {
        (preview.background as? GradientDrawable)?.setColor(c)
        alphaSlider.setColor(c)
        if (!fromHexEdit && !hexEdit.isFocused) {
            hexEdit.setText(ThemeManager.toHex(c))
            hexEdit.setTextColor(ThemeManager.getColor(requireContext(), ThemeManager.TEXT))
        }
        fromHexEdit = false
    }

    private fun surfaceColor(context: Context): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(android.R.attr.colorBackground, tv, true)
        return if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            tv.data
        } else {
            Color.WHITE
        }
    }

    private fun makeButton(context: Context, text: String, primary: Boolean): AppCompatTextView {
        val accent = ThemeManager.getColor(context, ThemeManager.PRIMARY)
        return AppCompatTextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = context.dip(8).toFloat()
                if (primary) {
                    setColor(accent)
                } else {
                    setColor(0x14000000)
                }
            }
            setTextColor(if (primary) Color.WHITE else ThemeManager.getColor(context, ThemeManager.TEXT))
            isClickable = true
            isFocusable = true
        }
    }

    companion object {
        private const val ARG_COLOR = "arg_color"

        fun newInstance(initialColor: Int, onConfirmed: (Int) -> Unit): ColorWheelDialogFragment {
            return ColorWheelDialogFragment().apply {
                arguments = Bundle().apply { putInt(ARG_COLOR, initialColor) }
                this.onConfirmed = onConfirmed
            }
        }
    }
}
