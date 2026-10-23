package cn.ziyis.cqustwifi
import org.junit.Assert.*
import org.junit.Test
class ProxyQrTest {
    @Test fun importsAuthenticatedLanProxy(){val p=ProxyQr.parse("socks5://student:pass%2Bword@172.21.39.11:7890");assertEquals("pass+word",p.password);assertFalse(p.campusPassword)}
    @Test fun rejectsPublicServersAndMissingCredentials(){
        assertThrows(IllegalArgumentException::class.java){ProxyQr.parse("socks5://u:p@8.8.8.8:7890")}
        assertThrows(IllegalArgumentException::class.java){ProxyQr.parse("https://example.com")}
    }
}
