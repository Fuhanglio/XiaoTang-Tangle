package com.Tangle.timetable.schedule

import android.animation.StateListAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet.PARENT_ID
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.setMargins
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import com.Tangle.timetable.R
import com.Tangle.timetable.base_view.Ui
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.ViewUtils
import com.Tangle.timetable.utils.ViewUtils.getStatusBarHeight
import com.Tangle.timetable.utils.getPrefer
import splitties.dimensions.dip
import splitties.dimensions.dp
import splitties.resources.colorSL
import splitties.resources.styledColor

class ScheduleActivityUI(override val ctx: Context) : Ui {

    private val statusBarMargin = getStatusBarHeight(ctx) + ctx.dip(8)
    private val outValue = TypedValue()

    val viewPager: ViewPager = ViewPager(ctx).apply {
        id = R.id.anko_vp_schedule
    }

    val bg = AppCompatImageView(ctx).apply {
        id = R.id.anko_iv_bg
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    val dateView = AppCompatTextView(ctx).apply {
        id = R.id.anko_tv_date
        gravity = Gravity.CENTER
        setTextColor(Color.BLACK)
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
    }

    val weekView = AppCompatTextView(ctx).apply {
        id = R.id.anko_tv_week
        setTextColor(Color.BLACK)
    }

    val weekDayView = AppCompatTextView(ctx).apply {
        id = R.id.anko_tv_weekday
        setTextColor(Color.BLACK)
    }

    

    

    

    

    

    val navBtn = AppCompatImageView(ctx).apply {
        id = R.id.anko_ib_nav
        setImageResource(R.drawable.ic_nav_menu)
        scaleType = ImageView.ScaleType.CENTER
        setBackgroundResource(outValue.resourceId)
        minimumWidth = ctx.dip(48)
        minimumHeight = ctx.dip(48)
    }

    val addBtn = AppCompatImageView(ctx).apply {
        id = R.id.anko_ib_add
        setImageResource(R.drawable.ic_add)
        scaleType = ImageView.ScaleType.CENTER
        setBackgroundResource(outValue.resourceId)
        minimumWidth = ctx.dip(44)
        minimumHeight = ctx.dip(44)
    }

    val importBtn = AppCompatImageView(ctx).apply {
        id = R.id.anko_ib_import
        setImageResource(R.drawable.ic_import_download)
        scaleType = ImageView.ScaleType.CENTER
        setBackgroundResource(outValue.resourceId)
        minimumWidth = ctx.dip(44)
        minimumHeight = ctx.dip(44)
    }

    val shareBtn = AppCompatImageView(ctx).apply {
        id = R.id.anko_ib_share
        setImageResource(R.drawable.ic_share)
        scaleType = ImageView.ScaleType.CENTER
        setBackgroundResource(outValue.resourceId)
        minimumWidth = ctx.dip(44)
        minimumHeight = ctx.dip(44)
    }

    val moreBtn = AppCompatImageView(ctx).apply {
        id = R.id.anko_ib_more
        setImageResource(R.drawable.ic_more_vert)
        scaleType = ImageView.ScaleType.CENTER
        setBackgroundResource(outValue.resourceId)
        minimumWidth = ctx.dip(44)
        minimumHeight = ctx.dip(44)
    }

    val content = ConstraintLayout(ctx).apply {
        id = R.id.anko_cl_schedule
        context.theme.resolveAttribute(R.attr.selectableItemBackgroundBorderless, outValue, true)
        addView(bg, ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT).apply {
            startToStart = PARENT_ID
            endToEnd = PARENT_ID
            topToTop = PARENT_ID
            bottomToBottom = PARENT_ID
        })

        addView(dateView, ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            startToStart = PARENT_ID
            topToTop = PARENT_ID
            bottomToBottom = R.id.anko_ib_nav
            // 与左侧汉堡图标（8dp 边距 + 32dp 宽 + 8dp 间隙）保持左对齐关系
            marginStart = dip(48)
            topMargin = statusBarMargin
        })

