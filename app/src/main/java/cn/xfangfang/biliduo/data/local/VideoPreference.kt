package cn.xfangfang.biliduo.data.local

import android.content.SharedPreferences

/**
 * Defaults used every time a video is opened. The playback page changes its
 * own copy only for the current video; this object is written from Settings.
 */
data class VideoPreference(
    val quality: Int = 0,
    val speed: Float = 1f,
    val subtitleEnabled: Boolean = false,
    val subtitleTextSizeSp: Float = SubtitleStylePreference.DEFAULT_TEXT_SIZE_SP,
    val subtitleBackgroundAlpha: Float = SubtitleStylePreference.DEFAULT_BACKGROUND_ALPHA,
    val subtitleBottomPaddingDp: Int = SubtitleStylePreference.DEFAULT_BOTTOM_PADDING_DP,
)

data class VideoQualityOption(val qn: Int, val label: String)

val videoQualityOptions = listOf(
    VideoQualityOption(0, "自动"),
    VideoQualityOption(116, "1080P60"),
    VideoQualityOption(112, "1080P+"),
    VideoQualityOption(80, "1080P"),
    VideoQualityOption(74, "720P60"),
    VideoQualityOption(64, "720P"),
    VideoQualityOption(32, "480P"),
    VideoQualityOption(16, "360P"),
)

val videoSpeedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
val videoSubtitleSizeOptions = listOf(18f, 22f, 26f, 30f)
val videoSubtitleBackgroundOptions = listOf(0f, 0.35f, 0.55f, 0.8f)
val videoSubtitleBottomOptions = listOf(32, 58, 90)

fun videoQualityLabel(qn: Int): String =
    videoQualityOptions.firstOrNull { it.qn == qn }?.label ?: "自动"

fun videoSpeedLabel(speed: Float): String {
    val text = "%.2f".format(speed).trimEnd('0').trimEnd('.')
    return "${text}x"
}

fun videoSubtitleSizeLabel(value: Float): String = when {
    value <= 19f -> "小"
    value <= 23f -> "标准"
    value <= 27f -> "大"
    else -> "特大"
}

fun videoSubtitleBackgroundLabel(value: Float): String = when {
    value <= 0.01f -> "无"
    value <= 0.4f -> "淡"
    value <= 0.6f -> "标准"
    else -> "深"
}

fun videoSubtitlePositionLabel(value: Int): String = when {
    value <= 40 -> "靠下"
    value <= 70 -> "标准"
    else -> "靠上"
}

class VideoPreferenceManager(private val prefs: SharedPreferences) {

    fun load(): VideoPreference {
        // rc48 and older stored the subtitle style as a global preference.
        // Carry it over once as the new default; no per-video track memory is
        // migrated because subtitle state is now session-only.
        val legacyStyle = SubtitlePreferenceManager(prefs).loadStyle()
        return VideoPreference(
            quality = prefs.getInt(KEY_QUALITY, 0),
            speed = prefs.getFloat(KEY_SPEED, 1f).coerceIn(0.5f, 2f),
            subtitleEnabled = prefs.getBoolean(KEY_SUBTITLE_ENABLED, false),
            subtitleTextSizeSp = prefs.getFloat(
                KEY_SUBTITLE_SIZE,
                legacyStyle.textSizeSp,
            ).coerceIn(12f, 36f),
            subtitleBackgroundAlpha = prefs.getFloat(
                KEY_SUBTITLE_BACKGROUND,
                legacyStyle.backgroundAlpha,
            ).coerceIn(0f, 1f),
            subtitleBottomPaddingDp = prefs.getInt(
                KEY_SUBTITLE_BOTTOM,
                legacyStyle.bottomPaddingDp,
            ).coerceIn(0, 160),
        )
    }

    fun save(preference: VideoPreference) {
        prefs.edit()
            .putInt(KEY_QUALITY, preference.quality)
            .putFloat(KEY_SPEED, preference.speed)
            .putBoolean(KEY_SUBTITLE_ENABLED, preference.subtitleEnabled)
            .putFloat(KEY_SUBTITLE_SIZE, preference.subtitleTextSizeSp)
            .putFloat(KEY_SUBTITLE_BACKGROUND, preference.subtitleBackgroundAlpha)
            .putInt(KEY_SUBTITLE_BOTTOM, preference.subtitleBottomPaddingDp)
            .apply()
    }

    private companion object {
        const val KEY_QUALITY = "video_default_quality"
        const val KEY_SPEED = "video_default_speed"
        const val KEY_SUBTITLE_ENABLED = "video_default_subtitle_enabled"
        const val KEY_SUBTITLE_SIZE = "video_default_subtitle_size"
        const val KEY_SUBTITLE_BACKGROUND = "video_default_subtitle_background"
        const val KEY_SUBTITLE_BOTTOM = "video_default_subtitle_bottom"
    }
}
