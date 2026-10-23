package cn.ziyis.cqustwifi
import org.junit.Assert.*
import org.junit.Test
class ProxyQrTest {
    @Test fun universalQrRoundtripWithUnicode(){
        val profile=VpnProfile(host="172.21.39.11",port=7890,username="测试用户",password="a+测试😀123",campusPassword=false)
        val raw=ProxyQr.encode(profile)
        assertEquals(profile,ProxyQr.parse(raw))
        val matrix=com.google.zxing.qrcode.QRCodeWriter().encode(raw,com.google.zxing.BarcodeFormat.QR_CODE,720,720,mapOf(com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"))
        val bitmap=com.google.zxing.BinaryBitmap(object:com.google.zxing.Binarizer(object:com.google.zxing.LuminanceSource(720,720){
            override fun getRow(y:Int,row:ByteArray?):ByteArray=(row?.takeIf{it.size>=720}?:ByteArray(720)).also{r->for(x in 0 until 720)r[x]=if(matrix[x,y])0 else 255.toByte()}
            override fun getMatrix():ByteArray=ByteArray(720*720){i->if(matrix[i%720,i/720])0 else 255.toByte()}
        }){
            override fun getBlackRow(y:Int,row:com.google.zxing.common.BitArray?):com.google.zxing.common.BitArray=(row?:com.google.zxing.common.BitArray(720)).also{r->r.clear();for(x in 0 until 720)if(matrix[x,y])r.set(x)}
            override fun getBlackMatrix()=matrix
            override fun createBinarizer(source:com.google.zxing.LuminanceSource):com.google.zxing.Binarizer=throw UnsupportedOperationException()
        })
        assertEquals(profile,ProxyQr.parse(com.google.zxing.qrcode.QRCodeReader().decode(bitmap).text))
    }
    @Test fun importsAuthenticatedLanProxy(){val p=ProxyQr.parse("socks5://student:pass%2Bword@172.21.39.11:7890");assertEquals("pass+word",p.password);assertFalse(p.campusPassword)}
    @Test fun rejectsPublicServersAndMissingCredentials(){
        assertThrows(IllegalArgumentException::class.java){ProxyQr.parse("socks5://u:p@8.8.8.8:7890")}
        assertThrows(IllegalArgumentException::class.java){ProxyQr.parse("https://example.com")}
        for(host in listOf("010.1.2.3","10.bad.1.2.3","+10.1.2.3"))assertFalse(Protocol.privateV4(host))
    }
}
