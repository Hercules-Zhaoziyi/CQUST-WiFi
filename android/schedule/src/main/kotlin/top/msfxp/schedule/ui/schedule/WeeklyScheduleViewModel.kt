package top.msfxp.schedule.ui.schedule

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import top.msfxp.schedule.BuildConfig
import top.msfxp.schedule.R
import top.msfxp.schedule.data.api.AppRelease
import top.msfxp.schedule.data.api.CqustSyncManager
import top.msfxp.schedule.data.api.LocalCrypto
import top.msfxp.schedule.data.api.UpdateCheckService
import top.msfxp.schedule.data.model.*
import top.msfxp.schedule.data.repository.ScheduleRepository
import top.msfxp.schedule.data.repository.applyAdjustmentsToWeekEvents
import top.msfxp.schedule.data.repository.SettingsRepository
import top.msfxp.schedule.util.AppActionHelper
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

// 周课表主界面状态
data class WeeklyScheduleUiState(
    val isReady: Boolean = false,
    val isLoggedIn: Boolean = false,
    val semesterStartDate: LocalDate? = SemesterDateHelper.calculateDefaultStartDate(),
    val totalWeeks: Int = 20,
    val currentWeekNumber: Int = SemesterDateHelper.calculateWeekNumber(),
    val currentSemesterId: String = "",
    val isSemesterSet: Boolean = false,
    val isVacation: Boolean = false,
    val timeSlots: List<TimeSlot> = DefaultTimeSlots,
    val weekBlocksMap: Map<Int, List<MergedCourseBlock>> = emptyMap(),
    val weekUnarrangedMap: Map<Int, List<CourseEventWithMeta>> = emptyMap(),
    val weekCourseCountMap: Map<Int, Int> = emptyMap(),
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val hasCustomWallpaper: Boolean = false,
    val wallpaperMaskDim: Float = 0.15f,
    val courseCardAlpha: Float = 0.95f,
    val wallpaperFile: java.io.File? = null,
    val courseColors: List<CourseColor> = DefaultCourseColors
)

