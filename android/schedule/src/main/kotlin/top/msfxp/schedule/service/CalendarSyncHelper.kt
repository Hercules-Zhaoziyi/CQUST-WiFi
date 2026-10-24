package top.msfxp.schedule.service

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.DefaultTimeSlots
import top.msfxp.schedule.data.model.effectiveStartDate
import top.msfxp.schedule.data.repository.ScheduleRepository
import top.msfxp.schedule.data.repository.SettingsRepository
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

// 系统日历同步助手
class CalendarSyncHelper(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val scheduleRepository: ScheduleRepository
) {
    companion object {
        private const val LEGACY_CALENDAR_ACCOUNT_NAME = "cqust_schedule_account"
        private const val CALENDAR_ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL
        private const val BATCH_OPERATION_LIMIT = 200
    }

    // 日历账号使用当前应用包名
    private val accountName: String get() = context.packageName
    private val calendarDisplayName: String get() = context.getString(R.string.app_name)
    private val syncMutex = Mutex()

    // 检查日历读写权限
    fun hasCalendarPermission(): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    // 将当前课表增量同步至系统日历
    suspend fun syncCurrentScheduleToCalendar(): Boolean = withContext(Dispatchers.IO) {
        if (!hasCalendarPermission()) return@withContext false

        syncMutex.withLock {
            try {
                val settings = settingsRepository.getAppSettingsOnce()
                val currentSemester = scheduleRepository.getCurrentSemesterOnce() ?: return@withLock false
                val totalWeeks = currentSemester.totalWeeks
                val semesterStartDate = currentSemester.effectiveStartDate

                val tableId = scheduleRepository.getOrCreateDefaultTableId()
                val events = scheduleRepository.getAllEventsForTable(tableId)
                val slots = DefaultTimeSlots

                val calendarId = getOrCreateCalendarId()
                if (calendarId == -1L) return@withLock false

                val adjustments = scheduleRepository.getAdjustmentsOnce(tableId, semesterId = currentSemester.id)
                val zoneId = ZoneId.systemDefault()
                val calendarRemindMinutes = settings.calendarRemindBeforeMinutes
                val desiredList = mutableListOf<DesiredCalendarEvent>()
                val usedKeys = mutableSetOf<String>()

                for (w in 1..totalWeeks) {
                    val weekRawEvents = events.filter { it.week == w && it.day in 1..7 && it.startSection > 0 }
                    val weekAdjustedEvents = top.msfxp.schedule.data.repository.applyAdjustmentsToWeekEvents(weekRawEvents, adjustments, w, events)

                    weekAdjustedEvents.forEach { ev ->
                        val startSlot = slots.find { it.sectionNumber == ev.startSection }
                        val endSlot = slots.find { it.sectionNumber == ev.endSection } ?: startSlot

                        val startTime = startSlot?.startTime ?: "08:30"
                        val endTime = endSlot?.endTime ?: "09:15"

                        val parsedStartTime = try { LocalTime.parse(startTime) } catch (_: Exception) { LocalTime.of(8, 30) }
                        val parsedEndTime = try { LocalTime.parse(endTime) } catch (_: Exception) { LocalTime.of(9, 15) }

                        val daysOffset = (w - 1) * 7L + (ev.day - 1)
                        val eventDate = semesterStartDate.plusDays(daysOffset)

                        val startDateTime = LocalDateTime.of(eventDate, parsedStartTime)
                        val endDateTime = LocalDateTime.of(eventDate, parsedEndTime)

                        val startMillis = startDateTime.atZone(zoneId).toInstant().toEpochMilli()
                        val endMillis = endDateTime.atZone(zoneId).toInstant().toEpochMilli()

                        val descParts = mutableListOf<String>()
                        if (ev.isAdjusted) descParts.add(context.getString(R.string.calendar_event_desc_adjusted))
                        if (ev.teacher.isNotBlank()) descParts.add(context.getString(R.string.calendar_event_desc_teacher_format, ev.teacher))
                        val desc = descParts.joinToString("\n")

                        val cleanProject = ev.projectName.trim().replace(Regex("[^a-zA-Z0-9_\\u4e00-\\u9fa5]"), "_")
                        val baseKey = "cqust_${tableId}_w${w}_d${ev.day}_s${ev.startSection}_c${ev.courseId}_p${cleanProject}"
                        var finalKey = baseKey
                        var counter = 1
                        while (finalKey in usedKeys) {
                            finalKey = "${baseKey}_${counter++}"
                        }
                        usedKeys.add(finalKey)

                        desiredList.add(
                            DesiredCalendarEvent(
                                syncKey = finalKey,
                                title = ev.calendarTitle,
                                location = ev.location,
                                description = desc,
                                startMillis = startMillis,
                                endMillis = endMillis,
                                remindMinutes = calendarRemindMinutes
                            )
                        )
                    }
                }

                // 精细化增量同步，不全量删除已存在的事件
                incrementalSyncCalendarEvents(calendarId, desiredList)
            } catch (e: Exception) {
                android.util.Log.e("CalendarSyncHelper", "Sync schedule to calendar failed", e)
                false
            }
        }
    }

    // 精细化增量更新日历事件与关联提醒
    private fun incrementalSyncCalendarEvents(
        calendarId: Long,
        desiredList: List<DesiredCalendarEvent>
    ): Boolean {
        val existingList = queryExistingEvents(calendarId)
        val existingIds = existingList.map { it.id }.toSet()
        val existingRemindersMap = queryExistingReminders(existingIds)

        val desiredByKey = desiredList.associateBy { it.syncKey }
        val desiredBySig = desiredList.associateBy { it.signature }

        val matchedExistingToDesired = mutableMapOf<ExistingCalendarEvent, DesiredCalendarEvent>()
        val matchedDesiredKeys = mutableSetOf<String>()
        val eventsToDelete = mutableListOf<Long>()

        for (existing in existingList) {
            if (!existing.syncKey.isNullOrBlank()) {
                val desired = desiredByKey[existing.syncKey]
                if (desired != null && existing.syncKey !in matchedDesiredKeys) {
                    matchedExistingToDesired[existing] = desired
                    matchedDesiredKeys.add(existing.syncKey)
                } else {
                    eventsToDelete.add(existing.id)
                }
            } else {
                val desired = desiredBySig[existing.signature]
                if (desired != null && desired.syncKey !in matchedDesiredKeys) {
                    matchedExistingToDesired[existing] = desired
                    matchedDesiredKeys.add(desired.syncKey)
                } else {
                    eventsToDelete.add(existing.id)
                }
            }
        }

        val eventsToInsert = desiredList.filter { it.syncKey !in matchedDesiredKeys }
        val eventsToUpdate = mutableListOf<Pair<ExistingCalendarEvent, DesiredCalendarEvent>>()
        val remindersToDelete = mutableListOf<Long>()
        val remindersToInsertForExisting = mutableListOf<Pair<Long, Int>>()

        for ((existing, desired) in matchedExistingToDesired) {
            val contentChanged = existing.title != desired.title ||
                    existing.location != desired.location ||
                    existing.description != desired.description ||
                    existing.startMillis != desired.startMillis ||
                    existing.endMillis != desired.endMillis ||
                    existing.syncKey != desired.syncKey

            if (contentChanged) {
                eventsToUpdate.add(existing to desired)
            }

            val reminders = existingRemindersMap[existing.id].orEmpty()
            if (desired.remindMinutes <= 0) {
                if (reminders.isNotEmpty()) {
                    remindersToDelete.addAll(reminders.map { it.id })
                }
            } else {
                if (reminders.size == 1 && reminders.first().minutes == desired.remindMinutes) {
                    // 提醒完全匹配，保持不变
                } else {
                    remindersToDelete.addAll(reminders.map { it.id })
                    remindersToInsertForExisting.add(existing.id to desired.remindMinutes)
                }
            }
        }

        // 无任何数据变动时直接返回，避免触发系统日历重新调度
        if (eventsToDelete.isEmpty() &&
            eventsToUpdate.isEmpty() &&
            eventsToInsert.isEmpty() &&
            remindersToDelete.isEmpty() &&
            remindersToInsertForExisting.isEmpty()
        ) {
            android.util.Log.d("CalendarSyncHelper", "Calendar sync: already up-to-date, 0 changes needed.")
            return true
        }

        android.util.Log.i(
            "CalendarSyncHelper",
            "Incremental sync diff: insert=${eventsToInsert.size}, update=${eventsToUpdate.size}, " +
                    "deleteEvents=${eventsToDelete.size}, updateReminders=${remindersToInsertForExisting.size}, deleteReminders=${remindersToDelete.size}"
        )

        val eventsUri = buildEventsUri()
        val remindersUri = buildRemindersUri()
        val timeZoneId = TimeZone.getDefault().id
        val ops = ArrayList<ContentProviderOperation>()

        // 1. 批量删除失效与重复事件
        for (id in eventsToDelete) {
            if (ops.size >= BATCH_OPERATION_LIMIT) executeBatch(ops)
            ops.add(
                ContentProviderOperation.newDelete(eventsUri)
                    .withSelection("${CalendarContract.Events._ID} = ?", arrayOf(id.toString()))
                    .build()
            )
        }

        // 2. 批量删除多余或待更新提醒
        for (id in remindersToDelete) {
            if (ops.size >= BATCH_OPERATION_LIMIT) executeBatch(ops)
            ops.add(
                ContentProviderOperation.newDelete(remindersUri)
                    .withSelection("${CalendarContract.Reminders._ID} = ?", arrayOf(id.toString()))
                    .build()
            )
        }

        // 3. 批量更新变动事件
        for ((existing, desired) in eventsToUpdate) {
            if (ops.size >= BATCH_OPERATION_LIMIT) executeBatch(ops)
            ops.add(
                ContentProviderOperation.newUpdate(eventsUri)
                    .withSelection("${CalendarContract.Events._ID} = ?", arrayOf(existing.id.toString()))
                    .withValue(CalendarContract.Events.TITLE, desired.title)
                    .withValue(CalendarContract.Events.EVENT_LOCATION, desired.location)
                    .withValue(CalendarContract.Events.DESCRIPTION, desired.description)
                    .withValue(CalendarContract.Events.DTSTART, desired.startMillis)
                    .withValue(CalendarContract.Events.DTEND, desired.endMillis)
                    .withValue(CalendarContract.Events.EVENT_TIMEZONE, timeZoneId)
                    .withValue(CalendarContract.Events._SYNC_ID, desired.syncKey)
                    .withValue(CalendarContract.Events.SYNC_DATA1, desired.syncKey)
                    .build()
            )
        }

        // 4. 批量为现有事件插入更新提醒
        for ((eventId, minutes) in remindersToInsertForExisting) {
            if (ops.size >= BATCH_OPERATION_LIMIT) executeBatch(ops)
            ops.add(
                ContentProviderOperation.newInsert(remindersUri)
                    .withValue(CalendarContract.Reminders.EVENT_ID, eventId)
                    .withValue(CalendarContract.Reminders.MINUTES, minutes)
                    .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    .build()
            )
        }

        // 5. 批量插入新事件及伴随提醒
        for (payload in eventsToInsert) {
            if (ops.size + 2 >= BATCH_OPERATION_LIMIT) {
                executeBatch(ops)
            }
            val eventOpIndex = ops.size
            ops.add(
                ContentProviderOperation.newInsert(eventsUri)
                    .withValue(CalendarContract.Events.CALENDAR_ID, calendarId)
                    .withValue(CalendarContract.Events.TITLE, payload.title)
                    .withValue(CalendarContract.Events.EVENT_LOCATION, payload.location)
                    .withValue(CalendarContract.Events.DESCRIPTION, payload.description)
                    .withValue(CalendarContract.Events.DTSTART, payload.startMillis)
                    .withValue(CalendarContract.Events.DTEND, payload.endMillis)
                    .withValue(CalendarContract.Events.EVENT_TIMEZONE, timeZoneId)
                    .withValue(CalendarContract.Events._SYNC_ID, payload.syncKey)
                    .withValue(CalendarContract.Events.SYNC_DATA1, payload.syncKey)
                    .build()
            )

            if (payload.remindMinutes > 0) {
                ops.add(
                    ContentProviderOperation.newInsert(remindersUri)
                        .withValueBackReference(CalendarContract.Reminders.EVENT_ID, eventOpIndex)
                        .withValue(CalendarContract.Reminders.MINUTES, payload.remindMinutes)
                        .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                        .build()
                )
            }
        }

        if (ops.isNotEmpty()) {
            executeBatch(ops)
        }

        return true
    }

    // 查询系统日历中的已有课程日程
    private fun queryExistingEvents(calendarId: Long): List<ExistingCalendarEvent> {
        val uri = CalendarContract.Events.CONTENT_URI
        val standardProjection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events._SYNC_ID,
            CalendarContract.Events.SYNC_DATA1
        )
        val fallbackProjection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events._SYNC_ID
        )

        val selection = "${CalendarContract.Events.CALENDAR_ID} = ?"
        val selectionArgs = arrayOf(calendarId.toString())

        val list = mutableListOf<ExistingCalendarEvent>()
        val cursor = try {
            context.contentResolver.query(uri, standardProjection, selection, selectionArgs, null)
        } catch (_: Exception) {
            try {
                context.contentResolver.query(uri, fallbackProjection, selection, selectionArgs, null)
            } catch (e: Exception) {
                android.util.Log.e("CalendarSyncHelper", "Query existing events failed", e)
                null
            }
        }

        cursor?.use {
            val idIdx = it.getColumnIndex(CalendarContract.Events._ID)
            val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
            val locIdx = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
            val descIdx = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
            val endIdx = it.getColumnIndex(CalendarContract.Events.DTEND)
            val syncIdIdx = it.getColumnIndex(CalendarContract.Events._SYNC_ID)
            val syncData1Idx = it.getColumnIndex(CalendarContract.Events.SYNC_DATA1)

            while (it.moveToNext()) {
                val id = if (idIdx >= 0) it.getLong(idIdx) else continue
                val title = if (titleIdx >= 0) it.getString(titleIdx).orEmpty() else ""
                val location = if (locIdx >= 0) it.getString(locIdx).orEmpty() else ""
                val description = if (descIdx >= 0) it.getString(descIdx).orEmpty() else ""
                val startMillis = if (startIdx >= 0) it.getLong(startIdx) else 0L
                val endMillis = if (endIdx >= 0) it.getLong(endIdx) else 0L
                val syncId = if (syncIdIdx >= 0) it.getString(syncIdIdx)?.takeIf { s -> s.isNotBlank() } else null
                val syncData1 = if (syncData1Idx >= 0) it.getString(syncData1Idx)?.takeIf { s -> s.isNotBlank() } else null
                val key = syncId ?: syncData1

                list.add(
                    ExistingCalendarEvent(
                        id = id,
                        syncKey = key,
                        title = title,
                        location = location,
                        description = description,
                        startMillis = startMillis,
                        endMillis = endMillis
                    )
                )
            }
        }
        return list
    }

    // 批量查询现有日程关联的提醒
    private fun queryExistingReminders(eventIds: Set<Long>): Map<Long, List<ExistingReminder>> {
        if (eventIds.isEmpty()) return emptyMap()

        val resultMap = mutableMapOf<Long, MutableList<ExistingReminder>>()
        val uri = CalendarContract.Reminders.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Reminders._ID,
            CalendarContract.Reminders.EVENT_ID,
            CalendarContract.Reminders.MINUTES
        )

        val chunks = eventIds.chunked(200)
        for (chunk in chunks) {
            val placeholders = chunk.joinToString(",") { "?" }
            val selection = "${CalendarContract.Reminders.EVENT_ID} IN ($placeholders)"
            val selectionArgs = chunk.map { it.toString() }.toTypedArray()

            val cursor = try {
                context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            } catch (e: Exception) {
                android.util.Log.e("CalendarSyncHelper", "Query reminders failed", e)
                null
            }

            cursor?.use {
                val idIdx = it.getColumnIndex(CalendarContract.Reminders._ID)
                val eventIdIdx = it.getColumnIndex(CalendarContract.Reminders.EVENT_ID)
                val minutesIdx = it.getColumnIndex(CalendarContract.Reminders.MINUTES)

                while (it.moveToNext()) {
                    val id = if (idIdx >= 0) it.getLong(idIdx) else continue
                    val eventId = if (eventIdIdx >= 0) it.getLong(eventIdIdx) else continue
                    val minutes = if (minutesIdx >= 0) it.getInt(minutesIdx) else 0
                    resultMap.getOrPut(eventId) { mutableListOf() }
                        .add(ExistingReminder(id = id, eventId = eventId, minutes = minutes))
                }
            }
        }
        return resultMap
    }

    // 执行事务批处理
    private fun executeBatch(ops: ArrayList<ContentProviderOperation>) {
        if (ops.isEmpty()) return
        try {
            context.contentResolver.applyBatch(CalendarContract.AUTHORITY, ops)
        } finally {
            ops.clear()
        }
    }

    // 构造带同步适配器参数的事件 URI
    private fun buildEventsUri(): Uri {
        return CalendarContract.Events.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
            .build()
    }

    // 构造带同步适配器参数的提醒 URI
    private fun buildRemindersUri(): Uri {
        return CalendarContract.Reminders.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
            .build()
    }

    // 从系统日历中删除专属日历账户及所有关联日程
    suspend fun deleteScheduleCalendar(): Boolean = withContext(Dispatchers.IO) {
        if (!hasCalendarPermission()) return@withContext false

        syncMutex.withLock {
            try {
                val calendarIds = findAllCalendarIds()
                calendarIds.forEach { clearCalendarEvents(it) }

                val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
                    .build()

                val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
                val selectionArgs = arrayOf(accountName, CALENDAR_ACCOUNT_TYPE)
                context.contentResolver.delete(uri, selection, selectionArgs)
                true
            } catch (e: Exception) {
                android.util.Log.e("CalendarSyncHelper", "Delete schedule calendar failed", e)
                false
            }
        }
    }

    // 查询所有已存在的专属本地日历账户 ID（并清理旧版本遗留账号）
    private fun findAllCalendarIds(): List<Long> {
        val uri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.ACCOUNT_NAME)
        val selection = "(${CalendarContract.Calendars.ACCOUNT_NAME} = ? OR ${CalendarContract.Calendars.ACCOUNT_NAME} = ?) AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
        val selectionArgs = arrayOf(accountName, LEGACY_CALENDAR_ACCOUNT_NAME, CALENDAR_ACCOUNT_TYPE)

        val currentAccounts = mutableListOf<Long>()
        val legacyAccounts = mutableListOf<Long>()
        val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val acc = it.getString(1)
                if (acc == accountName) {
                    currentAccounts.add(id)
                } else {
                    legacyAccounts.add(id)
                }
            }
        }

        // 清除旧版本遗留账户及数据
        legacyAccounts.forEach { legacyId ->
            clearCalendarEvents(legacyId, LEGACY_CALENDAR_ACCOUNT_NAME)
            val delUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, LEGACY_CALENDAR_ACCOUNT_NAME)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
                .build()
            context.contentResolver.delete(delUri, "${CalendarContract.Calendars._ID} = ?", arrayOf(legacyId.toString()))
        }

        return currentAccounts
    }

    // 获取或创建专属本地日历账户 ID
    private fun getOrCreateCalendarId(): Long {
        val existingIds = findAllCalendarIds()
        if (existingIds.isNotEmpty()) {
            // 清理多余的历史遗留账户，只保留首个
            if (existingIds.size > 1) {
                for (i in 1 until existingIds.size) {
                    val excessId = existingIds[i]
                    clearCalendarEvents(excessId)
                    val delUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
                        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
                        .build()
                    context.contentResolver.delete(delUri, "${CalendarContract.Calendars._ID} = ?", arrayOf(excessId.toString()))
                }
            }
            return existingIds.first()
        }

        // 创建以应用包名命名的专属日历账户
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, calendarDisplayName)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, calendarDisplayName)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF0066FF.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, accountName)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }

        val insertUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
            .build()

        val createdUri = context.contentResolver.insert(insertUri, values)
        return createdUri?.lastPathSegment?.toLongOrNull() ?: -1L
    }

    // 物理清除指定日历账户下的所有日程
    private fun clearCalendarEvents(calendarId: Long, customAccountName: String = accountName) {
        val uri = CalendarContract.Events.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, customAccountName)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
            .build()
        context.contentResolver.delete(uri, "${CalendarContract.Events.CALENDAR_ID} = ?", arrayOf(calendarId.toString()))
    }
}

// 目标日历日程数据模型
private data class DesiredCalendarEvent(
    val syncKey: String,
    val title: String,
    val location: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
    val remindMinutes: Int
) {
    val signature: String get() = "$title|$startMillis|$endMillis"
}

// 系统日历已有日程数据模型
private data class ExistingCalendarEvent(
    val id: Long,
    val syncKey: String?,
    val title: String,
    val location: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long
) {
    val signature: String get() = "$title|$startMillis|$endMillis"
}

// 系统日历已有提醒数据模型
private data class ExistingReminder(
    val id: Long,
    val eventId: Long,
    val minutes: Int
)
