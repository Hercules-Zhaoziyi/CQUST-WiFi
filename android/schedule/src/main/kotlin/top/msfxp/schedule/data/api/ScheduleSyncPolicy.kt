package top.msfxp.schedule.data.api

// 实践课独立于理论课；失败的分支不能把同学期已缓存的实践课清空。
internal object ScheduleSyncPolicy {
    fun rejectEmpty(allowEmpty:Boolean,theoryCount:Int,practicalCount:Int)=
        !allowEmpty&&theoryCount==0&&practicalCount==0
    fun keepPractical(sameSemester:Boolean,failed:Boolean,hasCachedPractical:Boolean)=
        sameSemester&&failed&&hasCachedPractical
}
