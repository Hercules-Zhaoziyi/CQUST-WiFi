package top.msfxp.schedule.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.effectiveStartDate
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// 设置主界面
@Composable
fun SettingsScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToOverview: () -> Unit,
    onNavigateToPreClassReminder: () -> Unit,
    onNavigateToCalendarSync: () -> Unit,
    onNavigateToClassAutomation: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToPersonalization: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showSetDateDialog by remember { mutableStateOf(false) }
    var showSemesterPickerSheet by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.syncMessage) {
        uiState.syncMessage?.let { msg ->
            scope.launch { snackbarHostState.showSnackbar(msg) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.ChevronBackward,
                                contentDescription = stringResource(R.string.common_back),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 用户信息卡片
            item {
                UserProfileCard(
                    settings = uiState.appSettings,
                    onNavigateToLogin = onNavigateToLogin,
                    onLogoutClick = { showLogoutDialog = true }
                )
            }

            // 统计与学期卡片
            item {
                OverviewAndSemesterCard(
                    courseCount = uiState.courseCount,
                    currentSemester = uiState.currentSemester,
                    onNavigateToOverview = onNavigateToOverview,
                    onSemesterClick = {
                        if (uiState.availableSemesters.isNotEmpty()) {
                            showSemesterPickerSheet = true
                        } else if (!uiState.appSettings.isLoggedIn) {
                            onNavigateToLogin()
                        }
                    }
                )
            }

            // 课表常规设置
            item {
                GeneralSettingsSection(
                    startDate = uiState.currentSemester.effectiveStartDate.toString(),
                    onStartDateClick = { showSetDateDialog = true },
                    onPersonalizationClick = onNavigateToPersonalization
                )
            }

            // 通知与自动化
            item {
                AutomationSettingsSection(
                    onPreClassReminderClick = onNavigateToPreClassReminder,
                    onCalendarSyncClick = onNavigateToCalendarSync,
                    onClassAutomationClick = onNavigateToClassAutomation,
                    onPermissionsClick = onNavigateToPermissions
                )
            }

            // 关于
            item {
                AboutSection(onAboutClick = onNavigateToAbout)
            }
        }

        // 退出登录确认弹窗
        if (showLogoutDialog) {
            LogoutConfirmDialog(
                onConfirm = {
                    showLogoutDialog = false
                    viewModel.logout(onNavigateToLogin)
                },
                onDismiss = { showLogoutDialog = false }
            )
        }

        // 学期选择抽屉
        if (showSemesterPickerSheet && uiState.availableSemesters.isNotEmpty()) {
            SemesterPickerBottomSheet(
                uiState = uiState,
                onSelectSemester = { semId ->
                    viewModel.switchSemester(
                        semesterId = semId,
                        onNeedLogin = onNavigateToLogin,
                        onSuccess = { showSemesterPickerSheet = false }
                    )
                },
                onDismiss = {
                    if (uiState.switchingSemesterId == null) {
                        showSemesterPickerSheet = false
                    }
                }
            )
        }

        // 行课起始日期设置抽屉
        if (showSetDateDialog) {
            StartDatePickerDialog(
                initialDate = uiState.currentSemester.effectiveStartDate,
                onConfirm = { mondayDate ->
                    val formatted = mondayDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    viewModel.updateSemesterStartDate(formatted)
                    showSetDateDialog = false
                },
                onDismiss = { showSetDateDialog = false }
            )
        }
    }
}

