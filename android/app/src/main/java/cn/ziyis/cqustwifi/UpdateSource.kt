package cn.ziyis.cqustwifi

import java.net.URI

object UpdateSource {
    const val GITEE="https://gitee.com/ithercules/CQUST-WiFi"
    const val GITHUB="https://github.com/Hercules-Zhaoziyi/CQUST-WiFi"
    val feeds=listOf("$GITEE/raw/main/updates/android.json","https://api.github.com/repos/Hercules-Zhaoziyi/CQUST-WiFi/releases?per_page=30")
    fun asset(address:String):Boolean=runCatching {
        val uri=URI(address)
        uri.scheme=="https"&&uri.userInfo==null&&(uri.port==-1||uri.port==443)&&
            (address.startsWith("$GITEE/releases/download/")||address.startsWith("$GITHUB/releases/download/"))
    }.getOrDefault(false)
}
