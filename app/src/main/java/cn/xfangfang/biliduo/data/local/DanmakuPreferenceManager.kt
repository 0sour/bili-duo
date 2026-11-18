package cn.xfangfang.biliduo.data.local

import android.content.SharedPreferences

data class DanmakuPreference(
    val enabled: Boolean = true,
    val opacity: Float = 0.9f,
    val speed: Float = 1f,
    val textSizeSp: Float = 16f,
    val area: Float = 1f,
    val blockedWords: Set<String> = emptySet(),
    /** Raw regular expression entered by the user; invalid patterns are ignored. */
    val blockedRegex: String = "",
    /** Official-style shield level 0–10; keeps weight >= level (0 = off). */
    val shieldLevel: Int = 0,
    val showScroll: Boolean = true,
    val showTop: Boolean = true,
    val showBottom: Boolean = true,
    /** Per-author hash from the danmaku XML (`p` field 7); never a real uid. */
    val blockedUidHashes: Set<String> = emptySet(),
    /** 0 = unlimited. */
    val maxOnScreen: Int = 0,
    /** Live danmaku has its own values; it has no blocked-word/max settings. */
    val liveEnabled: Boolean = enabled,
    val liveOpacity: Float = opacity,
    val liveSpeed: Float = speed,
    val liveTextSizeSp: Float = textSizeSp,
    val liveArea: Float = area,
)

/**
 * Global danmaku appearance preference. Kept in the same `bili_prefs` file as
 * progress/subtitles so it survives process restarts without a new store.
 */
class DanmakuPreferenceManager(private val prefs: SharedPreferences) {

    fun load(): DanmakuPreference {
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        val opacity = prefs.getFloat(KEY_OPACITY, 0.9f).coerceIn(0.2f, 1f)
        val speed = prefs.getFloat(KEY_SPEED, 1f).coerceIn(0.5f, 2f)
        val textSize = prefs.getFloat(KEY_TEXT_SIZE, 16f).coerceIn(12f, 24f)
        val area = prefs.getFloat(KEY_AREA, 1f).coerceIn(0.25f, 1f)
        return DanmakuPreference(
            enabled = enabled,
            opacity = opacity,
            speed = speed,
            textSizeSp = textSize,
            area = area,
            blockedWords = prefs.getStringSet(KEY_BLOCKED_WORDS, emptySet())
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.take(50)
                ?.toSet()
                ?: emptySet(),
            blockedRegex = prefs.getString(KEY_BLOCKED_REGEX, "").orEmpty().take(500),
            shieldLevel = prefs.getInt(KEY_SHIELD_LEVEL, 0).coerceIn(0, 10),
            showScroll = prefs.getBoolean(KEY_SHOW_SCROLL, true),
            showTop = prefs.getBoolean(KEY_SHOW_TOP, true),
            showBottom = prefs.getBoolean(KEY_SHOW_BOTTOM, true),
            blockedUidHashes = prefs.getStringSet(KEY_BLOCKED_UIDS, emptySet())
                ?.filter { it.isNotBlank() }
                ?.take(200)
                ?.toSet()
                ?: emptySet(),
            maxOnScreen = prefs.getInt(KEY_MAX_ON_SCREEN, 0).coerceIn(0, 200),
            // First run after the split: clone the existing video settings so
            // upgrading users keep their current live appearance.
            liveEnabled = prefs.getBoolean(KEY_LIVE_ENABLED, enabled),
            liveOpacity = prefs.getFloat(KEY_LIVE_OPACITY, opacity).coerceIn(0.2f, 1f),
            liveSpeed = prefs.getFloat(KEY_LIVE_SPEED, speed).coerceIn(0.5f, 2f),
            liveTextSizeSp = prefs.getFloat(KEY_LIVE_TEXT_SIZE, textSize).coerceIn(12f, 24f),
            liveArea = prefs.getFloat(KEY_LIVE_AREA, area).coerceIn(0.25f, 1f),
        )
    }

    fun save(pref: DanmakuPreference) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, pref.enabled)
            .putFloat(KEY_OPACITY, pref.opacity.coerceIn(0.2f, 1f))
            .putFloat(KEY_SPEED, pref.speed.coerceIn(0.5f, 2f))
            .putFloat(KEY_TEXT_SIZE, pref.textSizeSp.coerceIn(12f, 24f))
            .putFloat(KEY_AREA, pref.area.coerceIn(0.25f, 1f))
            .putStringSet(
                KEY_BLOCKED_WORDS,
                pref.blockedWords.map { it.trim() }.filter { it.isNotEmpty() }.take(50).toSet(),
            )
            .putString(KEY_BLOCKED_REGEX, pref.blockedRegex.take(500))
            .putInt(KEY_SHIELD_LEVEL, pref.shieldLevel.coerceIn(0, 10))
            .putBoolean(KEY_SHOW_SCROLL, pref.showScroll)
            .putBoolean(KEY_SHOW_TOP, pref.showTop)
            .putBoolean(KEY_SHOW_BOTTOM, pref.showBottom)
            .putStringSet(
                KEY_BLOCKED_UIDS,
                pref.blockedUidHashes.filter { it.isNotBlank() }.take(200).toSet(),
            )
            .putInt(KEY_MAX_ON_SCREEN, pref.maxOnScreen.coerceIn(0, 200))
            .putBoolean(KEY_LIVE_ENABLED, pref.liveEnabled)
            .putFloat(KEY_LIVE_OPACITY, pref.liveOpacity.coerceIn(0.2f, 1f))
            .putFloat(KEY_LIVE_SPEED, pref.liveSpeed.coerceIn(0.5f, 2f))
            .putFloat(KEY_LIVE_TEXT_SIZE, pref.liveTextSizeSp.coerceIn(12f, 24f))
            .putFloat(KEY_LIVE_AREA, pref.liveArea.coerceIn(0.25f, 1f))
            .commit()
    }

    private companion object {
        const val KEY_ENABLED = "danmaku_enabled"
        const val KEY_OPACITY = "danmaku_opacity"
        const val KEY_SPEED = "danmaku_speed"
        const val KEY_TEXT_SIZE = "danmaku_text_size"
        const val KEY_AREA = "danmaku_area"
        const val KEY_BLOCKED_WORDS = "danmaku_blocked_words"
        const val KEY_BLOCKED_REGEX = "danmaku_blocked_regex"
        const val KEY_SHIELD_LEVEL = "danmaku_shield_level"
        const val KEY_SHOW_SCROLL = "danmaku_show_scroll"
        const val KEY_SHOW_TOP = "danmaku_show_top"
        const val KEY_SHOW_BOTTOM = "danmaku_show_bottom"
        const val KEY_BLOCKED_UIDS = "danmaku_blocked_uids"
        const val KEY_MAX_ON_SCREEN = "danmaku_max_on_screen"
        const val KEY_LIVE_ENABLED = "danmaku_live_enabled"
        const val KEY_LIVE_OPACITY = "danmaku_live_opacity"
        const val KEY_LIVE_SPEED = "danmaku_live_speed"
        const val KEY_LIVE_TEXT_SIZE = "danmaku_live_text_size"
        const val KEY_LIVE_AREA = "danmaku_live_area"
    }
}
