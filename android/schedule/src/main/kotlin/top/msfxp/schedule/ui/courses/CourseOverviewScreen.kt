package top.msfxp.schedule.ui.courses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.DefaultCourseColors
import top.msfxp.schedule.data.model.EXAM_MODE_WRITTEN
import top.msfxp.schedule.data.model.ExamRecord
import top.msfxp.schedule.data.model.findSectionTimeSlot
import top.msfxp.schedule.data.model.formatWeeksSummary
import top.msfxp.schedule.data.model.getCourseColor
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate

// 课程总览界面
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseOverviewScreen(
    onBack: () -> Unit,
    viewModel: CourseOverviewViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val dayNames = listOf(
        stringResource(R.string.day_monday),
        stringResource(R.string.day_tuesday),
        stringResource(R.string.day_wednesday),
        stringResource(R.string.day_thursday),
        stringResource(R.string.day_friday),
        stringResource(R.string.day_saturday),
        stringResource(R.string.day_sunday)
    )

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.course_overview_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.ChevronBackward,
                            contentDescription = stringResource(R.string.common_back),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 学期课程统计概览卡片
            if (uiState.courses.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        FlowRow(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.course_summary_prefix),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            Text(
                                text = "${uiState.theoryCount}",
                                style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                                color = MiuixTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.course_summary_unit_theory),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            Text(
                                text = "${uiState.practicalCount}",
                                style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                                color = MiuixTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.course_summary_unit_practical),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            if (uiState.totalCredits > 0) {
                                Text(
                                    text = stringResource(R.string.course_summary_prefix_credits),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                Text(
                                    text = "${uiState.totalCredits}",
                                    style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                                    color = MiuixTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.course_summary_unit_credits),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    }
                }
            }

            // 课程列表
            if (uiState.courses.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.course_empty_data),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            } else {
                items(uiState.courses, key = { it.courseId }) { item ->
                    val courseThemeColor = uiState.courseColors.getCourseColor(item.colorIndex).light
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 课程头部信息
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 10.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 3.dp)
                                            .size(width = 4.dp, height = 20.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(courseThemeColor)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Bold),
                                            color = MiuixTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (item.category.isNotBlank()) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF8B5CF6).copy(alpha = 0.12f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = item.category,
                                                        style = MiuixTheme.textStyles.footnote2.copy(
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 10.sp
                                                        ),
                                                        color = Color(0xFF8B5CF6)
                                                    )
                                                }
                                            }
                                            if (item.hasTheory && item.hasPractical) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF059669).copy(alpha = 0.12f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.course_overview_badge_combined),
                                                        style = MiuixTheme.textStyles.footnote2.copy(
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 10.sp
                                                        ),
                                                        color = Color(0xFF059669)
                                                    )
                                                }
                                            } else if (item.isPracticalOnly && item.projectCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF059669).copy(alpha = 0.12f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.course_overview_practice_items_format, item.projectCount),
                                                        style = MiuixTheme.textStyles.footnote2.copy(
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 10.sp
                                                        ),
                                                        color = Color(0xFF059669)
                                                    )
                                                }
                                            }
                                            if (item.credit.isNotBlank()) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF2563EB).copy(alpha = 0.12f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.course_overview_credit_format, item.credit),
                                                        style = MiuixTheme.textStyles.footnote2.copy(
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 10.sp
                                                        ),
                                                        color = Color(0xFF2563EB)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (item.primaryTeacher.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .widthIn(max = 110.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MiuixTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = item.primaryTeacher,
                                            style = MiuixTheme.textStyles.footnote2.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // 课程时段展示
                            if (item.hasTheory) {
                                CourseSlotSection(
                                    title = stringResource(R.string.course_overview_section_theory),
                                    slots = item.theorySlots,
                                    courseThemeColor = courseThemeColor,
                                    dayNames = dayNames
                                )
                            }
                            if (item.hasPractical) {
                                CourseSlotSection(
                                    title = stringResource(R.string.course_overview_section_practical),
                                    slots = item.practicalSlots,
                                    trailingText = item.practicalTeacher,
                                    courseThemeColor = courseThemeColor,
                                    dayNames = dayNames
                                )
                            }

                            if (item.hasExam) {
                                CourseExamSection(
                                    exams = item.exams,
                                    dayNames = dayNames,
                                    courseThemeColor = courseThemeColor
                                )
                            }
                        }
                    }
                }
            }

            // 未匹配课程的考试记录
            if (uiState.unmatchedExams.isNotEmpty()) {
                item {
                    UnmatchedExamCard(exams = uiState.unmatchedExams, dayNames = dayNames)
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// 课程时段区块（标题 + 时段列表）
@Composable
private fun CourseSlotSection(
    title: String,
    slots: List<top.msfxp.schedule.data.model.CompressedCourseSlot>,
    courseThemeColor: Color,
    dayNames: List<String>,
    trailingText: String = ""
) {
    if (slots.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.Bold),
                color = MiuixTheme.colorScheme.onSurface
            )
            if (trailingText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = trailingText,
                        style = MiuixTheme.textStyles.footnote2.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
        CourseSlotListBlock(
            slots = slots,
            courseThemeColor = courseThemeColor,
            dayNames = dayNames
        )
    }
}

// 课程时段列表块
@Composable
private fun CourseSlotListBlock(
    slots: List<top.msfxp.schedule.data.model.CompressedCourseSlot>,
    courseThemeColor: Color,
    dayNames: List<String>,
    modifier: Modifier = Modifier
) {
    if (slots.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        slots.forEach { slotItem ->
            val isUnarranged = slotItem.day == 0 || slotItem.startSection == 0
            val dayStr = if (isUnarranged) stringResource(R.string.course_pending_location)
            else dayNames.getOrElse(slotItem.day - 1) { stringResource(R.string.day_number_format, slotItem.day) }
            val slotStart = findSectionTimeSlot(slotItem.startSection)
            val slotEnd = findSectionTimeSlot(slotItem.endSection) ?: slotStart
            val secStr = when {
                isUnarranged -> stringResource(R.string.course_practice_training)
                slotItem.startSection < 0 && slotStart != null && slotEnd != null ->
                    "${slotStart.startTime} - ${slotEnd.endTime}"
                else -> stringResource(R.string.course_section_format, slotItem.startSection, slotItem.endSection)
            }
            val weeksStr = slotItem.weeks.formatWeeksSummary()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = dayStr,
                            style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold),
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = secStr,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        if (slotItem.projectName.isNotBlank()) {
                            Text(
                                text = slotItem.projectName,
                                style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.Medium),
                                color = courseThemeColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.course_label_classroom),
                            style = MiuixTheme.textStyles.footnote2.copy(fontSize = 10.sp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Text(
                            text = slotItem.displayLocation.ifBlank { stringResource(R.string.course_pending_location) },
                            style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 周次标签
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(courseThemeColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = weeksStr,
                        style = MiuixTheme.textStyles.footnote2.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}

// 课程卡内考试安排区块
@Composable
private fun CourseExamSection(
    exams: List<ExamRecord>,
    dayNames: List<String>,
    courseThemeColor: Color,
    modifier: Modifier = Modifier
) {
    if (exams.isEmpty()) return
    val sharedMode = exams.map { it.examMode }.distinct().singleOrNull()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.exam_type_label),
                style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.Bold),
                color = MiuixTheme.colorScheme.onSurface
            )
            if (sharedMode != null) ExamModeLabel(sharedMode)
        }
        exams.forEach { exam ->
            ExamInfoBlock(
                exam = exam,
                dayNames = dayNames,
                showModeBadge = sharedMode == null,
                courseThemeColor = courseThemeColor
            )
        }
    }
}

