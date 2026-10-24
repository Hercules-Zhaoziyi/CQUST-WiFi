package top.msfxp.schedule.ui.schedule.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.CourseColor
import top.msfxp.schedule.data.model.CourseEventWithMeta
import top.msfxp.schedule.data.model.DefaultCourseColors
import top.msfxp.schedule.data.model.DefaultTimeSlots
import top.msfxp.schedule.data.model.MergedCourseBlock
import top.msfxp.schedule.data.model.TimeSlot
import top.msfxp.schedule.data.model.getCourseColor
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import java.time.LocalTime

private val SectionHeight: Dp = 62.dp
private val TimeColumnWidth: Dp = 39.dp
private val CardCornerRadius: Dp = 7.dp

// 课表核心栅格视图
@Composable
fun ScheduleGrid(
    pageMondayDate: LocalDate,
    weekNumber: Int = 1,
    timeSlots: List<TimeSlot> = DefaultTimeSlots,
    mergedBlocks: List<MergedCourseBlock>,
    unarrangedCourses: List<CourseEventWithMeta>,
    isCurrentWeek: Boolean,
    onCourseBlockClick: (MergedCourseBlock) -> Unit,
    onUnarrangedCourseClick: (CourseEventWithMeta) -> Unit,
    courseColors: List<CourseColor> = DefaultCourseColors,
    cardAlpha: Float = 1f,
    modifier: Modifier = Modifier
) {
    val totalDays = 7
    val today = remember { LocalDate.now() }
    val currentTime = remember { LocalTime.now() }

    val dayShortHeaders = listOf(
        stringResource(R.string.day_mon_short),
        stringResource(R.string.day_tue_short),
        stringResource(R.string.day_wed_short),
        stringResource(R.string.day_thu_short),
        stringResource(R.string.day_fri_short),
        stringResource(R.string.day_sat_short),
        stringResource(R.string.day_sun_short)
    )

    val effectiveSlots = remember(timeSlots) {
        if (timeSlots.isNotEmpty()) timeSlots else DefaultTimeSlots
    }

    // 计算当前进行中或即将开始的节次
    val currentSectionNumber = remember(effectiveSlots, currentTime) {
        if (effectiveSlots.isEmpty()) return@remember -1

        val inClassSlot = effectiveSlots.firstOrNull { slot ->
            try {
                val start = LocalTime.parse(slot.startTime)
                val end = LocalTime.parse(slot.endTime)
                currentTime in start..end
            } catch (_: Exception) { false }
        }
        if (inClassSlot != null) return@remember inClassSlot.sectionNumber

        val upcomingSlot = effectiveSlots.firstOrNull { slot ->
            try {
                val start = LocalTime.parse(slot.startTime)
                currentTime < start && java.time.Duration.between(currentTime, start).toMinutes() <= 25
            } catch (_: Exception) { false }
        }
        upcomingSlot?.sectionNumber ?: -1
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部月份与星期日期表头
        ScheduleGridHeader(
            pageMondayDate = pageMondayDate,
            today = today,
            dayShortHeaders = dayShortHeaders,
            totalDays = totalDays
        )

        // 课表矩阵滚动主体
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // 左侧节次时间轴
                    ScheduleTimeColumn(
                        effectiveSlots = effectiveSlots,
                        currentSectionNumber = currentSectionNumber,
                        isCurrentWeek = isCurrentWeek
                    )

                    // 右侧课程网格区域
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(SectionHeight * 11)
                    ) {
                        // 背景网格分割线
                        val dividerColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val sectionHeightPx = SectionHeight.toPx()
                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), 0f)

                            for (i in 1..10) {
                                val y = sectionHeightPx * i
                                drawLine(dividerColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dashEffect)
                            }

                            val dayColumnWidthPx = size.width / totalDays
                            for (day in 1 until totalDays) {
                                val x = dayColumnWidthPx * day
                                drawLine(dividerColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx(), pathEffect = dashEffect)
                            }
                        }

                        // 渲染各天课程合并块
                        val blocksByDay = remember(mergedBlocks) { mergedBlocks.groupBy { it.day } }
                        Row(modifier = Modifier.fillMaxSize()) {
                            for (dayIndex in 1..totalDays) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(horizontal = 2.dp)
                                ) {
                                    val dayBlocks = blocksByDay[dayIndex] ?: emptyList()
                                    dayBlocks.forEach { block ->
                                        ScheduleCourseBlockItem(
                                            block = block,
                                            courseColors = courseColors,
                                            cardAlpha = cardAlpha,
                                            onClick = { onCourseBlockClick(block) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 未排课或集中实践课程专区
                if (unarrangedCourses.isNotEmpty()) {
                    ScheduleUnarrangedCourseSection(
                        unarrangedCourses = unarrangedCourses,
                        courseColors = courseColors,
                        cardAlpha = cardAlpha,
                        onUnarrangedCourseClick = onUnarrangedCourseClick
                    )
                }
            }
        }
    }
}

