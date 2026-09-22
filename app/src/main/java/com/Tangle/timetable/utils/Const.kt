package com.Tangle.timetable.utils

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences

fun Context.getPrefer(name: String = "config"): SharedPreferences = getSharedPreferences(name, MODE_PRIVATE)

object Const {

    const val REQUEST_CODE_EXPORT = 100
    const val REQUEST_CODE_IMPORT = 101
    const val REQUEST_CODE_SCHEDULE_SETTING = 102
    const val REQUEST_CODE_EXPORT_ICS = 103
    const val REQUEST_CODE_IMPORT_FILE = 104
    const val REQUEST_CODE_IMPORT_HTML = 105
    const val REQUEST_CODE_IMPORT_CSV = 106
    const val REQUEST_CODE_CHOOSE_SCHOOL = 107
    const val REQUEST_CODE_ADD_COURSE = 108

    /** 申请日历读写权限（同步课表到系统日历用） */

    /** 图片导入课表：选择课表图片 */
    const val REQUEST_CODE_IMPORT_IMAGE = 110

    const val KEY_OLD_VERSION_COURSE = "course"
    const val KEY_OLD_VERSION_BG_URI = "pic_uri"
    const val KEY_OLD_VERSION_TERM_START = "termStart"
    const val KEY_HAS_ADJUST = "has_adjust"

    const val KEY_IMPORT_SCHOOL = "import_school"
    const val KEY_SCHOOL_URL = "school_url"
    const val KEY_DAY_NIGHT_THEME = "day_night_theme"
    const val KEY_HIDE_NAV_BAR = "hide_main_nav_bar"
    const val KEY_COURSE_REMIND = "course_reminder"
    const val KEY_REMINDER_ON_GOING = "reminder_on_going"
    const val KEY_SILENCE_REMINDER = "silence_reminder"
    const val KEY_DAY_WIDGET_COLOR = "s_colorful_day_widget"
    const val KEY_SHOW_EMPTY_VIEW = "show_empty_view"
    const val KEY_SHOW_SUDA_LIFE = "suda_life"
    const val KEY_THEME_COLOR = "nav_bar_color"
    const val KEY_REMINDER_TIME = "reminder_min"
    const val KEY_HAS_INTRO = "has_intro"
    const val KEY_SCHEDULE_PRE_LOAD = "schedule_pre_load"
    const val KEY_SCHEDULE_BLANK_AREA = "schedule_blank_area"
    const val KEY_SCHEDULE_DETAIL_TIME = "schedule_detail_time"
    const val KEY_SHOW_DASHED_GRID = "show_dashed_grid"

    /** 老库迁移备份（B1）：检测到 DB 1~6 并完成文件备份后置位，主界面据此弹一次性恢复提示 */
    const val KEY_DB_OLD_VERSION_DETECTED = "db_old_version_detected"
    /** 老库迁移备份文件路径（提示弹窗里展示给用户） */
    const val KEY_DB_BACKUP_PATH = "db_backup_path"
    /** 一次性迁移提示弹窗已确认（用户点过「知道了」后置位，之后不再弹） */
    const val KEY_DB_MIGRATED_V8_BACKUP = "db_migrated_v8_backup"

    /** 今日小部件「课程预告范围」：从今天起一共看几天，只允许 2 或 7（2 = 今天和明天）；未配置时默认 2（v158） */
    const val KEY_WIDGET_PREVIEW_DAYS = "widget_preview_days"

    /** 今日小部件课程排列：0 = 竖排列表（一行一门课），1 = 紧凑两列（半宽并排，一行两门课） */
    const val KEY_TODAY_CARD_LAYOUT = "today_card_layout"

    /** 生日提醒：月份 1-12；没有这个键（或不在范围内）表示未设置 */
    const val KEY_BIRTHDAY_MONTH = "birthday_month"

    /** 生日提醒：日 1-31 */
    const val KEY_BIRTHDAY_DAY = "birthday_day"

    /** 生日提醒：想对未来的自己/TA 说的话 */
    const val KEY_BIRTHDAY_TEXT = "birthday_text"
    const val KEY_HIDE_ENDED_COURSE = "hide_ended_course"
    const val KEY_PERMISSION_GUIDE_SHOWN = "permission_guide_shown"
    const val KEY_WIDGET_CACHE = "widget_today_cache"

    /** 上一次「同步到系统日历」选中的日历 id，-1 表示还没选过 */
    /** v132 起记忆「authority|id」组合键，避免不同日历库同 id 撞号写错日历 */

    /** 自动检查更新：上次自动检查的时间戳（6 小时内不重复查，省流量/避限流） */
    const val KEY_UPDATE_LAST_AUTO_CHECK = "update_last_auto_check"

    /** 自动检查更新：用户选择「跳过此版本」的 versionCode，该版本不再自动提示 */
    const val KEY_UPDATE_SKIP_VERSION = "update_skip_version"

}