        addView(weekView, ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            startToStart = R.id.anko_tv_date
            topToBottom = R.id.anko_tv_date
            topMargin = dip(4)
        })

        addView(weekDayView, ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            startToEnd = R.id.anko_tv_week
            topToBottom = R.id.anko_tv_date
            topMargin = dip(4)
            marginStart = dip(8)
        })

        //导航按钮：左边距 8dp，避免汉堡图标贴屏幕左边缘
        addView(navBtn, ConstraintLayout.LayoutParams(dip(32), dip(32)).apply {
            topMargin = statusBarMargin
            startToStart = PARENT_ID
            marginStart = dip(8)
            topToTop = PARENT_ID
        })

        //添加按钮
        addView(addBtn, ConstraintLayout.LayoutParams(dip(32), dip(32)).apply {
            topMargin = statusBarMargin
            endToStart = R.id.anko_ib_import
            topToTop = PARENT_ID
        })

        //导入按钮
        addView(importBtn, ConstraintLayout.LayoutParams(dip(32), dip(32)).apply {
            topMargin = statusBarMargin
            endToStart = R.id.anko_ib_share
            topToTop = PARENT_ID
        })

        //分享按钮
        addView(shareBtn, ConstraintLayout.LayoutParams(dip(32), dip(32)).apply {
            topMargin = statusBarMargin
            endToStart = R.id.anko_ib_more
            topToTop = PARENT_ID
        })

        addView(moreBtn, ConstraintLayout.LayoutParams(dip(32), dip(32)).apply {
            topMargin = statusBarMargin
            marginEnd = dip(8)
            endToEnd = PARENT_ID
            topToTop = PARENT_ID
        })

        addView(viewPager, ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT).apply {
            topToBottom = R.id.anko_tv_week
            bottomToBottom = PARENT_ID
            startToStart = PARENT_ID
            endToEnd = PARENT_ID
        })

    }

    val navViewStart = NavigationView(ctx).apply {
        id = R.id.anko_nv
        setBackgroundColor(ctx.getColor(R.color.page_bg))
        fitsSystemWindows = false
        inflateHeaderView(R.layout.nav_header)
        inflateMenu(R.menu.main_navigation_menu)
        // 图标为自带柔和彩色块（样式里 itemIconTint=@null 保留原色），文字选中主色
        itemIconTintList = null
        itemTextColor = ctx.colorSL(R.color.nav_item_text)
        // 菜单项 padding
        setPadding(ctx.dip(8), 0, ctx.dip(8), ctx.dip(8))
    }

    val rvTableName = RecyclerView(ctx).apply {
        id = R.id.bottom_sheet_rv_table
        overScrollMode = View.OVER_SCROLL_NEVER
        layoutManager = LinearLayoutManager(context).apply {
            orientation = RecyclerView.HORIZONTAL
        }
    }

    val drawerLayout = DrawerLayout(ctx).apply {
        id = R.id.anko_drawer_layout
        addView(content, DrawerLayout.LayoutParams(DrawerLayout.LayoutParams.MATCH_PARENT,
                DrawerLayout.LayoutParams.MATCH_PARENT))

        addView(navViewStart, DrawerLayout.LayoutParams(DrawerLayout.LayoutParams.MATCH_PARENT,
                DrawerLayout.LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.START
        })
    }

    val changeWeekBtn = createTextButton().apply {
        id = R.id.bottom_sheet_change_week_btn
        text = "修改当前周"
        minWidth = 0
        minimumWidth = 0
        textSize = 12f
    }

    val createScheduleBtn = MaterialButton(ctx).apply {
        id = R.id.bottom_sheet_create_schedule_btn
        text = "新建课表"
        minWidth = 0
        minimumWidth = 0
        textSize = 12f
        setPadding(dip(12), 0, dip(12), 0)
        minimumHeight = dip(32)
        cornerRadius = dip(10)
        setTextColor(ctx.getColor(R.color.text_primary))
        backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFF2F2F7.toInt())
        elevation = 0f
        stateListAnimator = StateListAnimator()
    }

    val manageScheduleBtn = createTextButton().apply {
        id = R.id.bottom_sheet_manage_schedule_btn
        text = "管理"
        minWidth = 0
        minimumWidth = 0
        textSize = 12f
    }

    /** 周数按钮集合（手动维护选中态，替代 MaterialButtonToggleGroup 的统一圆角容器） */
    val weekButtons = mutableListOf<MaterialButton>()

    /** 周数被点击的回调（由 Activity 注入：切换 viewPager） */
    var onWeekSelected: ((Int) -> Unit)? = null

    /** 横向排布的周数容器：每个周数是独立胶囊，间距 6dp，不再是“一整条” */
    val weekToggleGroup = androidx.appcompat.widget.LinearLayoutCompat(ctx).apply {
        id = R.id.bottom_sheet_cg_week
        orientation = androidx.appcompat.widget.LinearLayoutCompat.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    /** 按最大周数重建周数胶囊：首个无左边距，其余 marginStart=6dp */
    fun rebuildWeekButtons(maxWeek: Int) {
        weekButtons.clear()
        weekToggleGroup.removeAllViews()
        for (i in 1..maxWeek) {
            val btn = createOutlineButton().apply {
                id = i
                text = i.toString()
                isChecked = false
                setOnClickListener {
                    weekButtons.forEach { b -> b.isChecked = b === this }
                    onWeekSelected?.invoke(id)
                }
            }
            // 宽度 wrap_content + minWidth 40dp，两位数也单行显示
            val lp = androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(
                    androidx.appcompat.widget.LinearLayoutCompat.LayoutParams.WRAP_CONTENT, ctx.dip(34))
            if (i > 1) lp.marginStart = ctx.dip(6)
            weekToggleGroup.addView(btn, lp)
            weekButtons.add(btn)
        }
    }

    /** 选中指定周（仅视觉态） */
    fun selectWeekButton(week: Int) {
        weekButtons.forEach { it.isChecked = it.id == week }
    }

    val weekScrollView = HorizontalScrollView(ctx).apply {
        id = R.id.bottom_sheet_sv_week
        overScrollMode = View.OVER_SCROLL_NEVER
        isHorizontalScrollBarEnabled = false
        addView(weekToggleGroup)
    }

    // 捷径：4 个等宽 #F2F2F7 圆角小卡（18dp 主色线性图标 + 12sp 文字）
    val timeBtn = createShortcutBlock(R.drawable.q_clock, "上课时间").apply {
        id = R.id.bottom_sheet_modify_time_btn
    }

    val changeBgBtn = createShortcutBlock(R.drawable.q_photo, "更换背景").apply {
        id = R.id.bottom_sheet_bg_btn
    }

    val courseBtn = createShortcutBlock(R.drawable.q_list, "已添课程").apply {
        id = R.id.bottom_sheet_check_course_btn
    }

    val qaBtn = createShortcutBlock(R.drawable.q_help, "常见问题").apply {
        id = R.id.bottom_sheet_question_btn
    }

    // 底部弹层拖拽条 36×4 #D1D1D6
    val dragHandle = View(ctx).apply {
        id = R.id.bottom_sheet_drag_handle
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dip(2).toFloat()
            setColor(0xFFD1D1D6.toInt())
        }
    }

    val cardContent = ConstraintLayout(ctx).apply {
        val space = dip(20)
        // 底部留 24dp，避免捷径按钮被弹层下边缘裁切
        setPadding(space, 0, space, dip(24))
        isMotionEventSplittingEnabled = false

        // 顶部居中拖拽条 36×4
        addView(dragHandle, ConstraintLayout.LayoutParams(dip(36), dip(4)).apply {
            startToStart = PARENT_ID
            endToEnd = PARENT_ID
            topToTop = PARENT_ID
            topMargin = dip(8)
        })

        addView(AppCompatTextView(context).apply {
            id = R.id.bottom_sheet_title_week
            text = "周数"
            textSize = 13f
            setTextColor(ctx.getColor(R.color.text_secondary))
        }, ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            startToStart = PARENT_ID
            topToBottom = R.id.bottom_sheet_drag_handle
            topMargin = dip(16)
        })
        addView(changeWeekBtn, ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            endToEnd = PARENT_ID
            topToTop = R.id.bottom_sheet_title_week
            bottomToBottom = R.id.bottom_sheet_title_week
        })
        addView(weekScrollView, ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            startToStart = PARENT_ID
            endToEnd = PARENT_ID
            topToBottom = R.id.bottom_sheet_title_week
            topMargin = dip(8)
        })
        addView(AppCompatTextView(context).apply {
            id = R.id.bottom_sheet_title_schedule
            text = "多课表"
            textSize = 13f
            setTextColor(ctx.getColor(R.color.text_secondary))
        }, ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            startToStart = PARENT_ID
            topToBottom = R.id.bottom_sheet_sv_week
            topMargin = dip(8)
        })
        addView(rvTableName, ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            startToStart = PARENT_ID
            endToEnd = PARENT_ID
            topToBottom = R.id.bottom_sheet_title_schedule
            topMargin = dip(16)
        })
        addView(manageScheduleBtn, ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            endToEnd = PARENT_ID
            topToTop = R.id.bottom_sheet_title_schedule
            bottomToBottom = R.id.bottom_sheet_title_schedule
        })
        addView(createScheduleBtn, ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            endToStart = R.id.bottom_sheet_manage_schedule_btn
            topToTop = R.id.bottom_sheet_title_schedule
            bottomToBottom = R.id.bottom_sheet_title_schedule
        })
        addView(AppCompatTextView(context).apply {
            id = R.id.bottom_sheet_title_shortcut
            text = "捷径"
            textSize = 13f
            setTextColor(ctx.getColor(R.color.text_secondary))
        }, ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            startToStart = PARENT_ID
            topToBottom = R.id.bottom_sheet_rv_table
            topMargin = dip(16)
        })

        // 4 个等宽捷径块（水平链 + MATCH_CONSTRAINT），底部各留 4dp 防止贴边
        addView(timeBtn, ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            startToStart = PARENT_ID
            topToBottom = R.id.bottom_sheet_title_shortcut
            topMargin = dip(8)
            bottomMargin = dip(4)
            endToStart = R.id.bottom_sheet_bg_btn
            marginEnd = dip(8)
        })
        addView(changeBgBtn, ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            startToEnd = R.id.bottom_sheet_modify_time_btn
            topToBottom = R.id.bottom_sheet_title_shortcut
            topMargin = dip(8)
            bottomMargin = dip(4)
            endToStart = R.id.bottom_sheet_check_course_btn
            marginStart = dip(8)
            marginEnd = dip(8)
        })
        addView(courseBtn, ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            startToEnd = R.id.bottom_sheet_bg_btn
            topToBottom = R.id.bottom_sheet_title_shortcut
            topMargin = dip(8)
            bottomMargin = dip(4)
            endToStart = R.id.bottom_sheet_question_btn
            marginStart = dip(8)
            marginEnd = dip(8)
        })
        addView(qaBtn, ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            startToEnd = R.id.bottom_sheet_check_course_btn
            topToBottom = R.id.bottom_sheet_title_shortcut
            topMargin = dip(8)
            bottomMargin = dip(4)
            endToEnd = PARENT_ID
            marginStart = dip(8)
        })
    }

    val bottomSheet = FrameLayout(ctx).apply {
        addView(MaterialCardView(context).apply {
            setCardBackgroundColor(styledColor(R.attr.colorSurface))
            radius = dip(24).toFloat()
            cardElevation = dp(6)
            setContentPadding(0, 0, 0, dip(8))
            addView(cardContent, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
            setMargins(dip(12))
            if (context.getPrefer().getBoolean(Const.KEY_HIDE_NAV_BAR, false)) {
                bottomMargin = dip(12) + ViewUtils.getVirtualBarHeight(ctx)
            }
        })
    }

    override val root = CoordinatorLayout(ctx).apply {

        addView(drawerLayout, CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.MATCH_PARENT)
        )

        addView(bottomSheet, CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                ViewUtils.getScreenInfo(context)[1]).apply {
            behavior = BottomSheetBehavior<FrameLayout>(ctx, null).apply {
                isHideable = true
                peekHeight = 0
            }
        })

    }

    fun createTextButton() = MaterialButton(ctx).apply {
        setTextColor(ctx.getColor(R.color.colorPrimary))
        val space = dip(8)
        setPadding(space, 0, space, 0)
        backgroundTintList = android.content.res.ColorStateList.valueOf(0x00000000)
        rippleColor = colorSL(R.color.mtrl_btn_text_btn_ripple_color)
        elevation = 0f
        stateListAnimator = StateListAnimator()
        cornerRadius = dip(12)
    }

    /** 周数胶囊：高34 圆角999，未选 #F2F2F7 底 #3C3C43 字，选中主色底白字 */
    fun createOutlineButton() = MaterialButton(ctx).apply {
        // 强制单行：两位数（10~20）不换行
        isSingleLine = true
        maxLines = 1
        ellipsize = null
        includeFontPadding = false
        gravity = Gravity.CENTER
        textSize = 13f
        isAllCaps = false
        isCheckable = true
        minWidth = ctx.dip(40)
        minimumWidth = ctx.dip(40)
        minimumHeight = ctx.dip(34)
        minHeight = ctx.dip(34)
        cornerRadius = dip(999)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        backgroundTintList = android.content.res.ColorStateList(states,
                intArrayOf(ctx.getColor(R.color.colorPrimary), 0xFFF2F2F7.toInt()))
        setTextColor(android.content.res.ColorStateList(states,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFF3C3C43.toInt())))
        setPadding(ctx.dip(10), 0, ctx.dip(10), 0)
        minimumHeight = ctx.dip(34)
        minHeight = ctx.dip(34)
        elevation = 0f
        stateListAnimator = StateListAnimator()
    }

    /** 捷径块：#F2F2F7 圆角14 小卡，内含 18dp 主色线性图标 + 12sp 文字（id 由调用方赋值） */
    fun createShortcutBlock(iconRes: Int, label: String): androidx.appcompat.widget.LinearLayoutCompat {
        return androidx.appcompat.widget.LinearLayoutCompat(ctx).apply {
            orientation = androidx.appcompat.widget.LinearLayoutCompat.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            val content = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dip(14).toFloat()
                setColor(0xFFF2F2F7.toInt())
            }
            val rippleColor = android.content.res.ColorStateList.valueOf(
                    (ctx.getColor(R.color.colorPrimary) and 0x00FFFFFF) or 0x1E000000)
            background = android.graphics.drawable.RippleDrawable(rippleColor, content, null)
            val icon = androidx.appcompat.widget.AppCompatImageView(ctx).apply {
                setImageResource(iconRes)
                setColorFilter(ctx.getColor(R.color.colorPrimary))
                scaleType = ImageView.ScaleType.CENTER
            }
            addView(icon, androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(dip(18), dip(18)).apply {
                bottomMargin = dip(4)
            })
            addView(androidx.appcompat.widget.AppCompatTextView(ctx).apply {
                text = label
                textSize = 12f
                setTextColor(ctx.getColor(R.color.text_primary))
            }, androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(
                    androidx.appcompat.widget.LinearLayoutCompat.LayoutParams.WRAP_CONTENT,
                    androidx.appcompat.widget.LinearLayoutCompat.LayoutParams.WRAP_CONTENT))
            setPadding(0, dip(12), 0, dip(12))
        }
    }

}
