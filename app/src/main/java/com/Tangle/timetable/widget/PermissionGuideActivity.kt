package com.Tangle.timetable.widget

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.edit
import com.Tangle.timetable.R
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.ThemeManager
import com.Tangle.timetable.utils.getPrefer
import es.dmoral.toasty.Toasty
import splitties.dimensions.dip

/**
 * 权限设置引导页：自启动、电池优化白名单、通知权限、精确闹钟。
 * 用户开完返回本页会自动检查，全部就绪后可进入；也可“稍后再说”。
 */
class PermissionGuideActivity : AppCompatActivity() {

    private lateinit var rootView: LinearLayoutCompat
    private lateinit var scrollContent: LinearLayoutCompat
    private lateinit var bottomArea: LinearLayoutCompat
    private lateinit var statusView: AppCompatTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 可滚动区内容：标题 + 状态文字 + 权限项（超高时滚动）
        scrollContent = LinearLayoutCompat(this).apply {
            orientation = LinearLayoutCompat.VERTICAL
            setPadding(dip(20), dip(24), dip(20), dip(8))
        }

        scrollContent.addView(AppCompatTextView(this).apply {
            text = "为保证桌面小部件能准时更新\n请开启以下权限"
            setTextColor(getColor(com.Tangle.timetable.R.color.text_primary))
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dip(6) })

