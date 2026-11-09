package top.msfxp.schedule.util

import top.msfxp.schedule.data.model.DefaultTimeSlots
import top.msfxp.schedule.data.model.TimeSlot
import java.time.LocalTime

// 考试起止时刻与课表节次映射
object ExamTimeMapper {

    // 将考试起止时刻映射为节次区间，无法解析时返回 null
    fun resolveSections(
        startTime: String?,
        endTime: String?,
        slots: List<TimeSlot> = DefaultTimeSlots
    ): Pair<Int, Int>? {
        if (slots.isEmpty()) return null
        val start = parseTime(startTime) ?: return null
        val end = parseTime(endTime) ?: return null
        if (end <= start) return null

        val startSection = slots.firstOrNull { slot ->
            val slotEnd = parseTime(slot.endTime) ?: return@firstOrNull false
            start < slotEnd
        }?.sectionNumber ?: slots.first().sectionNumber

        val endSection = slots.lastOrNull { slot ->
            val slotStart = parseTime(slot.startTime) ?: return@lastOrNull false
            end > slotStart
        }?.sectionNumber ?: startSection

        return startSection to endSection.coerceAtLeast(startSection)
    }

    // 解析 HH:mm 时刻文本
    private fun parseTime(text: String?): LocalTime? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return runCatching { LocalTime.parse(trimmed) }.getOrNull()
    }
}
