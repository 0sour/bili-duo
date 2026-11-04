package cn.xfangfang.biliduo.ui.player

import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Top screen: a pure video surface. No controller, no OSD, no touch handling.
 * The PlayerView detaches from the player while the Activity is stopped and
 * reattaches on start; the ExoPlayer instance itself is never recreated.
 */
@Composable
fun TopPlayerSurface(
    controller: PlayerController,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var started by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> started = true
                Lifecycle.Event.ON_STOP -> started = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize().background(ComposeColor.Black)) {
        if (state.isActive) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        setBackgroundColor(Color.BLACK)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        useController = false
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        // Keep the last frame while a live quality switch
                        // re-prepares the HLS source; the next frame replaces it.
                        setKeepContentOnPlayerReset(true)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        isClickable = false
                        isFocusable = false
                    }
                },
                update = { view ->
                    view.player = if (started) controller.player else null
                    view.keepScreenOn = started && state.isPlaying
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
