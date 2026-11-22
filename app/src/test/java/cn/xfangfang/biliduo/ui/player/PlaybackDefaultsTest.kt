package cn.xfangfang.biliduo.ui.player

import cn.xfangfang.biliduo.data.model.AudioTrackOption
import cn.xfangfang.biliduo.data.model.DashStream
import cn.xfangfang.biliduo.data.model.QualityOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackDefaultsTest {

    private val qualities = listOf(
        QualityOption(125, "HDR", vipOnly = true),
        QualityOption(120, "4K", vipOnly = true),
        QualityOption(116, "1080P60", vipOnly = true),
        QualityOption(112, "1080P+", vipOnly = true),
        QualityOption(80, "1080P"),
        QualityOption(74, "720P60"),
        QualityOption(64, "720P"),
        QualityOption(32, "480P"),
        QualityOption(16, "360P"),
    )

    private fun track(
        id: Int,
        vip: Boolean = false,
        supported: Boolean = true,
    ) = AudioTrackOption(
        id = id,
        label = id.toString(),
        stream = DashStream(id = id),
        vipOnly = vip,
        supported = supported,
    )

    @Test
    fun autoQualityStaysOff4kAndHdr() {
        assertEquals(116, pickDefaultQuality(qualities, 0, isVip = true)?.quality)
    }

    @Test
    fun explicitHdrIsUsedWhenAllowed() {
        assertEquals(125, pickDefaultQuality(qualities, 125, isVip = true)?.quality)
    }

    @Test
    fun nonVipExplicitHdrFallsToBestFreeTier() {
        assertEquals(80, pickDefaultQuality(qualities, 125, isVip = false)?.quality)
    }

    @Test
    fun missingPreferredQualityFallsToNextLowerTier() {
        val without1080 = qualities.filter { it.quality != 80 }
        assertEquals(74, pickDefaultQuality(without1080, 80, isVip = true)?.quality)
    }

    @Test
    fun preferredBelowEveryAvailableTierUsesLowest() {
        val onlyHigh = qualities.filter { it.quality >= 64 }
        assertEquals(64, pickDefaultQuality(onlyHigh, 16, isVip = true)?.quality)
    }

    @Test
    fun autoAudioUses192k() {
        val tracks = listOf(
            track(30251, vip = true),
            track(30250, vip = true),
            track(30280),
            track(30232),
            track(30216),
        )
        assertEquals(30280, pickDefaultAudio(tracks, 0, isVip = true)?.id)
    }

    @Test
    fun explicitFlacIsUsedWhenAllowed() {
        val tracks = listOf(track(30251, vip = true), track(30280))
        assertEquals(30251, pickDefaultAudio(tracks, 30251, isVip = true)?.id)
    }

    @Test
    fun nonVipFlacFallsBackTo192k() {
        val tracks = listOf(
            track(30251, vip = true),
            track(30250, vip = true),
            track(30280),
        )
        assertEquals(30280, pickDefaultAudio(tracks, 30251, isVip = false)?.id)
    }

    @Test
    fun missingDolbyFallsBackTo192k() {
        val tracks = listOf(track(30280), track(30232), track(30216))
        assertEquals(30280, pickDefaultAudio(tracks, 30250, isVip = true)?.id)
    }

    @Test
    fun missing132kFallsBackTo64k() {
        val tracks = listOf(track(30280), track(30216))
        assertEquals(30216, pickDefaultAudio(tracks, 30232, isVip = true)?.id)
    }

    @Test
    fun emptyTracksYieldNull() {
        assertNull(pickDefaultAudio(emptyList(), 0, isVip = true))
    }

    @Test
    fun unsupportedDolbyIsSkippedForExplicitDefault() {
        val tracks = listOf(
            track(30250, vip = true, supported = false),
            track(30280),
            track(30232),
        )
        assertEquals(30280, pickDefaultAudio(tracks, 30250, isVip = true)?.id)
    }

    @Test
    fun unsupportedDolbyIsSkippedForAuto() {
        val tracks = listOf(
            track(30251, vip = true),
            track(30250, vip = true, supported = false),
            track(30280),
        )
        assertEquals(30280, pickDefaultAudio(tracks, 0, isVip = true)?.id)
    }

    @Test
    fun dolbyAndFlacRequirePlatformDecoders() {
        assertEquals("audio/eac3", requiredAudioDecoderMime(30250, "audio/mp4", "ec-3"))
        assertEquals("audio/eac3", requiredAudioDecoderMime(0, "audio/eac3", ""))
        assertEquals("audio/ac3", requiredAudioDecoderMime(0, "", "ac-3"))
        assertEquals("audio/flac", requiredAudioDecoderMime(30251, "", "flac"))
        assertNull(requiredAudioDecoderMime(30280, "audio/mp4", "mp4a.40.2"))
    }
}
