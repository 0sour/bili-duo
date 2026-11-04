package cn.xfangfang.biliduo.duo

import android.util.Log
import android.view.KeyEvent
import android.view.View
import cn.xfangfang.biliduo.BilibiliApp
import cn.xfangfang.biliduo.BuildConfig

/**
 * Forwards key events from the top Activity to the bottom Presentation's
 * decorView. The bridge is intentionally one-way: events still have exactly
 * one owner (the bottom Compose tree) in PLAYER/BROWSE.
 */
class BottomUiBridge {

    private var decorView: View? = null

    val isAttached: Boolean
        get() = decorView != null

    fun attach(view: View?) {
        decorView = view
    }

    fun detach() {
        decorView = null
    }

    /** Returns false if the Presentation is gone or the view rejected the event. */
    fun dispatch(event: KeyEvent): Boolean {
        val view = decorView ?: run {
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "BottomUiBridge: no decorView")
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
