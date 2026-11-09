package top.msfxp.schedule.data.model

private fun Course.identityKey():String? = unifiedCode.takeIf{it.isNotBlank()}?.let{"$courseType|$it"}

// 将旧教学班编号下的调课记录迁移到统一课程编号。
fun courseIdentityMappings(previous:List<Course>,incoming:List<Course>):Map<String,String>{
    val destinations=incoming.mapNotNull{course->course.identityKey()?.let{it to course.id}}.toMap()
    return previous.mapNotNull{old->destinations[old.identityKey()]?.takeIf{it!=old.id}?.let{old.id to it}}.toMap()
}

// 同步保留已有课程配色；多教学班合并时按旧编号稳定选取。
fun preserveCourseColors(previous:List<Course>,incoming:List<Course>):List<Course>{
    val exact=previous.associateBy{it.id}
    val unified=previous.sortedBy{it.id}.mapNotNull{course->course.identityKey()?.let{it to course}}.toMap()
    return incoming.map{course->
        val old=exact[course.id]?:unified[course.identityKey()]
        if(old==null)course else course.copy(colorIndex=old.colorIndex)
    }
}
