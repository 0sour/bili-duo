package cn.xfangfang.biliduo.duo

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable

/**
 * Owns the bottom-screen [android.app.Presentation].
 *
 * The Duo exposes the bottom panel as a display with FLAG_PRESENTATION and
 * canHostTasks=false, so an Activity cannot be launched there; a Presentation
 * with a ComposeView is the only reliable UI host.
 */
class SecondScreenController(
    private val activity: ComponentActivity,
    private val content: @Composable () -> Unit,
    private val onBack: () -> Unit = {},
    private val onAvailabilityChanged: (Boolean) -> Unit = {},
    private val onDecorViewChanged: (View?) -> Unit = {},
) : DisplayManager.DisplayListener {

    private val displayManager =
        activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private var presentation: SecondScreenPresentation? = null
    private var started = false

    init {
        displayManager.registerDisplayListener(this, null)
    }

    fun hasSecondaryDisplay(): Boolean = findSecondaryDisplay() != null

    /** Called from Activity.onStart. */
    fun show() {
        started = true
        showIfPossible()
    }

    /**
     * Called from Activity.onStop. Hide the window without dismissing it so the
     * Compose tree, navigation state and player session survive backgrounding.
     */
    fun hide() {
        started = false
        val panel = presentation ?: return
        onDecorViewChanged(null)
        runCatching { panel.hide() }
            .onFailure { Log.w(TAG, "Unable to hide second screen", it) }
    }

    /** Called from Activity.onDestroy. */
    fun release() {
        started = false
        displayManager.unregisterDisplayListener(this)
        hidePresentation()
    }

    val isShowing: Boolean
        get() = runCatching { presentation?.isShowing == true }.getOrDefault(false)

    private fun showIfPossible() {
        if (!started || isShowing) return
        val existing = presentation
        if (existing != null) {
            runCatching { existing.show() }
                .onSuccess {
                    existing.window?.decorView?.post { existing.requestComposeFocus() }
                    onDecorViewChanged(existing.window?.decorView)
                    onAvailabilityChanged(true)
                }
                .onFailure {
                    Log.w(TAG, "Unable to show existing second screen", it)
                    hidePresentation()
                }
            return
        }
        val display = findSecondaryDisplay()
        if (display == null) {
            Log.i(TAG, "No presentation display found")
            onAvailabilityChanged(false)
            return
        }
        val panel = SecondScreenPresentation(activity, display, content, onBack)
        try {
            panel.show()
            presentation = panel
            panel.window?.decorView?.post { panel.requestComposeFocus() }
            onDecorViewChanged(panel.window?.decorView)
            onAvailabilityChanged(true)
            Log.i(TAG, "Second screen shown on display ${display.displayId}")
        } catch (e: Exception) {
            runCatching { panel.dismiss() }
            presentation = null
            onDecorViewChanged(null)
            onAvailabilityChanged(false)
            Log.w(TAG, "Unable to show second screen", e)
        }
    }

    private fun hidePresentation() {
        val panel = presentation
        presentation = null
        onDecorViewChanged(null)
        onAvailabilityChanged(false)
        if (panel != null) {
            runCatching { panel.dismiss() }
                .onFailure { Log.w(TAG, "Unable to dismiss second screen", it) }
        }
    }

    private fun findSecondaryDisplay(): Display? {
        return displayManager.displays.firstOrNull { display ->
            display.displayId != Display.DEFAULT_DISPLAY &&
                display.state != Display.STATE_OFF &&
                (display.flags and Display.FLAG_PRESENTATION) != 0
        }
    }

    override fun onDisplayAdded(displayId: Int) {
        if (started) showIfPossible()
    }

    override fun onDisplayRemoved(displayId: Int) {
        if (presentation?.display?.displayId == displayId) hidePresentation()
    }

    override fun onDisplayChanged(displayId: Int) {
        // The panel may come back after the lid is opened; keep the Activity
        // state independent from the display being temporarily unavailable.
        if (started && presentation == null) showIfPossible()
    }

    private companion object {
        const val TAG = "BiliDuo/SecondScreen"
    }
}