// 顶部月份与星期表头
@Composable
private fun ScheduleGridHeader(
    pageMondayDate: LocalDate,
    today: LocalDate,
    dayShortHeaders: List<String>,
    totalDays: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .width(TimeColumnWidth)
                .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${pageMondayDate.monthValue}",
                style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.common_month_suffix),
                style = MiuixTheme.textStyles.footnote2.copy(fontSize = 10.sp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }

        for (i in 0 until totalDays) {
            val date = pageMondayDate.plusDays(i.toLong())
            val isToday = date == today

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isToday) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = dayShortHeaders[i],
                    style = MiuixTheme.textStyles.footnote2.copy(
                        fontSize = 12.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isToday) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.schedule_date_short_format, date.monthValue, date.dayOfMonth),
                    style = MiuixTheme.textStyles.footnote2.copy(
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 10.5.sp
                    ),
                    color = if (isToday) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

// 左侧节次时间轴列
@Composable
private fun ScheduleTimeColumn(
    effectiveSlots: List<TimeSlot>,
    currentSectionNumber: Int,
    isCurrentWeek: Boolean
) {
    Column(
        modifier = Modifier.width(TimeColumnWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (section in 1..11) {
            val slot = effectiveSlots.find { it.sectionNumber == section }
            val isCurrentSection = section == currentSectionNumber && isCurrentWeek

            Box(
                modifier = Modifier
                    .height(SectionHeight)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(CardCornerRadius))
                        .background(if (isCurrentSection) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                ) {
                    Text(
                        text = section.toString(),
                        style = MiuixTheme.textStyles.body2.copy(
                            fontWeight = if (isCurrentSection) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        ),
                        color = if (isCurrentSection) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                    )
                    if (slot != null) {
                        Text(
                            text = slot.startTime,
                            style = MiuixTheme.textStyles.footnote2.copy(fontSize = 9.sp),
                            color = if (isCurrentSection) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Text(
                            text = slot.endTime,
                            style = MiuixTheme.textStyles.footnote2.copy(fontSize = 8.5.sp),
                            color = if (isCurrentSection) MiuixTheme.colorScheme.primary.copy(alpha = 0.85f)
                            else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }
    }
}

// 单门课程网格卡片
@Composable
private fun ScheduleCourseBlockItem(
    block: MergedCourseBlock,
    courseColors: List<CourseColor>,
    cardAlpha: Float,
    onClick: () -> Unit
) {
    val cardVerticalMargin = 2.dp
    val topOffset = SectionHeight * (block.startSection - 1) + cardVerticalMargin
    val blockHeight = SectionHeight * (block.endSection - block.startSection + 1) - (cardVerticalMargin * 2)
    val primary = block.primaryEvent
    val courseColor = courseColors.getCourseColor(primary.colorIndex).light.copy(alpha = cardAlpha)

    Box(
        modifier = Modifier
            .offset(y = topOffset)
            .fillMaxWidth()
            .height(blockHeight)
            .clip(RoundedCornerShape(CardCornerRadius))
            .background(courseColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = primary.courseName,
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 13.5.sp),
                    color = Color.White,
                    maxLines = if (primary.isPractical && primary.projectName.isNotBlank()) 2 else (if (block.endSection - block.startSection >= 1) 3 else 2),
                    overflow = TextOverflow.Ellipsis
                )
                if (primary.isPractical && primary.projectName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(1.5.dp))
                    Text(
                        text = "(${primary.projectName})",
                        style = MiuixTheme.textStyles.footnote2.copy(fontSize = 9.sp, fontWeight = FontWeight.Normal, lineHeight = 11.sp),
                        color = Color.White.copy(alpha = 0.90f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                if (primary.location.isNotBlank()) {
                    Text(
                        text = primary.location,
                        style = MiuixTheme.textStyles.footnote2.copy(fontSize = 9.sp, lineHeight = 11.5.sp),
                        color = Color.White.copy(alpha = 0.95f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (primary.teacher.isNotBlank()) {
                    Text(
                        text = primary.teacher,
                        style = MiuixTheme.textStyles.footnote2.copy(fontSize = 8.5.sp, lineHeight = 10.5.sp),
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (block.hasMultiple) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.28f))
                    .padding(horizontal = 2.5.dp, vertical = 1.dp)
                    .align(Alignment.TopEnd)
            ) {
                Text(
                    text = "+${block.events.size}",
                    style = MiuixTheme.textStyles.footnote2.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}

// 未排课或集中实践展示条
@Composable
private fun ScheduleUnarrangedCourseSection(
    unarrangedCourses: List<CourseEventWithMeta>,
    courseColors: List<CourseColor>,
    cardAlpha: Float,
    onUnarrangedCourseClick: (CourseEventWithMeta) -> Unit
) {
    Spacer(modifier = Modifier.height(12.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.schedule_grid_unarranged_title),
            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )

        val stopParentScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    return available.copy(y = 0f)
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(stopParentScrollConnection)
        ) {
            items(unarrangedCourses) { ev ->
                val cardBg = courseColors.getCourseColor(ev.colorIndex).light.copy(alpha = cardAlpha)

                Card(
                    modifier = Modifier
                        .width(135.dp)
                        .height(56.dp)
                        .clickable { onUnarrangedCourseClick(ev) },
                    cornerRadius = CardCornerRadius,
                    insideMargin = PaddingValues(horizontal = 9.dp, vertical = 6.dp),
                    colors = CardDefaults.defaultColors(color = cardBg)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = ev.courseName,
                            style = MiuixTheme.textStyles.footnote1.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 13.5.sp),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.5.dp))
                        Text(
                            text = ev.teacher.ifBlank { stringResource(R.string.schedule_grid_unarranged_desc) },
                            style = MiuixTheme.textStyles.footnote2.copy(fontSize = 9.sp),
                            color = Color.White.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}
