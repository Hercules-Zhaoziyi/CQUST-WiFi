package top.msfxp.schedule

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import top.msfxp.schedule.navigation.Route
import top.msfxp.schedule.ui.courses.CourseOverviewScreen
import top.msfxp.schedule.ui.login.CqustLoginScreen
import top.msfxp.schedule.ui.schedule.WeeklyScheduleScreen
import top.msfxp.schedule.ui.settings.SettingsScreen
import top.msfxp.schedule.ui.settings.subscreens.AboutScreen
import top.msfxp.schedule.ui.settings.subscreens.CalendarSyncScreen
import top.msfxp.schedule.ui.settings.subscreens.ClassAutomationScreen
import top.msfxp.schedule.ui.settings.subscreens.PermissionManagementScreen
import top.msfxp.schedule.ui.settings.subscreens.PersonalizationScreen
import top.msfxp.schedule.ui.settings.subscreens.PreClassReminderScreen
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

// 应用根导航组件
@Composable
fun App(onSharedLogin:(()->Unit)?=null,onHostAbout:(()->Unit)?=null,controller:ThemeController?=null,
    onSharedSettings:(()->Unit)?=null,settingsOnly:Boolean=false,onExit:(()->Unit)?=null,
    accountContent:(@Composable (()->Unit)->Unit)?=null,
    preferencesContent:(@Composable ()->Unit)?=null,
    permissionsContent:(@Composable ()->Unit)?=null,
    updatesContent:(@Composable ()->Unit)?=null) {
    val defaultController = remember { ThemeController(ColorSchemeMode.System) }
    val backStack = rememberNavBackStack<Route>(if(settingsOnly)Route.Settings else Route.Schedule)
    fun back(){if(backStack.size>1)backStack.removeLastOrNull()else onExit?.invoke()}

    MiuixTheme(controller = controller?:defaultController) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MiuixTheme.colorScheme.background
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { back() }
            ) {
                // 主课表页面
                entry<Route.Schedule> {
                    WeeklyScheduleScreen(
                        onNavigateToSettings = {
                            if(onSharedSettings!=null)onSharedSettings()else backStack.add(Route.Settings)
                        },
                        onNavigateToLogin = {
                            if(onSharedLogin!=null)onSharedLogin()else backStack.add(Route.Login)
                        }
                    )
                }

                // 设置主页面
                entry<Route.Settings> {
                    SettingsScreen(
                        accountContent=accountContent,
                        preferencesContent=preferencesContent,
                        permissionsContent=permissionsContent,
                        updatesContent=updatesContent,
                        onNavigateToLogin = {
                            if(onSharedLogin!=null)onSharedLogin()else backStack.add(Route.Login)
                        },
                        onNavigateToOverview = {
                            backStack.add(Route.CourseOverview)
                        },
                        onNavigateToPreClassReminder = {
                            backStack.add(Route.PreClassReminderSettings)
                        },
                        onNavigateToCalendarSync = {
                            backStack.add(Route.CalendarSyncSettings)
                        },
                        onNavigateToClassAutomation = {
                            backStack.add(Route.ClassAutomationSettings)
                        },
                        onNavigateToPermissions = {
                            backStack.add(Route.PermissionManagement)
                        },
                        onNavigateToPersonalization = {
                            backStack.add(Route.PersonalizationSettings)
                        },
                        onNavigateToAbout = {
                            if(onHostAbout!=null)onHostAbout()else backStack.add(Route.About)
                        },
                        onBack = {
                            back()
                        }
                    )
                }

                // 个性化设置页面
                entry<Route.PersonalizationSettings> {
                    PersonalizationScreen(
                        onBack = {
                            backStack.removeLastOrNull()
                        }
                    )
                }

                // 课前通知提醒页面
                entry<Route.PreClassReminderSettings> {
                    PreClassReminderScreen(
                        onBack = {
                            backStack.removeLastOrNull()
                        }
                    )
                }

                // 日历日程同步页面
                entry<Route.CalendarSyncSettings> {
                    CalendarSyncScreen(
                        onBack = {
                            backStack.removeLastOrNull()
                        }
                    )
                }

                // 课中自动化设置页面
                entry<Route.ClassAutomationSettings> {
                    ClassAutomationScreen(
                        onBack = {
                            backStack.removeLastOrNull()
                        },
                        onNavigateToPermissions = {
                            backStack.add(Route.PermissionManagement)
                        }
                    )
                }

                // 权限管理页面
                entry<Route.PermissionManagement> {
                    PermissionManagementScreen(
                        onBack = {
                            backStack.removeLastOrNull()
                        }
                    )
                }

                // 教务登录页面
                entry<Route.Login> {
                    CqustLoginScreen(
                        onLoginSuccess = {
                            backStack.removeLastOrNull()
                        },
                        onBack = {
                            backStack.removeLastOrNull()
                        }
                    )
                }

                // 课程总览列表页面
                entry<Route.CourseOverview> {
                    CourseOverviewScreen(
                        onBack = {
                            backStack.removeLastOrNull()
                        }
                    )
                }

                // 关于应用二级页面
                entry<Route.About> {
                    AboutScreen(
                        onBack = {
                            backStack.removeLastOrNull()
                        }
                    )
                }
            }
        }
    }
}
