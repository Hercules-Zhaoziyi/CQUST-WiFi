package cn.ziyis.cqustwifi

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LegacyTimetableTest {
    @Test fun retainsOldCourseWeeksPlacesAndTeachers(){
        val old=JSONObject("""{"courses":[{"name":"测试课程","day":2,"start":3,"end":4,"weeks":[1,2],"place":"测试教学楼","teacher":"测试教师","kind":"practical"}]}""")
        val(courses,events)=LegacyTimetable.decode(old,"default")
        assertEquals(1,courses.size);assertTrue(courses.single().isPractical)
        assertEquals(listOf(1,2),events.map{it.week});assertEquals("测试教学楼",events.first().location)
        assertEquals("测试教师",events.first().teacher);assertEquals(4,events.first().endSection)
    }
    @Test fun rejectsCorruptRangesWithoutProducingReplacementData(){
        val bad=JSONObject("""{"courses":[{"name":"测试课程","day":9,"start":0,"end":30,"weeks":[99]}]}""")
        assertThrows(IllegalArgumentException::class.java){LegacyTimetable.decode(bad,"default")}
    }
    @Test fun preservesUnarrangedCourses(){
        val old=JSONObject("""{"courses":[{"name":"待排课程","day":0,"start":0,"end":0,"weeks":[],"teacher":"测试教师"}]}""")
        val(courses,events)=LegacyTimetable.decode(old,"default")
        assertEquals("待排课程",courses.single().name);assertEquals(0,events.single().week);assertEquals(0,events.single().startSection)
    }
}
