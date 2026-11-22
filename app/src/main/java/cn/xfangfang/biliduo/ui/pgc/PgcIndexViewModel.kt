package cn.xfangfang.biliduo.ui.pgc

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.xfangfang.biliduo.BilibiliApp
import cn.xfangfang.biliduo.data.model.PgcIndexFilter
import cn.xfangfang.biliduo.data.model.PgcIndexItem
import cn.xfangfang.biliduo.data.model.PgcIndexOrder
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Index kinds exposed by the PGC index endpoints. `type` + `index_type` are
 * the two API parameters; the labels mirror Bilibili's web index tabs.
 */
enum class PgcIndexType(
    val label: String,
    val type: Int,
    val indexType: Int,
) {
    ANIME("追番", type = 1, indexType = 1),
    MOVIE("电影", type = 2, indexType = 2),
    TV("电视剧", type = 2, indexType = 5),
    DOCUMENTARY("纪录片", type = 2, indexType = 3),
    VARIETY("综艺", type = 2, indexType = 7),
    FILM_TV("影视综合", type = 2, indexType = 102),
    ;

    companion object {
        fun fromOrdinal(value: Int): PgcIndexType =
            entries.getOrElse(value) { ANIME }
    }
}

data class PgcIndexUiState(
    val selectedType: PgcIndexType = PgcIndexType.ANIME,
    val filters: List<PgcIndexFilter> = emptyList(),
    val orders: List<PgcIndexOrder> = emptyList(),
    val selectedFilters: Map<String, String> = emptyMap(),
    val selectedOrder: String = "0",
    val items: List<PgcIndexItem> = emptyList(),
    val page: Int = 0,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
)

class PgcIndexViewModel(
    private val repository: BilibiliRepository = BilibiliRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(PgcIndexUiState())
    val uiState: StateFlow<PgcIndexUiState> = _uiState.asStateFlow()

    private var requestId = 0
    private var initialized = false

    fun start(initialType: PgcIndexType) {
        if (initialized) return
        initialized = true
        loadType(initialType)
    }

    fun selectType(type: PgcIndexType) {
        if (type == _uiState.value.selectedType && _uiState.value.items.isNotEmpty()) return
        loadType(type)
    }

    fun selectFilter(field: String, keyword: String) {
        val state = _uiState.value
        if (state.selectedFilters[field] == keyword) return
        _uiState.value = state.copy(
            selectedFilters = state.selectedFilters + (field to keyword),
            isLoading = true,
            isLoadingMore = false,
            error = null,
        )
        reloadFirstPage()
    }

    fun selectOrder(field: String) {
        val state = _uiState.value
        if (state.selectedOrder == field) return
        _uiState.value = state.copy(
            selectedOrder = field,
            isLoading = true,
            isLoadingMore = false,
            error = null,
        )
        reloadFirstPage()
    }

    fun refresh() {
        if (_uiState.value.filters.isEmpty()) {
            loadType(_uiState.value.selectedType)
        } else {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            reloadFirstPage()
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val nextPage = state.page + 1
        val id = requestId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            val result = repository.getPgcIndexResult(
                type = state.selectedType.type,
                indexType = state.selectedType.indexType,
                page = nextPage,
                filters = state.selectedFilters,
                order = state.selectedOrder,
            )
            if (id != requestId) return@launch
            result.onSuccess { data ->
                val known = _uiState.value.items.map { it.seasonId }.toSet()
                _uiState.value = _uiState.value.copy(
                    items = _uiState.value.items + data.list.orEmpty()
                        .filter { it.seasonId > 0 && it.seasonId !in known },
                    page = nextPage,
                    total = data.total,
                    hasMore = data.hasNext == 1,
                    isLoadingMore = false,
                    error = null,
                )
            }.onFailure { e ->
                Log.e(BilibiliApp.TAG, "PgcIndex: loadMore failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = e.message ?: "加载更多失败",
                )
            }
        }
    }

    private fun loadType(type: PgcIndexType) {
        val id = ++requestId
        _uiState.value = PgcIndexUiState(
            selectedType = type,
            isLoading = true,
        )
        viewModelScope.launch {
            val condition = repository.getPgcIndexCondition(
                type = type.type,
                indexType = type.indexType,
            )
            if (id != requestId) return@launch
            condition.onSuccess { data ->
                val filters = data.filter.orEmpty()
                val defaults = filters.mapNotNull { filter ->
                    val keyword = filter.values?.firstOrNull()?.keyword ?: return@mapNotNull null
                    filter.field to keyword
                }.toMap()
                val orders = data.order.orEmpty()
                _uiState.value = _uiState.value.copy(
                    filters = filters,
                    orders = orders,
                    selectedFilters = defaults,
                    selectedOrder = orders.firstOrNull()?.field ?: "0",
                )
                fetchFirstPage(id)
            }.onFailure { e ->
                Log.e(BilibiliApp.TAG, "PgcIndex: condition failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "筛选条件加载失败",
                )
            }
        }
    }

    private fun reloadFirstPage() {
        val id = ++requestId
        viewModelScope.launch { fetchFirstPage(id) }
    }

    private suspend fun fetchFirstPage(id: Int) {
        val state = _uiState.value
        val result = repository.getPgcIndexResult(
            type = state.selectedType.type,
            indexType = state.selectedType.indexType,
            page = 1,
            filters = state.selectedFilters,
            order = state.selectedOrder,
        )
        if (id != requestId) return
        result.onSuccess { data ->
            _uiState.value = _uiState.value.copy(
                items = data.list.orEmpty().filter { it.seasonId > 0 },
                page = 1,
                total = data.total,
                hasMore = data.hasNext == 1,
                isLoading = false,
                isLoadingMore = false,
                error = null,
            )
        }.onFailure { e ->
            Log.e(BilibiliApp.TAG, "PgcIndex: first page failed", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isLoadingMore = false,
                error = e.message ?: "加载失败",
            )
        }
    }
}
