package top.msfxp.schedule.data.api

import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import top.msfxp.schedule.data.model.Semester
import top.msfxp.schedule.data.model.StudentProfile

// 原始教务课程排课条目模型
data class RawEamsCourse(
    val courseCode: String = "",
    val name: String,
    val credit: String = "",
    val category: String = "",
    val studentCount: Int = 0,
    val teacher: String = "",
    val position: String = "",
    val day: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int> = emptyList(),
    val remark: String = ""
)

// 教务抓取解析结果
data class EamsFetchResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val studentId: String? = null,
    val semesterId: String? = null,
    val profile: StudentProfile = StudentProfile(),
    val semesters: List<Semester> = emptyList(),
    val courses: List<RawEamsCourse> = emptyList(),
    val statusCode: Int? = null,
    val isTimeout: Boolean = false,
    val isNetworkError: Boolean = false
)

// 课表入口数据模型
private data class CourseTableEntryInfo(
    val tagId: String,
    val defaultSemesterId: String,
    val scheduleIds: String
)

// 单源教务系统解析器
object CqustEamsParser {
    private const val JWNEW_HOST = "jwnew.cqust.edu.cn"

    // 抓取并解析教务系统课表数据
    suspend fun fetchAndParse(
        session: CqustWebVpnSession,
        studentId: String,
        targetSemesterId: String? = null,
        onProgress: ((String) -> Unit)? = null
    ): EamsFetchResult {
        return try {
            val baseUrl = session.eamsBaseUrl ?: CqustVpnCrypto.buildVpnUrl("http", JWNEW_HOST, "/eams")

            // 1. 并发获取学生档案与课表入口信息
            onProgress?.invoke("正在获取学生档案与学期日历")
            val (profile, entryInfo) = coroutineScope {
                val profileDeferred = async { fetchStudentProfile(session, baseUrl, studentId) }
                val entryDeferred = async { fetchCourseTableEntry(session, baseUrl) }
                profileDeferred.await() to entryDeferred.await()
            }

            if (entryInfo.scheduleIds.isBlank()) {
                return EamsFetchResult(
                    success = false,
                    errorMessage = "未能获取到学生排课标识（ids）"
                )
            }

            // 2. 查询学期日历
            val (dynamicSemId, semesterList) = fetchSemesterCalendar(
                session = session,
                baseUrl = baseUrl,
                tagId = entryInfo.tagId,
                targetSemesterId = targetSemesterId.orEmpty(),
                defaultSemesterId = entryInfo.defaultSemesterId
            )
            val detectedSemesterId = targetSemesterId?.ifBlank { null }
                ?: dynamicSemId.ifBlank { semesterList.find { it.isCurrent }?.id ?: semesterList.firstOrNull()?.id.orEmpty() }

            // 3. 并发下载课表正文与培养计划
            onProgress?.invoke("正在获取并解析课表正文")
            val (tableHtml, planMap) = coroutineScope {
                val tableDeferred = async {
                    val tableBody = "ignoreHead=1&setting.kind=std&startWeek=1&project.id=1&semester.id=$detectedSemesterId&ids=${entryInfo.scheduleIds}"
                    session.post(
                        url = "$baseUrl/courseTableForStd!courseTable.action",
                        body = tableBody,
                        referer = "$baseUrl/courseTableForStd.action"
                    ).bodyAsText()
                }
                val planDeferred = async {
                    fetchTeachingPlan(session, baseUrl, detectedSemesterId)
                }
                tableDeferred.await() to planDeferred.await()
            }

            if (!tableHtml.contains("TaskActivity") && !tableHtml.contains("未安排时间任务列表")) {
                if (tableHtml.isBlank() || tableHtml.contains("loginForm") || tableHtml.contains("cqustadminweb=1")) {
                    return EamsFetchResult(
                        success = false,
                        errorMessage = "教务会话已失效或课表页面不可用",
                        isNetworkError = false
                    )
                }
                return EamsFetchResult(
                    success = true,
                    studentId = studentId,
                    semesterId = detectedSemesterId,
                    profile = profile,
                    semesters = semesterList,
                    courses = emptyList()
                )
            }

            // 4. 解析课表正文及未安排时间任务列表并合并培养方案属性
            val parsedCourses = parseCourseTableHtml(tableHtml)
            val finalCourses = parsedCourses.map { c ->
                val info = planMap[c.courseCode]
                if (info != null) {
                    c.copy(
                        category = info.category.ifBlank { c.category },
                        credit = info.credit.ifBlank { c.credit },
                        studentCount = if (info.studentCount > 0) info.studentCount else c.studentCount
                    )
                } else {
                    c
                }
            }

            EamsFetchResult(
                success = true,
                studentId = studentId,
                semesterId = detectedSemesterId,
                profile = profile,
                semesters = semesterList,
                courses = finalCourses
            )
        } catch (e: Exception) {
            EamsFetchResult(
                success = false,
                errorMessage = "教务课表解析异常: ${e.message}",
                isNetworkError = true
            )
        }
    }

