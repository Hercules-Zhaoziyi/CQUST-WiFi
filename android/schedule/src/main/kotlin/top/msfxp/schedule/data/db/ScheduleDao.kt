package top.msfxp.schedule.data.db

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import top.msfxp.schedule.data.model.*

// 课表数据库数据访问接口
@Dao
interface ScheduleDao {

    // 课表元数据操作
    @Query("SELECT * FROM course_tables LIMIT 1")
    fun getDefaultCourseTable(): Flow<CourseTable?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourseTable(table: CourseTable)

    // 提取全学期教学事件
    @Transaction
    @Query("""
        SELECT 
            e.id, e.courseId, e.tableId, e.week, e.day, e.startSection, e.endSection,
            e.location, e.teacher, e.projectName, e.studentCount,
            c.name as courseName, c.courseCode as courseCode, c.credit as credit,
            c.category as category, c.courseType as courseType, c.colorIndex as colorIndex
        FROM course_events e
        INNER JOIN courses c ON e.courseId = c.id
        WHERE e.tableId = :tableId AND e.day > 0 AND e.startSection != 0
        ORDER BY e.week ASC, e.day ASC, e.startSection ASC
    """)
    suspend fun getAllEventsForTable(tableId: String): List<CourseEventWithMeta>

    // 课程聚合查询
    @Transaction
    @Query("SELECT * FROM courses WHERE tableId = :tableId ORDER BY name ASC")
    fun getAllCoursesWithEvents(tableId: String): Flow<List<CourseWithEvents>>

    @Transaction
    @Query("SELECT * FROM courses WHERE tableId = :tableId ORDER BY name ASC")
    suspend fun getAllCoursesWithEventsOnce(tableId: String): List<CourseWithEvents>

    @Query("UPDATE courses SET colorIndex = :colorIndex WHERE tableId = :tableId AND id = :courseId")
    suspend fun updateCourseColor(tableId: String, courseId: String, colorIndex: Int)

