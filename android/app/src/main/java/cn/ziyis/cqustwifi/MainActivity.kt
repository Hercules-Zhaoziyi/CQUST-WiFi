package cn.ziyis.cqustwifi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity:ComponentActivity() {
    private val vpnPermission=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        if(it.resultCode==RESULT_OK)startVpn()else Repository.message("VPN 授权未完成，未启动转发。")
    }
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        setContent{CampusUi(onVpn={if(Repository.state.value.vpn){
            Repository.vpnStoppedByUser();startService(Intent(this,CampusVpnService::class.java).setAction("STOP"))
        }else{val request=VpnService.prepare(this);if(request!=null)vpnPermission.launch(request)else startVpn()}},
            onNotifications={
                val preferences=getSharedPreferences("permissions",MODE_PRIVATE)
                if(android.os.Build.VERSION.SDK_INT>=33&&androidx.core.content.ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED&&(!preferences.getBoolean("notification_asked",false)||shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))){
                    preferences.edit().putBoolean("notification_asked",true).apply();notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
            })}
        Repository.ensureMonitor()
    }
    override fun onResume(){super.onResume();AppUpdates.onResume()}
    private fun startVpn(){runCatching{ContextCompat.startForegroundService(this,Intent(this,CampusVpnService::class.java))}
        .onFailure{Repository.message("系统暂不允许启动 VPN，请保持应用在前台后重试。")}}
}
fun openLink(context:Context,url:String){
    val uri=runCatching{Uri.parse(url)}.getOrNull()?:return
    if(uri.scheme !in listOf("http","https")||uri.host.isNullOrEmpty()||uri.userInfo!=null)return
    runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
        .onFailure{Repository.message("没有可打开此链接的浏览器。")}
}
fun batterySettings(context:Context){runCatching{
    context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.onFailure{context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}}
