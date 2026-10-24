package top.msfxp.schedule.ui.schedule.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.CourseAdjustmentWithMeta
import top.msfxp.schedule.data.model.CourseColor
import top.msfxp.schedule.data.model.getCourseColor
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

// 调课记录列表子抽屉
@Composable
fun CourseAdjustmentRecordsSubSheet(
    adjustments: List<CourseAdjustmentWithMeta>,
    courseColors: List<CourseColor>,
    onDeleteAdjustment: (Long) -> Unit,
    onClearAllAdjustments: () -> Unit,
    onBack: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

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
        title = stringResource(R.string.adjustments_screen_title),
        startAction = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MiuixIcons.ChevronBackward,
                    contentDescription = stringResource(R.string.common_back),
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        endAction = {
            if (adjustments.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(
                        imageVector = MiuixIcons.Clear,
                        contentDescription = stringResource(R.string.adjustments_action_clear_all)
                    )
                }
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
            if (adjustments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.adjustments_empty_title),
                        style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.SemiBold),
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 200.dp)
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(adjustments, key = { it.adjustment.id }) { item ->
                        val adj = item.adjustment
                        val origDayStr = if (adj.originalDay in 1..7) dayNames[adj.originalDay - 1] else stringResource(R.string.course_pending_location)
                        val targetDayStr = if (adj.targetDay in 1..7) dayNames[adj.targetDay - 1] else stringResource(R.string.course_pending_location)
                        val itemColor = courseColors.getCourseColor(item.colorIndex).light

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(itemColor)
                                        )
                                        Text(
                                            text = item.courseName,
                                            style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold),
                                            color = MiuixTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (item.isPractical && item.projectName.isNotBlank()) {
                                        Text(
                                            text = "(${item.projectName})",
                                            style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.Normal),
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (adj.isSuspended) {
                                        Text(
                                            text = stringResource(
                                                R.string.adjustments_suspended_desc,
                                                adj.originalWeek,
                                                origDayStr,
                                                adj.originalStartSection,
                                                adj.originalEndSection
                                            ),
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.error,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else {
                                        val origLocation = adj.originalLocation.ifBlank { stringResource(R.string.course_label_classroom) }
                                        val targetLocation = adj.targetLocation.ifBlank { origLocation }
                                        Text(
                                            text = stringResource(
                                                R.string.adjustments_original_label,
                                                adj.originalWeek,
                                                origDayStr,
                                                adj.originalStartSection,
                                                adj.originalEndSection,
                                                origLocation
                                            ),
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.adjustments_target_label,
                                                adj.targetWeek,
                                                targetDayStr,
                                                adj.targetStartSection,
                                                adj.targetEndSection,
                                                targetLocation
                                            ),
                                            style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
                                            color = MiuixTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onDeleteAdjustment(adj.id) },
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Delete,
                                        contentDescription = stringResource(R.string.adjustments_restore_single),
                                        tint = MiuixTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.adjustments_clear_dialog_title),
            summary = stringResource(R.string.adjustments_clear_dialog_message),
            onDismissRequest = { showClearDialog = false }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showClearDialog = false },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
                Button(
                    onClick = {
                        onClearAllAdjustments()
                        showClearDialog = false
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.adjustments_action_clear_all),
                        color = MiuixTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
