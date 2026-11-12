package cn.ziyis.cqustwifi

import top.msfxp.schedule.data.api.CqustVpnCrypto

object CampusServiceRoutes {
    fun authenticated(site:CampusSite)=site.viaPortal||site.name in setOf("信息门户","教务系统","实践教学")
    fun destination(site:CampusSite):String = when(site.name){
        "教务系统"->CqustVpnCrypto.buildVpnUrl("http","jwnew.cqust.edu.cn","/eams/unifiedLogin.action")
        "实践教学"->CqustVpnCrypto.buildVpnUrl("http","sjjx.cqust.edu.cn","/cqust_sso/")
        else->CqustVpnCrypto.buildVpnUrl("https","casp.cqust.edu.cn","/index.html#/")
    }
}
