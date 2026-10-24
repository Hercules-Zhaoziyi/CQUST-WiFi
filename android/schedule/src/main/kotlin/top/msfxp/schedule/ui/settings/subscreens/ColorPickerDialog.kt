package top.msfxp.schedule.ui.settings.subscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.CourseColor
import top.msfxp.schedule.data.model.toHexArgb
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

// 课程颜色编辑抽屉
@Composable
fun ColorPickerDialog(
    show: Boolean,
    slotIndex: Int,
    initialColor: Color,
    defaultColor: CourseColor,
    onDismissRequest: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    if (!show) return

    var workingColor by remember(show, initialColor) { mutableStateOf(initialColor) }

    WindowBottomSheet(
        show = show,
        title = stringResource(R.string.personalization_color_dialog_title, slotIndex + 1),
        onDismissRequest = onDismissRequest,
        startAction = {
            val dismiss = LocalDismissState.current
            IconButton(
                onClick = {
                    dismiss?.invoke()
                    onDismissRequest()
                }
            ) {
                Icon(
                    imageVector = MiuixIcons.Basic.Close,
                    contentDescription = stringResource(R.string.common_cancel)
                )
            }
        },
        endAction = {
            val dismiss = LocalDismissState.current
            IconButton(
                onClick = {
                    onConfirm(workingColor)
                    dismiss?.invoke()
                    onDismissRequest()
                }
            ) {
                Icon(
                    imageVector = MiuixIcons.Basic.Check,
                    contentDescription = stringResource(R.string.common_confirm)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 色值预览条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(workingColor)
                    )
                    Text(
                        text = workingColor.toHexArgb(),
                        style = MiuixTheme.textStyles.title3.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp
                        ),
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }

                // 恢复默认颜色按钮
                IconButton(
                    onClick = { workingColor = defaultColor.light }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Clear,
                        contentDescription = stringResource(R.string.personalization_color_reset_slot)
                    )
                }
            }

            // Miuix 原生调色盘
            ColorPalette(
                color = workingColor,
                onColorChanged = { workingColor = it },
                showPreview = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

