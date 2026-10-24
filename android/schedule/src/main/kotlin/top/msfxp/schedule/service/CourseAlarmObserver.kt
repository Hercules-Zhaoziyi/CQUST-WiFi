package top.msfxp.schedule.service

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import top.msfxp.schedule.data.model.AutoControlMode
import top.msfxp.schedule.data.repository.ScheduleRepository
import top.msfxp.schedule.data.repository.SettingsRepository
import top.msfxp.schedule.widget.updateAllAppWidgets

// 课表数据与偏好监听器
class CourseAlarmObserver(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val scheduleRepository: ScheduleRepository,
    private val calendarSyncHelper: CalendarSyncHelper,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    // 自动化调度与小组件触发参数
    private data class AutomationTriggerParams(
        val reminderEnabled: Boolean,
        val remindBeforeMinutes: Int,
        val autoDndEnabled: Boolean,
        val autoControlMode: AutoControlMode,
        val semesterId: String?,
        val semesterStartDate: String?,
        val totalWeeks: Int?,
        val coursesHash: Int,
        val adjustmentsHash: Int
    )

    // 纯课表课程排课数据参数（仅用于日历同步）
    private data class CourseScheduleDataParams(
        val semesterId: String?,
        val semesterStartDate: String?,
        val totalWeeks: Int?,
        val coursesHash: Int,
        val adjustmentsHash: Int
    )

    // 启动响应式监听管道
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun startObserving() {
        val adjustmentsWithSemesterFlow = scheduleRepository.currentSemesterFlow.flatMapLatest { semester ->
            scheduleRepository.getAllAdjustmentsWithMetaFlow(semesterId = semester?.id.orEmpty())
        }

        // 1. 课前提醒与免打扰自动化排程、小组件刷新管道
        combine(
            settingsRepository.appSettingsFlow,
            scheduleRepository.currentSemesterFlow,
            scheduleRepository.allCoursesFlow,
            adjustmentsWithSemesterFlow
        ) { settings, semester, courses, adjustments ->
            AutomationTriggerParams(
                reminderEnabled = settings.reminderEnabled,
                remindBeforeMinutes = settings.remindBeforeMinutes,
                autoDndEnabled = settings.autoDndEnabled,
                autoControlMode = settings.autoControlMode,
                semesterId = semester?.id,
                semesterStartDate = semester?.startDate,
                totalWeeks = semester?.totalWeeks,
                coursesHash = courses.hashCode(),
                adjustmentsHash = adjustments.hashCode()
            )
        }
            .distinctUntilChanged()
            .debounce(500L)
            .onEach {
                DndSchedulerWorker.triggerImmediately(context)
                updateAllAppWidgets(context)
            }
            .launchIn(scope)

        // 2. 日历自动同步管道：仅在课表课程排课数据发生变动时触发
        combine(
            scheduleRepository.currentSemesterFlow,
            scheduleRepository.allCoursesFlow,
            adjustmentsWithSemesterFlow
        ) { semester, courses, adjustments ->
            CourseScheduleDataParams(
                semesterId = semester?.id,
                semesterStartDate = semester?.startDate,
                totalWeeks = semester?.totalWeeks,
                coursesHash = courses.hashCode(),
                adjustmentsHash = adjustments.hashCode()
            )
        }
            .distinctUntilChanged()
            .drop(1)
            .debounce(500L)
            .onEach {
                val settings = settingsRepository.getAppSettingsOnce()
                if (settings.autoSyncToCalendar && calendarSyncHelper.hasCalendarPermission()) {
                    calendarSyncHelper.syncCurrentScheduleToCalendar()
                }
            }
            .launchIn(scope)
    }
}
