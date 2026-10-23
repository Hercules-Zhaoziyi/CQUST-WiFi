package cn.ziyis.cqustwifi

import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

object ProxyQr {
    fun encode(profile:VpnProfile):String {
        val raw=JSONObject().put("type","cqust-wifi-proxy").put("version",1).put("protocol","socks5")
            .put("host",profile.host).put("port",profile.port).put("username",profile.username).put("password",profile.password).toString()
        parse(raw)
        return raw
    }
    fun parse(raw:String):VpnProfile {
        require(raw.length in 1..4096){"二维码内容无效"}
        val profile=if(raw.startsWith("{")){
            val o=JSONObject(raw)
            require(o.optString("type")=="cqust-wifi-proxy"&&o.optInt("version")==1&&o.optString("protocol","socks5")=="socks5"){"不支持此代理配置格式"}
            VpnProfile(o.getString("host"),o.getInt("port"),o.getString("username"),o.getString("password"),campusPassword=false)
        }else{
            val u=URI(raw);require(u.scheme=="socks5"&&u.rawUserInfo!=null&&u.rawQuery==null&&u.rawFragment==null){"需要 SOCKS5 代理配置二维码"}
            val auth=u.rawUserInfo.split(':',limit=2);require(auth.size==2)
            fun decode(s:String)=URLDecoder.decode(s.replace("+","%2B"),"UTF-8")
            VpnProfile(u.host.orEmpty(),u.port,decode(auth[0]),decode(auth[1]),campusPassword=false)
        }
        require(Protocol.privateV4(profile.host)&&profile.port in 1024..65535){"仅允许连接局域网电脑地址"}
        require(profile.username.isNotBlank()&&profile.password.isNotEmpty()&&profile.username.toByteArray().size<=255&&profile.password.toByteArray().size<=255&&
            (profile.username+profile.password).none{it.isISOControl()}){"代理凭据无效"}
        return profile
    }
}