        statusView = AppCompatTextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
        }
        scrollContent.addView(statusView, LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dip(12) })

        val scrollView = android.widget.ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(scrollContent, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        // 底部固定区：始终可见的“完成/稍后再说”按钮
        bottomArea = LinearLayoutCompat(this).apply {
            orientation = LinearLayoutCompat.VERTICAL
            setPadding(dip(20), dip(12), dip(20), dip(24))
        }

        rootView = LinearLayoutCompat(this).apply {
            orientation = LinearLayoutCompat.VERTICAL
            fitsSystemWindows = true
            addView(scrollView, LinearLayoutCompat.LayoutParams(
                    LinearLayoutCompat.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
            addView(bottomArea, LinearLayoutCompat.LayoutParams(
                    LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                    LinearLayoutCompat.LayoutParams.WRAP_CONTENT))
        }

        // 手势条/导航栏高度补到底部，防止按钮被系统栏挡住
        rootView.setOnApplyWindowInsetsListener { _, insets ->
            val bottom = insets.systemWindowInsetBottom
            bottomArea.setPadding(bottomArea.paddingLeft, bottomArea.paddingTop,
                    bottomArea.paddingRight, bottom + dip(24))
            insets
        }

        setContentView(rootView)
    }

    override fun onResume() {
        super.onResume()
        rebuild()
    }

    private fun isBatteryWhitelisted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return runCatching { pm.isIgnoringBatteryOptimizations(packageName) }.getOrDefault(false)
    }

    private fun isNotificationEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        return nm.areNotificationsEnabled()
    }

    private fun isExactAlarmOk(): Boolean = WidgetScheduler.canScheduleExact(this)

    private fun rebuild() {
        // 移除之前的权限条目（保留标题和状态）
        while (scrollContent.childCount > 2) scrollContent.removeViewAt(2)

        val readyText = "✓ 已开启"
        val notReadyText = "未开启"

        addRow("自启动（重要）",
                "在系统“自启动管理”里允许本应用自启动，重启手机后小部件才能自动恢复更新",
                R.drawable.p_power, R.color.icon_blue,
                /*done=*/false,
                readyText, notReadyText) { openAutoStart() }
        addRow("电池优化（不优化）",
                "允许应用在后台运行，Doze 低电耗模式下也能按时刷新小部件",
                R.drawable.p_battery, R.color.icon_green,
                isBatteryWhitelisted(), readyText, notReadyText) { openBatteryOptimization() }
        addRow("通知权限",
                "课前提醒需要通知权限，才能在锁屏/通知栏提醒上课",
                R.drawable.s_bell, R.color.icon_pink,
                isNotificationEnabled(), readyText, notReadyText) { openNotificationSetting() }
        if (Build.VERSION.SDK_INT >= 31) {   // Build.VERSION_CODES.S
            addRow("精确闹钟（Android 12+）",
                    "允许精确闹钟，小部件在每节课开始/结束时准时刷新；未授予时自动降级为约每30分钟兜底刷新",
                    R.drawable.s_alarm, R.color.icon_teal,
                    isExactAlarmOk(), readyText, notReadyText) { openExactAlarmSetting() }
        }

        val allSet = isBatteryWhitelisted() && isNotificationEnabled() &&
                (Build.VERSION.SDK_INT < 31 || isExactAlarmOk())
        statusView.text = if (allSet) "关键权限已全部就绪 ✓" else "请逐项开启，完成后会自动识别"
        statusView.setTextColor(if (allSet) getColor(com.Tangle.timetable.R.color.colorPrimary)
        else getColor(com.Tangle.timetable.R.color.warn_orange))

        // 底部按钮固定在滚动区外，始终可见：主色实心 iOS 风格
        bottomArea.removeAllViews()
        bottomArea.addView(makeButton(if (allSet) "完成" else "稍后再说", !allSet) {
            getPrefer().edit { putBoolean(Const.KEY_PERMISSION_GUIDE_SHOWN, true) }
            if (allSet) {
                WidgetScheduler.scheduleAll(this)
            }
            finish()
        }, LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.MATCH_PARENT, dip(50)))
    }

    private fun addRow(title: String, desc: String, iconRes: Int, blockColorRes: Int, done: Boolean,
                       readyText: String, notReadyText: String, onClick: () -> Unit) {
        // 白色圆角 16 卡片 + 极淡阴影
        val item = LinearLayoutCompat(this).apply {
            orientation = LinearLayoutCompat.VERTICAL
            background = GradientDrawableProxy.cardBg(getColor(com.Tangle.timetable.R.color.card_bg))
            setPadding(dip(14), dip(14), dip(14), dip(14))
            elevation = dip(1).toFloat()
        }
        val titleRow = LinearLayoutCompat(this).apply {
            orientation = LinearLayoutCompat.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // 左侧 36dp 柔和彩色图标块 + 白色线性图标
        titleRow.addView(LinearLayoutCompat(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dip(10).toFloat()
                setColor(getColor(blockColorRes))
            }
            gravity = Gravity.CENTER
            addView(androidx.appcompat.widget.AppCompatImageView(this@PermissionGuideActivity).apply {
                setImageResource(iconRes)
            }, LinearLayoutCompat.LayoutParams(dip(20), dip(20)))
        }, LinearLayoutCompat.LayoutParams(dip(36), dip(36)).apply { marginEnd = dip(12) })

        titleRow.addView(AppCompatTextView(this).apply {
            text = title
            setTextColor(getColor(com.Tangle.timetable.R.color.text_primary))
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayoutCompat.LayoutParams(0, LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply { weight = 1f })

        // 右上状态胶囊
        titleRow.addView(AppCompatTextView(this).apply {
            text = if (done) readyText else notReadyText
            textSize = 12f
            val fg = if (done) getColor(com.Tangle.timetable.R.color.switch_on)
            else getColor(com.Tangle.timetable.R.color.warn_orange)
            setTextColor(fg)
            setPadding(dip(10), dip(4), dip(10), dip(4))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dip(11).toFloat()
                setColor((fg and 0x00FFFFFF) or 0x1E000000)
            }
        })
        item.addView(titleRow)
        item.addView(AppCompatTextView(this).apply {
            text = desc
            setTextColor(getColor(com.Tangle.timetable.R.color.text_secondary))
            textSize = 12f
            setLineSpacing(2f, 1f)
        }, LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dip(10)
            marginStart = dip(48)
        })
        item.addView(makeButton(if (done) "重新设置" else "去开启", false) { onClick() },
                LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.MATCH_PARENT, dip(44))
                        .apply {
                            topMargin = dip(12)
                            marginStart = dip(48)
                        })

        scrollContent.addView(item, LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                LinearLayoutCompat.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dip(10)
        })
    }

    private fun makeButton(text: String, primary: Boolean, onClick: () -> Unit): AppCompatTextView {
        return AppCompatTextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            val accent = getColor(com.Tangle.timetable.R.color.colorPrimary)
            setTextColor(if (primary) android.graphics.Color.WHITE else accent)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dip(14).toFloat()
                setColor(if (primary) accent else (accent and 0x00FFFFFF) or 0x14000000)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun openBatteryOptimization() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                Toasty.info(this, "请在系统设置中手动关闭对本应用的电池优化").show()
            }
        }
    }

    private fun openNotificationSetting() {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (e2: Exception) {
            }
        }
    }

    private fun openExactAlarmSetting() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                // Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM（API31，用常量字符串兼容 compileSdk29）
                val intent = Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM").apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }
    }

    /** 自启动没有统一入口，打开应用详情页并提示用户手动开启 */
    private fun openAutoStart() {
        try {
            Toasty.info(this, "请在打开的页面中找到「自启动」并允许").show()
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (e: Exception) {
        }
    }

    private object GradientDrawableProxy {
        /** 权限卡：白底圆角16（深色 #1C1C1E 自动适配），颜色由调用方按当前昼夜模式解析 */
        fun cardBg(bgColor: Int): android.graphics.drawable.GradientDrawable =
                android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 16f
                    setColor(bgColor)
                }
    }
}
