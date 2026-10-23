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
            rule.waitUntil(30000){Repository.engine.wifi()?.let{net->
                Protocol.privateV4(Repository.engine.ip(net))&&Repository.engine.connectivity.getLinkProperties(net)?.routes?.any{it.isDefaultRoute}==true
            }==true}
            // Permission grant exists only in this isolated emulator test, never in app code.
            shell("appops set ${context.packageName} ACTIVATE_VPN allow")
            assertNull(android.net.VpnService.prepare(context))
            Repository.saveVpn("10.0.2.2",17890,"fixture-user","fixture-password",false)
            Repository.vpnState(true,"测试准备启动")
            ContextCompat.startForegroundService(context,Intent(context,CampusVpnService::class.java))
            rule.waitUntil(15000){Repository.state.value.vpnMessage.startsWith("VPN 已启动")||!Repository.state.value.vpn}
            assertTrue("VPN startup: ${Repository.state.value.vpnMessage}; ${Repository.state.value.logs.takeLast(3)}",Repository.state.value.vpnMessage.startsWith("VPN 已启动"))
            shell("am start -n cn.ziyis.cqustprobe/.MainActivity")
            var response=""
            rule.waitUntil(15000){response=shell("run-as cn.ziyis.cqustprobe cat files/result.txt");response.contains("CQUST_TUN_OK")||response.contains("PROBE_ERROR")}
            assertTrue("No controlled DNS/HTTP response through TUN: $response",response.contains("CQUST_TUN_OK"))
            context.startService(Intent(context,CampusVpnService::class.java).setAction("STOP"))
            rule.waitUntil(5000){Repository.engine.connectivity.allNetworks.none{Repository.engine.connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true}}
            assertFalse(Repository.state.value.vpn)
        }finally{
            context.stopService(Intent(context,CampusVpnService::class.java))
            shell("svc wifi disable");Repository.refresh()
        }
    }
}
