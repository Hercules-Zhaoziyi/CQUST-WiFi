package top.msfxp.schedule.ui.schedule.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

// 胶囊拉伸分页指示器
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WormPagerIndicator(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier,
    dotSize: Dp = 6.dp,
    spacing: Dp = 8.dp,
    activeColor: Color = MiuixTheme.colorScheme.primary,
    inactiveColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f)
) {
    if (pageCount <= 1) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragSelectedPage by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .drawWithContent {
                    drawContent()

                    val dotSizePx = dotSize.toPx()
                    val stepPx = dotSizePx + spacing.toPx()
                    val currentPage = dragSelectedPage ?: pagerState.currentPage
                    val fraction = if (dragSelectedPage != null) 0f else pagerState.currentPageOffsetFraction

                    val startDot = if (fraction >= 0) currentPage else currentPage - 1
                    val progress = if (fraction >= 0) fraction else 1f + fraction

                    val headProgress = (progress * 2f).coerceAtMost(1f)
                    val tailProgress = ((progress - 0.5f) * 2f).coerceAtLeast(0f)

                    val leftX = startDot * stepPx + tailProgress * stepPx
                    val rightX = startDot * stepPx + dotSizePx + headProgress * stepPx

                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(x = leftX, y = (size.height - dotSizePx) / 2f),
                        size = Size(width = (rightX - leftX).coerceAtLeast(dotSizePx), height = dotSizePx),
                        cornerRadius = CornerRadius(dotSizePx / 2f, dotSizePx / 2f)
                    )
                }
                .pointerInput(pageCount) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val stepPx = with(density) { (dotSize + spacing).toPx() }
                        val startPage = (down.position.x / stepPx).roundToInt().coerceIn(0, pageCount - 1)
                        dragSelectedPage = startPage

                        scope.launch {
                            pagerState.scrollToPage(startPage)
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val position = event.changes.firstOrNull()?.position ?: break

                            if (event.changes.firstOrNull()?.pressed != true) {
                                dragSelectedPage = null
                                break
                            }

                            val selectedPage = (position.x / stepPx).roundToInt().coerceIn(0, pageCount - 1)
                            if (selectedPage != dragSelectedPage) {
                                dragSelectedPage = selectedPage
                                scope.launch {
                                    pagerState.scrollToPage(selectedPage)
                                }
                            }
                        }
                    }
                }
        ) {
            repeat(pageCount) {
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(inactiveColor)
                )
            }
        }
    }
}
