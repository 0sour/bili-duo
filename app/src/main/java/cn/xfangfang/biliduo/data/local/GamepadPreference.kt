package cn.xfangfang.biliduo.data.local

import android.content.SharedPreferences
import android.view.KeyEvent
import cn.xfangfang.biliduo.BilibiliApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The RP Duo handheld-style option changes which Android keycode each physical
 * face button emits. Actions stay on the physical positions:
 * bottom = confirm, right = back, left = refresh. The labels follow the
 * physical buttons, so every hint reads labels from the enum instead of
 * hardcoding A/B/X/Y.
 *
 * Important: on this device the system "XBox style" already flips the firmware
 * keycodes (printed B at the bottom emits the canonical A code), so XBOX must
 * NOT normalize. Retro sends the unflipped codes and is normalized here.
 */
enum class GamepadStyle(
    val storageValue: String,
    val label: String,
    val faceLayout: String,
    val confirmLabel: String,
    val backLabel: String,
    val actionLabel: String,
    val unboundLabel: String,
) {
    XBOX(
        storageValue = "xbox",
        label = "Xbox 模式",
        faceLayout = "左 Y · 右 A · 上 X · 下 B",
        confirmLabel = "B",
        backLabel = "A",
        actionLabel = "Y",
        unboundLabel = "X",
    ),
    RETRO(
        storageValue = "retro",
        label = "Retro 模式",
        faceLayout = "左 X · 右 B · 上 Y · 下 A",
        confirmLabel = "A",
        backLabel = "B",
        actionLabel = "X",
        unboundLabel = "Y",
    );

    /**
     * Normalize a face-button keycode to the canonical Xbox layout used by
     * all screen handlers. Retro emits the unflipped B/A/Y/X codes, so A/B
     * and X/Y are swapped here. XBox style is already flipped by the system;
     * normalizing it would invert the buttons. Non-face keys are untouched.
     */
    fun normalizeFaceKey(code: Int): Int {
        if (this != RETRO) return code
        return when (code) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
            KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
            KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
            else -> code
        }
    }

    companion object {
        fun fromStorage(value: String?): GamepadStyle? =
            entries.firstOrNull { it.storageValue == value }
    }
}

class GamepadPreferenceManager(private val prefs: SharedPreferences) {
    fun load(): GamepadStyle? =
        GamepadStyle.fromStorage(prefs.getString(KEY_STYLE, null))

    fun save(style: GamepadStyle) {
        prefs.edit().putString(KEY_STYLE, style.storageValue).apply()
    }

    private companion object {
        const val KEY_STYLE = "gamepad_style"
    }
}

/**
 * InputRouter needs the current style on every key event; the Settings screen
 * and first-run setup need a synchronous read/write as well. Cache the value
 * so a held key does not hit SharedPreferences repeatedly.
 */
object GamepadStyleStore {
    @Volatile
    private var loaded = false

    @Volatile
    private var cached: GamepadStyle? = null

    private val _style = MutableStateFlow(GamepadStyle.XBOX)

    /** Compose-friendly current style; updates immediately after [save]. */
    val style: StateFlow<GamepadStyle> = _style.asStateFlow()

    private val prefs: SharedPreferences
        get() = BilibiliApp.instance.getSharedPreferences("bili_prefs", android.content.Context.MODE_PRIVATE)

    fun hasChosen(): Boolean {
        ensureLoaded()
        return cached != null
    }

    fun current(): GamepadStyle {
        ensureLoaded()
        return cached ?: GamepadStyle.XBOX
    }

    fun save(style: GamepadStyle) {
        GamepadPreferenceManager(prefs).save(style)
        cached = style
        loaded = true
        _style.value = style
    }

    private fun ensureLoaded() {
        if (loaded) return
        cached = GamepadPreferenceManager(prefs).load()
        loaded = true
        _style.value = cached ?: GamepadStyle.XBOX
    }
}
