package top.msfxp.schedule.ui.schedule.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.*
import top.msfxp.schedule.util.MapNavigationHelper
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.window.WindowBottomSheet

// 课程详情弹窗抽屉
@Composable
fun CourseDetailSheet(
    block: MergedCourseBlock?,
    timeSlots: List<TimeSlot> = emptyList(),
    currentWeekNumber: Int = 1,
    totalWeeks: Int = 20,
    currentSemesterId: String = "",
    courseColors: List<CourseColor> = DefaultCourseColors,
    adjustmentsList: List<CourseAdjustmentWithMeta> = emptyList(),
    onColorIndexSelected: (String, Int) -> Unit = { _, _ -> },
    onMoveCourse: (CourseAdjustment) -> Unit = {},
    onSwapCourses: (CourseAdjustment, CourseAdjustment) -> Unit = { _, _ -> },
    onMarkDeleted: (CourseAdjustment) -> Unit = {},
    onDeleteAdjustment: (Long) -> Unit = {},
    onClearAllAdjustments: () -> Unit = {},
    onGetAdjustmentForEvent: (suspend (String, Int, Int, Int) -> CourseAdjustment?)? = null,
    getCourseAtSlot: ((Int, Int, Int, Int) -> CourseEventWithMeta?)? = null,
    onDismissRequest: () -> Unit
) {
    if (block == null || block.events.isEmpty()) return

    val context = LocalContext.current
    var selectedNavPosition by remember { mutableStateOf<String?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }
    val coursesList = block.events
    val pagerState = rememberPagerState(pageCount = { coursesList.size })
    val currentEvent = coursesList.getOrNull(pagerState.currentPage)
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

    WindowBottomSheet(
        show = true,
        title = if (coursesList.size > 1) {
            stringResource(R.string.course_sheet_title_format, pagerState.currentPage + 1, coursesList.size)
        } else {
            stringResource(R.string.course_sheet_title_single)
        },
        endAction = {
            IconButton(onClick = { showEditSheet = true }) {
                Icon(
                    imageVector = MiuixIcons.Edit,
                    contentDescription = stringResource(R.string.course_edit_menu_title)
                )
            }
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
                val event = coursesList[page]
                val isUnarranged = event.day == 0 || event.startSection == 0
                val dayStr = if (isUnarranged) stringResource(R.string.course_pending_location)
                else dayNames.getOrElse(event.day - 1) { stringResource(R.string.day_number_format, event.day) }
                val activeWeeksSet = event.weeks.toSet()
                val courseThemeColor = courseColors.getCourseColor(event.colorIndex).light

                val startSlot = timeSlots.find { it.sectionNumber == event.startSection }
                val endSlot = timeSlots.find { it.sectionNumber == event.endSection } ?: startSlot
                val timeRangeStr = if (startSlot != null && endSlot != null) {
                    "${startSlot.startTime} - ${endSlot.endTime}"
                } else {
                    stringResource(R.string.course_section_format, event.startSection, event.endSection)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 496.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CourseDetailHeader(
                        event = event,
                        courseThemeColor = courseThemeColor,
                        dayStr = dayStr,
                        isUnarranged = isUnarranged
                    )
                    CourseDetailLocationCard(
                        event = event,
                        courseThemeColor = courseThemeColor,
                        onOpenMap = { loc ->
                            val availableApps = MapNavigationHelper.getAvailableMapApps(context)
                            when {
                                availableApps.isEmpty() -> Toast.makeText(context, R.string.map_no_supported_app_toast, Toast.LENGTH_SHORT).show()
                                availableApps.size == 1 -> {
                                    val dest = MapNavigationHelper.buildWalkingDestination(loc)
                                    MapNavigationHelper.openMapWalkingNavigation(context, availableApps.first(), dest)
                                }
                                else -> selectedNavPosition = loc
                            }
                        },
                        onNoLocation = {
                            Toast.makeText(context, R.string.map_no_location_toast, Toast.LENGTH_SHORT).show()
                        }
                    )
                    CourseDetailPropertiesGrid(
                        event = event,
                        isUnarranged = isUnarranged,
                        timeRangeStr = timeRangeStr
                    )
                    CourseDetailWeeksMatrix(
                        activeWeeksSet = activeWeeksSet,
                        currentWeekNumber = currentWeekNumber,
                        courseThemeColor = courseThemeColor
                    )
                }
            }

            if (coursesList.size > 1) {
                Spacer(modifier = Modifier.height(16.dp))
                WormPagerIndicator(
                    pagerState = pagerState,
                    pageCount = coursesList.size,
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
                getCourseAtSlot?.invoke(w, d, s, e)
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
