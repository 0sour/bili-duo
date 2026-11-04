package cn.xfangfang.biliduo.ui.profile

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.xfangfang.biliduo.data.api.BilibiliApi
import cn.xfangfang.biliduo.data.local.PendingReportManager
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoggedIn: Boolean = false,
    val mid: Long = 0,
    val uname: String = "",
    val face: String = "",
    val level: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("bili_prefs", 0)
    private val repository = BilibiliRepository()

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    /**
     * Re-reads the local session and (when online) refreshes nickname / avatar /
     * level from nav. Called on every ON_RESUME so returning from the login
     * screen updates the card without relying on a duplicated Profile route.
     */
    fun refresh() {
        val sessdata = prefs.getString("sessdata", null)
        val uid = prefs.getString("dede_userid", "")?.toLongOrNull() ?: 0L
        if (sessdata.isNullOrEmpty()) {
            refreshJob?.cancel()
            _state.value = ProfileUiState(isLoggedIn = false)
            return
        }

        refreshJob?.cancel()
        _state.value = _state.value.copy(
            isLoggedIn = true,
            mid = if (uid != 0L) uid else _state.value.mid,
            isLoading = true,
            error = null,
        )
        refreshJob = viewModelScope.launch {
            repository.getNavInfo()
                .onSuccess { nav ->
                    if (nav.isLogin) {
                        _state.value = ProfileUiState(
                            isLoggedIn = true,
                            mid = nav.mid,
                            uname = nav.uname,
                            face = nav.face.replace("http://", "https://"),
                            level = nav.levelInfo?.currentLevel ?: 0,
                        )
                    } else {
                        // Cookie expired or revoked on the server side.
                        clearSession()
                    }
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message,
                    )
                }
        }
    }

    fun logout() {
        clearSession()
        PendingReportManager(prefs).clear()
    }

    private fun clearSession() {
        refreshJob?.cancel()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        prefs.edit()
            .remove("sessdata")
            .remove("bili_jct")
            .remove("dede_userid")
            .apply()
        BilibiliApi.loginCookies = ""
        BilibiliApi.biliJct = ""
        _state.value = ProfileUiState(isLoggedIn = false)
    }
}
