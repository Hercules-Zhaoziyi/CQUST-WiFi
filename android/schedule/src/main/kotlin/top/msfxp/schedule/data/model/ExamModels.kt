package top.msfxp.schedule.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import top.msfxp.schedule.util.CourseLocationFormatter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

// 考试批次与形式常量（期末考试结构已明确，期中考试结构未知暂用通用名，后续可区分；期待PR）
const val EXAM_TYPE_DEFAULT = "考试安排"
const val EXAM_MODE_WRITTEN = "标考"
const val EXAM_MODE_NON_STANDARD = "非标"

// 教务原始考试条目
data class RawExamItem(
    val sequence: Int,
    val courseCode: String,
    val courseName: String,
    val examType: String = EXAM_TYPE_DEFAULT,
    val examDate: String? = null,
    val week: Int? = null,
    val weekday: Int? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val position: String? = null,
    val seatNo: String? = null,
    val status: String? = null,
    val remark: String = "",
    val arrangeText: String = ""
) {
    // 日期与起止时间完整时视为标准化考试
    val isScheduled: Boolean
        get() = !examDate.isNullOrBlank() && !startTime.isNullOrBlank() && !endTime.isNullOrBlank()

    val examMode: String
        get() = if (isScheduled) EXAM_MODE_WRITTEN else EXAM_MODE_NON_STANDARD

    val canSyncCalendar: Boolean
        get() = isScheduled && !position.isNullOrBlank()
}

// 考试记录实体
@Entity(
    tableName = "exam_records",
    indices = [Index(value = ["tableId", "semesterId", "examDate"], name = "index_exam_records_semester")]
)
data class ExamRecord(
    @PrimaryKey val id: String,
    val tableId: String,
    val semesterId: String,
    val courseId: String?,
    val examType: String,
    val sequence: Int,
    val courseCode: String,
    val courseName: String,
    val examDate: String?,
    val week: Int?,
    val weekday: Int?,
    val startTime: String?,
    val endTime: String?,
    val position: String?,
    val seatNo: String?,
    val status: String?,
    val remark: String,
    val arrangeText: String,
    val updatedAt: Long
) {
    // 日期与起止时间完整时视为标准化考试
    val isScheduled: Boolean
        get() = !examDate.isNullOrBlank() && !startTime.isNullOrBlank() && !endTime.isNullOrBlank()

    val examMode: String
        get() = if (isScheduled) EXAM_MODE_WRITTEN else EXAM_MODE_NON_STANDARD

    val canSyncCalendar: Boolean
        get() = isScheduled && !position.isNullOrBlank()

    // 规范化展示地点
    val displayPosition: String
        get() = position?.let { CourseLocationFormatter.formatDisplay(it) }.orEmpty()

    // 安全解析考试日期
    val examDateValue: LocalDate?
        get() = examDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    // 日期与安排星期不一致
    val hasWeekdayMismatch: Boolean
        get() {
            val date = examDateValue ?: return false
            return weekday != null && date.dayOfWeek.value != weekday
        }

    // 优先按考试日期推算星期，缺失时使用教务解析值
    val resolvedWeekday: Int?
        get() = examDateValue?.dayOfWeek?.value ?: weekday

    // 优先使用教务返回的周次，缺失时按考试日期推算
    fun resolveWeek(semesterStart: LocalDate): Int? {
        if (week != null) return week
        val date = examDateValue ?: return null
        val startMonday = semesterStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val examMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (ChronoUnit.DAYS.between(startMonday, examMonday) / 7 + 1).toInt()
    }
}

// 考试课表栅格块
data class ExamGridBlock(
    val week: Int,
    val day: Int,
    val startSection: Int,
    val endSection: Int,
    val exam: ExamRecord
)

// 教务考试抓取结果
data class ExamFetchResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val items: List<RawExamItem> = emptyList(),
    val isSessionExpired: Boolean = false
)

// 构造考试记录主键（按课程统一标识）
fun buildExamRecordId(tableId: String, semesterId: String, item: RawExamItem): String {
    val unifiedCode = courseUnifiedCode(item.courseCode)
    val key = if (item.isScheduled) {
        "$unifiedCode|${item.examDate}|${item.startTime}"
    } else {
        "$unifiedCode|${item.sequence}"
    }
    return "$tableId|$semesterId|$key"
}
