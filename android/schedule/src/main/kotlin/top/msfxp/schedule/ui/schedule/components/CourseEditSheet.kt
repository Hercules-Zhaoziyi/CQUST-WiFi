package top.msfxp.schedule.ui.schedule.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.*
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.window.WindowBottomSheet

// 调课编辑流程子页面枚举
enum class CourseEditPage {
    MENU,        // 二级页面：菜单导航（课程配色、课程调整、调课记录）
    PALETTE,     // 三级页面 1：课程配色编号选择
    ADJUSTMENT,  // 三级页面 2：课程调整（移动、调换、删除）
    RECORDS      // 三级页面 3：调课记录列表
}

// 调课处理方式模式枚举
enum class CourseAdjustmentMode {
    MOVE,   // 移动（目标坐标不能有课）
    SWAP,   // 调换（目标坐标必须有课，两课互换时段）
    DELETE  // 删除（标记删除/本周停课）
}

// 课程编辑与调整完整流程控制器
@Composable
fun CourseEditFlow(
    event: CourseEventWithMeta,
    currentWeek: Int,
    totalWeeks: Int = 20,
    currentSemesterId: String = "",
    courseColors: List<CourseColor>,
    existingAdjustment: CourseAdjustment?,
    adjustmentsList: List<CourseAdjustmentWithMeta> = emptyList(),
    getCourseAtSlot: (week: Int, day: Int, startSection: Int, endSection: Int) -> CourseEventWithMeta?,
    onMoveCourse: (CourseAdjustment) -> Unit,
    onSwapCourses: (CourseAdjustment, CourseAdjustment) -> Unit,
    onMarkDeleted: (CourseAdjustment) -> Unit,
    onDeleteAdjustment: (Long) -> Unit,
    onClearAllAdjustments: () -> Unit,
    onColorSelected: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    var currentPage by remember { mutableStateOf(CourseEditPage.MENU) }

    when (currentPage) {
        CourseEditPage.MENU -> {
            CourseEditMenuSheet(
                onNavigateToPalette = { currentPage = CourseEditPage.PALETTE },
                onNavigateToAdjustment = { currentPage = CourseEditPage.ADJUSTMENT },
                onNavigateToRecords = { currentPage = CourseEditPage.RECORDS },
                onDismissRequest = onDismissRequest
            )
        }
        CourseEditPage.PALETTE -> {
            CoursePaletteSubSheet(
                event = event,
                courseColors = courseColors,
                onColorSelected = onColorSelected,
                onBack = { currentPage = CourseEditPage.MENU },
                onDismissRequest = onDismissRequest
            )
        }
        CourseEditPage.ADJUSTMENT -> {
            CourseAdjustmentSubSheet(
                event = event,
                currentWeek = currentWeek,
                totalWeeks = totalWeeks,
                currentSemesterId = currentSemesterId,
                existingAdjustment = existingAdjustment,
                getCourseAtSlot = getCourseAtSlot,
                onMoveCourse = onMoveCourse,
                onSwapCourses = onSwapCourses,
                onMarkDeleted = onMarkDeleted,
                onDeleteAdjustment = onDeleteAdjustment,
                onBack = { currentPage = CourseEditPage.MENU },
                onDismissRequest = onDismissRequest
            )
        }
        CourseEditPage.RECORDS -> {
            CourseAdjustmentRecordsSubSheet(
                adjustments = adjustmentsList,
                courseColors = courseColors,
                onDeleteAdjustment = onDeleteAdjustment,
                onClearAllAdjustments = onClearAllAdjustments,
                onBack = { currentPage = CourseEditPage.MENU },
                onDismissRequest = onDismissRequest
            )
        }
    }
}

// 二级页面：功能菜单
@Composable
private fun CourseEditMenuSheet(
    onNavigateToPalette: () -> Unit,
    onNavigateToAdjustment: () -> Unit,
    onNavigateToRecords: () -> Unit,
    onDismissRequest: () -> Unit
) {
    WindowBottomSheet(
        show = true,
        title = stringResource(R.string.course_edit_menu_title),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.course_edit_menu_color),
                        summary = stringResource(R.string.course_edit_menu_color_desc),
                        onClick = onNavigateToPalette
                    )
                    ArrowPreference(
                        title = stringResource(R.string.course_edit_menu_adjust),
                        summary = stringResource(R.string.course_edit_menu_adjust_desc),
                        onClick = onNavigateToAdjustment
                    )
                    ArrowPreference(
                        title = stringResource(R.string.course_edit_menu_records),
                        summary = stringResource(R.string.course_edit_menu_records_desc),
                        onClick = onNavigateToRecords
                    )
                }
            }
        }
    }
}
