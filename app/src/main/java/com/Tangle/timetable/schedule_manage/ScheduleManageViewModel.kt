package com.Tangle.timetable.schedule_manage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.Tangle.timetable.AppDatabase
import com.Tangle.timetable.bean.AppWidgetBean
import com.Tangle.timetable.bean.CourseBaseBean
import com.Tangle.timetable.bean.TableBean
import com.Tangle.timetable.bean.TableSelectBean

class ScheduleManageViewModel(application: Application) : AndroidViewModel(application) {

    private val dataBase = AppDatabase.getDatabase(application)
    private val tableDao = dataBase.tableDao()
    private val courseDao = dataBase.courseDao()
    private val widgetDao = dataBase.appWidgetDao()

    suspend fun initTableSelectList(): MutableList<TableSelectBean> {
        return tableDao.getTableSelectList().toMutableList()
    }

    suspend fun getCourseBaseBeanListByTable(tableId: Int): MutableList<CourseBaseBean> {
        return courseDao.getCourseBaseBeanOfTable(tableId).toMutableList()
    }

    suspend fun getTableById(id: Int): TableBean? {
        return tableDao.getTableById(id)
    }

    suspend fun addBlankTable(tableName: String): Long {
        return tableDao.insertTable(TableBean(id = 0, tableName = tableName))
    }

    suspend fun deleteTable(id: Int) {
        tableDao.deleteTable(id)
    }

    /** 删除默认课表前，先把默认身份转移给剩余课表，避免删除后无默认表 */
    suspend fun changeDefaultTable(oldId: Int, newId: Int) {
        tableDao.changeDefaultTable(oldId, newId)
    }

    suspend fun clearTable(id: Int) {
        tableDao.clearTable(id)
    }

    suspend fun deleteCourse(course: CourseBaseBean) {
        courseDao.deleteCourseBaseBeanOfTable(course.id, course.tableId)
    }

    suspend fun getScheduleWidgetIds(): List<AppWidgetBean> {
        return widgetDao.getWidgetsByBaseType(0)
    }
}