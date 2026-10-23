package cn.ziyis.cqustwifi

import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetTest {
    @Test fun everyCampusIconCanBeDecoded()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val loader=ImageLoader.Builder(context).components{add(SvgDecoder.Factory())}.build()
        try{
            for(site in Repository.sites()){
                val result=loader.execute(ImageRequest.Builder(context).data("file:///android_asset/${site.icon}").size(96).build())
                assertTrue("Cannot decode ${site.icon}: $result",result is SuccessResult)
            }
        }finally{loader.shutdown()}
    }
}
