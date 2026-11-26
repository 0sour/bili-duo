@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package cn.xfangfang.biliduo.ui.pgc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import cn.xfangfang.biliduo.data.model.PgcTimelineDay
import cn.xfangfang.biliduo.data.model.PgcTimelineEpisode
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.common.DuoEmptyState
import cn.xfangfang.biliduo.ui.common.DuoIconAction
import cn.xfangfang.biliduo.ui.common.DuoPageHeader
import cn.xfangfang.biliduo.ui.common.DuoPalette
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.ui.duo.requestPageFocus
import cn.xfangfang.biliduo.ui.top.TopAmbientUi
import cn.xfangfang.biliduo.ui.top.TopGlyph
import cn.xfangfang.biliduo.util.fixPic
import kotlinx.coroutines.delay

@Composable
fun PgcTimelineScreen(
    onBack: () -> Unit,
    onPgcClick: (seasonId: Long) -> Unit,
    onTopAmbient: (TopAmbientUi) -> Unit = {},
    onFocusCleared: () -> Unit = {},
    session: DuoSessionViewModel? = null,
    viewModel: PgcTimelineViewModel = viewModel(),
) {
    val ui by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val firstRowFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    // Remember the row that opened the PGC page so returning from playback
    // lands back on it instead of jumping to the first episode of the week.
    var openedKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Today is six days into the window; open there instead of at the oldest
    // day. Today itself often has no episode, so focus the next day that does
    // while still scrolling to today's header for context.
    val todayIndex = remember(ui.days) { ui.days.indexOfFirst { it.isToday == 1 } }
    val focusDayIndex = remember(ui.days, todayIndex) {
        val start = if (todayIndex >= 0) todayIndex else 0
        (start until ui.days.size).firstOrNull {
            !ui.days[it].episodes.isNullOrEmpty()
        } ?: ui.days.indexOfFirst { !it.episodes.isNullOrEmpty() }
    }
    val scrollDayIndex = remember(ui.days, todayIndex, focusDayIndex) {
        if (todayIndex >= 0) todayIndex else focusDayIndex
    }
    // dayIndex to episodeIndex of the row to focus first: the one we came back
    // from, otherwise the first episode after today.
    val restoreTarget = remember(ui.days, openedKey) {
        val key = openedKey ?: return@remember null
        ui.days.forEachIndexed { dayIndex, day ->
            day.episodes.orEmpty().forEachIndexed { epIndex, episode ->
                if (timelineEpisodeKey(day, episode, epIndex) == key) {
                    return@remember dayIndex to epIndex
                }
            }
        }
        null
    }
    val initialFocus = restoreTarget
        ?: focusDayIndex.takeIf { it >= 0 }?.let { it to 0 }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(Unit) {
        onTopAmbient(
            TopAmbientUi(
                eyebrow = "SCHEDULE / 新番时间表",
                title = "这一周，\n有哪些新番更新。",
                subtitle = "按更新日期排列，确认键直接进入番剧。",
                glyph = TopGlyph.BANGUMI,
            )
        )
    }
    LaunchedEffect(ui.isLoading, ui.days.size, ui.error) {
        if (ui.isLoading) return@LaunchedEffect
        delay(80)
        val target = restoreTarget
        if (target != null) {
            // Put the row just below the viewport top so its ring and the day
            // header stay visible, then focus it.
            val rowIndex = pgcTimelineItemIndex(ui.days, target.first) + 1 + target.second
            runCatching { listState.scrollToItem((rowIndex - 1).coerceAtLeast(0)) }
            runCatching { firstRowFocus.requestPageFocus() }
        } else if (focusDayIndex >= 0) {
            val scrollTarget = if (focusDayIndex >= scrollDayIndex) {
                scrollDayIndex
            } else {
                focusDayIndex
            }
            runCatching {
                listState.scrollToItem(pgcTimelineItemIndex(ui.days, scrollTarget))
            }
            runCatching { firstRowFocus.requestPageFocus() }
        } else {
            runCatching { retryFocus.requestPageFocus() }
        }
    }
    LaunchedEffect(ui.days.size) {
        if (ui.days.isEmpty()) onFocusCleared()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoPalette.Bg),
    ) {
        DuoPageHeader(
            title = "新番时间表",
            onBack = onBack,
            actions = {
                DuoIconAction(
                    icon = Icons.Default.Refresh,
                    contentDescription = "刷新时间表",
                    tint = DuoPalette.Text,
                    onClick = viewModel::load,
                )
            },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                ui.isLoading && ui.days.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = DuoPalette.Pink,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                ui.error != null && ui.days.isEmpty() -> {
                    DuoEmptyState(
                        title = "加载失败",
                        hint = ui.error,
                        actionText = "重试",
                        onAction = viewModel::load,
                        focusRequester = retryFocus,
                    )
                }
                !ui.days.any { !it.episodes.isNullOrEmpty() } -> {
                    DuoEmptyState(
                        title = "本周暂无更新",
                        hint = "稍后再来看看",
                        actionText = "刷新",
                        onAction = viewModel::load,
                        focusRequester = retryFocus,
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 10.dp,
                            end = 10.dp,
                            top = 2.dp,
                            bottom = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        ui.days.forEachIndexed { dayIndex, day ->
                            item(key = "day_${day.date ?: dayIndex}") {
                                TimelineDayHeader(day)
                            }
                            val episodes = day.episodes.orEmpty()
                            itemsIndexed(
                                items = episodes,
                                key = { index, ep ->
                                    timelineEpisodeKey(day, ep, index)
                                },
                            ) { index, episode ->
                                TimelineEpisodeRow(
                                    episode = episode,
                                    modifier = if (
                                        initialFocus?.first == dayIndex &&
                                        initialFocus.second == index
                                    ) {
                                        Modifier.focusRequester(firstRowFocus)
                                    } else {
                                        Modifier
                                    },
                                    onClick = {
                                        openedKey = timelineEpisodeKey(day, episode, index)
                                        episode.seasonId?.takeIf { it > 0 }?.let(onPgcClick)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineDayHeader(day: PgcTimelineDay) {
    val label = pgcTimelineDayLabel(day)
    val count = day.episodes.orEmpty().size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (day.isToday == 1) DuoPalette.Pink else DuoPalette.Secondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        if (count > 0) {
            Text(
                text = "$count 部",
                color = DuoPalette.Tertiary,
                fontSize = 9.sp,
            )
        }
    }
}

/** LazyColumn index of the day header for [dayIndex] (header + episodes per day). */
fun pgcTimelineItemIndex(days: List<PgcTimelineDay>, dayIndex: Int): Int {
    var index = 0
    for (i in 0 until dayIndex.coerceIn(0, days.size)) {
        index += 1 + days[i].episodes.orEmpty().size
    }
    return index
}

/** Stable per-row key; also used to restore focus after returning from playback. */
fun timelineEpisodeKey(
    day: PgcTimelineDay,
    episode: PgcTimelineEpisode,
    index: Int,
): String = "ep_${day.date}_${episode.seasonId}_${episode.episodeId}_$index"

/** Pure so the day header can be unit tested. `day_of_week` is 1=Monday. */
fun pgcTimelineDayLabel(day: PgcTimelineDay): String {
    val weekday = when (day.dayOfWeek) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> ""
    }
    return buildString {
        if (day.isToday == 1) append("今天 · ")
        day.date?.let { append(it) }
        if (weekday.isNotEmpty()) {
            if (isNotEmpty()) append("  ")
            append(weekday)
        }
    }
}

@Composable
private fun TimelineEpisodeRow(
    episode: PgcTimelineEpisode,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val cover = episode.epCover?.takeIf { it.isNotBlank() }
        ?: episode.squareCover?.takeIf { it.isNotBlank() }
        ?: episode.cover.orEmpty()
    val delayed = (episode.delay ?: 0) > 0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.hasFocus }
            .duoClickable(onClick = onClick)
            // Keep the ring inside the Lazy viewport (same 7dp rule as search).
            .padding(vertical = 7.dp)
            .height(70.dp)
            .duoFocusRing(shape, focused = focused)
            .clip(shape)
            .background(DuoPalette.Surface)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(106.dp)
                .height(62.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DuoPalette.SurfaceHigh),
        ) {
            AsyncImage(
                model = fixPic(cover),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = episode.title.orEmpty(),
                color = DuoPalette.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                episode.pubIndex?.takeIf { it.isNotBlank() }?.let { index ->
                    Text(
                        text = index,
                        color = DuoPalette.Pink,
                        fontSize = 9.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(DuoPalette.Pink.copy(alpha = 0.14f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                episode.pubTime?.takeIf { it.isNotBlank() }?.let { time ->
                    Text(
                        text = time,
                        color = DuoPalette.Secondary,
                        fontSize = 9.5.sp,
                    )
                }
                if (delayed) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = episode.delayReason?.takeIf { it.isNotBlank() } ?: "延期",
                        color = DuoPalette.Warning,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
