package top.msfxp.schedule.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Release 资源文件模型
@Serializable
data class AppAsset(
    @SerialName("name")
    val name: String = "",
    @SerialName("browser_download_url")
    val browserDownloadUrl: String = "",
    @SerialName("size")
    val size: Long = 0L
)

// Release 数据模型
@Serializable
data class AppRelease(
    @SerialName("tag_name")
    val tagName: String = "",
    @SerialName("name")
    val name: String? = null,
    @SerialName("body")
    val body: String? = null,
    @SerialName("html_url")
    val htmlUrl: String = "",
    @SerialName("assets")
    val assets: List<AppAsset> = emptyList()
)

typealias GithubAsset = AppAsset
typealias GithubRelease = AppRelease

// 应用更新检查服务 (优先 Gitee 快速通道，降级 GitHub 官方源)
object UpdateCheckService {
    private const val GITEE_API_BASE = "https://gitee.com/api/v5/repos"
    private const val GITHUB_API_BASE = "https://api.github.com/repos"
    private const val DEFAULT_OWNER = "yuexps"
    private const val DEFAULT_REPO = "cqust-schedule-miuix"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client by lazy {
        HttpClient(Android) {
            install(HttpTimeout) {
                requestTimeoutMillis = 6000
                connectTimeoutMillis = 3000
                socketTimeoutMillis = 6000
            }
        }
    }

    // 检查是否有新版本 Release (优先 Gitee，降级 GitHub)
    suspend fun fetchLatestRelease(
        owner: String = DEFAULT_OWNER,
        repo: String = DEFAULT_REPO,
        onFallbackToGithub: (() -> Unit)? = null
    ): Result<AppRelease> {
        val giteeUrl = "$GITEE_API_BASE/$owner/$repo/releases/latest"
        val giteeResult = runCatching {
            val release = fetchReleaseFromUrl(giteeUrl)
            if (release.htmlUrl.isBlank()) {
                release.copy(htmlUrl = "https://gitee.com/$owner/$repo/releases/tag/${release.tagName}")
            } else {
                release
            }
        }

        if (giteeResult.isSuccess) {
            return giteeResult
        }

        // Gitee 失败，通知降级回调
        onFallbackToGithub?.invoke()

        // Gitee 失败时平滑降级至 GitHub 官方源
        val githubUrl = "$GITHUB_API_BASE/$owner/$repo/releases/latest"
        return runCatching { fetchReleaseFromUrl(githubUrl) }
    }

    // 请求指定地址的 Release 信息
    private suspend fun fetchReleaseFromUrl(url: String): AppRelease {
        val response = client.get(url) {
            header("Accept", "application/json")
            header("User-Agent", "cqust-schedule-app")
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val bodyText = response.bodyAsText()
                json.decodeFromString<AppRelease>(bodyText)
            }
            HttpStatusCode.NotFound -> throw IllegalStateException("HTTP 404 Not Found")
            HttpStatusCode.Forbidden -> throw IllegalStateException("HTTP 403 Forbidden")
            else -> throw IllegalStateException("HTTP ${response.status.value}")
        }
    }

    // 解析直链 APK 地址
    fun resolveApkDownloadUrl(release: AppRelease): String {
        return release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }?.browserDownloadUrl
            ?.takeIf { it.isNotBlank() }
            ?: release.htmlUrl
    }

    // 版本号比较逻辑
    fun isNewerVersion(remoteTag: String, localVersionName: String): Boolean {
        val remoteNums = extractVersionNumbers(remoteTag)
        val localNums = extractVersionNumbers(localVersionName)
        val maxLen = maxOf(remoteNums.size, localNums.size)

        for (i in 0 until maxLen) {
            val r = remoteNums.getOrElse(i) { 0 }
            val l = localNums.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    // 提取纯数字版本分段
    private fun extractVersionNumbers(raw: String): List<Int> {
        val clean = raw.trim().trimStart('v', 'V').split("-", "_", "+")[0]
        return clean.split(".").mapNotNull { it.trim().toIntOrNull() }
    }

    // 满足冷却时间条件时后台静默检查更新 (默认冷却 24 小时)
    suspend fun silentCheckUpdateIfNeeded(
        settingsRepository: top.msfxp.schedule.data.repository.SettingsRepository,
        localVersionName: String,
        cooldownHours: Long = 24L
    ): AppRelease? {
        val settings = settingsRepository.getAppSettingsOnce()
        val now = System.currentTimeMillis()
        val lastCheck = settings.lastUpdateCheckTime
        if ((now - lastCheck) < (cooldownHours * 3600L * 1000L)) {
            return null
        }

        settingsRepository.updateLastUpdateCheckTime(now)

        val result = fetchLatestRelease()
        val release = result.getOrNull() ?: return null

        if (isNewerVersion(release.tagName, localVersionName)) {
            val directUrl = resolveApkDownloadUrl(release)
            settingsRepository.saveLatestReleaseInfo(
                tag = release.tagName,
                downloadUrl = directUrl,
                body = release.body.orEmpty()
            )
            return release
        } else {
            settingsRepository.clearLatestReleaseInfo()
            return null
        }
    }
}
