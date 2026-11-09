package top.msfxp.schedule.di

import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import top.msfxp.schedule.data.api.CqustSyncManager
import top.msfxp.schedule.data.db.MIGRATION_1_2
import top.msfxp.schedule.data.db.ScheduleDatabase
import top.msfxp.schedule.data.repository.ExamRepository
import top.msfxp.schedule.data.repository.ScheduleRepository
import top.msfxp.schedule.data.repository.SettingsRepository
import top.msfxp.schedule.service.CalendarSyncHelper
import top.msfxp.schedule.service.CourseAlarmObserver
import top.msfxp.schedule.ui.courses.CourseOverviewViewModel
import top.msfxp.schedule.ui.login.CqustLoginViewModel
import top.msfxp.schedule.ui.schedule.WeeklyScheduleViewModel
import top.msfxp.schedule.ui.settings.AboutViewModel
import top.msfxp.schedule.ui.settings.PersonalizationViewModel
import top.msfxp.schedule.ui.settings.SettingsViewModel

// 应用 Koin 依赖注入模块
val appModule = module {
    // Room 数据库
    single {
        Room.databaseBuilder(
            get(),
            ScheduleDatabase::class.java,
            "cqust_schedule_database"
        ).addMigrations(MIGRATION_1_2)
            .build()
    }
    single { get<ScheduleDatabase>().scheduleDao() }
    single { get<ScheduleDatabase>().examDao() }

    // 仓库层
    single { SettingsRepository(get()) }
    single { ScheduleRepository(get()) }
    single { ExamRepository(get(), get()) }

    // 服务与同步组件
    single { CalendarSyncHelper(get(), get(), get(), get()) }
    single { CqustSyncManager(get(), get(), get()) }

    // 课表与闹钟调度观察器
    single(createdAtStart = true) {
        CourseAlarmObserver(androidContext(), get(), get(), get(), get()).apply {
            startObserving()
        }
    }

    // ViewModel 视图模型
    viewModel { WeeklyScheduleViewModel(androidContext(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(androidContext(), get(), get(), get(), get()) }
    viewModel { CqustLoginViewModel(androidContext(), get(), get()) }
    viewModel { CourseOverviewViewModel(get(), get(), get()) }
    viewModel { PersonalizationViewModel(get(), get()) }
    viewModel { AboutViewModel(get()) }
}
