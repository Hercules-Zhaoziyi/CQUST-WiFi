package top.msfxp.schedule.data.model

import androidx.compose.ui.graphics.Color
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

// 课程类型常量
const val COURSE_TYPE_THEORY = "THEORY"
const val COURSE_TYPE_PRACTICAL = "PRACTICAL"

// 课表实体
@Entity(tableName = "course_tables")
@Serializable
data class CourseTable(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: Long
)

// 课程信息实体
@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = CourseTable::class,
            parentColumns = ["id"],
            childColumns = ["tableId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tableId"]),
        Index(value = ["courseCode"])
    ]
)
@Serializable
data class Course(
    @PrimaryKey
    val id: String,
    val tableId: String,
    val courseCode: String = "",
    val name: String,
    val credit: String = "",
    val category: String = "",
    val courseType: String = COURSE_TYPE_THEORY,
    val colorIndex: Int = 0,
    val remark: String = ""
) {
    val isPractical: Boolean get() = courseType == COURSE_TYPE_PRACTICAL
}

// 课程事件实体
@Entity(
    tableName = "course_events",
    foreignKeys = [
        ForeignKey(
            entity = Course::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CourseTable::class,
            parentColumns = ["id"],
            childColumns = ["tableId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tableId", "week"]),
        Index(value = ["tableId", "week", "day"]),
        Index(value = ["courseId"])
    ]
)
@Serializable
data class CourseEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val courseId: String,
    val tableId: String,
    val week: Int,
    val day: Int,
    val startSection: Int,
    val endSection: Int,
    val location: String = "",
    val teacher: String = "",
    val projectName: String = "",
    val studentCount: Int = 0
)

// 节次作息时间模型
data class TimeSlot(
    val sectionNumber: Int,
    val startTime: String,
    val endTime: String
)

// 重庆科技大学标准作息时间列表
val DefaultTimeSlots = listOf(
    TimeSlot(1, "08:30", "09:15"),
    TimeSlot(2, "09:25", "10:10"),
    TimeSlot(3, "10:30", "11:15"),
    TimeSlot(4, "11:25", "12:10"),
    TimeSlot(5, "14:00", "14:45"),
    TimeSlot(6, "14:55", "15:40"),
    TimeSlot(7, "16:00", "16:45"),
    TimeSlot(8, "16:55", "17:40"),
    TimeSlot(9, "19:00", "19:45"),
    TimeSlot(10, "19:55", "20:40"),
    TimeSlot(11, "20:50", "21:35")
)

// 调课规则实体
@Entity(
    tableName = "course_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = CourseTable::class,
            parentColumns = ["id"],
            childColumns = ["tableId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tableId"]),
        Index(value = ["courseId"]),
        Index(value = ["semesterId"])
    ]
)
@Serializable
data class CourseAdjustment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tableId: String,
    val semesterId: String,  // 绑定学期，隔离多学期调课记录
    val courseId: String,
    val originalWeek: Int,
    val originalDay: Int,
    val originalStartSection: Int,
    val originalEndSection: Int,
    val originalLocation: String = "",  // 原上课地点
    val targetWeek: Int,
    val targetDay: Int,
    val targetStartSection: Int,
    val targetEndSection: Int,
    val targetLocation: String = "",
    val isSuspended: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// 调课规则展示模型
data class CourseAdjustmentWithMeta(
    @Embedded val adjustment: CourseAdjustment,
    val courseName: String,
    val courseCode: String,
    val courseType: String,
    val colorIndex: Int,
    val projectName: String = ""
) {
    val isPractical: Boolean get() = courseType == COURSE_TYPE_PRACTICAL
}

