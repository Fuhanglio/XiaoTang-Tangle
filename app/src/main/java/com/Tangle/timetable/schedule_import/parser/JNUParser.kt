package com.Tangle.timetable.schedule_import.parser

import com.Tangle.timetable.schedule_import.bean.Course
import org.jsoup.Jsoup

// 暨南大学
class JNUParser(source: String) : Parser(source) {
    override fun generateCourseList(): List<Course> {
        val courseList = arrayListOf<Course>()

        val xml = source.substringAfter("</html>")
        val doc = Jsoup.parse(xml)
        val frame = doc.getElementById("oReportCell")
        val table = frame.getElementsByClass("a8")
        val trs = table[0].getElementsByTag("tr").subList(3, 10)

        // W7-15：先按课名收集这一行里出现过的**列索引（列索引即节次）**，再按"连续段"切成若干条
        // Course。原实现用"课名出现次数 - 1"当连堂节数，隐含"这些出现位置必然连续"：
        // 同一门课若出现在第 1 节和第 6 节，会被合并成"1-2 节" —— 第 6 节那节课直接丢失，
        // 且学生看到的上课时间是错的。
        // 同时按「行」分别收集，顺带修掉"同一门课出现在不同星期时被并成一条"的同类错位。
        for (i in trs.indices) {
            val tds = trs[i].getElementsByTag("td")
            if (tds.isEmpty()) continue

            val posOf = linkedMapOf<String, MutableList<Int>>()
            val roomOf = mutableMapOf<String, String>()
            for (j in tds.indices) {
                val str = tds[j].getElementsByTag("div").text()
                if (str.isNullOrEmpty() || j == 0) continue
                val name = str.substringAfter('：').substringBeforeLast('(')
                val nodes = posOf.getOrPut(name) { mutableListOf() }
                if (nodes.isEmpty()) roomOf[name] = str.substringBefore(' ')
                nodes.add(j)
            }
            for ((name, nodes) in posOf) {
                var segStart = 0
                for (k in nodes.indices) {
                    val last = k == nodes.size - 1
                    if (last || nodes[k + 1] != nodes[k] + 1) {
                        courseList.add(
                            Course(
                                name = name, day = i + 1, room = roomOf[name] ?: "", teacher = "",
                                startNode = nodes[segStart], endNode = nodes[k],
                                startWeek = 1, endWeek = 18, type = 0
                            )
                        )
                        segStart = k + 1
                    }
                }
            }
        }
        return courseList
    }
}