// 用户信息展示卡片
@Composable
private fun UserProfileCard(
    settings: top.msfxp.schedule.data.model.AppSettings,
    onNavigateToLogin: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                if (!settings.isLoggedIn || settings.studentId.isBlank()) {
                    onNavigateToLogin()
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 头像徽章
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (settings.isLoggedIn && settings.studentName.isNotBlank()) {
                        settings.studentName.take(1)
                    } else if (settings.isLoggedIn && settings.studentId.isNotBlank()) {
                        settings.studentId.takeLast(2)
                    } else {
                        stringResource(R.string.settings_avatar_default)
                    },
                    style = MiuixTheme.textStyles.title2.copy(
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onPrimary
                    )
                )
            }

            // 用户与学籍信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (settings.isLoggedIn && settings.studentId.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = settings.studentName.ifBlank { settings.studentId },
                            style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Bold),
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        if (settings.studentName.isNotBlank()) {
                            Text(
                                text = "(${settings.studentId})",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                    if (settings.major.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = settings.major,
                                style = MiuixTheme.textStyles.footnote2.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.school_name),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.settings_login_prompt),
                        style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Bold),
                        color = MiuixTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.settings_login_sub_prompt),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            if (settings.isLoggedIn && settings.studentId.isNotBlank()) {
                IconButton(onClick = onLogoutClick) {
                    Icon(
                        imageVector = MiuixIcons.Import,
                        contentDescription = stringResource(R.string.settings_dialog_logout_title),
                        modifier = Modifier.size(24.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }
}

// 课程总览与学期卡片
@Composable
private fun OverviewAndSemesterCard(
    courseCount: Int,
    currentSemester: top.msfxp.schedule.data.model.Semester?,
    onNavigateToOverview: () -> Unit,
    onSemesterClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 已同步课程总览
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onNavigateToOverview() }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.height(26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$courseCount",
                        style = MiuixTheme.textStyles.title2.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_overview_entry),
                    style = MiuixTheme.textStyles.footnote2.copy(fontSize = 11.sp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }

            // 垂直分割线
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.2f))
            )

            // 当前学期信息
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onSemesterClick)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val semesterShortTitle = when {
                    currentSemester != null && currentSemester.name.isNotBlank() && currentSemester.name.length <= 4 ->
                        "${currentSemester.schoolYear}-${currentSemester.name}"
                    currentSemester != null && currentSemester.schoolYear.isNotBlank() -> currentSemester.schoolYear
                    currentSemester != null -> currentSemester.label
                    else -> "-"
                }

                Box(
                    modifier = Modifier.height(26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = semesterShortTitle,
                        style = MiuixTheme.textStyles.title2.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_current_semester_entry),
                    style = MiuixTheme.textStyles.footnote2.copy(fontSize = 11.sp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

// 课表常规设置分组
@Composable
private fun GeneralSettingsSection(
    startDate: String,
    onStartDateClick: () -> Unit,
    onPersonalizationClick: () -> Unit
) {
    SmallTitle(text = stringResource(R.string.settings_section_general))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = stringResource(R.string.settings_item_start_date),
                summary = "$startDate ${stringResource(R.string.common_monday_format)}",
                onClick = onStartDateClick
            )

            ArrowPreference(
                title = stringResource(R.string.settings_item_personalization),
                summary = stringResource(R.string.settings_desc_personalization),
                onClick = onPersonalizationClick
            )
        }
    }
}

// 通知与自动化设置分组
@Composable
private fun AutomationSettingsSection(
    onPreClassReminderClick: () -> Unit,
    onCalendarSyncClick: () -> Unit,
    onClassAutomationClick: () -> Unit,
    onPermissionsClick: () -> Unit
) {
    SmallTitle(text = stringResource(R.string.settings_section_notifications))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = stringResource(R.string.settings_item_pre_class_reminder),
                summary = stringResource(R.string.settings_desc_pre_class_reminder),
                onClick = onPreClassReminderClick
            )

            ArrowPreference(
                title = stringResource(R.string.settings_item_calendar_sync),
                summary = stringResource(R.string.settings_desc_calendar_sync),
                onClick = onCalendarSyncClick
            )

            ArrowPreference(
                title = stringResource(R.string.settings_item_class_automation),
                summary = stringResource(R.string.settings_desc_class_automation),
                onClick = onClassAutomationClick
            )

            ArrowPreference(
                title = stringResource(R.string.settings_item_permission_management),
                summary = stringResource(R.string.settings_desc_permission_management),
                onClick = onPermissionsClick
            )
        }
    }
}

// 关于应用设置分组
@Composable
private fun AboutSection(onAboutClick: () -> Unit) {
    SmallTitle(text = stringResource(R.string.settings_section_about))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = stringResource(R.string.settings_item_about),
                summary = stringResource(R.string.settings_desc_about),
                onClick = onAboutClick
            )
        }
    }
}

// 退出登录确认弹窗
@Composable
private fun LogoutConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    WindowDialog(
        show = true,
        title = stringResource(R.string.settings_dialog_logout_title),
        summary = stringResource(R.string.settings_dialog_logout_message),
        onDismissRequest = onDismiss
    ) {
        val dismiss = LocalDismissState.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(
                text = stringResource(R.string.common_cancel),
                onClick = {
                    dismiss?.invoke()
                    onDismiss()
                },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    dismiss?.invoke()
                    onConfirm()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.settings_dialog_logout_title))
            }
        }
    }
}

