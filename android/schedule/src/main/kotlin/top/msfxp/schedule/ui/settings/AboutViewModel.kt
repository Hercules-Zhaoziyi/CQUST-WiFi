package top.msfxp.schedule.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.msfxp.schedule.BuildConfig
import top.msfxp.schedule.R
import top.msfxp.schedule.data.api.AppRelease
import top.msfxp.schedule.data.api.UpdateCheckService
import top.msfxp.schedule.data.repository.SettingsRepository
import top.msfxp.schedule.util.AppActionHelper

// 更新状态枚举
enum class UpdateCheckState {
    IDLE,
    CHECKING,
    LATEST,
    HAS_NEW_VERSION,
    FAILED
}

// 关于界面 UI 状态
data class AboutUiState(
    val localVersionName: String = "",
    val checkState: UpdateCheckState = UpdateCheckState.IDLE,
    val latestRelease: AppRelease? = null,
    val showUpdateDialog: Boolean = false,
    val directApkDownloadUrl: String? = null,
    val isDownloadStarted: Boolean = false,
    val showTestFeatures: Boolean = false,
    val errorMessageResId: Int? = null,
    val errorMessage: String? = null
)

// 关于界面视图模型
class AboutViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AboutUiState(
            localVersionName = BuildConfig.VERSION_NAME
        )
    )
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

    private val _toastMessageResId = MutableSharedFlow<Int>()
    val toastMessageResId: SharedFlow<Int> = _toastMessageResId.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.appSettingsFlow.collect { settings ->
                _uiState.update { it.copy(showTestFeatures = settings.showTestFeatures) }

                // 首次处于 IDLE 时检查本地持久化缓存
                if (_uiState.value.checkState == UpdateCheckState.IDLE) {
                    val cachedTag = settings.latestVersionTag
                    val cachedUrl = settings.latestVersionDownloadUrl
                    if (cachedTag.isNotBlank() && cachedUrl.isNotBlank()) {
                        if (UpdateCheckService.isNewerVersion(cachedTag, _uiState.value.localVersionName)) {
                            val cachedRelease = AppRelease(
                                tagName = cachedTag,
                                body = settings.latestVersionBody,
                                htmlUrl = cachedUrl
                            )
                            _uiState.update {
                                it.copy(
                                    checkState = UpdateCheckState.HAS_NEW_VERSION,
                                    latestRelease = cachedRelease,
                                    directApkDownloadUrl = cachedUrl,
                                    showUpdateDialog = false
                                )
                            }
                        } else {
                            settingsRepository.clearLatestReleaseInfo()
                        }
                    }
                }
            }
        }
    }

    // 切换测试功能开关
    fun updateShowTestFeatures(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateShowTestFeatures(show)
        }
    }

    // 点击检查更新栏目 (始终发起实时网络请求，拉取服务器此时的最新版本)
    fun onCheckUpdateClicked() {
        if (_uiState.value.checkState == UpdateCheckState.CHECKING) return
        checkForUpdates(manual = true)
    }

    // 检查最新版本 (优先 Gitee 直连，降级 GitHub)
    fun checkForUpdates(manual: Boolean = true) {
        if (_uiState.value.checkState == UpdateCheckState.CHECKING) return

        _uiState.update {
            it.copy(
                checkState = UpdateCheckState.CHECKING,
                errorMessageResId = null,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val result = UpdateCheckService.fetchLatestRelease(
                onFallbackToGithub = {
                    viewModelScope.launch {
                        _toastMessageResId.emit(R.string.about_fallback_to_github_toast)
                    }
                }
            )
            result.onSuccess { release ->
                settingsRepository.updateLastUpdateCheckTime(System.currentTimeMillis())
                if (UpdateCheckService.isNewerVersion(release.tagName, _uiState.value.localVersionName)) {
                    val directUrl = UpdateCheckService.resolveApkDownloadUrl(release)

                    _uiState.update {
                        it.copy(
                            checkState = UpdateCheckState.HAS_NEW_VERSION,
                            latestRelease = release,
                            directApkDownloadUrl = directUrl,
                            isDownloadStarted = false,
                            showUpdateDialog = manual,
                            errorMessageResId = null,
                            errorMessage = null
                        )
                    }

                    // 持久化最新版本信息至本地
                    settingsRepository.saveLatestReleaseInfo(
                        tag = release.tagName,
                        downloadUrl = directUrl,
                        body = release.body.orEmpty()
                    )
                } else {
                    _uiState.update {
                        it.copy(
                            checkState = UpdateCheckState.LATEST,
                            latestRelease = release,
                            showUpdateDialog = false,
                            errorMessageResId = null,
                            errorMessage = null
                        )
                    }
                    // 已是最新版，清理本地旧版本缓存
                    settingsRepository.clearLatestReleaseInfo()
                }
            }.onFailure { throwable ->
                val errRes = resolveFriendlyErrorMessageRes(throwable)
                _uiState.update {
                    it.copy(
                        checkState = UpdateCheckState.FAILED,
                        showUpdateDialog = false,
                        errorMessageResId = errRes,
                        errorMessage = null
                    )
                }
            }
        }
    }

    // 执行安装包下载操作
    fun executeDownload(
        context: Context,
        onNotify: (String) -> Unit
    ) {
        val currentState = _uiState.value
        val release = currentState.latestRelease ?: return

        // 已经触发过系统下载时提示并拦截重复创建
        if (currentState.isDownloadStarted) {
            onNotify(context.getString(R.string.about_download_started_toast))
            dismissUpdateDialog()
            return
        }

        dismissUpdateDialog()
        val success = AppActionHelper.processReleaseDownload(context, release, onNotify)
        if (success) {
            _uiState.update { it.copy(isDownloadStarted = true) }
        }
    }

    // 解析用户友好的错误文本资源 ID
    private fun resolveFriendlyErrorMessageRes(throwable: Throwable): Int {
        val causes = generateSequence(throwable) { it.cause }
        for (cause in causes) {
            val msg = cause.message.orEmpty().lowercase()
            val className = cause::class.java.simpleName.lowercase()
            if (className.contains("timeout") || msg.contains("timeout") || msg.contains("timed out")) {
                return R.string.about_error_network_timeout
            }
            if (msg.contains("403") || msg.contains("rate limit")) {
                return R.string.about_error_rate_limited
            }
            if (className.contains("unknownhost") || className.contains("connectexception") || msg.contains("failed to connect")) {
                return R.string.about_error_network_failed
            }
        }
        return R.string.about_error_network_failed
    }

    // 关闭更新弹窗
    fun dismissUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = false) }
    }
}
