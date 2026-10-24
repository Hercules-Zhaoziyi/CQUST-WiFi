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
import top.msfxp.schedule.data.model.DefaultCourseColors
import top.msfxp.schedule.data.model.SemesterDateHelper
import top.msfxp.schedule.data.model.TodayCourseItem
import top.msfxp.schedule.data.model.effectiveStartDate
import top.msfxp.schedule.data.model.getCourseColor
import top.msfxp.schedule.data.model.parseCoursePaletteJson
import top.msfxp.schedule.data.repository.ScheduleRepository
import top.msfxp.schedule.data.repository.SettingsRepository
import java.time.LocalDate

// 桌面微件依赖注入容器
private object WidgetDependencyContainer : KoinComponent {
    val scheduleRepository: ScheduleRepository by inject()
    val settingsRepository: SettingsRepository by inject()
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

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java))
        if (widgetIds.isNotEmpty()) {
            val settings = runCatching { WidgetDependencyContainer.settingsRepository.getAppSettingsOnce() }.getOrNull()
            val palette = parseCoursePaletteJson(settings?.customCourseColorsJson)
            val views = renderScheduleWidget(context, today, todayCourses, isVacation, palette)
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
    todayCourses: List<TodayCourseItem>,
    isVacation: Boolean,
    palette: List<CourseColor> = DefaultCourseColors
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_schedule)
    views.setOnClickPendingIntent(R.id.widget_root, getOpenAppPendingIntent(context))

    // 顶部状态栏渲染
    views.setTextViewText(R.id.tv_school_label, context.getString(R.string.widget_brand_name))
    views.setTextViewText(R.id.tv_date_month_day, context.getString(R.string.widget_date_format, today.monthValue, today.dayOfMonth))
    views.setTextViewText(R.id.tv_date_weekday, getDayOfWeekString(context, today.dayOfWeek.value))

    // 筛选未上完的课程列表
    val activeCourses = todayCourses.filter { !it.isFinished }

    // 已上完或原本无课，均展示无课颜文字状态
    if (isVacation || activeCourses.isEmpty()) {
        views.setViewVisibility(R.id.layout_courses, View.GONE)
        views.setViewVisibility(R.id.layout_empty, View.VISIBLE)
        views.setTextViewText(R.id.tv_kaomoji, context.getString(R.string.widget_kaomoji_happy))
        views.setTextViewText(R.id.tv_empty_desc, context.getString(R.string.widget_no_courses_today))
        return views
    }

    views.setViewVisibility(R.id.layout_empty, View.GONE)
    views.setViewVisibility(R.id.layout_courses, View.VISIBLE)

    // 焦点课程渲染
    val first = activeCourses[0]
    views.setTextViewText(R.id.tv_name_1, first.event.courseName)

    val room1 = first.event.location.trim()
    val teacher1 = first.event.teacher.trim()
    val roomTeacher1 = listOf(room1, teacher1).filter { it.isNotBlank() }.joinToString(" ")
    views.setTextViewText(R.id.tv_room_teacher_1, roomTeacher1.ifBlank { "--" })
    views.setTextViewText(R.id.tv_time_1, "${first.startTime} - ${first.endTime}")

    val colorInt1 = palette.getCourseColor(first.event.colorIndex).light.toArgb()
    views.setImageViewBitmap(R.id.iv_indicator_1, createBarBitmap(context, colorInt1, 3.5f, 42f))

    // 后续课程渲染
    if (activeCourses.size >= 2) {
        val second = activeCourses[1]
        views.setViewVisibility(R.id.row_course_2, View.VISIBLE)
        views.setTextViewText(R.id.tv_name_2, second.event.courseName)

        val room2 = second.event.location.trim()
        val timeRoom2 = listOf(second.startTime, room2).filter { it.isNotBlank() }.joinToString(" ")
        views.setTextViewText(R.id.tv_time_room_2, timeRoom2.ifBlank { second.startTime })

        val colorInt2 = palette.getCourseColor(second.event.colorIndex).light.toArgb()
        views.setImageViewBitmap(R.id.iv_indicator_2, createBarBitmap(context, colorInt2, 3.5f, 32f))
    } else {
        // 若今日只有 1 节课，则收起第二行条目
        views.setViewVisibility(R.id.row_course_2, View.GONE)
    }

    return views
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
