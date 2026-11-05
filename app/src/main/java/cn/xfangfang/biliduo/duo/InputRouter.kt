package cn.xfangfang.biliduo.duo

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import cn.xfangfang.biliduo.BilibiliApp
import cn.xfangfang.biliduo.BuildConfig

/**
 * Single owner of gamepad/keyboard events coming from the top Activity.
 *
 * The player has no separate "top control" mode: while a player is active the
 * bottom console's focus tree owns every key. A/DPAD_CENTER is translated to
 * Compose's center key, B goes through the session's back interceptors, and
 * X/START/LB/RB are forwarded to the console.
 */
class InputRouter(
    private val session: DuoSessionViewModel,
    private val bridge: BottomUiBridge,
) {
    private val handledDown = mutableSetOf<Int>()
    private val forwardedDown = mutableSetOf<Int>()
    private val lastDownAt = mutableMapOf<Int, Long>()

    fun dispatch(event: KeyEvent): Boolean {
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> onDown(event)
            KeyEvent.ACTION_UP -> {
                val handled = handledDown.remove(event.keyCode)
                val forwarded = forwardedDown.remove(event.keyCode)
                if (forwarded) bridge.dispatch(event)
                handled || forwarded
            }
            else -> false
        }
    }

    private fun onDown(event: KeyEvent): Boolean {
        val code = event.keyCode
        val now = eventTime(event)
        if (event.repeatCount == 0) {
            val previous = lastDownAt[code]
            lastDownAt[code] = now
            if (previous != null && now - previous < DEDUPE_MS) {
                return true
            }
        }

        val overlay = session.overlay.value
        val mode = session.mode.value

        if (BuildConfig.DEBUG) {
            Log.d(
                BilibiliApp.TAG,
                "InputRouter: key=$code repeat=${event.repeatCount} mode=$mode overlay=$overlay",
            )
        }

        if (overlay != null) {
            // B closes the modal; the screen may first consume it (e.g. leave an
            // inline editor inside the danmaku settings sheet).
            if (isBack(code)) {
                if (!session.handleOverlayBack()) session.closeOverlay()
                return consume(code)
            }
            if (code == KeyEvent.KEYCODE_BUTTON_A) {
                if (event.repeatCount == 0) sendCenterTap()
                return consume(code)
            }
            return forward(event)
        }

        return when (mode) {
            DuoMode.BROWSE, DuoMode.PLAYER -> handleBottomUi(event)
            DuoMode.OVERLAY -> forward(event)
        }
    }

    private fun handleBottomUi(event: KeyEvent): Boolean {
        val code = event.keyCode
        val repeating = event.repeatCount > 0

        // Holding a direction must move exactly one grid cell, holding B must
        // not pop several destinations, holding a shoulder button must not
        // race through tabs, and holding X/Y must not toggle twice or spam a
        // write request. These keys have no repeat semantics in the bottom UI;
        // the first press is still delivered below.
        if (repeating && (
                isBack(code) || isNavigationKey(code) || isShoulderKey(code) ||
                    code == KeyEvent.KEYCODE_X || code == KeyEvent.KEYCODE_Y
                )
        ) {
            return true
        }

        if (isBack(code)) {
            session.requestBack()
            return consume(code)
        }

        // Gamepad A does not map to Compose's click key; synthesize center.
        if (code == KeyEvent.KEYCODE_BUTTON_A) {
            if (!repeating) sendCenterTap()
            return consume(code)
        }

        // X / START / LB / RB stay routed to the console; each screen decides
        // whether they are meaningful.
        return forward(event)
    }

    private fun forward(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            forwardedDown.add(event.keyCode)
            bridge.dispatch(event)
        }
        return true
    }

    private fun sendCenterTap() {
        val now = SystemClock.uptimeMillis()
        bridge.dispatch(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        bridge.dispatch(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
    }

    private fun consume(code: Int): Boolean {
        handledDown.add(code)
        return true
    }

    private fun eventTime(event: KeyEvent): Long =
        event.eventTime.takeIf { it > 0L } ?: SystemClock.uptimeMillis()

    private fun isNavigationKey(code: Int): Boolean =
        code == KeyEvent.KEYCODE_DPAD_UP ||
            code == KeyEvent.KEYCODE_DPAD_DOWN ||
            code == KeyEvent.KEYCODE_DPAD_LEFT ||
            code == KeyEvent.KEYCODE_DPAD_RIGHT ||
            code == KeyEvent.KEYCODE_DPAD_CENTER ||
            code == KeyEvent.KEYCODE_ENTER ||
            code == KeyEvent.KEYCODE_NUMPAD_ENTER

    private fun isBack(code: Int): Boolean =
        code == KeyEvent.KEYCODE_BUTTON_B ||
            code == KeyEvent.KEYCODE_BACK ||
            code == KeyEvent.KEYCODE_ESCAPE

    private fun isShoulderKey(code: Int): Boolean =
        code == KeyEvent.KEYCODE_BUTTON_L1 ||
            code == KeyEvent.KEYCODE_BUTTON_R1 ||
            code == KeyEvent.KEYCODE_BUTTON_L2 ||
            code == KeyEvent.KEYCODE_BUTTON_R2

    private companion object {
        const val DEDUPE_MS = 40L
    }
}
