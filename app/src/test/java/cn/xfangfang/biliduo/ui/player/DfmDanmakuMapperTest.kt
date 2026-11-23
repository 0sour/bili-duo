package cn.xfangfang.biliduo.ui.player

import cn.xfangfang.biliduo.data.model.DanmakuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DfmDanmakuMapperTest {

    private fun item(
        timeMs: Long = 1_000L,
        mode: Int = 1,
        fontSize: Float = 25f,
        color: Int = 0xFFFFFF,
        text: String = "danmaku",
        weight: Int = 0,
        uidHash: String = "",
    ) = DanmakuItem(timeMs, mode, fontSize, color, text, weight, uidHash)

    @Test
    fun sameTimestampItemsAllKeepUniqueIndexes() {
        val items = listOf(
            item(timeMs = 1_000L, text = "a"),
            item(timeMs = 1_000L, text = "b"),
            item(timeMs = 1_000L, text = "c"),
        )

        val specs = mapDanmakuItems(items, viewHeightPx = 1080, textSizeSp = 16f)

        assertEquals(3, specs.size)
        assertEquals(listOf(0, 1, 2), specs.map { it.index })
        // DFM's TreeSet comparator needs distinct indexes for equal times.
        assertEquals(3, specs.map { it.timeMs to it.index }.toSet().size)
    }

    @Test
    fun modesMapToDfmTypes() {
        val specs = mapDanmakuItems(
            listOf(
                item(timeMs = 1, mode = 1),
                item(timeMs = 2, mode = 4),
                item(timeMs = 3, mode = 5),
                item(timeMs = 4, mode = 6),
            ),
            viewHeightPx = 1080,
            textSizeSp = 16f,
        )

        assertEquals(
            listOf(
                DfmDanmakuType.SCROLL_RL,
                DfmDanmakuType.FIX_BOTTOM,
                DfmDanmakuType.FIX_TOP,
                DfmDanmakuType.SCROLL_LR,
            ),
            specs.map { it.type },
        )
        assertEquals(4_000L, specs[1].fixedDurationMs)
        assertEquals(4_000L, specs[2].fixedDurationMs)
        assertNull(specs[0].fixedDurationMs)
        assertNull(specs[3].fixedDurationMs)
    }

    @Test
    fun fontSizeIsClampedAroundTheBase() {
        val base = danmakuBaseTextPx(1080, 16f)
        val specs = mapDanmakuItems(
            listOf(item(fontSize = 1f), item(fontSize = 25f), item(fontSize = 100f)),
            viewHeightPx = 1080,
            textSizeSp = 16f,
        )

        assertEquals(base * 0.75f, specs[0].textSizePx, 0.01f)
        assertEquals(base, specs[1].textSizePx, 0.01f)
        assertEquals(base * 1.3f, specs[2].textSizePx, 0.01f)
    }

    @Test
    fun colorsAndAuthorHashAreCarried() {
        val spec = mapDanmakuItems(
            listOf(item(color = 0x123456, uidHash = "abc")),
            viewHeightPx = 1080,
            textSizeSp = 16f,
        ).single()

        assertEquals(0xFF123456.toInt(), spec.textColor)
        assertEquals(0xFF000000.toInt(), spec.shadowColor)
        assertEquals("abc", spec.userHash)
    }

    @Test
    fun existingFiltersStillApply() {
        val items = listOf(
            item(text = "keep", weight = 5),
            item(text = "blocked", weight = 5),
            item(text = "low", weight = 1),
            item(text = "bottom", mode = 4, weight = 5),
        )

        val specs = mapDanmakuItems(
            items = items,
            viewHeightPx = 1080,
            textSizeSp = 16f,
            blockedWords = setOf("BLOCK"),
            shieldLevel = 3,
            showBottom = false,
        )

        assertEquals(listOf("keep"), specs.map { it.text })
    }

    @Test
    fun scrollFactorInvertsSpeedAndTargetsEightSeconds() {
        val atNormal = danmakuScrollSpeedFactor(1f, 9_000L)
        val slower = danmakuScrollSpeedFactor(0.7f, 9_000L)
        val faster = danmakuScrollSpeedFactor(1.8f, 9_000L)

        assertEquals(8_000f / 9_000f, atNormal, 0.0001f)
        assertTrue(slower > atNormal)
        assertTrue(faster < atNormal)
        assertTrue(slower > 1f)
    }

    @Test
    fun baseTextSizeTracksViewHeightAndSetting() {
        assertTrue(danmakuBaseTextPx(1080, 16f) > danmakuBaseTextPx(720, 16f))
        assertTrue(danmakuBaseTextPx(1080, 24f) > danmakuBaseTextPx(1080, 16f))
    }
}
