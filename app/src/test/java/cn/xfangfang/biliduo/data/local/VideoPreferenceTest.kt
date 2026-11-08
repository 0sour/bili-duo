package cn.xfangfang.biliduo.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPreferenceTest {

    @Test
    fun `quality labels use the same options shown in settings`() {
        assertEquals("自动", videoQualityLabel(0))
        assertEquals("1080P60", videoQualityLabel(116))
        assertEquals("720P", videoQualityLabel(64))
        assertEquals("自动", videoQualityLabel(404))
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
