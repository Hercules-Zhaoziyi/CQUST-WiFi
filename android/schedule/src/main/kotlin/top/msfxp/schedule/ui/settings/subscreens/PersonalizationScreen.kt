package top.msfxp.schedule.ui.settings.subscreens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.DefaultCourseColors
import top.msfxp.schedule.ui.settings.PersonalizationViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlin.math.roundToInt

// 个性化设置界面
@Composable
fun PersonalizationScreen(
    onBack: () -> Unit,
    viewModel: PersonalizationViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var editingSlotIndex by remember { mutableStateOf<Int?>(null) }

    var localMaskDim by remember { mutableFloatStateOf(uiState.wallpaperMaskDim) }
    var localCardAlpha by remember { mutableFloatStateOf(uiState.courseCardAlpha) }

    LaunchedEffect(uiState.hasCustomWallpaper) {
        localMaskDim = uiState.wallpaperMaskDim
        localCardAlpha = uiState.courseCardAlpha
    }

    val croppingBitmap by viewModel.croppingBitmap.collectAsState()
    val isDecodingCropImage by viewModel.isDecodingCropImage.collectAsState()

    // 相册图片挑选器
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.prepareCropping(uri)
        }
    }

    // 监听提示消息
    LaunchedEffect(uiState.message) {
        uiState.message?.let { msg ->
            val text = context.getString(msg.resId)
            scope.launch {
                snackbarHostState.showSnackbar(text)
            }
            viewModel.clearMessage()
        }
    }

    // 壁纸缓存流
    val wallpaperBitmap by viewModel.wallpaperBitmapFlow.collectAsState()

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.personalization_title),
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
            // 分组一：课表壁纸设置
            item {
                SmallTitle(text = stringResource(R.string.personalization_section_wallpaper))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 壁纸状态行
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val currentWallpaper = wallpaperBitmap
                            if (currentWallpaper != null) {
                                Image(
                                    bitmap = currentWallpaper,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MiuixTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Image,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (uiState.hasCustomWallpaper) {
                                        stringResource(R.string.personalization_wallpaper_custom_status)
                                    } else {
                                        stringResource(R.string.personalization_wallpaper_default_status)
                                    },
                                    style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Bold),
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 操作按钮组
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                colors = ButtonDefaults.buttonColorsPrimary(),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Text(
                                    text = if (uiState.hasCustomWallpaper) {
                                        stringResource(R.string.personalization_wallpaper_change)
                                    } else {
                                        stringResource(R.string.personalization_wallpaper_select)
                                    }
                                )
                            }

                            if (uiState.hasCustomWallpaper) {
                                TextButton(
                                    text = stringResource(R.string.personalization_wallpaper_clear),
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.clearWallpaper() }
                                )
                            }
                        }

                        // 壁纸微调滑块组
                        if (uiState.hasCustomWallpaper) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SliderPreference(
                                    title = stringResource(
                                        R.string.personalization_wallpaper_mask_dim_title,
                                        (localMaskDim * 100).roundToInt()
                                    ),
                                    value = localMaskDim,
                                    valueRange = 0f..0.85f,
                                    onValueChange = {
                                        localMaskDim = it
                                        viewModel.updateWallpaperMaskDim(it)
                                    }
                                )

                                SliderPreference(
                                    title = stringResource(
                                        R.string.personalization_card_alpha_title,
                                        (localCardAlpha * 100).roundToInt()
                                    ),
                                    value = localCardAlpha,
                                    valueRange = 0.4f..1f,
                                    onValueChange = {
                                        localCardAlpha = it
                                        viewModel.updateCourseCardAlpha(it)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 分组二：课程配色
            item {
                SmallTitle(text = stringResource(R.string.personalization_section_palette))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val colors = uiState.courseColors
                        for (row in 0 until 4) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (col in 0 until 4) {
                                    val idx = row * 4 + col
                                    val itemColor = colors.getOrNull(idx)?.light ?: Color.Gray

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(itemColor)
                                            .border(
                                                width = 1.dp,
                                                color = Color.White.copy(alpha = 0.35f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { editingSlotIndex = idx },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${idx + 1}",
                                            style = MiuixTheme.textStyles.footnote2.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 颜色修改弹窗
    editingSlotIndex?.let { slotIdx ->
        val currentSlotColor = uiState.courseColors.getOrNull(slotIdx)?.light ?: DefaultCourseColors[0].light
        val defaultSlotColor = DefaultCourseColors.getOrElse(slotIdx) { DefaultCourseColors[0] }

        ColorPickerDialog(
            show = true,
            slotIndex = slotIdx,
            initialColor = currentSlotColor,
            defaultColor = defaultSlotColor,
            onDismissRequest = { editingSlotIndex = null },
            onConfirm = { chosenColor ->
                viewModel.updateSlotColor(slotIdx, chosenColor)
            }
        )
    }

    // 壁纸区域框选器弹窗
    croppingBitmap?.let { cropSrc ->
        WallpaperCropDialog(
            sourceBitmap = cropSrc,
            onConfirm = { cropped ->
                viewModel.applyCroppedWallpaper(cropped)
            },
            onDismissRequest = {
                viewModel.cancelCropping()
            }
        )
    }

    // 图像载入中提示
    if (isDecodingCropImage) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.crop_wallpaper_loading),
            onDismissRequest = {}
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.crop_wallpaper_loading),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

