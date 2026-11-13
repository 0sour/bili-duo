package cn.xfangfang.biliduo.ui.watchlater

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.xfangfang.biliduo.BilibiliApp
import cn.xfangfang.biliduo.data.model.WatchLaterItem
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WatchLaterUiState(
    val items: List<WatchLaterItem> = emptyList(),
    val count: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val actionError: String? = null,
)

class WatchLaterViewModel(
    private val repository: BilibiliRepository = BilibiliRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchLaterUiState())
    val uiState: StateFlow<WatchLaterUiState> = _uiState.asStateFlow()

    fun load() {
        Log.d(BilibiliApp.TAG, "WatchLater: load")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getWatchLater()
                .onSuccess { (items, count) ->
                    Log.d(BilibiliApp.TAG, "WatchLater: ${items.size} items, count=$count")
                    _uiState.value = _uiState.value.copy(
                        items = items,
                        count = count,
                        isLoading = false,
                        error = null,
                    )
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "WatchLater: load failed", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "加载失败",
                    )
                }
        }
    }

    fun remove(item: WatchLaterItem) {
        viewModelScope.launch {
            repository.removeWatchLater(item.aid)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        items = _uiState.value.items.filterNot { it.aid == item.aid },
                        count = (_uiState.value.count - 1).coerceAtLeast(0),
                        actionError = null,
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        actionError = e.message ?: "移除失败",
                    )
                }
        }
    }

    fun clearActionError() {
        if (_uiState.value.actionError != null) {
            _uiState.value = _uiState.value.copy(actionError = null)
        }
    }
}
