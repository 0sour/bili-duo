package cn.xfangfang.biliduo.ui.player

import cn.xfangfang.biliduo.data.model.PgcSkip
import cn.xfangfang.biliduo.data.model.PgcSkipRange
import cn.xfangfang.biliduo.data.model.PlaybackSkipRange
import cn.xfangfang.biliduo.data.model.VideoViewPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSkipTest {

    @Test
    fun `ugc view points convert seconds to millis`() {
        val points = listOf(
            VideoViewPoint(type = 1, from = 0.0, to = 90.5, content = "OP"),
            VideoViewPoint(type = 2, from = 1200.0, to = 1230.0, content = "ED"),
        )

        assertEquals(
            listOf(
                PlaybackSkipRange(kind = 1, startMs = 0L, endMs = 90_500L),
                PlaybackSkipRange(kind = 2, startMs = 1_200_000L, endMs = 1_230_000L),
            ),
            points.toPlaybackSkipRanges(),
        )
    }

    @Test
    fun `ugc view points ignore unknown types and short ranges`() {
        val points = listOf(
            VideoViewPoint(type = 0, from = 0.0, to = 30.0),
            VideoViewPoint(type = 3, from = 0.0, to = 30.0),
            VideoViewPoint(type = 1, from = 10.0, to = 10.5),
            VideoViewPoint(type = 2, from = 20.0, to = 20.0),
        )

        assertTrue(points.toPlaybackSkipRanges().isEmpty())
    }

    @Test
    fun `pgc skip converts opening and ending`() {
        val skip = PgcSkip(
            op = PgcSkipRange(start = 5, end = 95),
            ed = PgcSkipRange(start = 1300, end = 1330),
        )

        assertEquals(
            listOf(
                PlaybackSkipRange(kind = 1, startMs = 5_000L, endMs = 95_000L),
                PlaybackSkipRange(kind = 2, startMs = 1_300_000L, endMs = 1_330_000L),
            ),
            skip.toPlaybackSkipRanges(),
        )
    }

    @Test
    fun `pgc skip ignores unset and short ranges`() {
        val skip = PgcSkip(
            op = PgcSkipRange(start = 0, end = 0),
            ed = PgcSkipRange(start = 100, end = 100),
        )

        assertTrue(skip.toPlaybackSkipRanges().isEmpty())
        assertTrue((null as PgcSkip?).toPlaybackSkipRanges().isEmpty())
    }
}