// 课程展示事件模型
data class CourseEventWithMeta(
    val id: Long,
    val courseId: String,
    val tableId: String,
    val week: Int,
    val day: Int,
    val startSection: Int,
    val endSection: Int,
    val location: String,
    val teacher: String,
    val projectName: String,
    val studentCount: Int,
    val courseName: String,
    val courseCode: String,
    val credit: String,
    val category: String,
    val courseType: String,
    val colorIndex: Int
) {
    @androidx.room.Ignore
    var weeks: List<Int> = emptyList()

    @androidx.room.Ignore
    var isAdjusted: Boolean = false

    val isPractical: Boolean get() = courseType == COURSE_TYPE_PRACTICAL

    // 网格展示标题
    val displayName: String
        get() = if (isPractical && projectName.isNotBlank()) {
            "${courseName}\n(${projectName})"
        } else {
            courseName
        }

    // 单行标题
    val inlineTitle: String
        get() = if (isPractical && projectName.isNotBlank()) {
            "${courseName} (${projectName})"
        } else {
            courseName
        }

    // 日历日程标题
    val calendarTitle: String
        get() = if (isPractical && projectName.isNotBlank()) {
            "${courseName} - ${projectName}"
        } else {
            courseName
        }

    // 节次区间文本
    val sectionRangeText: String
        get() = if (startSection == endSection) "第${startSection}节" else "第${startSection}-${endSection}节"
}

// 转换为展示模型
fun CourseEvent.toMeta(course: Course, weeks: List<Int> = emptyList()): CourseEventWithMeta {
    return CourseEventWithMeta(
        id = id,
        courseId = courseId,
        tableId = tableId,
        week = week,
        day = day,
        startSection = startSection,
        endSection = endSection,
        location = location,
        teacher = teacher,
        projectName = projectName,
        studentCount = studentCount,
        courseName = course.name,
        courseCode = course.courseCode,
        credit = course.credit,
        category = course.category,
        courseType = course.courseType,
        colorIndex = course.colorIndex
    ).apply {
        this.weeks = weeks
    }
}

// 课程及关联事件模型
data class CourseWithEvents(
    @Embedded val course: Course,
    @Relation(
        parentColumn = "id",
        entityColumn = "courseId"
    )
    val events: List<CourseEvent> = emptyList()
) {
    // 压缩事件为时段槽位列表
    fun toCompressedSlots(): List<CompressedCourseSlot> {
        return events
            .groupBy { "${it.day}_${it.startSection}_${it.endSection}_${it.location}_${it.teacher}_${it.projectName}" }
            .map { (_, group) ->
                val first = group.first()
                CompressedCourseSlot(
                    day = first.day,
                    startSection = first.startSection,
                    endSection = first.endSection,
                    location = first.location,
                    teacher = first.teacher,
                    weeks = group.map { it.week }.filter { it > 0 }.distinct().sorted(),
                    projectName = first.projectName,
                    studentCount = first.studentCount
                )
            }
            .sortedWith(
                compareBy<CompressedCourseSlot> { it.day }
                    .thenBy { it.startSection }
                    .thenBy { it.weeks.minOrNull() ?: 0 }
                    .thenBy { it.projectName }
            )
    }
}

// 课程时段槽位模型
data class CompressedCourseSlot(
    val day: Int,
    val startSection: Int,
    val endSection: Int,
    val location: String,
    val teacher: String,
    val weeks: List<Int>,
    val projectName: String = "",
    val studentCount: Int = 0
)

// 课程栅格合并块模型
data class MergedCourseBlock(
    val day: Int,
    val startSection: Int,
    val endSection: Int,
    val events: List<CourseEventWithMeta>
) {
    // 首选展示事件
    val primaryEvent: CourseEventWithMeta get() = events.first()
    val hasMultiple: Boolean get() = events.size > 1
    val extraCount: Int get() = events.size - 1
}

// 合并为栅格块列表
fun List<CourseEventWithMeta>.toMergedBlocks(): List<MergedCourseBlock> {
    return this.filter { it.day in 1..7 && it.startSection > 0 }
        .groupBy { "${it.day}_${it.startSection}_${it.endSection}" }
        .map { (_, group) ->
            val sortedGroup = group.sortedWith(
                compareByDescending<CourseEventWithMeta> { it.isPractical }
                    .thenBy { it.courseName }
            )
            val first = sortedGroup.first()
            MergedCourseBlock(
                day = first.day,
                startSection = first.startSection,
                endSection = first.endSection,
                events = sortedGroup
            )
        }
}

