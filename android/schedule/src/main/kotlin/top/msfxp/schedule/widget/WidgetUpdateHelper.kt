package top.msfxp.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import top.msfxp.schedule.R
import top.msfxp.schedule.data.model.CourseColor
import top.msfxp.schedule.data.model.ExamRecord
import top.msfxp.schedule.data.model.SemesterDateHelper
import top.msfxp.schedule.data.model.TodayCourseItem
import top.msfxp.schedule.data.model.effectiveStartDate
import top.msfxp.schedule.data.model.getCourseColor
import top.msfxp.schedule.data.model.parseCoursePaletteJson
import top.msfxp.schedule.data.model.resolveExamColorIndex
import top.msfxp.schedule.data.model.unifiedCode
import top.msfxp.schedule.data.repository.ExamRepository
import top.msfxp.schedule.data.repository.ScheduleRepository
import top.msfxp.schedule.data.repository.SettingsRepository
import java.time.LocalDate
import java.time.LocalTime

// 微件日程条目
data class WidgetAgendaItem(
    val title: String,
    val subtitle: String,
    val roomText: String,
    val timeText: String,
    val colorArgb: Int,
    val isFinished: Boolean,
    val startMinutes: Int,
    val isExam: Boolean = false
)

// 桌面微件依赖注入容器
private object WidgetDependencyContainer : KoinComponent {
    val scheduleRepository: ScheduleRepository by inject()
    val settingsRepository: SettingsRepository by inject()
    val examRepository: ExamRepository by inject()
}

// 全量更新课程表桌面微件
suspend fun updateAllAppWidgets(context: Context) = withContext(Dispatchers.IO) {
    try {
        val scheduleRepo = WidgetDependencyContainer.scheduleRepository
        val currentSemester = scheduleRepo.getCurrentSemesterOnce()
        val totalWeeks = currentSemester?.totalWeeks ?: 20
        val startDate = currentSemester.effectiveStartDate

        val today = LocalDate.now()
        val currentWeek = SemesterDateHelper.calculateWeekNumber(startDate, today, totalWeeks)
        val isVacation = currentWeek < 1 || currentWeek > totalWeeks
        val tableId = scheduleRepo.getOrCreateDefaultTableId()

        val todayCourses = if (isVacation) emptyList() else scheduleRepo.getTodayCourses(tableId, currentSemester?.id.orEmpty(), today.dayOfWeek.value, currentWeek)
        val todayExams = if (isVacation) emptyList() else runCatching {
            WidgetDependencyContainer.examRepository
                .getExamsOnce(tableId, currentSemester?.id.orEmpty())
                .filter { it.isScheduled && it.examDate == today.toString() }
        }.getOrDefault(emptyList())

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java))
        if (widgetIds.isNotEmpty()) {
            val settings = runCatching { WidgetDependencyContainer.settingsRepository.getAppSettingsOnce() }.getOrNull()
            val palette = parseCoursePaletteJson(settings?.customCourseColorsJson)
            val courseColorIndexMap = runCatching {
                val courses = scheduleRepo.getAllCoursesWithEventsOnce(tableId)
                buildMap {
                    courses.forEach { relation ->
                        put(relation.course.id, relation.course.colorIndex)
                        putIfAbsent(relation.course.unifiedCode, relation.course.colorIndex)
                    }
                }
            }.getOrDefault(emptyMap())
            val agenda = buildAgendaItems(context, todayCourses, todayExams, palette, courseColorIndexMap)
            val views = renderScheduleWidget(context, today, agenda, isVacation)
            appWidgetManager.updateAppWidget(widgetIds, views)
        }
    } catch (_: Exception) {
        // 静默兜底保证桌面不抛异常
    }
}

