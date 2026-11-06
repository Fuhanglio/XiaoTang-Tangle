package com.Tangle.timetable.update

/**
 * Release notes 正文清洗（J）：只服务于「检查更新」弹窗展示，不改变原始 notes。
 *
 * 原实现把 release body 的前 800 个字符原样塞进 setMessage，于是 `###`、`> `、
 * 反引号、`**`、表格竖线全部裸露，点击链接文字还会被拆成多行。
 * 这里做三件事：
 *   ① 去掉行首的标题/引用/列表标记，以及行内的 `**`、`__`、反引号、`|`；
 *   ② 连续空格压成一个、丢掉空行与分隔线；
 *   ③ 最多保留 maxLines 行，超出时末行换成「…… 共 N 行」，保证弹窗高度可控。
 */
object ReleaseNotesFormatter {

    private const val DEFAULT_MAX_LINES = 12

    private val HEADING = Regex("^#{1,6}\\s*")
    private val QUOTE = Regex("^>\\s*")
    private val BULLET = Regex("^[-*+]\\s+")
    private val HORIZONTAL_RULE = Regex("^[-*_]{3,}$")
    private val MULTI_SPACE = Regex("[ \\t]{2,}")

    fun format(raw: String?, maxLines: Int = DEFAULT_MAX_LINES): String {
        val lines = raw.orEmpty()
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { HORIZONTAL_RULE.matches(it) }
            .map { line ->
                val stripped = stripPrefixes(line)
                MULTI_SPACE.replace(
                    stripped
                        .replace("**", "")
                        .replace("__", "")
                        .replace("`", "")
                        .replace("|", " ")
                        .trim(),
                    " "
                )
            }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) return ""
        if (lines.size <= maxLines) return lines.joinToString("\n")

        // 超出：保留 maxLines - 1 行正文 + 一行提示，总行数仍不超过 maxLines
        val head = lines.take((maxLines - 1).coerceAtLeast(0))
        return (head + "…… 共 ${lines.size} 行").joinToString("\n")
    }

    /**
     * 反复剥离行首标记，覆盖 `> ### 标题`、`- **重点**` 这类嵌套写法。
     * 最多三轮足够，避免异常输入把循环卡死。
     */
    private fun stripPrefixes(input: String): String {
        var s = input
        repeat(3) {
            val before = s
            s = HEADING.replace(s, "")
            s = QUOTE.replace(s, "")
            s = BULLET.replace(s, "")
            if (s == before) return s
        }
        return s
    }
}
