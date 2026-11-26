package cn.xfangfang.biliduo.ui.player

import cn.xfangfang.biliduo.data.model.UgcSeason
import cn.xfangfang.biliduo.data.model.UgcSeasonEpisode
import cn.xfangfang.biliduo.data.model.UgcSeasonEpisodeArc
import cn.xfangfang.biliduo.data.model.UgcSeasonEpisodePage
import cn.xfangfang.biliduo.data.model.UgcSeasonSection
import cn.xfangfang.biliduo.data.model.VideoStat
import org.junit.Assert.assertEquals
import org.junit.Test

class UgcSeasonEpisodesTest {

    @Test
    fun `flattens sections and falls back to page fields`() {
        val season = UgcSeason(
            id = 10,
            title = "测试合集",
            sections = listOf(
                UgcSeasonSection(
                    id = 1,
                    title = "第一节",
                    episodes = listOf(
                        UgcSeasonEpisode(
                            bvid = "BV1",
                            cid = 11,
                            title = "第一集",
                            arc = UgcSeasonEpisodeArc(
                                pic = "//i0.hdslb.com/bfs/archive/cover.jpg",
                                duration = 60,
                                stat = VideoStat(view = 12345),
                            ),
                        ),
                        UgcSeasonEpisode(
                            bvid = "BV2",
                            cid = 0,
                            title = "",
                            arc = UgcSeasonEpisodeArc(title = "第二集", duration = 120),
                            page = UgcSeasonEpisodePage(cid = 22, duration = 90),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                SeasonEpisodeUi(
                    "BV1", 11, "第一集", 60, "第一节",
                    cover = "//i0.hdslb.com/bfs/archive/cover.jpg",
                    viewCount = 12345,
                ),
                SeasonEpisodeUi("BV2", 22, "第二集", 120, "第一节"),
            ),
            season.episodeItems(),
        )
    }

    @Test
    fun `skips episodes without bvid and handles null sections`() {
        val season = UgcSeason(
            sections = listOf(
                UgcSeasonSection(
                    episodes = listOf(
                        UgcSeasonEpisode(bvid = "", title = "已删除"),
                        UgcSeasonEpisode(bvid = "BV3", title = "保留", page = UgcSeasonEpisodePage(cid = 33)),
                    ),
                ),
            ),
        )
        assertEquals(
            listOf(SeasonEpisodeUi("BV3", 33, "保留", 0, "")),
            season.episodeItems(),
        )
        assertEquals(emptyList<SeasonEpisodeUi>(), (null as UgcSeason?).episodeItems())
    }
}
