package com.Tangle.timetable.schedule_import.parser

import android.content.Context
import com.Tangle.timetable.bean.CourseBaseBean
import com.Tangle.timetable.bean.CourseDetailBean
import com.Tangle.timetable.schedule_import.Common
import com.Tangle.timetable.schedule_import.bean.Course
import com.Tangle.timetable.utils.ViewUtils

abstract class Parser(val source: String) {

    private val _baseList: ArrayList<CourseBaseBean> = arrayListOf()
    private val _detailList: ArrayList<CourseDetailBean> = arrayListOf()

    abstract fun generateCourseList(): List<Course>

    private fun convertCourse(context: Context, tableId: Int) {
        generateCourseList().forEach { course ->
            var id = Common.findExistedCourseId(_baseList, course.name)
            if (id == -1) {
                id = _baseList.size
                _baseList.add(
                        CourseBaseBean(
                                id = id, courseName = course.name,
                                color = "#${Integer.toHexString(ViewUtils.getCustomizedColor(context, id % 9))}",
                                tableId = tableId
                        )
                )
            }
            var step = course.endNode - course.startNode + 1
            if (step < 1) step = 1
            _detailList.add(CourseDetailBean(
                    id = id, room = course.room,
                    teacher = course.teacher,
                    day = if (course.day < 1) 1 else course.day,
                    step = step,
                    startWeek = if (course.startWeek < 1) 1 else course.startWeek,
                    endWeek = if (course.endWeek < 1) 1 else course.endWeek,
                    type = course.type,
                    startNode = if (course.startNode < 1) 1 else course.startNode,
                    tableId = tableId
            ))
        }
    }

    suspend fun saveCourse(context: Context, tableId: Int, block: suspend (baseList: List<CourseBaseBean>,
                                                                           detailList: List<CourseDetailBean>) -> Unit): Int {
        convertCourse(context, tableId)
        if (_baseList.isEmpty()) {
            // 诊断兜底：把原始 HTML 保存到 Download 目录，方便用户发给开发者排查
            NewZFParser.diagnoseAndDump(context, source)
            throw Exception("导入数据为空>_<请确保选择正确的教务类型\n以及到达显示课程的页面\n（源码已保存到 Download/wakeup_import_debug_xxx.html，请把文件发给开发者协助排查）")
        }
        block(_baseList, _detailList)
        return _baseList.size
    }

}