package cn.xfangfang.biliduo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.xfangfang.biliduo.data.model.HomeTab
import cn.xfangfang.biliduo.data.model.HomeVideoItem
import cn.xfangfang.biliduo.data.model.LiveAreaGroup
import cn.xfangfang.biliduo.data.model.LiveFollowedAnchor
import cn.xfangfang.biliduo.data.model.LiveRoomItem
import cn.xfangfang.biliduo.data.model.PgcSeasonItem
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import cn.xfangfang.biliduo.ui.duo.cycleIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val tab: HomeTab = HomeTab.RECOMMEND,
    val items: List<HomeVideoItem> = emptyList(),
    val pgcItems: List<PgcSeasonItem> = emptyList(),
    val liveItems: List<LiveRoomItem> = emptyList(),
    val liveFollowed: List<LiveFollowedAnchor> = emptyList(),
    val liveAreas: List<LiveAreaGroup> = emptyList(),
    val liveAreaId: Int = 0,
    val liveSubAreaId: Int = 0,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val page: Int = 1,
    val error: String? = null,
)

class HomeViewModel(
    private val repository: BilibiliRepository = BilibiliRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var requestJob: Job? = null
    private var areasJob: Job? = null
    private var followedJob: Job? = null
    private var requestId = 0

    init {
        refresh()
    }

    fun selectTab(tab: HomeTab) {
        if (_state.value.tab == tab) {
            if (tab == HomeTab.RECOMMEND || tab.isPgc || tab.isLive) refresh()
            return
        }
        val previous = _state.value
        _state.value = HomeUiState(
            tab = tab,
            isLoading = true,
            // Area chips are global to the live tab; keep them while switching
            // tabs so returning to 直播 does not flash an empty filter row.
            liveFollowed = previous.liveFollowed,
            liveAreas = previous.liveAreas,
            liveAreaId = previous.liveAreaId,
            liveSubAreaId = previous.liveSubAreaId,
        )
        if (tab.isLive) {
            ensureLiveAreas()
            ensureLiveFollowed()
        }
        refresh()
    }

    /** L1/R1 on the home page: wrap through 直播/推荐/热门/排行/番剧/影视. */
    fun cycleTab(delta: Int) {
        val tabs = HomeTab.entries
        val index = cycleIndex(tabs.indexOf(_state.value.tab), delta, tabs.size)
        selectTab(tabs[index])
    }

    /** L2/R2 on the live tab: wrap through 推荐 and the parent area chips. */
    fun cycleLiveArea(delta: Int) {
        val snapshot = _state.value
        if (snapshot.tab != HomeTab.LIVE) return
        val ids = listOf(0) + snapshot.liveAreas.map { it.id }
        val index = cycleIndex(ids.indexOf(snapshot.liveAreaId), delta, ids.size)
        selectLiveArea(ids[index])
    }

    fun selectLiveArea(areaId: Int) {
        val snapshot = _state.value
        if (snapshot.tab != HomeTab.LIVE || snapshot.liveAreaId == areaId) return
        _state.value = snapshot.copy(
            liveAreaId = areaId,
            liveSubAreaId = 0,
            liveItems = emptyList(),
            isLoading = true,
            isLoadingMore = false,
            hasMore = false,
            page = 1,
            error = null,
        )
        refresh()
    }

    fun selectLiveSubArea(areaId: Int) {
        val snapshot = _state.value
        if (snapshot.tab != HomeTab.LIVE || snapshot.liveSubAreaId == areaId) return
        _state.value = snapshot.copy(
            liveSubAreaId = areaId,
            liveItems = emptyList(),
            isLoading = true,
            isLoadingMore = false,
            hasMore = false,
            page = 1,
            error = null,
        )
        refresh()
    }

    private fun ensureLiveAreas() {
        if (_state.value.liveAreas.isNotEmpty() || areasJob?.isActive == true) return
        areasJob = viewModelScope.launch {
            repository.getLiveAreas()
                .onSuccess { areas ->
                    _state.value = _state.value.copy(liveAreas = areas)
                }
        }
    }

    private fun ensureLiveFollowed() {
        if (_state.value.liveFollowed.isNotEmpty() || followedJob?.isActive == true) return
        followedJob = viewModelScope.launch {
            repository.getFollowedLiveAnchors()
                .onSuccess { anchors ->
                    _state.value = _state.value.copy(liveFollowed = anchors)
                }
        }
    }

    fun refresh() {
        requestJob?.cancel()
        val id = ++requestId
        val tab = _state.value.tab
        _state.value = _state.value.copy(
            isLoading = true,
            isLoadingMore = false,
            error = null,
        )
        requestJob = viewModelScope.launch {
            if (tab.isLive) {
                val areaId = _state.value.liveAreaId
                val subAreaId = _state.value.liveSubAreaId
                repository.getLiveRooms(
                    parentAreaId = areaId,
                    areaId = subAreaId,
                    page = 1,
                    pageSize = LIVE_PAGE_SIZE,
                )
                    .onSuccess { rooms ->
                        if (id != requestId) return@onSuccess
                        _state.value = _state.value.copy(
                            liveItems = rooms.distinctBy { it.roomId },
                            items = emptyList(),
                            pgcItems = emptyList(),
                            isLoading = false,
                            hasMore = rooms.size >= LIVE_PAGE_SIZE,
                            page = 1,
                            error = null,
                        )
                    }
                    .onFailure { error ->
                        if (id != requestId) return@onFailure
                        _state.value = _state.value.copy(
                            liveItems = emptyList(),
                            isLoading = false,
                            hasMore = false,
                            error = error.message ?: "直播加载失败",
                        )
                    }
                return@launch
            }
            if (tab.isPgc) {
                val seasonType = if (tab == HomeTab.ANIME) 1 else 2
                repository.getPgcRank(seasonType)
                    .onSuccess { list ->
                        if (id != requestId) return@onSuccess
                        _state.value = _state.value.copy(
                            pgcItems = list,
                            items = emptyList(),
                            isLoading = false,
                            hasMore = false,
                            page = 1,
                            error = null,
                        )
                    }
                    .onFailure { error ->
                        if (id != requestId) return@onFailure
                        _state.value = _state.value.copy(
                            pgcItems = emptyList(),
                            isLoading = false,
                            hasMore = false,
                            error = error.message ?: "${tab.label}加载失败",
                        )
                    }
                return@launch
            }
            repository.getHomeFeed(tab, page = 1)
                .onSuccess { (items, hasMore) ->
                    if (id != requestId) return@onSuccess
                    _state.value = _state.value.copy(
                        items = items.distinctBy { it.bvid },
                        isLoading = false,
                        hasMore = hasMore,
                        page = 1,
                        error = null,
                    )
                }
                .onFailure { error ->
                    if (id != requestId) return@onFailure
                    _state.value = _state.value.copy(
                        items = emptyList(),
                        isLoading = false,
                        hasMore = false,
                        error = error.message ?: "首页加载失败",
                    )
                }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.isLoading || snapshot.isLoadingMore || !snapshot.hasMore) return
        if (snapshot.tab != HomeTab.POPULAR && !snapshot.tab.isLive) return
        requestJob?.cancel()
        val id = ++requestId
        val nextPage = snapshot.page + 1
        _state.value = snapshot.copy(isLoadingMore = true, error = null)
        requestJob = viewModelScope.launch {
            if (snapshot.tab.isLive) {
                repository.getLiveRooms(
                    parentAreaId = snapshot.liveAreaId,
                    areaId = snapshot.liveSubAreaId,
                    page = nextPage,
                    pageSize = LIVE_PAGE_SIZE,
                )
                    .onSuccess { rooms ->
                        if (id != requestId) return@onSuccess
                        val merged = (_state.value.liveItems + rooms).distinctBy { it.roomId }
                        _state.value = _state.value.copy(
                            liveItems = merged,
                            isLoadingMore = false,
                            hasMore = rooms.size >= LIVE_PAGE_SIZE,
                            page = nextPage,
                        )
                    }
                    .onFailure { error ->
                        if (id != requestId) return@onFailure
                        _state.value = _state.value.copy(
                            isLoadingMore = false,
                            error = error.message ?: "加载更多失败",
                        )
                    }
                return@launch
            }
            repository.getHomeFeed(snapshot.tab, page = nextPage)
                .onSuccess { (items, hasMore) ->
                    if (id != requestId) return@onSuccess
                    val merged = (_state.value.items + items).distinctBy { it.bvid }
                    _state.value = _state.value.copy(
                        items = merged,
                        isLoadingMore = false,
                        hasMore = hasMore,
                        page = nextPage,
                    )
                }
                .onFailure { error ->
                    if (id != requestId) return@onFailure
                    _state.value = _state.value.copy(
                        isLoadingMore = false,
                        error = error.message ?: "加载更多失败",
                    )
                }
        }
    }

    private companion object {
        const val LIVE_PAGE_SIZE = 24
    }
}
