package top.msfxp.schedule.ui.schedule.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.ExamRecord
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 详情页最小高度，对齐课程页与考试页
internal val DetailPageMinHeight = 496.dp

// 考试详情页内容
@Composable
internal fun ExamDetailPage(
    exam: ExamRecord,
    themeColor: Color,
    currentWeekNumber: Int,
    dayNames: List<String>,
    onOpenMap: (String) -> Unit
) {
    val dayStr = exam.resolvedWeekday?.let { dayNames.getOrNull(it - 1) }
        ?: stringResource(R.string.course_sheet_not_assigned)
    val dateStr = exam.examDateValue
        ?.let { stringResource(R.string.exam_date_format, it.monthValue, it.dayOfMonth) }
        .orEmpty()
    val timeRangeStr = listOfNotNull(exam.startTime, exam.endTime).joinToString("-")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DetailPageMinHeight),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ExamDetailHeader(
            exam = exam,
            themeColor = themeColor,
            dateStr = dateStr,
            dayStr = dayStr
        )
        DetailLocationCard(
            label = stringResource(R.string.exam_detail_label_location),
            locationText = exam.displayPosition,
            rawPosition = exam.position.orEmpty(),
            accentColor = themeColor,
            onOpenMap = onOpenMap
        )
        ExamDetailPropertiesGrid(
            exam = exam,
            timeRangeStr = timeRangeStr
        )
        // 教务返回的考试周次为权威值，缺失时回退所在网格周
        CourseDetailWeeksMatrix(
            label = stringResource(R.string.exam_detail_label_week),
            activeWeeksSet = setOf(exam.week ?: currentWeekNumber),
            currentWeekNumber = currentWeekNumber,
            courseThemeColor = themeColor
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

// 考试详情标题区
@Composable
private fun ExamDetailHeader(
    exam: ExamRecord,
    themeColor: Color,
    dateStr: String,
    dayStr: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 5.dp, height = 26.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(themeColor)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 62.dp)
        ) {
            Text(
                text = exam.courseName,
                style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold),
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(themeColor.copy(alpha = 0.14f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.exam_type_label),
                        style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                        color = themeColor
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = listOf(dateStr, dayStr)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                            .ifBlank { stringResource(R.string.exam_detail_pending_arrange) },
                        style = MiuixTheme.textStyles.footnote2.copy(fontSize = 11.sp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }
}

// 考试详细属性网格
@Composable
private fun ExamDetailPropertiesGrid(
    exam: ExamRecord,
    timeRangeStr: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            InfoGridItem(
                label = stringResource(R.string.exam_detail_label_time),
                value = timeRangeStr.ifBlank { stringResource(R.string.exam_detail_pending_arrange) },
                modifier = Modifier.weight(1f)
            )
            InfoGridItem(
                label = stringResource(R.string.exam_detail_label_seat),
                value = exam.seatNo?.takeIf { it.isNotBlank() }
                    ?.let { stringResource(R.string.exam_seat_number_format, it) }
                    ?: stringResource(R.string.course_sheet_unspecified),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            InfoGridItem(
                label = stringResource(R.string.course_sheet_label_code),
                value = exam.courseCode.ifBlank { stringResource(R.string.course_sheet_unspecified) },
                modifier = Modifier.weight(1f)
            )
            InfoGridItem(
                label = stringResource(R.string.exam_detail_label_status),
                value = exam.status?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.course_sheet_unspecified),
                modifier = Modifier.weight(1f)
            )
        }
        if (exam.remark.isNotBlank()) {
            InfoGridItem(
                label = stringResource(R.string.exam_detail_label_remark),
                value = exam.remark,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
        }
    }
}
