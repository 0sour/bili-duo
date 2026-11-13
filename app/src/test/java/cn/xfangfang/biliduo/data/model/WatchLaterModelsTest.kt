package cn.xfangfang.biliduo.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchLaterModelsTest {

    private val gson = Gson()

    @Test
    fun parsesListAndOwner() {
        val data = gson.fromJson(
            """
            {
              "count": 2,
              "list": [
                {
                  "aid": 123,
                  "bvid": "BV1xx411c7mD",
                  "title": "测试视频",
                  "pic": "https://example.com/cover.jpg",
                  "duration": 60,
                  "progress": 12,
                  "cid": 456,
                  "videos": 2,
                  "owner": {"mid": 7, "name": "UP", "face": "face.jpg"},
                  "stat": {"view": 100},
                  "add_at": 1700000000
                }
              ]
            }
            """.trimIndent(),
            WatchLaterData::class.java,
        )

        assertEquals(2, data.count)
        val item = data.list.orEmpty().single()
        assertEquals(123L, item.aid)
        assertEquals("BV1xx411c7mD", item.bvid)
        assertEquals("测试视频", item.title)
        assertEquals(60L, item.duration)
        assertEquals(12L, item.progress)
        assertEquals(456L, item.cid)
        assertEquals(2, item.videos)
        assertEquals("UP", item.owner?.name)
        assertEquals(100L, item.stat?.view)
        assertEquals(1700000000L, item.addAt)
    }

    @Test
    fun missingOptionalFieldsStayNull() {
        val data = gson.fromJson(
            """{"list":[{"aid":1}]}""",
            WatchLaterData::class.java,
        )

        val item = data.list.orEmpty().single()
        assertEquals(1L, item.aid)
        assertNull(item.bvid)
        assertNull(item.title)
        assertNull(item.owner)
        assertNull(item.stat)
    }
}
