package top.msfxp.schedule.data.api

import top.msfxp.schedule.data.model.Course
import top.msfxp.schedule.data.model.CourseEvent
import top.msfxp.schedule.data.model.DefaultCourseColors
import top.msfxp.schedule.data.model.COURSE_TYPE_PRACTICAL
import top.msfxp.schedule.data.model.COURSE_TYPE_THEORY

// 课表时空矩阵构建器
object ScheduleMatrixBuilder {

    // 实践教学楼地点纠偏 (虎溪校区字母楼栋转标准名称)
    fun normalizePracticalLocation(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed == "待定" || trimmed.contains("金凤")) {
            return trimmed
        }

        // 仅剥离已确证的虎溪校区前缀
        val cleanTrimmed = trimmed.replace("虎溪校区", "").trim()

        val regex = Regex("""^([A-Za-z])(?:栋)?([A-Za-z]?\d+.*)$""")
        val match = regex.find(cleanTrimmed)
        if (match != null) {
            val letter = match.groupValues[1].uppercase()
            val roomNum = match.groupValues[2].removePrefix(letter)
            val buildingName = when (letter) {
                "A", "B", "C", "D" -> "教学主楼"
                "K" -> "笃行楼"
                "L" -> "砺志楼"
                "M" -> "知行楼"
                "E", "F" -> "博学楼"
                "G", "H" -> "厚德楼"
                "I" -> "逸夫楼"
                else -> ""
            }
            if (buildingName.isNotEmpty()) {
                return "$buildingName $letter$roomNum"
            }
        }

        // 不匹配已知虎溪字母楼栋时，保持原始文本不作破坏
        return trimmed
    }

    // 构建课程元数据与时空事件列表
    fun build(
        tableId: String,
        eamsCourses: List<RawEamsCourse>,
        practicalItems: List<RawPracticalItem> = emptyList()
    ): Pair<List<Course>, List<CourseEvent>> {
        val colorIndexMap = mutableMapOf<String, Int>()
        var currentColorIdx = 0
        val colorAllocator: (String) -> Int = { name ->
            colorIndexMap.getOrPut(name) { (currentColorIdx++) % DefaultCourseColors.size }
        }

        val (theoryMap, theoryEvents) = buildTheoryCourses(tableId, eamsCourses, colorAllocator)
        val (practiceCourses, practiceEvents) = buildPracticeCourses(tableId, practicalItems, theoryMap, colorAllocator)

        val courses = theoryMap.values.toList() + practiceCourses
        val events = theoryEvents + practiceEvents
        return Pair(courses, events)
    }

    // 构建常规理论课程及事件
    private fun buildTheoryCourses(
        tableId: String,
        eamsCourses: List<RawEamsCourse>,
        colorAllocator: (String) -> Int
    ): Pair<Map<String, Course>, List<CourseEvent>> {
        val theoryByName = mutableMapOf<String, Course>()
        val eventList = mutableListOf<CourseEvent>()

        for ((name, rawList) in eamsCourses.groupBy { it.name }) {
            val first = rawList.first()
            val courseId = "${tableId}_THEORY_${first.courseCode.ifBlank { name }}"
            val course = Course(
                id = courseId,
                tableId = tableId,
                courseCode = first.courseCode,
                name = name,
                credit = first.credit,
                category = first.category,
                courseType = COURSE_TYPE_THEORY,
                colorIndex = colorAllocator(name),
                remark = first.remark
            )
            theoryByName[name] = course

            for (c in rawList) {
                eventList.addAll(expandTheoryEvents(courseId, tableId, c))
            }
        }
        return Pair(theoryByName, eventList)
    }

    // 展开单门理论课的各周次事件
    private fun expandTheoryEvents(
        courseId: String,
        tableId: String,
        rawCourse: RawEamsCourse
    ): List<CourseEvent> {
        val targetWeeks = if (rawCourse.weeks.isEmpty()) listOf(0) else rawCourse.weeks
        return targetWeeks.map { week ->
            CourseEvent(
                courseId = courseId,
                tableId = tableId,
                week = week,
                day = rawCourse.day,
                startSection = rawCourse.startSection,
                endSection = rawCourse.endSection,
                location = rawCourse.position,
                teacher = rawCourse.teacher,
                projectName = "",
                studentCount = rawCourse.studentCount
            )
        }
    }

    // 构建实践教学课程及事件
    private fun buildPracticeCourses(
        tableId: String,
        practicalItems: List<RawPracticalItem>,
        theoryByName: Map<String, Course>,
        colorAllocator: (String) -> Int
    ): Pair<List<Course>, List<CourseEvent>> {
        if (practicalItems.isEmpty()) return Pair(emptyList(), emptyList())

        val courseList = mutableListOf<Course>()
        val eventList = mutableListOf<CourseEvent>()
        val theoryByCode = theoryByName.values.filter { it.courseCode.isNotBlank() }.associateBy { it.courseCode }
        val theoryByBaseCode = theoryByName.values.filter { it.courseCode.isNotBlank() }
            .associateBy { it.courseCode.substringBefore('.').trim() }

        for ((name, pList) in practicalItems.groupBy { it.name }) {
            val directCode = pList.firstOrNull { it.courseCode.isNotBlank() }?.courseCode.orEmpty()
            val baseCode = directCode.substringBefore('.').trim()
            val matchedTheory = (if (directCode.isNotBlank()) theoryByCode[directCode] ?: theoryByBaseCode[baseCode] else null)
                ?: theoryByName[name]
            val finalCourseCode = directCode.ifBlank { matchedTheory?.courseCode?.substringBefore('.').orEmpty() }
            val colorIdx = matchedTheory?.colorIndex ?: colorAllocator(name)
            val courseId = "${tableId}_PRACTICAL_${finalCourseCode.ifBlank { name }}"

            val practicalCourse = Course(
                id = courseId,
                tableId = tableId,
                courseCode = finalCourseCode,
                name = name,
                credit = matchedTheory?.credit.orEmpty(),
                category = matchedTheory?.category?.ifBlank { "实践教学" } ?: "实践教学",
                courseType = COURSE_TYPE_PRACTICAL,
                colorIndex = colorIdx,
                remark = ""
            )
            courseList.add(practicalCourse)

            for (item in pList) {
                eventList.addAll(expandPracticalEvents(courseId, tableId, item))
            }
        }
        return Pair(courseList, eventList)
    }

    // 展开单条实践项目的各周次事件并纠偏地点与晚课时段
    private fun expandPracticalEvents(
        courseId: String,
        tableId: String,
        item: RawPracticalItem
    ): List<CourseEvent> {
        val cleanLocation = normalizePracticalLocation(item.location)
        val correctedStart = if (item.startSection == 10 && item.endSection == 11) 9 else item.startSection
        val correctedEnd = if (item.startSection == 10 && item.endSection == 11) 10 else item.endSection

        return item.weeks.map { week ->
            CourseEvent(
                courseId = courseId,
                tableId = tableId,
                week = week,
                day = item.day,
                startSection = correctedStart,
                endSection = correctedEnd,
                location = cleanLocation,
                teacher = item.teacher,
                projectName = item.projectName,
                studentCount = item.studentCount
            )
        }
    }
}
