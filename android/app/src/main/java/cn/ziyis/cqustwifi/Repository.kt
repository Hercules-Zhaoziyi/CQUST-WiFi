package cn.ziyis.cqustwifi

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Repository {
    lateinit var app:Context;private set
    lateinit var engine:CampusEngine;private set
    private lateinit var vault:Vault
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val gate=Mutex()
    private var credentials=JSONObject()
    private val runtimeAccounts=java.util.concurrent.ConcurrentHashMap<String,Account>()
    private val mutable=MutableStateFlow(ViewState())
    val state=mutable.asStateFlow()
    val issues=MutableStateFlow<AuthIssue?>(null)
    private var session:CampusSession?=null
    private var manualPause=false
    @Volatile private var networkRequested=true
    private var lastAttempt=0L
    private var sharing:LanProxy?=null
    private var sharePassword:String?=null
    private var activeShareOptions:ProxyOptions?=null
    private var selectedService=""
    fun initialize(context:Context) {
        app=context.applicationContext;vault=Vault(app);engine=CampusEngine(app,::log)
        val error=runCatching{credentials=vault.read()}.exceptionOrNull()
        val p=app.getSharedPreferences("settings",Context.MODE_PRIVATE)
        networkRequested=Protocol.usesCampusLogin(p.getInt("loginPurpose",0))
        val prefs=Preferences(p.getBoolean("startup",false),p.getBoolean("autoLogin",false),
            p.getBoolean("keepConnected",false),p.getInt("interval",10).takeIf{it in listOf(5,10,120)}?:10,
            p.getBoolean("animations",true),p.getString("theme","system")?:"system",p.getBoolean("vpnAuto",false))
        val accounts=credentials.optJSONObject("accounts")?:JSONObject()
        val names=accounts.keys().asSequence().toList()
        mutable.value=ViewState(username=p.getString("username","").orEmpty(),accounts=names,prefs=prefs)
        if(error!=null)log("安全凭据暂不可读，请检查设备密钥；不会自动重置或上传凭据。")
        scope.launch{refresh()}
    }
    fun log(text:String) {
        val stamp=SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(Date())
        mutable.update{it.copy(logs=(it.logs+("$stamp  "+text)).takeLast(200))}
    }
    fun clearLogs(){mutable.update{it.copy(logs=emptyList())}}
    fun message(text:String){mutable.update{it.copy(message=text)}}
    fun dismissMessage(){mutable.update{it.copy(message="")}}
    fun preferences(next:Preferences) {
        val p=app.getSharedPreferences("settings",Context.MODE_PRIVATE).edit()
        p.putBoolean("startup",next.startup).putBoolean("autoLogin",next.autoLogin)
            .putBoolean("keepConnected",next.keepConnected).putInt("interval",next.interval)
            .putBoolean("animations",next.animations).putString("theme",next.theme)
            .putBoolean("vpnAuto",next.vpnAuto).apply()
        mutable.update{it.copy(prefs=next)}
        ensureMonitor()
    }
    fun ensureMonitor() {
        val s=state.value
        if((networkRequested&&(s.prefs.autoLogin||s.prefs.keepConnected))||s.prefs.vpnAuto||s.sharing) {
            runCatching{ContextCompat.startForegroundService(app,Intent(app,MonitorService::class.java))}
                .onFailure{message("系统暂不允许后台运行，请打开应用后重试，并检查通知与电池设置。")}
        } else app.stopService(Intent(app,MonitorService::class.java))
    }
    private fun account(user:String=state.value.username):Account? {
        runtimeAccounts[user]?.let{return it}
        val password=credentials.optJSONObject("accounts")?.optString(user).orEmpty()
        return if(user.isNotBlank()&&password.isNotEmpty())Account(user,password,true)else null
    }
    fun hasPassword(user:String=state.value.username)=account(user)!=null
    internal fun sharedAccount(user:String)=account(user)
    fun loginPurpose()=app.getSharedPreferences("settings",Context.MODE_PRIVATE).getInt("loginPurpose",0).coerceIn(0,2)
    fun loginPurpose(mode:Int){
        val safe=mode.coerceIn(0,2)
        networkRequested=Protocol.usesCampusLogin(safe)
        app.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putInt("loginPurpose",safe).apply()
        ensureMonitor()
    }
    fun rememberIdentity(user:String,password:String,remember:Boolean=true){scope.launch{gate.withLock{
        val accounts=credentials.optJSONObject("accounts")?:JSONObject().also{credentials.put("accounts",it)}
        if(remember)accounts.put(user,password)else accounts.remove(user)
        runCatching{vault.write(credentials)}.onFailure{message("身份认证已通过，但安全凭据保存失败。")}
        runtimeAccounts[user]=Account(user,password,remember)
        mutable.update{it.copy(accounts=(it.accounts+user).distinct())}
        if(session==null)select(user)
    }}}
    private fun select(user:String) {
        app.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putString("username",user).apply()
        mutable.update{it.copy(username=user,name="")}
    }
    fun chooseAccount(user:String){scope.launch{gate.withLock{
        if(session!=null){message("请先退出当前登录，再切换账号。");return@withLock}
        select(user);manualPause=true
    }}}
    fun forgetAccount(user:String){scope.launch{gate.withLock{
        if(session!=null&&state.value.username==user){message("请先退出当前账号。");return@withLock}
        runtimeAccounts.remove(user);credentials.optJSONObject("accounts")?.remove(user);vault.write(credentials)
        mutable.update{it.copy(accounts=it.accounts-user)}
        if(state.value.username==user)select("")
    }}}
    fun login(user:String,password:String,remember:Boolean,manual:String="",service:String="",code:String="",reuse:Boolean=false) {
        scope.launch {gate.withLock{
            if(session!=null){message("当前已连接，请先退出登录后再切换账号。");return@withLock}
            val existing=account(user)
            val a=Account(user.trim(),password.ifEmpty{existing?.password.orEmpty()},remember)
            if(a.username.isEmpty()||a.password.isEmpty()){message("请输入校园网账号和密码。");return@withLock}
            runtimeAccounts[a.username]=a;select(a.username);manualPause=false
            performLogin(a,manual,service,code,reuse)
        }}
    }
    private fun performLogin(a:Account,manual:String="",service:String="",code:String="",reuse:Boolean=false) {
        mutable.update{it.copy(busy=true,status=LinkState.CONNECTING)}
        try {
            val result=engine.login(a,manual,service.ifEmpty{selectedService},code,reuse)
            session=result;selectedService=service.ifEmpty{selectedService}
            val saved=credentials.optJSONObject("accounts")?:JSONObject().also{credentials.put("accounts",it)}
            if(a.remember)saved.put(a.username,a.password)else saved.remove(a.username)
            runCatching{vault.write(credentials)}.onFailure{message("已连接，但安全凭据保存失败；下次需重新输入。")}
            val accounts=(state.value.accounts+a.username).distinct()
            mutable.update{it.copy(status=LinkState.CONNECTED,name=result.name,ip=result.ip,
                started=System.currentTimeMillis(),accounts=accounts)}
            log("校园网已连接。");issues.value=null;ensureMonitor()
        }catch(e:AuthIssue){
            mutable.update{it.copy(status=LinkState.NEED_AUTH)}
            if(e.kind in listOf("captcha","url","services","page"))issues.value=e else message(e.message.orEmpty())
            log("认证未完成 · "+e.message.orEmpty())
        }catch(_:Exception){mutable.update{it.copy(status=LinkState.UNKNOWN)};message("校园网认证暂不可达，请检查 Wi-Fi 后重试。");log("认证请求未完成。")}
        finally{mutable.update{it.copy(busy=false)}}
    }
    fun refresh(){scope.launch{gate.withLock{refreshLocked()}}}
    private fun refreshLocked() {
        val net=engine.wifi()
        if(net==null){session=null;stopSharing();mutable.update{it.copy(status=LinkState.OFFLINE,ip="",name="",started=0)};return}
        mutable.update{it.copy(ip=engine.ip(net))}
        if(state.value.sharing&&state.value.sharingAddress.substringBeforeLast(':') !in listOf("127.0.0.1",engine.ip(net))){
            stopSharing();message("Wi-Fi 地址已改变，本机代理已停止；请重新开启并更新客户端地址。")
        }
        session?.let{s->
            try {
                if(engine.status(s)){mutable.update{it.copy(status=LinkState.CONNECTED,name=s.name,ip=s.ip)};return}
                session=null
                if(!state.value.prefs.keepConnected)manualPause=true
                mutable.update{it.copy(started=0,status=LinkState.NEED_AUTH)};log("检测到校园网会话已下线。")
            }catch(_:Exception){mutable.update{it.copy(status=LinkState.UNKNOWN)};log("暂无法确认在线状态，稍后再检查。");return}
        }
        if(manualPause){mutable.update{it.copy(status=LinkState.NEED_AUTH)};return}
        val detection=runCatching{engine.detect(net)}.getOrDefault(Detection.Unknown)
        val status=when(detection){is Detection.Portal->LinkState.NEED_AUTH;Detection.Online->LinkState.OPEN;Detection.Unknown->LinkState.UNKNOWN}
        mutable.update{it.copy(status=status)}
    }
    fun tick(){scope.launch{
        if(!gate.tryLock())return@launch
        try {
            val wasConnected=session!=null
            refreshLocked()
            val prefs=state.value.prefs
            val enabled=if(wasConnected)prefs.keepConnected else prefs.autoLogin||prefs.keepConnected
            if(networkRequested&&!manualPause&&enabled&&state.value.status==LinkState.NEED_AUTH&&
                System.currentTimeMillis()-lastAttempt>=10000&&issues.value==null) {
                lastAttempt=System.currentTimeMillis();account()?.let{performLogin(it)}
            }
            if(prefs.vpnAuto&&!state.value.vpn&&!state.value.sharing&&!manualPause) {
                val profile=vpnProfile()
                if(profile.host.isNotEmpty()&&profile.networkKey==engine.networkKey()&&engine.wifi()!=null&&
                    android.net.VpnService.prepare(app)==null) {
                    runCatching{ContextCompat.startForegroundService(app,Intent(app,CampusVpnService::class.java))}
                }
            }
        }finally{gate.unlock()}
    }}
    fun disconnect(logout:Boolean=false){scope.launch{gate.withLock{
        mutable.update{it.copy(busy=true)}
        try {
            session?.let{engine.logout(it)};session=null;manualPause=true
            mutable.update{it.copy(status=LinkState.NEED_AUTH,started=0,name="")}
            if(logout)select("")
            log(if(logout)"已退出校园网登录。"else"已手动断开；自动重连暂停，点击连接可恢复。")
        }catch(e:Exception){message((e as? AuthIssue)?.message?:"断开未确认，已保留原状态。")}
        finally{mutable.update{it.copy(busy=false)}}
    }}}
    fun refreshCaptcha(){scope.launch{try{issues.value=AuthIssue("captcha","请输入验证码。",engine.captcha())}
        catch(_:Exception){issues.value=AuthIssue("captcha","验证码图片加载失败，请刷新。")}}}
    fun retryAuth(url:String,service:String,code:String){scope.launch{gate.withLock{
        val a=account()?:return@withLock message("请先填写账号密码。")
        performLogin(a,url,service,code,issues.value?.kind=="captcha");selectedService=service
    }}}
    fun dismissIssue(){issues.value=null}
    fun vpnProfile():VpnProfile {
        val p=credentials.optJSONObject("vpn")?:return VpnProfile(username=state.value.username)
        return VpnProfile(p.optString("host"),p.optInt("port",7890),p.optString("username"),
            p.optString("password"),p.optString("networkKey"),p.optBoolean("campusPassword",true))
    }
    fun saveVpn(host:String,port:Int,user:String,password:String,useCampus:Boolean) {
        require(Protocol.privateV4(host)){"请填写电脑的局域网 IPv4 地址。"}
        require(port in 1024..65535){"端口应为 1024–65535。"}
        val secret=if(useCampus)account()?.password.orEmpty()else password
        val username=user.ifBlank{state.value.username}
        require(username.isNotBlank()&&secret.isNotBlank()){"请填写代理账号密码，或先保存校园网凭据。"}
        credentials.put("vpn",JSONObject().put("host",host).put("port",port).put("username",username)
            .put("password",secret).put("networkKey",engine.networkKey()).put("campusPassword",useCampus))
        vault.write(credentials);manualPause=false
    }
    fun vpnState(running:Boolean,text:String,transfer:String=""){mutable.update{it.copy(vpn=running,vpnMessage=text,transfer=transfer)}}
    fun vpnStoppedByUser(){manualPause=true;vpnState(false,"电脑代理已断开")}
    fun startSharing(options:ProxyOptions){scope.launch{gate.withLock{
        try {
            require(!state.value.vpn){"请先关闭“连接电脑”的 VPN。"}
            require(sharing==null){"请先关闭当前代理。"}
            val a=account();val user=options.username.ifBlank{a?.username.orEmpty()}
            val secret=if(options.campusPassword)a?.password.orEmpty()else options.password
            val net=engine.wifi()?:error("请先连接 Wi-Fi。")
            val ip=if(options.lan)engine.ip(net)else "127.0.0.1"
            require(!options.lan||Protocol.privateV4(ip)){"未找到可用的 Wi-Fi 局域网地址。"}
            val server=LanProxy(options.copy(username=user,password=secret),ip,
                resolve={net.getAllByName(it).toList()},connect={address->net.socketFactory.createSocket().apply{connect(address,5000)}},
                bindUdp={net.bindSocket(it)})
            server.start();sharing=server;sharePassword=if(options.campusPassword)null else secret
            activeShareOptions=options.copy(username=user,password=secret)
            mutable.update{it.copy(sharing=true,sharingAddress="$ip:${options.port}",sharingUser=user,sharingSecret=options.campusPassword)}
            log("本机代理已开启；设备仍需账号密码认证。");ensureMonitor()
        }catch(_:Exception){message("代理启动失败，请检查端口、账号密码和当前 Wi-Fi。")}
    }}}
    fun displayedSharePassword()=sharePassword
    fun sharingQr():String {
        val options=activeShareOptions?:error("请先开启本机共享")
        require(options.mode!="http"&&options.lan){"二维码连接需要开启局域网的 SOCKS5 或混合模式"}
        val host=state.value.sharingAddress.substringBeforeLast(':')
        require(Protocol.privateV4(host)){"局域网地址已失效，请重启共享"}
        return ProxyQr.encode(VpnProfile(host=host,port=options.port,username=options.username,password=options.password,campusPassword=false))
    }
    fun stopSharing(){sharing?.close();sharing=null;sharePassword=null;activeShareOptions=null;mutable.update{it.copy(sharing=false,sharingAddress="",sharingUser="",sharingSecret=false)}}
    fun sites():List<CampusSite> {
        val a=JSONArray(app.assets.open("sites.json").bufferedReader().readText())
        return (0 until a.length()).map{val o=a.getJSONObject(it);CampusSite(o.getString("name"),o.getString("url"),o.getString("icon"),o.optBoolean("viaPortal"))}
    }
    suspend fun checkUpdate():UpdateInfo=withContext(Dispatchers.IO){
        val client=ApplicationClients.client()
        val req=Request.Builder().url("https://api.github.com/repos/Hercules-Zhaoziyi/CQUST-WiFi/releases?per_page=30")
            .header("Accept","application/vnd.github+json").header("User-Agent","CQUST-WiFi-Android").build()
        client.newCall(req).execute().use{r->
            require(r.isSuccessful){"更新服务器暂不可用。"}
            val array=JSONArray(r.body?.string().orEmpty())
            for(i in 0 until array.length()){
                val release=array.getJSONObject(i);if(release.optBoolean("draft"))continue
                val assets=release.optJSONArray("assets")?:continue
                for(j in 0 until assets.length()){
                    val asset=assets.getJSONObject(j);val name=asset.optString("name")
                    if(!name.startsWith("CQUST-WiFi-Android-")||!name.endsWith(".apk"))continue
                    val url=asset.optString("browser_download_url")
                    if(!url.startsWith("https://github.com/Hercules-Zhaoziyi/CQUST-WiFi/releases/download/"))continue
                    val version=Regex("""\d+\.\d+\.\d+(?:-beta\d+)?""").find(name)?.value?:continue
                    return@withContext UpdateInfo(version,release.optString("body"),url,
                        release.optString("html_url"),Protocol.newer(version,BuildConfig.VERSION_NAME),asset.optString("digest"),asset.optLong("size"))
                }
            }
            UpdateInfo(BuildConfig.VERSION_NAME,"暂无适用于 Android 的新版本。",null,
                "https://github.com/Hercules-Zhaoziyi/CQUST-WiFi/releases",false)
        }
    }
}
