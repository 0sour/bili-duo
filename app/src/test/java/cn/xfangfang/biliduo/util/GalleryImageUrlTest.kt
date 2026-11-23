package cn.xfangfang.biliduo.util

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryImageUrlTest {

    @Test
    fun `bilibili url is capped at 1600w`() {
        assertEquals(
            "https://i0.hdslb.com/bfs/new_dyn/abc.jpg@1600w.webp",
            galleryImageUrl("https://i0.hdslb.com/bfs/new_dyn/abc.jpg"),
        )
    }

    @Test
    fun `protocol relative url is normalized`() {
        assertEquals(
            "https://i0.hdslb.com/bfs/new_dyn/abc.jpg@1600w.webp",
            galleryImageUrl("//i0.hdslb.com/bfs/new_dyn/abc.jpg"),
        )
    }

    @Test
    fun `already processed url is not rewritten`() {
        val url = "https://i0.hdslb.com/bfs/new_dyn/abc.jpg@640w.webp"
        assertEquals(url, galleryImageUrl(url))
    }

    @Test
    fun `non bilibili url is unchanged`() {
        val url = "https://example.com/picture.jpg"
        assertEquals(url, galleryImageUrl(url))
    }
}
