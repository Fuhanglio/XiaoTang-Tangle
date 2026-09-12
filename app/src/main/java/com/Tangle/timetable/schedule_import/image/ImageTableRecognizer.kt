package com.Tangle.timetable.schedule_import.image

import com.Tangle.timetable.schedule_import.bean.Course
import com.Tangle.timetable.utils.TessOcrUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 课表图片识别：把 OCR 输出的「词 + 坐标」还原成课程列表。
 *
 * 不去识别表格线，只靠文字的几何位置推算：
 *   1) 找日期表头（周一 ~ 周日），它们的横向位置就是 7 列的锚点；
 *   2) 找左侧节次列（1 ~ 12 / 第1节），它们的纵向位置就是每节课的行锚点；
 *   3) 其余文字按「最近的列 + 纵向相邻」聚成一个一个课程块；
 *   4) 每个块里按行拆文本，分出课名 / 教室 / 周次；
 *   5) 块的纵向跨度决定它占第几节到第几节。
 *
 * 表头缺失时降级为「按宽度等分 7 列」，节次列缺失时降级为「按高度等分」，
 * 并在 message 里说明，方便用户判断结果可不可信。
 */
object ImageTableRecognizer {

    /** [message] 用于向用户说明识别情况：成功条数，或失败原因与建议。 */
    data class Result(val courses: List<Course>, val message: String)

    private class DayAnchor(val day: Int, val x: Float, val y: Float)

    private class Block(val day: Int) {
        val words = arrayListOf<TessOcrUtils.Word>()
        var top = 0
        var bottom = 0
        val centerY: Float get() = (top + bottom) / 2f
        val height: Int get() = bottom - top
    }

    private class DayHeader(val xs: FloatArray, val colWidth: Float, val bottom: Int)

    private class NodeHeader(val firstNode: Int, val lastNode: Int, val a: Float, val b: Float, val right: Int) {
        /** 第 n 节所在行的中心 y。 */
        fun yOf(n: Int): Float = a + b * n
    }

    private class ParsedText(val name: String, val room: String,
                             val startWeek: Int, val endWeek: Int, val type: Int)

    /* ----------------------------- 正则 ----------------------------- */

    private val DAY_LABEL = Regex("(周|星期|礼拜)\\s*([一二三四五六日天1-7])")
    private val NODE_LABEL = Regex("^第?\\s*(\\d{1,2})\\s*节?$")
    private val TIME_LABEL = Regex("\\d{1,2}\\s*[:：]\\s*\\d{1,2}")
    private val NUMBER = Regex("\\d")
    private val WEEK_RANGE = Regex("(\\d{1,2})\\s*[-~～－—至]\\s*(\\d{1,2})")

    /** 教室：A1-414 / 教1-203 / A414 / 101 / 1204 */
    private val ROOM_STRICT = Regex("^.{0,3}\\d{1,2}\\s*[-－]\\s*\\d{3,4}$|^[A-Za-z]\\d{2,4}$|^\\d{3,4}$")
    /** 教室：含明确场所词 */
    private val ROOM_WORDS = Regex("(教学楼|实验楼|实验室|机房|体育馆|运动场|实训室|报告厅|会议室|综合楼)")
    /** 教室：以场所字结尾的短串（A楼 / 体育馆 / 操场 / 阶梯教室） */
    private val ROOM_SUFFIX = Regex("^.{0,4}(楼|馆|场|室|厅|区|栋)$")

