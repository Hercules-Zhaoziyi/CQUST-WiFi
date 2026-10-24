package top.msfxp.schedule.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import top.msfxp.schedule.data.db.ScheduleDao
import top.msfxp.schedule.data.model.*
import java.time.LocalTime

// 课表与学期数据仓库
class ScheduleRepository(
    private val dao: ScheduleDao
) {
    val defaultTableFlow: Flow<CourseTable?> = dao.getDefaultCourseTable()

    // 获取或创建默认课表 ID
    suspend fun getOrCreateDefaultTableId(): String {
        val existing = defaultTableFlow.first()
        if (existing != null) return existing.id
        val newTable = CourseTable(
            id = "default",
            name = "重科课表",
            createdAt = System.currentTimeMillis()
        )
        dao.insertCourseTable(newTable)
        return "default"
    }

    // 观察全学期课程及事件
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allCoursesFlow: Flow<List<CourseWithEvents>> = defaultTableFlow.flatMapLatest { table ->
        val tableId = table?.id
        if (tableId != null) {
            dao.getAllCoursesWithEvents(tableId)
        } else {
            flowOf(emptyList())
        }
    }

    fun getAllCoursesWithEvents(tableId: String): Flow<List<CourseWithEvents>> {
        return dao.getAllCoursesWithEvents(tableId)
    }

    suspend fun getAllCoursesWithEventsOnce(tableId: String): List<CourseWithEvents> {
        return dao.getAllCoursesWithEventsOnce(tableId)
    }

    // 更新课程配色编号
    suspend fun updateCourseColor(tableId: String, courseId: String, colorIndex: Int) {
        dao.updateCourseColor(tableId, courseId, colorIndex)
    }

    // 保存课表数据
    suspend fun saveSchedule(
        tableId: String,
        courses: List<Course>,
        events: List<CourseEvent>
    ) {
        dao.replaceCoursesAndEvents(tableId, courses, events)
    }

    // 获取指定周次的合并课程块列表
    fun getMergedBlocksForWeek(
        tableId: String,
        weekNumber: Int
    ): Flow<List<MergedCourseBlock>> {
        return dao.getWeekGridEvents(tableId, weekNumber).map { it.toMergedBlocks() }
    }

    // 获取指定周次的未排时段课程列表
    fun getWeekUnarrangedEvents(
        tableId: String,
        weekNumber: Int
    ): Flow<List<CourseEventWithMeta>> {
        return dao.getWeekUnarrangedEvents(tableId, weekNumber)
    }

    // 获取全学期所有已排教学事件
    suspend fun getAllEventsForTable(tableId: String): List<CourseEventWithMeta> {
        return dao.getAllEventsForTable(tableId)
    }

    // 获取今日时间轴课程列表
    suspend fun getTodayCourses(
        tableId: String,
        semesterId: String,
        dayOfWeek: Int,
        currentWeek: Int
    ): List<TodayCourseItem> {
        val allEvents = dao.getAllEventsForTable(tableId)
        val rawWeekEvents = allEvents.filter { it.week == currentWeek }
        val adjustments = dao.getAdjustmentsOnce(tableId, semesterId)
        val adjustedWeekEvents = applyAdjustmentsToWeekEvents(rawWeekEvents, adjustments, currentWeek, allEvents)
        val events = adjustedWeekEvents.filter { it.day == dayOfWeek }
            .sortedWith(compareBy({ it.startSection }, { it.courseName }))
        val slots = DefaultTimeSlots
        val currentTime = LocalTime.now()

        return events.map { ev ->
            val startSlot = slots.find { it.sectionNumber == ev.startSection }
            val endSlot = slots.find { it.sectionNumber == ev.endSection } ?: startSlot

            val startTimeStr = startSlot?.startTime ?: "08:30"
            val endTimeStr = endSlot?.endTime ?: "09:15"

            val isFinished = try {
                LocalTime.parse(endTimeStr) < currentTime
            } catch (_: Exception) {
                false
            }

            TodayCourseItem(
                event = ev,
                startTime = startTimeStr,
                endTime = endTimeStr,
                isFinished = isFinished
            )
        }
    }

    // 清空课表课程与事件
    suspend fun clearCourses(tableId: String) {
        dao.deleteEventsByTableId(tableId)
        dao.deleteCoursesByTableId(tableId)
    }

    // 学期数据操作
    val allSemestersFlow: Flow<List<Semester>> = dao.getAllSemesters()
    val currentSemesterFlow: Flow<Semester?> = dao.getCurrentSemester()

    suspend fun getCurrentSemesterOnce(): Semester? = dao.getCurrentSemesterOnce()
    suspend fun getSemesterById(semesterId: String): Semester? = dao.getSemesterById(semesterId)

    suspend fun saveSemesters(semesters: List<Semester>) {
        dao.insertSemesters(semesters)
    }

    suspend fun setActiveSemester(semesterId: String) {
        dao.setActiveSemester(semesterId)
    }

    suspend fun updateSemesterStartDate(semesterId: String, startDate: String) {
        dao.updateSemesterStartDate(semesterId, startDate)
    }

    suspend fun updateSemesterTotalWeeks(semesterId: String, totalWeeks: Int) {
        dao.updateSemesterTotalWeeks(semesterId, totalWeeks)
    }

    suspend fun clearSemesters() {
        dao.clearSemesters()
    }

    // 调课规则 (CourseAdjustment) 相关操作
    fun getAdjustmentsFlow(tableId: String = "default", semesterId: String): Flow<List<CourseAdjustment>> =
        dao.getAdjustmentsFlow(tableId, semesterId)

    fun getAllAdjustmentsWithMetaFlow(tableId: String = "default", semesterId: String): Flow<List<CourseAdjustmentWithMeta>> =
        dao.getAllAdjustmentsWithMeta(tableId, semesterId)

    suspend fun getAdjustmentsOnce(tableId: String = "default", semesterId: String): List<CourseAdjustment> =
        dao.getAdjustmentsOnce(tableId, semesterId)

    suspend fun getAdjustmentForEvent(
        tableId: String = "default",
        semesterId: String,
        courseId: String,
        week: Int,
        day: Int,
        startSection: Int
    ): CourseAdjustment? = dao.getAdjustmentForEvent(tableId, semesterId, courseId, week, day, startSection)

    // 移动课程（目标坐标不能有课）
    suspend fun moveCourse(adjustment: CourseAdjustment) {
        val existing = dao.getAdjustmentForEvent(
            tableId = adjustment.tableId,
            semesterId = adjustment.semesterId,
            courseId = adjustment.courseId,
            week = adjustment.originalWeek,
            day = adjustment.originalDay,
            startSection = adjustment.originalStartSection
        )
        val finalAdjustment = if (existing != null) adjustment.copy(id = existing.id) else adjustment
        dao.upsertAdjustment(finalAdjustment.copy(isSuspended = false))
    }

    // 调换两门课程（目标坐标必须有课，两课互换时段）
    suspend fun swapCourses(adjA: CourseAdjustment, adjB: CourseAdjustment) {
        val existingA = dao.getAdjustmentForEvent(
            tableId = adjA.tableId,
            semesterId = adjA.semesterId,
            courseId = adjA.courseId,
            week = adjA.originalWeek,
            day = adjA.originalDay,
            startSection = adjA.originalStartSection
        )
        val existingB = dao.getAdjustmentForEvent(
            tableId = adjB.tableId,
            semesterId = adjB.semesterId,
            courseId = adjB.courseId,
            week = adjB.originalWeek,
            day = adjB.originalDay,
            startSection = adjB.originalStartSection
        )
        val finalA = if (existingA != null) adjA.copy(id = existingA.id) else adjA
        val finalB = if (existingB != null) adjB.copy(id = existingB.id) else adjB
        dao.upsertAdjustments(listOf(finalA.copy(isSuspended = false), finalB.copy(isSuspended = false)))
    }

    // 标记删除（本节停课）
    suspend fun markCourseDeleted(adjustment: CourseAdjustment) {
        val existing = dao.getAdjustmentForEvent(
            tableId = adjustment.tableId,
            semesterId = adjustment.semesterId,
            courseId = adjustment.courseId,
            week = adjustment.originalWeek,
            day = adjustment.originalDay,
            startSection = adjustment.originalStartSection
        )
        val finalAdjustment = if (existing != null) adjustment.copy(id = existing.id) else adjustment
        dao.upsertAdjustment(finalAdjustment.copy(isSuspended = true))
    }

    // 保存单项调课通用方法
    suspend fun saveAdjustment(adjustment: CourseAdjustment) {
        if (adjustment.isSuspended) {
            markCourseDeleted(adjustment)
        } else {
            moveCourse(adjustment)
        }
    }

    // 删除单项调课
    suspend fun deleteAdjustment(id: Long) {
        dao.deleteAdjustmentById(id)
    }

    // 一键清空所有调课记录
    suspend fun clearAllAdjustments(tableId: String = "default") {
        dao.clearAllAdjustments(tableId)
    }

}

