package cn.xfangfang.biliduo.ui.duo



import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import cn.xfangfang.biliduo.duo.DuoImeState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xfangfang.biliduo.duo.DuoSessionViewModel

import androidx.compose.runtime.Stable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import kotlinx.coroutines.delay

private val BarBg = Color(0xFF141418)
private val BarText = Color(0xFFF5F5F7)
private val Accent = Color(0xFFFB7299)
private val Muted = Color(0xFF73737F)

/**
 * bili-duo focus indicator: a thin white ring outside the content with a dark
 * separator edge, leaving a 4dp gap so the ring never covers text or artwork.
 *
 * The ring is drawn outside the measured bounds, so this modifier must be
 * placed *before* any `clip(...)` in the chain; otherwise the clip layer hides
 * the ring. See docs/ui/INPUT_AND_FOCUS.md.
 *
 * [focused] lets a wrapper own the focus target while this node only draws the
 * ring. Scrollable lists use that to keep transparent clearance inside the
 * focus bounds, so focus-driven scrolling cannot clip the ring at an edge.
 */
fun Modifier.duoFocusRing(
    shape: Shape = RoundedCornerShape(8.dp),
    enabled: Boolean = true,
    focused: Boolean? = null,
): Modifier = composed {
    var localFocused by remember { mutableStateOf(false) }
    val isFocused = focused ?: localFocused
    val progress by animateFloatAsState(
        targetValue = if (enabled && isFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "duoFocusRing",
    )
    this
        .then(
            if (focused == null) {
                Modifier.onFocusChanged { localFocused = it.isFocused }
            } else {
                Modifier
            },
        )
        .drawWithContent {
            drawContent()
            if (progress <= 0.01f) return@drawWithContent
            val gap = 4.dp.toPx()
            val ringWidth = 1.5.dp.toPx()
            val edgeWidth = 3.5.dp.toPx()

            fun outlineAt(expand: Float) = shape.createOutline(
                Size(size.width + expand * 2f, size.height + expand * 2f),
                layoutDirection,
                this,
            )

            // A thin dark edge keeps the white ring readable on bright covers,
            // without the pink halo that read like a background tint.
            translate(-gap, -gap) {
                drawFocusOutline(
                    outline = outlineAt(gap),
                    color = Color.Black.copy(alpha = 0.55f * progress),
                    strokeWidth = edgeWidth,
                )
                drawFocusOutline(
                    outline = outlineAt(gap),
                    color = Color.White.copy(alpha = 0.96f * progress),
                    strokeWidth = ringWidth,
                )
            }
        }
}

private fun DrawScope.drawFocusOutline(
    outline: Outline,
    color: Color,
    strokeWidth: Float,
) {
    when (outline) {
        is Outline.Rectangle -> drawRect(
            color = color,
            topLeft = outline.rect.topLeft,
            size = outline.rect.size,
            style = Stroke(width = strokeWidth),
        )
        is Outline.Rounded -> drawPath(
            path = Path().apply { addRoundRect(outline.roundRect) },
            color = color,
            style = Stroke(width = strokeWidth),
        )
        is Outline.Generic -> drawPath(
            path = outline.path,
            color = color,
            style = Stroke(width = strokeWidth),
        )
    }
}

/**
 * A clickable that is also a D-pad focus target and activates on center/Enter.
 * Compose's plain clickable does not expose a focus target in this setup, so
 * TV-style controls need an explicit focusable + key handler.
 */
fun Modifier.duoClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    this
        .onKeyEvent { event ->
            if (enabled && event.type == KeyEventType.KeyDown && isActivationKey(event.key)) {
                onClick()
                true
            } else {
                false
            }
        }
        .focusable(enabled)
        .clickable(enabled = enabled, onClick = onClick)
}

@Stable
class DuoTextFieldState(initiallyEditing: Boolean = false) {
    var editing by mutableStateOf(initiallyEditing)
}

@Composable
fun rememberDuoTextFieldState(initiallyEditing: Boolean = false): DuoTextFieldState =
    remember { DuoTextFieldState(initiallyEditing) }

/**
 * Shows the IME after a gate field has focus. The first show right after a cold
 * start can be rejected while the Presentation updates its IME visibility, so
 * retry twice. B increments [DuoImeState.generation] and cancels the retries.
 */
