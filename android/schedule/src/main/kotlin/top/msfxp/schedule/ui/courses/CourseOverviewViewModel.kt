package top.msfxp.schedule.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import top.msfxp.schedule.data.model.*
import top.msfxp.schedule.data.repository.ScheduleRepository
import top.msfxp.schedule.data.repository.SettingsRepository

// 课程概览分组项数据模型
data class CourseGroupItem(
    val courseId: String,
    val courseCode: String,
    val name: String,
    val credit: String,
    val category: String,
    val theoryTeacher: String = "",
    val practicalTeacher: String = "",
    val colorIndex: Int,
    val theorySlots: List<CompressedCourseSlot> = emptyList(),
    val practicalSlots: List<CompressedCourseSlot> = emptyList(),
    val isPracticalOnly: Boolean = false,
    val projectCount: Int = 0
) {
    val hasTheory: Boolean get() = theorySlots.isNotEmpty()
    val hasPractical: Boolean get() = practicalSlots.isNotEmpty()
    val totalSlotCount: Int get() = theorySlots.size + practicalSlots.size
    val primaryTeacher: String get() = theoryTeacher.ifBlank { practicalTeacher }
}

// 课程概览界面状态
data class CourseOverviewUiState(
    val courses: List<CourseGroupItem> = emptyList(),
    val uniqueCount: Int = 0,
    val totalSlots: Int = 0,
    val totalCredits: Double = 0.0,
    val courseColors: List<CourseColor> = DefaultCourseColors
)

// 课程概览视图模型
class CourseOverviewViewModel(
    scheduleRepository: ScheduleRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    // 响应式聚合课程概览状态
    val uiState: StateFlow<CourseOverviewUiState> = combine(
        scheduleRepository.allCoursesFlow,
        settingsRepository.appSettingsFlow
    ) { allCourses, settings ->
        val palette = parseCoursePaletteJson(settings.customCourseColorsJson)
        val groups = allCourses.groupBy { it.course.name }.map { (courseName, courseWithEventsList) ->
            val theoryCourse = courseWithEventsList.find { !it.course.isPractical }
            val practicalCourse = courseWithEventsList.find { it.course.isPractical }
            val primaryCourse = theoryCourse ?: practicalCourse ?: courseWithEventsList.first()
            val course = primaryCourse.course

            val theorySlots = theoryCourse?.toCompressedSlots()
                ?.sortedWith(compareBy({ it.day }, { it.startSection }, { it.weeks.minOrNull() ?: 0 }))
                .orEmpty()
            val practicalSlots = practicalCourse?.toCompressedSlots()
                ?.sortedWith(compareBy({ it.weeks.minOrNull() ?: 0 }, { it.day }, { it.startSection }))
                .orEmpty()

            val theoryTeacher = theoryCourse?.events
                ?.map { it.teacher }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?.joinToString("/")
                .orEmpty()

            val practicalTeacher = practicalCourse?.events
                ?.map { it.teacher }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?.joinToString("/")
                .orEmpty()

            val projectCount = practicalCourse?.events
                ?.map { it.projectName }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?.size ?: 0

            CourseGroupItem(
                courseId = course.id,
                courseCode = course.courseCode.ifBlank { practicalCourse?.course?.courseCode.orEmpty() },
                name = courseName,
                credit = course.credit.ifBlank { practicalCourse?.course?.credit.orEmpty() },
                category = course.category.ifBlank { practicalCourse?.course?.category.orEmpty() },
                theoryTeacher = theoryTeacher,
                practicalTeacher = practicalTeacher,
                colorIndex = course.colorIndex,
                theorySlots = theorySlots,
                practicalSlots = practicalSlots,
                isPracticalOnly = theoryCourse == null && practicalCourse != null,
                projectCount = projectCount
            )
        }.sortedBy { it.name }

        CourseOverviewUiState(
            courses = groups,
            uniqueCount = groups.size,
            totalSlots = groups.sumOf { it.totalSlotCount },
            totalCredits = groups.sumOf { it.credit.toDoubleOrNull() ?: 0.0 },
            courseColors = palette
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CourseOverviewUiState()
    )
}