    // 原子操作：插入与清理
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<Course>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<CourseEvent>)

    @Query("DELETE FROM courses WHERE tableId = :tableId")
    suspend fun deleteCoursesByTableId(tableId: String)

    @Query("DELETE FROM course_events WHERE tableId = :tableId")
    suspend fun deleteEventsByTableId(tableId: String)

    @Transaction
    suspend fun replaceCoursesAndEvents(
        tableId: String,
        courses: List<Course>,
        events: List<CourseEvent>
    ) {
        deleteEventsByTableId(tableId)
        deleteCoursesByTableId(tableId)
        insertCourses(courses)
        insertEvents(events)
    }

    // 学期数据操作
    @Query("SELECT * FROM semesters ORDER BY schoolYear DESC, CAST(name AS INTEGER) DESC, CAST(id AS INTEGER) DESC")
    fun getAllSemesters(): Flow<List<Semester>>

    @Query("SELECT * FROM semesters WHERE isCurrent = 1 LIMIT 1")
    fun getCurrentSemester(): Flow<Semester?>

    @Query("SELECT * FROM semesters WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrentSemesterOnce(): Semester?

    @Query("SELECT * FROM semesters WHERE id = :semesterId LIMIT 1")
    suspend fun getSemesterById(semesterId: String): Semester?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSemesters(semesters: List<Semester>)

    @Query("UPDATE semesters SET isCurrent = CASE WHEN id = :activeSemesterId THEN 1 ELSE 0 END")
    suspend fun setActiveSemester(activeSemesterId: String)

    @Query("UPDATE semesters SET startDate = :startDate WHERE id = :semesterId")
    suspend fun updateSemesterStartDate(semesterId: String, startDate: String)

    @Query("UPDATE semesters SET totalWeeks = :totalWeeks WHERE id = :semesterId")
    suspend fun updateSemesterTotalWeeks(semesterId: String, totalWeeks: Int)

    @Query("DELETE FROM semesters")
    suspend fun clearSemesters()

    // 调课规则 (CourseAdjustment) 操作
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAdjustment(adjustment: CourseAdjustment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAdjustments(adjustments: List<CourseAdjustment>)

    @Transaction
    @Query("""
        SELECT a.id, a.tableId, a.semesterId, a.courseId, a.originalWeek, a.originalDay, 
               a.originalStartSection, a.originalEndSection, a.originalLocation, a.targetWeek, a.targetDay, 
               a.targetStartSection, a.targetEndSection, a.targetLocation, a.isSuspended, a.createdAt,
               COALESCE(c.name, '') as courseName, COALESCE(c.courseCode, '') as courseCode, 
               COALESCE(c.courseType, 'THEORY') as courseType, COALESCE(c.colorIndex, 0) as colorIndex,
               COALESCE(e.projectName, '') as projectName
        FROM course_adjustments a
        LEFT JOIN courses c ON a.courseId = c.id
        LEFT JOIN course_events e ON a.tableId = e.tableId 
                                 AND a.courseId = e.courseId 
                                 AND a.originalWeek = e.week 
                                 AND a.originalDay = e.day 
                                 AND a.originalStartSection = e.startSection
        WHERE a.tableId = :tableId AND a.semesterId = :semesterId
        GROUP BY a.id
        ORDER BY a.targetWeek ASC, a.targetDay ASC, a.targetStartSection ASC
    """)
    fun getAllAdjustmentsWithMeta(tableId: String, semesterId: String): Flow<List<CourseAdjustmentWithMeta>>

    @Query("SELECT * FROM course_adjustments WHERE tableId = :tableId AND semesterId = :semesterId")
    fun getAdjustmentsFlow(tableId: String, semesterId: String): Flow<List<CourseAdjustment>>

    @Query("SELECT * FROM course_adjustments WHERE tableId = :tableId AND semesterId = :semesterId")
    suspend fun getAdjustmentsOnce(tableId: String, semesterId: String): List<CourseAdjustment>

    @Query("""
        SELECT * FROM course_adjustments 
        WHERE tableId = :tableId AND semesterId = :semesterId
          AND courseId = :courseId AND originalWeek = :week AND originalDay = :day AND originalStartSection = :startSection
        LIMIT 1
    """)
    suspend fun getAdjustmentForEvent(
        tableId: String,
        semesterId: String,
        courseId: String,
        week: Int,
        day: Int,
        startSection: Int
    ): CourseAdjustment?

    @Query("DELETE FROM course_adjustments WHERE id = :id")
    suspend fun deleteAdjustmentById(id: Long)

    @Query("DELETE FROM course_adjustments WHERE tableId = :tableId")
    suspend fun clearAllAdjustments(tableId: String)
}

// 考试记录数据访问接口
@Dao
interface ExamDao {

    // 观察指定学期的考试记录
    @Query("""
        SELECT * FROM exam_records
        WHERE tableId = :tableId AND semesterId = :semesterId
        ORDER BY (examDate IS NULL) ASC, examDate ASC, startTime ASC, sequence ASC
    """)
    fun observeBySemester(tableId: String, semesterId: String): Flow<List<ExamRecord>>

    @Query("""
        SELECT * FROM exam_records
        WHERE tableId = :tableId AND semesterId = :semesterId
        ORDER BY (examDate IS NULL) ASC, examDate ASC, startTime ASC, sequence ASC
    """)
    suspend fun getBySemester(tableId: String, semesterId: String): List<ExamRecord>

    @Query("DELETE FROM exam_records WHERE tableId = :tableId AND semesterId = :semesterId")
    suspend fun clearBySemester(tableId: String, semesterId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ExamRecord>)

    // 按学期整批替换考试记录
    @Transaction
    suspend fun replaceSemesterExams(tableId: String, semesterId: String, records: List<ExamRecord>) {
        clearBySemester(tableId, semesterId)
        if (records.isNotEmpty()) insertAll(records)
    }
}

// 课表 Room 本地数据库
@Database(
    entities = [
        Semester::class,
        CourseTable::class,
        Course::class,
        CourseEvent::class,
        CourseAdjustment::class,
        ExamRecord::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ScheduleDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun examDao(): ExamDao
}

// 数据库 1 → 2：新增考试记录表与查询索引
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exam_records` (
                `id` TEXT NOT NULL,
                `tableId` TEXT NOT NULL,
                `semesterId` TEXT NOT NULL,
                `courseId` TEXT,
                `examType` TEXT NOT NULL,
                `sequence` INTEGER NOT NULL,
                `courseCode` TEXT NOT NULL,
                `courseName` TEXT NOT NULL,
                `examDate` TEXT,
                `week` INTEGER,
                `weekday` INTEGER,
                `startTime` TEXT,
                `endTime` TEXT,
                `position` TEXT,
                `seatNo` TEXT,
                `status` TEXT,
                `remark` TEXT NOT NULL,
                `arrangeText` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_exam_records_semester` " +
                "ON `exam_records` (`tableId`, `semesterId`, `examDate`)"
        )
    }
}
