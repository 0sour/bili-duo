package cn.xfangfang.biliduo.ui.common

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shared zoom/pan state for the full-screen image viewers. The transform is
 * applied to the image with [graphicsLayer]; [viewport] is the visible image
 * area used for clamping and for translating the left stick into pixels.
 */
@Stable
class ImageZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var viewport by mutableStateOf(IntSize.Zero)
        private set

    fun updateViewport(size: IntSize) {
        viewport = size
        clamp()
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    /** R2/L2: zoom around the viewport center. */
    fun zoomBy(factor: Float) {
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        zoomAround(center, factor)
    }

    /** Touch: pinch and drag; [centroid] is in the image area's local pixels. */
    fun applyGesture(centroid: Offset, pan: Offset, zoom: Float) {
        val old = scale
        val next = (old * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        if (next <= 1.001f) {
            reset()
            return
        }
        val change = next / old
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        val focus = centroid - center
        offset = offset * change + focus * (1f - change) + pan
        scale = next
        clamp()
    }

    /** Left stick: continuous movement while the stick is deflected. */
    fun panBy(delta: Offset) {
        if (scale <= 1.001f) return
        offset += delta
        clamp()
    }

    private fun zoomAround(centroid: Offset, factor: Float) {
        val old = scale
        val next = (old * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        if (next <= 1.001f) {
            reset()
            return
        }
        val change = next / old
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        val focus = centroid - center
        offset = offset * change + focus * (1f - change)
        scale = next
        clamp()
    }

    private fun clamp() {
        if (scale <= 1.001f) {
            scale = 1f
            offset = Offset.Zero
            return
        }
        val maxX = (scale - 1f) * viewport.width / 2f
        val maxY = (scale - 1f) * viewport.height / 2f
        offset = Offset(
            offset.x.coerceIn(-maxX, maxX),
            offset.y.coerceIn(-maxY, maxY),
        )
    }

    private companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 4f
    }
}

@Composable
fun rememberImageZoomState(): ImageZoomState = remember { ImageZoomState() }

/**
 * The top Activity receives gamepad MotionEvents; this bridge lets the active
 * bottom-screen image viewer consume only the left stick while it is open.
 */
object ImageZoomMotion {
    var active: ImageZoomState? = null
        private set
    var stickX = 0f
        private set
    var stickY = 0f
        private set

    fun attach(state: ImageZoomState) {
        active = state
        stickX = 0f
        stickY = 0f
    }

    fun detach(state: ImageZoomState) {
        if (active === state) {
            active = null
            stickX = 0f
            stickY = 0f
        }
    }

    fun onStick(x: Float, y: Float) {
        stickX = x
        stickY = y
    }

    fun reset() {
        stickX = 0f
        stickY = 0f
    }
}

/**
 * AsyncImage with shared pinch/drag and left-stick panning. Callers keep the
 * image inside a clipped rounded container; this composable clips again so a
 * zoomed image can never draw over the viewer chrome.
 */
@Composable
fun DuoZoomableImage(
    model: Any?,
    contentDescription: String?,
    state: ImageZoomState,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    BoxWithConstraints(modifier.clipToBounds()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        LaunchedEffect(widthPx, heightPx) {
            state.updateViewport(
                IntSize(widthPx.roundToInt(), heightPx.roundToInt()),
            )
        }
        DisposableEffect(state) {
            ImageZoomMotion.attach(state)
            onDispose { ImageZoomMotion.detach(state) }
        }
        LaunchedEffect(state) {
            var lastFrame = 0L
            while (isActive) {
                withFrameNanos { now ->
                    if (lastFrame != 0L) {
                        val dt = ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
                        val x = ImageZoomMotion.stickX
                        val y = ImageZoomMotion.stickY
                        if (abs(x) > STICK_DEADZONE || abs(y) > STICK_DEADZONE) {
                            // Scroll-style panning: pushing the stick right/up
                            // moves the viewport right/up, so the image content
                            // moves the opposite way. This matches user
                            // expectation and keeps touch drag unchanged.
                            state.panBy(
                                Offset(
                                    -x * state.viewport.width * STICK_SPEED * dt,
                                    -y * state.viewport.height * STICK_SPEED * dt,
                                ),
                            )
                        }
                    }
                    lastFrame = now
                }
            }
        }
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = state.scale
                    scaleY = state.scale
                    translationX = state.offset.x
                    translationY = state.offset.y
                },
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(state) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        state.applyGesture(centroid, pan, zoom)
                    }
                },
        )
    }
}

private const val STICK_DEADZONE = 0.15f
private const val STICK_SPEED = 1.6f
