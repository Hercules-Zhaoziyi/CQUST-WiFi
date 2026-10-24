package top.msfxp.schedule.ui.settings

import android.app.Application
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import top.msfxp.schedule.data.model.*
import top.msfxp.schedule.data.repository.SettingsRepository
import java.io.File

// 个性化反馈提示枚举
enum class PersonalizationMessage(val resId: Int) {
    WallpaperSetSuccess(top.msfxp.schedule.R.string.personalization_wallpaper_set_success),
    WallpaperSetFail(top.msfxp.schedule.R.string.personalization_wallpaper_set_fail),
    WallpaperClearSuccess(top.msfxp.schedule.R.string.personalization_wallpaper_clear_success)
}

// 个性化设置界面状态
data class PersonalizationUiState(
    val hasCustomWallpaper: Boolean = false,
    val wallpaperMaskDim: Float = 0.15f,
    val courseCardAlpha: Float = 0.95f,
    val wallpaperFile: File? = null,
    val wallpaperTimestamp: Long = 0L,
    val courseColors: List<CourseColor> = DefaultCourseColors,
    val message: PersonalizationMessage? = null
)

// 个性化业务视图模型
class PersonalizationViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _message = MutableStateFlow<PersonalizationMessage?>(null)
    private val _wallpaperTimestamp = MutableStateFlow(System.currentTimeMillis())

    val wallpaperBitmapFlow: StateFlow<ImageBitmap?> = settingsRepository.wallpaperBitmapFlow

    // 组合设置流与界面状态
    val uiState: StateFlow<PersonalizationUiState> = combine(
        settingsRepository.appSettingsFlow,
        _message,
        _wallpaperTimestamp
    ) { settings, msg, timestamp ->
        val wallpaperFile = settingsRepository.getWallpaperFile()
        val fileExists = settings.hasCustomWallpaper && wallpaperFile.exists()
        val parsedColors = parseCoursePaletteJson(settings.customCourseColorsJson)

        PersonalizationUiState(
            hasCustomWallpaper = fileExists,
            wallpaperMaskDim = settings.wallpaperMaskDim,
            courseCardAlpha = settings.courseCardAlpha,
            wallpaperFile = if (fileExists) wallpaperFile else null,
            wallpaperTimestamp = timestamp,
            courseColors = parsedColors,
            message = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = run {
            val initialSettings = settingsRepository.currentSettings
            val wallpaperFile = settingsRepository.getWallpaperFile()
            val fileExists = initialSettings.hasCustomWallpaper && wallpaperFile.exists()
            PersonalizationUiState(
                hasCustomWallpaper = fileExists,
                wallpaperMaskDim = initialSettings.wallpaperMaskDim,
                courseCardAlpha = initialSettings.courseCardAlpha,
                wallpaperFile = if (fileExists) wallpaperFile else null,
                courseColors = parseCoursePaletteJson(initialSettings.customCourseColorsJson)
            )
        }
    )

    // 裁剪原图与解码状态
    val croppingBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val isDecodingCropImage = MutableStateFlow(false)

    // 准备裁剪图片
    fun prepareCropping(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            isDecodingCropImage.value = true
            val bitmap = settingsRepository.decodeBitmapForCropping(uri)
            isDecodingCropImage.value = false
            if (bitmap != null) {
                croppingBitmap.value = bitmap
            } else {
                _message.value = PersonalizationMessage.WallpaperSetFail
            }
        }
    }

    // 保存裁剪后的壁纸
    fun applyCroppedWallpaper(croppedBitmap: android.graphics.Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = settingsRepository.saveCroppedWallpaper(croppedBitmap)
            croppingBitmap.value = null
            if (success) {
                _wallpaperTimestamp.value = System.currentTimeMillis()
                _message.value = PersonalizationMessage.WallpaperSetSuccess
            } else {
                _message.value = PersonalizationMessage.WallpaperSetFail
            }
        }
    }

    // 取消裁剪
    fun cancelCropping() {
        croppingBitmap.value = null
    }

    // 设置课表壁纸 (保留直接设置兜底)
    fun setWallpaper(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = settingsRepository.saveWallpaperFromUri(uri)
            if (success) {
                _wallpaperTimestamp.value = System.currentTimeMillis()
                _message.value = PersonalizationMessage.WallpaperSetSuccess
            } else {
                _message.value = PersonalizationMessage.WallpaperSetFail
            }
        }
    }

    // 清除课表壁纸
    fun clearWallpaper() {
        viewModelScope.launch(Dispatchers.IO) {
            val success = settingsRepository.clearWallpaper()
            if (success) {
                _wallpaperTimestamp.value = System.currentTimeMillis()
                _message.value = PersonalizationMessage.WallpaperClearSuccess
            }
        }
    }

    private var saveDimJob: kotlinx.coroutines.Job? = null
    private var saveAlphaJob: kotlinx.coroutines.Job? = null

    // 更新壁纸暗度遮罩
    fun updateWallpaperMaskDim(dim: Float) {
        val clamped = dim.coerceIn(0f, 0.85f)
        saveDimJob?.cancel()
        saveDimJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(200)
            settingsRepository.updateWallpaperMaskDim(clamped)
        }
    }

    // 更新课程卡片透明度
    fun updateCourseCardAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0.4f, 1f)
        saveAlphaJob?.cancel()
        saveAlphaJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(200)
            settingsRepository.updateCourseCardAlpha(clamped)
        }
    }

    // 修改特定槽位颜色
    fun updateSlotColor(index: Int, newColor: Color) {
        viewModelScope.launch {
            val currentList = uiState.value.courseColors.toMutableList()
            if (index in currentList.indices) {
                currentList[index] = CourseColor.fromColor(newColor)
                val json = serializeCoursePalette(currentList)
                settingsRepository.updateCustomCourseColors(json)
            }
        }
    }

    // 重置调色板为默认 16 色
    fun resetPalette() {
        viewModelScope.launch {
            settingsRepository.resetCourseColors()
        }
    }

    // 清除反馈提示
    fun clearMessage() {
        _message.value = null
    }
}
