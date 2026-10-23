package top.msfxp.schedule.data.model

data class StudentProfile(val studentId:String="",val name:String="",val college:String="",val major:String="",val className:String="")
data class Semester(val id:String,val schoolYear:String,val name:String,val label:String,val startDate:String="",val totalWeeks:Int=20,val isCurrent:Boolean=false)
