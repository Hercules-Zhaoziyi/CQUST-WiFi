package cn.ziyis.cqustwifi

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.*
import android.os.IBinder
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*

class MonitorService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val callback=object:ConnectivityManager.NetworkCallback(){
        override fun onAvailable(network:Network){scope.launch{delay(1500);Repository.tick()}}
        override fun onLost(network:Network){Repository.refresh()}
        override fun onCapabilitiesChanged(network:Network,caps:NetworkCapabilities){scope.launch{delay(1000);Repository.tick()}}
    }
    override fun onCreate() {
        super.onCreate()
        try {
            ServiceCompat.startForeground(this,10,Notifications.build(this,"后台监测校园网连接"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            val request=NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
            Repository.engine.connectivity.registerNetworkCallback(request,callback)
            scope.launch{
                while(isActive){
                    Repository.tick()
                    val seconds=Repository.state.value.prefs.interval
                    delay(seconds*1000L)
                }
            }
        }catch(_:Exception){Repository.message("系统限制后台服务，请保持应用开启，并检查通知与电池设置。");stopSelf()}
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="STOP"){
            val p=Repository.state.value.prefs
            Repository.preferences(p.copy(autoLogin=false,keepConnected=false,vpnAuto=false))
            Repository.stopSharing();stopSelf();return START_NOT_STICKY
        }
        return START_STICKY
    }
    override fun onDestroy(){scope.cancel();runCatching{Repository.engine.connectivity.unregisterNetworkCallback(callback)};super.onDestroy()}
    override fun onBind(intent:Intent?):IBinder?=null
}
