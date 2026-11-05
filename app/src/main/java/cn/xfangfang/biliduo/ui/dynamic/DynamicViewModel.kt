package cn.xfangfang.biliduo.ui.dynamic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.xfangfang.biliduo.data.api.BilibiliApi
import cn.xfangfang.biliduo.data.model.DynamicAuthorUi
import cn.xfangfang.biliduo.data.model.DynamicCardUi
import cn.xfangfang.biliduo.data.model.DynamicFilter
import cn.xfangfang.biliduo.data.model.toCardUi
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import cn.xfangfang.biliduo.ui.duo.cycleIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DynamicUiState(
    val items: List<DynamicCardUi> = emptyList(),
    val authors: List<DynamicAuthorUi> = emptyList(),
    val authorMid: Long? = null,
    val authorName: String = "",
    val filter: DynamicFilter = DynamicFilter.ALL,
    val offset: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
    val actionMessage: String? = null,
)

class DynamicViewModel(
    private val repository: BilibiliRepository = BilibiliRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(DynamicUiState())
    val state: StateFlow<DynamicUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var requestId = 0
    private val likeJobs = mutableMapOf<String, Job>()
    private var messageJob: Job? = null

    /** Optimistically toggles one card's like; rolls back if the API fails. */
    fun toggleLike(card: DynamicCardUi) {
        val current = _state.value.items.firstOrNull { it.id == card.id } ?: return
        if (likeJobs[current.id]?.isActive == true) return
        if (BilibiliApi.loginCookies.isBlank()) {
            showActionMessage("请先登录")
            return
        }
        val target = !current.liked
        val optimisticCount = (current.likeCount + if (target) 1 else -1).coerceAtLeast(0)
        updateCard(current.id) { it.copy(liked = target, likeCount = optimisticCount) }
        val job = viewModelScope.launch {
            repository.likeDynamic(current.id, target)
                .onSuccess {
                    showActionMessage(if (target) "已点赞" else "已取消点赞")
                }
                .onFailure { failure ->
                    updateCard(current.id) {
                        it.copy(liked = current.liked, likeCount = current.likeCount)
                    }
                    showActionMessage(failure.message ?: "点赞失败")
                }
        }
        likeJobs[current.id] = job
        job.invokeOnCompletion { likeJobs.remove(current.id) }
    }

    private fun updateCard(id: String, transform: (DynamicCardUi) -> DynamicCardUi) {
        _state.value = _state.value.copy(
            items = _state.value.items.map { if (it.id == id) transform(it) else it }
        )
    }

    private fun showActionMessage(message: String) {
        messageJob?.cancel()
        _state.value = _state.value.copy(actionMessage = message)
        messageJob = viewModelScope.launch {
            delay(2_200L)
            _state.value = _state.value.copy(actionMessage = null)
        }
    }

    fun selectFilter(filter: DynamicFilter) {
        val snapshot = _state.value
        if (snapshot.authorMid == null && snapshot.filter == filter) return
        loadJob?.cancel()
        _state.value = DynamicUiState(
            authors = snapshot.authors,
            filter = filter,
            isLoading = true,
        )
        refresh()
    }

    /** L1/R1 on the dynamic page: wrap through 全部/视频/图文. */
    fun cycleFilter(delta: Int) {
        val filters = DynamicFilter.entries
        val index = cycleIndex(filters.indexOf(_state.value.filter), delta, filters.size)
        selectFilter(filters[index])
    }

    /** L2/R2 on the dynamic page: wrap through 全部 UP + the author chips. */
    fun cycleAuthor(delta: Int) {
        val snapshot = _state.value
        val authors = snapshot.authors
        val mids = listOf<Long?>(null) + authors.map { it.mid }
        val index = cycleIndex(mids.indexOf(snapshot.authorMid), delta, mids.size)
        val mid = mids[index] ?: return clearAuthor()
        authors.firstOrNull { it.mid == mid }?.let(::selectAuthor)
    }

    fun selectAuthor(author: DynamicAuthorUi) {
        if (author.mid <= 0L || _state.value.authorMid == author.mid) return
        loadJob?.cancel()
        _state.value = DynamicUiState(
            authors = _state.value.authors,
            authorMid = author.mid,
            authorName = author.name,
            filter = DynamicFilter.ALL,
            isLoading = true,
        )
        refresh()
    }

    fun clearAuthor() {
        if (_state.value.authorMid == null) return
        loadJob?.cancel()
        _state.value = DynamicUiState(
            authors = _state.value.authors,
            filter = DynamicFilter.ALL,
            isLoading = true,
        )
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        val id = ++requestId
        val snapshot = _state.value
        val authorMid = snapshot.authorMid
        val filter = snapshot.filter
        _state.value = snapshot.copy(
            isLoading = true,
            isLoadingMore = false,
            error = null,
        )
        loadJob = viewModelScope.launch {
            val result = if (authorMid != null) {
                repository.getDynamicSpaceFeed(hostMid = authorMid, offset = "")
            } else {
                repository.getDynamicFeed(type = filter.apiType, offset = "")
            }
            result
                .onSuccess { feed ->
                    if (id != requestId) return@onSuccess
                    val cards = feed.items.orEmpty().mapNotNull { it.toCardUi() }
                    val nextOffset = feed.offset.orEmpty()
                    _state.value = _state.value.copy(
                        items = cards,
                        authors = if (authorMid == null) {
                            mergeAuthors(_state.value.authors, cards)
                        } else {
                            _state.value.authors
                        },
                        offset = nextOffset,
                        hasMore = feed.hasMore && nextOffset.isNotBlank() && cards.isNotEmpty(),
                        isLoading = false,
                        isLoadingMore = false,
                        error = null,
                    )
                }
                .onFailure { failure ->
                    if (id != requestId) return@onFailure
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = false,
                        error = failure.message ?: "动态加载失败",
                    )
                }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.isLoading || snapshot.isLoadingMore || !snapshot.hasMore) return
        val id = ++requestId
        val authorMid = snapshot.authorMid
        _state.value = snapshot.copy(isLoadingMore = true, error = null)
        loadJob = viewModelScope.launch {
            val result = if (authorMid != null) {
                repository.getDynamicSpaceFeed(hostMid = authorMid, offset = snapshot.offset)
            } else {
                repository.getDynamicFeed(
                    type = snapshot.filter.apiType,
                    offset = snapshot.offset,
                )
            }
            result
                .onSuccess { feed ->
                    if (id != requestId) return@onSuccess
                    val more = feed.items.orEmpty().mapNotNull { it.toCardUi() }
                    val nextOffset = feed.offset.orEmpty()
                    _state.value = _state.value.copy(
                        items = (_state.value.items + more).distinctBy { it.id },
                        authors = if (authorMid == null) {
                            mergeAuthors(_state.value.authors, more)
                        } else {
                            _state.value.authors
                        },
                        offset = nextOffset.ifBlank { snapshot.offset },
                        hasMore = feed.hasMore && more.isNotEmpty() &&
                            nextOffset.isNotBlank() && nextOffset != snapshot.offset,
                        isLoadingMore = false,
                        error = null,
                    )
                }
                .onFailure { failure ->
                    if (id != requestId) return@onFailure
                    _state.value = _state.value.copy(
                        isLoadingMore = false,
                        error = failure.message ?: "加载更多失败",
                    )
                }
        }
    }

    fun retry() {
        if (_state.value.items.isEmpty()) refresh() else loadMore()
    }

    private fun mergeAuthors(
        current: List<DynamicAuthorUi>,
        cards: List<DynamicCardUi>,
    ): List<DynamicAuthorUi> {
        val incoming = cards
            .filter { it.authorMid > 0L && it.authorName.isNotBlank() }
            .map { DynamicAuthorUi(it.authorMid, it.authorName, it.authorFace) }
        return (current + incoming).distinctBy { it.mid }.take(24)
    }
}
