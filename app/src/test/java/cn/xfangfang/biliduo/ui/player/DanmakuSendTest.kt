package cn.xfangfang.biliduo.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DanmakuSendTest {

    @Test
    fun acceptsNormalTextAndTrimsWhitespace() {
        assertNull(validateDanmakuText("  前方高能  "))
    }

    @Test
    fun rejectsBlankText() {
        assertEquals("弹幕不能为空", validateDanmakuText("   "))
    }

    @Test
    fun rejectsNewlines() {
        assertEquals("弹幕不能包含换行", validateDanmakuText("第一行\n第二行"))
        assertEquals("弹幕不能包含换行", validateDanmakuText("第一行\r第二行"))
    }

    @Test
    fun rejectsTextOverOneHundredCharacters() {
        assertEquals(
            "弹幕最多 100 个字符",
            validateDanmakuText("弹".repeat(DANMAKU_MAX_LENGTH + 1)),
        )
    }

    @Test
    fun acceptsExactlyOneHundredCharacters() {
        assertNull(validateDanmakuText("弹".repeat(DANMAKU_MAX_LENGTH)))
    }

    @Test
    fun countsEmojiAsOneCharacter() {
        assertEquals(1, danmakuLength("😀"))
        assertNull(validateDanmakuText("😀".repeat(DANMAKU_MAX_LENGTH)))
    }
}
