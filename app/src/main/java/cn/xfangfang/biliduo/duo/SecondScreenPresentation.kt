package cn.xfangfang.biliduo.duo

import android.app.Presentation
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Display
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

    /** Restore Android focus after the window is shown again (Home/return). */
    fun requestComposeFocus() {
        val root = composeView ?: return
        val target = root.getChildAt(0) ?: root
        target.requestFocus()
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
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onStop() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onStop()
    }
}
