package cn.xfangfang.biliduo.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import cn.xfangfang.biliduo.data.local.GamepadStyleStore
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.ui.theme.DuoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DuoChoiceOption(
    val label: String,
    val selected: Boolean = false,
    val vip: Boolean = false,
)

data class DuoChoiceRequest(
    val title: String,
    val options: List<DuoChoiceOption>,
    val returnFocus: FocusRequester? = null,
    val onSelect: (Int) -> Unit,
)

/**
 * In-window choice popup state. Do not use Compose Dialog/AlertDialog here:
 * the bottom Display 2 is a Presentation and system dialog windows crash it.
 */
class DuoChoiceController {
    var request by mutableStateOf<DuoChoiceRequest?>(null)
        private set

    fun show(request: DuoChoiceRequest) {
        this.request = request
    }

    fun dismiss(): DuoChoiceRequest? {
        val current = request
        request = null
        return current
    }
}

val LocalDuoChoiceController = staticCompositionLocalOf<DuoChoiceController?> { null }

@Composable
fun rememberDuoChoiceController(): DuoChoiceController = remember { DuoChoiceController() }

/** Dismiss from a B/back interceptor and return focus to the row that opened it. */
fun dismissDuoChoice(controller: DuoChoiceController, scope: CoroutineScope) {
    val request = controller.dismiss() ?: return
    restoreChoiceFocus(request.returnFocus, scope)
}

/**
 * Selecting an option usually changes a setting, which recomposes (and may
 * re-attach) the row that opened the popup. A single 80ms request can land
 * before that row is focusable again, so retry once after the update settles.
 */
private fun restoreChoiceFocus(target: FocusRequester?, scope: CoroutineScope) {
    if (target == null) return
    scope.launch {
        for (wait in longArrayOf(80L, 260L)) {
            delay(wait)
            runCatching { target.requestFocus() }
        }
    }
}

/**
 * Renders the currently requested choice list above the screen. The caller
 * owns B handling and must call [dismissDuoChoice] from its back interceptor.
 */
@Composable
fun DuoChoiceHost(controller: DuoChoiceController) {
    // Keep the scope alive across dismissal: the focus-restore coroutine is
    // launched from the selection callback, which also removes the popup.
    // Creating the scope after the early return below cancelled that coroutine.
    val scope = rememberCoroutineScope()
    val request = controller.request ?: return
    DuoChoiceOverlay(
        request = request,
        onSelect = { index ->
            val current = controller.dismiss() ?: return@DuoChoiceOverlay
            current.onSelect(index)
            restoreChoiceFocus(current.returnFocus, scope)
        },
        onDismiss = {
            val current = controller.dismiss() ?: return@DuoChoiceOverlay
            restoreChoiceFocus(current.returnFocus, scope)
        },
    )
}

@Composable
fun DuoChoiceOverlay(
    request: DuoChoiceRequest,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = request.options
    val selectedIndex = options.indexOfFirst { it.selected }.coerceAtLeast(0)
    var focusIndex by remember(request) {
        mutableIntStateOf(selectedIndex)
    }
    val focusers = remember(request) { List(options.size) { FocusRequester() } }
    val colors = DuoTheme.colors
    val gamepadStyle by GamepadStyleStore.style.collectAsState()

    LaunchedEffect(request) {
        if (options.isEmpty()) return@LaunchedEffect
        delay(80L)
        runCatching { focusers[focusIndex].requestFocus() }
    }

    fun move(delta: Int) {
        if (options.isEmpty()) return
        focusIndex = (focusIndex + delta).coerceIn(0, options.lastIndex)
        runCatching { focusers[focusIndex].requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        move(-1)
                        true
                    }
                    Key.DirectionDown -> {
                        move(1)
                        true
                    }
                    // Single-column popup: keep horizontal/shoulder/action keys
                    // from reaching the page behind the scrim.
                    Key.DirectionLeft, Key.DirectionRight,
                    Key.ButtonL1, Key.ButtonR1, Key.ButtonL2, Key.ButtonR2,
                    Key.X, Key.ButtonX, Key.Y, Key.ButtonY -> true
                    else -> false
                }
            }
            .duoClickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.82f)
                .heightIn(max = 420.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface1)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = request.title,
                    color = colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${gamepadStyle.backLabel} 关闭",
                    color = colors.tertiary,
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (options.isEmpty()) {
                Text(
                    text = "暂无可选项",
                    color = colors.secondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                )
            } else {
                // Short option lists (<= 11 rows) fit without scrolling. A plain
                // Column avoids focus bring-into-view scrolling the selected row
                // to an arbitrary offset and clipping a neighbour. Rows keep a
                // 6dp gap so the focus ring never overlaps the adjacent option.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    options.forEachIndexed { index, option ->
                        var focused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusers[index])
                                .onFocusChanged { focused = it.isFocused }
                                .duoFocusRing(RoundedCornerShape(6.dp), focused = focused)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (option.selected) {
                                        colors.pink.copy(alpha = 0.14f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .duoClickable { onSelect(index) }
                                .padding(horizontal = 10.dp, vertical = 1.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option.label,
                                color = if (option.selected) colors.pinkInk else colors.text,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (option.vip) {
                                DuoVipBadge()
                            }
                            if (option.selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = colors.pinkInk,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
