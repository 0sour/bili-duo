package cn.xfangfang.biliduo.ui.downloads

import cn.xfangfang.biliduo.data.model.DownloadInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsFilterTest {

    @Test
    fun blankQueryReturnsAllItems() {
        val items = listOf(single(id = 1, title = "视频A"), single(id = 2, title = "视频B"))

        val filtered = filterDownloadItems(items, "   ")

        assertEquals(2, filtered.size)
    }

    @Test
    fun matchesSingleByTitleCaseInsensitive() {
        val items = listOf(
            single(id = 1, title = "How To Ride"),
            single(id = 2, title = "别的视频")
        )

        val filtered = filterDownloadItems(items, "ride")

        assertEquals(1, filtered.size)
        assertEquals("id_1", (filtered[0] as DownloadListItem.Single).download.id)
    }

    @Test
    fun matchesGroupByTitle() {
        val group = DownloadListItem.Group(
            DownloadGroup(
                bvid = "BV1",
                title = "合集标题",
                cover = "",
                downloads = listOf(download(id = 1, title = "其他标题"))
            )
        )

        val filtered = filterDownloadItems(listOf(group), "合集")

        assertEquals(1, filtered.size)
    }

    @Test
    fun groupMatchesBySubItemPart() {
        val group = DownloadListItem.Group(
            DownloadGroup(
                bvid = "BV1",
                title = "长视频合集",
                cover = "",
                downloads = listOf(download(id = 1, title = "长视频合集", part = "开场介绍"))
            )
        )

        val filtered = filterDownloadItems(listOf(group), "开场")

        assertEquals(1, filtered.size)
    }

    @Test
    fun noMatchReturnsEmpty() {
        val items = listOf(single(id = 1, title = "视频A"))

        val filtered = filterDownloadItems(items, "不存在的关键词")

        assertTrue(filtered.isEmpty())
    }

    @Test
    fun statusFilterSplitsCompletedAndActiveItems() {
        val completed = single(id = 1, title = "已完成")
        val active = DownloadListItem.Single(
            download(id = 2, title = "下载中").copy(status = DownloadInfo.STATUS_DOWNLOADING)
        )
        val mixedGroup = DownloadListItem.Group(
            DownloadGroup(
                bvid = "BV3",
                title = "混合合集",
                cover = "",
                downloads = listOf(
                    download(id = 3, title = "混合合集").copy(status = DownloadInfo.STATUS_COMPLETED),
                    download(id = 4, title = "混合合集").copy(status = DownloadInfo.STATUS_PAUSED),
                ),
            )
        )
        val completedGroup = DownloadListItem.Group(
            DownloadGroup(
                bvid = "BV5",
                title = "完成合集",
                cover = "",
                downloads = listOf(download(id = 5, title = "完成合集")),
            )
        )
        val items = listOf(completed, active, mixedGroup, completedGroup)

        val activeOnly = filterDownloadItems(items, "", DownloadFilter.ACTIVE)
        val completedOnly = filterDownloadItems(items, "", DownloadFilter.COMPLETED)

        assertEquals(listOf(active, mixedGroup), activeOnly)
        assertEquals(listOf(completed, completedGroup), completedOnly)
    }

    @Test
    fun statusFilterStillAppliesSearchQuery() {
        val items = listOf(
            single(id = 1, title = "已完成 原神"),
            DownloadListItem.Single(
                download(id = 2, title = "下载中 原神")
                    .copy(status = DownloadInfo.STATUS_DOWNLOADING)
            ),
        )

        val filtered = filterDownloadItems(items, "原神", DownloadFilter.ACTIVE)

        assertEquals(1, filtered.size)
        assertEquals("id_2", (filtered[0] as DownloadListItem.Single).download.id)
    }

    private fun download(id: Int, title: String, part: String = "") = DownloadInfo(
        id = "id_$id",
        bvid = "BV_$id",
        cid = id.toLong(),
        title = title,
        cover = "",
        quality = 32,
        qualityDesc = "1080P",
        filePath = "/tmp/video_$id.mp4",
        status = DownloadInfo.STATUS_COMPLETED,
        part = part
    )

    private fun single(id: Int, title: String) = DownloadListItem.Single(download(id, title))
}
