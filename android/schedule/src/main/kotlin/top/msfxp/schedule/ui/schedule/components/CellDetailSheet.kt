package top.msfxp.schedule.ui.schedule.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.CourseAdjustment
import top.msfxp.schedule.data.model.CourseAdjustmentWithMeta
import top.msfxp.schedule.data.model.CourseColor
import top.msfxp.schedule.data.model.CourseEventWithMeta
import top.msfxp.schedule.data.model.DefaultCourseColors
import top.msfxp.schedule.data.model.ExamRecord
import top.msfxp.schedule.data.model.ScheduleGridCell
import top.msfxp.schedule.data.model.findSectionTimeSlot
import top.msfxp.schedule.data.model.getCourseColor
import top.msfxp.schedule.util.MapNavigationHelper
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.window.WindowBottomSheet

// 详情页条目（考试 > 实践 > 课程）
internal sealed interface CellDetailPage {
    data class Exam(val exam: ExamRecord) : CellDetailPage
    data class Course(val event: CourseEventWithMeta) : CellDetailPage
}

// 课表单元详情抽屉（同格课程、实践与考试统一分页滑动）
@Composable
fun CellDetailSheet(
    cell: ScheduleGridCell?,
    currentWeekNumber: Int = 1,
    totalWeeks: Int = 20,
    currentSemesterId: String = "",
    courseColors: List<CourseColor> = DefaultCourseColors,
    adjustmentsList: List<CourseAdjustmentWithMeta> = emptyList(),
    colorIndexResolver: (ExamRecord) -> Int = { 0 },
    getExamAtSlot: ((Int, Int, Int, Int) -> ExamRecord?)? = null,
    onColorIndexSelected: (String, Int) -> Unit = { _, _ -> },
    onMoveCourse: (CourseAdjustment) -> Unit = {},
    onSwapCourses: (CourseAdjustment, CourseAdjustment) -> Unit = { _, _ -> },
    onMarkDeleted: (CourseAdjustment) -> Unit = {},
    onDeleteAdjustment: (Long) -> Unit = {},
    onClearAllAdjustments: () -> Unit = {},
    onGetAdjustmentForEvent: (suspend (String, Int, Int, Int) -> CourseAdjustment?)? = null,
    getCourseAtSlot: ((week: Int, day: Int, startSection: Int, endSection: Int, excludeCourseId: String, excludeStartSection: Int) -> CourseEventWithMeta?)? = null,
    onDismissRequest: () -> Unit
) {
    if (cell == null) return
    val pages = remember(cell) {
        cell.exams.map { CellDetailPage.Exam(it) } +
            cell.courses
                .sortedByDescending { it.isPractical }
                .map { CellDetailPage.Course(it) }
    }
    if (pages.isEmpty()) return

    val context = LocalContext.current
    var selectedNavPosition by remember { mutableStateOf<String?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val currentPage = pages.getOrNull(pagerState.currentPage)
    val currentEvent = (currentPage as? CellDetailPage.Course)?.event
    var currentAdjustment by remember { mutableStateOf<CourseAdjustment?>(null) }

    LaunchedEffect(currentEvent, currentWeekNumber, showEditSheet) {
        if (currentEvent != null && onGetAdjustmentForEvent != null) {
            currentAdjustment = onGetAdjustmentForEvent(
                currentEvent.courseId,
                currentWeekNumber,
                currentEvent.day,
                currentEvent.startSection
            )
        }
    }

    val dayNames = listOf(
        stringResource(R.string.day_monday),
        stringResource(R.string.day_tuesday),
        stringResource(R.string.day_wednesday),
        stringResource(R.string.day_thursday),
        stringResource(R.string.day_friday),
        stringResource(R.string.day_saturday),
        stringResource(R.string.day_sunday)
    )

    val sheetTitle = when {
        pages.size == 1 -> if (currentPage is CellDetailPage.Exam) {
            stringResource(R.string.exam_detail_title)
        } else {
            stringResource(R.string.course_sheet_title_single)
        }
        pages.all { it is CellDetailPage.Course } -> stringResource(
            R.string.course_sheet_title_format,
            pagerState.currentPage + 1,
            pages.size
        )
        pages.all { it is CellDetailPage.Exam } -> stringResource(
            R.string.exam_detail_title_format,
            pagerState.currentPage + 1,
            pages.size
        )
        else -> stringResource(
            R.string.cell_detail_title_format,
            pagerState.currentPage + 1,
            pages.size
        )
    }

    val openMap: (String) -> Unit = { loc ->
        val destination = MapNavigationHelper.buildWalkingDestination(loc)
        if (destination == null) {
            Toast.makeText(context, R.string.map_no_location_toast, Toast.LENGTH_SHORT).show()
        } else {
            val availableApps = MapNavigationHelper.getAvailableMapApps(context)
            when {
                availableApps.isEmpty() -> Toast.makeText(
                    context,
                    R.string.map_no_supported_app_toast,
                    Toast.LENGTH_SHORT
                ).show()
                availableApps.size == 1 -> MapNavigationHelper.openMapWalkingNavigation(
                    context,
                    availableApps.first(),
                    destination
                )
                else -> selectedNavPosition = loc
            }
        }
    }

    WindowBottomSheet(
        show = true,
        title = sheetTitle,
        endAction = if (currentEvent != null) {
            {
                IconButton(onClick = { showEditSheet = true }) {
                    Icon(
                        imageVector = MiuixIcons.Edit,
                        contentDescription = stringResource(R.string.course_edit_menu_title)
                    )
                }
            }
        } else {
            null
        },
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                when (val item = pages[page]) {
                    is CellDetailPage.Exam -> ExamDetailPage(
                        exam = item.exam,
                        themeColor = courseColors.getCourseColor(colorIndexResolver(item.exam)).light,
                        currentWeekNumber = currentWeekNumber,
                        dayNames = dayNames,
                        onOpenMap = openMap
                    )
                    is CellDetailPage.Course -> CourseDetailPage(
                        event = item.event,
                        currentWeekNumber = currentWeekNumber,
                        courseThemeColor = courseColors.getCourseColor(item.event.colorIndex).light,
                        dayNames = dayNames,
                        onOpenMap = openMap
                    )
                }
            }

            if (pages.size > 1) {
                Spacer(modifier = Modifier.height(16.dp))
                WormPagerIndicator(
                    pagerState = pagerState,
                    pageCount = pages.size,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // 步行导航地图选择弹窗
    MapChooserDialog(
        show = selectedNavPosition != null,
        rawPosition = selectedNavPosition.orEmpty(),
        onDismissRequest = { selectedNavPosition = null }
    )

    // 课程外观与调课编辑流程
    if (showEditSheet && currentEvent != null) {
        CourseEditFlow(
            event = currentEvent,
            currentWeek = currentWeekNumber,
            totalWeeks = totalWeeks,
            currentSemesterId = currentSemesterId,
            courseColors = courseColors,
            existingAdjustment = currentAdjustment,
            adjustmentsList = adjustmentsList,
            getCourseAtSlot = { w, d, s, e ->
                getCourseAtSlot?.invoke(w, d, s, e, currentEvent.courseId, currentEvent.startSection)
            },
            getExamAtSlot = { w, d, s, e ->
                getExamAtSlot?.invoke(w, d, s, e)
            },
            onMoveCourse = { adj ->
                onMoveCourse(adj)
                showEditSheet = false
            },
            onSwapCourses = { adjA, adjB ->
                onSwapCourses(adjA, adjB)
                showEditSheet = false
            },
            onMarkDeleted = { adj ->
                onMarkDeleted(adj)
                showEditSheet = false
            },
            onDeleteAdjustment = { adjId ->
                onDeleteAdjustment(adjId)
                showEditSheet = false
            },
            onClearAllAdjustments = {
                onClearAllAdjustments()
                showEditSheet = false
            },
            onColorSelected = { newIdx ->
                onColorIndexSelected(currentEvent.courseId, newIdx)
            },
            onDismissRequest = { showEditSheet = false }
        )
    }
}

// 课程详情页内容
@Composable
private fun CourseDetailPage(
    event: CourseEventWithMeta,
    currentWeekNumber: Int,
    courseThemeColor: Color,
    dayNames: List<String>,
    onOpenMap: (String) -> Unit
) {
    val isUnarranged = event.day == 0 || event.startSection == 0
    val dayStr = if (isUnarranged) {
        stringResource(R.string.course_pending_location)
    } else {
        dayNames.getOrElse(event.day - 1) { stringResource(R.string.day_number_format, event.day) }
    }
    val activeWeeksSet = event.weeks.toSet()

    val startSlot = findSectionTimeSlot(event.startSection)
    val endSlot = findSectionTimeSlot(event.endSection) ?: startSlot
    val timeRangeStr = if (startSlot != null && endSlot != null) {
        "${startSlot.startTime} - ${endSlot.endTime}"
    } else {
        stringResource(R.string.course_section_format, event.startSection, event.endSection)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DetailPageMinHeight),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CourseDetailHeader(
            event = event,
            courseThemeColor = courseThemeColor,
            dayStr = dayStr,
            isUnarranged = isUnarranged
        )
        DetailLocationCard(
            label = stringResource(R.string.course_sheet_label_location),
            locationText = event.displayLocation,
            rawPosition = event.location,
            accentColor = courseThemeColor,
            onOpenMap = onOpenMap
        )
        CourseDetailPropertiesGrid(
            event = event,
            isUnarranged = isUnarranged,
            timeRangeStr = timeRangeStr
        )
        CourseDetailWeeksMatrix(
            label = stringResource(R.string.course_sheet_label_weeks),
            activeWeeksSet = activeWeeksSet,
            currentWeekNumber = currentWeekNumber,
            courseThemeColor = courseThemeColor
        )
    }
}
