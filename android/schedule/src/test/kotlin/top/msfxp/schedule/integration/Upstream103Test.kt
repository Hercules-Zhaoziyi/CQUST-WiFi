package top.msfxp.schedule.integration

import org.junit.Assert.*
import org.junit.Test
import top.msfxp.schedule.data.api.CqustExamParser
import top.msfxp.schedule.data.api.CqustPracticalParser
import top.msfxp.schedule.data.model.*
import top.msfxp.schedule.util.CourseLocationFormatter
import top.msfxp.schedule.util.ExamTimeMapper

class Upstream103Test {
    @Test fun huxiBuildingsUseChineseNames() {
        assertEquals("虎溪校区 笃行楼 K302",CourseLocationFormatter.format("虎溪校区K302"))
        assertEquals("笃行楼 K302",CourseLocationFormatter.formatDisplay("虎溪校区K302"))
    }
    @Test fun otherCampusIsNotGuessed() {
        assertEquals("金凤校区K302",CourseLocationFormatter.format("金凤校区K302"))
    }
    @Test fun sameCourseDifferentClassesShareUnifiedCode() {
        assertEquals(courseUnifiedCode("ABC.01"),courseUnifiedCode("ABC.02"))
    }
    @Test fun noonSlotsKeepChronologicalOrder() {
        assertNotNull(findSectionTimeSlot(-1));assertNotNull(findSectionTimeSlot(-2))
        assertNull(findSectionTimeSlot(0))
        assertTrue(sectionTimeKey(4)<sectionTimeKey(-1))
        assertTrue(sectionTimeKey(-2)<sectionTimeKey(5))
    }
    @Test fun noonPracticePrefixesAreNotDropped() {
        val parser=CqustPracticalParser::class.java.getDeclaredMethod("parseTimeText",String::class.java).apply{isAccessible=true}
        val result=parser.invoke(CqustPracticalParser,"第3-4周星期一中午第01-02节")
        assertNotNull(result)
        val field=result.javaClass.getDeclaredField("startSection").apply{isAccessible=true}
        assertEquals(-1,field.getInt(result))
    }
    @Test fun examParserKeepsServerWeekAndTime() {
        val html="""<table><tr><td>1</td><td>ABC.02</td><td>测试课程</td><td>2026-11-10</td><td>第9周星期二09:25-11:15</td><td>虎溪校区K302</td><td>12</td><td>已安排</td><td></td></tr></table>"""
        val exam=CqustExamParser.parseExamHtml(html).single()
        assertEquals(9,exam.week);assertEquals("09:25",exam.startTime)
        assertEquals("2026-11-10",exam.examDate)
        assertEquals(EXAM_TYPE_DEFAULT,exam.examType)
    }
    @Test fun examTimeRejectsInvalidRanges() {
        assertNull(ExamTimeMapper.resolveSections("11:15","09:25"))
        assertEquals(2 to 3,ExamTimeMapper.resolveSections("09:25","11:15"))
    }
}
