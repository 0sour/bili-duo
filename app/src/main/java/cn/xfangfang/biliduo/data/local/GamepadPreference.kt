package cn.xfangfang.biliduo.data.local

import android.content.SharedPreferences
import android.view.KeyEvent
import cn.xfangfang.biliduo.BilibiliApp

/**
 * The RP Duo handheld-style option changes which Android keycode each physical
 * face button emits. Actions stay on the physical positions:
 * bottom B = confirm, right A = back, left Y = refresh.
 */
enum class GamepadStyle(
    val storageValue: String,
    val label: String,
    val faceLayout: String,
) {
    XBOX(
        storageValue = "xbox",
        label = "Xbox 模式",
        faceLayout = "左 X · 右 B · 上 Y · 下 A",
    ),
    RETRO(
        storageValue = "retro",
        label = "Retro 模式",
        faceLayout = "左 Y · 右 A · 上 X · 下 B",
    );

    /**
     * Normalize a face-button keycode to the canonical Xbox layout used by
     * all screen handlers. Retro emits the physical labels directly, so A/B
     * and X/Y are swapped here; non-face keys and keyboards are untouched.
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
    }

    private fun ensureLoaded() {
        if (loaded) return
        cached = GamepadPreferenceManager(prefs).load()
        loaded = true
    }
}
