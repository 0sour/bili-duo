package cn.xfangfang.biliduo.ui.player

import cn.xfangfang.biliduo.data.model.DanmakuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuFilterTest {

    private fun item(text: String, time: Long = 0L) = DanmakuItem(
        timeMs = time,
        mode = 1,
        fontSize = 25f,
        color = 0xFFFFFF,
        text = text,
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
}
