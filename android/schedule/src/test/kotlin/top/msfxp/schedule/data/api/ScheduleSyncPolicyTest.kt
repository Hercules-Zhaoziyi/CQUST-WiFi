package top.msfxp.schedule.data.api

import org.junit.Assert.*
import org.junit.Test

class ScheduleSyncPolicyTest {
    @Test fun practicalOnlyScheduleIsNotMistakenForEmpty(){
        assertFalse(ScheduleSyncPolicy.rejectEmpty(false,0,1))
        assertFalse(ScheduleSyncPolicy.rejectEmpty(false,1,0))
        assertTrue(ScheduleSyncPolicy.rejectEmpty(false,0,0))
        assertFalse(ScheduleSyncPolicy.rejectEmpty(true,0,0))
    }
    @Test fun failedPracticalFetchKeepsSameSemesterCache(){
        assertTrue(ScheduleSyncPolicy.keepPractical(true,true,true))
        assertFalse(ScheduleSyncPolicy.keepPractical(false,true,true))
        assertFalse(ScheduleSyncPolicy.keepPractical(true,false,true))
        assertFalse(ScheduleSyncPolicy.keepPractical(true,true,false))
    }
}