// 今日课程项模型
data class TodayCourseItem(
    val event: CourseEventWithMeta,
    val startTime: String,
    val endTime: String,
    val isFinished: Boolean
)

// 格式化周次列表
fun List<Int>.formatWeeksSummary(): String {
    val weekNums = this.distinct().sorted()
    if (weekNums.isEmpty()) return ""
    val min = weekNums.first()
    val max = weekNums.last()
    val count = weekNums.size
    val isAllConsecutive = count == (max - min + 1)
    val isAllOdd = weekNums.all { it % 2 == 1 } && count == (max - min) / 2 + 1
    val isAllEven = weekNums.all { it % 2 == 0 } && count == (max - min) / 2 + 1
    return when {
        isAllConsecutive -> if (min == max) "第${min}周" else "${min}-${max}周"
        isAllOdd -> "${min}-${max}周(单)"
        isAllEven -> "${min}-${max}周(双)"
        else -> "${weekNums.joinToString(",")}周"
    }
}

// 学生档案模型
@Serializable
data class StudentProfile(
    val studentId: String = "",
    val name: String = "",
    val college: String = "",
    val major: String = "",
    val className: String = ""
)

// 学期实体
@Entity(tableName = "semesters")
@Serializable
data class Semester(
    @PrimaryKey
    val id: String,
    val schoolYear: String,
    val name: String,
    val label: String,
    val startDate: String = "",
    val totalWeeks: Int = 20,
    val isCurrent: Boolean = false
)

// 获取学期有效开学日期
val Semester?.effectiveStartDate: LocalDate
    get() = runCatching {
        if (!this?.startDate.isNullOrBlank()) LocalDate.parse(this.startDate) else null
    }.getOrNull() ?: SemesterDateHelper.calculateDefaultStartDate(
        schoolYear = this?.schoolYear,
        semesterName = this?.name
    )

// 课程配色模型
data class CourseColor(
    val light: Color,
    val dark: Color,
    val text: Color = Color.White
) {
    companion object {
        // 从单一颜色生成亮暗双态配色
        fun fromColor(color: Color): CourseColor {
            val darkColor = Color(
                red = (color.red * 0.82f).coerceIn(0f, 1f),
                green = (color.green * 0.82f).coerceIn(0f, 1f),
                blue = (color.blue * 0.82f).coerceIn(0f, 1f),
                alpha = color.alpha
            )
            return CourseColor(light = color, dark = darkColor)
        }

        // 从十六进制颜色文本解析
        fun fromHex(hex: String, fallback: CourseColor = DefaultCourseColors[0]): CourseColor {
            val color = hex.toColorOrNull() ?: return fallback
            return fromColor(color)
        }
    }
}

// 颜色转 16 进制字符串
fun Color.toHexArgb(): String {
    val a = (alpha * 255f).toInt().coerceIn(0, 255)
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X%02X", a, r, g, b)
}

