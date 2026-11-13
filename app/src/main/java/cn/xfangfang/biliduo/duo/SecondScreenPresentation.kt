package cn.xfangfang.biliduo.duo

import android.app.Presentation
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import android.view.KeyEvent
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import cn.xfangfang.biliduo.ui.theme.BilibiliPureTheme
import cn.xfangfang.biliduo.ui.theme.rememberDuoDarkTheme

/**
 * Compose host on the bottom display.
 *
 * A Presentation has no lifecycle of its own, so it implements the owners that
 * Compose, ViewModel, SavedState, ActivityResult and BackHandler expect. The
 * Activity-owned ActivityResultRegistry and OnBackPressedDispatcher are shared
 * with the bottom UI instead of creating a second, disconnected instance.
 */
class SecondScreenPresentation(
    private val activity: ComponentActivity,
    display: Display,
    private val content: @Composable () -> Unit,
    private val onBack: () -> Unit,
) : Presentation(activity, display),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    ActivityResultRegistryOwner,
    OnBackPressedDispatcherOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedState = SavedStateRegistryController.create(this)
    private var composeView: ComposeView? = null
    private var lastBackAt = 0L

    private val backInvokedCallback: OnBackInvokedCallback? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OnBackInvokedCallback { dispatchBack() }
        } else {
            null
        }

    /** Restore Android focus after the window is shown again (Home/return). */
    fun requestComposeFocus() {
        val root = composeView ?: return
        val target = root.getChildAt(0) ?: root
        target.requestFocus()
    }

    /**
     * Swapped layout: this Presentation only renders the video surface. Make its
     * window non-focusable so hardware keys keep going to the Activity (which
     * now owns the UI) instead of being captured by an inert video window.
     */
    fun setInteractive(interactive: Boolean) {
        val window = window ?: return
        if (interactive) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            window.decorView.post { requestComposeFocus() }
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    /**
     * The bottom Presentation owns the focused window, so the Activity's
     * dispatchKeyEvent no longer sees Return. Route the physical key and the
     * platform back invocation through the same bottom-UI back pipeline as B.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) dispatchBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        dispatchBack()
    }

    private fun dispatchBack() {
        val now = SystemClock.uptimeMillis()
        if (now - lastBackAt < BACK_DEBOUNCE_MS) return
        lastBackAt = now
        onBack()
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val activityResultRegistry: ActivityResultRegistry get() = activity.activityResultRegistry
    override val onBackPressedDispatcher: OnBackPressedDispatcher
        get() = activity.onBackPressedDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(false)
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        val decor = window!!.decorView
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeViewModelStoreOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
        decor.setViewTreeOnBackPressedDispatcherOwner(this)

        val owner = this
        val view = ComposeView(context).apply {
            isFocusableInTouchMode = true
            setContent {
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides owner,
                    LocalOnBackPressedDispatcherOwner provides owner,
                ) {
                    BilibiliPureTheme(darkTheme = rememberDuoDarkTheme()) {
                        content()
                    }
                }
            }
        }
        composeView = view
        setContentView(view)
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback?.let { callback ->
                window?.onBackInvokedDispatcher?.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    callback,
                )
            }
        }
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback?.let { callback ->
                window?.onBackInvokedDispatcher?.unregisterOnBackInvokedCallback(callback)
            }
        }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onStop()
    }

    private companion object {
        const val BACK_DEBOUNCE_MS = 250L
    }
}
