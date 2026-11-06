package cn.xfangfang.biliduo.duo

import androidx.lifecycle.ViewModel
import cn.xfangfang.biliduo.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DuoMode {
    /** No player session: the bottom UI owns input. */
    BROWSE,

    /** Player is active: the bottom console focus tree owns all input. */
    PLAYER,

    /** A bottom-screen modal (quality/speed/exit) owns input. */
    OVERLAY,
}

enum class DuoOverlay {
    QUALITY,
    SPEED,
    /** Playback settings: danmaku and subtitle options. */
    SETTINGS,
    DANMAKU_SEND,
    COMMENT_SEND,
    COMMENT_DELETE,
    DOWNLOAD,
    COIN,
    FAVORITE,
    IMAGE,
}

/**
 * Activity-scoped input/session state shared by the top Activity and the bottom
 * Presentation. It deliberately contains no Android View references so it can
 * outlive the bottom display being hidden.
 */
class DuoSessionViewModel : ViewModel() {

    private val _mode = MutableStateFlow(DuoMode.BROWSE)
    val mode: StateFlow<DuoMode> = _mode.asStateFlow()

    private val _overlay = MutableStateFlow<DuoOverlay?>(null)
    val overlay: StateFlow<DuoOverlay?> = _overlay.asStateFlow()

    private val _playerActive = MutableStateFlow(false)
    val playerActive: StateFlow<Boolean> = _playerActive.asStateFlow()

    private val _backRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val backRequests: SharedFlow<Unit> = _backRequests.asSharedFlow()

    private val _focusEpoch = MutableStateFlow(0)
    val focusEpoch: StateFlow<Int> = _focusEpoch.asStateFlow()

    private val _gamepadConnected = MutableStateFlow(true)
    val gamepadConnected: StateFlow<Boolean> = _gamepadConnected.asStateFlow()

    /** Updated from MainActivity's InputDeviceListener; only drives the mode bar hint. */
    fun setGamepadConnected(connected: Boolean) {
        if (_gamepadConnected.value == connected) return
        _gamepadConnected.value = connected
        log("gamepad connected=$connected")
        if (connected) _focusEpoch.value++
    }

    /** Called when the player console enters composition. */
    fun onPlayerEntered() {
        _playerActive.value = true
        _overlay.value = null
        _mode.value = DuoMode.PLAYER
        log("onPlayerEntered")
    }

    /** Called when the player console leaves composition. */
    fun onPlayerExited() {
        _playerActive.value = false
        _overlay.value = null
        _mode.value = DuoMode.BROWSE
        log("onPlayerExited")
    }

    /** Open a bottom modal. Input ownership moves to OVERLAY until it is closed. */
    fun openOverlay(overlay: DuoOverlay) {
        if (!_playerActive.value) return
        if (_overlay.value == overlay) return
        _overlay.value = overlay
        _mode.value = DuoMode.OVERLAY
    }

    /** B / close: return to the player console focus tree. */
    fun closeOverlay() {
        if (_overlay.value == null) return
        overlayBackInterceptor = null
        _overlay.value = null
        _mode.value = if (_playerActive.value) DuoMode.PLAYER else DuoMode.BROWSE
    }

    private var overlayBackInterceptor: (() -> Boolean)? = null

    /**
     * Lets a screen consume B while an overlay is open, e.g. to leave an inline
     * text editor without closing the whole sheet. Returning true keeps the
     * overlay open.
     */
    fun setOverlayBackInterceptor(interceptor: (() -> Boolean)?) {
        overlayBackInterceptor = interceptor
    }

    fun handleOverlayBack(): Boolean = overlayBackInterceptor?.invoke() == true

    private var backInterceptor: (() -> Boolean)? = null

    /**
     * Lets the current screen consume B before the NavHost pops a destination.
     * Used by in-screen confirm cards (e.g. logout), which live in the same
     * window as the rest of the bottom UI and therefore can receive routed keys.
     */
    fun setBackInterceptor(interceptor: (() -> Boolean)?) {
        backInterceptor = interceptor
    }

    private var focusBackInterceptor: (() -> Boolean)? = null

    /**
     * Player consoles use this to move focus back inside the console before the
     * route pops. It runs after [setBackInterceptor], so an inline text field
     * (live chat) can still consume B first to hide the keyboard.
     */
    fun setFocusBackInterceptor(interceptor: (() -> Boolean)?) {
        focusBackInterceptor = interceptor
    }

    /** Ask the bottom NavHost to pop one destination (B in player/browse). */
    fun requestBack() {
        log("requestBack")
        if (backInterceptor?.invoke() == true) return
        if (focusBackInterceptor?.invoke() == true) return
        _backRequests.tryEmit(Unit)
    }

    /** Re-request focus after the Presentation window is shown again. */
    fun requestFocus() {
        _focusEpoch.value++
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) println("DuoSession: $message")
    }
}
