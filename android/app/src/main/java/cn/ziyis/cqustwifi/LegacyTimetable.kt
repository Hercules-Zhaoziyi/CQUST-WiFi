package cn.ziyis.cqustwifi

import org.json.JSONObject
import top.msfxp.schedule.data.model.Course
import top.msfxp.schedule.data.model.CourseEvent
import top.msfxp.schedule.data.model.COURSE_TYPE_PRACTICAL
import top.msfxp.schedule.data.model.COURSE_TYPE_THEORY
import java.util.UUID

object LegacyTimetable {
    fun decode(document:JSONObject,table:String):Pair<List<Course>,List<CourseEvent>>{
        val array=document.getJSONArray("courses");require(array.length() in 1..3000)
        val courses=linkedMapOf<String,Course>();val events=mutableListOf<CourseEvent>()
        for(i in 0 until array.length()){
            val old=array.getJSONObject(i)
            val name=old.getString("name");require(name.isNotBlank()&&name.length<=300)
            val day=old.getInt("day");val start=old.getInt("start");val end=old.getInt("end")
            val unarranged=day==0||start==0
            require(day in 0..7&&if(unarranged)start==0&&end==0 else start in 1..11&&end in start..11)
            val kind=if(old.optString("kind")=="practical")COURSE_TYPE_PRACTICAL else COURSE_TYPE_THEORY
            val id=UUID.nameUUIDFromBytes("$name|$kind".toByteArray()).toString()
            courses.putIfAbsent(id,Course(id=id,tableId=table,name=name,courseType=kind,colorIndex=(name.hashCode()and Int.MAX_VALUE)%12))
            val weeks=old.getJSONArray("weeks");require(weeks.length() in 0..30)
            if(weeks.length()==0){
                events+=CourseEvent(courseId=id,tableId=table,week=0,day=if(unarranged)day else 0,
                    startSection=0,endSection=0,location=old.optString("place"),teacher=old.optString("teacher"))
            }
            for(j in 0 until weeks.length()){
                val week=weeks.getInt(j);require(week in 1..30)
                events+=CourseEvent(courseId=id,tableId=table,week=week,day=day,startSection=start,endSection=end,
                    location=old.optString("place"),teacher=old.optString("teacher"))
            }
        }
        return courses.values.toList() to events.distinct()
    }
}
