package cn.ziyis.cqustwifi

import android.content.Intent
import android.net.NetworkCapabilities
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VpnFlowTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun shell(command:String):String {
        val fd=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use{it.readText()}
    }
    @Test fun realTunTcpDnsAndStop(){
        val context=rule.activity
        try{
            shell("svc wifi enable")
            rule.waitUntil(20000){Repository.engine.wifi()!=null}
            // Permission grant exists only in this isolated emulator test, never in app code.
            shell("appops set ${context.packageName} ACTIVATE_VPN allow")
            assertNull(android.net.VpnService.prepare(context))
            Repository.saveVpn("10.0.2.2",17890,"fixture-user","fixture-password",false)
            ContextCompat.startForegroundService(context,Intent(context,CampusVpnService::class.java))
            rule.waitUntil(15000){Repository.state.value.vpnMessage.startsWith("VPN 已启动")}
            val response=shell("printf 'GET / HTTP/1.0\\r\\nHost: fixture\\r\\n\\r\\n' | toybox nc -w 5 1.1.1.1 80")
            assertTrue("No controlled HTTP response through TUN",response.contains("CQUST_TUN_OK"))
            val dns=shell("ping -c 1 -W 1 cqust-fixture.invalid")
            assertTrue("Mapped DNS failed",dns.contains("198.19."))
            context.startService(Intent(context,CampusVpnService::class.java).setAction("STOP"))
            rule.waitUntil(5000){Repository.engine.connectivity.allNetworks.none{Repository.engine.connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true}}
            assertFalse(Repository.state.value.vpn)
        }finally{
            context.stopService(Intent(context,CampusVpnService::class.java))
            shell("svc wifi disable");Repository.refresh()
        }
    }
}
