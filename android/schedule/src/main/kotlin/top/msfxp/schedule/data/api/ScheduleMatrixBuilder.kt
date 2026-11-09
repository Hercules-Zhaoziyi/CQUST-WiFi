package top.msfxp.schedule.data.api

import top.msfxp.schedule.data.model.Course
import top.msfxp.schedule.data.model.CourseEvent
import top.msfxp.schedule.data.model.DefaultCourseColors
import top.msfxp.schedule.data.model.COURSE_TYPE_PRACTICAL
import top.msfxp.schedule.data.model.COURSE_TYPE_THEORY
import top.msfxp.schedule.data.model.courseUnifiedCode
import top.msfxp.schedule.data.model.unifiedCode
import top.msfxp.schedule.util.CourseLocationFormatter

// 课表时空矩阵构建器
object ScheduleMatrixBuilder {

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

        val (theoryCourses, theoryEvents) = buildTheoryCourses(tableId, eamsCourses, colorAllocator)
        val (practiceCourses, practiceEvents) = buildPracticeCourses(tableId, practicalItems, theoryCourses, colorAllocator)

        val courses = theoryCourses + practiceCourses
        val events = theoryEvents + practiceEvents
        return Pair(courses, events)
    }

    // 构建常规理论课程及事件
    private fun buildTheoryCourses(
        tableId: String,
        eamsCourses: List<RawEamsCourse>,
        colorAllocator: (String) -> Int
    ): Pair<List<Course>, List<CourseEvent>> {
        val courseList = mutableListOf<Course>()
        val eventList = mutableListOf<CourseEvent>()

        for ((_, rawList) in eamsCourses.groupBy { courseUnifiedCode(it.courseCode) }) {
            val first = rawList.first()
            val unifiedCode = courseUnifiedCode(first.courseCode)
            val courseId = "${tableId}_THEORY_$unifiedCode"
            val course = Course(
                id = courseId,
                tableId = tableId,
                courseCode = first.courseCode,
                name = first.name,
                credit = first.credit,
                category = first.category,
                courseType = COURSE_TYPE_THEORY,
                colorIndex = colorAllocator(unifiedCode),
                remark = first.remark
            )
            courseList.add(course)

            for (c in rawList) {
                eventList.addAll(expandTheoryEvents(courseId, tableId, c))
            }
        }
        return Pair(courseList, eventList)
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
                location = CourseLocationFormatter.format(rawCourse.position),
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
        theoryCourses: List<Course>,
        colorAllocator: (String) -> Int
    ): Pair<List<Course>, List<CourseEvent>> {
        if (practicalItems.isEmpty()) return Pair(emptyList(), emptyList())

        val courseList = mutableListOf<Course>()
        val eventList = mutableListOf<CourseEvent>()
        val theoryByKey = theoryCourses.associateBy { it.unifiedCode }

        for ((_, pList) in practicalItems.groupBy { courseUnifiedCode(it.courseCode) }) {
            val first = pList.first()
            val unifiedCode = courseUnifiedCode(first.courseCode)
            val matchedTheory = theoryByKey[unifiedCode]
            val colorIdx = matchedTheory?.colorIndex ?: colorAllocator(unifiedCode)
            val courseId = "${tableId}_PRACTICAL_$unifiedCode"

            val practicalCourse = Course(
                id = courseId,
                tableId = tableId,
                courseCode = unifiedCode,
                name = first.name,
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

    // 展开单条实践项目的各周次事件
    private fun expandPracticalEvents(
        courseId: String,
        tableId: String,
        item: RawPracticalItem
    ): List<CourseEvent> {
        val cleanLocation = CourseLocationFormatter.format(item.location)

        return item.weeks.map { week ->
            CourseEvent(
                courseId = courseId,
                tableId = tableId,
                week = week,
                day = item.day,
                startSection = item.startSection,
                endSection = item.endSection,
                location = cleanLocation,
                teacher = item.teacher,
                projectName = item.projectName,
                studentCount = item.studentCount
            )
        }
    }
}
