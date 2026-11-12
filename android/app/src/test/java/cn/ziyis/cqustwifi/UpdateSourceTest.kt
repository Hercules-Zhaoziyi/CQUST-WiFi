package cn.ziyis.cqustwifi

import org.junit.Assert.*
import org.junit.Test

class UpdateSourceTest {
    @Test fun giteeIsPrimaryAndGithubIsBackup(){assertTrue(UpdateSource.feeds.first().startsWith(UpdateSource.GITEE));assertTrue(UpdateSource.feeds.last().startsWith("https://api.github.com/"))}
    @Test fun onlyOwnedRepositoryAssetsAreAccepted(){
        assertTrue(UpdateSource.asset("${UpdateSource.GITEE}/releases/download/test/app.apk"))
        for(url in listOf("https://gitee.com.evil.test/ithercules/CQUST-WiFi/releases/download/a/app.apk","http://gitee.com/ithercules/CQUST-WiFi/releases/download/a/app.apk","https://gitee.com/other/repo/releases/download/a/app.apk"))assertFalse(UpdateSource.asset(url))
    }
}