    // 提取学生个人信息
    private suspend fun fetchStudentProfile(
        session: CqustWebVpnSession,
        baseUrl: String,
        studentId: String
    ): StudentProfile {
        return try {
            val (_, resp) = session.getFollowingRedirects("$baseUrl/stdDetail.action")
            val html = resp.bodyAsText()
            fun extractField(fieldName: String): String {
                val regex = Regex("""$fieldName\s*：\s*</td>\s*<td[^>]*>\s*([^<]+)""")
                return regex.find(html)?.groupValues?.get(1)?.trim().orEmpty()
            }
            StudentProfile(
                studentId = studentId,
                name = extractField("姓名"),
                college = extractField("院系"),
                major = extractField("专业"),
                className = extractField("行政班级")
            )
        } catch (_: Exception) {
            StudentProfile(studentId = studentId)
        }
    }

    // 一次性提取课表入口配置与排课标识
    private suspend fun fetchCourseTableEntry(
        session: CqustWebVpnSession,
        baseUrl: String
    ): CourseTableEntryInfo {
        return try {
            val (_, pageResp) = session.getFollowingRedirects("$baseUrl/courseTableForStd.action")
            val pageHtml = pageResp.bodyAsText()

            val tagIdMatch = Regex("""id=["'](semesterBar\d+Semester)["']""").find(pageHtml)
            val tagId = tagIdMatch?.groupValues?.get(1) ?: "semesterBar"

            val pageSemId = Regex("""semesterCalendar\([^)]*value\s*:\s*["']?(\d+)["']?""").find(pageHtml)?.groupValues?.get(1)
                ?: Regex("""getSemesters\([^)]*value\s*:\s*["']?(\d+)["']?""").find(pageHtml)?.groupValues?.get(1)
                ?: Regex("""name=["']semester\.id["'][^>]*value=["'](\d+)["']""").find(pageHtml)?.groupValues?.get(1)
                ?: Regex("""value=["'](\d+)["'][^>]*name=["']semester\.id["']""").find(pageHtml)?.groupValues?.get(1)
                .orEmpty()

            val idsMatch = Regex("""addInput\(form,\s*["']ids["'],\s*["'](\d+)["']\)""").find(pageHtml)
                ?: Regex("""name=["']ids["'][^>]*value=["'](\d+)["']""").find(pageHtml)
            val scheduleIds = idsMatch?.groupValues?.get(1).orEmpty()

            CourseTableEntryInfo(
                tagId = tagId,
                defaultSemesterId = pageSemId,
                scheduleIds = scheduleIds
            )
        } catch (_: Exception) {
            CourseTableEntryInfo(
                tagId = "semesterBar",
                defaultSemesterId = "",
                scheduleIds = ""
            )
        }
    }

    // 提取学期日历
    private suspend fun fetchSemesterCalendar(
        session: CqustWebVpnSession,
        baseUrl: String,
        tagId: String,
        targetSemesterId: String,
        defaultSemesterId: String
    ): Pair<String, List<Semester>> {
        return try {
            val queryVal = targetSemesterId.ifBlank { defaultSemesterId }
            val postBody = "tagId=$tagId&dataType=semesterCalendar&value=$queryVal&empty=false"

            val resp = session.post(
                url = "$baseUrl/dataQuery.action",
                body = postBody,
                referer = "$baseUrl/courseTableForStd.action",
                isAjax = true
            )
            val text = resp.bodyAsText()

            val currentMatch = Regex("""semesterId:\s*["']?(\d+)["']?""").find(text)
            val currentSemId = currentMatch?.groupValues?.get(1)?.ifBlank { null }
                ?: defaultSemesterId.ifBlank { targetSemesterId }

            val semRegex = Regex("""\{[^}]*id:\s*(\d+)[^}]*schoolYear:\s*["']([^"']+)["'][^}]*name:\s*["']([^"']+)["'][^}]*\}""")
            val allList = semRegex.findAll(text).map { match ->
                val id = match.groupValues[1]
                val schoolYear = match.groupValues[2]
                val name = match.groupValues[3]
                Semester(
                    id = id,
                    schoolYear = schoolYear,
                    name = name,
                    label = "${schoolYear}学年 第${name}学期"
                )
            }.sortedWith(
                compareByDescending<Semester> { it.schoolYear }
                    .thenByDescending { it.name.toIntOrNull() ?: 0 }
                    .thenByDescending { it.id.toIntOrNull() ?: 0 }
            ).toList()

            val detectedCurId = currentSemId.ifBlank {
                allList.firstOrNull()?.id.orEmpty()
            }

            val recentYears = allList.map { it.schoolYear }.distinct().take(2).toSet()
            val filteredList = allList.filter { it.schoolYear in recentYears }

            val finalSemesters = filteredList.map { sem ->
                val isCur = (sem.id == detectedCurId)
                sem.copy(
                    isCurrent = isCur,
                    label = if (isCur) "${sem.label} (当前)" else sem.label
                )
            }

            Pair(detectedCurId, finalSemesters)
        } catch (_: Exception) {
            Pair(targetSemesterId, emptyList())
        }
    }

