package cn.xfangfang.biliduo.ui.channel

import cn.xfangfang.biliduo.data.model.DynamicCardUi
import cn.xfangfang.biliduo.data.model.SpaceAccInfo
import cn.xfangfang.biliduo.data.model.UserCardData
import cn.xfangfang.biliduo.data.model.UserVideoItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelTopPersonTest {

    private fun video(index: Int) = UserVideoItem(
        bvid = "BV$index",
        aid = index.toLong(),
        title = "视频$index",
        pic = "pic$index",
        playCount = 1000,
        danmakuCount = 10,
        pubdate = 0,
        description = "",
        mid = 42,
        author = "UP",
        duration = "01:00",
    )

    private fun moment(index: Int, text: String = "") = DynamicCardUi(
        id = "$index",
        authorName = "UP",
        authorFace = "",
        pubTime = "2小时前",
        kindLabel = "视频",
        text = text,
        images = emptyList(),
        video = null,
        forward = null,
        commentCount = 0,
        forwardCount = 0,
        likeCount = 0,
    )

    @Test
    fun `maps stats and caps recent videos and moments`() {
        val person = buildChannelPersonUi(
            mid = 42,
            info = SpaceAccInfo(mid = 42, name = "UP", face = "face", sign = "签名"),
            card = UserCardData(follower = 36_000, archive_count = 34),
            videos = (1..5).map(::video),
            moments = (1..4).map { moment(it, "动态$it") },
        )

        assertEquals("UP", person.name)
        assertEquals("UID 42", person.subtitle)
        assertEquals(listOf("3万", "34"), person.stats.map { it.value })
        assertEquals(3, person.recentVideos.size)
        assertEquals("视频1", person.recentVideos.first().title)
        assertEquals(2, person.recentMoments.size)
        assertEquals("动态1", person.recentMoments.first().text)
        assertEquals("视频 · 2小时前", person.recentMoments.first().meta)
    }

    @Test
    fun `falls back when profile and moment text are missing`() {
        val person = buildChannelPersonUi(
            mid = 7,
            info = null,
            card = null,
            videos = emptyList(),
            moments = listOf(moment(1)),
        )

        assertEquals("UP 主", person.name)
        assertEquals("这个 UP 主还没有填写签名。", person.description)
        assertEquals("（无文字动态）", person.recentMoments.first().text)
        assertEquals("0", person.stats.first().value)
    }
}