    private val DIGIT_CN = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '日' to 7, '天' to 7)

    /**
     * @param defaultMaxWeek 解析不到周次时使用的结束周（一般取课表的学期总周数）
     * @param maxNode        每天最多几节课（用于无节次锚点时的等分推算）
     */
    fun recognize(words: List<TessOcrUtils.Word>, imgWidth: Int, imgHeight: Int,
                 defaultMaxWeek: Int, maxNode: Int): Result {
        if (words.isEmpty()) return Result(arrayListOf(), "没有从图片里识别到任何文字")
        if (imgWidth < 200 || imgHeight < 200) return Result(arrayListOf(), "图片太小")

        val heights = words.map { it.boxHeight }.filter { it > 0 }.sorted()
        if (heights.isEmpty()) return Result(arrayListOf(), "文字尺寸异常")
        val medH = heights[heights.size / 2].toFloat()
        if (medH < 6f) return Result(arrayListOf(), "文字太小，识别不可靠。请换一张更清晰的课表截图")

        val safeMaxWeek = if (defaultMaxWeek in 1..40) defaultMaxWeek else 20
        val safeMaxNode = if (maxNode in 4..30) maxNode else 12

        val dayHeader = findDayHeader(words, medH)
        val nodeLeftBound = dayHeader?.let { it.xs[1] - it.colWidth * 0.5f }
                ?: (imgWidth * 0.18f)
        val nodeHeader = findNodeHeader(words, medH, nodeLeftBound)

        val headerBottom = dayHeader?.bottom ?: 0

        val blocks = clusterBlocks(words, dayHeader, nodeHeader, imgWidth, medH, headerBottom)
        if (blocks.isEmpty()) return Result(arrayListOf(), "没能从图片里还原出课表格子")

        val rowH = if (nodeHeader != null) nodeHeader.b else 0f
        val courses = arrayListOf<Course>()
        val seen = HashSet<String>()

        for (b in blocks) {
            val lines = linesOf(b, medH)
            if (lines.isEmpty()) continue
            val parsed = parseText(lines, safeMaxWeek)
            if (parsed.name.isEmpty()) continue
            if (parsed.name.length < 2) continue

            val range = nodeRange(b, nodeHeader, rowH, imgHeight, headerBottom, safeMaxNode)
            val key = "${b.day}|${range[0]}|${range[1]}|${parsed.name}"
            if (!seen.add(key)) continue

            courses.add(Course(
                    name = parsed.name,
                    day = b.day,
                    room = parsed.room,
                    teacher = "",
                    startNode = range[0],
                    endNode = range[1],
                    startWeek = parsed.startWeek,
                    endWeek = parsed.endWeek,
                    type = parsed.type
            ))
        }

        if (courses.isEmpty()) {
            return Result(arrayListOf(),
                    "没能从图片里还原出课程。建议：截图时保留顶部的「周一~周日」和左侧的节次列，"
                            + "尽量避免倾斜、遮挡和压缩。也可以换一张更清晰的图片试试。")
        }

        val notes = StringBuilder()
        notes.append("识别到 ").append(courses.size).append(" 门课程。")
        if (dayHeader == null) notes.append("（没找到星期表头，已按左右等分推算，可能有偏差）")
        if (nodeHeader == null) notes.append("（没找到节次列，已按上下等分推算，可能有偏差）")
        notes.append("请核对下面的列表，无误后点「确认导入」。")
        return Result(courses, notes.toString())
    }

    /* ------------------------------------------------------------------ */
    /* 锚点                                                                */
    /* ------------------------------------------------------------------ */

    private fun dayOfToken(t: String): Int {
        if (t.length != 1) return -1
        val c = t[0]
        if (c in '1'..'7') return c - '0'
        return DIGIT_CN[c] ?: -1
    }

    private fun findDayHeader(words: List<TessOcrUtils.Word>, medH: Float): DayHeader? {
        val raw = arrayListOf<DayAnchor>()
        for (w in words) {
            val m = DAY_LABEL.find(w.text) ?: continue
            val d = dayOfToken(m.groupValues[2])
            if (d > 0) raw.add(DayAnchor(d, w.centerX.toFloat(), w.centerY.toFloat()))
        }
        if (raw.size < 5) return null

        // 表头应该在同一条水平线上，取 y 最集中的一组
        val sortedY = raw.map { it.y }.sorted()
        val medY = sortedY[sortedY.size / 2]
        val near = raw.filter { abs(it.y - medY) <= medH * 3f }
        if (near.map { it.day }.distinct().size < 5) return null

        val xs = FloatArray(8)
        for (d in 1..7) {
            val list = near.filter { it.day == d }
            if (list.isEmpty()) continue
            val lx = list.map { it.x }.sorted()
            xs[d] = lx[lx.size / 2]
        }

        val known = (1..7).filter { xs[it] > 0f }
        if (known.size < 5) return null
        for (i in 1 until known.size) {
            if (xs[known[i]] <= xs[known[i - 1]]) return null
        }
        val step = (xs[known.last()] - xs[known.first()]) / (known.last() - known.first())
        if (step < medH * 2f) return null
        for (d in 1..7) {
            if (xs[d] <= 0f) xs[d] = xs[known.first()] + (d - known.first()) * step
        }
        val bottom = near.map { it.y }.max()!!.toInt() + (medH * 1.1f).toInt()
        return DayHeader(xs, step, bottom)
    }

    /**
     * 找节次锚点。做法：先在左侧区域收集「像节次号」的词，再用最小二乘拟合
     * y = a + b * n，这样个别错识别不会把整条轴带偏。
     */
    private fun findNodeHeader(words: List<TessOcrUtils.Word>, medH: Float, leftBound: Float): NodeHeader? {
        val candidates = arrayListOf<Pair<Int, TessOcrUtils.Word>>()
        for (w in words) {
            if (w.centerX.toFloat() >= leftBound) continue
            val t = w.text.trim()
            if (TIME_LABEL.containsMatchIn(t)) continue
            val m = NODE_LABEL.find(t) ?: continue
            val n = m.groupValues[1].toIntOrNull() ?: continue
            if (n in 1..30) candidates.add(n to w)
        }
        if (candidates.size < 4) return null

        // 同一节次号被识别出多次时取 y 中位数
        val grouped = candidates.groupBy { it.first }
        val pairs = arrayListOf<Pair<Int, Float>>()
        for ((n, ws) in grouped) {
            val ys = ws.map { it.second.centerY.toFloat() }.sorted()
            pairs.add(n to ys[ys.size / 2])
        }
        if (pairs.size < 4) return null
        val sorted = pairs.sortedBy { it.second }
        for (i in 1 until sorted.size) {
            if (sorted[i].first <= sorted[i - 1].first) return null
        }

        // 最小二乘拟合 y = a + b * n
        val nMean = sorted.map { it.first.toFloat() }.sum() / sorted.size
        val yMean = sorted.map { it.second }.sum() / sorted.size
        var num = 0f
        var den = 0f
        for (p in sorted) {
            num += (p.first - nMean) * (p.second - yMean)
            den += (p.first - nMean) * (p.first - nMean)
        }
        if (den <= 0f) return null
        val b = num / den
        if (b < medH * 1.2f) return null        // 行高比字还小，锚点不可信
        val a = yMean - b * nMean

        val right = candidates.map { it.second.right }.max()!! + (medH * 0.4f).toInt()
        return NodeHeader(sorted.first().first, sorted.last().first, a, b, right)
    }

    /* ------------------------------------------------------------------ */
    /* 课程块聚类                                                          */
    /* ------------------------------------------------------------------ */

    private fun clusterBlocks(words: List<TessOcrUtils.Word>, dayHeader: DayHeader?, nodeHeader: NodeHeader?,
                              imgWidth: Int, medH: Float, headerBottom: Int): List<Block> {
        val colX = FloatArray(8)
        var colSpan: Float
        if (dayHeader != null) {
            for (d in 1..7) colX[d] = dayHeader.xs[d]
            colSpan = dayHeader.colWidth
        } else {
            val left = if (nodeHeader != null) nodeHeader.right.toFloat() else 0f
            val w = (imgWidth - left) / 7f
            for (d in 1..7) colX[d] = left + w * (d - 0.5f)
            colSpan = w
        }
        if (colSpan <= 0f) colSpan = imgWidth / 7f

        // 分组：day -> 词列表
        val buckets = arrayListOf<ArrayList<TessOcrUtils.Word>>()
        for (i in 0..7) buckets.add(arrayListOf())

        for (w in words) {
            if (DAY_LABEL.containsMatchIn(w.text)) continue
            if (w.centerY.toFloat() <= headerBottom) continue
            if (nodeHeader != null && w.centerX < nodeHeader.right) continue
            val t = w.text.trim()
            // 节次号、时间这种纯数字不会是课程内容，无论有没有锚点都排除
            if (NODE_LABEL.matches(t) || TIME_LABEL.containsMatchIn(t)) continue

            var bestDay = 0
            var bestD = Float.MAX_VALUE
            for (d in 1..7) {
                val dist = abs(w.centerX.toFloat() - colX[d])
                if (dist < bestD) {
                    bestD = dist
                    bestDay = d
                }
            }
            if (bestDay == 0 || bestD > colSpan * 0.62f) continue
            buckets[bestDay].add(w)
        }

        val rowH = if (nodeHeader != null) nodeHeader.b else medH * 2.5f
        var gap = medH * 1.05f
        if (rowH > 0f) gap = min(gap, rowH * 0.5f)
        if (gap < medH * 0.6f) gap = medH * 0.6f

        val blocks = arrayListOf<Block>()
        for (d in 1..7) {
            val list = buckets[d].sortedBy { it.centerY.toFloat() }
            var cur: Block? = null
            for (w in list) {
                val open = cur
                if (open == null) {
                    val nb = Block(d)
                    nb.words.add(w)
                    cur = nb
                } else if (w.top - open.words.last().bottom <= gap) {
                    open.words.add(w)
                } else {
                    blocks.add(open)
                    val nb = Block(d)
                    nb.words.add(w)
                    cur = nb
                }
            }
            val tail = cur
            if (tail != null) blocks.add(tail)
        }

        for (b in blocks) {
            b.top = b.words.map { it.top }.min()!!
            b.bottom = b.words.map { it.bottom }.max()!!
        }
        return blocks
    }

    /* ------------------------------------------------------------------ */
    /* 文本解析                                                            */
    /* ------------------------------------------------------------------ */

    private fun linesOf(block: Block, medH: Float): List<String> {
        val sorted = block.words.sortedBy { it.centerY.toFloat() }
        val groups = arrayListOf<ArrayList<TessOcrUtils.Word>>()
        for (w in sorted) {
            val last = groups.lastOrNull()
            if (last != null && abs(w.centerY - last.last().centerY) <= medH * 0.65f) {
                last.add(w)
            } else {
                groups.add(arrayListOf(w))
            }
        }
        return groups.map { g ->
            val sb = StringBuilder()
            var lastRight = Int.MIN_VALUE
            for (w in g.sortedBy { it.left }) {
                if (sb.isNotEmpty() && w.left - lastRight > medH * 0.3f) sb.append(' ')
                sb.append(w.text)
                lastRight = w.right
            }
            sb.toString().trim()
        }.filter { it.isNotEmpty() }
    }

    private fun parseText(lines: List<String>, defaultMaxWeek: Int): ParsedText {
        var room = ""
        var weekS = 1
        var weekE = defaultMaxWeek
        var type = 0
        val nameParts = arrayListOf<String>()

        for (line in lines) {
            val t = line.trim()
            if (t.isEmpty()) continue
            if (isWeekText(t)) {
                val w = parseWeeks(t, defaultMaxWeek)
                weekS = w[0]
                weekE = w[1]
                type = w[2]
                continue
            }
            if (room.isEmpty() && isRoomText(t)) {
                room = t
                continue
            }
            nameParts.add(t)
        }

        var name = nameParts.joinToString(" ")
        if (name.length > 24) name = name.substring(0, 24)
        if (name.isEmpty() && room.isNotEmpty()) {
            name = room
            room = ""
        }
        return ParsedText(name, room, weekS, weekE, type)
    }

    private fun isWeekText(t: String): Boolean {
        if (t.length > 20) return false
        if (!t.contains('周')) return false
        if (NUMBER.containsMatchIn(t)) return true
        return t.contains('单') || t.contains('双')
    }

    private fun isRoomText(t: String): Boolean {
        if (t.length > 20) return false
        if (t.startsWith("@")) return true
        val body = if (t.startsWith("@")) t.substring(1) else t
        if (ROOM_STRICT.matches(body)) return true
        if (ROOM_WORDS.containsMatchIn(t)) return true
        if (ROOM_SUFFIX.matches(t)) return true
        return false
    }

    /** 返回 [startWeek, endWeek, type]，type: 0 每周 / 1 单周 / 2 双周。 */
    private fun parseWeeks(t: String, defaultMaxWeek: Int): IntArray {
        val compact = t.replace(" ", "").replace("　", "")
                .replace('－', '-').replace('—', '-').replace('～', '-').replace('~', '-').replace('至', '-')
        var s = 1
        var e = defaultMaxWeek
        val m = WEEK_RANGE.find(compact)
        if (m != null) {
            s = m.groupValues[1].toIntOrNull() ?: 1
            e = m.groupValues[2].toIntOrNull() ?: defaultMaxWeek
        } else {
            val single = Regex("(\\d{1,2})\\s*周").find(compact)
            val n = single?.groupValues?.get(1)?.toIntOrNull()
            if (n != null) {
                s = n
                e = n
            }
        }
        if (s < 1) s = 1
        if (e < s) {
            val tmp = s
            s = e
            e = tmp
        }
        if (e > 40) e = 40
        val type = when {
            t.contains('单') -> 1
            t.contains('双') -> 2
            else -> 0
        }
        return intArrayOf(s, e, type)
    }

    /* ------------------------------------------------------------------ */
    /* 节次范围                                                            */
    /* ------------------------------------------------------------------ */

    private fun nodeRange(block: Block, nodeHeader: NodeHeader?, rowH: Float,
                          imgHeight: Int, headerBottom: Int, maxNode: Int): IntArray {
        if (nodeHeader != null && rowH > 0f) {
            val covered = arrayListOf<Int>()
            for (n in nodeHeader.firstNode..nodeHeader.lastNode) {
                val y = nodeHeader.yOf(n)
                val top = y - rowH / 2f
                val bottom = y + rowH / 2f
                val overlap = min(block.bottom.toFloat(), bottom) - max(block.top.toFloat(), top)
                if (overlap > rowH * 0.4f) covered.add(n)
            }
            if (covered.isNotEmpty()) {
                val s = covered.min()!!
                var e = covered.max()!!
                // 文字偏少但格子偏高时，按高度补足跨度
                val spanByHeight = (block.height / rowH).roundToInt()
                if (spanByHeight > (e - s + 1)) e = min(maxNode, s + spanByHeight - 1)
                return intArrayOf(s, e)
            }
            // 一行都没压上：取最近的锚点
            var best = nodeHeader.firstNode
            var bestD = Float.MAX_VALUE
            for (n in nodeHeader.firstNode..nodeHeader.lastNode) {
                val d = abs(nodeHeader.yOf(n) - block.centerY)
                if (d < bestD) {
                    bestD = d
                    best = n
                }
            }
            return intArrayOf(best, best)
        }

        // 没有节次锚点：按图高等分
        val usable = (imgHeight - headerBottom).toFloat().coerceAtLeast(1f)
        val h = usable / maxNode
        val s = ((block.top - headerBottom) / h).toInt().coerceIn(0, maxNode - 1) + 1
        val e = ((block.bottom - headerBottom) / h).toInt().coerceIn(0, maxNode - 1) + 1
        return intArrayOf(s, max(s, e))
    }

}
