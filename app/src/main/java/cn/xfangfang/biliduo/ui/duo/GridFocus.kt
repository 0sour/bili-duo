package cn.xfangfang.biliduo.ui.duo

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Column-preserving D-pad navigation for lazy grids.
 *
 * Compose's 2D focus search loses the column anchor when the next row is not
 * composed yet and falls back to the first item of that row. We compute the
 * target index ourselves, scroll it into composition, then request focus.
 */
class GridFocusNavigator(
    private val columns: Int,
    private val state: LazyGridState,
    private val scope: CoroutineScope,
    private val firstRequester: FocusRequester? = null,
) {
    private val requesters = mutableMapOf<Int, FocusRequester>()
    private var lastUpAtNanos = 0L

    /**
     * Grids usually wire their first card to a page-level requester (initial
     * focus/refresh), so requesting a fresh index-0 requester here would hit
     * an unattached node: Up from the second row then consumes the key but
     * leaves focus where it was. Reuse the page's node for index 0.
     */
    fun requester(index: Int): FocusRequester =
        if (index == 0 && firstRequester != null) {
            firstRequester
        } else {
            requesters.getOrPut(index) { FocusRequester() }
        }

    /** True for any card in the grid's first row. */
    fun isFirstRow(index: Int): Boolean = index < columns

    /**
     * True when this Up follows the previous Up within [UP_BURST_WINDOW_MS],
     * i.e. the user is still flicking through rows. First-row cards use it to
     * absorb the overshoot press instead of immediately jumping to the header
     * tabs; a deliberate press after a pause is never delayed.
     */
    fun isRapidUpBurst(): Boolean {
        val now = System.nanoTime()
        val burst = lastUpAtNanos != 0L &&
            now - lastUpAtNanos < UP_BURST_WINDOW_MS * 1_000_000L
        lastUpAtNanos = now
        return burst
    }

    /** Focus [index] after ensuring the lazy grid has composed it. */
    fun focus(index: Int, itemCount: Int) {
        if (index !in 0 until itemCount) return
        val requester = requester(index)
        // Lazy grids compose a prefetch range outside the viewport. Focus can
        // land on one of those items while the list stays put, which looks
        // like the focus ring disappeared. Only accept the focus request for
        // an item that is actually in the viewport; otherwise scroll first.
        val visible = state.layoutInfo.visibleItemsInfo.any { it.index == index }
        if (visible && runCatching { requester.requestFocus() }.isSuccess) return
        scope.launch {
            runCatching { state.scrollToItem(index) }
            delay(64L)
            runCatching { requester.requestFocus() }
        }
    }

    /** Returns true when a previous-row target exists. */
    fun moveUp(index: Int, itemCount: Int): Boolean {
        val target = index - columns
        if (target !in 0 until itemCount) return false
        focusWithRowScroll(target, index, -1f)
        return true
    }

    /** Returns true when a next-row target exists. */
    fun moveDown(index: Int, itemCount: Int): Boolean {
        val target = index + columns
        if (target >= itemCount) return false
        focusWithRowScroll(target, index, 1f)
        return true
    }

    /**
     * Move focus one row while keeping the ring on the viewport's top/bottom
     * row. If the target row is already visible, let Compose reveal it
     * minimally; otherwise scroll exactly one row height instead of
     * alignTop-scrolling the target (which looked like a full-page jump).
     */
    private fun focusWithRowScroll(target: Int, index: Int, direction: Float) {
        val requester = requester(target)
        val visible = state.layoutInfo.visibleItemsInfo.any { it.index == target }
        if (visible) {
            runCatching { requester.requestFocus() }
            return
        }
        val rowHeight = state.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == index }
            ?.size
            ?.height
            ?: 0
        scope.launch {
            if (rowHeight > 0) {
                runCatching {
                    state.animateScrollBy(direction * rowHeight.toFloat(), tween(140))
                }
            } else {
                runCatching { state.scrollToItem(target) }
            }
            // The target row is composed during the scroll. Try immediately,
            // with a couple of 16ms retries for the frame that composes it;
            // a fixed delay here made the ring pause after the page stopped.
            repeat(4) {
                if (runCatching { requester.requestFocus() }.isSuccess) return@launch
                delay(16L)
            }
        }
    }
}

@Composable
fun rememberGridFocusNavigator(
    columns: Int,
    state: LazyGridState,
    firstRequester: FocusRequester? = null,
): GridFocusNavigator {
    val scope = rememberCoroutineScope()
    return remember(columns, state, firstRequester) {
        GridFocusNavigator(columns, state, scope, firstRequester)
    }
}

/**
 * Intercepts Up/Down on a grid card so focus keeps its column and the lazy
 * grid scrolls one row at a time. Down is consumed even on the last row so
 * focus cannot fall through to the mode bar; Up on the first row is passed
 * through so it can reach the page header.
 */
fun Modifier.duoGridVertical(
    index: Int,
    itemCount: Int,
    navigator: GridFocusNavigator,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionUp -> {
            val rapid = navigator.isRapidUpBurst()
            // Every column's first row absorbs the overshoot press; a
            // deliberate Up after a pause still hands off to the header.
            if (rapid && navigator.isFirstRow(index)) {
                true
            } else {
                navigator.moveUp(index, itemCount)
            }
        }
        Key.DirectionDown -> {
            navigator.moveDown(index, itemCount)
            true
        }
        else -> false
    }
}

/**
 * Two Up presses closer than this are treated as one flick; the extra press
 * on a first-row card is absorbed instead of leaving the grid. A deliberate
 * press after a pause always goes straight to the header.
 */
private const val UP_BURST_WINDOW_MS = 180L
