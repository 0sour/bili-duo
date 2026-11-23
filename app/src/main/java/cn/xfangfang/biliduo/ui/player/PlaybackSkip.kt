package cn.xfangfang.biliduo.ui.player

import cn.xfangfang.biliduo.data.model.PgcSkip
import cn.xfangfang.biliduo.data.model.PlaybackSkipRange
import cn.xfangfang.biliduo.data.model.VideoViewPoint

/** A marker shorter than this is treated as unset/placeholder. */
private const val MIN_SKIP_MS = 1_000L

/**
 * UGC `player/wbi/v2` view points. [VideoViewPoint.from]/[VideoViewPoint.to]
 * are seconds from the start; types 1/2 are opening/ending.
 */
internal fun List<VideoViewPoint>.toPlaybackSkipRanges(): List<PlaybackSkipRange> =
    mapNotNull { point ->
        val startMs = (point.from * 1000.0).toLong()
        val endMs = (point.to * 1000.0).toLong()
        if (point.type !in 1..2 || endMs <= startMs + MIN_SKIP_MS) {
            null
        } else {
            PlaybackSkipRange(kind = point.type, startMs = startMs, endMs = endMs)
        }
    }

/** PGC season episodes carry `skip.op` / `skip.ed` ranges in whole seconds. */
internal fun PgcSkip?.toPlaybackSkipRanges(): List<PlaybackSkipRange> {
    if (this == null) return emptyList()
    return buildList {
        op?.let { range ->
            if (range.end * 1000L > range.start * 1000L + MIN_SKIP_MS) {
                add(
                    PlaybackSkipRange(
                        kind = 1,
                        startMs = range.start * 1000L,
                        endMs = range.end * 1000L,
                    ),
                )
            }
        }
        ed?.let { range ->
            if (range.end * 1000L > range.start * 1000L + MIN_SKIP_MS) {
                add(
                    PlaybackSkipRange(
                        kind = 2,
                        startMs = range.start * 1000L,
                        endMs = range.end * 1000L,
                    ),
                )
            }
        }
    }
}