    // 提取培养计划
    private suspend fun fetchTeachingPlan(
        session: CqustWebVpnSession,
        baseUrl: String,
        semesterId: String
    ): Map<String, TeachingPlanInfo> {
        val map = mutableMapOf<String, TeachingPlanInfo>()
        return try {
            val body = "setting.forSemester=1&semester.id=$semesterId"
            val resp = session.post("$baseUrl/teachingPlanSearchForStu!courseTable.action", body = body)
            val html = resp.bodyAsText()
            val trRegex = Regex("""<tr[^>]*>([\s\S]*?)</tr>""", RegexOption.IGNORE_CASE)
            val tdRegex = Regex("""<td[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE)
            val stripRegex = Regex("""<[^>]+>""")

            for (trMatch in trRegex.findAll(html)) {
                val tds = tdRegex.findAll(trMatch.groupValues[1]).map { stripRegex.replace(it.groupValues[1], "").trim() }.toList()
                if (tds.size >= 8) {
                    val courseCode = tds[2]
                    val category = tds[4]
                    val credit = tds[5]
                    val count = tds[7].toIntOrNull() ?: 0
                    if (courseCode.isNotEmpty()) {
                        map[courseCode] = TeachingPlanInfo(category = category, credit = credit, studentCount = count)
                    }
                }
            }
            map
        } catch (_: Exception) {
            map
        }
    }

    // 解析课表 HTML 正文
    fun parseCourseTableHtml(html: String): List<RawEamsCourse> {
        val scriptCourses = parseTaskActivityScript(html)
        val unarrangedCourses = parseUnarrangedTable(html)
        return scriptCourses + unarrangedCourses
    }

    // 解析 TaskActivity 网格排课脚本
    private fun parseTaskActivityScript(html: String): List<RawEamsCourse> {
        val scriptMatch = Regex("""var\s+table0\s*=\s*new\s+CourseTable[\s\S]*?</script>""").find(html) ?: return emptyList()
        val lines = scriptMatch.value.split('\n')
        var currentActivity: RawActivity? = null
        val rawSlots = mutableListOf<RawSlot>()

        val cleanNameSuffixRegex = Regex("""\([A-Za-z0-9._-]+\)$""")
        val codeRegex = Regex("""\(([^)]+)\)$""")
        val taskActivityRegex = Regex("""new\s+TaskActivity\((.*)\);?""")
        val indexRegex = Regex("""index\s*=\s*(\d+)\s*\*\s*unitCount\s*\+\s*(\d+);""")

        for (rawLine in lines) {
            val line = rawLine.trim()
            val actMatch = taskActivityRegex.find(line)
            if (actMatch != null) {
                val args = parseJsStringArgs(actMatch.groupValues[1])
                if (args.size >= 7) {
                    val teacher = args[1]
                    val rawFullName = args[3]
                    val name = cleanNameSuffixRegex.replace(rawFullName, "").trim().ifBlank { rawFullName }
                    val codeInName = codeRegex.find(rawFullName)?.groupValues?.get(1).orEmpty()
                    val courseCode = codeInName.ifEmpty {
                        codeRegex.find(args[2])?.groupValues?.get(1).orEmpty()
                    }
                    val room = args[5]
                    val weeks = parseWeekBits(args[6])

                    currentActivity = RawActivity(
                        teacher = teacher,
                        name = name,
                        courseCode = courseCode,
                        room = room,
                        weeks = weeks
                    )
                }
                continue
            }

            val idxMatch = indexRegex.find(line)
            if (idxMatch != null && currentActivity != null) {
                rawSlots.add(
                    RawSlot(
                        name = currentActivity.name,
                        courseCode = currentActivity.courseCode,
                        teacher = currentActivity.teacher,
                        position = currentActivity.room,
                        day = idxMatch.groupValues[1].toInt() + 1,
                        section = idxMatch.groupValues[2].toInt() + 1,
                        weeks = currentActivity.weeks
                    )
                )
            }
        }

        return mergeAdjacentSlots(rawSlots)
    }

    // 合并相邻节次为统一课程区间
    private fun mergeAdjacentSlots(rawSlots: List<RawSlot>): List<RawEamsCourse> {
        val result = mutableListOf<RawEamsCourse>()
        val groupedSlots = rawSlots.groupBy {
            "${it.name}_${it.courseCode}_${it.teacher}_${it.position}_${it.day}_${it.weeks.joinToString(",")}"
        }

        for ((_, group) in groupedSlots) {
            val sortedSections = group.map { it.section }.distinct().sorted()
            if (sortedSections.isEmpty()) continue
            val first = group.first()

            var startSec = sortedSections.first()
            var endSec = startSec
            for (i in 1 until sortedSections.size) {
                val s = sortedSections[i]
                if (s == endSec + 1) {
                    endSec = s
                } else {
                    result.add(createCourseFromSlot(first, startSec, endSec))
                    startSec = s
                    endSec = s
                }
            }
            result.add(createCourseFromSlot(first, startSec, endSec))
        }
        return result
    }

    // 从网格节点创建 RawEamsCourse 实例
    private fun createCourseFromSlot(slot: RawSlot, startSec: Int, endSec: Int) = RawEamsCourse(
        name = slot.name,
        courseCode = slot.courseCode,
        teacher = slot.teacher,
        position = slot.position,
        day = slot.day,
        startSection = startSec,
        endSection = endSec,
        weeks = slot.weeks
    )

    // 解析未安排时间任务列表表格
    private fun parseUnarrangedTable(html: String): List<RawEamsCourse> {
        val unarrangedMatch = Regex("""未安排时间任务列表[\s\S]*?<table[^>]*>([\s\S]*?)</table>""", RegexOption.IGNORE_CASE).find(html)
            ?: return emptyList()

        val result = mutableListOf<RawEamsCourse>()
        val trRegex = Regex("""<tr[^>]*>([\s\S]*?)</tr>""", RegexOption.IGNORE_CASE)
        val tdRegex = Regex("""<td[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE)
        val stripRegex = Regex("""<[^>]+>""")

        for (trMatch in trRegex.findAll(unarrangedMatch.groupValues[1])) {
            val tds = tdRegex.findAll(trMatch.groupValues[1])
                .map { stripRegex.replace(it.groupValues[1], "").trim() }
                .toList()

            if (tds.size >= 7 && tds[0].toIntOrNull() != null) {
                val weekText = tds[6]
                result.add(
                    RawEamsCourse(
                        name = tds[2],
                        courseCode = tds[1],
                        credit = tds[3],
                        teacher = tds[5],
                        position = "待定",
                        day = 0,
                        startSection = 0,
                        endSection = 0,
                        weeks = parseWeekRangeText(weekText),
                        remark = "起止周: $weekText"
                    )
                )
            }
        }
        return result
    }

    // 解析周次位串
    private fun parseWeekBits(bits: String): List<Int> {
        val weeks = mutableListOf<Int>()
        for (i in 1 until bits.length) {
            if (bits[i] == '1') weeks.add(i)
        }
        return weeks
    }

    // 解析起止周文本
    private fun parseWeekRangeText(text: String): List<Int> {
        val result = mutableListOf<Int>()
        val segments = text.split(',', '，')
        for (seg in segments) {
            val clean = seg.trim()
            if (clean.contains('-')) {
                val parts = clean.split('-')
                val start = parts.getOrNull(0)?.toIntOrNull()
                val end = parts.getOrNull(1)?.toIntOrNull()
                if (start != null && end != null && start <= end) {
                    for (w in start..end) result.add(w)
                }
            } else {
                clean.toIntOrNull()?.let { result.add(it) }
            }
        }
        return result.distinct().sorted()
    }

    // 解析 JS 字符串参数
    private fun parseJsStringArgs(argsStr: String): List<String> {
        val list = mutableListOf<String>()
        val regex = Regex(""""([^"\\]*(?:\\.[^"\\]*)*)"|'([^'\\]*(?:\\.[^'\\]*)*)'""")
        for (m in regex.findAll(argsStr)) {
            val str = m.groupValues[1].ifEmpty { m.groupValues[2] }
            list.add(str.replace("\\\"", "\"").replace("\\'", "'"))
        }
        return list
    }

    private data class TeachingPlanInfo(val category: String, val credit: String, val studentCount: Int)

    private data class RawActivity(
        val teacher: String,
        val name: String,
        val courseCode: String,
        val room: String,
        val weeks: List<Int>
    )

    private data class RawSlot(
        val name: String,
        val courseCode: String,
        val teacher: String,
        val position: String,
        val day: Int,
        val section: Int,
        val weeks: List<Int>
    )
}
