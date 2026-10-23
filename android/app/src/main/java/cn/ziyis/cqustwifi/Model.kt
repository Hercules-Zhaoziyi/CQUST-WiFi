package cn.ziyis.cqustwifi

enum class LinkState { OFFLINE, NEED_AUTH, CONNECTING, CONNECTED, OPEN, UNKNOWN }
data class Preferences(
    val startup:Boolean=false, val autoLogin:Boolean=false, val keepConnected:Boolean=false,
    val interval:Int=10, val animations:Boolean=true, val theme:String="system",
    val vpnAuto:Boolean=false
)
data class ViewState(
    val status:LinkState=LinkState.UNKNOWN, val username:String="", val name:String="",
    val ip:String="", val started:Long=0, val accounts:List<String> = emptyList(),
    val prefs:Preferences=Preferences(), val busy:Boolean=false, val message:String="",
    val logs:List<String> = emptyList(), val vpn:Boolean=false, val vpnMessage:String="未连接电脑代理",
    val sharing:Boolean=false, val sharingAddress:String="", val sharingUser:String="",
    val sharingSecret:Boolean=false, val transfer:String=""
)
data class Account(val username:String, val password:String, val remember:Boolean)
data class VpnProfile(val host:String="", val port:Int=7890, val username:String="", val password:String="", val networkKey:String="",val campusPassword:Boolean=true)
data class ProxyOptions(val port:Int=7890, val mode:String="mixed", val lan:Boolean=true,
    val username:String, val password:String, val campusPassword:Boolean)
data class CampusSite(val name:String, val url:String, val icon:String, val viaPortal:Boolean)
data class UpdateInfo(val version:String, val notes:String, val download:String?, val url:String, val newer:Boolean,val digest:String="",val size:Long=0)
