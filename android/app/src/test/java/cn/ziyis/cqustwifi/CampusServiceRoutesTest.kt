package cn.ziyis.cqustwifi

import org.junit.Assert.*
import org.junit.Test
import top.msfxp.schedule.data.api.CqustVpnCrypto

class CampusServiceRoutesTest {
    private fun site(path:String?)=CampusSite("research","科研系统","https://example.invalid",true,path)
    private fun portal(path:String)=CqustVpnCrypto.buildVpnUrl("https","casp.cqust.edu.cn",path)

    @Test fun serviceCardKeepsOfficialMenuRoute(){
        val path="/index.html#/ServiceShow?isMobile=2&wid=888009823426125824"
        assertEquals(portal(path),CampusServiceRoutes.destination(site(path)))
        assertEquals(portal("/index.html#/"),CampusServiceRoutes.destination(site(null)))
    }

    @Test fun malformedMenuRouteFallsBackToPortal(){
        for(path in listOf("https://other.test/","/index.html#/ServiceShow?isMobile=2&wid=1&redirect=https://other.test","/index.html#/ServiceShow?isMobile=9&wid=1","/index.html#/ServiceShow?isMobile=2&wid=x")){
            assertEquals(portal("/index.html#/"),CampusServiceRoutes.destination(site(path)))
        }
        assertTrue(CampusServiceRoutes.destination(site(null).copy(name="教务系统")).endsWith("/eams/unifiedLogin.action"))
        assertTrue(CampusServiceRoutes.destination(site(null).copy(name="实践教学")).endsWith("/cqust_sso/"))
    }
}
