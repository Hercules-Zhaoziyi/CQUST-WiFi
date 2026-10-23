package cn.ziyis.cqustwifi

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun pagesAreReachableWithoutCredentials(){
        rule.onNodeWithTag("power").assertExists()
        rule.onNodeWithText("常用网站").performClick()
        rule.onNodeWithText("校园服务，触手可及。").assertExists()
        rule.onNodeWithText("代理",useUnmergedTree=true).performClick()
        rule.onNodeWithText("连接电脑").assertExists()
        rule.onNodeWithText("本机共享").performClick()
        rule.onNodeWithText("启用代理").assertExists()
        rule.onNodeWithText("关于",useUnmergedTree=true).performClick()
        rule.onNodeWithText("加入内测群").assertExists()
    }
}
