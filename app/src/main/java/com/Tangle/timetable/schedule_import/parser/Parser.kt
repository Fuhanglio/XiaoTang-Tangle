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
        val isEmpty = _baseList.isEmpty()

        // 新版正方（茅台学院等）解析链路：无论成败都留一份原始页面样本到 Download，
        // 便于定位「导入不全」（页面里明明有课、却只解析出部分）这类问题。
        var dumpPath: String? = null
        if (this is NewZFParser && source.length > 300) {
            val note = StringBuilder().apply {
                append("解析结果: ").append(if (isEmpty) "为空（0 门）" else "${_baseList.size} 门课程")
                append("\n--- 课程列表 ---\n")
                _baseList.forEach { append("  [${it.id}] ${it.courseName}\n") }
                append("--- 课程明细 ---\n")
                _detailList.forEach {
                    append("  课${it.id} 周${it.day} 第${it.startNode}节 连${it.step}节 ${it.startWeek}-${it.endWeek}周 单双${it.type} 教室=${it.room} 教师=${it.teacher}\n")
                }
            }.toString()
            dumpPath = NewZFParser.dumpHtml(context, source, if (isEmpty) "empty" else "ok", note)
        }

        if (isEmpty) {
            throw Exception("导入数据为空>_<请确保选择正确的教务类型\n以及到达显示课程的页面\n（源码已保存到 ${dumpPath ?: "Download/wakeup_import_*.html"}，请把文件发给开发者协助排查）")
        }
        block(_baseList, _detailList)
        return _baseList.size
    }

}