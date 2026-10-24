package top.msfxp.schedule.ui.schedule.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.CourseEventWithMeta
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.MapAlbum
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 课程详情顶部标题与属性标签
@Composable
fun CourseDetailHeader(
    event: CourseEventWithMeta,
    courseThemeColor: Color,
    dayStr: String,
    isUnarranged: Boolean
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
                .background(courseThemeColor)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 62.dp)
        ) {
            Text(
                text = event.courseName,
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
                if (event.category.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.12f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = event.category,
                            style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                            color = Color(0xFF8B5CF6)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isUnarranged) stringResource(R.string.course_sheet_practice_unarranged)
                        else stringResource(R.string.course_time_format, dayStr, event.startSection, event.endSection),
                        style = MiuixTheme.textStyles.footnote2.copy(fontSize = 11.sp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                if (event.isAdjusted) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.16f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.course_tag_adjusted),
                            style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            color = Color(0xFFD97706)
                        )
                    }
                }
            }
        }
    }
}

// 教室位置及地图跳转卡片
@Composable
fun CourseDetailLocationCard(
    event: CourseEventWithMeta,
    courseThemeColor: Color,
    onOpenMap: (String) -> Unit,
    onNoLocation: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.course_sheet_label_location),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = event.location.ifBlank { stringResource(R.string.course_pending_location) },
                    style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Bold),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(courseThemeColor)
                    .clickable {
                        if (event.location.isNotBlank() && event.location != "待定") {
                            onOpenMap(event.location)
                        } else {
                            onNoLocation()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MiuixIcons.MapAlbum,
                    contentDescription = stringResource(R.string.map_navigate_title),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// 课程详细属性网格
@Composable
fun CourseDetailPropertiesGrid(
    event: CourseEventWithMeta,
    isUnarranged: Boolean,
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
                label = stringResource(R.string.course_sheet_label_teacher),
                value = event.teacher.ifBlank { stringResource(R.string.course_sheet_not_assigned) },
                modifier = Modifier.weight(1f)
            )
            InfoGridItem(
                label = stringResource(R.string.course_sheet_label_time),
                value = if (isUnarranged) stringResource(R.string.course_pending_practice) else timeRangeStr,
                modifier = Modifier.weight(1f)
            )
        }

        if (event.isPractical) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                InfoGridItem(
                    label = stringResource(R.string.course_sheet_label_category),
                    value = event.category.ifBlank { stringResource(R.string.course_type_practical) },
                    modifier = Modifier.weight(1f)
                )
                InfoGridItem(
                    label = stringResource(R.string.course_sheet_label_course_type),
                    value = stringResource(R.string.course_type_practical),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 58.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                InfoGridItem(
                    label = stringResource(R.string.course_sheet_label_project),
                    value = event.projectName.ifBlank { "--" },
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    minHeight = 58.dp
                )
                InfoGridItem(
                    label = stringResource(R.string.course_sheet_label_selected_count),
                    value = if (event.studentCount > 0) stringResource(R.string.course_sheet_count_format, event.studentCount) else stringResource(R.string.course_sheet_unspecified),
                    modifier = Modifier.weight(1f),
                    minHeight = 58.dp
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                InfoGridItem(
                    label = stringResource(R.string.course_sheet_label_category),
                    value = event.category.ifBlank { stringResource(R.string.course_sheet_not_assigned) },
                    modifier = Modifier.weight(1f)
                )
                InfoGridItem(
                    label = stringResource(R.string.course_sheet_label_credit),
                    value = if (event.credit.isNotBlank()) stringResource(R.string.course_overview_credit_format, event.credit) else "--",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 58.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                InfoGridItem(
                    label = stringResource(R.string.course_sheet_label_code),
                    value = event.courseCode.ifBlank { "--" },
                    modifier = Modifier.weight(1f),
                    minHeight = 58.dp
                )
                InfoGridItem(
                    label = stringResource(R.string.course_sheet_label_student_count),
                    value = if (event.studentCount > 0) stringResource(R.string.course_sheet_count_format, event.studentCount) else stringResource(R.string.course_sheet_unspecified),
                    modifier = Modifier.weight(1f),
                    minHeight = 58.dp
                )
            }
        }
    }
}

// 全学期行课周次点阵图
@Composable
fun CourseDetailWeeksMatrix(
    activeWeeksSet: Set<Int>,
    currentWeekNumber: Int,
    courseThemeColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.course_sheet_label_weeks),
                style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                color = MiuixTheme.colorScheme.onSurface
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(2) { rowIdx ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (col in 1..10) {
                            val weekNum = rowIdx * 10 + col
                            val hasClass = activeWeeksSet.contains(weekNum)
                            val isThisWeek = weekNum == currentWeekNumber

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(
                                        if (hasClass) courseThemeColor
                                        else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$weekNum",
                                    style = MiuixTheme.textStyles.footnote2.copy(
                                        fontSize = 10.sp,
                                        fontWeight = if (hasClass || isThisWeek) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = when {
                                        hasClass -> Color.White
                                        isThisWeek -> MiuixTheme.colorScheme.onSurface
                                        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f)
                                    }
                                )

                                if (isThisWeek) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 2.5.dp)
                                            .size(3.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (hasClass) Color.White.copy(alpha = 0.9f)
                                                else MiuixTheme.colorScheme.primary
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 详情属性网格项
@Composable
fun InfoGridItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    minHeight: Dp = 44.dp
) {
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2.copy(fontSize = 11.sp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}
