package cn.xfangfang.biliduo.duo

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
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
    private var videoOnly = false

    init {
        displayManager.registerDisplayListener(this, null)
    }

    fun hasSecondaryDisplay(): Boolean = findSecondaryDisplay() != null

    /**
     * Swapped layout: the Presentation renders only video, so its window must
     * not take key focus away from the Activity UI. Safe to call before show().
     */
    fun setVideoOnly(value: Boolean) {
        videoOnly = value
        presentation?.setInteractive(!value)
    }

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
                    existing.setInteractive(!videoOnly)
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
            panel.setInteractive(!videoOnly)
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
        val ownDisplayId = activityDisplayId()
        return displayManager.displays.firstOrNull { display ->
            display.displayId != Display.DEFAULT_DISPLAY &&
                display.displayId != ownDisplayId &&
                display.state != Display.STATE_OFF &&
                (display.flags and Display.FLAG_PRESENTATION) != 0
        }
    }

    /**
     * When the bottom-panel launcher starts the Activity on Display 2, the
     * Presentation must not be created on that same panel (it would cover the
     * Activity or fail). MainActivity normally re-homes to Display 0; this keeps
     * the fallback path from picking the Activity's own display as "secondary".
     */
    private fun activityDisplayId(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.displayId ?: Display.DEFAULT_DISPLAY
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.displayId
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
