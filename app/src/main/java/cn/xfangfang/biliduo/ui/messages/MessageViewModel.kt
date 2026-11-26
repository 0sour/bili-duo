package cn.xfangfang.biliduo.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.xfangfang.biliduo.data.model.MessageFeedKind
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MessageUiState(
    val tab: MessageFeedKind = MessageFeedKind.REPLY,
    val rows: List<MessageRowUi> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    /** Failure of the "load more" footer; the list itself stays usable. */
    val loadMoreError: String? = null,
    val isEnd: Boolean = false,
    val unread: Int = 0,
    val unreadReply: Int = 0,
    val unreadAt: Int = 0,
    val unreadLike: Int = 0,
)

class MessageViewModel(
    private val repository: BilibiliRepository = BilibiliRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessageUiState())
    val uiState: StateFlow<MessageUiState> = _uiState.asStateFlow()

    private var nextId = 0L
    private var nextTime = 0L
    private var loadSerial = 0
    private var loadJob: Job? = null
    private var unreadJob: Job? = null

    fun selectTab(tab: MessageFeedKind) {
        if (tab == _uiState.value.tab && _uiState.value.rows.isNotEmpty()) return
        load(tab = tab, reset = true)
    }

    fun refresh() {
        load(tab = _uiState.value.tab, reset = true)
    }

    fun load(tab: MessageFeedKind = _uiState.value.tab, reset: Boolean = true) {
        val serial = ++loadSerial
        loadJob?.cancel()
        if (reset) {
            nextId = 0L
            nextTime = 0L
        }
        _uiState.value = _uiState.value.copy(
            tab = tab,
            isLoading = reset,
            isLoadingMore = !reset,
            error = null,
            loadMoreError = null,
            isEnd = false,
            rows = if (reset) emptyList() else _uiState.value.rows,
        )
        loadJob = viewModelScope.launch {
            val result = repository.getMessagePage(tab, id = nextId, time = nextTime)
            if (serial != loadSerial) return@launch
            val page = result.getOrNull()
            if (page != null) {
                nextId = page.nextId
                nextTime = page.nextTime
                val rows = page.items.mapNotNull { it.toMessageRowUi(tab) }
                _uiState.value = _uiState.value.copy(
                    rows = if (reset) rows else _uiState.value.rows + rows,
                    isLoading = false,
                    isLoadingMore = false,
                    isEnd = page.isEnd,
                    error = null,
                    loadMoreError = null,
                )
                refreshUnread()
            } else {
                val message = result.exceptionOrNull()?.message ?: "加载失败"
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = if (reset) message else null,
                    loadMoreError = if (reset) null else message,
                    isEnd = false,
                )
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || state.isEnd) return
        load(tab = state.tab, reset = false)
    }

    /** Refreshes the rail badge; silently keeps the old value on failure. */
    fun refreshUnread() {
        if (unreadJob?.isActive == true) return
        unreadJob = viewModelScope.launch {
            repository.getMessageUnread()
                .onSuccess { counts ->
                    _uiState.value = _uiState.value.copy(
                        unread = counts.total,
                        unreadReply = counts.reply,
                        unreadAt = counts.at,
                        unreadLike = counts.like,
                    )
                }
        }
    }

    /** Video rows sometimes only carry an aid; resolve it before navigating. */
    fun resolveBvid(aid: Long, onResolved: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            repository.getVideoBvidByAid(aid)
                .onSuccess(onResolved)
                .onFailure { onError(it.message ?: "视频不存在") }
        }
    }
}
