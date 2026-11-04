package cn.xfangfang.biliduo.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlayResultTest {

    private fun codec(
        name: String = "avc",
        currentQn: Int = 10000,
        acceptQn: List<Int> = listOf(10000, 400, 250),
        baseUrl: String = "/live/index.m3u8?",
        host: String = "https://cdn.example",
        extra: String = "token=abc",
    ) = LiveCodecInfo(
        codecName = name,
        currentQn = currentQn,
        acceptQn = acceptQn,
        baseUrl = baseUrl,
        urlInfo = listOf(LiveUrlInfo(host = host, extra = extra)),
    )

    @Test
    fun prefersTsHlsOverFmp4AndFlv() {
        val result = LivePlayurl(
            qualityDescriptions = listOf(
                LiveQualityDesc(10000, "原画"),
                LiveQualityDesc(400, "蓝光"),
                LiveQualityDesc(250, "高清"),
            ),
            stream = listOf(
                LiveStreamInfo(
                    protocolName = "http_stream",
                    format = listOf(LiveFormatInfo("flv", listOf(codec(baseUrl = "/live.flv?")))),
                ),
                LiveStreamInfo(
                    protocolName = "http_hls",
                    format = listOf(
                        LiveFormatInfo("fmp4", listOf(codec(baseUrl = "/fmp4/index.m3u8?"))),
                        LiveFormatInfo("ts", listOf(codec(baseUrl = "/ts/index.m3u8?"))),
                    ),
                ),
            ),
        ).selectLivePlayResult(liveStatus = 1)

        assertEquals("https://cdn.example/ts/index.m3u8?token=abc", result?.url)
        assertTrue(result?.isHls == true)
        assertEquals("原画", result?.currentQuality?.description)
        assertEquals(listOf(10000, 400, 250), result?.qualities?.map { it.quality })
    }

    @Test
    fun fallsBackToFlvWhenHlsIsMissing() {
        val result = LivePlayurl(
            stream = listOf(
                LiveStreamInfo(
                    protocolName = "http_stream",
                    format = listOf(
                        LiveFormatInfo(
                            "flv",
                            listOf(codec(baseUrl = "/live.flv?", currentQn = 250)),
                        )
                    ),
                )
            ),
        ).selectLivePlayResult()

        assertEquals("https://cdn.example/live.flv?token=abc", result?.url)
        assertFalse(result?.isHls == true)
        assertEquals(250, result?.currentQuality?.quality)
        assertEquals("高清", result?.currentQuality?.description)
    }

    @Test
    fun prefersAvcCodecWithinAFormat() {
        val result = LivePlayurl(
            stream = listOf(
                LiveStreamInfo(
                    protocolName = "http_hls",
                    format = listOf(
                        LiveFormatInfo(
                            "fmp4",
                            listOf(
                                codec(name = "hevc", baseUrl = "/hevc.m3u8?"),
                                codec(name = "avc", baseUrl = "/avc.m3u8?"),
                            ),
                        )
                    ),
                )
            ),
        ).selectLivePlayResult()

        assertEquals("https://cdn.example/avc.m3u8?token=abc", result?.url)
    }

    @Test
    fun excludesDolbyQualityAndUsesFallbackNames() {
        val result = LivePlayurl(
            qualityDescriptions = listOf(LiveQualityDesc(10000, "原画")),
            stream = listOf(
                LiveStreamInfo(
                    protocolName = "http_hls",
                    format = listOf(
                        LiveFormatInfo(
                            "fmp4",
                            listOf(codec(acceptQn = listOf(30000, 10000, 15000, 150))),
                        )
                    ),
                )
            ),
        ).selectLivePlayResult()

        assertEquals(listOf(15000, 10000, 150), result?.qualities?.map { it.quality })
        assertEquals("2K", result?.qualities?.first { it.quality == 15000 }?.description)
        assertEquals("流畅", result?.qualities?.first { it.quality == 150 }?.description)
    }

    @Test
    fun returnsNullWhenStreamsAreEmpty() {
        assertNull(null.selectLivePlayResult())
        assertNull(LivePlayurl(stream = emptyList()).selectLivePlayResult())
    }
}
