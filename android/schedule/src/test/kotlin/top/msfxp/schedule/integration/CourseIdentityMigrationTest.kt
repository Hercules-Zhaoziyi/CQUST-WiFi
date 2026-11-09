package top.msfxp.schedule.integration

import org.junit.Assert.*
import org.junit.Test
import top.msfxp.schedule.data.model.*

class CourseIdentityMigrationTest {
    private val old=Course(id="old-class",tableId="t",courseCode="ABC.01",name="测试课程",colorIndex=7)
    private val fresh=old.copy(id="unified",courseCode="ABC",colorIndex=1)
    @Test fun oldAdjustmentReferenceFollowsUnifiedCourse(){assertEquals(mapOf("old-class" to "unified"),courseIdentityMappings(listOf(old),listOf(fresh)))}
    @Test fun customColorSurvivesIdentityUpgrade(){assertEquals(7,preserveCourseColors(listOf(old),listOf(fresh)).single().colorIndex)}
    @Test fun unrelatedTypeAndBlankCodeAreNotMapped(){
        assertTrue(courseIdentityMappings(listOf(old),listOf(fresh.copy(courseType=COURSE_TYPE_PRACTICAL))).isEmpty())
        assertTrue(courseIdentityMappings(listOf(old.copy(courseCode="")),listOf(fresh.copy(courseCode=""))).isEmpty())
    }
}
