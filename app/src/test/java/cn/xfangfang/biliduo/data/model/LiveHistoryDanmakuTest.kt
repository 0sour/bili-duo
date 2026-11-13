package cn.xfangfang.biliduo.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveHistoryDanmakuTest {

    private val gson = Gson()

    @Test
    fun parsesRoomMessagesWithMedalAndNegativeIds() {
        val data = gson.fromJson(
            """
            {
              "room": [
                {
                  "text": "历史弹幕",
                  "uid": 42,
                  "nickname": "历史用户",
                  "isadmin": 1,
                  "ctime": 1700000000,
                  "medal": [18, "粉丝牌", "主播", 0, 123]
                }
              ],
              "admin": []
            }
            """.trimIndent(),
            LiveHistoryDanmakuData::class.java,
        )

        val message = data.room.orEmpty()
            .filter { !it.text.isNullOrBlank() }
            .mapIndexed { index, item -> item.toLiveChatMessage(index) }
            .single()

        assertEquals(-1L, message.id)
        assertEquals("历史用户", message.uname)
        assertEquals("历史弹幕", message.text)
        assertEquals("粉丝牌 18", message.medal)
        assertEquals(42L, message.uid)
        assertTrue(message.isAdmin)
        assertEquals(1700000000L, message.timestampSec)
    }

    @Test
    fun missingFieldsFallBackToAnonymous() {
        val data = gson.fromJson(
            """{"room":[{"text":"只有文本"}]}""",
            LiveHistoryDanmakuData::class.java,
        )

        val message = data.room.orEmpty().single().toLiveChatMessage(2)

        assertEquals(-3L, message.id)
        assertEquals("匿名用户", message.uname)
        assertEquals("只有文本", message.text)
        assertNull(message.medal)
        assertEquals(0L, message.uid)
        assertFalse(message.isAdmin)
    }
}
