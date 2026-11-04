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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xfangfang.biliduo.duo.DuoSessionViewModel

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
fun Modifier.duoTabCycling(
    onPreviousTab: (() -> Unit)? = null,
    onNextTab: (() -> Unit)? = null,
    onPreviousSection: (() -> Unit)? = null,
    onNextSection: (() -> Unit)? = null,
): Modifier = onPreviewKeyEvent { event ->
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
) {
    val overlay by session.overlay.collectAsState()
    val playerActive by session.playerActive.collectAsState()
    val gamepadConnected by session.gamepadConnected.collectAsState()

    val label = when {
        !enabled -> "单屏模式 · 触摸操作"
        !gamepadConnected -> "手柄未连接 · 触屏操作"
        overlay != null -> "菜单"
        !playerActive -> "浏览 · 手柄/触屏"
        else -> "播放控制 · 手柄/触屏"
    }
    val middle = when {
        !enabled || !gamepadConnected -> null
        overlay != null -> "B 关闭 · 方向键选择"
        !playerActive -> "B 返回"
        else -> "B 返回"
    }
    val right = when {
        !enabled || !gamepadConnected -> null
        overlay != null -> "A 确认"
        !playerActive -> "A 确认"
        else -> "A 确认 · START 菜单"
    }
    val dotColor = when {
        !enabled || !gamepadConnected -> Muted
        overlay != null -> Color(0xFFFFC65C)
        !playerActive -> Color(0xFF6FB7FF)
        else -> Accent
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
        Spacer(Modifier.weight(1f))
        if (middle != null) {
            Text(text = middle, color = Muted, fontSize = 10.sp, maxLines = 1)
        }
        if (right != null) {
            Spacer(Modifier.width(18.dp))
            Text(text = right, color = Muted, fontSize = 10.sp, maxLines = 1)
        }
    }
}
