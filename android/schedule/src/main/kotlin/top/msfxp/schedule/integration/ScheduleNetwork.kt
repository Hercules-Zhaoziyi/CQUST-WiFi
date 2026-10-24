package top.msfxp.schedule.integration

import java.net.URI
import java.net.Proxy
import okhttp3.OkHttpClient

object ScheduleNetwork {
    @Volatile var clientProvider:(()->OkHttpClient)?=null
    private val hosts=setOf("web.cqust.edu.cn","cas.cqust.edu.cn","casp.cqust.edu.cn",
        "authserver.cqust.edu.cn","sso.cqust.edu.cn","ids.cqust.edu.cn",
        "jwnew.cqust.edu.cn","sjjx.cqust.edu.cn","icon.cqust.edu.cn")

    fun client():OkHttpClient=clientProvider?.invoke()?:OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()

    fun trusted(url:String):Boolean=runCatching {
        val uri=URI(url)
        uri.rawUserInfo==null&&uri.host?.lowercase() in hosts&&when(uri.scheme?.lowercase()){
            "https"->uri.port in listOf(-1,443)
            "http"->uri.port in listOf(-1,80)
            else->false
        }
    }.getOrDefault(false)
}
