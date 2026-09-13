package com.Tangle.timetable.utils
import android.util.Log

import android.content.Context
import androidx.core.content.edit
import com.Tangle.timetable.AppDatabase
import com.Tangle.timetable.bean.TableBean
import com.Tangle.timetable.bean.TimeDetailBean
import com.Tangle.timetable.bean.TimeTableBean

object UpdateUtils {

    private const val TAG = "UpdateUtils"

    @Throws(Exception::class)
    fun getVersionCode(context: Context): Int {
        val packageManager = context.packageManager
        //getPackageName()是你当前类的包名，0代表是获取版本信息
        val packInfo = packageManager.getPackageInfo(context.packageName, 0)
        return packInfo.versionCode
    }

    @Throws(Exception::class)
    fun getVersionName(context: Context): String {
        val packageManager = context.packageManager
        //getPackageName()是你当前类的包名，0代表是获取版本信息
        val packInfo = packageManager.getPackageInfo(context.packageName, 0)
        return packInfo.versionName
    }

    suspend fun tranOldData(context: Context) {
        if (context.getPrefer().getBoolean("has_intro", false) &&
                !context.getPrefer().getBoolean("has_adjust", false)) {
            val tableData = TableBean(
                    tableName = "",
                    itemHeight = context.getPrefer().getInt("item_height", 56),
                    maxWeek = context.getPrefer().getInt("sb_weeks", 30),
                    itemTextSize = context.getPrefer().getInt("sb_text_size", 12),
                    showOtherWeekCourse = context.getPrefer().getBoolean("s_show", false),
                    showTime = context.getPrefer().getBoolean("s_show_time_detail", false),
                    showSat = context.getPrefer().getBoolean("s_show_sat", true),
                    showSun = context.getPrefer().getBoolean("s_show_weekend", true),
                    sundayFirst = context.getPrefer().getBoolean("s_sunday_first", false),
                    nodes = context.getPrefer().getInt("classNum", 11),
                    itemAlpha = context.getPrefer().getInt("sb_alpha", 60),
                    background = context.getPrefer().getString(Const.KEY_OLD_VERSION_BG_URI, "")!!,
                    startDate = context.getPrefer().getString(Const.KEY_OLD_VERSION_TERM_START, "2019-02-25")!!,
                    widgetItemAlpha = context.getPrefer().getInt("sb_widget_alpha", 60),
                    widgetItemHeight = context.getPrefer().getInt("widget_item_height", 56),
                    widgetItemTextSize = context.getPrefer().getInt("sb_widget_text_size", 12),
                    type = 1,
                    id = 1)

            if (!context.getPrefer().getBoolean("s_stroke", true)) {
                tableData.strokeColor = 0x00ffffff
            }

            if (context.getPrefer().getBoolean("s_color", false)) {
                tableData.textColor = 0xff000000.toInt()
            }

            if (context.getPrefer().getBoolean("s_widget_color", false)) {
                tableData.widgetTextColor = 0xff000000.toInt()
            }

            val dataBase = AppDatabase.getDatabase(context)
            val tableDao = dataBase.tableDao()
            val timeDao = dataBase.timeDetailDao()
            val widgetDao = dataBase.appWidgetDao()

            try {
                tableDao.updateTable(tableData)
                widgetDao.updateFromOldVer()
                if (!context.getPrefer().getBoolean("isInitTimeTable", false)) {
                    val timeList = ArrayList<TimeDetailBean>().apply {
                        add(TimeDetailBean(1, "08:00", "08:45", 1))
                        add(TimeDetailBean(2, "09:00", "09:45", 1))
                        add(TimeDetailBean(3, "10:10", "10:55", 1))
                        add(TimeDetailBean(4, "11:10", "11:55", 1))
                        add(TimeDetailBean(5, "13:30", "14:15", 1))
                        add(TimeDetailBean(6, "14:30", "15:15", 1))
                        add(TimeDetailBean(7, "15:40", "16:25", 1))
                        add(TimeDetailBean(8, "16:40", "17:25", 1))
                        add(TimeDetailBean(9, "18:30", "19:15", 1))
                        add(TimeDetailBean(10, "19:30", "20:15", 1))
                        add(TimeDetailBean(11, "20:30", "21:15", 1))
                        add(TimeDetailBean(12, "00:00", "00:00", 1))
                        add(TimeDetailBean(13, "00:00", "00:00", 1))
                        add(TimeDetailBean(14, "00:00", "00:00", 1))
                        add(TimeDetailBean(15, "00:00", "00:00", 1))
                        add(TimeDetailBean(16, "00:00", "00:00", 1))
                        add(TimeDetailBean(17, "00:00", "00:00", 1))
                        add(TimeDetailBean(18, "00:00", "00:00", 1))
                        add(TimeDetailBean(19, "00:00", "00:00", 1))
                        add(TimeDetailBean(20, "00:00", "00:00", 1))
                        add(TimeDetailBean(21, "00:00", "00:00", 1))
                        add(TimeDetailBean(22, "00:00", "00:00", 1))
                        add(TimeDetailBean(23, "00:00", "00:00", 1))
                        add(TimeDetailBean(24, "00:00", "00:00", 1))
                        add(TimeDetailBean(25, "00:00", "00:00", 1))
                        add(TimeDetailBean(26, "00:00", "00:00", 1))
                        add(TimeDetailBean(27, "00:00", "00:00", 1))
                        add(TimeDetailBean(28, "00:00", "00:00", 1))
                        add(TimeDetailBean(29, "00:00", "00:00", 1))
                        add(TimeDetailBean(30, "00:00", "00:00", 1))
                    }
                    timeDao.insertTimeList(timeList)
                }

                context.getPrefer().edit {
                    remove("termStart")
                    remove("item_height")
                    remove("sb_weeks")
                    remove("sb_text_size")
                    remove("s_show")
                    remove("s_show_time_detail")
                    remove("s_show_sat")
                    remove("s_show_weekend")
                    remove("s_sunday_first")
                    remove("classNum")
                    remove("sb_alpha")
                    remove("pic_uri")
                    remove("sb_widget_alpha")
                    remove("widget_item_height")
                    remove("sb_widget_text_size")
                    remove("s_stroke")
                    remove("s_color")
                    remove("s_widget_color")
                }

                context.getPrefer().edit {
                    putBoolean(Const.KEY_HAS_ADJUST, true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "老版本数据迁移失败", e)
            }

        }

        if (!context.getPrefer().getBoolean("has_intro", false) &&
                !context.getPrefer().getBoolean("has_adjust", false)) {
            val tableData = TableBean(type = 1, id = 1, tableName = "")
            val dataBase = AppDatabase.getDatabase(context)
            val tableDao = dataBase.tableDao()
            val timeDao = dataBase.timeDetailDao()
            val timeTableDao = dataBase.timeTableDao()
            if (timeTableDao.getTimeTable(1) == null) {
                timeTableDao.insertTimeTable(TimeTableBean(id = 1, name = "默认"))
            }
            val timeList = ArrayList<TimeDetailBean>().apply {
                add(TimeDetailBean(1, "08:00", "08:45", 1))
                add(TimeDetailBean(2, "09:00", "09:45", 1))
                add(TimeDetailBean(3, "10:10", "10:55", 1))
                add(TimeDetailBean(4, "11:10", "11:55", 1))
                add(TimeDetailBean(5, "13:30", "14:15", 1))
                add(TimeDetailBean(6, "14:30", "15:15", 1))
                add(TimeDetailBean(7, "15:40", "16:25", 1))
                add(TimeDetailBean(8, "16:40", "17:25", 1))
                add(TimeDetailBean(9, "18:30", "19:15", 1))
                add(TimeDetailBean(10, "19:30", "20:15", 1))
                add(TimeDetailBean(11, "20:30", "21:15", 1))
                add(TimeDetailBean(12, "00:00", "00:00", 1))
                add(TimeDetailBean(13, "00:00", "00:00", 1))
                add(TimeDetailBean(14, "00:00", "00:00", 1))
                add(TimeDetailBean(15, "00:00", "00:00", 1))
                add(TimeDetailBean(16, "00:00", "00:00", 1))
                add(TimeDetailBean(17, "00:00", "00:00", 1))
                add(TimeDetailBean(18, "00:00", "00:00", 1))
                add(TimeDetailBean(19, "00:00", "00:00", 1))
                add(TimeDetailBean(20, "00:00", "00:00", 1))
                add(TimeDetailBean(21, "00:00", "00:00", 1))
                add(TimeDetailBean(22, "00:00", "00:00", 1))
                add(TimeDetailBean(23, "00:00", "00:00", 1))
                add(TimeDetailBean(24, "00:00", "00:00", 1))
                add(TimeDetailBean(25, "00:00", "00:00", 1))
                add(TimeDetailBean(26, "00:00", "00:00", 1))
                add(TimeDetailBean(27, "00:00", "00:00", 1))
                add(TimeDetailBean(28, "00:00", "00:00", 1))
                add(TimeDetailBean(29, "00:00", "00:00", 1))
                add(TimeDetailBean(30, "00:00", "00:00", 1))
            }
            try {
                timeDao.insertTimeList(timeList)
                tableDao.insertTable(tableData)
                context.getPrefer().edit {
                    putBoolean(Const.KEY_HAS_ADJUST, true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "初始化默认课表失败", e)
            }
        }
    }
}
