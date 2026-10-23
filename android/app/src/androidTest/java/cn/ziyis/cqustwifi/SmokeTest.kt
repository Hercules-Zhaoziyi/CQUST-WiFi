package cn.ziyis.cqustwifi

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.graphics.asAndroidBitmap
import hev.htproxy.TProxyService
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun pagesAreReachableWithoutCredentials(){
        // CI intentionally disables emulator radios: do not depend on public probe responses.
        rule.waitUntil(10000){Repository.state.value.status==LinkState.OFFLINE}
        rule.onNodeWithTag("power").assertExists()
        screenshot("01-connection")
        rule.onNodeWithTag("username").performScrollTo()
        screenshot("01b-login")
        rule.onNodeWithText("常用网站").performClick()
        rule.onNodeWithText("校园服务，触手可及。").assertExists()
        screenshot("02-sites")
        rule.onNodeWithText("代理",useUnmergedTree=true).performClick()
        rule.onNodeWithText("连接电脑").assertExists()
        screenshot("03-desktop-vpn")
        rule.onNodeWithText("本机共享").performClick()
        rule.onNodeWithText("启用代理").assertExists()
        screenshot("04-sharing")
        rule.onNodeWithText("关于",useUnmergedTree=true).performClick()
        rule.onNodeWithText("加入内测群").assertExists()
        screenshot("05-about")
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("软件更新").performScrollTo().assertExists()
        screenshot("06-settings-update")
    }
    @Test fun nativeCoreLoadsWithoutStartingVpn(){assertFalse(TProxyService.TProxyIsRunning())}
    private fun screenshot(name:String){
        rule.waitForIdle()
        val image=rule.onRoot().captureToImage().asAndroidBitmap()
        val dir=File(rule.activity.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
        File(dir,"$name.png").outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}
