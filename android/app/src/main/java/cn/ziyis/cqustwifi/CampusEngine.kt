package cn.ziyis.cqustwifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit

class AuthIssue(val kind:String, message:String, val image:ByteArray?=null,
                val services:List<String> = emptyList()):Exception(message)
data class CampusSession(val client:OkHttpClient,val page:String,val api:String,
    val index:String,val username:String,var name:String="",var ip:String="",
    var keepaliveSeconds:Long=0,var nextKeepalive:Long=0)
private data class Challenge(val client:OkHttpClient,val page:String,val api:String,
    val query:String,val encrypt:Boolean,val exponent:String,val modulus:String,
    val service:String,val prefix:String,val captchaUrl:String?)
sealed interface Detection {
    data class Portal(val url:String):Detection
    data object Online:Detection
    data object Unknown:Detection
}

/** Auth traffic is always pinned to the actual Wi-Fi, never a VPN/system proxy. */
class CampusEngine(private val context:Context,private val diagnostic:(String)->Unit) {
    val connectivity=context.getSystemService(ConnectivityManager::class.java)
    private var challenge:Challenge?=null
    private var hint:String?=null
    private var hintNetwork:String=""
    private var challengeNetwork:String=""
    fun wifi():Network?=connectivity.allNetworks.firstOrNull {
        val c=connectivity.getNetworkCapabilities(it)
        c?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true&&!c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
    fun ip(net:Network?=wifi()):String=net?.let{connectivity.getLinkProperties(it)?.linkAddresses
        ?.firstOrNull{a->a.address.address.size==4&&!a.address.isLoopbackAddress}?.address?.hostAddress}.orEmpty()
    fun networkKey(net:Network?=wifi()):String=net?.let {
        val p=connectivity.getLinkProperties(it)
        val gateway=p?.routes?.firstOrNull{r->r.isDefaultRoute}?.gateway?.hostAddress.orEmpty()
        gateway+"/"+p?.linkAddresses?.firstOrNull{a->a.address.address.size==4}?.prefixLength
    }.orEmpty()
    fun client(net:Network,timeout:Long=5000):OkHttpClient {
        val cookies=mutableListOf<Cookie>()
        return OkHttpClient.Builder().proxy(Proxy.NO_PROXY).socketFactory(net.socketFactory)
            .dns(object:Dns{override fun lookup(hostname:String)=net.getAllByName(hostname).toList()})
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(timeout,TimeUnit.MILLISECONDS).readTimeout(timeout,TimeUnit.MILLISECONDS)
            .callTimeout(timeout,TimeUnit.MILLISECONDS)
            .cookieJar(object:CookieJar{
                override fun saveFromResponse(url:HttpUrl,c:List<Cookie>){synchronized(cookies){
                    for(cookie in c){cookies.removeAll{it.name==cookie.name&&it.domain==cookie.domain};cookies.add(cookie)}
                }}
                override fun loadForRequest(url:HttpUrl):List<Cookie> = synchronized(cookies){cookies.filter{it.matches(url)}}
            }).build()
    }
    private fun bodyText(bytes:ByteArray):String {
        val utf=Charsets.UTF_8.newDecoder()
        return runCatching{utf.decode(java.nio.ByteBuffer.wrap(bytes)).toString()}
            .getOrElse{String(bytes,charset("GBK"))}.removePrefix("\uFEFF")
    }
    private fun bounded(response:okhttp3.Response,limit:Long=131072):ByteArray {
        val bytes=response.peekBody(limit+1).bytes()
        if(bytes.size>limit)throw AuthIssue("page","认证响应过大，已停止处理。")
        return bytes
    }
    private fun follow(http:OkHttpClient,start:String,deadline:Long):Pair<String,String>? {
        var url=start;val seen=mutableSetOf<String>()
        repeat(7) {
            if(!seen.add(url)||System.currentTimeMillis()>=deadline)return null
            if(!Protocol.trusted(url)&&url !in Protocol.entries)return null
            val remaining=(deadline-System.currentTimeMillis()).coerceIn(1,4000)
            val request=Request.Builder().url(url).header("Cache-Control","no-cache")
                .header("User-Agent","Mozilla/5.0 CQUST-WiFi-Android").build()
            val response=http.newBuilder().callTimeout(remaining,TimeUnit.MILLISECONDS).build().newCall(request).execute()
            response.use {r->
                diagnostic("入口 "+URI(url).host+" · HTTP "+r.code)
                val location=r.header("Location")
                if(r.code in 300..399&&location!=null){url=URI(url).resolve(location).toString();return@repeat}
                if(r.code!=200)return null
                val html=bodyText(bounded(r))
                if(Protocol.candidate(url))return url to html
                if(Protocol.trusted(url)&&URI(url).path.endsWith("/success.jsp")) {
                    throw AuthIssue("online","当前设备已有校园网会话，请刷新状态，或先在官方页面下线。")
                }
                val document=Jsoup.parse(html)
                val refresh=document.select("meta[http-equiv]").firstOrNull{
                    it.attr("http-equiv").equals("refresh",true)
                }?.attr("content")?.let{Regex("(?i)url\\s*=\\s*(.+)").find(it)?.groupValues?.get(1)?.trim('\'','"',' ')}
                val next=refresh?:Protocol.redirect(html)?:return null
                url=URI(url).resolve(next).toString()
            }
        }
        return null
    }
    fun detect(net:Network):Detection {
        val http=client(net);val deadline=System.currentTimeMillis()+14000
        var online=0
        for(entry in Protocol.entries.take(1)+Protocol.entries.drop(2).take(5)) {
            if(System.currentTimeMillis()>=deadline)break
            try {
                val req=Request.Builder().url(entry).header("Cache-Control","no-cache").build()
                http.newBuilder().callTimeout((deadline-System.currentTimeMillis()).coerceIn(1,3000),TimeUnit.MILLISECONDS)
                    .build().newCall(req).execute().use{r->
                    val location=r.header("Location")?.let{URI(entry).resolve(it).toString()}
                    if(location!=null&&Protocol.trusted(location)&&!URI(location).path.endsWith("/success.jsp"))return Detection.Portal(location)
                    val text=bodyText(bounded(r,8192))
                    val embedded=Protocol.redirect(text)?.let{URI(entry).resolve(it).toString()}
                    if(embedded!=null&&Protocol.trusted(embedded)&&!URI(embedded).path.endsWith("/success.jsp"))return Detection.Portal(embedded)
                    if(Protocol.trusted(entry)&&r.code==200&&(text.contains("InterFace.do")||text.contains("net_access_type")))return Detection.Portal(entry)
                    if(r.code==204||(r.code==200&&text.trim()=="Microsoft Connect Test"))online++
                }
            }catch(_:Exception){diagnostic("探针 "+URI(entry).host+" · 暂不可判定")}
        }
        return if(online>=2)Detection.Online else Detection.Unknown
    }
    private fun post(http:OkHttpClient,api:String,page:String,method:String,body:String):JSONObject {
        require(Protocol.trusted(api)){"认证接口不受信任"}
        val target=api.substringBefore('?')+"?method="+method+if(method=="getServices")"&"+body else ""
        val request=Request.Builder().url(target).header("Referer",page)
            .header("Origin",URI(page).let{"${it.scheme}://${it.rawAuthority}"})
            .post(body.toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaType())).build()
        http.newCall(request).execute().use{r->
            if(r.code in 300..399)throw AuthIssue("server","认证接口发生异常跳转，已停止发送凭据。")
            if(!r.isSuccessful)throw AuthIssue("network","校园认证接口暂不可达。")
            return runCatching{
                val text=bodyText(bounded(r)).trim()
                if(method=="getServices"&&text.startsWith("["))JSONObject().put("services",org.json.JSONArray(text))else JSONObject(text)
            }.getOrElse{throw AuthIssue("server","认证服务器返回格式异常。")}
        }
    }
    private fun discover(net:Network,manual:String):Pair<String,String> {
        if(manual.isNotBlank()&&!Protocol.trusted(manual))throw AuthIssue("url","仅接受学校认证域名或认证 IP 的网址。")
        val key=networkKey(net)+"/"+ip(net)
        if(key!=hintNetwork){hint=null;hintNetwork=key}
        val entries=if(manual.isNotBlank())listOf(manual)else(listOfNotNull(hint)+Protocol.entries).distinct()
        val http=client(net);val deadline=System.currentTimeMillis()+16000
        var found:Pair<String,String>?=null
        for(entry in entries) {
            if(System.currentTimeMillis()>=deadline)break
            try {found=follow(http,entry,deadline);if(found!=null)break}
            catch(e:AuthIssue){if(e.kind=="online")throw e}
            catch(_:Exception){diagnostic("入口 "+runCatching{URI(entry).host}.getOrDefault("学校")+" · 未取得跳转")}
        }
        val page=found?:throw AuthIssue("url",context.getString(R.string.auth_probe_failed))
        hint=page.first
        return page
    }
    fun probePortal(net:Network):Detection=try{
        Detection.Portal(discover(net,"").first)
    }catch(e:AuthIssue){if(e.kind=="online")Detection.Online else Detection.Unknown}
    private fun prepare(net:Network,manual:String,selected:String):Challenge {
        val(page,html)=discover(net,manual)
        val query=URI(page).rawQuery.orEmpty();val doc=Jsoup.parse(html)
        val values=doc.select("input").associate{it.id().ifBlank{it.attr("name")} to it.attr("value")}.toMutableMap()
        val init=Regex("""AuthInterFace\.init\(\s*["']([^"']*)["']""").find(html)?.groupValues?.get(1)?:"./"
        val api=URI(page).resolve(init+"InterFace.do").toString()
        if(!Protocol.trusted(api))throw AuthIssue("server","页面提供的认证接口不受信任。")
        val info=post(http,api,page,"pageInfo","queryString="+Protocol.component(query))
        for(k in listOf("passwordEncrypt","publicKeyExponent","publicKeyModulus"))
            if(info.has(k)&&!info.isNull(k))values[k]=info.opt(k).toString()
        val encrypt=when(values["passwordEncrypt"]?.lowercase()){
            "true"->true;"false"->false;else->throw AuthIssue("page","未取得页面的密码处理方式，请重新探测。")
        }
        val exponent=values["publicKeyExponent"].orEmpty();val modulus=values["publicKeyModulus"].orEmpty()
        if(encrypt&&(!exponent.matches(Regex("[0-9a-fA-F]+"))||!modulus.matches(Regex("[0-9a-fA-F]+"))))
            throw AuthIssue("page","未取得有效认证公钥，请重新探测。")
        var services=doc.select("select#net_access_type option,select#service option,select[name=service] option")
            .map{it.attr("value")}.filter{it.isNotBlank()}.distinct()
        if(services.isEmpty()) {
            val response=post(http,api,page,"getServices","queryString="+Protocol.component(query))
            val array=response.optJSONArray("services")?:response.optJSONArray("serviceList")?:
                runCatching{org.json.JSONArray(response.optString("services"))}.getOrNull()
            if(array!=null)services=(0 until array.length()).mapNotNull{array.optJSONObject(it)?.optString("serviceName")}.filter{it.isNotBlank()}
        }
        if(services.isEmpty())values["net_access_type"]?.takeIf{it.isNotBlank()}?.let{services=listOf(it)}
        val service=when {selected in services->selected;services.size==1->services.first()
            services.isEmpty()->throw AuthIssue("page","未取得校园网服务列表，请重新探测。")
            else->throw AuthIssue("services","请选择认证页面提供的服务。",services=services)}
        if(info.optString("isOperatorPwd")=="true"||info.optString("isOperatorUserId")=="true")
            throw AuthIssue("page","当前服务要求运营商账号，请选择普通校园网服务。")
        val prefix=if(doc.getElementById("Tj_yes")?.attr("style")?.contains("display:block")==true)
            Regex("""prefixValue\s*=\s*["']([^"']*)""").find(html)?.groupValues?.get(1).orEmpty() else ""
        val captcha=info.optString("validCodeUrl").takeIf{it.isNotBlank()&&it!="null"}?.let{URI(page).resolve(it).toString()}
        if(captcha!=null&&!Protocol.trusted(captcha))throw AuthIssue("page","验证码地址不受信任。")
        hint=page
        return Challenge(http,page,api,query,encrypt,exponent,modulus,service,prefix,captcha)
    }
    fun captcha():ByteArray {
        val c=challenge?:throw AuthIssue("url","认证会话已过期，请重新登录。")
        var url=c.captchaUrl?:throw AuthIssue("url","当前会话没有验证码。")
        url+=(if(url.contains('?'))"&" else "?")+"_captcha="+System.nanoTime()
        repeat(4) {
            if(!Protocol.trusted(url))throw AuthIssue("captcha","验证码地址不受信任。")
            c.client.newCall(Request.Builder().url(url).header("Referer",c.page).header("Cache-Control","no-cache").build()).execute().use{r->
                if(r.isRedirect){url=URI(url).resolve(r.header("Location")?:throw AuthIssue("captcha","验证码跳转无效。")).toString();return@repeat}
                val bytes=bounded(r,1_000_000)
                val image=bytes.take(3).map{it.toInt()and 255}
                if(r.isSuccessful&&(image==listOf(137,80,78)||image.take(2)==listOf(255,216)||image==listOf(71,73,70)))return bytes
                throw AuthIssue("captcha","验证码图片加载失败，请刷新。")
            }
        }
        throw AuthIssue("captcha","验证码跳转次数过多，请重新登录。")
    }
    fun login(account:Account,manual:String="",selected:String="",code:String="",reuse:Boolean=false):CampusSession {
        val net=wifi()?:throw AuthIssue("network","请先连接校园 Wi-Fi，移动数据不能代替校园网认证。")
        val key=networkKey(net)+"/"+ip(net)
        if(reuse&&challengeNetwork!=key){challenge=null;throw AuthIssue("url","Wi-Fi 已改变，请重新探测认证入口。")}
        val c=if(reuse)challenge?:prepare(net,manual,selected)else prepare(net,manual,selected)
        challenge=c
        challengeNetwork=key
        if(c.captchaUrl!=null&&code.isBlank())throw AuthIssue("captcha","请输入认证图片中的验证码。",runCatching{captcha()}.getOrNull())
        val body=Protocol.loginBody(account.username,account.password,c.service,c.query,c.encrypt,c.exponent,c.modulus,code,c.prefix)
        val result=post(c.client,c.api,c.page,"login",body)
        if(result.optString("result")!="success") {
            val captchaUrl=result.optString("validCodeUrl").takeIf{it.isNotBlank()&&it!="null"}
            if(captchaUrl!=null){val u=URI(c.page).resolve(captchaUrl).toString();if(Protocol.trusted(u)){challenge=c.copy(captchaUrl=u);throw AuthIssue("captcha","请填写验证码。",runCatching{captcha()}.getOrNull())}}
            val message=result.optString("message").lowercase()
            throw AuthIssue("credentials",if(listOf("password","密码","用户不存在","账号不存在").any{message.contains(it)})"服务器拒绝账号或密码，请核对。"
                else if(message.contains("online")||message.contains("在线"))"当前账号或设备已在线，请刷新或先在官方页面下线。" else "校园认证未通过，请检查账号权限和服务。")
        }
        val index=result.optString("userIndex").takeIf{it.matches(Regex("[A-Za-z0-9_.:-]{1,1024}"))}
            ?:throw AuthIssue("server","服务器未返回有效会话，暂未确认登录结果。")
        val session=CampusSession(c.client,c.page,c.api,index,c.prefix+account.username,ip=ip(net))
        val minutes=result.optDouble("keepaliveInterval",0.0)
        session.keepaliveSeconds=if(minutes in 0.01..1440.0)(minutes*60).toLong()else 0
        var success=false
        repeat(4){if(!success){success=status(session);if(!success)Thread.sleep(700)}}
        if(!success)throw AuthIssue("network","登录响应成功，但尚未确认在线，请稍后刷新。")
        challenge=null
        return session
    }
    fun status(session:CampusSession):Boolean {
        val data=post(session.client,session.api,session.page,"getOnlineUserInfo","userIndex="+Protocol.component(session.index))
        if(data.optString("result") !in listOf("success","wait"))return false
        val user=data.optString("userId").takeIf{it.isNotBlank()&&it!="null"}
        if(user!=null&&user!=session.username)return false
        session.name=listOf("userName","realName","displayName","name").map{data.optString(it)}
            .firstOrNull{it.isNotBlank()&&it!="null"}.orEmpty()
        data.optString("userIp").takeIf{it.isNotBlank()&&it!="null"}?.let{session.ip=it}
        val now=System.currentTimeMillis()
        if(session.keepaliveSeconds>0&&now>=session.nextKeepalive) {
            post(session.client,session.api,session.page,"keepalive","userIndex="+Protocol.component(session.index))
            session.nextKeepalive=now+session.keepaliveSeconds*1000
        }
        return true
    }
    fun logout(session:CampusSession) {
        val result=post(session.client,session.api,session.page,"logout","userIndex="+Protocol.component(session.index))
        if(result.optString("result")!="success")throw AuthIssue("server","服务器未确认下线，已保留当前状态。")
        challenge=null
    }
}
