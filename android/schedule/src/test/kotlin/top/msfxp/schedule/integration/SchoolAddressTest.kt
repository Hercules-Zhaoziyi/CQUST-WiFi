package top.msfxp.schedule.integration

import org.junit.Assert.*
import org.junit.Test

class SchoolAddressTest {
    @Test fun schoolHttpAndCasRedirectsAreAccepted(){
        for(url in listOf("http://jwnew.cqust.edu.cn/eams/login.action?cqustadminweb=1",
            "https://cas.cqust.edu.cn/authserver/login","http://casp.cqust.edu.cn/authserver/login",
            "https://web.cqust.edu.cn/login","http://sjjx.cqust.edu.cn/"))assertTrue(url,ScheduleNetwork.trusted(url))
    }
    @Test fun unknownOriginsAndEmbeddedCredentialsAreRejected(){
        for(url in listOf("https://jwnew.cqust.edu.cn.evil.test/eams","https://other.cqust.edu.cn/",
            "http://jwnew.cqust.edu.cn:8080/eams","https://u:p@cas.cqust.edu.cn/",
            "file:///data/private","https://172.21.1.1/","javascript:alert(1)"))assertFalse(url,ScheduleNetwork.trusted(url))
    }
}
