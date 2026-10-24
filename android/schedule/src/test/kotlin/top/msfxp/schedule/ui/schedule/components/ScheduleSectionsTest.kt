package top.msfxp.schedule.ui.schedule.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleSectionsTest {
    @Test fun defaultTimelineStillHasElevenSections() {
        assertEquals(11, scheduleSectionCount(emptyList(), emptyList()))
    }
    @Test fun lateClassExtendsMeasuredGridPastEleven() {
        assertEquals(12, scheduleSectionCount((1..11).toList(), listOf(2, 8, 12)))
    }
    @Test fun customBellTimesExtendTimelineWithoutClasses() {
        assertEquals(14, scheduleSectionCount(listOf(1, 14), listOf(2)))
    }
    @Test fun malformedDataCannotCreateUnboundedLayout() {
        assertEquals(24, scheduleSectionCount(listOf(Int.MAX_VALUE), listOf(-2)))
        assertEquals(11, scheduleSectionCount(listOf(-1), listOf(-2)))
    }
}
