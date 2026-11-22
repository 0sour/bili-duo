package cn.xfangfang.biliduo.data.model

import cn.xfangfang.biliduo.ui.pgc.PgcIndexType
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PgcIndexModelsTest {

    private val gson = Gson()

    @Test
    fun `condition uses the data wrapper and exposes filters and orders`() {
        val response = gson.fromJson(
            """
            {
              "code": 0,
              "message": "success",
              "data": {
                "filter": [
                  {
                    "field": "area",
                    "name": "地区",
                    "values": [
                      {"keyword": "-1", "name": "全部地区"},
                      {"keyword": "2", "name": "日本"}
                    ]
                  }
                ],
                "order": [
                  {"field": "0", "name": "综合排序", "sort": "0"},
                  {"field": "3", "name": "最多追番", "sort": "0"}
                ]
              }
            }
            """.trimIndent(),
            PgcIndexResponse::class.java,
        )

        val data = gson.fromJson(
            gson.toJson(response.data),
            PgcIndexConditionData::class.java,
        )
        val area = data.filter.orEmpty().single()
        assertEquals("area", area.field)
        assertEquals("-1", area.values.orEmpty().first().keyword)
        assertEquals("综合排序", data.order.orEmpty().first().name)
    }

    @Test
    fun `result maps has_next and nullable item fields`() {
        val data = gson.fromJson(
            """
            {
              "has_next": 1,
              "num": 1,
              "size": 24,
              "total": 4280,
              "list": [
                {
                  "season_id": 47836,
                  "title": "鬼灭之刃 柱训练篇",
                  "cover": "https://example/cover.jpg",
                  "badge": "大会员",
                  "index_show": "全8话",
                  "order": "1770.1万追番",
                  "is_finish": 1,
                  "season_type": 1,
                  "season_type_name": "番剧"
                },
                {
                  "season_id": 123
                }
              ]
            }
            """.trimIndent(),
            PgcIndexResultData::class.java,
        )

        assertEquals(1, data.hasNext)
        assertEquals(4280, data.total)
        assertEquals(2, data.list.orEmpty().size)
        val first = data.list.orEmpty().first()
        assertEquals(47836L, first.seasonId)
        assertEquals("全8话", first.indexShow)
        assertEquals(1, first.isFinish)
        assertNull(data.list.orEmpty()[1].title)
    }

    @Test
    fun `index type ordinal maps to the api pair`() {
        assertEquals(PgcIndexType.ANIME, PgcIndexType.fromOrdinal(0))
        assertEquals(1, PgcIndexType.ANIME.type)
        assertEquals(1, PgcIndexType.ANIME.indexType)
        assertEquals(2, PgcIndexType.MOVIE.type)
        assertEquals(2, PgcIndexType.MOVIE.indexType)
        assertEquals(5, PgcIndexType.TV.indexType)
        assertEquals(3, PgcIndexType.DOCUMENTARY.indexType)
        assertEquals(7, PgcIndexType.VARIETY.indexType)
        assertEquals(102, PgcIndexType.FILM_TV.indexType)
        assertEquals(PgcIndexType.ANIME, PgcIndexType.fromOrdinal(99))
    }
}
