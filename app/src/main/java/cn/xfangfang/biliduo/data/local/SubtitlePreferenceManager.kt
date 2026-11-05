package cn.xfangfang.biliduo.data.local

import android.content.SharedPreferences
import org.json.JSONObject

data class SubtitlePreference(
    val lan: String,
    val enabled: Boolean,
    val offsetX: Float,
    val offsetY: Float
)

/**
 * Global subtitle appearance, shared by every video. The per-page preference
 * above only remembers which track was on for a given aid/cid.
 */
data class SubtitleStylePreference(
    val textSizeSp: Float = DEFAULT_TEXT_SIZE_SP,
    val backgroundAlpha: Float = DEFAULT_BACKGROUND_ALPHA,
    val bottomPaddingDp: Int = DEFAULT_BOTTOM_PADDING_DP,
) {
    companion object {
        const val DEFAULT_TEXT_SIZE_SP = 22f
        const val DEFAULT_BACKGROUND_ALPHA = 0.55f
        const val DEFAULT_BOTTOM_PADDING_DP = 58
    }
}

class SubtitlePreferenceManager(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY_PREFIX = "subtitle_"
        private const val KEY_STYLE_TEXT_SIZE = "subtitle_style_text_size"
        private const val KEY_STYLE_BACKGROUND = "subtitle_style_background_alpha"
        private const val KEY_STYLE_BOTTOM = "subtitle_style_bottom_padding"
    }

    private fun pageKey(aid: Long, cid: Long) = "$KEY_PREFIX${aid}_$cid"

    fun load(aid: Long, cid: Long): SubtitlePreference? {
        val json = prefs.getString(pageKey(aid, cid), null) ?: return null
        return try {
            val obj = JSONObject(json)
            SubtitlePreference(
                lan = obj.optString("lan", ""),
                enabled = obj.optBoolean("enabled", false),
                offsetX = obj.optDouble("offsetX", 0.0).toFloat(),
                offsetY = obj.optDouble("offsetY", 0.0).toFloat()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun save(aid: Long, cid: Long, pref: SubtitlePreference) {
        val obj = JSONObject().apply {
            put("lan", pref.lan)
            put("enabled", pref.enabled)
            put("offsetX", pref.offsetX.toDouble())
            put("offsetY", pref.offsetY.toDouble())
        }
        prefs.edit()
            .putString(pageKey(aid, cid), obj.toString())
            .apply()
    }

    fun loadStyle(): SubtitleStylePreference = SubtitleStylePreference(
        textSizeSp = prefs.getFloat(
            KEY_STYLE_TEXT_SIZE,
            SubtitleStylePreference.DEFAULT_TEXT_SIZE_SP
        ),
        backgroundAlpha = prefs.getFloat(
            KEY_STYLE_BACKGROUND,
            SubtitleStylePreference.DEFAULT_BACKGROUND_ALPHA
        ),
        bottomPaddingDp = prefs.getInt(
            KEY_STYLE_BOTTOM,
            SubtitleStylePreference.DEFAULT_BOTTOM_PADDING_DP
        ),
    )

    fun saveStyle(style: SubtitleStylePreference) {
        prefs.edit()
            .putFloat(KEY_STYLE_TEXT_SIZE, style.textSizeSp)
            .putFloat(KEY_STYLE_BACKGROUND, style.backgroundAlpha)
            .putInt(KEY_STYLE_BOTTOM, style.bottomPaddingDp)
            .apply()
    }
}
