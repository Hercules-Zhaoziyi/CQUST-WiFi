package cn.ziyis.cqustwifi

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RsaTest {
    @Test fun matchesDesktopAndPortalVectors(){
        val fixture=JSONObject(javaClass.getResource("/rsa_vectors.json")!!.readText())
        val cases=fixture.getJSONArray("cases")
        for(i in 0 until cases.length()){
            val c=cases.getJSONObject(i)
            assertEquals("RSA case $i",c.getString("ciphertext"),Protocol.encrypted(
                c.getString("password"),c.getString("mac"),fixture.getString("exponent"),fixture.getString("modulus")))
        }
    }
}