// 学期选择抽屉
@Composable
private fun SemesterPickerBottomSheet(
    uiState: SettingsUiState,
    onSelectSemester: (String) -> Unit,
    onDismiss: () -> Unit
) {
    WindowBottomSheet(
        show = true,
        title = stringResource(R.string.settings_sheet_switch_semester),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.availableSemesters.forEach { sem ->
                val isSelected = sem.id == uiState.currentSemester?.id
                val isSwitchingThis = sem.id == uiState.switchingSemesterId
                val displayName = if (sem.schoolYear.isNotBlank() && sem.name.isNotBlank()) {
                    stringResource(R.string.settings_semester_display_format, sem.schoolYear, sem.name)
                } else {
                    sem.label.ifBlank { sem.id }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MiuixTheme.colorScheme.surfaceVariant
                        )
                        .clickable(enabled = uiState.switchingSemesterId == null) {
                            if (isSelected) {
                                onDismiss()
                            } else {
                                onSelectSemester(sem.id)
                            }
                        }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = displayName,
                                style = MiuixTheme.textStyles.body1.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_semester_code_format, sem.id),
                                style = MiuixTheme.textStyles.footnote2,
                                color = if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.8f) else MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        if (isSwitchingThis) {
                            Spacer(modifier = Modifier.width(8.dp))
                            InfiniteProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// 行课起始日期设置抽屉
@Composable
private fun StartDatePickerDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedYear by remember(initialDate) { mutableIntStateOf(initialDate.year) }
    var selectedMonth by remember(initialDate) { mutableIntStateOf(initialDate.monthValue) }
    var selectedDay by remember(initialDate) { mutableIntStateOf(initialDate.dayOfMonth) }

    val maxDaysInMonth = remember(selectedYear, selectedMonth) {
        try {
            LocalDate.of(selectedYear, selectedMonth, 1).lengthOfMonth()
        } catch (_: Exception) {
            31
        }
    }

    LaunchedEffect(maxDaysInMonth) {
        if (selectedDay > maxDaysInMonth) {
            selectedDay = maxDaysInMonth
        }
    }

    val pickedDate = remember(selectedYear, selectedMonth, selectedDay) {
        try {
            LocalDate.of(selectedYear, selectedMonth, selectedDay)
        } catch (_: Exception) {
            null
        }
    }

    val calculatedMonday = remember(pickedDate) {
        pickedDate?.with(DayOfWeek.MONDAY)
    }

    WindowBottomSheet(
        show = true,
        title = stringResource(R.string.settings_dialog_set_start_date),
        onDismissRequest = onDismiss,
        startAction = {
            val dismiss = LocalDismissState.current
            IconButton(
                onClick = {
                    dismiss?.invoke()
                    onDismiss()
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
                    if (calculatedMonday != null) {
                        onConfirm(calculatedMonday)
                    }
                    dismiss?.invoke()
                    onDismiss()
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 年月日滚轮选择器
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(vertical = 10.dp, horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 年
                    Row(
                        modifier = Modifier.weight(1.25f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        NumberPicker(
                            value = selectedYear,
                            onValueChange = { selectedYear = it },
                            range = 2020..2035,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.common_year_suffix),
                            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 2.dp, end = 4.dp)
                        )
                    }

                    // 月
                    Row(
                        modifier = Modifier.weight(1.0f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        NumberPicker(
                            value = selectedMonth,
                            onValueChange = { selectedMonth = it },
                            range = 1..12,
                            wrapAround = true,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.common_month_suffix),
                            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 2.dp, end = 4.dp)
                        )
                    }

                    // 日
                    Row(
                        modifier = Modifier.weight(1.0f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        NumberPicker(
                            value = selectedDay,
                            onValueChange = { selectedDay = it },
                            range = 1..maxDaysInMonth,
                            wrapAround = true,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.common_day_suffix),
                            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            }

            // 自动校准周一提示
            if (calculatedMonday != null) {
                val formattedMonday = calculatedMonday.format(DateTimeFormatter.ISO_LOCAL_DATE)
                Text(
                    text = stringResource(R.string.settings_auto_calibrate_monday_hint, formattedMonday),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