internal suspend fun showDuoIme(
    keyboard: SoftwareKeyboardController?,
    generation: Int,
    initialDelayMs: Long = 60L,
) {
    delay(initialDelayMs)
    if (!DuoImeState.shown || generation != DuoImeState.generation) return
    keyboard?.show()
    for (wait in longArrayOf(300L, 600L)) {
        delay(wait)
        if (!DuoImeState.shown || generation != DuoImeState.generation) return
        keyboard?.show()
    }
}

/**
 * Wraps a text field with a focus-only overlay. The field is disabled while
 * the overlay owns focus; A/Enter or a tap swaps in the real editable field,
 * which starts a fresh input session. This avoids both the focus-time IME
 * flash and the "show() does nothing after hide()" session problem.
 *
 * [content] receives the current editing state, whether the gate currently
 * owns focus, and the modifier to apply to the real text field.
 */
@Composable
fun DuoTextFieldGate(
    state: DuoTextFieldState,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    downFocus: FocusRequester? = null,
    onGateFocusChanged: (Boolean) -> Unit = {},
    content: @Composable (editing: Boolean, gateFocused: Boolean, fieldModifier: Modifier) -> Unit,
) {
    var gateFocused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.editing) {
        if (state.editing && enabled) {
            DuoImeState.onShown()
            val generation = DuoImeState.generation
            delay(16L)
            runCatching { focusRequester.requestFocus() }
            showDuoIme(keyboard, generation, initialDelayMs = 44L)
        }
    }
    var fieldWasFocused by remember { mutableStateOf(false) }
    val fieldModifier = Modifier
        .then(if (state.editing) Modifier.focusRequester(focusRequester) else Modifier)
        .then(
            if (state.editing) {
                Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) DuoImeState.onShown()
                        }
                    }
                }
            } else {
                Modifier
            }
        )
        .onFocusChanged {
            if (it.isFocused) {
                fieldWasFocused = true
            } else if (fieldWasFocused) {
                fieldWasFocused = false
                state.editing = false
            }
        }
        .onPreviewKeyEvent { event ->
            if (state.editing && event.type == KeyEventType.KeyDown &&
                (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
            ) {
                DuoImeState.onShown()
                keyboard?.show()
                true
            } else {
                false
            }
        }
    Box(modifier = modifier) {
        content(state.editing, gateFocused, fieldModifier)
        if (!state.editing && enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .focusRequester(focusRequester)
                    .duoTextFieldNavigation()
                    .then(
                        if (downFocus != null) {
                            Modifier.focusProperties { down = downFocus }
                        } else {
                            Modifier
                        }
                    )
                    .onFocusChanged {
                        gateFocused = it.isFocused
                        onGateFocusChanged(it.isFocused)
                    }
                    .duoClickable {
                        state.editing = true
                        DuoImeState.onShown()
                    },
            )
        }
    }
}

/**
 * Lets a single-line text field release Up/Down so the D-pad can leave it.
 * BasicTextField otherwise consumes these keys for cursor movement and traps
 * gamepad focus inside forms/search fields.
 */
fun Modifier.duoTextFieldNavigation(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionUp -> {
                focusManager.moveFocus(FocusDirection.Up)
                true
            }
            Key.DirectionDown -> {
                focusManager.moveFocus(FocusDirection.Down)
                true
            }
            else -> false
        }
    }
}

/**
 * Page-level shoulder shortcuts: L1/R1 move to the previous/next top tab and
 * L2/R2 to the previous/next secondary row (live areas, dynamic filters,
 * dynamic UP filters...). Apply to the page root: the preview handler sees the
 * event before the focused child, so it works wherever focus currently sits.
 *
 * `InputRouter` swallows auto-repeat for these keys, so holding a shoulder
 * button advances exactly one step.
 */
internal fun interface DuoShoulderEntry {
    fun handle(key: Key): Boolean
}

private class DuoShoulderActions(
    val onPreviousTab: (() -> Unit)?,
    val onNextTab: (() -> Unit)?,
    val onPreviousSection: (() -> Unit)?,
    val onNextSection: (() -> Unit)?,
) {
    fun handle(key: Key): Boolean {
        val action: (() -> Unit)? = when (key) {
            Key.ButtonL1 -> onPreviousTab
            Key.ButtonR1 -> onNextTab
            Key.ButtonL2 -> onPreviousSection
            Key.ButtonR2 -> onNextSection
            else -> null
        }
        if (action == null) return false
        action()
        return true
    }
}

