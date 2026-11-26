package cn.xfangfang.biliduo.ui.messages

import cn.xfangfang.biliduo.data.model.MessageFeedKind
import cn.xfangfang.biliduo.data.model.MsgFeedItem
import cn.xfangfang.biliduo.data.model.MsgFeedSubject
import cn.xfangfang.biliduo.data.model.MsgFeedUser
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRowsTest {

    private fun user(mid: Long, name: String, avatar: String = "https://i0.hdslb.com/$mid.jpg") =
        MsgFeedUser(mid = mid, nickname = name, avatar = avatar)

    @Test
    fun replyRowUsesReplyTimeAndContent() {
        val item = MsgFeedItem(
            id = 42,
            user = user(7, "阿蓝"),
            item = MsgFeedSubject(
                type = "video",
                title = "视频标题",
                sourceContent = "这是回复内容",
                uri = "https://www.bilibili.com/video/BV1xx411c7mD",
            ),
            replyTime = 1_700_000_000,
        )
        val row = item.toMessageRowUi(MessageFeedKind.REPLY)!!
        assertEquals("阿蓝", row.actorText)
        assertEquals("回复了我", row.actionText)
        assertEquals("这是回复内容", row.content)
        assertEquals("视频标题", row.sourceTitle)
        assertEquals("视频", row.typeLabel)
        assertEquals(1_700_000_000L, row.timeSeconds)
        assertEquals("BV1xx411c7mD", row.bvid)
        assertNull(row.aid)
    }

    @Test
    fun atRowUsesAtTime() {
        val item = MsgFeedItem(
            id = 1,
            user = user(8, "小绿"),
            item = MsgFeedSubject(type = "dynamic", sourceContent = "@我 看这个"),
            atTime = 123,
        )
        val row = item.toMessageRowUi(MessageFeedKind.AT)!!
        assertEquals("@了我", row.actionText)
        assertEquals(123L, row.timeSeconds)
        assertEquals("动态", row.typeLabel)
    }

    @Test
    fun likeRowAggregatesUsersAndNumericCounts() {
        val item = MsgFeedItem(
            id = 9,
            users = listOf(user(1, "甲"), user(2, "乙")),
            item = MsgFeedSubject(type = "reply", sourceContent = "被赞的评论"),
            likeTime = 456,
            counts = JsonPrimitive(5),
        )
        val row = item.toMessageRowUi(MessageFeedKind.LIKE)!!
        assertEquals("甲、乙 等 5 人", row.actorText)
        assertEquals("赞了我", row.actionText)
        assertEquals("评论", row.typeLabel)
    }

    @Test
    fun likeRowKeepsNamesWhenCountsMatch() {
        val item = MsgFeedItem(
            id = 10,
            users = listOf(user(1, "甲"), user(2, "乙")),
            item = MsgFeedSubject(type = "video", sourceContent = "x"),
            likeTime = 1,
            counts = JsonPrimitive(2),
        )
        val row = item.toMessageRowUi(MessageFeedKind.LIKE)!!
        assertEquals("甲、乙", row.actorText)
    }

    @Test
    fun countsObjectDoesNotBreakLikeRow() {
        val item = MsgFeedItem(
            id = 11,
            users = listOf(user(1, "甲")),
            item = MsgFeedSubject(type = "video", sourceContent = "x"),
            likeTime = 1,
            counts = com.google.gson.JsonObject().apply {
                addProperty("like", 3)
            },
        )
        val row = item.toMessageRowUi(MessageFeedKind.LIKE)!!
        assertEquals("甲", row.actorText)
    }

    @Test
    fun parsesAidFromNativeUri() {
        val target = parseVideoTarget(
            MsgFeedSubject(type = "reply", uri = "bilibili://video/170001"),
            MessageFeedKind.REPLY,
        )
        assertNull(target.first)
        assertEquals(170001L, target.second)
    }

    @Test
    fun fallsBackToSubjectIdForVideoType() {
        val target = parseVideoTarget(
            MsgFeedSubject(type = "video", subjectId = 889900),
            MessageFeedKind.REPLY,
        )
        assertNull(target.first)
        assertEquals(889900L, target.second)
    }

    @Test
    fun dynamicTargetIsNotTreatedAsVideo() {
        val target = parseVideoTarget(
            MsgFeedSubject(type = "dynamic", subjectId = 123),
            MessageFeedKind.AT,
        )
        assertNull(target.first)
        assertNull(target.second)
    }

    @Test
    fun contentIsCleanedFromHtml() {
        val item = MsgFeedItem(
            id = 12,
            user = user(1, "甲"),
            item = MsgFeedSubject(
                type = "video",
                sourceContent = "第一行<br/>第二行 &amp; 符号",
            ),
            replyTime = 1,
        )
        val row = item.toMessageRowUi(MessageFeedKind.REPLY)!!
        assertEquals("第一行\n第二行 & 符号", row.content)
    }

    @Test
    fun blankRowIsDropped() {
        assertNull(MsgFeedItem().toMessageRowUi(MessageFeedKind.REPLY))
    }

    @Test
    fun relativeTimeBuckets() {
        val now = 1_800_000_000L
        assertEquals("刚刚", messageTimeLabel(now - 10, now))
        assertEquals("2 分钟前", messageTimeLabel(now - 120, now))
        assertEquals("2 小时前", messageTimeLabel(now - 7200, now))
        assertEquals("昨天", messageTimeLabel(now - 90_000, now))
        assertEquals("3 天前", messageTimeLabel(now - 3 * 86_400, now))
        assertEquals("", messageTimeLabel(0, now))
    }

    @Test
    fun oldMessageFallsBackToDate() {
        val now = 1_800_000_000L
        val label = messageTimeLabel(now - 30 * 86_400L, now)
        assertTrue(label, label.contains("月"))
        assertTrue(label, label.contains("日"))
        assertTrue(label, !label.contains("前"))
    }

    @Test
    fun typeLabels() {
        assertEquals("视频", subjectTypeLabel("video"))
        assertEquals("图文", subjectTypeLabel("opus"))
        assertEquals("专栏", subjectTypeLabel("article"))
        assertEquals("内容", subjectTypeLabel(null))
    }
}