// 16 进制字符串转 Compose Color
fun String.toColorOrNull(): Color? {
    val cleanHex = this.trim().removePrefix("#")
    return try {
        when (cleanHex.length) {
            6 -> {
                val colorInt = cleanHex.toLong(16) or 0x00000000FF000000
                Color(colorInt)
            }
            8 -> {
                val colorInt = cleanHex.toLong(16)
                Color(colorInt)
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

// 默认课程配色调色板 (16色)
val DefaultCourseColors = listOf(
    CourseColor(Color(0xFFFF6584), Color(0xFFD94464)), // 珊瑚粉
    CourseColor(Color(0xFFFFA726), Color(0xFFD98204)), // 暖阳橙
    CourseColor(Color(0xFF4FD1C5), Color(0xFF23A89C)), // 薄荷青
    CourseColor(Color(0xFF9F7AEA), Color(0xFF7A54C9)), // 风信紫
    CourseColor(Color(0xFF4299E1), Color(0xFF2478BE)), // 晴空蓝
    CourseColor(Color(0xFF48BB78), Color(0xFF2A9958)), // 翡翠绿
    CourseColor(Color(0xFFF56565), Color(0xFFCE3D3D)), // 绯红
    CourseColor(Color(0xFF667EEA), Color(0xFF435BCB)), // 鸢尾靛
    CourseColor(Color(0xFFED8936), Color(0xFFC76717)), // 蜜桔橙
    CourseColor(Color(0xFF319795), Color(0xFF167674)), // 碧青
    CourseColor(Color(0xFFD53F8C), Color(0xFFB01D6B)), // 洋红
    CourseColor(Color(0xFF805AD5), Color(0xFF5E39B2)), // 葡萄紫
    CourseColor(Color(0xFF00B5D8), Color(0xFF008FA8)), // 孔雀蓝
    CourseColor(Color(0xFF68D391), Color(0xFF42AF6B)), // 嫩芽绿
    CourseColor(Color(0xFFE53E3E), Color(0xFFBE2323)), // 炽红
    CourseColor(Color(0xFF8B5CF6), Color(0xFF6D3BD8))  // 丁香紫
)

// 解析调色板 JSON
fun parseCoursePaletteJson(json: String?): List<CourseColor> {
    if (json.isNullOrBlank()) return DefaultCourseColors
    return try {
        val hexList = kotlinx.serialization.json.Json.decodeFromString<List<String>>(json)
        if (hexList.isEmpty()) return DefaultCourseColors
        hexList.mapIndexed { idx, hex ->
            val fallback = DefaultCourseColors.getOrElse(idx) { DefaultCourseColors[0] }
            CourseColor.fromHex(hex, fallback)
        }
    } catch (_: Exception) {
        DefaultCourseColors
    }
}

// 序列化调色板
fun serializeCoursePalette(colors: List<CourseColor>): String {
    val hexList = colors.map { it.light.toHexArgb() }
    return kotlinx.serialization.json.Json.encodeToString(hexList)
}

// 获取课程颜色
fun List<CourseColor>.getCourseColor(colorIndex: Int): CourseColor {
    if (isEmpty()) return DefaultCourseColors[0]
    val safeIdx = (colorIndex % size).let { if (it < 0) it + size else it }
    return this[safeIdx]
}

// 学期日期与周次计算工具
object SemesterDateHelper {

    // 计算默认开学日期
    fun calculateDefaultStartDate(
        schoolYear: String? = null,
        semesterName: String? = null,
        fallbackDate: LocalDate = LocalDate.now()
    ): LocalDate {
        val years = schoolYear?.let { Regex("""\d{4}""").findAll(it).map { m -> m.value.toInt() }.toList() }
        val isSpring = semesterName?.let { it.contains("2") || it.contains("春") || it.contains("下") } ?: false

        return when {
            years != null && years.size >= 2 -> {
                if (isSpring) LocalDate.of(years[1], 2, 22) else LocalDate.of(years[0], 9, 7)
            }
            else -> {
                val year = fallbackDate.year
                val month = fallbackDate.monthValue
                if (month in 2..7) {
                    LocalDate.of(year, 2, 22)
                } else {
                    val autumnYear = if (month == 1) year - 1 else year
                    LocalDate.of(autumnYear, 9, 7)
                }
            }
        }
    }

    // 计算指定日期所属周次
    fun calculateWeekNumber(
        startDate: LocalDate = calculateDefaultStartDate(),
        targetDate: LocalDate = LocalDate.now(),
        totalWeeks: Int = 20
    ): Int {
        val thisMonday = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val daysDiff = ChronoUnit.DAYS.between(startDate, thisMonday)
        val week = if (daysDiff >= 0) (daysDiff / 7).toInt() + 1 else 1
        return week.coerceIn(1, totalWeeks)
    }
}
