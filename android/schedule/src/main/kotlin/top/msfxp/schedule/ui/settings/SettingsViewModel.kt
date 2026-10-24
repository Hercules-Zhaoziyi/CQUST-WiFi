package top.msfxp.schedule.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.msfxp.schedule.data.api.CqustSyncManager
import top.msfxp.schedule.data.api.LocalCrypto
import top.msfxp.schedule.data.model.AppSettings
import top.msfxp.schedule.data.model.AutoControlMode
import top.msfxp.schedule.data.model.Semester
import top.msfxp.schedule.data.repository.ScheduleRepository
import top.msfxp.schedule.data.repository.SettingsRepository
import top.msfxp.schedule.service.CalendarSyncHelper

// 设置界面状态
data class SettingsUiState(
    val appSettings: AppSettings = AppSettings(),
    val currentSemester: Semester? = null,
    val availableSemesters: List<Semester> = emptyList(),
    val courseCount: Int = 0,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val switchingSemesterId: String? = null
)

// 偏好设置视图模型
class SettingsViewModel(
    private val context: android.content.Context,
    private val settingsRepository: SettingsRepository,
    private val scheduleRepository: ScheduleRepository,
    private val cqustSyncManager: CqustSyncManager,
    private val calendarSyncHelper: CalendarSyncHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val tableId = scheduleRepository.getOrCreateDefaultTableId()
            scheduleRepository.getAllCoursesWithEvents(tableId).collect { list ->
                val distinctCount = list.map { it.course.courseCode }.distinct().size
                _uiState.update { it.copy(courseCount = distinctCount) }
            }
        }

        viewModelScope.launch {
            combine(
                settingsRepository.appSettingsFlow,
                scheduleRepository.allSemestersFlow,
                scheduleRepository.currentSemesterFlow
            ) { settings, semesters, currentSemester ->
                Triple(settings, semesters, currentSemester)
            }.collect { (settings, semesters, currentSemester) ->
                _uiState.update {
                    it.copy(
                        appSettings = settings,
                        availableSemesters = semesters,
                        currentSemester = currentSemester
                    )
                }
            }
        }
    }

    // 重新同步课表数据
    fun resyncCourses(targetSemesterId: String? = null, onNeedLogin: () -> Unit) {
        val settings = _uiState.value.appSettings
        if (!settings.isLoggedIn || settings.studentId.isBlank() || settings.passwordEncrypted.isBlank()) {
            onNeedLogin()
            return
        }

        _uiState.update { it.copy(isSyncing = true, syncMessage = null) }
        viewModelScope.launch {
            val rawPassword = LocalCrypto.decrypt(settings.passwordEncrypted)
            val currentSem = _uiState.value.currentSemester
            val result = cqustSyncManager.syncCourses(
                studentId = settings.studentId,
                passwordRaw = rawPassword,
                targetSemesterId = targetSemesterId ?: currentSem?.id
            )
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    syncMessage = if (result.isSuccess) {
                        val summary = result.getOrNull()
                        if (summary != null) {
                            context.getString(top.msfxp.schedule.R.string.sync_success_format, summary.regularCount, summary.practicalCount)
                        } else {
                            context.getString(top.msfxp.schedule.R.string.calendar_sync_success_toast)
                        }
                    } else result.exceptionOrNull()?.message ?: context.getString(top.msfxp.schedule.R.string.sync_failed_default)
                )
            }
        }
    }

    // 切换当前激活学期并重新同步
    fun switchSemester(
        semesterId: String,
        onNeedLogin: () -> Unit,
        onSuccess: (() -> Unit)? = null
    ) {
        val settings = _uiState.value.appSettings
        if (!settings.isLoggedIn || settings.studentId.isBlank() || settings.passwordEncrypted.isBlank()) {
            onNeedLogin()
            return
        }

        _uiState.update {
            it.copy(
                isSyncing = true,
                switchingSemesterId = semesterId,
                syncMessage = null
            )
        }

        viewModelScope.launch {
            try {
                scheduleRepository.setActiveSemester(semesterId)
                val rawPassword = LocalCrypto.decrypt(settings.passwordEncrypted)
                val result = cqustSyncManager.syncCourses(
                    studentId = settings.studentId,
                    passwordRaw = rawPassword,
                    targetSemesterId = semesterId
                )
                val isSuccess = result.isSuccess
                val syncMsg = if (isSuccess) {
                    val summary = result.getOrNull()
                    if (summary != null) {
                        context.getString(top.msfxp.schedule.R.string.sync_success_format, summary.regularCount, summary.practicalCount)
                    } else {
                        context.getString(top.msfxp.schedule.R.string.calendar_sync_success_toast)
                    }
                } else {
                    result.exceptionOrNull()?.message ?: context.getString(top.msfxp.schedule.R.string.sync_failed_default)
                }

                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        switchingSemesterId = null,
                        syncMessage = syncMsg
                    )
                }

                if (isSuccess) {
                    onSuccess?.invoke()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        switchingSemesterId = null,
                        syncMessage = e.message ?: context.getString(top.msfxp.schedule.R.string.sync_failed_default)
                    )
                }
            }
        }
    }

    // 退出登录并清除本地缓存数据
    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val tableId = scheduleRepository.getOrCreateDefaultTableId()
            scheduleRepository.clearCourses(tableId)
            scheduleRepository.clearSemesters()
            settingsRepository.logout()
            onSuccess()
        }
    }

    // 更新当前学期开学日期
    fun updateSemesterStartDate(dateStr: String) {
        viewModelScope.launch {
            val currentSem = _uiState.value.currentSemester
            if (currentSem != null) {
                scheduleRepository.updateSemesterStartDate(currentSem.id, dateStr)
            }
        }
    }

    // 更新当前学期总周数
    fun updateTotalWeeks(weeks: Int) {
        viewModelScope.launch {
            val currentSem = _uiState.value.currentSemester
            if (currentSem != null) {
                scheduleRepository.updateSemesterTotalWeeks(currentSem.id, weeks)
            }
        }
    }

    // 更新课前提醒开关
    fun updateReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateReminderEnabled(enabled)
        }
    }

    // 更新提前提醒分钟数
    fun updateRemindBeforeMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.updateRemindBeforeMinutes(minutes)
        }
    }

    // 更新日历事件提前提醒分钟数
    fun updateCalendarRemindBeforeMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.updateCalendarRemindBeforeMinutes(minutes)
            if (_uiState.value.appSettings.autoSyncToCalendar) {
                calendarSyncHelper.syncCurrentScheduleToCalendar()
            }
        }
    }

    // 更新课堂免打扰开关
    fun updateAutoDndEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAutoDndEnabled(enabled)
        }
    }

    // 更新上课控制模式
    fun updateAutoControlMode(mode: AutoControlMode) {
        viewModelScope.launch {
            settingsRepository.updateAutoControlMode(mode)
        }
    }

    // 更新自动同步到系统日历开关
    fun updateAutoSyncToCalendar(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAutoSyncToCalendar(enabled)
            if (enabled) {
                calendarSyncHelper.syncCurrentScheduleToCalendar()
            }
        }
    }

    // 更新测试功能显示与隐藏开关
    fun updateShowTestFeatures(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateShowTestFeatures(show)
        }
    }


    // 手动触发全量同步至系统日历
    fun syncToCalendarManually(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = calendarSyncHelper.syncCurrentScheduleToCalendar()
            onResult(success)
        }
    }

    // 从系统日历中删除所有课表日程及专属账户
    fun deleteCalendarSchedule(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = calendarSyncHelper.deleteScheduleCalendar()
            onResult(success)
        }
    }


    // 发送立即测试提醒通知
    fun sendTestNotification(context: android.content.Context) {
        viewModelScope.launch {
            val tableId = scheduleRepository.getOrCreateDefaultTableId()
            val courses = scheduleRepository.getAllCoursesWithEventsOnce(tableId)
            val (name, position, teacher) = getTestCoursePayload(context)
            val testTitle = context.getString(top.msfxp.schedule.R.string.test_notification_title, name)

            top.msfxp.schedule.service.CourseAlarmReceiver.showNotification(
                context = context,
                notificationId = TEST_NOTIFICATION_ID_IMMEDIATE,
                name = testTitle,
                position = position,
                teacher = teacher
            )
        }
    }

    // 设置 60 秒后后台定时测试通知闹钟
    fun scheduleDelayed60sTestNotification(context: android.content.Context) {
        viewModelScope.launch {
            val (name, position, teacher) = getTestCoursePayload(context)
            val testTitle = context.getString(top.msfxp.schedule.R.string.test_notification_wakeup_title, name)

            val alarmManager = context.getSystemService(android.app.AlarmManager::class.java) ?: return@launch
            val triggerMillis = System.currentTimeMillis() + 60_000L

            val reminderIntent = android.content.Intent(context, top.msfxp.schedule.service.CourseAlarmReceiver::class.java).apply {
                putExtra(top.msfxp.schedule.service.CourseAlarmReceiver.EXTRA_COURSE_NAME, testTitle)
                putExtra(top.msfxp.schedule.service.CourseAlarmReceiver.EXTRA_COURSE_POSITION, position)
                putExtra(top.msfxp.schedule.service.CourseAlarmReceiver.EXTRA_COURSE_TEACHER, teacher)
                putExtra(top.msfxp.schedule.service.CourseAlarmReceiver.EXTRA_NOTIFICATION_ID, TEST_NOTIFICATION_ID_DELAYED)
            }

            val pi = android.app.PendingIntent.getBroadcast(
                context,
                TEST_NOTIFICATION_ID_DELAYED,
                reminderIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            if (alarmManager.canScheduleExactAlarms()) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerMillis, pi)
                } catch (_: SecurityException) {
                    alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerMillis, pi)
                }
            } else {
                alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            }
        }
    }

    // 立即测试切换控制模式并在10秒后自动恢复
    fun testToggleModeImmediately(context: android.content.Context) {
        viewModelScope.launch {
            try {
                val mode = _uiState.value.appSettings.autoControlMode
                top.msfxp.schedule.service.AudioModeControlService.start(context, true, mode)
                kotlinx.coroutines.delay(10_000L)
                top.msfxp.schedule.service.AudioModeControlService.start(context, false, mode)
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "testToggleModeImmediately failed", e)
            }
        }
    }

    // 设置 30 秒后后台定时测试勿扰/静音闹钟
    fun scheduleDelayed30sDndTest(context: android.content.Context) {
        viewModelScope.launch {
            val alarmManager = context.getSystemService(android.app.AlarmManager::class.java) ?: return@launch
            val startMillis = System.currentTimeMillis() + 30_000L
            val endMillis = startMillis + 30_000L

            val startIntent = android.content.Intent(context, top.msfxp.schedule.service.CourseAlarmReceiver::class.java).apply {
                putExtra(top.msfxp.schedule.service.CourseAlarmReceiver.EXTRA_DND_ACTION, top.msfxp.schedule.service.CourseAlarmReceiver.DND_ACTION_START)
            }
            val startPI = android.app.PendingIntent.getBroadcast(
                context,
                TEST_DND_PI_START,
                startIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val endIntent = android.content.Intent(context, top.msfxp.schedule.service.CourseAlarmReceiver::class.java).apply {
                putExtra(top.msfxp.schedule.service.CourseAlarmReceiver.EXTRA_DND_ACTION, top.msfxp.schedule.service.CourseAlarmReceiver.DND_ACTION_END)
            }
            val endPI = android.app.PendingIntent.getBroadcast(
                context,
                TEST_DND_PI_END,
                endIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            if (alarmManager.canScheduleExactAlarms()) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, startMillis, startPI)
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, endMillis, endPI)
                } catch (_: SecurityException) {
                    alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, startMillis, startPI)
                    alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, endMillis, endPI)
                }
            } else {
                alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, startMillis, startPI)
                alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, endMillis, endPI)
            }
        }
    }

    // 获取测试用课程字段
    private suspend fun getTestCoursePayload(context: android.content.Context): Triple<String, String, String> {
        val tableId = scheduleRepository.getOrCreateDefaultTableId()
        val courses = scheduleRepository.getAllCoursesWithEventsOnce(tableId)
        val first = courses.firstOrNull()
        val firstEvent = first?.events?.firstOrNull()
        val name = first?.course?.name ?: context.getString(top.msfxp.schedule.R.string.test_course_default_name)
        val position = firstEvent?.location ?: context.getString(top.msfxp.schedule.R.string.test_course_default_location)
        val teacher = firstEvent?.teacher ?: context.getString(top.msfxp.schedule.R.string.test_course_default_teacher)
        return Triple(name, position, teacher)
    }

    companion object {
        private const val TEST_NOTIFICATION_ID_IMMEDIATE = 99999
        private const val TEST_NOTIFICATION_ID_DELAYED = 99998
        private const val TEST_DND_PI_START = 99996
        private const val TEST_DND_PI_END = 99997
    }
}
