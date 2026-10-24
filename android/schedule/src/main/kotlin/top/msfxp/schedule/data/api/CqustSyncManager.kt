package top.msfxp.schedule.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.msfxp.schedule.data.model.COURSE_TYPE_PRACTICAL
import top.msfxp.schedule.data.model.COURSE_TYPE_THEORY
import top.msfxp.schedule.data.model.Semester
import top.msfxp.schedule.data.model.StudentProfile
import top.msfxp.schedule.data.model.effectiveStartDate
import top.msfxp.schedule.data.repository.ScheduleRepository
import top.msfxp.schedule.data.repository.SettingsRepository

// 同步结果统计数据模型
data class SyncResultSummary(
    val regularCount: Int,
    val practicalCount: Int
) {
    val totalCount: Int get() = regularCount + practicalCount
}

// 课表数据同步服务协调器
class CqustSyncManager(
    private val settingsRepository: SettingsRepository,
    private val scheduleRepository: ScheduleRepository
) {
    private val syncGate=Mutex()

    suspend fun forgetSavedPassword(studentId:String)=syncGate.withLock {
        val settings=settingsRepository.getAppSettingsOnce()
        if(settings.studentId==studentId)settingsRepository.updateSettings{it.copy(passwordEncrypted="")}
    }

    // 登录教务并同步课表到本地数据库
    suspend fun syncCourses(
        studentId: String,
        passwordRaw: String,
        targetSemesterId: String? = null,
        allowEmpty: Boolean = true,
        rememberPassword:Boolean=true,
        onProgress: ((String) -> Unit)? = null
    ): Result<SyncResultSummary> = withContext(Dispatchers.IO) {
        syncGate.withLock {
        val sid = studentId.trim()
        val pwd = passwordRaw

        if (sid.isEmpty()) return@withContext Result.failure(IllegalArgumentException("学号不能为空"))
        if (pwd.isEmpty()) return@withContext Result.failure(IllegalArgumentException("密码不能为空"))

        // 1. 建立教务传输会话
        val connectResult = CqustCasAuthClient.connect(sid, pwd, onProgress)
        if (connectResult.isFailure) {
            return@withContext Result.failure(connectResult.exceptionOrNull() ?: Exception("教务网络连接失败"))
        }
        val session = connectResult.getOrThrow()
        try {

        // 2. 抓取课表数据
        onProgress?.invoke("正在同步课表数据")
        val (eamsResult, practicalItems) = fetchScheduleData(session, sid, targetSemesterId, onProgress)

        if (!eamsResult.success) {
            return@withContext Result.failure(Exception(eamsResult.errorMessage ?: "教务课表解析失败"))
        }
        if (ScheduleSyncPolicy.rejectEmpty(allowEmpty,eamsResult.courses.size,practicalItems.size)) {
            return@withContext Result.failure(Exception("返回课程数为 0，不覆盖本地课表"))
        }

        // 3. 构建时空原子矩阵
        val tableId = scheduleRepository.getOrCreateDefaultTableId()
        val (courses, events) = ScheduleMatrixBuilder.build(
            tableId = tableId,
            eamsCourses = eamsResult.courses,
            practicalItems = practicalItems
        )

        // 4. 持久化存储（入库后由 CourseAlarmObserver 统一响应式触发日历同步）
        try {
            scheduleRepository.saveSchedule(tableId, courses, events)
            saveSemesters(eamsResult)
            persistProfileAndCredentials(sid, pwd, eamsResult.profile,rememberPassword)

            val regularCount = courses.count { it.courseType == COURSE_TYPE_THEORY }
            val practicalCount = courses.count { it.courseType == COURSE_TYPE_PRACTICAL }
            Result.success(SyncResultSummary(regularCount = regularCount, practicalCount = practicalCount))
        } catch (e: Exception) {
            Result.failure(Exception("课表数据入库失败: ${e.message}", e))
        }
        }catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){Result.failure(e)}
        finally{session.close()}
        }
    }

    // 并发协同抓取教务常规课表与实践教学课表
    private suspend fun fetchScheduleData(
        session: CqustWebVpnSession,
        studentId: String,
        targetSemesterId: String?,
        onProgress: ((String) -> Unit)?
    ): Pair<EamsFetchResult, List<RawPracticalItem>> {
        val (eamsResult, practicalResult) = coroutineScope {
            val eamsDeferred = async {
                CqustEamsParser.fetchAndParse(
                    session = session,
                    studentId = studentId,
                    targetSemesterId = targetSemesterId,
                    onProgress = onProgress
                )
            }
            val practicalDeferred = if (targetSemesterId == null) {
                async {
                    CqustPracticalParser.fetchAndParse(
                        session = session,
                        studentId = studentId,
                        targetYearTerm = null
                    )
                }
            } else null

            eamsDeferred.await() to practicalDeferred?.await()
        }

        var practicalItems = emptyList<RawPracticalItem>()
        var practicalFailed=practicalResult?.success==false
        if (practicalResult != null && practicalResult.success) {
            practicalItems = practicalResult.items
        } else if (targetSemesterId != null) {
            val semesterId = eamsResult.semesterId ?: eamsResult.semesters.firstOrNull()?.id.orEmpty()
            val semInfo = eamsResult.semesters.find { it.id == semesterId } ?: eamsResult.semesters.firstOrNull()
            val practicalTerm = if (semInfo != null) "${semInfo.schoolYear}-${semInfo.name}" else null

            onProgress?.invoke("正在获取学期实践课表")
            val termResult = CqustPracticalParser.fetchAndParse(session, studentId, practicalTerm)
            practicalFailed=!termResult.success
            if (termResult.success && termResult.items.isNotEmpty()) {
                practicalItems = termResult.items
            }
        }

        val current= scheduleRepository.getCurrentSemesterOnce()
        val sameSemester=targetSemesterId==null||current?.id==targetSemesterId
        val tableId=scheduleRepository.getOrCreateDefaultTableId()
        val existing=scheduleRepository.getAllCoursesWithEventsOnce(tableId)
        if(sameSemester&&eamsResult.courses.isEmpty()&&practicalItems.isEmpty()&&existing.isNotEmpty()){
            throw IllegalStateException("未解析到有效课程，已保留完整原课表。")
        }
        if(ScheduleSyncPolicy.keepPractical(sameSemester,practicalFailed,existing.any{it.course.isPractical})){
            throw IllegalStateException("实践课同步未完成，已保留完整原课表。")
        }
        return Pair(eamsResult, practicalItems)
    }

    // 保存学期列表至本地数据库
    private suspend fun saveSemesters(eamsResult: EamsFetchResult) {
        val semesterId = eamsResult.semesterId ?: eamsResult.semesters.firstOrNull()?.id.orEmpty()
        val semesterEntities = mutableListOf<Semester>()
        for (semInfo in eamsResult.semesters) {
            val existing = scheduleRepository.getSemesterById(semInfo.id)
            val startDate = existing?.startDate?.ifBlank { null }
                ?: semInfo.effectiveStartDate.toString()

            semesterEntities.add(
                Semester(
                    id = semInfo.id,
                    schoolYear = semInfo.schoolYear,
                    name = semInfo.name,
                    label = semInfo.label,
                    startDate = startDate,
                    totalWeeks = existing?.totalWeeks ?: 20,
                    isCurrent = (semInfo.id == semesterId)
                )
            )
        }
        scheduleRepository.clearSemesters()
        scheduleRepository.saveSemesters(semesterEntities)
        scheduleRepository.setActiveSemester(semesterId)
    }

    // 加密持久化档案与凭证
    private suspend fun persistProfileAndCredentials(
        studentId: String,
        passwordRaw: String,
        profile: StudentProfile,
        rememberPassword:Boolean
    ) {
        val encryptedPassword = if(rememberPassword)LocalCrypto.encrypt(passwordRaw)else""
        settingsRepository.saveFullProfileAndCredentials(
            studentId = studentId,
            studentName = profile.name,
            college = profile.college,
            major = profile.major,
            className = profile.className,
            passwordEncrypted = encryptedPassword
        )
    }

    // 静默同步课表
    suspend fun silentSync(onProgress: ((String) -> Unit)? = null): Result<SyncResultSummary> {
        val settings = settingsRepository.getAppSettingsOnce()
        if (!settings.isLoggedIn || settings.studentId.isBlank() || settings.passwordEncrypted.isBlank()) {
            return Result.failure(IllegalStateException("尚未保存登录凭证"))
        }
        val pwd = LocalCrypto.decrypt(settings.passwordEncrypted)
        val currentSemester = scheduleRepository.getCurrentSemesterOnce()
        return syncCourses(
            studentId = settings.studentId,
            passwordRaw = pwd,
            targetSemesterId = currentSemester?.id,
            allowEmpty = false,
            onProgress = onProgress
        )
    }

    // 满足冷却时间条件时后台静默同步
    suspend fun silentSyncIfNeeded(cooldownHours: Long = 12L): Result<SyncResultSummary>? {
        val settings = settingsRepository.getAppSettingsOnce()
        if (!settings.isLoggedIn || settings.studentId.isBlank() || settings.passwordEncrypted.isBlank()) {
            return null
        }
        val now = System.currentTimeMillis()
        val lastSync = settings.lastSyncTime
        if ((now - lastSync) < (cooldownHours * 3600L * 1000L)) return null
        val res = silentSync()
        if (res.isSuccess) {
            settingsRepository.updateLastSyncTime(now)
        }
        return res
    }
}

// 本地密码加解密工具 (使用 AndroidKeyStore 硬件保护密钥，AES-GCM 算法)
object LocalCrypto {
    private const val KEY_ALIAS = "CqustMiuixScheduleLocalKey"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private fun getOrCreateSecretKey(): javax.crypto.SecretKey {
        val keyStore = java.security.KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as javax.crypto.SecretKey }

        val keyGenerator = javax.crypto.KeyGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    // AES-GCM 加密 (输出为 Base64 编码的 IV + 密文)
    fun encrypt(rawText: String): String {
        if (rawText.isEmpty()) return ""
        return try {
            val cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION)
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val cipherText = cipher.doFinal(rawText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("LocalCrypto", "KeyStore encryption failed, fallback to raw", e)
            rawText
        }
    }

    // AES-GCM 解密
    fun decrypt(encryptedText: String): String {
        if (encryptedText.isEmpty()) return ""
        return try {
            val combined = android.util.Base64.decode(encryptedText, android.util.Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) return encryptedText
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val cipherText = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION)
            val spec = javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("LocalCrypto", "KeyStore decryption failed, fallback to encrypted", e)
            encryptedText
        }
    }
}
