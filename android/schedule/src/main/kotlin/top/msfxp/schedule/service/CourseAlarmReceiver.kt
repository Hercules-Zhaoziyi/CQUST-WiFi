package top.msfxp.schedule.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.AutoControlMode
import top.msfxp.schedule.data.model.SemesterDateHelper
import top.msfxp.schedule.data.model.effectiveStartDate
import top.msfxp.schedule.data.repository.ScheduleRepository
import top.msfxp.schedule.data.repository.SettingsRepository
import top.msfxp.schedule.data.repository.applyAdjustmentsToWeekEvents
import top.msfxp.schedule.widget.updateAllAppWidgets
import java.time.LocalDate
import java.time.format.DateTimeParseException

// 课程闹钟与免打扰/静音广播接收器
class CourseAlarmReceiver : BroadcastReceiver(), KoinComponent {

    private val settingsRepository: SettingsRepository by inject()
    private val scheduleRepository: ScheduleRepository by inject()

    companion object {
        private const val TAG = "CourseAlarmReceiver"
        const val NOTIFICATION_CHANNEL_ID = "cqust_course_reminder_channel_v2"
        const val EXTRA_COURSE_NAME = "extra_course_name"
        const val EXTRA_COURSE_POSITION = "extra_course_position"
        const val EXTRA_COURSE_TEACHER = "extra_course_teacher"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        const val EXTRA_SECTION = "extra_section"
        const val EXTRA_DATE = "extra_date"

        const val EXTRA_DND_ACTION = "extra_dnd_action"
        const val DND_ACTION_START = "dnd_action_start"
        const val DND_ACTION_END = "dnd_action_end"

        const val ACTION_DISMISS_NOTIFICATION = "top.msfxp.schedule.ACTION_DISMISS_NOTIFICATION"
        private const val PREFS_AUTO_SILENT_STATE = "cqust_auto_silent_state"
        private const val KEY_PREV_RINGER_MODE = "prev_ringer_mode"
        private const val KEY_WAS_MUTED_BY_APP = "was_muted_by_app"

        // 切换系统免打扰或静音模式
        fun toggleMode(context: Context, isEnabled: Boolean, modeType: AutoControlMode) {
            val audioManager = context.getSystemService<AudioManager>()
            val notificationManager = context.getSystemService<NotificationManager>()
            if (audioManager == null || notificationManager == null) return
            if (!notificationManager.isNotificationPolicyAccessGranted) return

            when (modeType) {
                AutoControlMode.DND -> {
                    try {
                        notificationManager.setInterruptionFilter(
                            if (isEnabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                            else NotificationManager.INTERRUPTION_FILTER_ALL
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Toggle DND filter failed", e)
                    }
                }
                AutoControlMode.SILENT -> {
                    try {
                        val sp = context.getSharedPreferences(PREFS_AUTO_SILENT_STATE, Context.MODE_PRIVATE)
                        if (isEnabled) {
                            val currentMode = audioManager.ringerMode
                            if (currentMode != AudioManager.RINGER_MODE_SILENT) {
                                sp.edit()
                                    .putInt(KEY_PREV_RINGER_MODE, currentMode)
                                    .putBoolean(KEY_WAS_MUTED_BY_APP, true)
                                    .apply()
                            } else {
                                sp.edit().putBoolean(KEY_WAS_MUTED_BY_APP, false).apply()
                            }
                            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                            try {
                                audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
                                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                            } catch (e: Exception) {
                                Log.w(TAG, "Auxiliary stream mute failed: ${e.message}")
                            }
                        } else {
                            val wasMutedByApp = sp.getBoolean(KEY_WAS_MUTED_BY_APP, false)
                            if (wasMutedByApp) {
                                val prevMode = sp.getInt(KEY_PREV_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
                                audioManager.ringerMode = prevMode
                                try {
                                    audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
                                    audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Auxiliary stream unmute failed: ${e.message}")
                                }
                            }
                            sp.edit().remove(KEY_PREV_RINGER_MODE).remove(KEY_WAS_MUTED_BY_APP).apply()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Toggle silent mode failed", e)
                    }
                }
            }
        }

        // 确保上课提醒通知渠道已初始化
        private fun ensureNotificationChannel(context: Context, nm: NotificationManager) {
            if (nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.item_course_reminder),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.reminder_switch_desc)
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            nm.createNotificationChannel(channel)
        }

        // 构建并展示上课提醒系统通知
        fun showNotification(
            context: Context,
            notificationId: Int,
            name: String,
            position: String,
            teacher: String
        ) {
            val nm = context.getSystemService<NotificationManager>() ?: return
            ensureNotificationChannel(context, nm)

            val liveStatusText = context.getString(R.string.notification_live_status_preparing)
            val locationText = if (position.isNotBlank()) context.getString(R.string.notification_location_format, position) else ""
            val teacherText = if (teacher.isNotBlank()) context.getString(R.string.notification_teacher_format, teacher) else ""
            val classStartingText = context.getString(R.string.notification_class_starting)
            val bigTextContent = listOf(locationText, teacherText).filter { it.isNotBlank() }.joinToString("\n")
            val shortContentText = listOfNotNull(
                position.takeIf { it.isNotBlank() },
                teacher.takeIf { it.isNotBlank() }
            ).joinToString(" · ").ifBlank { classStartingText }

            val bigTextStyle = NotificationCompat.BigTextStyle()
                .setBigContentTitle(name)
                .bigText(if (bigTextContent.isNotBlank()) bigTextContent else classStartingText)
                .setSummaryText(liveStatusText)

            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(name)
                .setContentText(shortContentText)
                .setStyle(bigTextStyle)
                .setAutoCancel(true)
                .setOngoing(false)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)

            if (Build.VERSION.SDK_INT >= 36) {
                builder.setRequestPromotedOngoing(true)
                builder.setShortCriticalText(liveStatusText)
            }

            nm.notify(notificationId, builder.build())
        }
    }

    // 接收闹钟广播并分发对应业务
    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        val currentIntent = intent ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (currentIntent.action) {
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        DndSchedulerWorker.triggerImmediately(ctx)
                        updateAllAppWidgets(ctx)
                        return@launch
                    }
                    ACTION_DISMISS_NOTIFICATION -> {
                        handleDismissNotification(ctx, currentIntent)
                        return@launch
                    }
                }

                val settings = settingsRepository.getAppSettingsOnce()
                val actionType = currentIntent.getStringExtra(EXTRA_DND_ACTION)

                if (actionType != null) {
                    if (settings.autoDndEnabled) {
                        handleDndAction(ctx, currentIntent, actionType, settings.autoControlMode)
                    }
                } else if (settings.reminderEnabled) {
                    val notifyId = currentIntent.getIntExtra(EXTRA_NOTIFICATION_ID, 1001)
                    handleCourseReminder(ctx, currentIntent, notifyId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Process alarm broadcast failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    // 校验指定日期的节次当前是否仍有有效课程开始
    private suspend fun hasActiveCourseAtSlot(dateStr: String?, section: Int): Boolean {
        if (section <= 0) return true
        val date = try {
            if (dateStr.isNullOrBlank()) LocalDate.now() else LocalDate.parse(dateStr)
        } catch (_: DateTimeParseException) {
            LocalDate.now()
        }

        val semester = scheduleRepository.getCurrentSemesterOnce() ?: return false
        val weekNumber = SemesterDateHelper.calculateWeekNumber(semester.effectiveStartDate, date, semester.totalWeeks)
        if (weekNumber !in 1..semester.totalWeeks) return false

        val tableId = scheduleRepository.getOrCreateDefaultTableId()
        val allEvents = scheduleRepository.getAllEventsForTable(tableId)
        val weekEvents = allEvents.filter { it.week == weekNumber && it.day == date.dayOfWeek.value && it.startSection > 0 }
        if (weekEvents.isEmpty()) return false

        val adjustments = scheduleRepository.getAdjustmentsOnce(tableId, semester.id)
        val adjustedEvents = applyAdjustmentsToWeekEvents(weekEvents, adjustments, weekNumber, allEvents)
        return adjustedEvents.any { it.startSection == section }
    }

    // 处理通知清除
    private fun handleDismissNotification(context: Context, intent: Intent) {
        val notifyId = intent.getIntExtra("target_notification_id", -1)
        if (notifyId != -1) {
            val nm = context.getSystemService<NotificationManager>()
            nm?.cancel(notifyId)
        }
    }

    // 处理免打扰/静音启闭切换与小组件刷新
    private suspend fun handleDndAction(context: Context, intent: Intent, actionType: String, mode: AutoControlMode) {
        when (actionType) {
            DND_ACTION_START -> {
                val section = intent.getIntExtra(EXTRA_SECTION, -1)
                val dateStr = intent.getStringExtra(EXTRA_DATE)
                if (!hasActiveCourseAtSlot(dateStr, section)) return
                AudioModeControlService.start(context, true, mode)
            }
            DND_ACTION_END -> {
                AudioModeControlService.start(context, false, mode)
            }
        }
    }

    // 处理上课横幅通知展示与小组件刷新
    private suspend fun handleCourseReminder(context: Context, intent: Intent, notifyId: Int) {
        val section = intent.getIntExtra(EXTRA_SECTION, -1)
        val dateStr = intent.getStringExtra(EXTRA_DATE)
        if (!hasActiveCourseAtSlot(dateStr, section)) return

        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME) ?: context.getString(R.string.notification_unknown_course)
        val position = intent.getStringExtra(EXTRA_COURSE_POSITION) ?: context.getString(R.string.notification_unknown_position)
        val teacher = intent.getStringExtra(EXTRA_COURSE_TEACHER) ?: ""

        showNotification(context, notifyId, courseName, position, teacher)
        updateAllAppWidgets(context)
    }
}
