package top.msfxp.schedule.ui.schedule

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.MergedCourseBlock
import top.msfxp.schedule.ui.schedule.components.CourseDetailSheet
import top.msfxp.schedule.ui.schedule.components.ScheduleGrid
import top.msfxp.schedule.ui.schedule.components.WeekSelectorSheet
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// 周课表主界面
@Composable
fun WeeklyScheduleScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: WeeklyScheduleViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
    val updateRelease by viewModel.updateRelease.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    val today = remember { LocalDate.now() }
    val thisMonday = remember(today) {
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    // 周次分页状态
    val initialPage = remember(uiState.currentWeekNumber, uiState.totalWeeks) {
        (uiState.currentWeekNumber - 1).coerceIn(0, uiState.totalWeeks - 1)
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { uiState.totalWeeks }
    )

    // 自动定位至当前周次
    var hasAutoScrolledToCurrentWeek by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.isReady, uiState.currentWeekNumber) {
        if (uiState.isReady && !hasAutoScrolledToCurrentWeek) {
            val targetPage = (uiState.currentWeekNumber - 1).coerceIn(0, uiState.totalWeeks - 1)
            if (pagerState.currentPage != targetPage) {
                pagerState.scrollToPage(targetPage)
            }
            hasAutoScrolledToCurrentWeek = true
        }
    }

    // 当前浏览周次与星期字符串
    val currentBrowsingWeek = pagerState.currentPage + 1
    val isCurrentWeekBrowsing = currentBrowsingWeek == uiState.currentWeekNumber

    val dayOfWeekChinese = mapOf(
        DayOfWeek.MONDAY to stringResource(R.string.day_monday),
        DayOfWeek.TUESDAY to stringResource(R.string.day_tuesday),
        DayOfWeek.WEDNESDAY to stringResource(R.string.day_wednesday),
        DayOfWeek.THURSDAY to stringResource(R.string.day_thursday),
        DayOfWeek.FRIDAY to stringResource(R.string.day_friday),
        DayOfWeek.SATURDAY to stringResource(R.string.day_saturday),
        DayOfWeek.SUNDAY to stringResource(R.string.day_sunday)
    )
    val currentDayOfWeekStr = dayOfWeekChinese[today.dayOfWeek] ?: stringResource(R.string.day_monday)

    // 监听同步提示反馈
    LaunchedEffect(uiState.syncMessage) {
        uiState.syncMessage?.let { msg ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = msg,
                    duration = SnackbarDuration.Short
                )
            }
            viewModel.clearSyncMessage()
        }
    }

    var showWeekSelector by remember { mutableStateOf(false) }
    var selectedBlockForDetail by remember { mutableStateOf<MergedCourseBlock?>(null) }
    val adjustmentsList by viewModel.adjustmentsListFlow.collectAsState()

    val wallpaperBitmap by viewModel.wallpaperBitmapFlow.collectAsState()
    val wallpaperAlpha by animateFloatAsState(
        targetValue = if (wallpaperBitmap != null && uiState.hasCustomWallpaper) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "wallpaper_fade"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // 背景壁纸与暗度遮罩
            val currentWallpaper = wallpaperBitmap
            if (currentWallpaper != null && wallpaperAlpha > 0f) {
                Image(
                    bitmap = currentWallpaper,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = wallpaperAlpha },
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = uiState.wallpaperMaskDim * wallpaperAlpha))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
            // 顶部控制栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 周次与日期选择
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showWeekSelector = true
                        }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(
                            R.string.schedule_header_date_format,
                            today.year,
                            today.monthValue,
                            today.dayOfMonth
                        ),
                        style = MiuixTheme.textStyles.title2.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            letterSpacing = 0.3.sp
                        ),
                        color = MiuixTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    val browsingWeekStr = stringResource(R.string.schedule_title_week_format, currentBrowsingWeek)
                    val currentWeekStr = stringResource(R.string.schedule_title_week_format, uiState.currentWeekNumber)
                    val subtitleText = when {
                        uiState.isVacation -> {
                            stringResource(R.string.schedule_subtitle_vacation, browsingWeekStr)
                        }
                        isCurrentWeekBrowsing -> {
                            stringResource(R.string.schedule_subtitle_current_week, browsingWeekStr, currentDayOfWeekStr)
                        }
                        else -> {
                            stringResource(R.string.schedule_subtitle_browsing_week, browsingWeekStr, currentWeekStr)
                        }
                    }

                    Text(
                        text = subtitleText,
                        style = MiuixTheme.textStyles.footnote1.copy(fontSize = 13.sp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }

                // 操作按钮区
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 同步导入按钮
                    IconButton(
                        enabled = !uiState.isSyncing,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onImportOrSyncClicked(onNeedLogin = onNavigateToLogin)
                        }
                    ) {
                        if (uiState.isSyncing) {
                            InfiniteProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MiuixTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = MiuixIcons.Download,
                                contentDescription = stringResource(
                                    if (uiState.isLoggedIn) R.string.schedule_action_sync else R.string.schedule_action_import
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // 设置按钮
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigateToSettings()
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Settings,
                            contentDescription = stringResource(R.string.schedule_action_settings),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 课表分页主体
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                HorizontalPager(
                    state = pagerState,
                    key = { pageIndex -> pageIndex },
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val pageWeek = pageIndex + 1
                    val pageStartDate = uiState.semesterStartDate
                    val pageMonday = if (pageStartDate != null) {
                        pageStartDate.plusWeeks(pageIndex.toLong())
                    } else {
                        thisMonday
                    }
                    val isCurrent = pageWeek == uiState.currentWeekNumber

                    val pageBlocks = uiState.weekBlocksMap[pageWeek] ?: emptyList()
                    val pageUnarranged = uiState.weekUnarrangedMap[pageWeek] ?: emptyList()

                    ScheduleGrid(
                        pageMondayDate = pageMonday,
                        weekNumber = pageWeek,
                        timeSlots = uiState.timeSlots,
                        mergedBlocks = pageBlocks,
                        unarrangedCourses = pageUnarranged,
                        isCurrentWeek = isCurrent,
                        courseColors = uiState.courseColors,
                        cardAlpha = uiState.courseCardAlpha,
                        onCourseBlockClick = { block ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedBlockForDetail = block
                        },
                        onUnarrangedCourseClick = { ev ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val virtualBlock = MergedCourseBlock(
                                day = 0,
                                startSection = 0,
                                endSection = 0,
                                events = listOf(ev)
                            )
                            selectedBlockForDetail = virtualBlock
                        }
                    )
                }
            }
        }
        }
    }

    // 周次选择抽屉
    WeekSelectorSheet(
        show = showWeekSelector,
        totalWeeks = uiState.totalWeeks,
        currentWeekNumber = uiState.currentWeekNumber,
        selectedWeek = currentBrowsingWeek,
        weekCourseCountMap = uiState.weekCourseCountMap,
        onWeekSelected = { week ->
            showWeekSelector = false
            scope.launch {
                pagerState.animateScrollToPage((week - 1).coerceIn(0, uiState.totalWeeks - 1))
            }
        },
        onDismissRequest = { showWeekSelector = false }
    )

    // 课程详情抽屉
    CourseDetailSheet(
        block = selectedBlockForDetail,
        timeSlots = uiState.timeSlots,
        currentWeekNumber = currentBrowsingWeek,
        totalWeeks = uiState.totalWeeks,
        currentSemesterId = uiState.currentSemesterId,
        courseColors = uiState.courseColors,
        adjustmentsList = adjustmentsList,
        onColorIndexSelected = { courseId, newIndex ->
            viewModel.updateCourseColor(courseId, newIndex)
            selectedBlockForDetail = selectedBlockForDetail?.let { curBlock ->
                curBlock.copy(
                    events = curBlock.events.map { ev ->
                        if (ev.courseId == courseId) {
                            ev.copy(colorIndex = newIndex).apply { this.weeks = ev.weeks }
                        } else ev
                    }
                )
            }
        },
        onMoveCourse = { adj ->
            viewModel.moveCourse(adj)
            selectedBlockForDetail = null
        },
        onSwapCourses = { adjA, adjB ->
            viewModel.swapCourses(adjA, adjB)
            selectedBlockForDetail = null
        },
        onMarkDeleted = { adj ->
            viewModel.markCourseDeleted(adj)
            selectedBlockForDetail = null
        },
        onDeleteAdjustment = { adjId ->
            viewModel.deleteAdjustment(adjId)
            selectedBlockForDetail = null
        },
        onClearAllAdjustments = {
            viewModel.clearAllAdjustments()
        },
        onGetAdjustmentForEvent = { cId, w, d, s ->
            viewModel.getAdjustmentForEvent(cId, w, d, s)
        },
        getCourseAtSlot = { w, d, s, e ->
            val currentEv = selectedBlockForDetail?.events?.firstOrNull()
            if (currentEv != null) {
                viewModel.getCourseAtSlot(
                    week = w,
                    day = d,
                    startSection = s,
                    endSection = e,
                    excludeCourseId = currentEv.courseId,
                    excludeStartSection = currentEv.startSection
                )
            } else null
        },
        onDismissRequest = { selectedBlockForDetail = null }
    )

    // 发现新版本主动弹窗 (24小时静默检查发现新版本时触发)
    if (showUpdateDialog && updateRelease != null) {
        val release = updateRelease!!
        WindowDialog(
            show = true,
            title = stringResource(R.string.about_update_has_new, release.tagName),
            onDismissRequest = { viewModel.dismissUpdateDialog() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                if (!release.body.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = release.body.trim(),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.about_update_dialog_btn_later),
                        onClick = { viewModel.dismissUpdateDialog() },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            viewModel.executeDownload(context) { message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.about_update_dialog_btn_download))
                    }
                }
            }
        }
    }
}
