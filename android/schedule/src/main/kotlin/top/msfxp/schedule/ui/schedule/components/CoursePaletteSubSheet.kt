package top.msfxp.schedule.ui.schedule.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.CourseColor
import top.msfxp.schedule.data.model.CourseEventWithMeta
import top.msfxp.schedule.data.model.DefaultCourseColors
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

// 课程调色板选择底部抽屉
@Composable
fun CoursePaletteSubSheet(
    event: CourseEventWithMeta,
    courseColors: List<CourseColor>,
    onColorSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var selectedIdx by remember(event.colorIndex) { mutableIntStateOf(event.colorIndex) }

    WindowBottomSheet(
        show = true,
        title = stringResource(R.string.course_pick_color_title),
        startAction = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MiuixIcons.ChevronBackward,
                    contentDescription = stringResource(R.string.common_back),
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.course_pick_color_summary, event.courseName),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )

            // 4x4 网格
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (row in 0..3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (col in 0..3) {
                            val slotIdx = row * 4 + col
                            val colorItem = courseColors.getOrNull(slotIdx) ?: DefaultCourseColors[0]
                            val isSelected = selectedIdx == slotIdx

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colorItem.light)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        selectedIdx = slotIdx
                                        onColorSelected(slotIdx)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = MiuixIcons.Basic.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Text(
                                        text = "${slotIdx + 1}",
                                        style = MiuixTheme.textStyles.footnote2.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
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
