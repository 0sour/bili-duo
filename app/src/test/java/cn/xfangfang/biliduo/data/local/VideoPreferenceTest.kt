package cn.xfangfang.biliduo.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPreferenceTest {

    @Test
    fun `quality labels use the same options shown in settings`() {
        assertEquals("自动", videoQualityLabel(0))
        assertEquals("HDR", videoQualityLabel(125))
        assertEquals("4K", videoQualityLabel(120))
        assertEquals("1080P60", videoQualityLabel(116))
        assertEquals("720P", videoQualityLabel(64))
        assertEquals("自动", videoQualityLabel(404))
    }

    @Test
    fun `hdr and 4k are vip marked, lower tiers stay free`() {
        assertEquals(true, videoQualityOptions.first { it.qn == 125 }.vip)
        assertEquals(true, videoQualityOptions.first { it.qn == 120 }.vip)
        assertEquals(false, videoQualityOptions.first { it.qn == 80 }.vip)
    }

    @Test
    fun `audio labels cover lossless dolby and aac tiers`() {
        assertEquals("自动", audioQualityLabel(0))
        assertEquals("无损", audioQualityLabel(30251))
        assertEquals("杜比", audioQualityLabel(30250))
        assertEquals("192K", audioQualityLabel(30280))
        assertEquals("132K", audioQualityLabel(30232))
        assertEquals("64K", audioQualityLabel(30216))
        assertEquals(true, audioQualityOptions.first { it.id == 30251 }.vip)
        assertEquals(true, audioQualityOptions.first { it.id == 30250 }.vip)
        assertEquals(false, audioQualityOptions.first { it.id == 30280 }.vip)
    }

    @Test
    fun `speed labels keep one or two decimals without trailing zeros`() {
        assertEquals("1x", videoSpeedLabel(1f))
        assertEquals("1.25x", videoSpeedLabel(1.25f))
        assertEquals("0.75x", videoSpeedLabel(0.75f))
    }

    @Test
    fun `subtitle style labels map the persisted values`() {
        assertEquals("标准", videoSubtitleSizeLabel(22f))
        assertEquals("特大", videoSubtitleSizeLabel(30f))
        assertEquals("无", videoSubtitleBackgroundLabel(0f))
        assertEquals("深", videoSubtitleBackgroundLabel(0.8f))
        assertEquals("靠下", videoSubtitlePositionLabel(32))
        assertEquals("靠上", videoSubtitlePositionLabel(90))
    }
}
