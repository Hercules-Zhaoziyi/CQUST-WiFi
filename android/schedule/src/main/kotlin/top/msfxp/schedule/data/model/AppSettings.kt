package top.msfxp.schedule.data.model

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

// 课中自动化控制模式
enum class AutoControlMode(val value: String) {
    DND("DND"),
    SILENT("SILENT");

    companion object {
        fun fromString(value: String?): AutoControlMode {
            return entries.find { it.value == value } ?: DND
        }
    }
}

// 应用全局配置数据模型
data class AppSettings(
    val currentTableId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val college: String = "",
    val major: String = "",
    val className: String = "",
    val passwordEncrypted: String = "",
    val isLoggedIn: Boolean = false,
    val lastSyncTime: Long = 0L,

    // 提醒与自动化配置
    val reminderEnabled: Boolean = false,
    val remindBeforeMinutes: Int = 15,
    val autoDndEnabled: Boolean = false,
    val autoControlMode: AutoControlMode = AutoControlMode.DND,
    val autoSyncToCalendar: Boolean = false,
    val calendarRemindBeforeMinutes: Int = 15,

    // 个性化设置
    val hasCustomWallpaper: Boolean = false,
    val wallpaperMaskDim: Float = 0.15f,
    val courseCardAlpha: Float = 0.95f,
    val customCourseColorsJson: String = "",

    // 测试功能开关
    val showTestFeatures: Boolean = false,

    // 更新结果缓存与检查时间
    val latestVersionTag: String = "",
    val latestVersionDownloadUrl: String = "",
    val latestVersionBody: String = "",
    val lastUpdateCheckTime: Long = 0L
) {
    companion object {
        val KEY_CURRENT_TABLE_ID = stringPreferencesKey("current_table_id")
        val KEY_STUDENT_ID = stringPreferencesKey("student_id")
        val KEY_STUDENT_NAME = stringPreferencesKey("student_name")
        val KEY_COLLEGE = stringPreferencesKey("college")
        val KEY_MAJOR = stringPreferencesKey("major")
        val KEY_CLASS_NAME = stringPreferencesKey("class_name")
        val KEY_PASSWORD = stringPreferencesKey("password")
        val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_LAST_SYNC_TIME = longPreferencesKey("last_sync_time")

        val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val KEY_REMIND_BEFORE_MINUTES = intPreferencesKey("remind_before_minutes")
        val KEY_AUTO_DND_ENABLED = booleanPreferencesKey("auto_dnd_enabled")
        val KEY_AUTO_CONTROL_MODE = stringPreferencesKey("auto_control_mode")
        val KEY_AUTO_SYNC_TO_CALENDAR = booleanPreferencesKey("auto_sync_to_calendar")
        val KEY_CALENDAR_REMIND_BEFORE_MINUTES = intPreferencesKey("calendar_remind_before_minutes")

        val KEY_HAS_CUSTOM_WALLPAPER = booleanPreferencesKey("has_custom_wallpaper")
        val KEY_WALLPAPER_MASK_DIM = androidx.datastore.preferences.core.floatPreferencesKey("wallpaper_mask_dim")
        val KEY_COURSE_CARD_ALPHA = androidx.datastore.preferences.core.floatPreferencesKey("course_card_alpha")
        val KEY_CUSTOM_COURSE_COLORS_JSON = stringPreferencesKey("custom_course_colors_json")
        val KEY_SHOW_TEST_FEATURES = booleanPreferencesKey("show_test_features")

        val KEY_LATEST_VERSION_TAG = stringPreferencesKey("latest_version_tag")
        val KEY_LATEST_VERSION_DOWNLOAD_URL = stringPreferencesKey("latest_version_download_url")
        val KEY_LATEST_VERSION_BODY = stringPreferencesKey("latest_version_body")
        val KEY_LAST_UPDATE_CHECK_TIME = longPreferencesKey("last_update_check_time")

        // 从 DataStore 首选项解析配置对象
        fun fromPreferences(prefs: Preferences): AppSettings {
            return AppSettings(
                currentTableId = prefs[KEY_CURRENT_TABLE_ID] ?: "",
                studentId = prefs[KEY_STUDENT_ID] ?: "",
                studentName = prefs[KEY_STUDENT_NAME] ?: "",
                college = prefs[KEY_COLLEGE] ?: "",
                major = prefs[KEY_MAJOR] ?: "",
                className = prefs[KEY_CLASS_NAME] ?: "",
                passwordEncrypted = prefs[KEY_PASSWORD] ?: "",
                isLoggedIn = prefs[KEY_IS_LOGGED_IN] ?: false,
                lastSyncTime = prefs[KEY_LAST_SYNC_TIME] ?: 0L,
                reminderEnabled = prefs[KEY_REMINDER_ENABLED] ?: false,
                remindBeforeMinutes = prefs[KEY_REMIND_BEFORE_MINUTES] ?: 15,
                autoDndEnabled = prefs[KEY_AUTO_DND_ENABLED] ?: false,
                autoControlMode = AutoControlMode.fromString(prefs[KEY_AUTO_CONTROL_MODE]),
                autoSyncToCalendar = prefs[KEY_AUTO_SYNC_TO_CALENDAR] ?: false,
                calendarRemindBeforeMinutes = prefs[KEY_CALENDAR_REMIND_BEFORE_MINUTES] ?: 15,
                hasCustomWallpaper = prefs[KEY_HAS_CUSTOM_WALLPAPER] ?: false,
                wallpaperMaskDim = prefs[KEY_WALLPAPER_MASK_DIM] ?: 0.15f,
                courseCardAlpha = prefs[KEY_COURSE_CARD_ALPHA] ?: 0.95f,
                customCourseColorsJson = prefs[KEY_CUSTOM_COURSE_COLORS_JSON] ?: "",
                showTestFeatures = prefs[KEY_SHOW_TEST_FEATURES] ?: false,
                latestVersionTag = prefs[KEY_LATEST_VERSION_TAG] ?: "",
                latestVersionDownloadUrl = prefs[KEY_LATEST_VERSION_DOWNLOAD_URL] ?: "",
                latestVersionBody = prefs[KEY_LATEST_VERSION_BODY] ?: "",
                lastUpdateCheckTime = prefs[KEY_LAST_UPDATE_CHECK_TIME] ?: 0L
            )
        }
    }
}
