package cn.ziyis.cqustwifi

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import top.msfxp.schedule.data.api.CqustSyncManager
import top.msfxp.schedule.data.api.LocalCrypto
import top.msfxp.schedule.data.repository.ScheduleRepository
import top.msfxp.schedule.data.repository.SettingsRepository
import top.msfxp.schedule.data.model.Semester
import top.msfxp.schedule.data.model.SemesterDateHelper
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.theme.ThemeController

object ScheduleStore {
    val busy=MutableStateFlow(false)
    val message=MutableStateFlow("")
    val completed=MutableSharedFlow<Unit>(extraBufferCapacity=1)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val migrationLock=Mutex()
    private fun <T:Any> service(type:kotlin.reflect.KClass<T>):T=GlobalContext.get().get(type)
    suspend fun forgetSavedPassword(user:String){service(CqustSyncManager::class).forgetSavedPassword(user)}
    fun sync(user:String,password:String,remember:Boolean=false){
        if(!busy.compareAndSet(false,true))return
        scope.launch{
            try{
                require(user.isNotBlank()&&password.isNotEmpty()){Repository.app.getString(R.string.schedule_credentials_required)}
                migrateLegacy()
                val manager=service(CqustSyncManager::class)
                val result=manager.syncCourses(user.trim(),password,allowEmpty=false,rememberPassword=remember,onProgress={message.value=it})
                result.getOrThrow()
                Repository.rememberIdentity(user.trim(),password,remember)
                message.value=Repository.app.getString(R.string.schedule_sync_done)
                completed.emit(Unit)
            }catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){message.value=Repository.app.getString(R.string.schedule_sync_failed)}
            finally{busy.value=false}
        }
    }

    suspend fun migrateLegacy()=migrationLock.withLock{
        val prefs=Repository.app.getSharedPreferences("schedule-migration",0)
        if(prefs.getBoolean("complete",false))return@withLock
        val settings=service(SettingsRepository::class)
        val repository=service(ScheduleRepository::class)
        if(settings.getAppSettingsOnce().isLoggedIn||repository.getCurrentSemesterOnce()!=null){
            prefs.edit().putBoolean("complete",true).apply();return@withLock
        }
        val old=Vault(Repository.app,"schedule.enc").read()
        val array=old.optJSONArray("courses")
        if(array!=null&&array.length()>0){
            val table=repository.getOrCreateDefaultTableId()
            val decoded=LegacyTimetable.decode(old,table)
            val start=old.optString("startDate").ifBlank{SemesterDateHelper.calculateDefaultStartDate().toString()}
            val semester=Semester(id="legacy",schoolYear="",name="",label=old.optString("semester"),startDate=start,totalWeeks=30,isCurrent=true)
            repository.saveSchedule(table,decoded.first,decoded.second)
            repository.saveSemesters(listOf(semester));repository.setActiveSemester("legacy")
            settings.saveFullProfileAndCredentials(old.optString("user"),old.optString("name"),"", "",old.optString("class"),"")
        }
        prefs.edit().putBoolean("complete",true).apply()
    }
}

@Composable fun SchedulePage(onLogin:()->Unit,onAbout:()->Unit,controller:ThemeController){
    var ready by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){
        withContext(Dispatchers.IO){runCatching{ScheduleStore.migrateLegacy()}.onFailure{Repository.message(Repository.app.getString(R.string.schedule_migration_failed))}}
        ready=true
    }
    if(ready)top.msfxp.schedule.App(onSharedLogin=onLogin,onHostAbout=onAbout,controller=controller)
    else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){InfiniteProgressIndicator()}
}
