package cn.xfangfang.biliduo.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageUrlsTest {

    @Test
    fun `cover uses the next width bucket and webp`() {
        assertEquals(
            "https://i1.hdslb.com/bfs/archive/x.jpg@480w.webp",
            bilibiliThumbnailUrl("https://i1.hdslb.com/bfs/archive/x.jpg", 360),
        )
    }

    @Test
    fun `avatar uses a small bucket`() {
        assertEquals(
            "https://i2.hdslb.com/bfs/face/x.jpg@160w.webp",
            bilibiliThumbnailUrl("https://i2.hdslb.com/bfs/face/x.jpg", 128),
        )
    }

    @Test
    fun `query is preserved after the resize suffix`() {
        assertEquals(
            "https://i0.hdslb.com/bfs/x.jpg@240w.webp?foo=1",
            bilibiliThumbnailUrl("https://i0.hdslb.com/bfs/x.jpg?foo=1", 200),
        )
    }

    @Test
    fun `http is upgraded to https`() {
        assertEquals(
            "https://i0.hdslb.com/bfs/x.jpg@320w.webp",
            bilibiliThumbnailUrl("http://i0.hdslb.com/bfs/x.jpg", 300),
        )
    }

    @Test
    fun `already processed animated and foreign urls are untouched`() {
        assertNull(bilibiliThumbnailUrl("https://i0.hdslb.com/bfs/x.jpg@1280w.webp", 320))
        assertNull(bilibiliThumbnailUrl("https://i0.hdslb.com/bfs/x.gif", 320))
        assertNull(bilibiliThumbnailUrl("https://example.com/x.jpg", 320))
        assertNull(bilibiliThumbnailUrl("", 320))
        assertNull(bilibiliThumbnailUrl("https://i0.hdslb.com/bfs/x.jpg", 0))
    }
}
