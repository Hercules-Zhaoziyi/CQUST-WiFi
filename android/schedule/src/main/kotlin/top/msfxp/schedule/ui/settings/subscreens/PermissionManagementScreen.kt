package top.msfxp.schedule.ui.settings.subscreens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.view.ViewTreeObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import top.msfxp.schedule.R
import top.msfxp.schedule.util.PermissionHelper
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 系统权限管理界面
@Composable
fun PermissionManagementScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current

    var resumeTrigger by remember { mutableIntStateOf(0) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // 监听生命周期与窗口焦点
    DisposableEffect(lifecycleOwner, view) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTrigger++
            }
        }
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                resumeTrigger++
            }
        }

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
        }
    }

    // 分段延时刷新权限状态
    LaunchedEffect(resumeTrigger) {
        if (resumeTrigger == 0) return@LaunchedEffect
        refreshTrigger++
        delay(250)
        refreshTrigger++
        delay(500)
        refreshTrigger++
        delay(800)
        refreshTrigger++
    }

    // 日历权限请求
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        resumeTrigger++
        if (!granted) {
            PermissionHelper.openAppDetailsSettings(context)
        }
    }

    // 通知权限请求
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        resumeTrigger++
        if (!granted) {
            PermissionHelper.openNotificationSettings(context)
        }
    }

    val isNotificationEnabled = remember(refreshTrigger) {
        PermissionHelper.isNotificationGranted(context)
    }

    val isCalendarPermissionGranted = remember(refreshTrigger) {
        PermissionHelper.isCalendarGranted(context)
    }

    val isExactAlarmEnabled = remember(refreshTrigger) {
        PermissionHelper.isExactAlarmGranted(context)
    }

    val isDndAccessGranted = remember(refreshTrigger) {
        PermissionHelper.isDndAccessGranted(context)
    }

    val isBatteryOptimizationsIgnored = remember(refreshTrigger) {
        PermissionHelper.isBatteryOptimizationsIgnored(context)
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.perm_title),
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
            // 核心提醒与日程权限
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 通知权限
                        ArrowPreference(
                            title = stringResource(R.string.perm_notification_title),
                            summary = stringResource(
                                if (isNotificationEnabled) R.string.perm_notification_granted
                                else R.string.perm_notification_denied
                            ),
                            onClick = {
                                if (!isNotificationEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    PermissionHelper.openNotificationSettings(context)
                                }
                            }
                        )

                        // 精确闹钟权限
                        ArrowPreference(
                            title = stringResource(R.string.perm_exact_alarm_title),
                            summary = stringResource(
                                if (isExactAlarmEnabled) R.string.perm_exact_alarm_granted
                                else R.string.perm_exact_alarm_denied
                            ),
                            onClick = {
                                PermissionHelper.openExactAlarmSettings(context)
                            }
                        )

                        // 日历权限
                        ArrowPreference(
                            title = stringResource(R.string.perm_calendar_title),
                            summary = stringResource(
                                if (isCalendarPermissionGranted) R.string.perm_calendar_granted
                                else R.string.perm_calendar_denied
                            ),
                            onClick = {
                                if (!isCalendarPermissionGranted) {
                                    calendarPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_CALENDAR,
                                            Manifest.permission.WRITE_CALENDAR
                                        )
                                    )
                                } else {
                                    PermissionHelper.openAppDetailsSettings(context)
                                }
                            }
                        )

                        // 勿扰策略权限
                        ArrowPreference(
                            title = stringResource(R.string.perm_dnd_title),
                            summary = stringResource(
                                if (isDndAccessGranted) R.string.perm_dnd_granted
                                else R.string.perm_dnd_denied
                            ),
                            onClick = {
                                PermissionHelper.openDndSettings(context)
                            }
                        )
                    }
                }
            }

            // 后台保活与自启动权限
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 自启动权限
                        ArrowPreference(
                            title = stringResource(R.string.perm_autostart_title),
                            summary = stringResource(R.string.perm_autostart_desc),
                            onClick = {
                                PermissionHelper.openAutoStartSettings(context)
                            }
                        )

                        // 忽略电池优化权限
                        ArrowPreference(
                            title = stringResource(R.string.perm_battery_title),
                            summary = stringResource(
                                if (isBatteryOptimizationsIgnored) R.string.perm_battery_granted
                                else R.string.perm_battery_denied
                            ),
                            onClick = {
                                PermissionHelper.openBatteryOptimizationSettings(context)
                            }
                        )
                    }
                }
            }

            // 说明与帮助
            item {
                Text(
                    text = stringResource(R.string.perm_hint_text),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

        }
    }
}
