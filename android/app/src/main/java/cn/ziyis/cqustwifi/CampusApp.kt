package cn.ziyis.cqustwifi

import android.app.Application
import org.koin.android.ext.koin.androidContext

class CampusApp:Application() {
    override fun onCreate(){
        super.onCreate();Repository.initialize(this)
        top.msfxp.schedule.integration.ScheduleNetwork.clientProvider=ApplicationClients::client
        top.msfxp.schedule.integration.ScheduleIntegration.managedByHost=true
        org.koin.core.context.startKoin {
            androidContext(this@CampusApp)
            modules(top.msfxp.schedule.di.appModule)
        }
        top.msfxp.schedule.service.DndSchedulerWorker.enqueuePeriodicWork(this)
        top.msfxp.schedule.service.DndSchedulerWorker.triggerImmediately(this)
    }
}
