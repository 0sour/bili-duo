package cn.xfangfang.biliduo.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoPageGsonTest {

    @Test
    fun `UGC page JSON without badge leaves badge null`() {
        val page = Gson().fromJson(
            """{"cid":123,"page":1,"part":"P1","duration":60}""",
            VideoPage::class.java,
        )

        assertEquals(123L, page.cid)
        assertEquals("P1", page.part)
        assertNull(page.badge)
        assertEquals("", page.badge.orEmpty())
    }

    @Test
    fun `PGC page keeps its badge`() {
        val page = Gson().fromJson(
            """{"cid":456,"page":2,"part":"第 2 话","duration":1440,"badge":"会员"}""",
            VideoPage::class.java,
        )

        assertEquals("会员", page.badge)
    }
}
