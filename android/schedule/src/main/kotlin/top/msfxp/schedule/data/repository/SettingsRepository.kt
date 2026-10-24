package top.msfxp.schedule.data.repository

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.msfxp.schedule.data.model.AppSettings
import top.msfxp.schedule.data.model.AutoControlMode

private val Context.dataStore by preferencesDataStore(name = "cqust_schedule_prefs")

// 用户偏好与应用设置仓库
class SettingsRepository(private val context: Context) {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 应用偏好设置热流
    val appSettingsFlow: StateFlow<AppSettings> = context.dataStore.data
        .map { prefs -> AppSettings.fromPreferences(prefs) }
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings()
        )

    val currentSettings: AppSettings
        get() = appSettingsFlow.value

    private val _wallpaperBitmapFlow = MutableStateFlow<ImageBitmap?>(null)
    val wallpaperBitmapFlow: StateFlow<ImageBitmap?> = _wallpaperBitmapFlow.asStateFlow()

    init {
        repositoryScope.launch {
            loadWallpaperToCache()
        }
    }

    // 后台加载壁纸至内存缓存
    suspend fun loadWallpaperToCache() = withContext(Dispatchers.IO) {
        val file = getWallpaperFile()
        val bitmap = if (file.exists() && file.length() > 0) {
            runCatching {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }.getOrNull()
        } else {
            null
        }
        _wallpaperBitmapFlow.value = bitmap
    }

    suspend fun getAppSettingsOnce(): AppSettings {
        return AppSettings.fromPreferences(context.dataStore.data.first())
    }

    // 更新全局设置项
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = AppSettings.fromPreferences(prefs)
            val updated = transform(current)
            prefs[AppSettings.KEY_CURRENT_TABLE_ID] = updated.currentTableId
            prefs[AppSettings.KEY_STUDENT_ID] = updated.studentId
            prefs[AppSettings.KEY_STUDENT_NAME] = updated.studentName
            prefs[AppSettings.KEY_COLLEGE] = updated.college
            prefs[AppSettings.KEY_MAJOR] = updated.major
            prefs[AppSettings.KEY_CLASS_NAME] = updated.className
            prefs[AppSettings.KEY_PASSWORD] = updated.passwordEncrypted
            prefs[AppSettings.KEY_IS_LOGGED_IN] = updated.isLoggedIn
            prefs[AppSettings.KEY_LAST_SYNC_TIME] = updated.lastSyncTime
            prefs[AppSettings.KEY_REMINDER_ENABLED] = updated.reminderEnabled
            prefs[AppSettings.KEY_REMIND_BEFORE_MINUTES] = updated.remindBeforeMinutes
            prefs[AppSettings.KEY_AUTO_DND_ENABLED] = updated.autoDndEnabled
            prefs[AppSettings.KEY_AUTO_CONTROL_MODE] = updated.autoControlMode.value
            prefs[AppSettings.KEY_AUTO_SYNC_TO_CALENDAR] = updated.autoSyncToCalendar
            prefs[AppSettings.KEY_CALENDAR_REMIND_BEFORE_MINUTES] = updated.calendarRemindBeforeMinutes
            prefs[AppSettings.KEY_HAS_CUSTOM_WALLPAPER] = updated.hasCustomWallpaper
            prefs[AppSettings.KEY_WALLPAPER_MASK_DIM] = updated.wallpaperMaskDim
            prefs[AppSettings.KEY_COURSE_CARD_ALPHA] = updated.courseCardAlpha
            prefs[AppSettings.KEY_CUSTOM_COURSE_COLORS_JSON] = updated.customCourseColorsJson
            prefs[AppSettings.KEY_SHOW_TEST_FEATURES] = updated.showTestFeatures
            prefs[AppSettings.KEY_LATEST_VERSION_TAG] = updated.latestVersionTag
            prefs[AppSettings.KEY_LATEST_VERSION_DOWNLOAD_URL] = updated.latestVersionDownloadUrl
            prefs[AppSettings.KEY_LATEST_VERSION_BODY] = updated.latestVersionBody
            prefs[AppSettings.KEY_LAST_UPDATE_CHECK_TIME] = updated.lastUpdateCheckTime
        }
    }

    // 保存学生档案与加密凭证
    suspend fun saveFullProfileAndCredentials(
        studentId: String,
        studentName: String,
        college: String,
        major: String,
        className: String,
        passwordEncrypted: String
    ) {
        updateSettings {
            it.copy(
                studentId = studentId,
                studentName = studentName,
                college = college,
                major = major,
                className = className,
                passwordEncrypted = passwordEncrypted,
                isLoggedIn = true,
                lastSyncTime = System.currentTimeMillis()
            )
        }
    }

    // 保存登录凭证
    suspend fun saveLoginCredentials(studentId: String, passwordEncrypted: String) {
        updateSettings {
            it.copy(
                studentId = studentId,
                passwordEncrypted = passwordEncrypted,
                isLoggedIn = true,
                lastSyncTime = System.currentTimeMillis()
            )
        }
    }

    // 更新最后同步时间戳
    suspend fun updateLastSyncTime(time: Long = System.currentTimeMillis()) {
        updateSettings { it.copy(lastSyncTime = time) }
    }

    // 清除用户登录状态与凭证
    suspend fun logout() {
        updateSettings {
            it.copy(
                studentId = "",
                passwordEncrypted = "",
                isLoggedIn = false,
                lastSyncTime = 0L
            )
        }
    }

    suspend fun updateReminderEnabled(enabled: Boolean) {
        updateSettings { it.copy(reminderEnabled = enabled) }
    }

    suspend fun updateRemindBeforeMinutes(minutes: Int) {
        updateSettings { it.copy(remindBeforeMinutes = minutes) }
    }

    suspend fun updateCalendarRemindBeforeMinutes(minutes: Int) {
        updateSettings { it.copy(calendarRemindBeforeMinutes = minutes) }
    }

    suspend fun updateAutoDndEnabled(enabled: Boolean) {
        updateSettings { it.copy(autoDndEnabled = enabled) }
    }

    suspend fun updateAutoControlMode(mode: AutoControlMode) {
        updateSettings { it.copy(autoControlMode = mode) }
    }

    suspend fun updateAutoSyncToCalendar(enabled: Boolean) {
        updateSettings { it.copy(autoSyncToCalendar = enabled) }
    }

    suspend fun updateShowTestFeatures(show: Boolean) {
        updateSettings { it.copy(showTestFeatures = show) }
    }


    // 获取壁纸存储文件对象
    fun getWallpaperFile(): java.io.File {
        val dir = java.io.File(context.filesDir, "wallpaper")
        if (!dir.exists()) dir.mkdirs()
        return java.io.File(dir, "schedule_wallpaper.jpg")
    }

    // 从 Uri 读取图片并保存为课表壁纸
    suspend fun saveWallpaperFromUri(uri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        val finalBitmap = top.msfxp.schedule.util.ImageBitmapHelper.decodeAndCorrectOrientation(context, uri, maxDimension = 2560)
            ?: return@withContext false

        try {
            val targetFile = getWallpaperFile()
            java.io.FileOutputStream(targetFile).use { out ->
                finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
            }
            _wallpaperBitmapFlow.value = finalBitmap.asImageBitmap()
            updateSettings { it.copy(hasCustomWallpaper = true) }
            true
        } catch (e: Exception) {
            android.util.Log.e("SettingsRepository", "Failed to save wallpaper from uri: $uri", e)
            false
        }
    }

    // 从 Uri 解析纠正旋转后的位图用于框选裁切
    suspend fun decodeBitmapForCropping(uri: android.net.Uri): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        top.msfxp.schedule.util.ImageBitmapHelper.decodeAndCorrectOrientation(context, uri, maxDimension = 3840)
    }

    // 保存已框选裁切的壁纸位图
    suspend fun saveCroppedWallpaper(bitmap: android.graphics.Bitmap): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val targetFile = getWallpaperFile()
            java.io.FileOutputStream(targetFile).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
            }
            _wallpaperBitmapFlow.value = bitmap.asImageBitmap()
            updateSettings { it.copy(hasCustomWallpaper = true) }
            true
        } catch (e: Exception) {
            android.util.Log.e("SettingsRepository", "Failed to save cropped wallpaper", e)
            false
        }
    }

    // 清除自定义课表壁纸
    suspend fun clearWallpaper(): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val file = getWallpaperFile()
            if (file.exists()) file.delete()
            _wallpaperBitmapFlow.value = null
            updateSettings { it.copy(hasCustomWallpaper = false) }
            true
        } catch (_: Exception) {
            false
        }
    }

    // 更新壁纸暗度遮罩
    suspend fun updateWallpaperMaskDim(dim: Float) {
        updateSettings { it.copy(wallpaperMaskDim = dim.coerceIn(0f, 0.85f)) }
    }

    // 更新课程卡片不透明度
    suspend fun updateCourseCardAlpha(alpha: Float) {
        updateSettings { it.copy(courseCardAlpha = alpha.coerceIn(0.3f, 1f)) }
    }

    // 保存自定义课程调色板 JSON
    suspend fun updateCustomCourseColors(json: String) {
        updateSettings { it.copy(customCourseColorsJson = json) }
    }

    // 重置课程配色为默认方案
    suspend fun resetCourseColors() {
        updateSettings { it.copy(customCourseColorsJson = "") }
    }

    // 保存最新版本更新信息
    suspend fun saveLatestReleaseInfo(tag: String, downloadUrl: String, body: String) {
        updateSettings {
            it.copy(
                latestVersionTag = tag,
                latestVersionDownloadUrl = downloadUrl,
                latestVersionBody = body
            )
        }
    }

    // 清除最新版本更新缓存
    suspend fun clearLatestReleaseInfo() {
        updateSettings {
            it.copy(
                latestVersionTag = "",
                latestVersionDownloadUrl = "",
                latestVersionBody = ""
            )
        }
    }

    // 更新上次检查更新时间戳
    suspend fun updateLastUpdateCheckTime(time: Long) {
        updateSettings { it.copy(lastUpdateCheckTime = time) }
    }
}
