package top.msfxp.schedule.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.msfxp.schedule.R
import top.msfxp.schedule.data.api.CqustSyncManager
import top.msfxp.schedule.data.repository.SettingsRepository

// 登录界面状态
data class LoginUiState(
    val studentId: String = "",
    val passwordRaw: String = "",
    val isLoading: Boolean = false,
    val progressMessage: String = "",
    val errorMessage: String? = null,
    val isLoginSuccess: Boolean = false
)

// 统一身份认证与教务课表导入视图模型
class CqustLoginViewModel(
    private val context: Context,
    private val cqustSyncManager: CqustSyncManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.getAppSettingsOnce()
            if (settings.studentId.isNotEmpty()) {
                _uiState.update { it.copy(studentId = settings.studentId) }
            }
        }
    }

    // 更新输入的学号
    fun onStudentIdChanged(id: String) {
        _uiState.update { it.copy(studentId = id, errorMessage = null) }
    }

    // 更新输入的密码
    fun onPasswordChanged(pwd: String) {
        _uiState.update { it.copy(passwordRaw = pwd, errorMessage = null) }
    }

    // 执行登录并同步课表数据
    fun performLogin(onSuccess: () -> Unit) {
        val sid = _uiState.value.studentId.trim()
        val pwd = _uiState.value.passwordRaw.trim()

        if (sid.isEmpty()) {
            _uiState.update { it.copy(errorMessage = context.getString(R.string.login_error_empty_student_id)) }
            return
        }
        if (pwd.isEmpty()) {
            _uiState.update { it.copy(errorMessage = context.getString(R.string.login_error_empty_password)) }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                progressMessage = context.getString(R.string.login_connecting_hint),
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val result = cqustSyncManager.syncCourses(
                studentId = sid,
                passwordRaw = pwd,
                onProgress = { msg ->
                    _uiState.update { it.copy(progressMessage = msg) }
                }
            )

            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, isLoginSuccess = true) }
                onSuccess()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: context.getString(R.string.login_failed_general)
                    )
                }
            }
        }
    }
}
