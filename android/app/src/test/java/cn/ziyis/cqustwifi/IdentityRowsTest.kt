package cn.ziyis.cqustwifi

import org.junit.Assert.*
import org.junit.Test

class IdentityRowsTest{
    @Test fun identicalAccountsAreMerged(){assertEquals(listOf(IdentityRow("student",IdentityKind.SHARED)),identityRows("student","student"))}
    @Test fun differentAccountsKeepExplicitRoles(){assertEquals(listOf(IdentityRow("wifi-user",IdentityKind.CAMPUS),IdentityRow("course-user",IdentityKind.COURSE)),identityRows("wifi-user","course-user"))}
    @Test fun emptyAccountsDoNotCreateRows(){assertTrue(identityRows("","").isEmpty());assertEquals(IdentityKind.COURSE,identityRows("","course").single().kind)}
}