// 周课表业务视图模型
class WeeklyScheduleViewModel(
    private val context: Context,
    private val scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository,
    private val cqustSyncManager: CqustSyncManager
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<String?>(null)

    private val _updateRelease = MutableStateFlow<AppRelease?>(null)
    val updateRelease: StateFlow<AppRelease?> = _updateRelease.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    init {
        // 启动时确保默认课表存在、补全学期开学日期、后台静默同步课表与冷却静默检查更新
        viewModelScope.launch(Dispatchers.IO) {
            scheduleRepository.getOrCreateDefaultTableId()
            val currentSemester = scheduleRepository.getCurrentSemesterOnce()
            if (currentSemester != null && currentSemester.startDate.isBlank()) {
                scheduleRepository.updateSemesterStartDate(currentSemester.id, currentSemester.effectiveStartDate.toString())
            }
            cqustSyncManager.silentSyncIfNeeded(cooldownHours = 12L)

            // 间隔 24 小时自动静默检查新版本，发现新版本时在主课表首页主动呼起弹窗
            val newRelease = if(top.msfxp.schedule.integration.ScheduleIntegration.managedByHost)null else UpdateCheckService.silentCheckUpdateIfNeeded(
                settingsRepository = settingsRepository,
                localVersionName = BuildConfig.VERSION_NAME,
                cooldownHours = 24L
            )
            if (newRelease != null) {
                _updateRelease.value = newRelease
                _showUpdateDialog.value = true
            }
        }
    }

    private val syncStateFlow = combine(_isSyncing, _syncMessage) { syncing, msg ->
        Pair(syncing, msg)
    }

    // 随当前学期动态切换的调课 Flow
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val adjustmentsFlow = scheduleRepository.currentSemesterFlow.flatMapLatest { semester ->
        scheduleRepository.getAdjustmentsFlow(semesterId = semester?.id.orEmpty())
    }

    val uiState: StateFlow<WeeklyScheduleUiState> = combine(
        settingsRepository.appSettingsFlow,
        scheduleRepository.currentSemesterFlow,
        scheduleRepository.allCoursesFlow,
        adjustmentsFlow,
        syncStateFlow
    ) { settings, currentSemester, allCourses, adjustments, (syncing, syncMsg) ->
        val today = LocalDate.now()
        val totalWeeks = currentSemester?.totalWeeks ?: 20
        val parsedStart = currentSemester.effectiveStartDate

        val currWeek = SemesterDateHelper.calculateWeekNumber(
            startDate = parsedStart,
            targetDate = today,
            totalWeeks = totalWeeks
        )

        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val daysDiff = ChronoUnit.DAYS.between(parsedStart, thisMonday)
        val vacation = daysDiff < 0 || (daysDiff / 7) >= totalWeeks

        // 预聚合各周课程块与未排课项（融入调课补丁与跨周调入）
        val blocksMap = mutableMapOf<Int, List<MergedCourseBlock>>()
        val unarrangedMap = mutableMapOf<Int, List<CourseEventWithMeta>>()
        val countMap = mutableMapOf<Int, Int>()

        val allMetaEvents = allCourses.flatMap { c ->
            val allCourseWeeks = c.events.map { it.week }.filter { it > 0 }.distinct().sorted()
            c.events.filter { it.week in 1..totalWeeks && it.day in 1..7 && it.startSection > 0 }
                .map { ev -> ev.toMeta(c.course, allCourseWeeks) }
        }

        for (w in 1..totalWeeks) {
            val weekEvents = allCourses.flatMap { c ->
                val allCourseWeeks = c.events.map { it.week }.filter { it > 0 }.distinct().sorted()
                c.events.filter { it.week == w && it.day in 1..7 && it.startSection > 0 }
                    .map { ev -> ev.toMeta(c.course, allCourseWeeks) }
            }
            val weekUnarranged = allCourses.flatMap { c ->
                val allCourseWeeks = c.events.map { it.week }.filter { it > 0 }.distinct().sorted()
                c.events.filter { it.week == w && (it.day == 0 || it.startSection == 0) }
                    .map { ev -> ev.toMeta(c.course, allCourseWeeks) }
            }

            val adjustedWeekEvents = applyAdjustmentsToWeekEvents(weekEvents, adjustments, w, allMetaEvents)
            val adjustedUnarranged = weekUnarranged.filter { unarr ->
                val adj = adjustments.find { it.originalWeek == w && it.courseId == unarr.courseId && it.originalDay == 0 }
                adj == null || (!adj.isSuspended && adj.targetDay == 0)
            }

            countMap[w] = adjustedWeekEvents.size + adjustedUnarranged.size
            blocksMap[w] = adjustedWeekEvents.toMergedBlocks()
            unarrangedMap[w] = adjustedUnarranged
        }

        val wallpaperFile = settingsRepository.getWallpaperFile()
        val hasWallpaper = settings.hasCustomWallpaper && wallpaperFile.exists()
        val palette = parseCoursePaletteJson(settings.customCourseColorsJson)

        WeeklyScheduleUiState(
            isReady = true,
            isLoggedIn = settings.isLoggedIn && settings.studentId.isNotBlank(),
            semesterStartDate = parsedStart,
            totalWeeks = totalWeeks,
            currentWeekNumber = currWeek,
            isSemesterSet = true,
            isVacation = vacation,
            timeSlots = DefaultTimeSlots,
            currentSemesterId = currentSemester?.id.orEmpty(),
            weekBlocksMap = blocksMap,
            weekUnarrangedMap = unarrangedMap,
            weekCourseCountMap = countMap,
            isSyncing = syncing,
            syncMessage = syncMsg,
            hasCustomWallpaper = hasWallpaper,
            wallpaperMaskDim = settings.wallpaperMaskDim,
            courseCardAlpha = settings.courseCardAlpha,
            wallpaperFile = if (hasWallpaper) wallpaperFile else null,
            courseColors = palette
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = run {
            val initialSettings = settingsRepository.currentSettings
            val wallpaperFile = settingsRepository.getWallpaperFile()
            val hasWallpaper = initialSettings.hasCustomWallpaper && wallpaperFile.exists()
            WeeklyScheduleUiState(
                hasCustomWallpaper = hasWallpaper,
                wallpaperMaskDim = initialSettings.wallpaperMaskDim,
                courseCardAlpha = initialSettings.courseCardAlpha,
                wallpaperFile = if (hasWallpaper) wallpaperFile else null,
                courseColors = parseCoursePaletteJson(initialSettings.customCourseColorsJson)
            )
        }
    )

    val wallpaperBitmapFlow: StateFlow<androidx.compose.ui.graphics.ImageBitmap?> = settingsRepository.wallpaperBitmapFlow

    // 同步课表或触发登录回调
    fun onImportOrSyncClicked(onNeedLogin: () -> Unit) {
        viewModelScope.launch {
            val settings = settingsRepository.getAppSettingsOnce()
            if (!settings.isLoggedIn || settings.studentId.isBlank() || settings.passwordEncrypted.isBlank()) {
                onNeedLogin()
                return@launch
            }

            _isSyncing.value = true
            val rawPassword = LocalCrypto.decrypt(settings.passwordEncrypted)
            val currentSemester = scheduleRepository.getCurrentSemesterOnce()
            val result = cqustSyncManager.syncCourses(
                studentId = settings.studentId,
                passwordRaw = rawPassword,
                targetSemesterId = currentSemester?.id
            )
            _isSyncing.value = false
            if (result.isSuccess) {
                val summary = result.getOrNull()
                _syncMessage.value = if (summary != null) {
                    context.getString(R.string.sync_success_format, summary.regularCount, summary.practicalCount)
                } else {
                    context.getString(R.string.calendar_sync_success_toast)
                }
            } else {
                _syncMessage.value = result.exceptionOrNull()?.message ?: context.getString(R.string.sync_failed_default)
            }
        }
    }

    // 清除同步提示信息
    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    // 更新当前学期开学日期
    fun setSemesterStartDate(date: LocalDate) {
        viewModelScope.launch {
            val current = scheduleRepository.getCurrentSemesterOnce()
            if (current != null) {
                scheduleRepository.updateSemesterStartDate(current.id, date.toString())
            }
        }
    }

    // 更新课程配色槽位
    fun updateCourseColor(courseId: String, newColorIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val tableId = scheduleRepository.getOrCreateDefaultTableId()
            scheduleRepository.updateCourseColor(tableId, courseId, newColorIndex)
        }
    }

    // 全部调课记录流（随当前学期自动切换）
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val adjustmentsListFlow: StateFlow<List<CourseAdjustmentWithMeta>> =
        scheduleRepository.currentSemesterFlow.flatMapLatest { semester ->
            scheduleRepository.getAllAdjustmentsWithMetaFlow(semesterId = semester?.id.orEmpty())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 一键清除所有调课记录
    fun clearAllAdjustments() {
        viewModelScope.launch(Dispatchers.IO) {
            scheduleRepository.clearAllAdjustments()
        }
    }

    // 移动课程（目标坐标不能有课）
    fun moveCourse(adjustment: CourseAdjustment) {
        viewModelScope.launch(Dispatchers.IO) {
            scheduleRepository.moveCourse(adjustment)
        }
    }

    // 调换两门课程（目标坐标必须有课，两课互换时段）
    fun swapCourses(adjA: CourseAdjustment, adjB: CourseAdjustment) {
        viewModelScope.launch(Dispatchers.IO) {
            scheduleRepository.swapCourses(adjA, adjB)
        }
    }

    // 标记删除课程（本周停课）
    fun markCourseDeleted(adjustment: CourseAdjustment) {
        viewModelScope.launch(Dispatchers.IO) {
            scheduleRepository.markCourseDeleted(adjustment)
        }
    }

    // 保存调课通用入口
    fun saveAdjustment(adjustment: CourseAdjustment) {
        viewModelScope.launch(Dispatchers.IO) {
            scheduleRepository.saveAdjustment(adjustment)
        }
    }

    // 删除调课设置（恢复原课）
    fun deleteAdjustment(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            scheduleRepository.deleteAdjustment(id)
        }
    }

    // 查找特定事件的调课记录
    suspend fun getAdjustmentForEvent(
        courseId: String,
        week: Int,
        day: Int,
        startSection: Int
    ): CourseAdjustment? {
        val tableId = scheduleRepository.getOrCreateDefaultTableId()
        val semId = scheduleRepository.getCurrentSemesterOnce()?.id.orEmpty()
        return scheduleRepository.getAdjustmentForEvent(tableId, semId, courseId, week, day, startSection)
    }

    // 检索指定周次、星期与节次上是否存在其他课程（用于校验移动与调换条件）
    fun getCourseAtSlot(
        week: Int,
        day: Int,
        startSection: Int,
        endSection: Int,
        excludeCourseId: String,
        excludeStartSection: Int
    ): CourseEventWithMeta? {
        val blocks = uiState.value.weekBlocksMap[week] ?: return null
        for (block in blocks) {
            if (block.day == day) {
                val overlaps = maxOf(startSection, block.startSection) <= minOf(endSection, block.endSection)
                if (overlaps) {
                    val found = block.events.find { ev ->
                        !(ev.courseId == excludeCourseId && ev.startSection == excludeStartSection)
                    }
                    if (found != null) return found
                }
            }
        }
        return null
    }

    // 关闭更新弹窗
    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    // 执行安装包下载操作
    fun executeDownload(
        context: Context,
        onNotify: (String) -> Unit
    ) {
        val release = _updateRelease.value ?: return
        dismissUpdateDialog()
        AppActionHelper.processReleaseDownload(context, release, onNotify)
    }
}
