package cn.xfangfang.biliduo.ui.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.xfangfang.biliduo.BilibiliApp
import cn.xfangfang.biliduo.data.model.HistoryItem
import cn.xfangfang.biliduo.data.model.HistorySearchItem
import cn.xfangfang.biliduo.data.model.HistoryPage
import cn.xfangfang.biliduo.data.model.VideoOwner
import cn.xfangfang.biliduo.data.model.VideoStat
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val searchResults: List<HistoryItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSearchLoading: Boolean = false,
    val error: String? = null,
    val actionError: String? = null,
    val currentPage: Int = 1,
    val hasMore: Boolean = false
)

class HistoryViewModel(
    private val repository: BilibiliRepository = BilibiliRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    fun loadHistory() {
        Log.d(BilibiliApp.TAG, "loadHistory")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.clearHistoryCache()
            repository.getHistory(page = 1)
                .onSuccess { list ->
                    Log.d(BilibiliApp.TAG, "loadHistory success: ${list.size} items")
                    _uiState.value = _uiState.value.copy(
                        items = list,
                        isLoading = false,
                        currentPage = 1,
                        hasMore = list.size >= 20
                    )
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "loadHistory error: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "未知错误"
                    )
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return

        val nextPage = state.currentPage + 1
        Log.d(BilibiliApp.TAG, "loadMore: page=$nextPage")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            repository.getHistory(page = nextPage)
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(
                        items = _uiState.value.items + list,
                        isLoadingMore = false,
                        currentPage = nextPage,
                        hasMore = list.size >= 20
                    )
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "loadMore error: ${e.message}")
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                }
        }
    }

    fun deleteHistory(item: HistoryItem) {
        viewModelScope.launch {
            repository.deleteHistory(item.aid)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        items = _uiState.value.items.filterNot { it.aid == item.aid },
                        searchResults = _uiState.value.searchResults.filterNot { it.aid == item.aid },
                        actionError = null,
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        actionError = e.message ?: "删除失败",
                    )
                }
        }
    }

    fun clearActionError() {
        if (_uiState.value.actionError != null) {
            _uiState.value = _uiState.value.copy(actionError = null)
        }
    }

    fun setSearchQuery(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                searchQuery = "", searchResults = emptyList(), isSearchLoading = false
            )
            return
        }
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchInHistory(query)
    }

    private fun searchInHistory(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearchLoading = true)
            repository.searchHistory(keyword = query)
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(
                        searchResults = list.map { it.toHistoryItem() },
                        isSearchLoading = false
                    )
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "searchInHistory error: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        isSearchLoading = false,
                        error = e.message ?: "搜索失败"
                    )
                }
        }
    }

    private fun HistorySearchItem.toHistoryItem() = HistoryItem(
        bvid = history?.bvid ?: "",
        aid = history?.oid ?: 0,
        title = title,
        pic = cover,
        owner = VideoOwner(mid = authorMid, name = authorName, face = authorFace ?: ""),
        stat = VideoStat(),
        duration = duration,
        progress = progress,
        viewAt = viewAt,
        cid = history?.cid ?: 0,
        page = history?.let { HistoryPage(page = it.page, part = it.part) },
        videos = videos
    )
}
