package cn.xfangfang.biliduo.ui.follows

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.xfangfang.biliduo.BilibiliApp
import cn.xfangfang.biliduo.data.model.PgcFollowItem
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MyFollowsUiState(
    /** 1 = 番剧, 2 = 影视. */
    val type: Int = 1,
    val items: List<PgcFollowItem> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
)

class MyFollowsViewModel(
    private val repository: BilibiliRepository = BilibiliRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyFollowsUiState())
    val uiState: StateFlow<MyFollowsUiState> = _uiState.asStateFlow()

    private var requestId = 0

    fun load(type: Int = _uiState.value.type) {
        val id = ++requestId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                type = type,
                items = if (type == _uiState.value.type) _uiState.value.items else emptyList(),
                page = 1,
                hasMore = false,
                isLoading = true,
                isLoadingMore = false,
                error = null,
            )
            repository.getPgcFollows(type = type, page = 1)
                .onSuccess { (items, hasMore) ->
                    if (id != requestId) return@onSuccess
                    Log.d(BilibiliApp.TAG, "MyFollows: type=$type ${items.size} items")
                    _uiState.value = _uiState.value.copy(
                        type = type,
                        items = items.distinctBy { it.seasonId },
                        page = 1,
                        hasMore = hasMore,
                        isLoading = false,
                        isLoadingMore = false,
                        error = null,
                    )
                }
                .onFailure { e ->
                    if (id != requestId) return@onFailure
                    Log.e(BilibiliApp.TAG, "MyFollows: load failed", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = e.message ?: "加载失败",
                    )
                }
        }
    }

    fun selectType(type: Int) {
        if (type == _uiState.value.type) return
        load(type)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val nextPage = state.page + 1
        val id = requestId
        viewModelScope.launch {
            _uiState.value = state.copy(isLoadingMore = true)
            repository.getPgcFollows(type = state.type, page = nextPage)
                .onSuccess { (items, hasMore) ->
                    if (id != requestId) return@onSuccess
                    val known = _uiState.value.items.map { it.seasonId }.toSet()
                    _uiState.value = _uiState.value.copy(
                        items = _uiState.value.items + items.filter { it.seasonId !in known },
                        page = nextPage,
                        hasMore = hasMore,
                        isLoadingMore = false,
                    )
                }
                .onFailure { e ->
                    if (id != requestId) return@onFailure
                    Log.e(BilibiliApp.TAG, "MyFollows: loadMore failed", e)
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                }
        }
    }

    fun refresh() = load(_uiState.value.type)
}
