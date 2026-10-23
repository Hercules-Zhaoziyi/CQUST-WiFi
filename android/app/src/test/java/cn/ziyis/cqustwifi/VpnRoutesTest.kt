package cn.ziyis.cqustwifi

import org.junit.Assert.*
import org.junit.Test

class VpnRoutesTest {
    private fun number(ip:String)=ip.split('.').fold(0L){a,b->(a shl 8)+b.toInt()}
    private fun routed(ip:String):Boolean {
        val n=number(ip)
        return Protocol.vpnRoutes().any{(base,prefix)->val start=number(base);n>=start&&n<start+(1L shl (32-prefix))}
    }
    @Test fun campusAndLoopbackStayDirect(){
        for(ip in listOf("10.0.0.1","172.16.0.1","172.21.39.11","172.31.255.255","192.168.1.1","127.0.0.1","169.254.1.1","100.64.0.1","224.0.0.1"))assertFalse(ip,routed(ip))
        for(ip in listOf("1.1.1.1","8.8.8.8","9.9.9.9","172.15.255.255","172.32.0.1","192.167.255.255","223.255.255.254"))assertTrue(ip,routed(ip))
    }
    @Test fun cidrsAreAlignedAndNonoverlapping(){
        val ranges=Protocol.vpnRoutes().map{(base,prefix)->
            val size=1L shl(32-prefix);val start=number(base)
            assertEquals(0L,start%size);start to start+size-1
        }
        for(i in 1 until ranges.size)assertTrue(ranges[i-1].second<ranges[i].first)
    }
    @Test fun betaUpdateComparison(){
        assertTrue(Protocol.newer("1.0.0-beta02","1.0.0-beta01"))
        assertFalse(Protocol.newer("1.0.0-beta01","1.0.0-beta02"))
        assertTrue(Protocol.newer("1.0.0","1.0.0-beta02"))
    }
}
