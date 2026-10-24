package top.msfxp.schedule.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import top.msfxp.schedule.data.model.*

// 课表数据库数据访问接口
@Dao
interface ScheduleDao {

    // 课表元数据操作
    @Query("SELECT * FROM course_tables LIMIT 1")
    fun getDefaultCourseTable(): Flow<CourseTable?>

    @Query("SELECT * FROM course_tables WHERE id = :tableId LIMIT 1")
    suspend fun getCourseTableById(tableId: String): CourseTable?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourseTable(table: CourseTable)

    // 查询指定周已排时段教学事件
    @Transaction
    @Query("""
        SELECT 
            e.id, e.courseId, e.tableId, e.week, e.day, e.startSection, e.endSection,
            e.location, e.teacher, e.projectName, e.studentCount,
            c.name as courseName, c.courseCode as courseCode, c.credit as credit,
            c.category as category, c.courseType as courseType, c.colorIndex as colorIndex
        FROM course_events e
        INNER JOIN courses c ON e.courseId = c.id
        WHERE e.tableId = :tableId AND e.week = :week AND e.day > 0 AND e.startSection > 0
        ORDER BY e.day ASC, e.startSection ASC
    """)
    fun getWeekGridEvents(tableId: String, week: Int): Flow<List<CourseEventWithMeta>>

    // 查询指定周未排时段教学事件
    @Transaction
    @Query("""
        SELECT 
            e.id, e.courseId, e.tableId, e.week, e.day, e.startSection, e.endSection,
            e.location, e.teacher, e.projectName, e.studentCount,
            c.name as courseName, c.courseCode as courseCode, c.credit as credit,
            c.category as category, c.courseType as courseType, c.colorIndex as colorIndex
        FROM course_events e
        INNER JOIN courses c ON e.courseId = c.id
        WHERE e.tableId = :tableId AND e.week = :week AND (e.day = 0 OR e.startSection = 0)
    """)
    fun getWeekUnarrangedEvents(tableId: String, week: Int): Flow<List<CourseEventWithMeta>>

    // 查询指定周与星期的已排教学事件
    @Transaction
    @Query("""
        SELECT 
            e.id, e.courseId, e.tableId, e.week, e.day, e.startSection, e.endSection,
            e.location, e.teacher, e.projectName, e.studentCount,
            c.name as courseName, c.courseCode as courseCode, c.credit as credit,
            c.category as category, c.courseType as courseType, c.colorIndex as colorIndex
        FROM course_events e
        INNER JOIN courses c ON e.courseId = c.id
        WHERE e.tableId = :tableId AND e.week = :week AND e.day = :day AND e.startSection > 0
        ORDER BY e.startSection ASC
    """)
    suspend fun getDayEvents(tableId: String, week: Int, day: Int): List<CourseEventWithMeta>

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
        WHERE e.tableId = :tableId AND e.day > 0 AND e.startSection > 0
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSemester(semester: Semester)

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

    @Query("DELETE FROM course_adjustments WHERE tableId = :tableId AND semesterId = :semesterId")
    suspend fun clearAdjustmentsBySemester(tableId: String, semesterId: String)

    @Query("DELETE FROM course_adjustments WHERE tableId = :tableId")
    suspend fun clearAllAdjustments(tableId: String)
}

// 课表 Room 本地数据库
@Database(
    entities = [
        Semester::class,
        CourseTable::class,
        Course::class,
        CourseEvent::class,
        CourseAdjustment::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ScheduleDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
}
