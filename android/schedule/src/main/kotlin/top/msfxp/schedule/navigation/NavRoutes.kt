package top.msfxp.schedule.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

// 导航路由接口定义
@Serializable
sealed interface Route : NavKey {

    // 主课表页面
    @Serializable
    data object Schedule : Route

    // 设置主页面
    @Serializable
    data object Settings : Route

    // 教务登录页面
    @Serializable
    data object Login : Route

    // 课程总览列表页面
    @Serializable
    data object CourseOverview : Route

    // 课前通知提醒页面
    @Serializable
    data object PreClassReminderSettings : Route

    // 日历日程同步页面
    @Serializable
    data object CalendarSyncSettings : Route

    // 课中自动化设置页面
    @Serializable
    data object ClassAutomationSettings : Route

    // 权限管理页面
    @Serializable
    data object PermissionManagement : Route

    // 个性化设置页面
    @Serializable
    data object PersonalizationSettings : Route

    // 关于应用二级页面
    @Serializable
    data object About : Route
}
