package top.msfxp.schedule.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.msfxp.schedule.service.DndSchedulerWorker

// 课程表 2x2 桌面微件提供者
class ScheduleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        CoroutineScope(Dispatchers.IO).launch {
            updateAllAppWidgets(context)
            DndSchedulerWorker.triggerImmediately(context)
        }
    }
}
