package com.Tangle.timetable.schedule_import.parser

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.Tangle.timetable.schedule_import.bean.Course
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 新版正方教务课表解析器，同时兼容三种页面结构：
 *
 * 1) 旧版网格表：<table id="table1">，每行 tr（首格 class=festival 是节次），
 *    其后 7 个 td 为周一~周日，课程块 div.title + p[title=教师/上课地点/节/周]。
 *
 * 2) 新版 div 网格（茅台学院曾用）：无 table1，课表在 div.stage_1..stage_12，
 *    每行 7 个 li（周一~周日），li 内 span.course-hasContent 文本为课程名。
 *
 * 3) 通用 table 网格（茅台学院当前 / 其他没 table1 的正方 V3.0）：
 *    传统 <table>，表头同时含"节次"+"星期X/周X"，每个大节一行 tr，
 *    课程单元格多行纯文本（<br>分隔），按行识别：课程名/周次/班级/教室/老师。
 *
 * 解析失败诊断：三种结构全部返回空时，HTML 自动保存到 Download 目录，文件名
 * wakeup_import_debug_时间戳.html，方便用户/作者排查。
 */
class NewZFParser(source: String) : Parser(source) {

    companion object {
        private const val TAG = "NewZFParser"

        /**
         * 导入诊断：把抓取到的原始 HTML 存到公共 Download 目录，方便用户直接发给开发者排查。
         *
         * - Android 10+：走 MediaStore 写入公共 Download（无需任何权限，用户能在「文件管理 → 下载」里直接看到）
         * - Android 9-：写 app-specific external dir（免权限）
         *
         * 文件开头附带一段诊断注释（解析出的课程数 / 课程列表 / 明细），
         * 便于对照「页面里实际有什么」与「解析出了什么」，定位"导入不全"类问题。
         *
         * @param tag 文件名标记：ok / empty（便于区分成功与失败样本）
         * @param note 诊断注释正文
         * @return 用户可读的保存位置描述；失败返回 null
         */
        @JvmStatic
        fun dumpHtml(context: Context, html: String, tag: String, note: String): String? {
            return try {
                val ts = SimpleDateFormat("MMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "wakeup_import_${tag}_$ts.html"
                val content = "<!-- ===== 小唐Tangle 导入诊断 =====\n" +
                        "$note\n" +
                        "抓取时间: $ts\n" +
                        "HTML 长度: ${html.length}\n" +
                        "================================== -->\n$html"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "text/html")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(content.toByteArray(Charsets.UTF_8))
                        }
                        Log.w(TAG, "=== 导入诊断：已保存 Download/$fileName （${html.length} chars） ===")
                        "Download/$fileName"
                    } else {
                        writeToAppDir(context, fileName, content)
                    }
                } else {
                    writeToAppDir(context, fileName, content)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "诊断 dump 失败: ${t.message}")
                null
            }
        }

        /** 兜底：写入 app 私有外部目录，用户可在 Android/data/<包名>/files/Download 下找到。 */
        private fun writeToAppDir(context: Context, fileName: String, content: String): String? {
            return try {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "")
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, fileName)
                f.writeText(content, Charsets.UTF_8)
                Log.w(TAG, "=== 导入诊断：已保存 ${f.absolutePath} （${content.length} chars） ===")
                f.absolutePath
            } catch (t: Throwable) {
                Log.e(TAG, "诊断 dump（私有目录）失败: ${t.message}")
                null
            }
        }
    }

    // ================== 正则（三种结构共用） ==================

    // 节次范围：第1-2节 / 1-2节 / 1-2
    private val nodeRangeRegex = Regex("""(?:第)?\s*(\d{1,2})\s*[-~～]\s*(\d{1,2})\s*节?""")
    // 单节：第5节 / 5节
    private val singleNodeRegex = Regex("""第?\s*(\d{1,2})\s*节""")
    // 周次范围：第1-16周 / 1-16周 / 1～16周
    private val weekRangeRegex = Regex("""第?\s*(\d{1,2})\s*[-~～]\s*(\d{1,2})\s*周""")
    // 单周
    private val singleWeekRegex = Regex("""第?\s*(\d{1,2})\s*周""")
    // 兜底：整段就是数字范围（无"周"字），如 "4-5"（来自 "4-5;7-16 周" 的拆分）
    private val bareRangeRegex = Regex("""^第?\s*(\d{1,2})\s*[-~～]\s*(\d{1,2})\s*$""")
    // 兜底：整段就是单个数字（无"周"字），如 "2"（来自 "2;4;6;8;13-16 周" 的拆分）
    private val bareSingleRegex = Regex("""^第?\s*(\d{1,2})\s*$""")
    // 行内样式 height
    private val heightRegex = Regex("""height:\s*(\d+(?:\.\d+)?)px""")

    // ================== 入口：按结构分发 ==================

    override fun generateCourseList(): List<Course> {
        val doc = Jsoup.parse(source)

        // 分支 0（最高优先级）：React + AntDesign 课表（茅台学院当前 / CourseTimeTableOld）
        // 特征：class 含 "courseBox"，CSS Modules 哈希名稳定
        val reactBoxes = doc.select("div[class*=courseBox]")
        if (reactBoxes.isNotEmpty()) {
            val r = parseReactCourseBox(doc)
            if (r.isNotEmpty()) {
                // 去重 key 更严（加 room+teacher），避免两个重复 tbody 合并时误删
                return r.distinctBy { listOf(it.name, it.day, it.startNode, it.endNode, it.startWeek, it.endWeek, it.type, it.room, it.teacher) }
            }
        }

        // 分支 1：旧版 table1（优先级最高，有明确 id）
        val table1 = doc.getElementById("table1")
        if (table1 != null) {
            val r = parseTableGrid(doc)
            if (r.isNotEmpty()) return r
        }

        // 分支 2：新版 div 网格（有 stage_N class）
        val anyStage = doc.selectFirst("[class^=stage_]")
        if (anyStage != null) {
            val r = parseDivGrid(doc)
            if (r.isNotEmpty()) return r
        }

        // 分支 3：通用 table（兜底，茅台学院等）
        val r = parseStandardTable(doc)

        // 三个分支产出后去重
        return r.distinctBy {
            listOf(it.name, it.day, it.startNode, it.startWeek, it.endWeek, it.type)
        }
    }

    // ================== 结构1：旧版 table1 网格 ==================

    private fun parseTableGrid(doc: Document): List<Course> {
        val result = arrayListOf<Course>()
        val table1 = doc.getElementById("table1") ?: return emptyList()

        for (tr in table1.getElementsByTag("tr")) {
            val festivalText = tr.getElementsByClass("festival").text()
            val rowNode = Regex("""\d{1,2}""").find(festivalText)?.value?.toIntOrNull()
            if (rowNode == null || rowNode !in 1..12) continue

            val tds = tr.getElementsByTag("td")
            var dayColumn = 0
            for (td in tds) {
                val idDay = td.attr("id").trim().firstOrNull()
                        ?.let { runCatching { it.toString().toInt() }.getOrNull() }
                val hasCourse = td.getElementsByClass("title").isNotEmpty()
                if ((idDay == null || idDay !in 1..7) && !hasCourse) continue
                dayColumn++
                if (dayColumn > 7) break
                val day = if (idDay != null && idDay in 1..7 && idDay == dayColumn) idDay else dayColumn
                if (day !in 1..7) continue

                for (div in td.getElementsByTag("div")) {
                    val courseName = div.getElementsByClass("title").text().trim()
                    if (courseName.isEmpty()) continue

                    var teacher = ""
                    var room = ""
                    var startNode: Int = rowNode
                    var step: Int = 1
                    val weekSegments = arrayListOf<String>()

                    for (p in div.getElementsByTag("p")) {
                        when (p.attr("title").trim()) {
                            "教师" -> teacher = p.text().trim()
                            "上课地点" -> room = p.text().trim()
                            "节/周", "周/节" -> {
                                val timeStr = p.text().trim()
                                val nodeMatch = nodeRangeRegex.find(timeStr)
                                if (nodeMatch != null) {
                                    val s = nodeMatch.groupValues[1].toIntOrNull()
                                    val e = nodeMatch.groupValues[2].toIntOrNull()
                                    if (s != null && s in 1..12) startNode = s
                                    if (s != null && e != null && e >= s) step = e - s + 1
                                }
                                val weekPart = nodeRangeRegex.replace(timeStr, " ")
                                        .replace("节", " ").replace("(", " ").replace("（", " ")
                                        .replace(")", " ").replace("）", " ")
                                weekSegments.addAll(
                                        weekPart.split(",", "，", " ")
                                                .map { it.trim() }
                                                .filter { it.isNotEmpty() && it.contains('周') }
                                )
                            }
                        }
                    }

                    val weekKeys = linkedSetOf<Triple<Int, Int, Int>>()
                    for (seg in weekSegments) {
                        val type = when {
                            seg.contains('单') -> 1
                            seg.contains('双') -> 2
                            else -> 0
                        }
                        val range = weekRangeRegex.find(seg)
                        if (range != null) {
                            val s = range.groupValues[1].toIntOrNull()
                            val e = range.groupValues[2].toIntOrNull()
                            if (s != null && e != null && s in 1..30 && e in s..30) weekKeys.add(Triple(s, e, type))
                            continue
                        }
                        val single = singleWeekRegex.find(seg)
                        if (single != null) {
                            val w = single.groupValues[1].toIntOrNull()
                            if (w != null && w in 1..30) weekKeys.add(Triple(w, w, type))
                        }
                    }
                    if (weekKeys.isEmpty()) weekKeys.add(Triple(1, 20, 0))

                    for ((startWeek, endWeek, type) in weekKeys) {
                        result.add(Course(
                                name = courseName, room = room, teacher = teacher,
                                day = day, startNode = startNode,
                                endNode = startNode + step - 1,
                                startWeek = startWeek, endWeek = endWeek, type = type
                        ))
                    }
                }
            }
        }
        return result
    }

    // ================== 结构2：新版 div 网格 ==================

    private val nodeHeightPx = 60

    private fun parseDivGrid(doc: Document): List<Course> {
        val result = arrayListOf<Course>()
        for (node in 1..12) {
            val stageEl = doc.selectFirst(".stage_$node") ?: continue
            val lis = stageEl.select("> li")
            for (i in lis.indices) {
                val day = i + 1
                if (day !in 1..7) continue
                val li = lis[i]
                val span = li.selectFirst("span.course-hasContent") ?: continue
                val name = span.text().trim()
                if (name.isEmpty()) continue

                val heightPx = heightRegex.find(span.attr("style"))
                        ?.groupValues?.get(1)?.toFloatOrNull()
                val step = if (heightPx != null && heightPx > 0) {
                    (heightPx / nodeHeightPx).roundToInt().coerceIn(1, 6)
                } else 1

                result.add(Course(
                        name = name, day = day, room = "", teacher = "",
                        startNode = node, endNode = node + step - 1,
                        startWeek = 1, endWeek = 20, type = 0
                ))
            }
        }
        return result
    }

    // ================== 结构3：通用 table 网格（茅台学院等） ==================

    /**
     * 定位课表 table：遍历所有 <table>，找第一行（表头）同时包含
     * "节次"/"节" 和 "星期X" 或 "周X" 的那个。
     */
    private fun findScheduleTable(doc: Document): Element? {
        for (table in doc.getElementsByTag("table")) {
            val firstRow = table.selectFirst("tr") ?: continue
            val headerText = firstRow.text()
            val hasNodeHeader = headerText.contains("节次") || headerText.contains("节 / 星期")
            val hasWeekdayHeader = headerText.contains("星期") || headerText.contains("周一") || headerText.contains("周日")
            if (hasNodeHeader && hasWeekdayHeader) return table
        }
        return null
    }

    /** 解析表头返回星期列索引映射：td 内文本 -> day (1..7)。 */
    private fun buildHeaderMap(headerRow: Element): Map<Int, Int> {
        val result = linkedMapOf<Int, Int>()
        val weekdays = listOf("一", "二", "三", "四", "五", "六", "日", "天")
        val tds = headerRow.getElementsByTag("td") + headerRow.getElementsByTag("th")
        for ((colIdx, cell) in tds.withIndex()) {
            val text = cell.text().trim()
            // 兼容 "星期一"/"周一"/"星期1" 甚至 "Mon"（少见）
            val day = when {
                text.contains("星期一") || text == "周一" || text.startsWith("Mon") -> 1
                text.contains("星期二") || text == "周二" -> 2
                text.contains("星期三") || text == "周三" -> 3
                text.contains("星期四") || text == "周四" -> 4
                text.contains("星期五") || text == "周五" -> 5
                text.contains("星期六") || text == "周六" -> 6
                text.contains("星期日") || text == "周日" || text.contains("星期天") || text == "周天" -> 7
                text == "日" || text == "天" || text == "Sun" -> 7
                text == "一" -> 1
                text == "二" -> 2
                text == "三" -> 3
                text == "四" -> 4
                text == "五" -> 5
                text == "六" -> 6
                text == "星期" -> 0   // 节次/星期 总表头列，跳过
                text == "节次" -> 0
                else -> {
                    // 尝试直接解析 "1"/"2"/..."7"
                    text.trim().toIntOrNull()?.takeIf { it in 1..7 } ?: 0
                }
            }
            if (day in 1..7) result[colIdx] = day
        }
        return result
    }

    /**
     * 从一行 <tr> 的第一列解析节次范围："第1-2节\n08:20-10:00" -> start=1, end=2.
     * 返回 null 表示这行不是课程数据行（跳过）。
     */
    private fun parseRowNode(firstCellText: String): Pair<Int, Int>? {
        val text = firstCellText.trim()
        val rangeMatch = nodeRangeRegex.find(text)
        if (rangeMatch != null) {
            val s = rangeMatch.groupValues[1].toIntOrNull()
            val e = rangeMatch.groupValues[2].toIntOrNull()
            if (s != null && e != null && s in 1..12 && e in s..12) return Pair(s, e)
        }
        // 单节："第5节"
        val singleMatch = singleNodeRegex.find(text)
        if (singleMatch != null) {
            val s = singleMatch.groupValues[1].toIntOrNull()
            if (s != null && s in 1..12) return Pair(s, s)
        }
        return null
    }

    /** 从原始文本里提取所有周次三元组 (startWeek, endWeek, type)。 */
    private fun parseWeeks(text: String, outList: MutableList<Triple<Int, Int, Int>>) {
        val segments = linkedSetOf<String>()
        // 按中英文逗号/分号 拆成小段
        text.split(",", "，", ";", "；", "/", "、")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { segments.add(it) }

        for (seg in segments) {
            val type = when {
                seg.contains('单') -> 1
                seg.contains('双') -> 2
                else -> 0
            }
            val range = weekRangeRegex.find(seg)
            if (range != null) {
                val s = range.groupValues[1].toIntOrNull()
                val e = range.groupValues[2].toIntOrNull()
                if (s != null && e != null && s in 1..30 && e in s..30) {
                    outList.add(Triple(s, e, type))
                }
                continue
            }
            val single = singleWeekRegex.find(seg)
            if (single != null) {
                val w = single.groupValues[1].toIntOrNull()
                if (w != null && w in 1..30) outList.add(Triple(w, w, type))
                continue
            }
            // 兜底：整段只有数字或数字范围（"周"字只在整串末尾出现一次时，
            // 拆分后前面的段会丢掉"周"，如 "2;4;6;8;13-16 周" / "4-5;7-16 周"）
            val bareRange = bareRangeRegex.find(seg)
            if (bareRange != null) {
                val s = bareRange.groupValues[1].toIntOrNull()
                val e = bareRange.groupValues[2].toIntOrNull()
                if (s != null && e != null && s in 1..30 && e in s..30) {
                    outList.add(Triple(s, e, type))
                }
                continue
            }
            val bareSingle = bareSingleRegex.find(seg)
            if (bareSingle != null) {
                val w = bareSingle.groupValues[1].toIntOrNull()
                if (w != null && w in 1..30) outList.add(Triple(w, w, type))
            }
        }
    }

    /**
     * 解析一个课程单元格的多行纯文本，输出 Course。
     * 行识别规则（顺序）：
     *   1. 第一行 = 课程名
     *   2. 含 "周" = 周次（括号里 (第x,y节) 是精确节次优先，覆盖 rowNode）
     *   3. 含 "班" / ")人" / "班(" = 上课班级（跳过）
     *   4. 含 "楼" / "室" / "教室" / "A1-422" 格式 = 教室
     *   5. 含 "/" 的 "/"-分隔行（电力电子技术 / 1-9周(...) / A1-415 / 闫庚龙），按 "/" 拆再识别
     *   6. 剩余最后一行短文本 = 老师
     */
    private fun parseCell(
            rawHtml: String,
            defaultDay: Int,
            defaultNode: Pair<Int, Int>,
            defaultRowSpan: Int
    ): List<Course> {
        if (rawHtml.isBlank()) return emptyList()

        // 先解 HTML：<br>、<p>、换行、空格归一
        val text = Jsoup.parseBodyFragment("<div>$rawHtml</div>").body().text()
                .replace("&nbsp;", " ")
                .trim()
        if (text.isEmpty()) return emptyList()

        // 拆行：先按 / 拆（"/" 格式），再按换行拆，最后按 "、" 拆
        val rawLines = text.split("\n", "/")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        if (rawLines.isEmpty()) return emptyList()

        var courseName = ""
        var room = ""
        var teacher = ""
        var startNode = defaultNode.first
        var endNode = defaultNode.second
        val weekKeys = arrayListOf<Triple<Int, Int, Int>>()

        // 逐行扫描
        val remainingCandidates = arrayListOf<String>()
        for ((idx, line) in rawLines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            when {
                idx == 0 -> courseName = trimmed   // 第一行永远是课程名
                trimmed.contains('周') -> {
                    // 周次行：可能带括号精确节次
                    val preciseNodeMatch = Regex("""第?\s*\(?\s*(\d{1,2})\s*[,，、]\s*(\d{1,2})\s*\)?\s*节""").find(trimmed)
                            ?: Regex("""\(?\s*(\d{1,2})\s*[,，、]\s*(\d{1,2})\s*\)?\s*节""").find(trimmed)
                    if (preciseNodeMatch != null) {
                        val s = preciseNodeMatch.groupValues[1].toIntOrNull()
                        val e = preciseNodeMatch.groupValues[2].toIntOrNull()
                        if (s != null && e != null && s in 1..12 && e in s..12) {
                            startNode = s
                            endNode = e
                        }
                    }
                    parseWeeks(trimmed, weekKeys)
                }
                trimmed.contains("班") || trimmed.contains("人)") || trimmed.contains("班(") -> {
                    // 上课班级，跳过不存
                    // 但如果这行只有班级信息，后面可能还有教室和老师
                }
                trimmed.contains("楼") || trimmed.contains("教室") || trimmed.contains("室") -> {
                    if (room.isEmpty()) room = trimmed
                }
                trimmed.matches(Regex("""[A-Za-z]\d[-–]\d+.*""")) || trimmed.matches(Regex("""[\u4e00-\u9fa5]+[-–]\d+.*""")) -> {
                    // A1-422 / 自动化243班(54人) 类格式，可能是教室
                    if (room.isEmpty() && !trimmed.contains("班(") && !trimmed.contains("人")) {
                        room = trimmed
                    } else {
                        remainingCandidates.add(trimmed)
                    }
                }
                else -> remainingCandidates.add(trimmed)
            }
        }

        // 老师 = 剩余候选里最后一个非空且短（<10字）的中文文本
        if (remainingCandidates.isNotEmpty()) {
            teacher = remainingCandidates.last()
            // 老师名通常 2-4 字；如果最后一个很长可能不是老师，往前找
            if (teacher.length > 10 && remainingCandidates.size >= 2) {
                teacher = remainingCandidates[remainingCandidates.size - 2]
            }
        }

        if (courseName.isEmpty()) return emptyList()
        if (weekKeys.isEmpty()) {
            // 周次完全解析不出，给一个保守默认（避免丢课）
            weekKeys.add(Triple(1, 20, 0))
        }

        val step = (endNode - startNode + 1).coerceAtLeast(1)
        val effectiveStep = if (defaultRowSpan > step) defaultRowSpan else step

        val result = arrayListOf<Course>()
        for ((wStart, wEnd, type) in weekKeys) {
            result.add(Course(
                    name = courseName,
                    day = defaultDay,
                    room = room,
                    teacher = teacher,
                    startNode = startNode,
                    endNode = startNode + effectiveStep - 1,
                    startWeek = wStart,
                    endWeek = wEnd,
                    type = type
            ))
        }
        return result
    }

    /**
     * 通用 table 解析主循环。
     * 核心技巧：遍历所有 <tr>，定位表头行 → 数据行，
     * 每一行第一列给节次，后续 td 用 headerMap 映射 day。
     * rowspan 单元格用 colspanHelper 模拟（向下填充）。
     */
    private fun parseStandardTable(doc: Document): List<Course> {
        val result = arrayListOf<Course>()
        val table = findScheduleTable(doc) ?: return emptyList()

        // 收集所有 <tr>（包括嵌套在 tbody/thead/tfoot 里的）
        val allRows = table.select("tr").toList()
        if (allRows.isEmpty()) return emptyList()

        // 找表头：包含 "星期" 的那一行
        var headerIdx = -1
        for ((i, tr) in allRows.withIndex()) {
            if (tr.text().contains("星期") || tr.text().contains("周一")) {
                headerIdx = i
                break
            }
        }
        if (headerIdx < 0) return emptyList()

        val headerMap = buildHeaderMap(allRows[headerIdx])
        if (headerMap.isEmpty()) return emptyList()

        // rowspan 帮助：模拟向下填充的课程块 { colIdx -> (rawHtml, remainingRows, day, rowNodeStart) }
        data class RowSpanBlock(
                val rawHtml: String,
                var remaining: Int,
                val day: Int,
                val rowNodePair: Pair<Int, Int>
        )
        val rowspanHelper = linkedMapOf<Int, RowSpanBlock>()

        // 数据行从 headerIdx+1 开始
        for (rowIdx in (headerIdx + 1) until allRows.size) {
            val tr = allRows[rowIdx]
            // 跳过备注行、空行
            val rowText = tr.text().trim()
            if (rowText.isEmpty() || rowText.startsWith("备注") || rowText.contains("实训课")) continue

            val tds = tr.getElementsByTag("td")
            if (tds.isEmpty()) continue

            // 从第一列解析节次（跨行的 rowspan 块有自己的起始节次，不需要当前行的）
            val firstCellText = tds.firstOrNull()?.text() ?: ""
            val rowNode = parseRowNode(firstCellText)
            if (rowNode == null) {
                // 非节次数据行，可能是备注行或被跳过
                continue
            }

            // 处理 rowspan 帮助的递减
            val rowspanColIndices = tds.mapIndexed { i, _ -> i }.toSet()
            for ((col, block) in rowspanHelper.entries) {
                if (block.remaining > 0) {
                    // 在当前行渲染这个跨节课程
                    val courses = parseCell(
                            rawHtml = block.rawHtml,
                            defaultDay = block.day,
                            defaultNode = block.rowNodePair,
                            defaultRowSpan = 1
                    )
                    result.addAll(courses)
                    block.remaining--
                }
            }

            // 处理当前行的 td（跳过节次列 colIdx=0）
            for ((colIdx, td) in tds.withIndex()) {
                if (colIdx == 0) continue   // 第一列是节次标签，不是课程
                val day = headerMap[colIdx] ?: continue

                // 如果这个 colIdx 还在 rowspanHelper 且 remaining>0，说明是跨节延续，不解析
                val existingBlock = rowspanHelper[colIdx]
                if (existingBlock != null && existingBlock.remaining > 0) continue

                val rawHtml = td.html().trim()
                if (rawHtml.isBlank()) continue

                val spanAttr = td.attr("rowspan").toIntOrNull() ?: 1
                // 跨行 > 1：存到 helper，后续行继续渲染
                if (spanAttr > 1) {
                    rowspanHelper[colIdx] = RowSpanBlock(
                            rawHtml = rawHtml,
                            remaining = spanAttr - 1,   // 本行已渲染，剩下 spanAttr-1 行
                            day = day,
                            rowNodePair = rowNode
                    )
                }

                val courses = parseCell(
                        rawHtml = rawHtml,
                        defaultDay = day,
                        defaultNode = rowNode,
                        defaultRowSpan = spanAttr
                )
                result.addAll(courses)
            }
        }

        return result
    }

    // ================== 分支 0：React + AntDesign courseBox（茅台学院） ==================

    private val weekdayCharToDay = mapOf('一' to 1, '二' to 2, '三' to 3,
            '四' to 4, '五' to 5, '六' to 6, '日' to 7, '天' to 7)

    /**
     * React courseBox 解析（茅台学院 CourseTimeTableOld / CourseTimeTable）。
     * DOM 特征（CSS Modules 哈希名稳定）：
     *   <div class="...courseBox-xxx">
     *     <div class="...subject-xxx">课程名</div>
     *     <div><img alt="时间"><span>4-5;7-16 周</span><span> (第1,2节)</span></div>
     *     <div><img alt="班级"><span>...</span></div>
     *     <div><img alt="地点"><span>A1-306 (4712)</span></div>
     *     <div><img alt="教师"><span>黄敏</span></div>
     *   </div>
     * 每个 tbody 在页面里重复 2 次（正常 + 打印副本），最终依赖 distinctBy 去重。
     */
    private fun parseReactCourseBox(doc: Document): List<Course> {
        val boxes = doc.select("div[class*=courseBox]")
        if (boxes.isEmpty()) return emptyList()

        // 先把所有 courseBox 所属 table 行和列的映射准备好：
        // 找到含这些 courseBox 的 table tbody，按 tr / td 的结构推断每个 courseBox 的 day 和 rowNode
        // Jsoup 支持 .parent() 链
        val boxList = boxes.toList()
        val result = arrayListOf<Course>()

        for (box in boxList) {
            try {
                // ===== name =====
                val name = box.selectFirst("div[class*=subject]")?.text()?.trim().orEmpty()
                if (name.isEmpty()) continue

                // ===== 遍历直接子 div，按 img alt 归类 =====
                var timeText = ""
                var room = ""
                var teacher = ""
                val directDivs = box.children()  // 直接子 div
                for (div in directDivs) {
                    val img = div.selectFirst("img[alt]") ?: continue
                    val alt = img.attr("alt")
                    val spans = div.select("span").joinToString(" ") { it.text() }.trim()
                    when (alt) {
                        "时间" -> timeText = spans
                        "地点" -> {
                            // "A1-306 (4712)" 只保留房间号部分
                            room = spans.substringBefore('(').trim()
                        }
                        "教师" -> teacher = spans.trim()
                        "班级" -> { /* 忽略 */ }
                    }
                }

                if (timeText.isEmpty()) continue

                // ===== day：优先从 timeText 匹配 "星期X"，否则从 td 列序号推断 =====
                var day = 0
                val weekdayMatch = Regex("""星期\s*([一二三四五六日天])""").find(timeText)
                if (weekdayMatch != null) {
                    day = weekdayCharToDay[weekdayMatch.groupValues[1].firstOrNull()] ?: 0
                }
                if (day !in 1..7) {
                    // 从 DOM 位置推断：td 在 tr 所有 td 中的列序号
                    // courseBox → td（courseBox.parent()）→ tr → td 列表
                    var td = box.parent()
                    while (td != null && td.tagName() != "td") td = td.parent()
                    if (td != null) {
                        val tr = td.parent()
                        if (tr != null && tr.tagName() == "tr") {
                            val tds = tr.children()
                            val tdIdx = tds.indexOf(td)
                            if (tdIdx > 0) day = tdIdx   // 第 0 个是 timeName 节次列，所以 tdIdx 本身就是 day
                        }
                    }
                }
                if (day !in 1..7) continue

                // ===== startNode / endNode =====
                var startNode: Int? = null
                var endNode: Int? = null
                // 先在 timeText 里找 (第x,y节) / (第x;y节) 等括号内精确节次
                val preciseNodeMatch = Regex("""[（(]\s*第?\s*(\d{1,2})\s*[,，;；、]\s*(\d{1,2})\s*节?\s*[)）]""").find(timeText)
                if (preciseNodeMatch != null) {
                    startNode = preciseNodeMatch.groupValues[1].toIntOrNull()
                    endNode = preciseNodeMatch.groupValues[2].toIntOrNull()
                }
                if (startNode == null || endNode == null) {
                    // 从所在行 timeName 文本推断
                    var td = box.parent()
                    while (td != null && td.tagName() != "td") td = td.parent()
                    if (td != null) {
                        val tr = td.parent()
                        if (tr != null && tr.tagName() == "tr") {
                            val firstTd = tr.selectFirst("td[class*=timeName], td")
                            if (firstTd != null && firstTd != td) {
                                val timeNameText = firstTd.text()
                                val rowNode = parseRowNode(timeNameText)
                                if (rowNode != null) {
                                    startNode = startNode ?: rowNode.first
                                    endNode = endNode ?: rowNode.second
                                }
                            }
                        }
                    }
                }
                // 兜底：双节 1-2
                startNode = startNode ?: 1
                endNode = endNode ?: (startNode!! + 1)
                if (startNode !in 1..12 || endNode !in startNode..12) continue

                // ===== 周次 =====
                val weekKeys = arrayListOf<Triple<Int, Int, Int>>()
                val weekPart = timeText.replace(Regex("""[（(].*?[)）]"""), "")  // 去掉 (第x,y节)
                        .trim()
                parseWeeks(weekPart, weekKeys)
                // parseWeeks 已按 分号/逗号// 拆段，兼容 "4-5;7-16 周" / "1;3;5-8周" 等

                if (weekKeys.isEmpty()) {
                    // 首页 CourseTimeTable 无周次文本 → 默认整学期
                    weekKeys.add(Triple(1, 20, 0))
                }

                for ((wStart, wEnd, type) in weekKeys) {
                    result.add(Course(
                            name = name,
                            day = day,
                            room = room,
                            teacher = teacher,
                            startNode = startNode!!,
                            endNode = endNode!!,
                            startWeek = wStart,
                            endWeek = wEnd,
                            type = type
                    ))
                }
            } catch (t: Throwable) {
                // 单个 courseBox 解析失败跳过，不阻塞其他
                Log.w(TAG, "parseReactCourseBox skip one: ${t.message}")
            }
        }

        return result
    }
}

