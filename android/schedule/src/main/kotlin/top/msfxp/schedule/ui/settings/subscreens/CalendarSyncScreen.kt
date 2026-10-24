package top.msfxp.schedule.ui.settings.subscreens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.msfxp.schedule.R
import top.msfxp.schedule.ui.settings.SettingsViewModel
import top.msfxp.schedule.util.PermissionHelper
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private val CalendarRemindOptions = listOf(5, 10, 15, 20, 30, 45, 60)

// 日历同步设置界面
@Composable
fun CalendarSyncScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.appSettings
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // 生命周期返回时刷新权限状态
    var refreshTrigger by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isCalendarPermissionGranted = remember(refreshTrigger) {
        PermissionHelper.isCalendarGranted(context)
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        refreshTrigger++
        if (granted) {
            pendingPermissionAction?.invoke()
        } else {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.calendar_sync_permission_needed_toast)) }
        }
        pendingPermissionAction = null
    }

    // 校验权限并执行对应操作
    val runWithPermission: (() -> Unit) -> Unit = { action ->
        if (isCalendarPermissionGranted) {
            action()
        } else {
            pendingPermissionAction = action
            calendarPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            )
        }
    }

    val remindMinutesLabels = CalendarRemindOptions.map {
        stringResource(R.string.calendar_sync_remind_format, it)
    }

    val selectedMinutesIndex = remember(settings.calendarRemindBeforeMinutes) {
        val idx = CalendarRemindOptions.indexOf(settings.calendarRemindBeforeMinutes)
        if (idx >= 0) idx else 2
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.calendar_sync_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.ChevronBackward,
                            contentDescription = stringResource(R.string.common_back),
                            modifier = Modifier.size(24.dp)
                        )
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
            // 同步操作与偏好设置
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SwitchPreference(
                            title = stringResource(R.string.calendar_sync_item_auto),
                            summary = stringResource(R.string.calendar_sync_desc_auto),
                            checked = settings.autoSyncToCalendar,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    runWithPermission {
                                        viewModel.updateAutoSyncToCalendar(true)
                                    }
                                } else {
                                    viewModel.updateAutoSyncToCalendar(false)
                                }
                            }
                        )

                        OverlayDropdownPreference(
                            title = stringResource(R.string.calendar_sync_item_remind),
                            items = remindMinutesLabels,
                            selectedIndex = selectedMinutesIndex,
                            onSelectedIndexChange = { index ->
                                val minutes = CalendarRemindOptions.getOrElse(index) { 15 }
                                viewModel.updateCalendarRemindBeforeMinutes(minutes)
                            }
                        )

                        ArrowPreference(
                            title = stringResource(R.string.calendar_sync_item_now),
                            summary = stringResource(R.string.calendar_sync_desc_now),
                            onClick = {
                                runWithPermission {
                                    viewModel.syncToCalendarManually { success ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(
                                                    if (success) R.string.calendar_sync_success_toast
                                                    else R.string.calendar_sync_fail_toast
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        )

                        ArrowPreference(
                            title = stringResource(R.string.calendar_delete_item_title),
                            summary = stringResource(R.string.calendar_delete_desc),
                            onClick = {
                                runWithPermission {
                                    showDeleteConfirmDialog = true
                                }
                            }
                        )
                    }
                }
            }

            // 账户说明
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = MiuixIcons.Info,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .size(14.dp)
                            .offset(y = 1.5.dp)
                    )
                    Text(
                        text = stringResource(R.string.calendar_sync_account_hint),
                        style = MiuixTheme.textStyles.footnote2.copy(lineHeight = 18.sp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 删除确认弹窗
        if (showDeleteConfirmDialog) {
            WindowDialog(
                show = true,
                title = stringResource(R.string.calendar_delete_dialog_title),
                summary = stringResource(R.string.calendar_delete_dialog_message),
                onDismissRequest = { showDeleteConfirmDialog = false }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.common_cancel),
                        onClick = { showDeleteConfirmDialog = false },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            viewModel.deleteCalendarSchedule { success ->
                                if (success && settings.autoSyncToCalendar) {
                                    viewModel.updateAutoSyncToCalendar(false)
                                }
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            if (success) R.string.calendar_delete_success_toast
                                            else R.string.calendar_delete_fail_toast
                                        )
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.calendar_delete_dialog_confirm),
                            color = MiuixTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}


