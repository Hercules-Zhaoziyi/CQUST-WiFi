package top.msfxp.schedule.data.api

import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.URLEncoder

// 原始实践实验排课条目模型
data class RawPracticalItem(
    val courseCode: String = "",
    val name: String,
    val projectName: String,
    val day: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int>,
    val location: String,
    val teacher: String,
    val studentCount: Int = 0
)

// 实践学年学期模型
data class PracticalSemesterInfo(
    val yearTerm: String,
    val schoolYear: String,
    val name: String,
    val isCurrent: Boolean
)

// 实践课表抓取结果
data class PracticalFetchResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val yearTerm: String? = null,
    val items: List<RawPracticalItem> = emptyList()
)

// 实践课表解析器
object CqustPracticalParser {
    private const val SJJX_HOST = "sjjx.cqust.edu.cn"

    private val WEEKDAY_MAP = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '日' to 7, '天' to 7
    )
    private val TIME_REGEX = Regex("""([\d,\-~—至]+)\s*周\s*(?:星期|周)\s*([一二三四五六日天])\s*(\d+)\s*[-~—至]\s*(\d+)\s*节""")
    private val RANGE_REGEX = Regex("""^(\d+)\s*[-~至—]\s*(\d+)$""")

    // 获取并解析实践教学课表
    suspend fun fetchAndParse(
        session: CqustWebVpnSession,
        studentId: String,
        targetYearTerm: String? = null
    ): PracticalFetchResult {
        return try {
            val sjjxVpnBase = CqustVpnCrypto.buildVpnUrl("http", SJJX_HOST, "")

            // 1. 单点登录与三级 JS 跳转跟随
            val ssoEntryUrl = "$sjjxVpnBase/cqust_sso"
            val (actualSsoUrl, ssoResp) = session.getFollowingRedirects(ssoEntryUrl)
            if (ssoResp.status != HttpStatusCode.OK) {
                return PracticalFetchResult(success = false, errorMessage = "实践教学单点登录失败(HTTP ${ssoResp.status.value})")
            }

            // 解析实践系统基地址
            val actualBase = if (actualSsoUrl.contains("web.cqust.edu.cn")) {
                sjjxVpnBase
            } else {
                try {
                    val uri = java.net.URI(actualSsoUrl)
                    "${uri.scheme}://${uri.authority}"
                } catch (_: Exception) {
                    sjjxVpnBase
                }
            }
            session.sjjxBaseUrl = actualBase

            val ssoHtml = ssoResp.bodyAsText()
            val jsLocationMatch = Regex("""(?:self|window)\.location\s*=\s*['"]([^'"]+)['"]""").find(ssoHtml)
                ?: return PracticalFetchResult(success = false, errorMessage = "未能提取实践系统跳转票据")

            val step2Path = jsLocationMatch.groupValues[1]
            val step2Url = session.resolveUrl(actualSsoUrl, step2Path)
            val (step2ActualUrl, step2Resp) = session.getFollowingRedirects(step2Url, referer = actualSsoUrl)

            val step2Html = step2Resp.bodyAsText()
            val step3Match = Regex("""(?:self|window)\.location\s*=\s*['"]([^'"]+)['"]""").find(step2Html)
            val finalLoginUrl = if (step3Match != null && !step3Match.groupValues[1].contains("skin=")) {
                session.resolveUrl(step2ActualUrl, step3Match.groupValues[1])
            } else {
                "$actualBase/StuExpbook/login.jsp"
            }
            session.getFollowingRedirects(finalLoginUrl, referer = step2ActualUrl)

            // 2. 访问课表页面提取当前选中学年学期与学期列表
            val bookResultUrl = "$actualBase/StuExpbook/teach/bookResult.jsp"
            val (_, initResp) = session.getFollowingRedirects(bookResultUrl, referer = finalLoginUrl)
            val initHtml = initResp.bodyAsText()

            val (detectedCurrentTerm, _) = extractSemesters(initHtml)
            val courseCodeMap = extractCourseCodeMap(initHtml)
            val activeYearTerm = targetYearTerm?.ifBlank { null } ?: detectedCurrentTerm.orEmpty()
            if (activeYearTerm.isBlank()) {
                return PracticalFetchResult(success = false, errorMessage = "未能提取到实践教学有效学期")
            }

            val rawRows = mutableListOf<List<String>>()

            // 3. POST 请求第一页数据与分页参数
            val firstPageBody = "currYearterm=${URLEncoder.encode(activeYearTerm, "UTF-8")}&currTeachCourseCode=%25&currWeek=%25&page=1"
            val firstPageResp = session.post(
                url = bookResultUrl,
                body = firstPageBody,
                referer = bookResultUrl
            )
            val firstPageHtml = firstPageResp.bodyAsText()

            // 提取第一页数据与分页参数
            val (totalCount, pageSize) = extractPageInfo(firstPageHtml)
            extractTableRows(firstPageHtml, rawRows)

            // 4. 并发翻页抓取全部剩余页码条目
            if (totalCount > pageSize && pageSize > 0) {
                val totalPages = (totalCount + pageSize - 1) / pageSize
                coroutineScope {
                    val deferredPages = (2..totalPages).map { page ->
                        async {
                            val postBody = "currYearterm=${URLEncoder.encode(activeYearTerm, "UTF-8")}&currTeachCourseCode=%25&currWeek=%25&page=$page"
                            session.post(
                                url = bookResultUrl,
                                body = postBody,
                                referer = bookResultUrl
                            ).bodyAsText()
                        }
                    }.awaitAll()
                    for (pageHtml in deferredPages) {
                        extractTableRows(pageHtml, rawRows)
                    }
                }
            }

            // 5. 解析实践数据行
            val finalCodeMap = courseCodeMap.ifEmpty { extractCourseCodeMap(firstPageHtml) }
            val items = parseRowsToItems(rawRows, finalCodeMap)
            PracticalFetchResult(
                success = true,
                yearTerm = activeYearTerm,
                items = items
            )
        } catch (e: Exception) {
            PracticalFetchResult(success = false, errorMessage = "实践课表获取异常: ${e.message}")
        }
    }

    // 从页面提取实践学期列表与当前选中学期
    fun extractSemesters(html: String): Pair<String?, List<PracticalSemesterInfo>> {
        val selectMatch = Regex("""<select[^>]*name=["']?currYearterm["']?[^>]*>([\s\S]*?)</select>""", RegexOption.IGNORE_CASE).find(html)
            ?: return Pair(null, emptyList())
        val selectContent = selectMatch.groupValues[1]

        val optionRegex = Regex("""<option\s+([^>]*?)>([^<]*)</option>""", RegexOption.IGNORE_CASE)
        val list = mutableListOf<PracticalSemesterInfo>()
        var currentTerm: String? = null

        for (m in optionRegex.findAll(selectContent)) {
            val attrs = m.groupValues[1]
            val valueMatch = Regex("""value=["']?([^"'\s>]+)""", RegexOption.IGNORE_CASE).find(attrs)
            val value = valueMatch?.groupValues?.get(1)?.trim().orEmpty()
            if (value.isBlank()) continue

            val isSelected = attrs.contains("selected", ignoreCase = true)
            if (isSelected) {
                currentTerm = value
            }

            val parts = value.split('-')
            val (schoolYear, termName) = if (parts.size >= 3) {
                "${parts[0]}-${parts[1]}" to parts[2]
            } else {
                value to ""
            }

            list.add(
                PracticalSemesterInfo(
                    yearTerm = value,
                    schoolYear = schoolYear,
                    name = termName,
                    isCurrent = isSelected
                )
            )
        }

        if (currentTerm == null) {
            currentTerm = list.firstOrNull()?.yearTerm
        }

        return Pair(currentTerm, list)
    }

    // 从页面提取课程代码与课程名称的映射字典
    fun extractCourseCodeMap(html: String): Map<String, String> {
        val selectMatch = Regex("""<select[^>]*name=["']?currTeachCourseCode["']?[^>]*>([\s\S]*?)</select>""", RegexOption.IGNORE_CASE).find(html)
            ?: return emptyMap()
        val selectContent = selectMatch.groupValues[1]
        val optionRegex = Regex("""<option\s+[^>]*value=["']?([^"'\s>]+)["']?[^>]*>([^<]*)</option>""", RegexOption.IGNORE_CASE)

        val map = mutableMapOf<String, String>()
        for (m in optionRegex.findAll(selectContent)) {
            val code = m.groupValues[1].trim()
            val text = m.groupValues[2].trim()
            if (code.isBlank() || code == "%") continue

            val courseName = if (text.contains("-")) {
                text.substringAfter("-").trim()
            } else {
                text
            }
            if (courseName.isNotBlank()) {
                map[courseName] = code
                val stripped = courseName.replace(Regex("""^[（(].*?[）)]"""), "").trim()
                if (stripped.isNotBlank() && stripped != courseName) {
                    map.putIfAbsent(stripped, code)
                }
            }
        }
        return map
    }

    // 提取分页栏总条数与单页数量
    private fun extractPageInfo(html: String): Pair<Int, Int> {
        val match = Regex("""createPageBar\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)""").find(html)
        return if (match != null) {
            val total = match.groupValues[1].toIntOrNull() ?: 0
            val size = match.groupValues[2].toIntOrNull() ?: 10
            total to size
        } else {
            0 to 10
        }
    }

    // 从 HTML 表格中提取 12 列的数据行
    private fun extractTableRows(html: String, output: MutableList<List<String>>) {
        val trRegex = Regex("""<tr[^>]*>([\s\S]*?)</tr>""", RegexOption.IGNORE_CASE)
        val tdRegex = Regex("""<td[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE)
        val tagStrip = Regex("""<[^>]+>""")

        for (tr in trRegex.findAll(html)) {
            val content = tr.groupValues[1]
            if (content.contains("<th", ignoreCase = true)) continue
            val cells = tdRegex.findAll(content).map {
                tagStrip.replace(it.groupValues[1], "")
                    .replace("&nbsp;", " ")
                    .trim()
            }.toList()
            if (cells.size >= 10 && cells[0].isNotEmpty() && cells[0] != "课程名称") {
                output.add(cells)
            }
        }
    }

    // 将解析出的表格行转换为原始排课模型
    private fun parseRowsToItems(
        rows: List<List<String>>,
        courseCodeMap: Map<String, String> = emptyMap()
    ): List<RawPracticalItem> {
        val list = mutableListOf<RawPracticalItem>()
        for (r in rows) {
            val courseName = r[0]
            val projectName = r[1]
            val timeText = r[2]
            val position = r[3]
            val teacher = r[4]
            val selectedCount = r.getOrNull(9)?.toIntOrNull() ?: 0

            val parsedTime = parseTimeText(timeText) ?: continue
            val code = courseCodeMap[courseName]
                ?: courseCodeMap[courseName.replace(Regex("""^[（(].*?[）)]"""), "").trim()]
                ?: ""

            list.add(
                RawPracticalItem(
                    courseCode = code,
                    name = courseName,
                    projectName = projectName,
                    day = parsedTime.day,
                    startSection = parsedTime.startSection,
                    endSection = parsedTime.endSection,
                    weeks = parsedTime.weeks,
                    location = position,
                    teacher = teacher,
                    studentCount = selectedCount
                )
            )
        }
        return list
    }

    private data class ParsedTime(
        val day: Int,
        val startSection: Int,
        val endSection: Int,
        val weeks: List<Int>
    )

    // 解析上课时间文本
    private fun parseTimeText(text: String): ParsedTime? {
        val match = TIME_REGEX.find(text) ?: return null
        val weeksStr = match.groupValues[1]
        val dayChar = match.groupValues[2].firstOrNull() ?: return null
        val startSec = match.groupValues[3].toIntOrNull() ?: return null
        val endSec = match.groupValues[4].toIntOrNull() ?: return null

        val day = WEEKDAY_MAP[dayChar] ?: return null
        val weeks = parseWeeksList(weeksStr)
        if (weeks.isEmpty()) return null

        return ParsedTime(day, startSec, endSec, weeks)
    }

    // 解析周次片段列表
    private fun parseWeeksList(raw: String): List<Int> {
        val list = mutableListOf<Int>()
        val parts = raw.split(',', '，', '、')
        for (p in parts) {
            val trimmed = p.trim()
            val rangeMatch = RANGE_REGEX.find(trimmed)
            if (rangeMatch != null) {
                val s = rangeMatch.groupValues[1].toIntOrNull()
                val e = rangeMatch.groupValues[2].toIntOrNull()
                if (s != null && e != null && s <= e) {
                    for (w in s..e) list.add(w)
                }
            } else {
                trimmed.toIntOrNull()?.let { list.add(it) }
            }
        }
        return list.distinct().sorted()
    }
}
