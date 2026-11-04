package cn.xfangfang.biliduo.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicModelsTest {

    private val gson = Gson()

    private fun feed(json: String): DynamicFeedData =
        gson.fromJson(json, DynamicFeedData::class.java)

    @Test
    fun mapsVideoDynamicFromArchiveMajor() {
        val data = feed(
            """
            {
              "has_more": true,
              "offset": "next-page",
              "items": [{
                "id_str": "9001",
                "type": "DYNAMIC_TYPE_AV",
                "modules": {
                  "module_author": {
                    "mid": 4242,
                    "name": "测试UP",
                    "face": "https://i0.hdslb.com/face.jpg",
                    "pub_time": "2小时前"
                  },
                  "module_dynamic": {
                    "desc": { "text": "新视频来了" },
                    "major": {
                      "type": "MAJOR_TYPE_ARCHIVE",
                      "archive": {
                        "bvid": "BV1xx411c7mD",
                        "title": "测试视频标题",
                        "cover": "https://i0.hdslb.com/cover.jpg",
                        "desc": "视频简介",
                        "duration_text": "12:34",
                        "stat": { "play": "1.2万" }
                      }
                    }
                  },
                  "module_stat": {
                    "comment": { "count": 12 },
                    "forward": { "count": 3 },
                    "like": { "count": 45 }
                  }
                }
              }]
            }
            """.trimIndent()
        )

        assertTrue(data.hasMore)
        assertEquals("next-page", data.offset)
        val card = data.items.orEmpty().single().toCardUi()!!
        assertEquals("9001", card.id)
        assertEquals(4242L, card.authorMid)
        assertEquals("测试UP", card.authorName)
        assertEquals("2小时前", card.pubTime)
        assertEquals("视频", card.kindLabel)
        assertEquals("新视频来了", card.text)
        assertEquals("BV1xx411c7mD", card.playbackBvid)
        assertEquals("测试视频标题", card.video?.title)
        assertEquals("12:34", card.video?.duration)
        assertEquals("1.2万", card.video?.play)
        assertEquals(12, card.commentCount)
        assertEquals(3, card.forwardCount)
        assertEquals(45, card.likeCount)
        assertTrue(card.images.isEmpty())
    }

    @Test
    fun mapsOpusImagesAndFallsBackToSummaryText() {
        val data = feed(
            """
            {
              "items": [{
                "id_str": "9002",
                "type": "DYNAMIC_TYPE_DRAW",
                "modules": {
                  "module_author": { "name": "画师" },
                  "module_dynamic": {
                    "major": {
                      "type": "MAJOR_TYPE_OPUS",
                      "opus": {
                        "summary": { "text": "今天的练习" },
                        "pics": [
                          { "url": "https://i0.hdslb.com/1.jpg" },
                          { "src": "https://i0.hdslb.com/2.jpg" }
                        ]
                      }
                    }
                  }
                }
              }]
            }
            """.trimIndent()
        )

        val card = data.items.orEmpty().single().toCardUi()!!
        assertEquals("图文", card.kindLabel)
        assertEquals("今天的练习", card.text)
        assertEquals(
            listOf("https://i0.hdslb.com/1.jpg", "https://i0.hdslb.com/2.jpg"),
            card.images,
        )
        assertNull(card.video)
        assertNull(card.playbackBvid)
    }

    @Test
    fun mapsForwardWithOriginalVideo() {
        val data = feed(
            """
            {
              "items": [{
                "id_str": "9003",
                "type": "DYNAMIC_TYPE_FORWARD",
                "modules": {
                  "module_author": { "name": "转发者" },
                  "module_dynamic": { "desc": { "text": "转发理由" } }
                },
                "orig": {
                  "id_str": "8003",
                  "type": "DYNAMIC_TYPE_AV",
                  "modules": {
                    "module_author": {
                      "name": "原作者",
                      "face": "https://i0.hdslb.com/orig-face.jpg"
                    },
                    "module_dynamic": {
                      "major": {
                        "type": "MAJOR_TYPE_ARCHIVE",
                        "archive": {
                          "bvid": "BV1yy411c7mE",
                          "title": "原视频",
                          "cover": "https://i0.hdslb.com/orig-cover.jpg",
                          "duration_text": "03:21"
                        }
                      }
                    }
                  }
                }
              }]
            }
            """.trimIndent()
        )

        val card = data.items.orEmpty().single().toCardUi()!!
        assertEquals("转发", card.kindLabel)
        assertEquals("转发理由", card.text)
        assertEquals("原作者", card.forward?.authorName)
        assertEquals("BV1yy411c7mE", card.forward?.video?.bvid)
        assertEquals("BV1yy411c7mE", card.playbackBvid)
    }

    @Test
    fun dropsItemsWithoutModules() {
        val data = feed("""{"items":[{"id_str":"1"}]}""")
        assertNull(data.items.orEmpty().single().toCardUi())
    }
}
