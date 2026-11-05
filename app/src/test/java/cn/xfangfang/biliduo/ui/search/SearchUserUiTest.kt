package cn.xfangfang.biliduo.ui.search

import cn.xfangfang.biliduo.data.model.SearchUserItem
import cn.xfangfang.biliduo.data.model.SearchUserVerify
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchUserUiTest {

    @Test
    fun `cleanForDisplay strips highlight tags and trims`() {
        val item = SearchUserItem(
            uname = " <em class=\"keyword\">老番茄</em> ",
            sign = "签名<br>第二行",
        )

        val cleaned = item.cleanForDisplay()

        assertEquals("老番茄", cleaned.uname)
        assertEquals("签名\n第二行", cleaned.sign)
    }

    @Test
    fun `badge prefers official verification text`() {
        val official = SearchUserItem(
            isUpUser = 1,
            officialVerify = SearchUserVerify(type = 1, desc = "bilibili 知名UP主"),
        )

        assertEquals("bilibili 知名UP主", official.badgeLabel())
        assertEquals("UP 主", SearchUserItem(isUpUser = 1).badgeLabel())
        assertEquals("用户", SearchUserItem().badgeLabel())
    }

    @Test
    fun `meta line formats fans and video counts`() {
        assertEquals("12万粉丝 · 345视频", SearchUserItem(fans = 120_000, videos = 345).metaLine())
        assertEquals("408粉丝", SearchUserItem(fans = 408).metaLine())
        assertEquals("", SearchUserItem().metaLine())
    }
}
