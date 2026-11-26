package cn.xfangfang.biliduo.ui.pgc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.xfangfang.biliduo.data.model.PgcTimelineDay
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PgcTimelineUiState(
    val days: List<PgcTimelineDay> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class PgcTimelineViewModel(
    private val repository: BilibiliRepository = BilibiliRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(PgcTimelineUiState())
    val uiState: StateFlow<PgcTimelineUiState> = _uiState.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    fun load() {
        loadJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        loadJob = viewModelScope.launch {
            repository.getPgcTimeline()
                .onSuccess { days ->
                    _uiState.value = PgcTimelineUiState(days = days)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "加载失败",
                    )
                }
        }
    }
}
