package com.Tangle.timetable.schedule_import.parser

import com.Tangle.timetable.schedule_import.bean.Course
import org.jsoup.Jsoup

class OldQzParser(source: String) : Parser(source) {

    /** W7-11：周次段与节次段之间的分隔符。页面实测有 `1-16周[1-2节]`、`1-16周 [1-2节]`、
     *  `[1-16周][1-2节]`、`[1-16周] [1-2节]` 四种写法，即 `周` 之后可能是 `[`，也可能是 `] [`。
     *  故 `周` 与 `[` 之间允许夹空白，并允许夹一个 `]`。 */
    private val weekNodeSep = Regex("""周\s*\]?\s*\[""")

    override fun generateCourseList(): List<Course> {
        val courseList = arrayListOf<Course>()
        val doc = Jsoup.parse(source)
        val kbtable = doc.getElementById("kbtable")
        val trs = kbtable.getElementsByTag("tr")

        for (tr in trs) {
            val tds = tr.getElementsByTag("td")
            if (tds.isEmpty()) {
                continue
            }

            var day = -1

            for (td in tds) {
                day++
                val divs = td.getElementsByTag("div")
                for (div in divs) {
                    if (div.attr("style") == "display: none;" || div.text().isBlank()) continue
                    val split = div.html().split("<br>")
                    var preIndex = -1

                    fun toCourse() {
                        if (preIndex == -1) return
                        val courseName = Jsoup.parse(split[0]).text().trim()
                        val room = Jsoup.parse(split[preIndex + 1]).text().trim()
                        val teacher = Jsoup.parse(split[preIndex - 1]).text().trim()
                        // W7-11：旧强智页面「周次」与「节次」之间不一定是紧邻的 `周[`（实测还有
                        // `[1-16周][1-2节]` / `[1-16周] [1-2节]` 这种中间带 `]` 或空格的写法）。
                        // 用固定串 split("周[") 会切不出第 2 段，紧接着 timeInfo[1] 直接越界崩溃。
                        // 改为允许空白与 `]` 的正则 + 段数校验。
                        val timeInfo = Jsoup.parse(split[preIndex]).text().trim().split(weekNodeSep)
                        if (timeInfo.size < 2) return
                        // 周次段首可能残留前导 '['（如 "[1-16"），先剥掉非数字前缀再解析。
                        val weekNums = timeInfo[0].trimStart { !it.isDigit() }.split('-')
                        val startWeek = weekNums[0].toIntOrNull() ?: return
                        val endWeek = (if (weekNums.size > 1) weekNums[1] else weekNums[0])
                                .toIntOrNull() ?: startWeek
                        // W7-11：单节写法（如 `[1节]`）里没有 '-'，原 `split('-')[1]` 同样越界。
                        val nodeNums = timeInfo[1].split('-')
                        val startNode = nodeNums[0].substringBefore('节').toIntOrNull() ?: return
                        val endNode = (if (nodeNums.size > 1) nodeNums[1] else nodeNums[0])
                                .substringBefore('节').toIntOrNull() ?: startNode

                        courseList.add(
                                Course(
                                        name = courseName, room = room,
                                        teacher = teacher, day = day,
                                        startNode = startNode, endNode = endNode,
                                        startWeek = startWeek, endWeek = endWeek,
                                        type = 0
                                )
                        )
                    }

                    for (i in split.indices) {
                        if (split[i].contains('[') && split[i].contains(']') && split[i].contains('节') && split[i].contains(
                                        '周'
                                )
                        ) {
                            if (preIndex != -1) {
                                toCourse()
                            }
                            preIndex = i
                        }
                        if (i == split.size - 1) {
                            toCourse()
                        }
                    }
                }
            }
        }
        return courseList
    }

}