// 应用调课规则到指定周次的课程事件列表
fun applyAdjustmentsToWeekEvents(
    events: List<CourseEventWithMeta>,
    adjustments: List<CourseAdjustment>,
    weekNumber: Int,
    allEvents: List<CourseEventWithMeta> = emptyList()
): List<CourseEventWithMeta> {
    if (adjustments.isEmpty()) return events

    val weekAdjustments = adjustments.filter { it.originalWeek == weekNumber || it.targetWeek == weekNumber }
    if (weekAdjustments.isEmpty()) return events

    val result = mutableListOf<CourseEventWithMeta>()

    // 1. 处理原本周排课（本周内微调、停课剔除、调出到其他周）
    for (event in events) {
        val adj = weekAdjustments.find {
            it.originalWeek == weekNumber &&
            it.courseId == event.courseId &&
            it.originalDay == event.day &&
            it.originalStartSection == event.startSection
        }

        if (adj == null) {
            result.add(event)
        } else if (adj.isSuspended) {
            // 本节停课，不展示
            continue
        } else if (adj.targetWeek != weekNumber) {
            // 调到其他周，本周不展示
            continue
        } else {
            // 本周内调时段或地点
            result.add(
                event.copy(
                    day = adj.targetDay,
                    startSection = adj.targetStartSection,
                    endSection = adj.targetEndSection,
                    location = if (adj.targetLocation.isNotBlank()) adj.targetLocation else event.location
                ).apply {
                    this.weeks = event.weeks
                    this.isAdjusted = true
                }
            )
        }
    }

    // 2. 处理从其他周跨周调入本周的课程
    val incomingAdjustments = weekAdjustments.filter {
        it.originalWeek != weekNumber && it.targetWeek == weekNumber && !it.isSuspended
    }
    if (incomingAdjustments.isNotEmpty() && allEvents.isNotEmpty()) {
        for (incomingAdj in incomingAdjustments) {
            val originEvent = allEvents.find {
                it.courseId == incomingAdj.courseId &&
                it.week == incomingAdj.originalWeek &&
                it.day == incomingAdj.originalDay &&
                it.startSection == incomingAdj.originalStartSection
            } ?: allEvents.find { it.courseId == incomingAdj.courseId }

            if (originEvent != null) {
                result.add(
                    originEvent.copy(
                        week = weekNumber,
                        day = incomingAdj.targetDay,
                        startSection = incomingAdj.targetStartSection,
                        endSection = incomingAdj.targetEndSection,
                        location = if (incomingAdj.targetLocation.isNotBlank()) incomingAdj.targetLocation else originEvent.location
                    ).apply {
                        this.weeks = originEvent.weeks
                        this.isAdjusted = true
                    }
                )
            }
        }
    }

    return result
}
