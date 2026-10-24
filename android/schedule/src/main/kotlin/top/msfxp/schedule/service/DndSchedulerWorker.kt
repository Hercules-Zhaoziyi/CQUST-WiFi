package top.msfxp.schedule.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import top.msfxp.schedule.data.model.CourseAdjustment
import top.msfxp.schedule.data.model.CourseEventWithMeta
import top.msfxp.schedule.data.model.DefaultTimeSlots
import top.msfxp.schedule.data.model.SemesterDateHelper
import top.msfxp.schedule.data.model.effectiveStartDate
import top.msfxp.schedule.data.repository.ScheduleRepository
import top.msfxp.schedule.data.repository.SettingsRepository
import top.msfxp.schedule.data.repository.applyAdjustmentsToWeekEvents
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

// 定时注册每日课前提醒与上课免打扰闹钟
class DndSchedulerWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams), KoinComponent {

    private val scheduleRepository: ScheduleRepository by inject()
    private val settingsRepository: SettingsRepository by inject()

    companion object {
        private const val WORK_NAME = "cqust_daily_dnd_scheduler"
        private const val IMMEDIATE_WORK_NAME = "cqust_immediate_dnd_scheduler"

        private const val MAX_SECTION_COUNT = 12

        // 注册后台周期性调度任务
        fun enqueuePeriodicWork(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<DndSchedulerWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        // 立即触发一次全量课程闹钟扫描与注册
        fun triggerImmediately(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<DndSchedulerWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        // 计算基于节次时隙的唯一请求码
        fun buildRequestCode(tag: String, date: LocalDate, section: Int): Int {
            return ("${tag}_${date}_$section").hashCode() and 0x7FFFFFFF
        }
    }

    // 课表调度运行环境数据
    private data class ScheduleContext(
        val semester: top.msfxp.schedule.data.model.Semester,
        val allEvents: List<CourseEventWithMeta>,
        val adjustments: List<CourseAdjustment>,
        val settings: top.msfxp.schedule.data.model.AppSettings
    )

    // 扫描今明两天课程并注册精确闹钟
    override suspend fun doWork(): Result {
        return try {
            val settings = settingsRepository.getAppSettingsOnce()
            val currentSemester = scheduleRepository.getCurrentSemesterOnce() ?: return Result.success()
            val tableId = scheduleRepository.getOrCreateDefaultTableId()
            val allEvents = scheduleRepository.getAllEventsForTable(tableId)
            val adjustments = scheduleRepository.getAdjustmentsOnce(tableId, semesterId = currentSemester.id)
            val alarmManager = context.getSystemService<AlarmManager>() ?: return Result.success()

            val scheduleContext = ScheduleContext(
                semester = currentSemester,
                allEvents = allEvents,
                adjustments = adjustments,
                settings = settings
            )

            val today = LocalDate.now()
            for (targetDate in (0L..6L).map { today.plusDays(it) }) {
                syncAlarmsForDate(targetDate, scheduleContext, alarmManager)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    // 同步指定日期所有节次的时隙闹钟（有课更新，无课注销）
    private fun syncAlarmsForDate(
        targetDate: LocalDate,
        scheduleContext: ScheduleContext,
        alarmManager: AlarmManager
    ) {
        val semester = scheduleContext.semester
        val weekNumber = SemesterDateHelper.calculateWeekNumber(semester.effectiveStartDate, targetDate, semester.totalWeeks)
        if (weekNumber !in 1..semester.totalWeeks) {
            cancelAllSlotsForDate(alarmManager, targetDate)
            return
        }

        val dayOfWeek = targetDate.dayOfWeek.value
        val weekEvents = scheduleContext.allEvents.filter { it.week == weekNumber && it.day in 1..7 && it.startSection > 0 }
        val adjustedWeekEvents = applyAdjustmentsToWeekEvents(weekEvents, scheduleContext.adjustments, weekNumber, scheduleContext.allEvents)
        val dayEvents = adjustedWeekEvents.filter { it.day == dayOfWeek }

        val startEvents = dayEvents.associateBy { it.startSection }
        val endEvents = dayEvents.associateBy { it.endSection }
        val settings = scheduleContext.settings

        for (section in 1..MAX_SECTION_COUNT) {
            val startEvent = startEvents[section]
            if (startEvent != null && settings.reminderEnabled) {
                scheduleReminder(startEvent, targetDate, settings.remindBeforeMinutes, alarmManager)
            } else {
                cancelAlarm(alarmManager, buildRequestCode("REMIND", targetDate, section))
            }

            if (startEvent != null && settings.autoDndEnabled) {
                scheduleDndStart(startEvent, targetDate, alarmManager)
            } else {
                cancelAlarm(alarmManager, buildRequestCode("DND_START", targetDate, section))
            }

            val endEvent = endEvents[section]
            if (endEvent != null && settings.autoDndEnabled) {
                scheduleDndEnd(endEvent, targetDate, alarmManager)
            } else {
                cancelAlarm(alarmManager, buildRequestCode("DND_END", targetDate, section))
            }
        }
    }

    // 注册单门课程的课前提醒闹钟
    private fun scheduleReminder(
        event: CourseEventWithMeta,
        targetDate: LocalDate,
        remindMinutes: Int,
        alarmManager: AlarmManager
    ) {
        val slot = DefaultTimeSlots.find { it.sectionNumber == event.startSection }
        val startTime = parseSlotTime(slot?.startTime, LocalTime.of(8, 30))
        val triggerMillis = LocalDateTime.of(targetDate, startTime)
            .minusMinutes(remindMinutes.toLong())
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (triggerMillis <= System.currentTimeMillis()) return

        val reqCode = buildRequestCode("REMIND", targetDate, event.startSection)
        val notifyId = buildRequestCode("NOTIFY", targetDate, event.startSection)
        val intent = Intent(context, CourseAlarmReceiver::class.java).apply {
            putExtra(CourseAlarmReceiver.EXTRA_COURSE_NAME, event.inlineTitle)
            putExtra(CourseAlarmReceiver.EXTRA_COURSE_POSITION, event.location)
            putExtra(CourseAlarmReceiver.EXTRA_COURSE_TEACHER, event.teacher)
            putExtra(CourseAlarmReceiver.EXTRA_NOTIFICATION_ID, notifyId)
            putExtra(CourseAlarmReceiver.EXTRA_SECTION, event.startSection)
            putExtra(CourseAlarmReceiver.EXTRA_DATE, targetDate.toString())
        }
        setExactAlarm(alarmManager, triggerMillis, createPendingIntent(reqCode, intent))
    }

    // 注册课堂免打扰开启闹钟
    private fun scheduleDndStart(
        event: CourseEventWithMeta,
        targetDate: LocalDate,
        alarmManager: AlarmManager
    ) {
        val slot = DefaultTimeSlots.find { it.sectionNumber == event.startSection }
        val startTime = parseSlotTime(slot?.startTime, LocalTime.of(8, 30))
        val triggerMillis = LocalDateTime.of(targetDate, startTime)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (triggerMillis <= System.currentTimeMillis()) return

        val reqCode = buildRequestCode("DND_START", targetDate, event.startSection)
        val intent = Intent(context, CourseAlarmReceiver::class.java).apply {
            putExtra(CourseAlarmReceiver.EXTRA_DND_ACTION, CourseAlarmReceiver.DND_ACTION_START)
            putExtra(CourseAlarmReceiver.EXTRA_SECTION, event.startSection)
            putExtra(CourseAlarmReceiver.EXTRA_DATE, targetDate.toString())
        }
        setExactAlarm(alarmManager, triggerMillis, createPendingIntent(reqCode, intent))
    }

    // 注册课堂免打扰恢复闹钟
    private fun scheduleDndEnd(
        event: CourseEventWithMeta,
        targetDate: LocalDate,
        alarmManager: AlarmManager
    ) {
        val slot = DefaultTimeSlots.find { it.sectionNumber == event.endSection }
        val endTime = parseSlotTime(slot?.endTime, LocalTime.of(9, 15))
        val triggerMillis = LocalDateTime.of(targetDate, endTime)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (triggerMillis <= System.currentTimeMillis()) return

        val reqCode = buildRequestCode("DND_END", targetDate, event.endSection)
        val intent = Intent(context, CourseAlarmReceiver::class.java).apply {
            putExtra(CourseAlarmReceiver.EXTRA_DND_ACTION, CourseAlarmReceiver.DND_ACTION_END)
        }
        setExactAlarm(alarmManager, triggerMillis, createPendingIntent(reqCode, intent))
    }

    // 注销指定日期的所有时隙闹钟
    private fun cancelAllSlotsForDate(alarmManager: AlarmManager, targetDate: LocalDate) {
        for (section in 1..MAX_SECTION_COUNT) {
            cancelAlarm(alarmManager, buildRequestCode("REMIND", targetDate, section))
            cancelAlarm(alarmManager, buildRequestCode("DND_START", targetDate, section))
            cancelAlarm(alarmManager, buildRequestCode("DND_END", targetDate, section))
        }
    }

    // 注销指定 RequestCode 的 PendingIntent 闹钟
    private fun cancelAlarm(alarmManager: AlarmManager, requestCode: Int) {
        val intent = Intent(context, CourseAlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    // 构造唯一 PendingIntent 广播
    private fun createPendingIntent(requestCode: Int, intent: Intent): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // 解析节次时间字符串
    private fun parseSlotTime(timeStr: String?, defaultTime: LocalTime): LocalTime {
        if (timeStr.isNullOrBlank()) return defaultTime
        return try {
            LocalTime.parse(timeStr)
        } catch (_: Exception) {
            defaultTime
        }
    }

    // 设置精确或低电耗定时闹钟
    private fun setExactAlarm(alarmManager: AlarmManager, triggerMillis: Long, operation: PendingIntent) {
        if (alarmManager.canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, operation)
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, operation)
            }
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, operation)
        }
    }
}
