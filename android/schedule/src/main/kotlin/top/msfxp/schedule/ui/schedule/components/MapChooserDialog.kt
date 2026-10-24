package top.msfxp.schedule.ui.schedule.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.msfxp.schedule.R
import top.msfxp.schedule.util.MapAppType
import top.msfxp.schedule.util.MapNavigationHelper
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.MapAlbum
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

// 地图步行导航选择弹窗
@Composable
fun MapChooserDialog(
    show: Boolean,
    rawPosition: String,
    onDismissRequest: () -> Unit
) {
    if (!show) return
    val context = LocalContext.current
    val destination = remember(rawPosition) {
        MapNavigationHelper.buildWalkingDestination(rawPosition)
    }
    val availableApps = remember(context) {
        MapNavigationHelper.getAvailableMapApps(context)
    }

    WindowDialog(
        show = true,
        title = stringResource(R.string.map_navigate_title),
        summary = stringResource(R.string.map_navigate_destination_label) + ": " + destination,
        onDismissRequest = onDismissRequest
    ) {
        val dismiss = LocalDismissState.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            availableApps.forEachIndexed { index, appType ->
                val isFirst = index == 0
                val isLast = index == availableApps.size - 1

                val itemShape = when {
                    isFirst && isLast -> RoundedCornerShape(16.dp)
                    isFirst -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    isLast -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                    else -> RoundedCornerShape(0.dp)
                }

                // 获取系统已安装地图的图标
                val appIcon = remember(appType.packageName) {
                    try {
                        val drawable = context.packageManager.getApplicationIcon(appType.packageName)
                        drawableToImageBitmap(drawable)
                    } catch (_: Exception) {
                        null
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(itemShape)
                        .clickable {
                            dismiss?.invoke()
                            onDismissRequest()
                            MapNavigationHelper.openMapWalkingNavigation(context, appType, destination)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = stringResource(appType.appNameRes),
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    } else {
                        Icon(
                            imageVector = MiuixIcons.MapAlbum,
                            contentDescription = stringResource(appType.appNameRes),
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = stringResource(appType.appNameRes),
                        style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// 将应用 Drawable 图标转为 Compose ImageBitmap
private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap.asImageBitmap()
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 72
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 72
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}
