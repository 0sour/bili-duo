package cn.xfangfang.biliduo.duo

import android.util.Log
import android.view.KeyEvent
import android.view.View
import cn.xfangfang.biliduo.BilibiliApp
import cn.xfangfang.biliduo.BuildConfig

/**
 * Forwards key events from the top Activity to whichever window currently owns
 * the UI: the bottom Presentation's decorView in the normal layout, or the
 * Activity's own ComposeView after the displays are swapped. The bridge is
 * intentionally one-way: events still have exactly one owner.
 */
class BottomUiBridge {

    private var decorView: View? = null
    private var localView: View? = null
    private var useLocal = false

    val isAttached: Boolean
        get() = if (useLocal) localView != null else decorView != null

    /** The Presentation window (UI on the bottom display). */
    fun attach(view: View?) {
        decorView = view
    }

    /** The Activity's content view (UI moved to the top display). */
    fun attachLocal(view: View?) {
        localView = view
    }

    fun setUseLocal(value: Boolean) {
        useLocal = value
    }

    fun detach() {
        decorView = null
    }

    /** Returns false if the target is gone or the view rejected the event. */
    fun dispatch(event: KeyEvent): Boolean {
        val view = (if (useLocal) localView else decorView) ?: run {
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "BottomUiBridge: no target view")
            return false
        }
        val handled = runCatching { view.dispatchKeyEvent(event) }.getOrDefault(false)
        if (BuildConfig.DEBUG) {
            Log.d(
                BilibiliApp.TAG,
                "BottomUiBridge: key=${event.keyCode} action=${event.action} " +
                    "handled=$handled focus=${view.findFocus()?.javaClass?.simpleName}",
            )
        }
        return handled
    }
}
