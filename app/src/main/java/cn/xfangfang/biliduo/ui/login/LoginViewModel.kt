package cn.xfangfang.biliduo.ui.login

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.xfangfang.biliduo.BuildConfig
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data class QRReady(val url: String, val key: String) : LoginUiState()
    data object Scanned : LoginUiState()
    data object Expired : LoginUiState()
    data object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BilibiliRepository()

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Loading)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess: StateFlow<Boolean> = _loginSuccess.asStateFlow()

    private var pollingJob: Job? = null

    init {
        generateQR()
    }

    private fun generateQR() {
        pollingJob?.cancel()
        viewModelScope.launch {
            _state.value = LoginUiState.Loading
            repository.generateQRCode()
                .onSuccess { data ->
                    _state.value = LoginUiState.QRReady(data.url, data.qrcodeKey)
                    startPolling(data.qrcodeKey)
                }
                .onFailure { e ->
                    _state.value = LoginUiState.Error(e.message ?: "生成二维码失败")
                }
        }
    }

    private fun startPolling(key: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(1500)
                repository.pollQRCode(key)
                    .onSuccess { (statusCode, pollData) ->
                        when (statusCode) {
                            0 -> {
                                val url = pollData?.url
                                if (url != null) {
                                    repository.resolveQrLogin(url)
                                        .onSuccess { cookies ->
                                            if (saveLoginSession(getApplication(), cookies)) {
                                                if (BuildConfig.DEBUG) Log.d("LoginVM", "Login success")
                                                _state.value = LoginUiState.Success
                                                _loginSuccess.value = true
                                            } else {
                                                _state.value = LoginUiState.Error("登录成功但无法获取凭证")
                                            }
                                        }
                                        .onFailure {
                                            _state.value = LoginUiState.Error(
                                                it.message ?: "登录成功但无法获取凭证"
                                            )
                                        }
                                } else {
                                    _state.value = LoginUiState.Error("登录成功但无法获取凭证")
                                }
                                return@launch
                            }
                            86038 -> {
                                _state.value = LoginUiState.Expired
                                return@launch
                            }
                            86090 -> {
                                _state.value = LoginUiState.Scanned
                            }
                        }
                    }
                    .onFailure {
                        if (BuildConfig.DEBUG) Log.e("LoginVM", "Poll failed")
                    }
            }
        }
    }

    fun retry() {
        generateQR()
    }
}
