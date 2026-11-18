package cn.xfangfang.biliduo.ui.player

import cn.xfangfang.biliduo.data.model.DanmakuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuFilterTest {

    private fun item(
        text: String,
        time: Long = 0L,
        mode: Int = 1,
        weight: Int = 0,
        uidHash: String = "",
    ) = DanmakuItem(
        timeMs = time,
        mode = mode,
        fontSize = 25f,
        color = 0xFFFFFF,
        text = text,
        weight = weight,
        uidHash = uidHash,
    )

    @Test
    fun parse_supports_mixed_separators_and_trims() {
        val words = parseBlockedDanmakuWords(" 前排, 广告，刷屏;  引战 | 剧透\n ")
        assertEquals(setOf("前排", "广告", "刷屏", "引战", "剧透"), words)
    }

    @Test
    fun parse_drops_empty_and_limits_to_50() {
        assertTrue(parseBlockedDanmakuWords(" , ， ; ").isEmpty())
        val many = (1..80).joinToString(",") { "w$it" }
        assertEquals(50, parseBlockedDanmakuWords(many).size)
    }

    @Test
    fun filter_is_case_insensitive_substring_match() {
        val items = listOf(item("前方高能"), item("Buy now"), item("普通弹幕"))
        val result = filterBlockedDanmaku(items, setOf("高能", "BUY"))
        assertEquals(listOf("普通弹幕"), result.map { it.text })
    }

    @Test
    fun filter_with_no_words_returns_same_list() {
        val items = listOf(item("a"), item("b"))
        assertTrue(filterBlockedDanmaku(items, emptySet()) === items)
    }

    @Test
    fun on_screen_limit_zero_means_unlimited() {
        assertTrue(limitDanmakuOnScreen(count = 999, maxOnScreen = 0))
        assertTrue(limitDanmakuOnScreen(count = 0, maxOnScreen = 30))
        assertTrue(limitDanmakuOnScreen(count = 29, maxOnScreen = 30))
        assertFalse(limitDanmakuOnScreen(count = 30, maxOnScreen = 30))
    }

    @Test
    fun shield_level_keeps_weight_at_or_above_level() {
        val items = listOf(
            item("w0", weight = 0),
            item("w3", weight = 3),
            item("w7", weight = 7),
            item("w10", weight = 10),
        )
        assertEquals(
            listOf("w3", "w7", "w10"),
            filterDanmaku(items, emptySet(), null, 3, true, true, true).map { it.text },
        )
        assertEquals(items, filterDanmaku(items, emptySet(), null, 0, true, true, true))
        assertEquals(
            listOf("w10"),
            filterDanmaku(items, emptySet(), null, 10, true, true, true).map { it.text },
        )
    }

    @Test
    fun type_switches_filter_scroll_top_and_bottom() {
        val items = listOf(
            item("scroll", mode = 1),
            item("top", mode = 5),
            item("bottom", mode = 4),
        )
        assertEquals(
            listOf("scroll", "bottom"),
            filterDanmaku(items, emptySet(), null, 0, true, false, true).map { it.text },
        )
        assertEquals(
            listOf("top"),
            filterDanmaku(items, emptySet(), null, 0, false, true, false).map { it.text },
        )
    }

    @Test
    fun regex_is_applied_and_invalid_patterns_are_ignored() {
        val items = listOf(item("前方高能"), item("666666"), item("普通弹幕"))
        assertEquals(
            listOf("前方高能", "普通弹幕"),
            filterDanmaku(items, emptySet(), compileDanmakuRegex("^\\d+$"), 0, true, true, true)
                .map { it.text },
        )
        assertEquals(items, filterDanmaku(items, emptySet(), compileDanmakuRegex("["), 0, true, true, true))
    }

    @Test
    fun blocked_author_hashes_hide_only_that_author() {
        val items = listOf(
            item("a1", uidHash = "hash-a"),
            item("a2", uidHash = "hash-a"),
            item("b1", uidHash = "hash-b"),
            item("no-hash"),
        )
        val result = filterDanmaku(
            items = items,
            blockedWords = emptySet(),
            blockedRegex = null,
            shieldLevel = 0,
            showScroll = true,
            showTop = true,
            showBottom = true,
            blockedUidHashes = setOf("hash-a"),
        )
        assertEquals(listOf("b1", "no-hash"), result.map { it.text })
        assertEquals(
            items,
            filterDanmaku(items, emptySet(), null, 0, true, true, true, setOf("")),
        )
    }
}
