package top.msfxp.schedule.data.api

import io.ktor.client.statement.*
import top.msfxp.schedule.data.model.EXAM_TYPE_DEFAULT
import top.msfxp.schedule.data.model.ExamFetchResult
import top.msfxp.schedule.data.model.RawExamItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// 教务考试信息解析器
object CqustExamParser {
    private const val JWNEW_HOST = "jwnew.cqust.edu.cn"
    private const val EXAM_PATH = "/stdExamTable!examTable.action"

    private val WEEKDAY_MAP = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4,
        "五" to 5, "六" to 6, "日" to 7, "天" to 7
    )
    private val ARRANGE_REGEX = Regex(
        """第\s*(\d+)\s*周\s*星期\s*([一二三四五六日天])\s*(\d{1,2})\s*[:：]\s*(\d{2})\s*[-~—至]\s*(\d{1,2})\s*[:：]\s*(\d{2})"""
    )
    private val ROW_REGEX = Regex("""<tr[^>]*>([\s\S]*?)</tr>""", RegexOption.IGNORE_CASE)
    private val CELL_REGEX = Regex("""<td[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE)
    private val TOOLBAR_REGEX = Regex("""bg\.ui\.toolbar\([^,]+,\s*['"]([^'"]+)['"]\s*\)""")
    private val BREAK_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val TAG_REGEX = Regex("""<[^>]+>""")

    // 抓取并解析当前学期考试安排
    suspend fun fetchAndParse(session: CqustWebVpnSession): ExamFetchResult {
        val baseUrl = session.eamsBaseUrl ?: CqustVpnCrypto.buildVpnUrl("http", JWNEW_HOST, "/eams")
        return try {
            val referer = "$baseUrl$EXAM_PATH"
            val url = "$baseUrl$EXAM_PATH?vpn-12-o1-$JWNEW_HOST&_=${System.currentTimeMillis()}"
            val html = session.get(
                url = url,
                referer = referer,
                isAjax = true,
                headers = mapOf("Accept" to "text/html, */*; q=0.01")
            ).bodyAsText()

            if (isSessionExpired(html)) {
                return ExamFetchResult(success = false, errorMessage = "教务会话已失效", isSessionExpired = true)
            }
            if (!html.contains("formTable", ignoreCase = true)) {
                return ExamFetchResult(success = false, errorMessage = "考试页面结构异常")
            }
            ExamFetchResult(success = true, items = parseExamHtml(html))
        } catch (e: Exception) {
            ExamFetchResult(success = false, errorMessage = "考试信息获取异常: ${e.message}")
        }
    }

    // 解析考试 HTML 片段，批次名取自页面工具栏
    fun parseExamHtml(html: String): List<RawExamItem> {
        val examType = TOOLBAR_REGEX.find(html)?.groupValues?.get(1)?.trim().orEmpty()
            .ifBlank { EXAM_TYPE_DEFAULT }
        return ROW_REGEX.findAll(html)
            .mapNotNull { parseRow(it.groupValues[1], examType) }
            .sortedWith(compareBy({ if (it.isScheduled) 0 else 1 }, { it.examDate.orEmpty() }, { it.startTime.orEmpty() }, { it.sequence }))
            .toList()
    }

    // 解析单行考试记录
    private fun parseRow(rowHtml: String, examType: String): RawExamItem? {
        val cells = CELL_REGEX.findAll(rowHtml).map { cleanCellText(it.groupValues[1]) }.toList()
        if (cells.isEmpty()) return null
        val sequence = cells[0].toIntOrNull() ?: return null
        if (cells.size < 4 || cells[1].isBlank()) return null

        val base = RawExamItem(
            sequence = sequence,
            courseCode = cells[1],
            courseName = cells[2],
            examType = examType
        )
        // 非标行：考试日期至其它说明合并为单个单元格
        if (cells.size < 9) {
            val remark = cells.getOrElse(3) { "" }
            return base.copy(remark = remark, arrangeText = remark)
        }
        return base.copy(
            examDate = normalizeDate(cells[3]),
            position = cells[5].ifBlank { null },
            seatNo = cells[6].ifBlank { null },
            status = cells[7].ifBlank { null },
            remark = cells[8],
            arrangeText = cells[4]
        ).withArrange(cells[4])
    }

    // 解析考试安排中的周次与起止时间
    private fun RawExamItem.withArrange(arrangeText: String): RawExamItem {
        val match = ARRANGE_REGEX.find(arrangeText) ?: return this
        return copy(
            week = match.groupValues[1].toIntOrNull(),
            weekday = WEEKDAY_MAP[match.groupValues[2]],
            startTime = "%02d:%s".format(match.groupValues[3].toInt(), match.groupValues[4]),
            endTime = "%02d:%s".format(match.groupValues[5].toInt(), match.groupValues[6])
        )
    }

    // 校验并规范化考试日期
    private fun normalizeDate(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val date = runCatching { LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
        return date?.toString()
    }

    // 清洗单元格文本，保留换行
    private fun cleanCellText(cellHtml: String): String {
        val withBreaks = BREAK_REGEX.replace(cellHtml, "\n")
        return decodeEntities(TAG_REGEX.replace(withBreaks, ""))
            .replace("\r\n", "\n")
            .lines()
            .map { it.replace(Regex("""[ \t\u00a0]+"""), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    // 解码常见 HTML 实体
    private fun decodeEntities(text: String): String = text
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'")
        .replace("&amp;", "&", ignoreCase = true)

    // 判断教务会话是否失效
    private fun isSessionExpired(html: String): Boolean {
        if (html.isBlank()) return true
        return html.contains("loginForm") ||
            html.contains("cqustadminweb=1") ||
            html.contains("authserver/login")
    }
}
