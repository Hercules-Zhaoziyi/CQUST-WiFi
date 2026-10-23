package cn.ziyis.cqustwifi

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import org.json.JSONArray

class ProtocolTest {
    @Test fun timetableOnlyNeverRequestsCampusLogin(){
        assertTrue(Protocol.usesCampusLogin(0));assertTrue(Protocol.usesCampusLogin(2))
        assertFalse(Protocol.usesCampusLogin(1));assertFalse(Protocol.usesCampusLogin(-1));assertFalse(Protocol.usesCampusLogin(99))
    }
    @Test fun trustedSchoolOnly(){
        assertTrue(Protocol.trusted("http://aaa.cqust.edu.cn/eportal/"))
        assertTrue(Protocol.trusted("http://172.28.228.2:8080/eportal/"))
        for(u in listOf("https://evil.test/","http://aaa.cqust.edu.cn@evil.test/","http://aaa.cqust.edu.cn:9999/","file:///data/local"))
            assertFalse(u,Protocol.trusted(u))
    }
    @Test fun discoveryKeepsCompleteQuery(){
        assertTrue(Protocol.candidate("http://aaa.cqust.edu.cn/eportal/index.jsp?wlanuserip=demo&mac=aa"))
        assertFalse(Protocol.candidate("http://aaa.cqust.edu.cn/"))
        assertFalse(Protocol.candidate("http://evil.test/eportal/index.jsp?wlanuserip=demo"))
        assertEquals("http://aaa.cqust.edu.cn/eportal/index.jsp?wlanuserip=demo&mac=aa",
            Protocol.redirect("""<script>location.href="http://aaa.cqust.edu.cn/eportal/index.jsp?wlanuserip=demo&amp;mac=aa";</script>"""))
    }
    @Test fun twoLayerEncodingDoesNotAlterRawQuery(){
        val query="wlanuserip=x&MAC=aa%3Abb&url=a%2520b"
        val body=Protocol.loginBody(" demo "," a+b% ","互联网",query,false)
        assertTrue(body.startsWith("userId=demo&"))
        assertTrue(body.contains("password="+Protocol.twice(" a+b% ")))
        assertTrue(body.contains("queryString="+Protocol.twice(query)))
        assertFalse(body.contains("userIndex="))
        assertTrue(body.endsWith("passwordEncrypt=false"))
    }
    @Test fun recommendedPasswordsAreUsable(){
        val a=Protocol.recommendedPassword();assertEquals(12,a.length)
        assertTrue(a.all{it.code in 33..126});assertFalse(a.any{it in "01IOlo"})
        assertNotEquals(a,Protocol.recommendedPassword())
    }
    @Test fun publicDestinationsOnly(){
        for(ip in listOf("127.0.0.1","10.0.0.1","172.21.39.11","192.168.1.1","169.254.169.254","100.64.0.1","224.0.0.1","::1","fc00::1","2001:db8::1"))
            assertFalse(ip,Protocol.publicTarget(InetAddress.getByName(ip)))
        assertTrue(Protocol.publicTarget(InetAddress.getByName("1.1.1.1")))
        assertTrue(Protocol.privateV4("172.21.39.11"));assertFalse(Protocol.privateV4("172.99.0.1"))
        assertFalse(Protocol.privateV4("10.999.0.1"))
    }
    @Test fun versionsAndYaml(){
        assertTrue(Protocol.newer("1.1.0","1.0.0-beta01"))
        assertTrue(Protocol.newer("1.0.0","1.0.0-beta01"))
        assertFalse(Protocol.newer("1.0.0-beta01","1.0.0-beta01"))
        assertEquals("'a''b'",Protocol.yaml("a'b"))
        assertThrows(IllegalArgumentException::class.java){Protocol.yaml("x\ny")}
    }
}
