package top.msfxp.schedule.integration

import org.junit.Assert.*
import org.junit.Test
import top.msfxp.schedule.data.api.PortalBrowserHandoff
import top.msfxp.schedule.data.api.CqustVpnCrypto

class PortalBrowserHandoffTest {
    @Test fun callbackCanBeTransferredBeforeNativeConsumption(){
        val url=CqustVpnCrypto.buildVpnUrl("https","web.cqust.edu.cn","/login?cas_login=true&ticket=ST-fixture")
        assertTrue(PortalBrowserHandoff.accepts(url))
    }
    @Test fun wrappedGatewayTokenIsAccepted(){
        assertTrue(PortalBrowserHandoff.accepts(CqustVpnCrypto.buildVpnUrl("https","web.cqust.edu.cn","/wengine-vpn-token-login?token=fixture")))
    }
    @Test fun foreignCallbackOrRedirectInjectionIsRejected(){
        for(url in listOf("https://web.cqust.edu.cn.evil.test/login?cas_login=true&ticket=ST-x","https://web.cqust.edu.cn/token-login?token=x&redirect=https://other.test/","https://web.cqust.edu.cn/login?cas_login=true&ticket=invalid"))assertFalse(PortalBrowserHandoff.accepts(url))
    }
}
