package top.msfxp.schedule.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.msfxp.schedule.R
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 教务登录界面
@Composable
fun CqustLoginScreen(
    onLoginSuccess: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: CqustLoginViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            if (msg.isNotBlank()) {
                scope.launch {
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.login_title),
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val totalHeight = maxHeight
            val topSpacing = (totalHeight * 0.04f).coerceIn(12.dp, 32.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .heightIn(min = totalHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 登录表单内容
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(topSpacing))

                    // 学士帽标志图标容器
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                if (isSystemInDarkTheme()) Color(0xFF2563EB).copy(alpha = 0.22f)
                                else Color(0xFFEAF1FF)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_graduation_cap),
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 学校大标题
                    Text(
                        text = stringResource(R.string.login_header_school_name),
                        style = MiuixTheme.textStyles.title1.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp
                        ),
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 业务副标题
                    Text(
                        text = stringResource(R.string.login_header_sync_desc),
                        style = MiuixTheme.textStyles.body2.copy(
                            fontSize = 15.sp
                        ),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // 学号输入框
                    TextField(
                        value = uiState.studentId,
                        onValueChange = viewModel::onStudentIdChanged,
                        label = stringResource(R.string.login_student_id_label),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 密码输入框
                    TextField(
                        value = uiState.passwordRaw,
                        onValueChange = viewModel::onPasswordChanged,
                        label = stringResource(R.string.login_password_label),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) MiuixIcons.Show else MiuixIcons.Hide,
                                    contentDescription = stringResource(
                                        if (passwordVisible) R.string.login_hide_password else R.string.login_show_password
                                    ),
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (uiState.studentId.isNotBlank() && uiState.passwordRaw.isNotBlank() && !uiState.isLoading) {
                                    viewModel.performLogin(onLoginSuccess)
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 登录同步按钮
                    Button(
                        enabled = !uiState.isLoading && uiState.studentId.isNotBlank() && uiState.passwordRaw.isNotBlank(),
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.performLogin(onLoginSuccess)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                InfiniteProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MiuixTheme.colorScheme.onPrimary
                                )
                                Text(
                                    text = uiState.progressMessage.ifBlank { stringResource(R.string.login_progress_default) },
                                    style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.login_button_text),
                                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                // 底部免责声明
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 24.dp, top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.login_disclaimer),
                        style = MiuixTheme.textStyles.footnote2.copy(
                            fontSize = 11.5.sp,
                            lineHeight = 18.sp
                        ),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