// 渲染 2x2 课程表微件布局
fun renderScheduleWidget(
    context: Context,
    today: LocalDate,
    agendaItems: List<WidgetAgendaItem>,
    isVacation: Boolean
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_schedule)
    views.setOnClickPendingIntent(R.id.widget_root, getOpenAppPendingIntent(context))

    // 顶部状态栏渲染
    views.setTextViewText(R.id.tv_school_label, context.getString(R.string.widget_brand_name))
    views.setTextViewText(R.id.tv_date_month_day, context.getString(R.string.widget_date_format, today.monthValue, today.dayOfMonth))
    views.setTextViewText(R.id.tv_date_weekday, getDayOfWeekString(context, today.dayOfWeek.value))

    // 筛选未上完的日程条目
    val activeItems = agendaItems.filter { !it.isFinished }

    // 已上完或原本无课，均展示无课颜文字状态
    if (isVacation || activeItems.isEmpty()) {
        views.setViewVisibility(R.id.layout_courses, View.GONE)
        views.setViewVisibility(R.id.layout_empty, View.VISIBLE)
        views.setTextViewText(R.id.tv_kaomoji, context.getString(R.string.widget_kaomoji_happy))
        views.setTextViewText(R.id.tv_empty_desc, context.getString(R.string.widget_no_courses_today))
        return views
    }

    views.setViewVisibility(R.id.layout_empty, View.GONE)
    views.setViewVisibility(R.id.layout_courses, View.VISIBLE)

    // 焦点条目渲染
    val first = activeItems[0]
    views.setTextViewText(R.id.tv_name_1, first.title)
    views.setTextViewText(R.id.tv_room_teacher_1, first.subtitle)
    views.setTextViewText(R.id.tv_time_1, first.timeText)
    views.setImageViewBitmap(R.id.iv_indicator_1, createBarBitmap(context, first.colorArgb, 3.5f, 42f))

    // 后续条目渲染
    if (activeItems.size >= 2) {
        val second = activeItems[1]
        views.setViewVisibility(R.id.row_course_2, View.VISIBLE)
        views.setTextViewText(R.id.tv_name_2, second.title)
        views.setTextViewText(
            R.id.tv_time_room_2,
            listOf(second.timeText, second.roomText).filter { it.isNotBlank() }.joinToString(" ")
        )
        views.setImageViewBitmap(R.id.iv_indicator_2, createBarBitmap(context, second.colorArgb, 3.5f, 32f))
    } else {
        // 若今日只有 1 项日程，则收起第二行条目
        views.setViewVisibility(R.id.row_course_2, View.GONE)
    }

    return views
}

// 合并今日课程与考试为微件日程条目
private fun buildAgendaItems(
    context: Context,
    todayCourses: List<TodayCourseItem>,
    todayExams: List<ExamRecord>,
    palette: List<CourseColor>,
    courseColorIndexMap: Map<String, Int>
): List<WidgetAgendaItem> {
    val now = LocalTime.now()
    val courseItems = todayCourses.map { item ->
        WidgetAgendaItem(
            title = item.event.courseName,
            subtitle = listOf(item.event.displayLocation.trim(), item.event.teacher.trim())
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "--" },
            roomText = item.event.displayLocation.trim(),
            timeText = "${item.startTime} - ${item.endTime}",
            colorArgb = palette.getCourseColor(item.event.colorIndex).light.toArgb(),
            isFinished = item.isFinished,
            startMinutes = parseMinutes(item.startTime)
        )
    }
    val examItems = todayExams.map { exam ->
        val startTime = exam.startTime.orEmpty()
        val endTime = exam.endTime.orEmpty()
        val seatText = exam.seatNo?.takeIf { it.isNotBlank() }
            ?.let { context.getString(R.string.exam_seat_number_format, it) }
            .orEmpty()
        WidgetAgendaItem(
            title = context.getString(R.string.calendar_exam_title_format, exam.courseName),
            subtitle = listOf(exam.displayPosition, seatText)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "--" },
            roomText = exam.displayPosition,
            timeText = "$startTime - $endTime",
            colorArgb = palette.getCourseColor(resolveExamColorIndex(exam, courseColorIndexMap)).light.toArgb(),
            isFinished = runCatching { LocalTime.parse(endTime) < now }.getOrDefault(false),
            startMinutes = parseMinutes(startTime),
            isExam = true
        )
    }
    return (courseItems + examItems)
        .sortedWith(compareBy({ it.startMinutes }, { if (it.isExam) 0 else 1 }))
}

// 解析 HH:mm 文本为当日分钟数
private fun parseMinutes(text: String): Int {
    return runCatching {
        val time = LocalTime.parse(text)
        time.hour * 60 + time.minute
    }.getOrDefault(Int.MAX_VALUE)
}

// 生成抗锯齿圆角矩形纯色指示条
private fun createBarBitmap(context: Context, colorInt: Int, widthDp: Float, heightDp: Float): Bitmap {
    val density = context.resources.displayMetrics.density
    val width = (widthDp * density).toInt().coerceAtLeast(1)
    val height = (heightDp * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        style = Paint.Style.FILL
    }
    val radius = width / 2f
    canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
    return bitmap
}

// 转换星期数字为本地化星期名称
private fun getDayOfWeekString(context: Context, day: Int): String {
    return when (day) {
        1 -> context.getString(R.string.day_monday)
        2 -> context.getString(R.string.day_tuesday)
        3 -> context.getString(R.string.day_wednesday)
        4 -> context.getString(R.string.day_thursday)
        5 -> context.getString(R.string.day_friday)
        6 -> context.getString(R.string.day_saturday)
        7 -> context.getString(R.string.day_sunday)
        else -> ""
    }
}

// 构建点击微件唤起主界面的待处理意图
private fun getOpenAppPendingIntent(context: Context): PendingIntent {
    val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName)?:Intent()).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("cqust-open-page", "schedule")
    }
    return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
