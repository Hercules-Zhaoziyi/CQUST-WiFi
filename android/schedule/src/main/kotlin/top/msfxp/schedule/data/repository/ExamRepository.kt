package top.msfxp.schedule.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import top.msfxp.schedule.data.db.ExamDao
import top.msfxp.schedule.data.model.ExamRecord

// 考试记录数据仓库
class ExamRepository(
    private val examDao: ExamDao,
    private val scheduleRepository: ScheduleRepository
) {

    // 观察当前学期考试记录
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSemesterExamsFlow: Flow<List<ExamRecord>> = combine(
        scheduleRepository.defaultTableFlow,
        scheduleRepository.currentSemesterFlow
    ) { table, semester ->
        table?.id to semester?.id
    }.distinctUntilChanged().flatMapLatest { (tableId, semesterId) ->
        if (tableId != null && semesterId != null) {
            examDao.observeBySemester(tableId, semesterId)
        } else {
            flowOf(emptyList())
        }
    }

    // 提取指定学期考试记录
    suspend fun getExamsOnce(tableId: String, semesterId: String): List<ExamRecord> {
        return examDao.getBySemester(tableId, semesterId)
    }

    // 按学期整批替换考试记录
    suspend fun replaceSemesterExams(tableId: String, semesterId: String, records: List<ExamRecord>) {
        examDao.replaceSemesterExams(tableId, semesterId, records)
    }
}
