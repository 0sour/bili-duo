package cn.xfangfang.biliduo.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.xfangfang.biliduo.BilibiliApp
import cn.xfangfang.biliduo.data.local.SearchHistoryManager
import cn.xfangfang.biliduo.data.model.SearchArticleItem
import cn.xfangfang.biliduo.data.model.SearchLiveItem
import cn.xfangfang.biliduo.data.model.SearchPgcItem
import cn.xfangfang.biliduo.data.model.SearchUserItem
import cn.xfangfang.biliduo.data.model.SearchVideoItem
import cn.xfangfang.biliduo.data.model.seasonKey
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import cn.xfangfang.biliduo.util.cleanHtmlText
import cn.xfangfang.biliduo.util.formatDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

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

enum class SearchType(val value: String, val label: String) {
    VIDEO("video", "视频"),
    USER("bili_user", "用户"),
    ARTICLE("article", "专栏"),
    LIVE("live_room", "直播"),
    BANGUMI("media_bangumi", "番剧"),
    MOVIE("media_ft", "影视"),
}

private val SearchType.isPgc: Boolean
    get() = this == SearchType.BANGUMI || this == SearchType.MOVIE

data class SearchUiState(
    val query: String = "",
    val searchType: SearchType = SearchType.VIDEO,
    val results: List<SearchVideoItem> = emptyList(),
    val users: List<SearchUserItem> = emptyList(),
    val pgc: List<SearchPgcItem> = emptyList(),
    val articles: List<SearchArticleItem> = emptyList(),
    val liveRooms: List<SearchLiveItem> = emptyList(),
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

    /**
     * Cancelling a Retrofit call can surface as a Result.failure because the
     * repository wraps everything in runCatching. Without a generation token
     * a cancelled type search would write "HTTP 412" / cancellation errors
     * into the newly selected type and clear its loading state.
     */
    private var searchRequestId = 0

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
            searchRequestId++
            _uiState.value = _uiState.value.copy(
                query = "",
                results = emptyList(),
                users = emptyList(),
                pgc = emptyList(),
                articles = emptyList(),
                liveRooms = emptyList(),
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
        if (_uiState.value.searchType != SearchType.VIDEO) return
        if (sort.value == _uiState.value.sortBy.value) return
        _uiState.value = _uiState.value.copy(sortBy = sort)
        searchJob?.cancel()
        search()
    }

    fun setSearchType(type: SearchType) {
        val state = _uiState.value
        if (type == state.searchType) return
        val shouldSearch = state.hasSearched &&
            state.query.trim().isNotEmpty() &&
            state.query.trim() == state.submittedQuery
        searchJob?.cancel()
        searchRequestId++
        _uiState.value = state.copy(
            searchType = type,
            results = emptyList(),
            users = emptyList(),
            pgc = emptyList(),
            articles = emptyList(),
            liveRooms = emptyList(),
            isLoading = shouldSearch,
            isLoadingMore = false,
            error = null,
            moreError = null,
            hasSearched = shouldSearch,
            currentPage = 1,
            hasMore = false,
        )
        // L1/R1 can cross several types quickly. Debounce so only the type
        // the user settles on issues a request; firing one per key press is
        // what triggered HTTP 412 and made cycling useless.
        if (shouldSearch) search(debounceMs = TYPE_SWITCH_DEBOUNCE_MS)
    }

    fun search(debounceMs: Long = SEARCH_SUBMIT_DEBOUNCE_MS) {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) {
            Log.d(BilibiliApp.TAG, "search: skipped due to empty query")
            return
        }

        val order = _uiState.value.sortBy.value
        val type = _uiState.value.searchType
        Log.d(BilibiliApp.TAG, "search: query=$query sort=$order type=${type.value}")
        suggestJob?.cancel()
        _uiState.value = _uiState.value.copy(
            submittedQuery = query,
            suggestions = emptyList(),
        )
        searchJob?.cancel()
        val requestId = ++searchRequestId
        searchJob = viewModelScope.launch {
            delay(debounceMs)
            if (requestId != searchRequestId) return@launch
            if (query != _uiState.value.query.trim() ||
                order != _uiState.value.sortBy.value ||
                type != _uiState.value.searchType
            ) return@launch

            val newHistory = historyManager.add(query)
            _uiState.value = _uiState.value.copy(searchHistory = newHistory)

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                moreError = null,
                hasSearched = true,
                results = emptyList(),
                users = emptyList(),
                pgc = emptyList(),
                articles = emptyList(),
                liveRooms = emptyList(),
                currentPage = 1
            )
            if (type == SearchType.USER) {
                repository.searchUsers(query, page = 1)
                    .onSuccess { (users, totalPages) ->
                        if (requestId != searchRequestId) return@onSuccess
                        Log.d(BilibiliApp.TAG, "searchUsers success: ${users.size} users, pages=$totalPages")
                        _uiState.value = _uiState.value.copy(
                            users = users.filter { it.mid > 0 }.distinctBy { it.mid }
                                .map { it.cleanForDisplay() },
                            isLoading = false,
                            currentPage = 1,
                            hasMore = totalPages > 1
                        )
                    }
                    .onFailure { e ->
                        if (requestId != searchRequestId) return@onFailure
                        Log.e(BilibiliApp.TAG, "searchUsers error: ${e.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = searchErrorMessage(e)
                        )
                    }
            } else if (type.isPgc) {
                repository.searchPgc(query, type.value, page = 1)
                    .onSuccess { (items, totalPages) ->
                        if (requestId != searchRequestId) return@onSuccess
                        Log.d(BilibiliApp.TAG, "searchPgc success: ${items.size} items, pages=$totalPages")
                        _uiState.value = _uiState.value.copy(
                            pgc = items.filter { it.seasonId > 0 || it.mediaId > 0 }
                                .distinctBy { it.seasonKey }
                                .map {
                                    it.copy(
                                        title = cleanHtmlText(it.title.orEmpty()),
                                        staff = cleanHtmlText(it.staff.orEmpty()),
                                    )
                                },
                            isLoading = false,
                            currentPage = 1,
                            hasMore = totalPages > 1
                        )
                    }
                    .onFailure { e ->
                        if (requestId != searchRequestId) return@onFailure
                        Log.e(BilibiliApp.TAG, "searchPgc error: ${e.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = searchErrorMessage(e)
                        )
                    }
            } else if (type == SearchType.ARTICLE) {
                repository.searchArticles(query, page = 1)
                    .onSuccess { (items, totalPages) ->
                        if (requestId != searchRequestId) return@onSuccess
                        Log.d(BilibiliApp.TAG, "searchArticles success: ${items.size} items, pages=$totalPages")
                        _uiState.value = _uiState.value.copy(
                            articles = items.filter { it.id > 0 }.distinctBy { it.id }
                                .map {
                                    it.copy(
                                        title = cleanHtmlText(it.title.orEmpty()),
                                        desc = cleanHtmlText(it.desc.orEmpty()),
                                        author = cleanHtmlText(it.author.orEmpty()),
                                    )
                                },
                            isLoading = false,
                            currentPage = 1,
                            hasMore = totalPages > 1
                        )
                    }
                    .onFailure { e ->
                        if (requestId != searchRequestId) return@onFailure
                        Log.e(BilibiliApp.TAG, "searchArticles error: ${e.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = searchErrorMessage(e)
                        )
                    }
            } else if (type == SearchType.LIVE) {
                repository.searchLiveRooms(query, page = 1)
                    .onSuccess { (items, totalPages) ->
                        if (requestId != searchRequestId) return@onSuccess
                        Log.d(BilibiliApp.TAG, "searchLiveRooms success: ${items.size} items, pages=$totalPages")
                        _uiState.value = _uiState.value.copy(
                            liveRooms = items.filter { it.roomId > 0 }.distinctBy { it.roomId }
                                .map {
                                    it.copy(
                                        title = cleanHtmlText(it.title.orEmpty()),
                                        uname = cleanHtmlText(it.uname.orEmpty()),
                                    )
                                },
                            isLoading = false,
                            currentPage = 1,
                            hasMore = totalPages > 1
                        )
                    }
                    .onFailure { e ->
                        if (requestId != searchRequestId) return@onFailure
                        Log.e(BilibiliApp.TAG, "searchLiveRooms error: ${e.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = searchErrorMessage(e)
                        )
                    }
            } else {
                repository.search(query, page = 1, order = order)
                    .onSuccess { (results, totalPages) ->
                        if (requestId != searchRequestId) return@onSuccess
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
                        if (requestId != searchRequestId) return@onFailure
                        Log.e(BilibiliApp.TAG, "search error: ${e.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = searchErrorMessage(e)
                        )
                    }
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
        searchRequestId++
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return

        val nextPage = state.currentPage + 1
        Log.d(BilibiliApp.TAG, "loadMore: page=$nextPage query=${state.query} type=${state.searchType.value}")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            if (state.searchType == SearchType.USER) {
                repository.searchUsers(state.query, page = nextPage)
                    .onSuccess { (users, totalPages) ->
                        if (_uiState.value.searchType != state.searchType) return@onSuccess
                        val existingMids = _uiState.value.users.map { it.mid }.toSet()
                        val deduped = users.filter { it.mid > 0 && it.mid !in existingMids }
                            .map { it.cleanForDisplay() }
                        _uiState.value = _uiState.value.copy(
                            users = _uiState.value.users + deduped,
                            isLoadingMore = false,
                            moreError = null,
                            currentPage = nextPage,
                            hasMore = nextPage < totalPages
                        )
                    }
                    .onFailure { e ->
                        if (_uiState.value.searchType != state.searchType) return@onFailure
                        Log.e(BilibiliApp.TAG, "loadMore users error: ${e.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoadingMore = false,
                            moreError = e.message ?: "加载失败"
                        )
                    }
            } else if (state.searchType.isPgc) {
                repository.searchPgc(state.query, state.searchType.value, page = nextPage)
                    .onSuccess { (items, totalPages) ->
                        if (_uiState.value.searchType != state.searchType) return@onSuccess
                        val existing = _uiState.value.pgc.map { it.seasonKey }.toSet()
                        val deduped = items.filter { it.seasonKey > 0 && it.seasonKey !in existing }
                            .distinctBy { it.seasonKey }
                            .map {
                                it.copy(
                                    title = cleanHtmlText(it.title.orEmpty()),
                                    staff = cleanHtmlText(it.staff.orEmpty()),
                                )
                            }
                        _uiState.value = _uiState.value.copy(
                            pgc = _uiState.value.pgc + deduped,
                            isLoadingMore = false,
                            moreError = null,
                            currentPage = nextPage,
                            hasMore = nextPage < totalPages
                        )
                    }
                    .onFailure { e ->
                        if (_uiState.value.searchType != state.searchType) return@onFailure
                        Log.e(BilibiliApp.TAG, "loadMore pgc error: ${e.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoadingMore = false,
                            moreError = e.message ?: "加载失败"
                        )
                    }
            } else if (state.searchType == SearchType.ARTICLE) {
                repository.searchArticles(state.query, page = nextPage)
                    .onSuccess { (items, totalPages) ->
                        if (_uiState.value.searchType != state.searchType) return@onSuccess
                        val existing = _uiState.value.articles.map { it.id }.toSet()
                        val deduped = items.filter { it.id > 0 && it.id !in existing }
                            .distinctBy { it.id }
                            .map {
                                it.copy(
                                    title = cleanHtmlText(it.title.orEmpty()),
                                    desc = cleanHtmlText(it.desc.orEmpty()),
                                    author = cleanHtmlText(it.author.orEmpty()),
                                )
                            }
                        _uiState.value = _uiState.value.copy(
                            articles = _uiState.value.articles + deduped,
                            isLoadingMore = false,
                            moreError = null,
                            currentPage = nextPage,
                            hasMore = nextPage < totalPages
                        )
                    }
                    .onFailure { e ->
                        if (_uiState.value.searchType != state.searchType) return@onFailure
                        Log.e(BilibiliApp.TAG, "loadMore articles error: ${e.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoadingMore = false,
                            moreError = e.message ?: "加载失败"
                        )
                    }
            } else if (state.searchType == SearchType.LIVE) {
                repository.searchLiveRooms(state.query, page = nextPage)
                    .onSuccess { (items, totalPages) ->
                        if (_uiState.value.searchType != state.searchType) return@onSuccess
                        val existing = _uiState.value.liveRooms.map { it.roomId }.toSet()
                        val deduped = items.filter { it.roomId > 0 && it.roomId !in existing }
                            .distinctBy { it.roomId }
                            .map {
                                it.copy(
                                    title = cleanHtmlText(it.title.orEmpty()),
                                    uname = cleanHtmlText(it.uname.orEmpty()),
                                )
                            }
                        _uiState.value = _uiState.value.copy(
                            liveRooms = _uiState.value.liveRooms + deduped,
                            isLoadingMore = false,
                            moreError = null,
                            currentPage = nextPage,
                            hasMore = nextPage < totalPages
                        )
                    }
                    .onFailure { e ->
                        if (_uiState.value.searchType != state.searchType) return@onFailure
                        Log.e(BilibiliApp.TAG, "loadMore live error: ${e.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoadingMore = false,
                            moreError = e.message ?: "加载失败"
                        )
                    }
            } else {
                repository.search(state.query, page = nextPage, order = state.sortBy.value)
                    .onSuccess { (results, totalPages) ->
                        if (_uiState.value.searchType != state.searchType) return@onSuccess
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
                        if (_uiState.value.searchType != state.searchType) return@onFailure
                        Log.e(BilibiliApp.TAG, "loadMore error: ${e.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoadingMore = false,
                            moreError = e.message ?: "加载失败"
                        )
                    }
            }
        }
    }

    private fun searchErrorMessage(e: Throwable): String = when {
        e is HttpException && e.code() == 412 -> "搜索太频繁，请稍后重试"
        e is HttpException && e.code() == 429 -> "请求过多，请稍后重试"
        else -> e.message ?: "未知错误"
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
        searchRequestId++
        suggestJob?.cancel()
        _uiState.value = _uiState.value.copy(
            query = "",
            suggestions = emptyList(),
            submittedQuery = "",
            results = emptyList(),
            users = emptyList(),
            pgc = emptyList(),
            articles = emptyList(),
            liveRooms = emptyList(),
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

/** Small delay so a submitted query wins over a queued suggestion press. */
private const val SEARCH_SUBMIT_DEBOUNCE_MS = 80L

/**
 * L1/R1 type cycling: wait for the user to settle before requesting, otherwise
 * every key press fires search/type and B站 risk control answers HTTP 412.
 */
private const val TYPE_SWITCH_DEBOUNCE_MS = 350L
