package cn.xfangfang.biliduo.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageZoomStateTest {

    private fun state(): ImageZoomState = ImageZoomState().apply {
        updateViewport(IntSize(1000, 800))
    }

    @Test
    fun `zoom in raises scale and keeps the center fixed`() {
        val zoom = state()
        zoom.zoomBy(1.25f)
        assertEquals(1.25f, zoom.scale, 0.001f)
        assertEquals(0f, zoom.offset.x, 0.001f)
        assertEquals(0f, zoom.offset.y, 0.001f)
    }

    @Test
    fun `zoom out clamps to one and clears the offset`() {
        val zoom = state()
        zoom.zoomBy(2f)
        zoom.panBy(Offset(300f, 200f))
        zoom.zoomBy(0.1f)
        assertEquals(1f, zoom.scale, 0.001f)
        assertEquals(Offset.Zero, zoom.offset)
    }

    @Test
    fun `pan is clamped to the scaled overflow`() {
        val zoom = state()
        zoom.zoomBy(2f)
        zoom.panBy(Offset(10_000f, 10_000f))
        assertEquals(500f, zoom.offset.x, 0.01f)
        assertEquals(400f, zoom.offset.y, 0.01f)
    }

    @Test
    fun `touch pan at one-to-one scale is ignored`() {
        val zoom = state()
        zoom.applyGesture(Offset(500f, 400f), Offset(120f, 80f), 1f)
        assertEquals(1f, zoom.scale, 0.001f)
        assertEquals(Offset.Zero, zoom.offset)
    }
}