/**
 * The page root normally receives L1/R1 through its focus tree. When the app
 * rail owns focus the page is outside that tree, so MainScreen forwards the
 * shoulder keys to the most recently composed page.
 */
internal object DuoShoulderDispatcher {
    private val entries = mutableListOf<DuoShoulderEntry>()

    fun push(entry: DuoShoulderEntry) {
        entries.add(entry)
    }

    fun remove(entry: DuoShoulderEntry) {
        entries.remove(entry)
    }

    fun dispatch(key: Key): Boolean = entries.lastOrNull()?.handle(key) ?: false
}

fun Modifier.duoTabCycling(
    onPreviousTab: (() -> Unit)? = null,
    onNextTab: (() -> Unit)? = null,
    onPreviousSection: (() -> Unit)? = null,
    onNextSection: (() -> Unit)? = null,
): Modifier = composed {
    val latest = rememberUpdatedState(
        DuoShoulderActions(
            onPreviousTab = onPreviousTab,
            onNextTab = onNextTab,
            onPreviousSection = onPreviousSection,
            onNextSection = onNextSection,
        )
    )
    DisposableEffect(Unit) {
        val entry = DuoShoulderEntry { key -> latest.value.handle(key) }
        DuoShoulderDispatcher.push(entry)
        onDispose { DuoShoulderDispatcher.remove(entry) }
    }
    this.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val action: (() -> Unit)? = when (event.key) {
            Key.ButtonL1 -> onPreviousTab
            Key.ButtonR1 -> onNextTab
            Key.ButtonL2 -> onPreviousSection
            Key.ButtonR2 -> onNextSection
            else -> null
        }
        if (action == null) {
            false
        } else {
            action()
            true
        }
    }
}

/**
 * Adds a focus target + center/Enter activation without adding another touch
 * clickable. Use this when the caller already has its own click handling.
 */
fun Modifier.duoFocusable(
    enabled: Boolean = true,
    onActivate: () -> Unit,
): Modifier = composed {
    this
        .onKeyEvent { event ->
            if (enabled && event.type == KeyEventType.KeyDown && isActivationKey(event.key)) {
                onActivate()
                true
            } else {
                false
            }
        }
        .focusable(enabled)
}

private fun isActivationKey(key: Key): Boolean =
    key == Key.DirectionCenter ||
        key == Key.Enter ||
        key == Key.NumPadEnter ||
        key == Key.Spacebar

@Composable
fun DuoModeBar(
    session: DuoSessionViewModel,
    enabled: Boolean,
    pageHint: String? = null,
) {
    val overlay by session.overlay.collectAsState()
    val hintOverride by session.hintOverride.collectAsState()
    val playerActive by session.playerActive.collectAsState()
    val gamepadConnected by session.gamepadConnected.collectAsState()

    val label = when {
        !enabled -> "单屏模式 · 触摸操作"
        !gamepadConnected -> "手柄未连接 · 触屏操作"
        overlay != null -> "菜单"
        !playerActive -> "浏览"
        else -> "播放控制"
    }
    val middle = when {
        !enabled || !gamepadConnected -> null
        overlay != null -> "A 关闭 · 方向键选择"
        !playerActive -> "A 返回"
        else -> "A 返回"
    }
    val right = when {
        !enabled || !gamepadConnected -> null
        overlay != null -> "B 确认"
        !playerActive -> "B 确认"
        else -> "B 确认"
    }
    val dotColor = when {
        !enabled || !gamepadConnected -> Muted
        overlay != null -> Color(0xFFFFC65C)
        !playerActive -> Color(0xFF6FB7FF)
        else -> Accent
    }
    val contextHint = when {
        !enabled || !gamepadConnected -> null
        // The modal itself already prints its own B/A and direction hints;
        // don't show a stale page hint behind it.
        overlay != null -> null
        else -> (hintOverride ?: pageHint)?.takeIf { it.isNotBlank() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(BarBg)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(dotColor, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            color = BarText,
            fontSize = 10.sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        if (contextHint != null) {
            Text(
                text = contextHint,
                color = Muted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (middle != null) {
            Spacer(Modifier.width(10.dp))
            Text(text = middle, color = Muted, fontSize = 10.sp, maxLines = 1)
        }
        if (right != null) {
            Spacer(Modifier.width(12.dp))
            Text(text = right, color = Muted, fontSize = 10.sp, maxLines = 1)
        }
    }
}
