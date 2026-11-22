package cn.xfangfang.biliduo.ui.player

import cn.xfangfang.biliduo.data.model.AudioTrackOption
import cn.xfangfang.biliduo.data.model.QualityOption

/**
 * Exact preferred tier, otherwise the highest available tier below it.
 * Auto (0) stays on the best conventional tier: 4K/HDR are opt-in defaults
 * because the target panel is 1080p and decoding them is not guaranteed.
 */
internal fun pickDefaultQuality(
    qualities: List<QualityOption>,
    preferredQn: Int,
    isVip: Boolean?,
): QualityOption? {
    val allowed = qualities
        .filter { !it.vipOnly || isVip != false }
        .sortedByDescending { it.quality }
    if (allowed.isEmpty()) {
        return qualities.sortedByDescending { it.quality }.firstOrNull()
    }
    if (preferredQn <= 0) {
        return allowed.firstOrNull { it.quality < 120 } ?: allowed.first()
    }
    return allowed.firstOrNull { it.quality <= preferredQn } ?: allowed.last()
}

/**
 * Audio fallback order. Auto uses 192K (the best normal AAC track) instead of
 * the much larger FLAC, matching the previous first-track behavior.
 *
 * Tracks the device cannot decode (typically Dolby E-AC-3 on handhelds without
 * a licensed decoder) are skipped; otherwise selecting one produces a silent
 * video because ExoPlayer has no matching audio renderer.
 */
internal fun pickDefaultAudio(
    tracks: List<AudioTrackOption>,
    preferredId: Int,
    isVip: Boolean?,
): AudioTrackOption? {
    if (tracks.isEmpty()) return null
    val playable = tracks.filter { it.supported }
    if (playable.isEmpty()) return tracks.first()
    val allowed = playable.filter { !it.vipOnly || isVip != false }
    if (allowed.isEmpty()) return playable.first()
    if (preferredId <= 0) {
        return allowed.firstOrNull { it.id == 30280 }
            ?: allowed.firstOrNull { it.id in setOf(30216, 30232) }
            ?: allowed.first()
    }
    val order = listOf(30251, 30250, 30280, 30232, 30216)
    val start = order.indexOf(preferredId)
    if (start >= 0) {
        for (i in start until order.size) {
            allowed.firstOrNull { it.id == order[i] }?.let { return it }
        }
    }
    return allowed.firstOrNull { it.id == preferredId } ?: allowed.first()
}

/**
 * MIME type that a platform decoder must support for this DASH audio stream.
 * Returns null for codecs every Android device handles (AAC etc.).
 */
internal fun requiredAudioDecoderMime(
    trackId: Int,
    mimeType: String,
    codecs: String,
): String? {
    val codec = codecs.lowercase()
    val mime = mimeType.lowercase()
    return when {
        trackId == 30250 ||
            codec.contains("ec-3") || codec.contains("eac3") || codec.contains("ec3") ||
            mime.contains("eac3") -> "audio/eac3"
        codec.contains("ac-3") || codec.contains("ac3") || mime.contains("ac3") -> "audio/ac3"
        trackId == 30251 || codec.contains("flac") || mime.contains("flac") -> "audio/flac"
        else -> null
    }
}
