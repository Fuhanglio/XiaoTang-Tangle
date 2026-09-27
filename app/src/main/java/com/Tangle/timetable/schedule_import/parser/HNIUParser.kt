package com.Tangle.timetable.schedule_import.parser

import com.Tangle.timetable.schedule_import.bean.Course
import org.jsoup.Jsoup

// 湖南信息职业技术学院
class HNIUParser(source: String) : Parser(source) {

    override fun generateCourseList(): List<Course> {
        val courseList = arrayListOf<Course>()
        val doc = Jsoup.parse(source, "utf-8")
        // W7-10①：属性/结构一变，`[0]` 直接越界；改为 getOrNull + 提前返回。
        val tBody = doc.getElementsByAttributeValue("bordercolordark", "#FFFFFF").firstOrNull()
                ?.getElementsByTag("tbody")?.firstOrNull() ?: return courseList
        val trs = tBody.getElementsByTag("tr")

        for (tr in trs) {
            val tds = tr.getElementsByTag("td")
            var day = 0
            for (td in tds) {
                if (td.attr("align") == "center") {
                    continue
                }
                if (td.attr("valign") == "top") {
                    day++
                    val courseSource = td.html().split("<br>")
                    if (courseSource.isEmpty()) continue
                    if (courseSource.size <= 4) {
                        // W7-10②：单元格只有一行文本时 size == 1，convertHNIU 内 courseSource[1] 越界。
                        if (courseSource.size < 2 || courseSource[0].isBlank()) continue
                        convertHNIU(day, courseSource, courseList)
                    } else {
                        var startIndex = 1
                        courseSource.forEachIndexed { index, s ->
                            if (s.contains('[') && s.contains(']') && s.contains('周') && s.contains('节')) {
                                // W7-10③：index == 0 时 subList(0, -1) 会抛异常，故加 index > 0。
                                if (index > 0 && index - 1 != 0) {
                                    convertHNIU(day, courseSource.subList(startIndex - 1, index - 1), courseList)
                                    startIndex = index
                                }
                            }
                            if (index == courseSource.size - 1) {
                                convertHNIU(day, courseSource.subList(startIndex - 1, index), courseList)
                            }
                        }
                    }
                }
            }
        }
        return courseList
    }

    private fun convertHNIU(day: Int, courseSource: List<String>, courseList: MutableList<Course>) {
        var startNode = 0
        var step = 0
        var startWeek = 0
        var endWeek = 0

        val courseName = courseSource[0].split(' ')[0]
        val teacher = courseSource[1].split(' ')[0]
        val room = if (courseSource.size > 2 && courseSource[2].trim().isNotBlank()) {
            courseSource[2].trim()
        } else {
            val tmp = courseSource[1].split(' ')
            tmp[tmp.size - 1]
        }
        val timeStr = courseSource[1].substringAfter('[').substringBeforeLast('节')
        // W7-10④：无"周]["分隔时（如页面写成"[1-16周] [1-2节]"）原 `[1]` 直接越界，
        // 改为 getOrNull，解析不出就跳过这门课。
        val timeParts = timeStr.split("周][")
        val weekPart = timeParts.getOrNull(0) ?: return
        val nodePart = timeParts.getOrNull(1) ?: return
        val weekList = weekPart.split(", ", ",")
        val nodeStr = nodePart

        val nodeList = nodeStr.split('-')
        if (nodeList.size == 1) {
            startNode = nodeList[0].toInt()
            step = 1
        } else {
            startNode = nodeList[0].toInt()
            step = nodeList[1].toInt() - startNode + 1
        }

        weekList.forEach {
            if (it.contains('-')) {
                val weeks = it.split('-')
                if (weeks.isNotEmpty()) {
                    startWeek = weeks[0].toInt()
                }
                if (weeks.size > 1) {
                    endWeek = weeks[1].toInt()
                }
            } else {
                startWeek = it.toInt()
                endWeek = it.toInt()
            }

            courseList.add(
                    Course(
                            name = courseName, room = room,
                            teacher = teacher, day = day,
                            startNode = startNode, endNode = startNode + step - 1,
                            startWeek = startWeek, endWeek = endWeek,
                            type = 0
                    )
            )
        }
    }

}