package cn.ziyis.cqustwifi

enum class IdentityKind(val loginMode:Int){CAMPUS(0),COURSE(1),SHARED(2)}
data class IdentityRow(val username:String,val kind:IdentityKind)

fun identityRows(campus:String,course:String):List<IdentityRow>{
    val wifi=campus.trim();val schedule=course.trim()
    if(wifi.isNotEmpty()&&wifi==schedule)return listOf(IdentityRow(wifi,IdentityKind.SHARED))
    return buildList{
        if(wifi.isNotEmpty())add(IdentityRow(wifi,IdentityKind.CAMPUS))
        if(schedule.isNotEmpty())add(IdentityRow(schedule,IdentityKind.COURSE))
    }
}
