package cn.ziyis.cqustwifi

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import top.msfxp.schedule.data.repository.ScheduleRepository
import top.msfxp.schedule.data.repository.SettingsRepository
import top.msfxp.schedule.data.model.Course
import top.msfxp.schedule.data.model.CourseEvent
import top.msfxp.schedule.data.model.Semester
import top.yukonga.miuix.kmp.theme.*
import java.io.File
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

@RunWith(AndroidJUnit4::class)
class MiuixIntegrationTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun scanEntryStaysAboveBothProxyModes(){
        rule.onNodeWithText("代理",useUnmergedTree=true).performClick()
        val before=rule.onNodeWithTag("proxy-scan-entry").fetchSemanticsNode().boundsInRoot.top
        val tabs=rule.onNodeWithText("本机共享").fetchSemanticsNode().boundsInRoot.top
        assertTrue(before<tabs)
        rule.onNodeWithText("本机共享").performTouchInput{click()}
        rule.onNodeWithTag("proxy-scan-entry").assertExists()
        screenshot("08-miuix-sharing-scan")
        rule.onNodeWithTag("proxy-scan-entry").performTouchInput{click()}
        rule.onNodeWithText("扫码连接").assertExists()
        rule.onNodeWithContentDescription("返回").performTouchInput{click()}
        rule.onNodeWithTag("proxy-scan-entry").assertExists()
    }
    @Test fun completeTimetableAndSettingsAreReachable(){
        seedSchedule()
        rule.onNodeWithText("课程表").performClick()
        rule.waitUntil(10000){rule.onAllNodesWithText("测试数学").fetchSemanticsNodes().isNotEmpty()}
        screenshot("09-miuix-week-grid")
        rule.onNodeWithContentDescription("设置").performTouchInput{click()}
        screenshot("10-miuix-course-settings")
        for(title in listOf("个性化设置","课前通知提醒","同步日历日程","自动化功能","权限管理")){
            rule.onNodeWithText(title).performScrollTo().performTouchInput{click()}
            rule.onNodeWithContentDescription("返回").assertExists().performTouchInput{click()}
        }
        rule.onNodeWithText("课程总览 ›").performScrollTo().performTouchInput{click()}
        rule.onNodeWithText("课程总览").assertExists()
        val settings=runBlocking{GlobalContext.get().get<SettingsRepository>().getAppSettingsOnce()}
        assertFalse(settings.autoDndEnabled);assertFalse(settings.autoSyncToCalendar)
    }
    @Test fun scannerLayoutCanBeRenderedWithoutAccessingCamera(){
        rule.activity.setContentForScannerTest()
        rule.onNodeWithText("扫码连接").assertExists()
        rule.onNodeWithText("从相册导入").assertExists()
        rule.onNodeWithText("手动填写").assertExists()
        screenshot("11-miuix-scanner-layout")
    }
    private fun seedSchedule()=runBlocking(Dispatchers.IO){
        val repository=GlobalContext.get().get<ScheduleRepository>()
        val table=repository.getOrCreateDefaultTableId()
        repository.saveSchedule(table,listOf(Course(id="demo-math",tableId=table,name="测试数学",credit="3")),
            listOf(CourseEvent(courseId="demo-math",tableId=table,week=1,day=1,startSection=1,endSection=2,location="测试教学楼",teacher="测试教师")))
        val start=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        repository.saveSemesters(listOf(Semester(id="demo-semester",schoolYear="2026-2027",name="1",label="测试学期",startDate=start,isCurrent=true)))
        repository.setActiveSemester("demo-semester")
        GlobalContext.get().get<SettingsRepository>().saveFullProfileAndCredentials("demo","测试同学","测试学院","测试专业","测试班级","")
    }
    private fun screenshot(name:String){
        rule.waitForIdle();Thread.sleep(700)
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory=File(rule.activity.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
        File(directory,"$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}

private fun MainActivity.setContentForScannerTest(){
    androidx.activity.compose.setContent {
        MiuixTheme(controller=androidx.compose.runtime.remember{ThemeController(ColorSchemeMode.Dark)}){
            ScannerScreen(ScannerUiState(camera=true),null,{})
        }
    }
}
