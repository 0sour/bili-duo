package cn.xfangfang.biliduo.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.xfangfang.biliduo.BilibiliApp
import cn.xfangfang.biliduo.data.local.SearchHistoryManager
import cn.xfangfang.biliduo.data.model.SearchVideoItem
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import cn.xfangfang.biliduo.util.cleanHtmlText
import cn.xfangfang.biliduo.util.formatDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchSortOption(
    val value: String,
    val label: String
)

object SearchSort {
    val options = listOf(
        SearchSortOption("", "综合"),
        SearchSortOption("click", "播放多"),
        SearchSortOption("pubdate", "最新"),
        SearchSortOption("dm", "弹幕多"),
        SearchSortOption("stow", "收藏多")
    )
}

data class SearchUiState(
    val query: String = "",
    val results: List<SearchVideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val moreError: String? = null,
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val hasSearched: Boolean = false,
    val sortBy: SearchSortOption = SearchSort.options[0],
    val searchHistory: List<String> = emptyList(),
    val hotSearches: List<String> = defaultHotSearches,
    val suggestions: List<String> = emptyList(),
    val submittedQuery: String = "",
)

private val defaultHotSearches = listOf(
    "原神", "崩坏：星穹铁道", "明日方舟", "英雄联盟",
    "周杰伦", "罗翔说刑法", "影视飓风", "数码",
    "美食", "电影解说"
)

class SearchViewModel(
    private val repository: BilibiliRepository = BilibiliRepository()
) : ViewModel() {

    private val historyManager = SearchHistoryManager(
        BilibiliApp.instance.getSharedPreferences("bili_prefs", android.content.Context.MODE_PRIVATE)
    )

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var suggestJob: Job? = null

    init {
        _uiState.value = _uiState.value.copy(searchHistory = historyManager.load())
        viewModelScope.launch {
            repository.getHotSearches().onSuccess { hot ->
                if (hot.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(hotSearches = hot.take(20))
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        suggestJob?.cancel()
        if (query.isEmpty() && _uiState.value.query.isNotEmpty()) {
            Log.d(BilibiliApp.TAG, "onQueryChange: query cleared")
            searchJob?.cancel()
            _uiState.value = _uiState.value.copy(
                query = "",
                results = emptyList(),
                isLoading = false,
                isLoadingMore = false,
                error = null,
                moreError = null,
                currentPage = 1,
                hasMore = false,
                hasSearched = false,
                suggestions = emptyList(),
                submittedQuery = "",
            )
        } else {
            _uiState.value = _uiState.value.copy(query = query, suggestions = emptyList())
            val trimmed = query.trim()
            if (trimmed.isNotEmpty()) {
                suggestJob = viewModelScope.launch {
                    delay(250L)
                    if (_uiState.value.query.trim() != trimmed) return@launch
                    repository.suggestSearch(trimmed).onSuccess { list ->
                        if (_uiState.value.query.trim() == trimmed) {
                            _uiState.value = _uiState.value.copy(suggestions = list)
                        }
                    }
                }
            }
        }
    }

    fun setSortBy(sort: SearchSortOption) {
        if (sort.value == _uiState.value.sortBy.value) return
        _uiState.value = _uiState.value.copy(sortBy = sort)
        searchJob?.cancel()
        search()
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) {
            Log.d(BilibiliApp.TAG, "search: skipped due to empty query")
            return
        }

        val order = _uiState.value.sortBy.value
        Log.d(BilibiliApp.TAG, "search: query=$query sort=$order")
        suggestJob?.cancel()
        _uiState.value = _uiState.value.copy(
            submittedQuery = query,
            suggestions = emptyList(),
        )
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500)
            if (query != _uiState.value.query.trim() || order != _uiState.value.sortBy.value) return@launch

            val newHistory = historyManager.add(query)
            _uiState.value = _uiState.value.copy(searchHistory = newHistory)

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                moreError = null,
                hasSearched = true,
                results = emptyList(),
                currentPage = 1
            )
            repository.search(query, page = 1, order = order)
                .onSuccess { (results, totalPages) ->
                    Log.d(BilibiliApp.TAG, "search success: ${results.size} results, pages=$totalPages")
                    _uiState.value = _uiState.value.copy(
                        results = results.distinctBy { it.bvid }.filter { it.bvid.isNotBlank() }
                            .map { it.copy(
                                title = cleanHtmlText(it.title),
                                author = cleanHtmlText(it.author),
                                description = cleanHtmlText(it.description),
                                duration = formatDuration(it.duration)
                            ) },
                        isLoading = false,
                        currentPage = 1,
                        hasMore = totalPages > 1
                    )
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "search error: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "未知错误"
                    )
                }
        }
    }

    fun nextSort() {
        val current = _uiState.value.sortBy
        val index = SearchSort.options.indexOf(current)
        if (index < SearchSort.options.lastIndex) {
            setSortBy(SearchSort.options[index + 1])
        }
    }

    fun prevSort() {
        val current = _uiState.value.sortBy
        val index = SearchSort.options.indexOf(current)
        if (index > 0) {
            setSortBy(SearchSort.options[index - 1])
        }
    }

    fun loadMore() {
        searchJob?.cancel()
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return

        val nextPage = state.currentPage + 1
        Log.d(BilibiliApp.TAG, "loadMore: page=$nextPage query=${state.query}")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            repository.search(state.query, page = nextPage, order = state.sortBy.value)
                .onSuccess { (results, totalPages) ->
                    val existingBvids = _uiState.value.results.map { it.bvid }.toSet()
                    val deduped = results.filter { it.bvid !in existingBvids && it.bvid.isNotBlank() }
                        .map { it.copy(
                            title = cleanHtmlText(it.title),
                            author = cleanHtmlText(it.author),
                            description = cleanHtmlText(it.description),
                            duration = formatDuration(it.duration)
                        ) }
                    _uiState.value = _uiState.value.copy(
                        results = _uiState.value.results + deduped,
                        isLoadingMore = false,
                        moreError = null,
                        currentPage = nextPage,
                        hasMore = nextPage < totalPages
                    )
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "loadMore error: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        moreError = e.message ?: "加载失败"
                    )
                }
        }
    }

    fun clearHistory() {
        val cleared = historyManager.clear()
        _uiState.value = _uiState.value.copy(searchHistory = cleared)
    }

    fun removeFromHistory(query: String) {
        val updated = historyManager.remove(query)
        _uiState.value = _uiState.value.copy(searchHistory = updated)
    }

    fun searchFromHistory(query: String) {
        _uiState.value = _uiState.value.copy(query = query, suggestions = emptyList())
        search()
    }

    fun searchSuggestion(query: String) {
        _uiState.value = _uiState.value.copy(query = query, suggestions = emptyList())
        search()
    }

    fun searchHot(query: String) = searchFromHistory(query)

    fun retryLoadMore() {
        _uiState.value = _uiState.value.copy(moreError = null)
        loadMore()
    }

    fun clearResults() {
        Log.d(BilibiliApp.TAG, "clearResults: called from X")
        searchJob?.cancel()
        suggestJob?.cancel()
        _uiState.value = _uiState.value.copy(
            query = "",
            suggestions = emptyList(),
            submittedQuery = "",
            results = emptyList(),
            hasMore = false,
            hasSearched = false,
            currentPage = 1,
            error = null,
            moreError = null,
            isLoading = false,
            isLoadingMore = false
        )
    }
}
