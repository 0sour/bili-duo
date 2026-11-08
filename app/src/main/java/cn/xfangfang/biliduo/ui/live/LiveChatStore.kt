package cn.xfangfang.biliduo.ui.live

import cn.xfangfang.biliduo.data.model.LiveChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Messages visible on the top screen for the active live room. */
data class LiveChatSnapshot(
    val roomId: Long = 0L,
    val messages: List<LiveChatMessage> = emptyList(),
)

/**
 * Bridges the bottom-screen live chat (owned by [LiveConsoleScreen]) to the top
 * overlay. Only the live console writes; the top screen reads.
 */
object LiveChatStore {
    private val _snapshot = MutableStateFlow(LiveChatSnapshot())
    val snapshot: StateFlow<LiveChatSnapshot> = _snapshot.asStateFlow()

    fun publish(roomId: Long, messages: List<LiveChatMessage>) {
        _snapshot.value = LiveChatSnapshot(roomId, messages)
    }

    fun clear(roomId: Long) {
        if (_snapshot.value.roomId == roomId) {
            _snapshot.value = LiveChatSnapshot()
        }
    }
}
