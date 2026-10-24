package top.msfxp.schedule.ui.settings.subscreens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.msfxp.schedule.R
import top.msfxp.schedule.ui.settings.AboutViewModel
import top.msfxp.schedule.ui.settings.UpdateCheckState
import top.msfxp.schedule.util.AppActionHelper
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private const val QQ_GROUP_KEY = "rIdKEU42PuQ9p4al09NyyXjRWlm62N_f"
private const val QQ_GROUP_NUMBER = "767082393"
private const val GITHUB_REPO_URL = "https://github.com/yuexps/cqust-schedule-miuix"

// 关于应用二级页面
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: AboutViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 监听检查更新结果反馈
    LaunchedEffect(uiState.checkState) {
        when (uiState.checkState) {
            UpdateCheckState.LATEST -> {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.about_update_already_latest, uiState.localVersionName)
                )
            }
            UpdateCheckState.FAILED -> {
                val reason = uiState.errorMessageResId?.let { context.getString(it) }
                    ?: uiState.errorMessage
                    ?: context.getString(R.string.sync_failed_default)
                snackbarHostState.showSnackbar(
                    context.getString(R.string.about_update_check_failed, reason)
                )
            }
            else -> {}
        }
    }

    // 监听降级等即时 Toast 提示事件
    LaunchedEffect(Unit) {
        viewModel.toastMessageResId.collect { resId ->
            android.widget.Toast.makeText(context, context.getString(resId), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.about_title),
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
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 头部应用信息
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(androidx.compose.ui.graphics.Color.White)
                            .border(
                                width = 0.5.dp,
                                color = MiuixTheme.colorScheme.outline.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(17.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(1.22f)
                        )
                    }

                    Text(
                        text = stringResource(R.string.app_name),
                        style = MiuixTheme.textStyles.title1.copy(fontWeight = FontWeight.Bold),
                        color = MiuixTheme.colorScheme.onSurface
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.about_version_format, uiState.localVersionName),
                            style = MiuixTheme.textStyles.footnote2.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            // 核心功能与链接入口列表
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 开源仓库
                        ArrowPreference(
                            title = stringResource(R.string.about_item_repo),
                            summary = stringResource(R.string.about_desc_repo),
                            onClick = {
                                val opened = AppActionHelper.openBrowser(context, GITHUB_REPO_URL)
                                if (!opened) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.about_browser_open_failed))
                                    }
                                }
                            }
                        )

                        // 检查更新
                        val updateSummary = when (uiState.checkState) {
                            UpdateCheckState.CHECKING -> stringResource(R.string.about_desc_check_update_checking)
                            UpdateCheckState.LATEST -> stringResource(R.string.about_desc_check_update_latest)
                            UpdateCheckState.HAS_NEW_VERSION -> stringResource(
                                R.string.about_update_has_new,
                                uiState.latestRelease?.tagName.orEmpty()
                            )
                            UpdateCheckState.FAILED -> {
                                val reason = uiState.errorMessageResId?.let { stringResource(it) }
                                    ?: uiState.errorMessage
                                    ?: stringResource(R.string.sync_failed_default)
                                stringResource(R.string.about_update_check_failed, reason)
                            }
                            else -> stringResource(R.string.about_desc_check_update_idle)
                        }

                        ArrowPreference(
                            title = stringResource(R.string.about_item_check_update),
                            summary = updateSummary,
                            onClick = {
                                viewModel.onCheckUpdateClicked()
                            }
                        )

                        // QQ交流群
                        ArrowPreference(
                            title = stringResource(R.string.about_item_qq_group),
                            summary = stringResource(R.string.about_desc_qq_group),
                            onClick = {
                                val success = AppActionHelper.joinQQGroup(context, QQ_GROUP_KEY)
                                if (!success) {
                                    AppActionHelper.copyToClipboard(
                                        context = context,
                                        text = QQ_GROUP_NUMBER,
                                        label = context.getString(R.string.about_qq_group_copy_label)
                                    )
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.about_qq_group_copied_toast)
                                        )
                                    }
                                }
                            }
                        )

                        // 测试功能开关
                        SwitchPreference(
                            title = stringResource(R.string.perm_show_test_features_title),
                            summary = stringResource(R.string.perm_show_test_features_desc),
                            checked = uiState.showTestFeatures,
                            onCheckedChange = { viewModel.updateShowTestFeatures(it) }
                        )
                    }
                }
            }

            // 免责声明卡片
            item {
                SmallTitle(text = stringResource(R.string.about_item_disclaimer))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    val disclaimerRaw = stringResource(R.string.login_disclaimer)
                    val disclaimerLines = remember(disclaimerRaw) {
                        disclaimerRaw.lines().filter { it.isNotBlank() }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        disclaimerLines.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .offset(y = 8.dp)
                                        .clip(CircleShape)
                                        .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f))
                                )
                                Text(
                                    text = line,
                                    style = MiuixTheme.textStyles.body2.copy(lineHeight = 20.sp),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 检查到新版本弹窗
        if (uiState.showUpdateDialog && uiState.latestRelease != null) {
            val release = uiState.latestRelease!!
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
}
