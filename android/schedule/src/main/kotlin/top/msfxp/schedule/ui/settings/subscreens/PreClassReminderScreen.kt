package top.msfxp.schedule.ui.settings.subscreens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val RemindMinutesOptions = listOf(5, 10, 15, 20, 30, 45, 60)

// 课前通知提醒设置界面
@Composable
fun PreClassReminderScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.appSettings
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 生命周期返回时刷新权限状态并处理待确认开启
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var pendingEnableOnResume by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
                if (pendingEnableOnResume) {
                    if (PermissionHelper.isNotificationGranted(context)) {
                        viewModel.updateReminderEnabled(true)
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.reminder_permission_notice_toast))
                        }
                    }
                    pendingEnableOnResume = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isNotificationEnabled = remember(refreshTrigger) {
        PermissionHelper.isNotificationGranted(context)
    }

    val isExactAlarmEnabled = remember(refreshTrigger) {
        PermissionHelper.isExactAlarmGranted(context)
    }

    val postNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshTrigger++
        if (granted) {
            viewModel.updateReminderEnabled(true)
        } else {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.reminder_permission_notice_toast)) }
        }
    }

    val remindMinutesLabels = RemindMinutesOptions.map {
        stringResource(R.string.calendar_sync_remind_format, it)
    }

    val currentMinutesIndex = remember(settings.remindBeforeMinutes) {
        val idx = RemindMinutesOptions.indexOf(settings.remindBeforeMinutes)
        if (idx >= 0) idx else 2
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.reminder_title),
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
            // 提醒开关与提前时间
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SwitchPreference(
                            title = stringResource(R.string.reminder_switch_title),
                            summary = stringResource(R.string.reminder_switch_desc),
                            checked = settings.reminderEnabled,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    if (isNotificationEnabled) {
                                        viewModel.updateReminderEnabled(true)
                                    } else {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            pendingEnableOnResume = true
                                            PermissionHelper.openNotificationSettings(context)
                                        }
                                    }
                                } else {
                                    pendingEnableOnResume = false
                                    viewModel.updateReminderEnabled(false)
                                }
                            }
                        )

                        if (settings.reminderEnabled) {
                            OverlayDropdownPreference(
                                title = stringResource(R.string.reminder_time_title),
                                items = remindMinutesLabels,
                                selectedIndex = currentMinutesIndex,
                                onSelectedIndexChange = { index ->
                                    val minutes = RemindMinutesOptions.getOrElse(index) { 15 }
                                    viewModel.updateRemindBeforeMinutes(minutes)
                                }
                            )
                        }
                    }
                }
            }

            // 模拟测试卡片
            if (settings.showTestFeatures) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = stringResource(R.string.reminder_test_immediate_title),
                                summary = stringResource(R.string.reminder_test_immediate_desc),
                                onClick = {
                                    if (!isNotificationEnabled) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            PermissionHelper.openNotificationSettings(context)
                                        }
                                    } else {
                                        viewModel.sendTestNotification(context)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.reminder_test_sent_toast))
                                        }
                                    }
                                }
                            )

                            ArrowPreference(
                                title = stringResource(R.string.reminder_test_delayed_title),
                                summary = stringResource(R.string.reminder_test_delayed_desc),
                                onClick = {
                                    if (!isNotificationEnabled) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            PermissionHelper.openNotificationSettings(context)
                                        }
                                    } else if (!isExactAlarmEnabled) {
                                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.reminder_test_alarm_permission_toast)) }
                                        PermissionHelper.openExactAlarmSettings(context)
                                    } else {
                                        viewModel.scheduleDelayed60sTestNotification(context)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.reminder_test_delayed_scheduled_toast))
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }


            // 说明文本
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
                        text = stringResource(R.string.reminder_hint_text),
                        style = MiuixTheme.textStyles.footnote2.copy(lineHeight = 18.sp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
