package cn.ziyis.cqustwifi

import java.math.BigInteger
import java.net.InetAddress
import java.net.URI
import java.security.SecureRandom

/** Pure JVM wire/security helpers. No Android or network access. */
object Protocol {
    fun usesCampusLogin(mode:Int)=mode==0||mode==2
    val entries=listOf("http://aaa.cqust.edu.cn/","http://aaa.cqust.edu.cn/eportal/index.jsp",
        "http://www.baidu.com/","http://www.qq.com/","http://connect.rom.miui.com/generate_204",
        "http://connect.hicloud.com/generate_204","http://www.msftconnecttest.com/connecttest.txt",
        "http://172.28.228.2/","http://123.123.123.123/")
    fun trusted(url:String):Boolean=runCatching {
        val u=URI(url); u.scheme in listOf("http","https") &&
            u.host in listOf("aaa.cqust.edu.cn","172.28.228.2") && u.rawUserInfo==null &&
            u.port in listOf(-1,80,443,8080)
    }.getOrDefault(false)
    fun candidate(url:String)=trusted(url)&&runCatching {
        val u=URI(url);u.path.endsWith("/index.jsp")&&Regex("(?i)(^|&)wlanuserip=[^&]+").containsMatchIn(u.rawQuery.orEmpty())
    }.getOrDefault(false)
    fun component(value:String):String=buildString {
        for(b in value.toByteArray(Charsets.UTF_8)){val n=b.toInt() and 255
            if(n in 65..90||n in 97..122||n in 48..57||n.toChar() in "-_.!~*'()")append(n.toChar())
            else append("%%%02X".format(n))
        }
    }
    fun twice(value:String)=component(component(value))
    fun encrypted(password:String,mac:String,exponent:String,modulus:String):String {
        val bytes=(password+">"+mac.ifBlank{"111111111"}).toByteArray()
        require(bytes.all{it.toInt() in 0..127}){"当前校园算法需要 ASCII 密码，其他字符请使用官方认证页。"}
        val n=BigInteger(modulus,16);val e=BigInteger(exponent,16)
        require(n.bitLength() in 17..8192&&e.signum()>0){"认证公钥无效"}
        val size=2*((n.bitLength()-1)/16)
        val reversed=bytes.reversedArray();val padded=reversed.copyOf(((reversed.size+size-1)/size)*size)
        return padded.toList().chunked(size).joinToString(" "){block->
            val hex=BigInteger(1,block.reversed().toByteArray()).modPow(e,n).toString(16)
            hex.padStart(((hex.length+3)/4)*4,'0')
        }
    }
    fun loginBody(user:String,password:String,service:String,query:String,encrypt:Boolean,
                  exponent:String="",modulus:String="",captcha:String="",prefix:String=""):String {
        require(user.isNotBlank()&&password.isNotEmpty()){"请输入账号密码"}
        require(captcha.isEmpty()||captcha.matches(Regex("[A-Za-z0-9]{1,16}"))){"验证码格式不正确"}
        val mac=Regex("(?i)(?:^|&)mac=([^&]+)").find(query)?.groupValues?.get(1).orEmpty()
        val secret=if(encrypt)encrypted(password,mac,exponent,modulus) else password
        return listOf("userId" to prefix+user.trim(),"password" to secret,"service" to service,
            "queryString" to query,"operatorPwd" to "","operatorUserId" to "")
            .joinToString("&"){(k,v)->"$k="+twice(v)}+"&validcode=$captcha&passwordEncrypt=$encrypt"
    }
    fun redirect(body:String):String? {
        val patterns=listOf(
            """(?i)(?:(?:window|top|self|document)\.)?location(?:\.href)?\s*=\s*["']([^"']+)""",
            """(?i)(?:window\.)?location\.(?:replace|assign)\(\s*["']([^"']+)""",
            """(?i)https?://[^"'\s<>]+/index\.jsp\?[^"'\s<>]+"""
        )
        for((i,p)in patterns.withIndex()){val m=Regex(p).find(body)?:continue;return (if(i==2)m.value else m.groupValues[1]).replace("&amp;","&")}
        return null
    }
    fun privateV4(host:String):Boolean {
        val raw=host.split('.')
        if(raw.size!=4||raw.any{part->part.toIntOrNull()?.let{it !in 0..255||part!=it.toString()}?:true})return false
        val parts=raw.map{it.toInt()}
        return parts.size==4&&(parts[0]==10||(parts[0]==172&&parts[1] in 16..31)||(parts[0]==192&&parts[1]==168))
    }
    fun publicTarget(ip:InetAddress):Boolean {
        if(ip.isAnyLocalAddress||ip.isLoopbackAddress||ip.isLinkLocalAddress||ip.isSiteLocalAddress||ip.isMulticastAddress)return false
        val b=ip.address.map{it.toInt() and 255}
        return if(b.size==4) b[0] in 1..223&&!(b[0]==100&&b[1] in 64..127)&&
            !(b[0]==192&&b[1]==0)&&!(b[0]==198&&b[1] in listOf(18,19,51))&&!(b[0]==203&&b[1]==0&&b[2]==113)
        else b[0] and 0xe0==0x20&&!(b[0]==0x20&&b[1]==2)&&
            !(b[0]==0x20&&b[1]==1&&b[2]==0)&&!(b.take(4)==listOf(0x20,1,0x0d,0xb8))
    }
    fun recommendedPassword():String {val chars="ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";val r=SecureRandom();return (1..12).map{chars[r.nextInt(chars.length)]}.joinToString("")}
    /** Route public IPv4 via VPN; keep campus/private/link-local networks direct. */
    fun vpnRoutes():List<Pair<String,Int>> {
        val excluded=listOf(0L to 0x00ffffffL,0x0a000000L to 0x0affffffL,
            0x64400000L to 0x647fffffL,0x7f000000L to 0x7fffffffL,
            0xa9fe0000L to 0xa9feffffL,0xac100000L to 0xac1fffffL,
            0xc0a80000L to 0xc0a8ffffL,0xe0000000L to 0xffffffffL)
        val out=mutableListOf<Pair<String,Int>>()
        fun addRange(start:Long,end:Long){var at=start
            while(at<=end){var size=java.lang.Long.lowestOneBit(at).let{if(it==0L)1L shl 32 else it}
                while(size>end-at+1)size=size shr 1
                val host=(3 downTo 0).joinToString("."){((at shr (it*8))and 255).toString()}
                out+=host to (32-java.lang.Long.numberOfTrailingZeros(size));at+=size
            }
        }
        var at=0L
        for((start,end)in excluded){if(at<start)addRange(at,start-1);at=end+1}
        return out
    }
    fun newer(candidate:String,current:String):Boolean {
        fun parts(s:String)=s.removePrefix("v").substringBefore('-').split('.').map{it.toIntOrNull()?:0}
        val a=parts(candidate);val b=parts(current)
        for(i in 0 until maxOf(a.size,b.size)){val c=(a.getOrElse(i){0}).compareTo(b.getOrElse(i){0});if(c!=0)return c>0}
        val aPre=candidate.removePrefix("v").substringAfter('-',"")
        val bPre=current.removePrefix("v").substringAfter('-',"")
        if(aPre.isEmpty()||bPre.isEmpty())return bPre.isNotEmpty()&&aPre.isEmpty()
        val tokens=Regex("[0-9]+|[A-Za-z]+")
        val ap=tokens.findAll(aPre).map{it.value}.toList();val bp=tokens.findAll(bPre).map{it.value}.toList()
        for(i in 0 until maxOf(ap.size,bp.size)){
            val av=ap.getOrElse(i){""};val bv=bp.getOrElse(i){""}
            val c=if(av.toIntOrNull()!=null&&bv.toIntOrNull()!=null)av.toInt().compareTo(bv.toInt())else av.compareTo(bv)
            if(c!=0)return c>0
        }
        return false
    }
    fun yaml(value:String):String {require(value.none{it.isISOControl()});return "'"+value.replace("'","''")+"'"}
}