// 考试形式标签
@Composable
private fun ExamModeLabel(mode: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = stringResource(
                if (mode == EXAM_MODE_WRITTEN) R.string.exam_mode_written
                else R.string.exam_mode_non_standard
            ),
            style = MiuixTheme.textStyles.footnote2.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            ),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

// 单条考试信息
@Composable
private fun ExamInfoBlock(
    exam: ExamRecord,
    dayNames: List<String>,
    showModeBadge: Boolean,
    courseThemeColor: Color = MiuixTheme.colorScheme.primary
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (exam.isScheduled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.exam_datetime_format,
                        formatExamDate(exam.examDate),
                        examWeekdayText(exam, dayNames),
                        exam.startTime.orEmpty(),
                        exam.endTime.orEmpty()
                    ),
                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold),
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (showModeBadge) ExamModeLabel(exam.examMode)
                exam.week?.let { week ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(courseThemeColor)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.exam_week_format, week),
                            style = MiuixTheme.textStyles.footnote2.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
            ExamDetailLine(exam = exam)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = exam.remark,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f)
                )
                if (showModeBadge) ExamModeLabel(exam.examMode)
            }
        }
    }
}

// 未匹配课程的考试独立卡片
@Composable
private fun UnmatchedExamCard(
    exams: List<ExamRecord>,
    dayNames: List<String>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.exam_type_label),
                style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Bold),
                color = MiuixTheme.colorScheme.onSurface
            )
            exams.forEach { exam ->
                Text(
                    text = exam.courseName,
                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
                    color = MiuixTheme.colorScheme.onSurface
                )
                ExamInfoBlock(exam = exam, dayNames = dayNames, showModeBadge = true)
            }
        }
    }
}

// 格式化考试日期
@Composable
private fun formatExamDate(examDate: String?): String {
    val date = examDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return stringResource(R.string.course_pending_location)
    return stringResource(R.string.exam_date_format, date.monthValue, date.dayOfMonth)
}

// 解析考试星期文案
private fun examWeekdayText(exam: ExamRecord, dayNames: List<String>): String {
    val weekdayIndex = exam.examDateValue?.dayOfWeek?.value ?: exam.weekday ?: 0
    return dayNames.getOrElse(weekdayIndex - 1) { "" }
}

// 组装考试地点、座位与状态
@Composable
private fun ExamDetailLine(exam: ExamRecord) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = exam.displayPosition.ifBlank { stringResource(R.string.course_pending_location) },
            style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
            color = MiuixTheme.colorScheme.onSurface
        )
        if (!exam.seatNo.isNullOrBlank()) {
            Text(
                text = "· " + stringResource(R.string.exam_seat_number_format, exam.seatNo),
                style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
                color = MiuixTheme.colorScheme.onSurface
            )
        }
        if (!exam.status.isNullOrBlank()) {
            Text(
                text = "· " + exam.status,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}
