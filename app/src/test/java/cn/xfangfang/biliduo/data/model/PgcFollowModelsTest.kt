package cn.xfangfang.biliduo.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PgcFollowModelsTest {

    @Test
    fun `follow status reads the follow flag`() {
        val data = Gson().fromJson(
            """{"status":1,"follow":1,"follow_status":2}""",
            PgcFollowStatusData::class.java,
        )

        assertEquals(1, data.follow)
        assertEquals(2, data.followStatus)
    }

    @Test
    fun `follow list item keeps progress and new episode`() {
        val data = Gson().fromJson(
            """
            {
              "total": 2,
              "list": [
                {
                  "season_id": 44282,
                  "title": "名侦探柯南",
                  "cover": "https://i0.hdslb.com/cover.jpg",
                  "progress": "看到第 3 话",
                  "new_ep": {"index_show": "更新至第 5 话"},
                  "total_count": 1279,
                  "follow_status": 2,
                  "season_type_name": "番剧",
                  "badge": "会员",
                  "rating": {"score": 9.4}
                }
              ]
            }
            """.trimIndent(),
            PgcFollowListData::class.java,
        )

        val item = data.list.orEmpty().single()
        assertEquals(44282L, item.seasonId)
        assertEquals("看到第 3 话", item.progress)
        assertEquals("更新至第 5 话", item.newEp?.indexShow)
        assertEquals(9.4f, item.rating?.score ?: 0f, 0.001f)
    }

    @Test
    fun `missing optional fields stay null instead of crashing`() {
        val data = Gson().fromJson(
            """{"list":[{"season_id":7}]}""",
            PgcFollowListData::class.java,
        )

        val item = data.list.orEmpty().single()
        assertNull(item.title)
        assertNull(item.progress)
        assertNull(item.newEp)
        assertTrue(item.seasonTypeName.isNullOrEmpty())
    }

    @Test
    fun `movie search result falls back to media id`() {
        val item = Gson().fromJson(
            """{"media_id":9988,"title":"电影"}""",
            SearchPgcItem::class.java,
        )

        assertEquals(0L, item.seasonId)
        assertEquals(9988L, item.seasonKey)
    }

    @Test
    fun `season result prefers season id`() {
        val item = Gson().fromJson(
            """{"season_id":44282,"media_id":9988,"title":"番剧"}""",
            SearchPgcItem::class.java,
        )

        assertEquals(44282L, item.seasonKey)
    }
}
