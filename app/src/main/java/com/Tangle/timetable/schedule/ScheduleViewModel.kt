package com.Tangle.timetable.schedule

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import biweekly.Biweekly
import biweekly.ICalVersion
import biweekly.ICalendar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.Tangle.timetable.App
import com.Tangle.timetable.AppDatabase
import com.Tangle.timetable.R
import com.Tangle.timetable.bean.*
import com.Tangle.timetable.schedule_import.Common
import com.Tangle.timetable.utils.Const
import com.Tangle.timetable.utils.CourseUtils
import com.Tangle.timetable.utils.ICalUtils
import com.Tangle.timetable.utils.getPrefer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "ScheduleViewModel"


class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val dataBase = AppDatabase.getDatabase(application)
    private val courseDao = dataBase.courseDao()
    private val tableDao = dataBase.tableDao()
    private val widgetDao = dataBase.appWidgetDao()
    private val timeTableDao = dataBase.timeTableDao()
    private val timeDao = dataBase.timeDetailDao()

    lateinit var table: TableBean
    lateinit var timeList: List<TimeDetailBean>
    var selectedWeek = 1
    val marTop = application.resources.getDimensionPixelSize(R.dimen.weekItemMarTop)
    var itemHeight = 0
    var alphaInt = 225
    val tableSelectList = arrayListOf<TableSelectBean>()
    val allCourseList = Array(7) { MutableLiveData<List<CourseBean>>() }
    val daysArray = arrayOf("日", "一", "二", "三", "四", "五", "六", "日")
    var currentWeek = 1

    fun initTableSelectList(): LiveData<List<TableSelectBean>> {
        return tableDao.getTableSelectListLiveData()
    }

    fun getMultiCourse(week: Int, day: Int, startNode: Int): List<CourseBean> {
        return allCourseList.getOrNull(day - 1)?.value?.filter {
            it.inWeek(week) && it.startNode == startNode
        } ?: emptyList()
    }

    suspend fun getDefaultTable(): TableBean? {
        return tableDao.getDefaultTable()
    }

    suspend fun getTimeList(timeTableId: Int): List<TimeDetailBean> {
        return timeDao.getTimeList(timeTableId)
    }

    suspend fun addBlankTable(tableName: String) {
        tableDao.insertTable(TableBean(id = 0, tableName = tableName))
    }

    suspend fun changeDefaultTable(id: Int) {
        tableDao.changeDefaultTable(table.id, id)
    }

    suspend fun getScheduleWidgetIds(): List<AppWidgetBean> {
        return widgetDao.getWidgetsByBaseType(0)
    }

    fun getRawCourseByDay(day: Int, tableId: Int): LiveData<List<CourseBean>> {
        return courseDao.getCourseByDayOfTableLiveData(day, tableId)
    }

    fun getShowCourseNumber(week: Int): LiveData<Int> {
        return if (table.showOtherWeekCourse) {
            courseDao.getShowCourseNumberWithOtherWeek(table.id, week)
        } else {
            courseDao.getShowCourseNumber(table.id, week)
        }
    }

    suspend fun deleteCourseBean(courseBean: CourseBean) {
        courseDao.deleteCourseDetail(CourseUtils.courseBean2DetailBean(courseBean))
    }

    suspend fun deleteCourseBaseBean(id: Int, tableId: Int) {
        courseDao.deleteCourseBaseBeanOfTable(id, tableId)
    }

    suspend fun updateFromOldVer(json: String) {
        val gson = Gson()
        val list = gson.fromJson<List<CourseOldBean>>(json, object : TypeToken<List<CourseOldBean>>() {
        }.type)
        // E：原 getDefaultTableSync() 会在主线程（ScheduleActivity onCreate launch）同步查库，改用 suspend 版本
        val tableId = tableDao.getDefaultTable()?.id ?: return
        oldBean2CourseBean(list, tableId)
    }

    private suspend fun oldBean2CourseBean(list: List<CourseOldBean>, tableId: Int) {
        val baseList = arrayListOf<CourseBaseBean>()
        val detailList = arrayListOf<CourseDetailBean>()
        var id = 0
        for (oldBean in list) {
            val flag = Common.findExistedCourseId(baseList, oldBean.name)
            if (flag == -1) {
                baseList.add(CourseBaseBean(id, oldBean.name, "", tableId))
                detailList.add(CourseDetailBean(
                        id = id, room = oldBean.room,
                        teacher = oldBean.teach, day = oldBean.day,
                        step = oldBean.step, startWeek = oldBean.startWeek, endWeek = oldBean.endWeek,
                        type = oldBean.isOdd, startNode = oldBean.start,
                        tableId = tableId
                ))
                id++
            } else {
                detailList.add(CourseDetailBean(
                        id = flag, room = oldBean.room,
                        teacher = oldBean.teach, day = oldBean.day,
                        step = oldBean.step, startWeek = oldBean.startWeek, endWeek = oldBean.endWeek,
                        type = oldBean.isOdd, startNode = oldBean.start,
                        tableId = tableId
                ))
            }
        }
        courseDao.insertCourses(baseList, detailList)
        getApplication<App>().getPrefer().edit {
            remove(Const.KEY_OLD_VERSION_COURSE)
        }
    }

    suspend fun exportData(uri: Uri?) {
        if (uri == null) throw Exception("无法获取文件")
        val outputStream = getApplication<App>().contentResolver.openOutputStream(uri)
        val gson = Gson()
        val strBuilder = StringBuilder()
        strBuilder.append(gson.toJson(timeTableDao.getTimeTable(table.timeTable)))
        strBuilder.append("\n${gson.toJson(timeList)}")
        strBuilder.append("\n${gson.toJson(table)}")
        strBuilder.append("\n${gson.toJson(courseDao.getCourseBaseBeanOfTable(table.id))}")
        strBuilder.append("\n${gson.toJson(courseDao.getDetailOfTable(table.id))}")
        withContext(Dispatchers.IO) {
            outputStream?.write(strBuilder.toString().toByteArray())
        }
    }

    suspend fun exportICS(uri: Uri?) {
        if (uri == null) throw Exception("无法获取文件")
        val ical = ICalendar()
        withContext(Dispatchers.Default) {
            ical.setProductId("-//Tangle//小唐Tangle//CN")
            val startTimeMap = ICalUtils.getClassTime(timeList, true)
            val endTimeMap = ICalUtils.getClassTime(timeList, false)
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            val date = sdf.parse(table.startDate)
            allCourseList.forEach {
                it.value?.forEach { course ->
                    try {
                        ICalUtils.getClassEvents(ical, startTimeMap, endTimeMap, table.maxWeek, course, date)
                    } catch (e: Exception) {
                        Log.e(TAG, "导入ICS时跳过一节课", e)
                    }
                }
            }
        }
        val warnings = ical.validate(ICalVersion.V2_0)
        Log.d("日历", warnings.toString())
        val outputStream = getApplication<App>().contentResolver.openOutputStream(uri)
        withContext(Dispatchers.IO) {
            Biweekly.write(ical).go(outputStream)
        }
    }
}




