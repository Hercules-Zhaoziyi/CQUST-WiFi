package top.msfxp.schedule.data.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// 仅接收校方已签发的 CAS 回调或网关提升地址。
object PortalBrowserHandoff {
    fun accepts(address:String):Boolean {
        val url=address.toHttpUrlOrNull()?:return false
        if(url.scheme!="https"||url.host!="web.cqust.edu.cn"||url.port!=443||url.username.isNotEmpty()||url.password.isNotEmpty()||url.fragment!=null)return false
        val prefix="/https/${CqustVpnCrypto.vpnToken("web.cqust.edu.cn") }"
        val path=url.encodedPath
        val tokenPaths=setOf("/token-login","/wengine-vpn-token-login","$prefix/token-login","$prefix/wengine-vpn-token-login")
        if(path in tokenPaths)return url.querySize==1&&url.queryParameterNames==setOf("token")&&url.queryParameter("token")?.let{it.isNotEmpty()&&it.length<=4096}==true
        return path in setOf("/login","$prefix/login")&&url.querySize==2&&url.queryParameterNames==setOf("ticket","cas_login")&&url.queryParameter("cas_login")=="true"&&url.queryParameter("ticket")?.let{it.startsWith("ST-")&&it.length<=4096}==true
    }
}
