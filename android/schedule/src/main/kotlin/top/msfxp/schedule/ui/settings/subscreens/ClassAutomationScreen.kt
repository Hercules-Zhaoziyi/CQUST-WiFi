package top.msfxp.schedule.ui.settings.subscreens

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.AutoControlMode
import top.msfxp.schedule.ui.settings.SettingsViewModel
import top.msfxp.schedule.util.PermissionHelper
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 课中自动化设置界面
@Composable
fun ClassAutomationScreen(
    onBack: () -> Unit,
    onNavigateToPermissions: () -> Unit,
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
                    if (PermissionHelper.isDndAccessGranted(context)) {
                        viewModel.updateAutoDndEnabled(true)
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.automation_test_dnd_permission_toast))
                        }
                    }
                    pendingEnableOnResume = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasDndPermission = remember(refreshTrigger) {
        PermissionHelper.isDndAccessGranted(context)
    }

    val isExactAlarmEnabled = remember(refreshTrigger) {
        PermissionHelper.isExactAlarmGranted(context)
    }

    val automationModes = listOf(
        AutoControlMode.SILENT to stringResource(R.string.automation_mode_silent),
        AutoControlMode.DND to stringResource(R.string.automation_mode_dnd)
    )

    val selectedModeIndex = remember(settings.autoControlMode) {
        if (settings.autoControlMode == AutoControlMode.DND) 1 else 0
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.automation_title),
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
            // 自动化开关与模式选择
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SwitchPreference(
                            title = stringResource(R.string.automation_switch_title),
                            summary = stringResource(R.string.automation_switch_desc),
                            checked = settings.autoDndEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (hasDndPermission) {
                                        viewModel.updateAutoDndEnabled(true)
                                    } else {
                                        pendingEnableOnResume = true
                                        PermissionHelper.openDndSettings(context)
                                    }
                                } else {
                                    pendingEnableOnResume = false
                                    viewModel.updateAutoDndEnabled(false)
                                }
                            }
                        )

                        if (settings.autoDndEnabled) {
                            OverlayDropdownPreference(
                                title = stringResource(R.string.automation_mode_title),
                                items = automationModes.map { it.second },
                                selectedIndex = selectedModeIndex,
                                onSelectedIndexChange = { index ->
                                    val mode = automationModes.getOrElse(index) { automationModes[0] }.first
                                    viewModel.updateAutoControlMode(mode)
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
                                title = stringResource(R.string.automation_test_immediate_title),
                                summary = stringResource(R.string.automation_test_immediate_desc),
                                onClick = {
                                    if (!hasDndPermission) {
                                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.automation_test_dnd_permission_toast)) }
                                        PermissionHelper.openDndSettings(context)
                                    } else {
                                        viewModel.testToggleModeImmediately(context)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.automation_test_immediate_toast))
                                        }
                                    }
                                }
                            )

                            ArrowPreference(
                                title = stringResource(R.string.automation_test_delayed_title),
                                summary = stringResource(R.string.automation_test_delayed_desc),
                                onClick = {
                                    if (!hasDndPermission) {
                                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.automation_test_dnd_permission_toast)) }
                                        PermissionHelper.openDndSettings(context)
                                    } else if (!isExactAlarmEnabled) {
                                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.automation_test_alarm_permission_toast)) }
                                        PermissionHelper.openExactAlarmSettings(context)
                                    } else {
                                        viewModel.scheduleDelayed30sDndTest(context)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.automation_test_delayed_scheduled_toast))
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
                        text = stringResource(R.string.automation_hint_text),
                        style = MiuixTheme.textStyles.footnote2.copy(lineHeight = 18.sp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
