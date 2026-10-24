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
        rule.onNodeWithText("课程表").performClick()
        rule.onNodeWithContentDescription("设置").assertExists()
        rule.onNodeWithText("CQUST Wi-Fi").assertDoesNotExist()
        screenshot("05-schedule")
        rule.onNodeWithText("连接",useUnmergedTree=true).performClick()
        rule.onNodeWithContentDescription("关于").performClick()
        rule.onNodeWithText("CQUST Wi-Fi × 课程表").assertExists()
        screenshot("05-about")
        rule.onNodeWithText("CQUST Wi-Fi 内测群").performScrollTo().assertExists()
        rule.onNodeWithText("课程表 QQ 交流群").assertExists()
        screenshot("05b-about-links")
        rule.runOnUiThread{rule.activity.onBackPressedDispatcher.onBackPressed()};rule.waitForIdle();Thread.sleep(400)
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithTag("main-navigation").assertDoesNotExist()
        rule.onNodeWithTag("unified-settings-list").performScrollToNode(hasText("软件更新"))
        rule.onNodeWithText("软件更新").assertExists()
        screenshot("06-settings-update")
    }
    @Test fun nativeCoreLoadsWithoutStartingVpn(){assertFalse(TProxyService.TProxyIsRunning())}
    private fun screenshot(name:String){
        rule.waitForIdle()
        // Coil's local decode and the platform render thread are not Compose idling resources.
        Thread.sleep(800)
        rule.waitForIdle()
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val window=automation.rootInActiveWindow
        // Cold hosted emulators can show an unrelated Pixel Launcher ANR after boot.
        // Dismiss ONLY that known system launcher dialog, never an app-under-test ANR.
        if(window?.findAccessibilityNodeInfosByText("Pixel Launcher isn't responding")?.isNotEmpty()==true){
            val button=window.findAccessibilityNodeInfosByText("Close app").firstOrNull()
            check(button?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)==true)
            Thread.sleep(400)
        }
        val active=automation.rootInActiveWindow
        check(active?.findAccessibilityNodeInfosByText("CQUST Wi-Fi isn't responding").isNullOrEmpty())
        val image=automation.takeScreenshot()
        val dir=File(rule.activity.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
        File(dir,"$name.png").outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}
