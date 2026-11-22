package cn.xfangfang.biliduo.ui.duo

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
) {
    private val requesters = mutableMapOf<Int, FocusRequester>()

    fun requester(index: Int): FocusRequester =
        requesters.getOrPut(index) { FocusRequester() }

    /** Focus [index] after ensuring the lazy grid has composed it. */
    fun focus(index: Int, itemCount: Int) {
        if (index !in 0 until itemCount) return
        val requester = requester(index)
        if (runCatching { requester.requestFocus() }.isSuccess) return
        scope.launch {
            runCatching { state.scrollToItem(index) }
            delay(64L)
            runCatching { requester.requestFocus() }
        }
    }

    /** Returns true when a next-row target exists. */
    fun moveDown(index: Int, itemCount: Int): Boolean {
        val target = index + columns
        if (target >= itemCount) return false
        focus(target, itemCount)
        return true
    }
}

@Composable
fun rememberGridFocusNavigator(
    columns: Int,
    state: LazyGridState,
): GridFocusNavigator {
    val scope = rememberCoroutineScope()
    return remember(columns, state) {
        GridFocusNavigator(columns, state, scope)
    }
}

/**
 * Intercepts Down on a grid card. The key is consumed even on the last row so
 * focus cannot fall through to the mode bar or the left rail.
 */
fun Modifier.duoGridDown(
    index: Int,
    itemCount: Int,
    navigator: GridFocusNavigator,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
        navigator.moveDown(index, itemCount)
        true
    } else {
        false
    }
}
