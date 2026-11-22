package cn.xfangfang.biliduo.ui.player

import android.media.MediaCodecList
import cn.xfangfang.biliduo.data.model.AudioTrackOption
import cn.xfangfang.biliduo.data.model.DashStream

/**
 * Platform decoder lookup for DASH audio tracks.
 *
 * Dolby streams are E-AC-3; many handhelds (including the Retroid Pocket Duo)
 * ship no E-AC-3 decoder. ExoPlayer then has no audio renderer for the track,
 * so the video plays silently. Mark those tracks unsupported instead of
 * offering a selection that cannot produce sound.
 */
object AudioCapabilities {

    private val decoderTypes: Set<String> by lazy {
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .asSequence()
                .filter { !it.isEncoder }
                .flatMap { it.supportedTypes.asSequence() }
                .map { it.lowercase() }
                .toSet()
        }.getOrDefault(emptySet())
    }

    fun supports(track: AudioTrackOption): Boolean = supports(track.id, track.stream)

    fun supports(trackId: Int, stream: DashStream): Boolean {
        val required = requiredAudioDecoderMime(
            trackId,
            stream.mimeType.ifBlank { stream.mime_type },
            stream.codecs,
        ) ?: return true
        // E-AC-3 JOC (Atmos) streams still decode their E-AC-3 bed with an
        // audio/eac3 decoder. Conversely, an AC-3-only decoder cannot handle
        // E-AC-3, and the reverse is also uncommon.
        return decoderTypes.any { type ->
            type == required || (required == "audio/eac3" && type == "audio/eac3-joc")
        }
    }
}
