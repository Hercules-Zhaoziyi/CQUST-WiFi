package top.msfxp.schedule.ui.schedule.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.CourseAdjustment
import top.msfxp.schedule.data.model.CourseEventWithMeta
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

// 课程调整底部抽屉（移动、调换、删除）
@Composable
fun CourseAdjustmentSubSheet(
    event: CourseEventWithMeta,
    currentWeek: Int,
    totalWeeks: Int = 20,
    currentSemesterId: String = "",
    existingAdjustment: CourseAdjustment?,
    getCourseAtSlot: (week: Int, day: Int, startSection: Int, endSection: Int) -> CourseEventWithMeta?,
    onMoveCourse: (CourseAdjustment) -> Unit,
    onSwapCourses: (CourseAdjustment, CourseAdjustment) -> Unit,
    onMarkDeleted: (CourseAdjustment) -> Unit,
    onDeleteAdjustment: (Long) -> Unit,
    onBack: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var mode by remember(existingAdjustment) {
        mutableStateOf(
            if (existingAdjustment?.isSuspended == true) CourseAdjustmentMode.DELETE
            else CourseAdjustmentMode.MOVE
        )
    }

    var targetWeek by remember(existingAdjustment, currentWeek) {
        mutableIntStateOf(existingAdjustment?.targetWeek ?: currentWeek)
    }
    var targetDay by remember(existingAdjustment, event.day) {
        mutableIntStateOf(existingAdjustment?.targetDay ?: if (event.day in 1..7) event.day else 1)
    }
    var targetStartSection by remember(existingAdjustment, event.startSection) {
        mutableIntStateOf(existingAdjustment?.targetStartSection ?: if (event.startSection > 0) event.startSection else 1)
    }
    var targetEndSection by remember(existingAdjustment, event.endSection) {
        mutableIntStateOf(existingAdjustment?.targetEndSection ?: if (event.endSection > 0) event.endSection else 2)
    }
    var targetLocation by remember(existingAdjustment, event.location) {
        mutableStateOf(existingAdjustment?.targetLocation ?: event.location)
    }

    // 数据变更检测
    val isSlotChanged = targetWeek != currentWeek ||
            targetDay != event.day ||
            targetStartSection != event.startSection ||
            targetEndSection != event.endSection
    val isLocationChanged = targetLocation.trim() != event.location.trim()

    val isSameAsOriginal = !isSlotChanged && !isLocationChanged

    val isSameAsExisting = existingAdjustment != null &&
            targetWeek == existingAdjustment.targetWeek &&
            targetDay == existingAdjustment.targetDay &&
            targetStartSection == existingAdjustment.targetStartSection &&
            targetEndSection == existingAdjustment.targetEndSection &&
            targetLocation.trim() == existingAdjustment.targetLocation.trim()

    val isDataChanged = !isSameAsOriginal && !isSameAsExisting

    // 实时检测目标坐标课程（未更改时段或自身课程直接判定为无目标冲突课程）
    val targetConflictCourse = remember(targetWeek, targetDay, targetStartSection, targetEndSection, isSlotChanged) {
        if (!isSlotChanged) {
            null
        } else {
            val course = getCourseAtSlot(targetWeek, targetDay, targetStartSection, targetEndSection)
            if (course != null && course.courseId == event.courseId && course.week == currentWeek && course.day == event.day && course.startSection == event.startSection) {
                null
            } else {
                course
            }
        }
    }

    val isSpanMatched = targetConflictCourse == null ||
            (targetConflictCourse.endSection - targetConflictCourse.startSection) == (event.endSection - event.startSection)

    val dayNames = listOf(
        stringResource(R.string.day_monday),
        stringResource(R.string.day_tuesday),
        stringResource(R.string.day_wednesday),
        stringResource(R.string.day_thursday),
        stringResource(R.string.day_friday),
        stringResource(R.string.day_saturday),
        stringResource(R.string.day_sunday)
    )

    val dayShorts = listOf(
        1 to stringResource(R.string.day_mon_short),
        2 to stringResource(R.string.day_tue_short),
        3 to stringResource(R.string.day_wed_short),
        4 to stringResource(R.string.day_thu_short),
        5 to stringResource(R.string.day_fri_short),
        6 to stringResource(R.string.day_sat_short),
        7 to stringResource(R.string.day_sun_short)
    )

    val isFourSectionSpan = (event.endSection - event.startSection + 1) == 4

    // 节次预设选项：4 节大课与 2 节普通课
    val sectionPresets = if (isFourSectionSpan) {
        listOf(
            SectionPreset(1, 4, stringResource(R.string.course_section_range_1_4)),
            SectionPreset(5, 8, stringResource(R.string.course_section_range_5_8))
        )
    } else {
        listOf(
            SectionPreset(1, 2, stringResource(R.string.course_section_range_1_2)),
            SectionPreset(3, 4, stringResource(R.string.course_section_range_3_4)),
            SectionPreset(5, 6, stringResource(R.string.course_section_range_5_6)),
            SectionPreset(7, 8, stringResource(R.string.course_section_range_7_8)),
            SectionPreset(9, 10, stringResource(R.string.course_section_range_9_10))
        )
    }

    val origDayStr = if (event.day in 1..7) dayNames[event.day - 1] else stringResource(R.string.course_pending_location)
    val targetDayStr = if (targetDay in 1..7) dayNames[targetDay - 1] else stringResource(R.string.course_pending_location)

    val isConfirmEnabled = when (mode) {
        CourseAdjustmentMode.MOVE -> isDataChanged && (!isSlotChanged || targetConflictCourse == null)
        CourseAdjustmentMode.SWAP -> isSlotChanged && targetConflictCourse != null && isSpanMatched
        CourseAdjustmentMode.DELETE -> true
    }

    val origLoc = existingAdjustment?.originalLocation?.ifBlank { event.location } ?: event.location

    WindowBottomSheet(
        show = true,
        title = stringResource(R.string.course_edit_adjust_title),
        startAction = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MiuixIcons.Basic.Close,
                    contentDescription = stringResource(R.string.common_cancel)
                )
            }
        },
        endAction = {
            IconButton(
                enabled = isConfirmEnabled,
                onClick = {
                    val tableId = event.tableId.ifBlank { "default" }
                    when (mode) {
                        CourseAdjustmentMode.MOVE -> {
                            val adj = CourseAdjustment(
                                id = existingAdjustment?.id ?: 0,
                                tableId = tableId,
                                semesterId = currentSemesterId,
                                courseId = event.courseId,
                                originalWeek = currentWeek,
                                originalDay = event.day,
                                originalStartSection = event.startSection,
                                originalEndSection = event.endSection,
                                originalLocation = origLoc,
                                targetWeek = targetWeek,
                                targetDay = targetDay,
                                targetStartSection = targetStartSection,
                                targetEndSection = targetEndSection,
                                targetLocation = targetLocation.trim(),
                                isSuspended = false
                            )
                            onMoveCourse(adj)
                        }
                        CourseAdjustmentMode.SWAP -> {
                            val conflict = targetConflictCourse ?: return@IconButton
                            val adjA = CourseAdjustment(
                                id = existingAdjustment?.id ?: 0,
                                tableId = tableId,
                                semesterId = currentSemesterId,
                                courseId = event.courseId,
                                originalWeek = currentWeek,
                                originalDay = event.day,
                                originalStartSection = event.startSection,
                                originalEndSection = event.endSection,
                                originalLocation = origLoc,
                                targetWeek = targetWeek,
                                targetDay = conflict.day,
                                targetStartSection = conflict.startSection,
                                targetEndSection = conflict.endSection,
                                targetLocation = if (targetLocation.isNotBlank()) targetLocation.trim() else conflict.location,
                                isSuspended = false
                            )
                            val adjB = CourseAdjustment(
                                id = 0,
                                tableId = tableId,
                                semesterId = currentSemesterId,
                                courseId = conflict.courseId,
                                originalWeek = targetWeek,
                                originalDay = conflict.day,
                                originalStartSection = conflict.startSection,
                                originalEndSection = conflict.endSection,
                                originalLocation = conflict.location,
                                targetWeek = currentWeek,
                                targetDay = event.day,
                                targetStartSection = event.startSection,
                                targetEndSection = event.endSection,
                                targetLocation = event.location,
                                isSuspended = false
                            )
                            onSwapCourses(adjA, adjB)
                        }
                        CourseAdjustmentMode.DELETE -> {
                            val adj = CourseAdjustment(
                                id = existingAdjustment?.id ?: 0,
                                tableId = tableId,
                                semesterId = currentSemesterId,
                                courseId = event.courseId,
                                originalWeek = currentWeek,
                                originalDay = event.day,
                                originalStartSection = event.startSection,
                                originalEndSection = event.endSection,
                                originalLocation = origLoc,
                                targetWeek = currentWeek,
                                targetDay = event.day,
                                targetStartSection = event.startSection,
                                targetEndSection = event.endSection,
                                targetLocation = event.location,
                                isSuspended = true
                            )
                            onMarkDeleted(adj)
                        }
                    }
                    onDismissRequest()
                }
            ) {
                Icon(
                    imageVector = if (mode == CourseAdjustmentMode.DELETE) MiuixIcons.Delete else MiuixIcons.Basic.Check,
                    contentDescription = stringResource(
                        if (mode == CourseAdjustmentMode.DELETE) R.string.course_edit_mode_delete else R.string.common_confirm
                    ),
                    tint = if (isConfirmEnabled) {
                        if (mode == CourseAdjustmentMode.DELETE) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary
                    } else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f)
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 当前课程信息卡片
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.course_edit_current_course_title),
                        style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Text(
                        text = event.courseName,
                        style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Bold),
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (event.isPractical && event.projectName.isNotBlank()) {
                        Text(
                            text = "(${event.projectName})",
                            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Normal),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.course_edit_current_course_desc,
                            currentWeek,
                            origDayStr,
                            event.startSection,
                            event.endSection
                        ) + " · ${event.location.ifBlank { stringResource(R.string.course_pending_location) }}",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 处理方式：移动 / 调换 / 删除
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.course_edit_mode_title),
                    style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        CourseAdjustmentMode.MOVE to stringResource(R.string.course_edit_mode_move),
                        CourseAdjustmentMode.SWAP to stringResource(R.string.course_edit_mode_swap),
                        CourseAdjustmentMode.DELETE to stringResource(R.string.course_edit_mode_delete)
                    ).forEach { (m, label) ->
                        val isSelected = mode == m
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.surfaceVariant
                                )
                                .clickable { mode = m },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MiuixTheme.textStyles.body2.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 模式详细配置
            when (mode) {
                CourseAdjustmentMode.DELETE -> {
                    // 标记删除说明
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.error.copy(alpha = 0.1f))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.course_edit_hint_delete, origDayStr, event.startSection, event.endSection),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.error
                        )
                    }
                }
                CourseAdjustmentMode.MOVE, CourseAdjustmentMode.SWAP -> {
                    // 目标周次选择 (1 2 3 4 ... 20)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.course_edit_target_week),
                            style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (w in 1..totalWeeks) {
                                val isSelected = targetWeek == w
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MiuixTheme.colorScheme.primary
                                            else MiuixTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { targetWeek = w },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$w",
                                        style = MiuixTheme.textStyles.body2.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // 目标星期选择 (一 二 三 四 五 六 日)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.course_edit_target_day),
                            style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            dayShorts.forEach { (d, label) ->
                                val isSelected = targetDay == d
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) MiuixTheme.colorScheme.primary
                                            else MiuixTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { targetDay = d },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MiuixTheme.textStyles.body2.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // 目标节次选择 (1-2, 3-4, 5-6, 7-8, 9-10, 11-12)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.course_edit_target_sections),
                            style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            sectionPresets.forEach { preset ->
                                val isSelected = targetStartSection == preset.start && targetEndSection == preset.end
                                Box(
                                    modifier = Modifier
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MiuixTheme.colorScheme.primary
                                            else MiuixTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable {
                                            targetStartSection = preset.start
                                            targetEndSection = preset.end
                                        }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = preset.label,
                                        style = MiuixTheme.textStyles.body2.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // 目标地点输入
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.course_edit_target_location),
                            style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        TextField(
                            value = targetLocation,
                            onValueChange = { targetLocation = it },
                            label = stringResource(R.string.course_edit_target_location_hint),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    // 状态提示条
                    if (mode == CourseAdjustmentMode.MOVE) {
                        if (isSlotChanged) {
                            if (targetConflictCourse != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MiuixTheme.colorScheme.error.copy(alpha = 0.12f))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.course_edit_hint_move_invalid, targetConflictCourse.inlineTitle),
                                        style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
                                        color = MiuixTheme.colorScheme.error,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF10B981).copy(alpha = 0.12f))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.course_edit_hint_move_valid),
                                        style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFF059669)
                                    )
                                }
                            }
                        }
                    } else if (mode == CourseAdjustmentMode.SWAP) {
                        if (isSlotChanged) {
                            if (targetConflictCourse != null) {
                                if (!isSpanMatched) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MiuixTheme.colorScheme.error.copy(alpha = 0.12f))
                                            .padding(12.dp)
                                    ) {
                                        val currentSpan = event.endSection - event.startSection + 1
                                        val targetSpan = targetConflictCourse.endSection - targetConflictCourse.startSection + 1
                                        Text(
                                            text = stringResource(R.string.course_edit_hint_swap_span_mismatch, currentSpan, targetSpan),
                                            style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
                                            color = MiuixTheme.colorScheme.error,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = stringResource(
                                                R.string.course_edit_hint_swap_valid,
                                                targetConflictCourse.inlineTitle,
                                                targetDayStr,
                                                targetConflictCourse.startSection,
                                                targetConflictCourse.endSection,
                                                targetConflictCourse.location.ifBlank { stringResource(R.string.course_pending_location) }
                                            ),
                                            style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
                                            color = MiuixTheme.colorScheme.primary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.course_edit_hint_swap_invalid),
                                        style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFFD97706)
                                    )
                                }
                            }
                        }
                    } else if (mode == CourseAdjustmentMode.DELETE) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MiuixTheme.colorScheme.error.copy(alpha = 0.12f))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.course_edit_hint_delete,
                                    origDayStr,
                                    event.startSection,
                                    event.endSection
                                ),
                                style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
                                color = MiuixTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // 恢复原课操作
            if (existingAdjustment != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        onDeleteAdjustment(existingAdjustment.id)
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.course_edit_btn_restore),
                        color = MiuixTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// 调课节次选项模型
private data class SectionPreset(
    val start: Int,
    val end: Int,
    val label: String
)
