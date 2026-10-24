package top.msfxp.schedule.ui.schedule.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.msfxp.schedule.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

// 教学周次快速选择抽屉
@Composable
fun WeekSelectorSheet(
    show: Boolean,
    totalWeeks: Int,
    currentWeekNumber: Int,
    selectedWeek: Int,
    weekCourseCountMap: Map<Int, Int> = emptyMap(),
    onWeekSelected: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    if (!show) return

    WindowBottomSheet(
        show = true,
        title = stringResource(R.string.week_selector_title),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 四列周次宫格
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                items(totalWeeks) { index ->
                    val week = index + 1
                    val isSelected = week == selectedWeek
                    val isCurrent = week == currentWeekNumber
                    val count = weekCourseCountMap[week] ?: 0

                    val subText = when {
                        isCurrent && count > 0 -> stringResource(R.string.week_selector_current_with_count, count)
                        isCurrent -> stringResource(R.string.week_selector_current_only)
                        count > 0 -> stringResource(R.string.week_selector_count_format, count)
                        else -> stringResource(R.string.week_selector_no_course)
                    }

                    val bgColor = when {
                        isSelected -> MiuixTheme.colorScheme.primary
                        isCurrent -> MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                        count > 0 -> MiuixTheme.colorScheme.surfaceVariant
                        else -> MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }

                    val textColor = when {
                        isSelected -> MiuixTheme.colorScheme.onPrimary
                        isCurrent -> MiuixTheme.colorScheme.primary
                        else -> MiuixTheme.colorScheme.onSurface
                    }

                    val subTextColor = when {
                        isSelected -> MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        isCurrent -> MiuixTheme.colorScheme.primary
                        count == 0 -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
                        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(bgColor)
                            .clickable {
                                onWeekSelected(week)
                                onDismissRequest()
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isCurrent && !isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(MiuixTheme.colorScheme.primary)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.week_selector_week_format, week),
                                    style = MiuixTheme.textStyles.body2.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    color = textColor
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subText,
                                style = MiuixTheme.textStyles.footnote2.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isCurrent || isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = subTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}
