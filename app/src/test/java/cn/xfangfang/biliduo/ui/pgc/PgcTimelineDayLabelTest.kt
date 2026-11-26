package cn.xfangfang.biliduo.ui.pgc

import cn.xfangfang.biliduo.data.model.PgcTimelineDay
import cn.xfangfang.biliduo.data.model.PgcTimelineEpisode
import org.junit.Assert.assertEquals
import org.junit.Test

class PgcTimelineDayLabelTest {

    @Test
    fun weekdayLabel() {
        assertEquals(
            "11-19  周四",
            pgcTimelineDayLabel(PgcTimelineDay(date = "11-19", dayOfWeek = 4)),
        )
    }

    @Test
    fun todayPrefix() {
        assertEquals(
            "今天 · 11-25  周二",
            pgcTimelineDayLabel(
                PgcTimelineDay(date = "11-25", dayOfWeek = 2, isToday = 1),
            ),
        )
    }

    @Test
    fun unknownWeekdayOnlyShowsDate() {
        assertEquals(
            "11-19",
            pgcTimelineDayLabel(PgcTimelineDay(date = "11-19", dayOfWeek = 0)),
        )
    }

    @Test
    fun sunday() {
        assertEquals(
            "11-22  周日",
            pgcTimelineDayLabel(PgcTimelineDay(date = "11-22", dayOfWeek = 7)),
        )
    }

    @Test
    fun missingDate() {
        assertEquals("", pgcTimelineDayLabel(PgcTimelineDay()))
    }

    @Test
    fun itemIndexSkipsHeadersAndEpisodes() {
        val days = listOf(
            PgcTimelineDay(date = "11-24", episodes = listOf(PgcTimelineEpisode(seasonId = 1))),
            PgcTimelineDay(date = "11-25", episodes = emptyList()),
            PgcTimelineDay(
                date = "11-26",
                episodes = listOf(
                    PgcTimelineEpisode(seasonId = 2),
                    PgcTimelineEpisode(seasonId = 3),
                ),
            ),
        )
        assertEquals(0, pgcTimelineItemIndex(days, 0))
        assertEquals(2, pgcTimelineItemIndex(days, 1))
        assertEquals(3, pgcTimelineItemIndex(days, 2))
        assertEquals(6, pgcTimelineItemIndex(days, 3))
    }

    @Test
    fun episodeKeyIsStableAcrossSamePosition() {
        val day = PgcTimelineDay(date = "11-27")
        val episode = PgcTimelineEpisode(seasonId = 7, episodeId = 9)
        val key = timelineEpisodeKey(day, episode, 1)
        assertEquals(key, timelineEpisodeKey(day, episode.copy(title = "x"), 1))
        // The index disambiguates duplicate season/episode ids in the payload.
        assertEquals(true, key != timelineEpisodeKey(day, episode, 2))
    }
}
