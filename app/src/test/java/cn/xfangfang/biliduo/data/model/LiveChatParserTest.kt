package cn.xfangfang.biliduo.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveChatParserTest {

    private var nextId = 0L
    private fun id() = ++nextId

    @Test
    fun parsesDanmakuTextUserAndMedal() {
        val events = LiveChatParser.parse(
            """
            {
              "cmd": "DANMU_MSG:4:0:2:2:2:0",
              "info": [
                [0, 1, 25, 16777215, 1700000000, 0, 0, "", 0],
                "前方高能",
                [12345, "测试用户", 1],
                [21, "粉丝牌", "主播", 1, 0, 0]
              ]
            }
            """.trimIndent(),
            ::id,
        )

        val message = (events.single() as LiveChatEvent.Message).message
        assertEquals("测试用户", message.uname)
        assertEquals("前方高能", message.text)
        assertEquals("粉丝牌 21", message.medal)
        assertTrue(message.isAdmin)
        assertEquals(12345L, message.uid)
        assertEquals(1700000000L, message.timestampSec)
    }

    @Test
    fun parsesSuperChatPriceAndColor() {
        val events = LiveChatParser.parse(
            """
            {
              "cmd": "SUPER_CHAT_MESSAGE",
              "data": {
                "id": 987654,
                "price": 30,
                "message": "老板大气",
                "background_bottom_color": "#ED4B4B",
                "ts": 1700000001,
                "user_info": {
                  "uid": 777,
                  "uname": "老板",
                  "face": "https://example/face.jpg"
                }
              }
            }
            """.trimIndent(),
            ::id,
        )

        val message = (events.single() as LiveChatEvent.Message).message
        assertTrue(message.isSuperChat)
        assertEquals(987654L, message.superChatId)
        assertEquals(30, message.price)
        assertEquals("老板大气", message.text)
        assertEquals("老板", message.uname)
        assertEquals(0xFFED4B4BL, message.background)
    }

    @Test
    fun parsesSuperChatDeleteByIdAndIds() {
        val single = LiveChatParser.parse(
            """{"cmd":"SUPER_CHAT_MESSAGE_DELETE","data":{"id":42}}""",
            ::id,
        )
        assertEquals(
            listOf(LiveChatEvent.DeleteSuperChat(42L)),
            single,
        )

        val multiple = LiveChatParser.parse(
            """{"cmd":"SUPER_CHAT_MESSAGE_DELETE","data":{"ids":[1,2,3]}}""",
            ::id,
        )
        assertEquals(
            listOf(
                LiveChatEvent.DeleteSuperChat(1L),
                LiveChatEvent.DeleteSuperChat(2L),
                LiveChatEvent.DeleteSuperChat(3L),
            ),
            multiple,
        )
    }

    @Test
    fun ignoresUnrelatedCommandsAndMalformedJson() {
        assertTrue(LiveChatParser.parse("""{"cmd":"INTERACT_WORD"}""", ::id).isEmpty())
        assertTrue(LiveChatParser.parse("""{"cmd":"DANMU_MSG"}""", ::id).isEmpty())
        assertTrue(LiveChatParser.parse("not json", ::id).isEmpty())
        assertTrue(LiveChatParser.parse("""{"cmd":"SUPER_CHAT_MESSAGE_JPN","data":{"id":1}}""", ::id).isEmpty())
    }

    @Test
    fun validatesLiveChatLengthAndNewlines() {
        assertNull(validateLiveChatText("  前方高能  "))
        assertEquals("弹幕不能为空", validateLiveChatText("  "))
        assertEquals("弹幕不能包含换行", validateLiveChatText("a\nb"))
        assertEquals(
            "弹幕最多 20 个字符",
            validateLiveChatText("弹".repeat(21)),
        )
        assertNull(validateLiveChatText("弹".repeat(20)))
        assertEquals(1, liveChatLength("😀"))
    }

    @Test
    fun parsesHexColors() {
        assertEquals(0xFFFFCC00L, parseLiveColor("#FFCC00"))
        assertEquals(0x80FFCC00L, parseLiveColor("#80FFCC00"))
        assertNull(parseLiveColor("gold"))
        assertEquals(1, liveChatLength("😀"))
    }
}
