package cn.xfangfang.biliduo.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import cn.xfangfang.biliduo.data.model.HomeTab
import cn.xfangfang.biliduo.data.model.HomeVideoItem
import cn.xfangfang.biliduo.data.model.LiveAreaGroup
import cn.xfangfang.biliduo.data.model.LiveFollowedAnchor
import cn.xfangfang.biliduo.data.model.LiveRoomItem
import cn.xfangfang.biliduo.data.model.LiveSubArea
import cn.xfangfang.biliduo.data.model.PgcSeasonItem
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.ui.duo.duoTabCycling
import cn.xfangfang.biliduo.ui.top.TopAmbientUi
import cn.xfangfang.biliduo.ui.top.TopGlyph
import cn.xfangfang.biliduo.ui.top.TopPreviewUi
import cn.xfangfang.biliduo.util.fixPic
import cn.xfangfang.biliduo.util.formatCount
import kotlinx.coroutines.delay

private val HomeBg = Color(0xFF0F0F0F)
private val HomeCardBg = Color(0xFF1B1B1D)
private val HomeText = Color(0xFFF2F2F4)
private val HomeSecondary = Color(0xFFA6A6AE)
private val HomePink = Color(0xFFFB7299)

// The ring is drawn outside the card, so list focus scrolling must leave this
// much transparent space inside the focus target. Otherwise bring-into-view
// aligns the card itself to the viewport edge and clips the ring.
private val CardFocusClearance = 7.dp

@Composable
fun HomeScreen(
    onVideoClick: (String) -> Unit,
    onPgcClick: (Long) -> Unit = {},
    onLiveClick: (Long) -> Unit = {},
    onVideoFocused: (TopPreviewUi) -> Unit = {},
    onFocusCleared: () -> Unit = {},
    onTopAmbient: (TopAmbientUi) -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val gridState = rememberLazyGridState()
    val firstFocus = remember { FocusRequester() }
    val areaFocus = remember { FocusRequester() }
    val followedFocus = remember { FocusRequester() }
    val subAreaFocus = remember { FocusRequester() }
    val areaFocusers = remember(state.liveAreas) {
        List(state.liveAreas.size) { FocusRequester() }
    }
    val subAreaFocusers = remember(state.liveAreaId, state.liveAreas) {
        val count = state.liveAreas.firstOrNull { it.id == state.liveAreaId }
            ?.subAreas?.size ?: 0
        List(count) { FocusRequester() }
    }
    var pendingAreaFocus by remember { mutableStateOf(false) }

    LaunchedEffect(state.liveAreaId, pendingAreaFocus) {
        if (pendingAreaFocus) {
            delay(80L)
            if (state.liveAreaId == 0) {
                runCatching { areaFocus.requestFocus() }
            } else {
                val index = state.liveAreas.indexOfFirst { it.id == state.liveAreaId }
                areaFocusers.getOrNull(index)?.let { runCatching { it.requestFocus() } }
            }
            pendingAreaFocus = false
        }
    }
    var focusedTab by remember { mutableStateOf<HomeTab?>(null) }
    val isPgc = state.tab.isPgc
    val isLive = state.tab.isLive
    val itemsEmpty = when {
        isLive -> state.liveItems.isEmpty()
        isPgc -> state.pgcItems.isEmpty()
        else -> state.items.isEmpty()
    }
    val firstKey = when {
        isLive -> state.liveItems.firstOrNull()?.roomId
        isPgc -> state.pgcItems.firstOrNull()?.seasonId
        else -> state.items.firstOrNull()?.bvid
    }

    LaunchedEffect(state.tab) {
        onTopAmbient(homeTabAmbient(state.tab))
        runCatching { gridState.scrollToItem(0) }
    }
    LaunchedEffect(itemsEmpty, state.isLoading, state.error) {
        if (itemsEmpty && !state.isLoading) {
            onTopAmbient(homeEmptyAmbient(state.tab, state.error))
        }
    }

    LaunchedEffect(state.tab, firstKey) {
        if (!itemsEmpty && focusedTab != state.tab) {
            delay(80L)
            runCatching { firstFocus.requestFocus() }
            focusedTab = state.tab
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = gridState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 4 && state.hasMore &&
                !state.isLoadingMore && !state.isLoading
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    // The sub-area and followed-anchor rows are tall. Collapse them as soon as
    // the room grid leaves the top, and expand again when it returns, so more
    // than two rooms fit while browsing.
    val liveRowsExpanded by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset <= 24
        }
    }

    val cyclePreviousArea: (() -> Unit)? = if (isLive) {
        {
            viewModel.cycleLiveArea(-1)
            pendingAreaFocus = true
        }
    } else {
        null
    }
    val cycleNextArea: (() -> Unit)? = if (isLive) {
        {
            viewModel.cycleLiveArea(1)
            pendingAreaFocus = true
        }
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBg)
            .duoTabCycling(
                onPreviousTab = { viewModel.cycleTab(-1) },
                onNextTab = { viewModel.cycleTab(1) },
                onPreviousSection = cyclePreviousArea,
                onNextSection = cycleNextArea,
            ),
    ) {
        HomeHeader(
            selected = state.tab,
            onSelect = viewModel::selectTab,
        )

        when {
            !isLive && state.isLoading && itemsEmpty -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HomePink, modifier = Modifier.size(28.dp))
                }
            }
            !isLive && state.error != null && itemsEmpty -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.error ?: "加载失败", color = Color(0xFFFF9AA9), fontSize = 12.sp)
                    Text(
                        text = "重试",
                        color = HomePink,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .duoFocusRing(RoundedCornerShape(6.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .duoClickable(onClick = viewModel::refresh)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            !isLive && itemsEmpty -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无内容", color = HomeSecondary, fontSize = 12.sp)
                }
            }
            isLive -> {
                LiveSection(
                    rooms = state.liveItems,
                    followed = state.liveFollowed,
                    areas = state.liveAreas,
                    subAreas = state.liveAreas
                        .firstOrNull { it.id == state.liveAreaId }
                        ?.subAreas
                        .orEmpty(),
                    selectedAreaId = state.liveAreaId,
                    selectedSubAreaId = state.liveSubAreaId,
                    showSecondaryRows = liveRowsExpanded,
                    gridState = gridState,
                    firstFocus = firstFocus,
                    areaFocus = areaFocus,
                    followedFocus = followedFocus,
                    subAreaFocus = subAreaFocus,
                    areaFocusRequesters = areaFocusers,
                    subAreaFocusRequesters = subAreaFocusers,
                    isLoadingMore = state.isLoadingMore,
                    isLoading = state.isLoading,
                    error = state.error,
                    onRetry = viewModel::refresh,
                    onAreaSelected = viewModel::selectLiveArea,
                    onSubAreaSelected = viewModel::selectLiveSubArea,
                    onFocused = { room -> onVideoFocused(room.toTopPreview()) },
                    onFocusCleared = onFocusCleared,
                    onClick = { onLiveClick(it.roomId) },
                    onFollowedClick = { onLiveClick(it.roomId) },
                )
            }
            isPgc -> {
                PgcSeasonGrid(
                    items = state.pgcItems,
                    firstFocus = firstFocus,
                    onFocused = { season -> onVideoFocused(season.toTopPreview(state.tab)) },
                    onFocusCleared = onFocusCleared,
                    onClick = { onPgcClick(it.seasonId) },
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 0.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(state.items, key = { _, item -> item.bvid }) { index, item ->
                        HomeVideoCard(
                            item = item,
                            focusRequester = if (index == 0) firstFocus else null,
                            onFocusChanged = { focused ->
                                if (focused) {
                                    onVideoFocused(item.toTopPreview())
                                } else {
                                    onFocusCleared()
                                }
                            },
                            onClick = { onVideoClick(item.bvid) },
                        )
                    }
                    if (state.isLoadingMore) {
                        item(span = { GridItemSpan(3) }) {
                            Box(
                                Modifier.fillMaxWidth().padding(10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = HomePink,
                                    modifier = Modifier.size(20.dp),
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
private fun HomeHeader(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(start = 16.dp, top = 6.dp, end = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeTab.entries.forEach { tab ->
            HomeTabItem(
                text = tab.label,
                selected = tab == selected,
                onClick = { onSelect(tab) },
            )
            Spacer(Modifier.width(16.dp))
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun HomeTabItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .height(42.dp)
            .duoFocusRing(RoundedCornerShape(4.dp))
            .duoClickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            color = if (selected) HomePink else HomeSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .width(16.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (selected) HomePink else Color.Transparent),
        )
    }
}

@Composable
private fun LiveSection(
    rooms: List<LiveRoomItem>,
    followed: List<LiveFollowedAnchor>,
    areas: List<LiveAreaGroup>,
    subAreas: List<LiveSubArea>,
    selectedAreaId: Int,
    selectedSubAreaId: Int,
    showSecondaryRows: Boolean,
    gridState: LazyGridState,
    firstFocus: FocusRequester,
    areaFocus: FocusRequester,
    followedFocus: FocusRequester,
    subAreaFocus: FocusRequester,
    areaFocusRequesters: List<FocusRequester> = emptyList(),
    subAreaFocusRequesters: List<FocusRequester> = emptyList(),
    isLoadingMore: Boolean,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onAreaSelected: (Int) -> Unit,
    onSubAreaSelected: (Int) -> Unit,
    onFocused: (LiveRoomItem) -> Unit,
    onFocusCleared: () -> Unit,
    onClick: (LiveRoomItem) -> Unit,
    onFollowedClick: (LiveFollowedAnchor) -> Unit,
) {
    // LazyRow focus groups do not hand D-pad focus to a neighbouring LazyRow
    // reliably; bridge the rows explicitly so every filter/avatar stays
    // reachable. Visual order: parent chips -> sub-area chips -> followed -> grid.
    val areaDownFocus = when {
        !showSecondaryRows -> firstFocus
        subAreas.isNotEmpty() -> subAreaFocus
        followed.isNotEmpty() -> followedFocus
        else -> firstFocus
    }
    val subAreaDownFocus = if (followed.isNotEmpty()) followedFocus else firstFocus
    val gridUpFocus = when {
        !showSecondaryRows -> areaFocus
        followed.isNotEmpty() -> followedFocus
        subAreas.isNotEmpty() -> subAreaFocus
        else -> areaFocus
    }
    var secondaryRowFocused by remember { mutableStateOf(false) }
    // If touch scrolling collapsed the rows while one of their chips owned
    // focus, hand focus back to the always-visible parent row; otherwise the
    // removed focus node would leave the gamepad without a target.
    LaunchedEffect(showSecondaryRows) {
        if (!showSecondaryRows && secondaryRowFocused) {
            runCatching { areaFocus.requestFocus() }
        }
    }
    // L2/R2 can select an area that is scrolled out of the chip row; keep the
    // selected chip visible so the shortcut has visible feedback.
    val areaRowState = rememberLazyListState()
    LaunchedEffect(selectedAreaId, areas.size) {
        val index = if (selectedAreaId == 0) {
            0
        } else {
            areas.indexOfFirst { it.id == selectedAreaId } + 1
        }
        if (selectedAreaId == 0 || index > 0) {
            areaRowState.scrollToItem(index)
        }
    }
    // Keep the grid at the top when the area changes; the old cards stay
    // visible until the new response replaces them.
    LaunchedEffect(selectedAreaId, selectedSubAreaId) {
        gridState.scrollToItem(0)
    }
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            state = areaRowState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "live_area_all") {
                LiveAreaChip(
                    text = "推荐",
                    selected = selectedAreaId == 0,
                    focusRequester = areaFocus,
                    downFocusRequester = areaDownFocus,
                    onClick = { onAreaSelected(0) },
                )
            }
            itemsIndexed(areas, key = { _, area -> area.id }) { index, area ->
                LiveAreaChip(
                    text = area.name,
                    selected = selectedAreaId == area.id,
                    focusRequester = areaFocusRequesters.getOrNull(index),
                    downFocusRequester = areaDownFocus,
                    onClick = { onAreaSelected(area.id) },
                )
            }
        }
        AnimatedVisibility(
            visible = showSecondaryRows,
            enter = expandVertically(tween(160)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged { secondaryRowFocused = it.hasFocus }
            ) {
                if (subAreas.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 8.dp,
                            bottom = 8.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The second level is deliberately smaller and labelled
                        // so it cannot be mistaken for a duplicate of the
                        // parent area row.
                        item(key = "live_sub_area_label") {
                            Text(
                                text = "子分区",
                                color = HomeSecondary.copy(alpha = 0.8f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        item(key = "live_sub_area_all") {
                            LiveAreaChip(
                                text = "全部",
                                selected = selectedSubAreaId == 0,
                                subtle = true,
                                focusRequester = subAreaFocus,
                                upFocusRequester = areaFocus,
                                downFocusRequester = subAreaDownFocus,
                                onClick = { onSubAreaSelected(0) },
                            )
                        }
                        itemsIndexed(subAreas, key = { _, area -> area.id }) { index, area ->
                            val areaId = area.id.toIntOrNull() ?: 0
                            LiveAreaChip(
                                text = area.name,
                                selected = selectedSubAreaId == areaId,
                                subtle = true,
                                focusRequester = subAreaFocusRequesters.getOrNull(index),
                                upFocusRequester = areaFocus,
                                downFocusRequester = subAreaDownFocus,
                                onClick = { onSubAreaSelected(areaId) },
                            )
                        }
                    }
                }
                if (followed.isNotEmpty()) {
                    LiveFollowedRow(
                        followed = followed,
                        firstFocus = followedFocus,
                        upFocusRequester = if (subAreas.isNotEmpty()) subAreaFocus else areaFocus,
                        downFocusRequester = firstFocus,
                        onClick = onFollowedClick,
                    )
                }
            }
        }
        if (isLoading && rooms.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = HomePink,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (rooms.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Box(
                        Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            isLoading -> CircularProgressIndicator(
                                color = HomePink,
                                modifier = Modifier.size(24.dp),
                            )
                            error != null -> Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(error, color = Color(0xFFFF9AA9), fontSize = 11.sp)
                                Text(
                                    text = "重试",
                                    color = HomePink,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .duoFocusRing(RoundedCornerShape(6.dp))
                                        .clip(RoundedCornerShape(6.dp))
                                        .duoClickable(onClick = onRetry)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                            else -> Text("暂无直播", color = HomeSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
            itemsIndexed(rooms, key = { _, room -> room.roomId }) { index, room ->
                LiveRoomCard(
                    room = room,
                    focusRequester = if (index == 0) firstFocus else null,
                    // Let the grid's top row jump into the filter rows instead
                    // of skipping straight to the header tabs.
                    upFocusRequester = gridUpFocus.takeIf { index < 2 },
                    onFocusChanged = { focused ->
                        if (focused) onFocused(room) else onFocusCleared()
                    },
                    onClick = { onClick(room) },
                )
            }
            if (isLoadingMore) {
                item(span = { GridItemSpan(2) }) {
                    Box(
                        Modifier.fillMaxWidth().padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = HomePink,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveAreaChip(
    text: String,
    selected: Boolean,
    subtle: Boolean = false,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(if (subtle) 9.dp else 14.dp)
    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(
                if (upFocusRequester != null || downFocusRequester != null) {
                    Modifier.focusProperties {
                        if (upFocusRequester != null) up = upFocusRequester
                        if (downFocusRequester != null) down = downFocusRequester
                    }
                } else {
                    Modifier
                }
            )
            .duoFocusRing(shape)
            .clip(shape)
            .background(
                if (selected) {
                    HomePink.copy(alpha = if (subtle) 0.10f else 0.18f)
                } else {
                    Color.White.copy(alpha = if (subtle) 0.03f else 0.06f)
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    HomePink.copy(alpha = if (subtle) 0.5f else 0.8f)
                } else {
                    Color.White.copy(alpha = if (subtle) 0.05f else 0.08f)
                },
                shape = shape,
            )
            .duoClickable(onClick = onClick)
            .padding(
                horizontal = if (subtle) 9.dp else 12.dp,
                vertical = if (subtle) 2.dp else 5.dp,
            ),
    ) {
        Text(
            text = text,
            color = if (selected) HomePink else HomeSecondary,
            fontSize = if (subtle) 9.sp else 10.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun LiveFollowedRow(
    followed: List<LiveFollowedAnchor>,
    firstFocus: FocusRequester,
    upFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    onClick: (LiveFollowedAnchor) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "关注的主播",
                color = HomeSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${followed.size} 人正在直播",
                color = HomePink,
                fontSize = 9.sp,
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            // The focus ring is drawn outside the avatar cell; 7dp keeps its
            // top/bottom edge inside the LazyRow clip.
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(followed, key = { it.uid }) { anchor ->
                Column(
                    modifier = Modifier
                        .width(58.dp)
                        .then(
                            if (anchor == followed.first()) Modifier.focusRequester(firstFocus)
                            else Modifier
                        )
                        .focusProperties {
                            up = upFocusRequester
                            down = downFocusRequester
                        }
                        .duoFocusRing(RoundedCornerShape(8.dp))
                        .duoClickable { onClick(anchor) }
                        .padding(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(38.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(HomeCardBg)
                                .border(1.5.dp, HomePink, CircleShape),
                        ) {
                            if (anchor.face.isNotBlank()) {
                                AsyncImage(
                                    model = fixPic(anchor.face),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(HomePink)
                                .border(1.5.dp, HomeBg, CircleShape),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = anchor.uname,
                        color = HomeSecondary,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveRoomCard(
    room: LiveRoomItem,
    focusRequester: FocusRequester?,
    upFocusRequester: FocusRequester?,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val area = listOf(room.parentName, room.areaName)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" · ")
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(
                if (upFocusRequester != null) {
                    Modifier.focusProperties { up = upFocusRequester }
                } else {
                    Modifier
                }
            )
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.hasFocus)
            }
            .duoClickable(onClick = onClick)
            .padding(vertical = CardFocusClearance),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .duoFocusRing(shape, focused = focused)
                .clip(shape)
                .background(HomeCardBg),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(HomeCardBg),
            ) {
                if (room.displayCover.isNotBlank()) {
                    AsyncImage(
                        model = fixPic(room.displayCover),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(HomePink)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "直播中",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (room.online > 0) {
                    Text(
                        text = "${formatCount(room.online)} 人气",
                        color = Color.White,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
                Text(
                    text = room.title,
                    color = HomeText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(HomeCardBg),
                    ) {
                        if (room.face.isNotBlank()) {
                            AsyncImage(
                                model = fixPic(room.face),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = room.uname,
                        color = HomeSecondary,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (area.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = area,
                            color = HomeSecondary.copy(alpha = 0.7f),
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PgcSeasonGrid(
    items: List<PgcSeasonItem>,
    firstFocus: FocusRequester,
    onFocused: (PgcSeasonItem) -> Unit,
    onFocusCleared: () -> Unit,
    onClick: (PgcSeasonItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 0.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { _, item -> item.seasonId }) { index, season ->
            PgcSeasonCard(
                season = season,
                focusRequester = if (index == 0) firstFocus else null,
                onFocusChanged = { focused ->
                    if (focused) onFocused(season) else onFocusCleared()
                },
                onClick = { onClick(season) },
            )
        }
    }
}

@Composable
private fun PgcSeasonCard(
    season: PgcSeasonItem,
    focusRequester: FocusRequester?,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.hasFocus)
            }
            .duoClickable(onClick = onClick)
            .padding(vertical = CardFocusClearance),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .duoFocusRing(RoundedCornerShape(6.dp), focused = focused)
                .padding(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(HomeCardBg),
            ) {
                AsyncImage(
                    model = fixPic(season.cover),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (season.badge.isNotBlank()) {
                    Text(
                        text = season.badge,
                        color = Color.White,
                        fontSize = 8.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(HomePink.copy(alpha = 0.9f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                if (season.rating.isNotBlank()) {
                    Text(
                        text = season.rating,
                        color = Color(0xFFFFD166),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = season.title,
                color = HomeText,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (season.indexShow.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = season.indexShow,
                    color = HomeSecondary,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun HomeTab.topGlyph(): TopGlyph = when (this) {
    HomeTab.LIVE -> TopGlyph.LIVE
    HomeTab.RECOMMEND -> TopGlyph.HOME
    HomeTab.POPULAR -> TopGlyph.TRENDING
    HomeTab.RANKING -> TopGlyph.RANKING
    HomeTab.ANIME -> TopGlyph.BANGUMI
    HomeTab.MOVIE -> TopGlyph.MOVIE
}

private fun HomeTab.topAccent(): Long = when (this) {
    HomeTab.LIVE -> 0xFFFB7299
    HomeTab.RECOMMEND -> 0xFFFB7299
    HomeTab.POPULAR -> 0xFFFFB35C
    HomeTab.RANKING -> 0xFFFB7299
    HomeTab.ANIME -> 0xFFB18CFF
    HomeTab.MOVIE -> 0xFF00A1D6
}

private fun homeTabAmbient(tab: HomeTab): TopAmbientUi = TopAmbientUi(
    eyebrow = "HOME / ${tab.label}",
    title = when (tab) {
        HomeTab.LIVE -> "正在直播。"
        HomeTab.RECOMMEND -> "为你推荐。"
        HomeTab.POPULAR -> "此刻正在热门。"
        HomeTab.RANKING -> "今天什么最火。"
        HomeTab.ANIME -> "番剧时间。"
        HomeTab.MOVIE -> "影视精选。"
    },
    subtitle = when (tab) {
        HomeTab.LIVE -> "聚焦直播间，上屏会展示封面、主播与实时人气；按 A 直接进入直播。"
        HomeTab.RECOMMEND -> "下屏滑动浏览，焦点停在卡片上，上屏立刻展示封面、简介与 UP 主。"
        HomeTab.POPULAR -> "全站热度最高的视频，按热度持续更新。"
        HomeTab.RANKING -> "每日排行榜，从全站到分区，一屏看完。"
        HomeTab.ANIME -> "追番、补剧；聚焦海报，上屏展示竖版封面与评分。"
        HomeTab.MOVIE -> "电影、纪录片与国创，聚焦即可预览。"
    },
    glyph = tab.topGlyph(),
    accent = tab.topAccent(),
)

private fun homeEmptyAmbient(tab: HomeTab, error: String?): TopAmbientUi = TopAmbientUi(
    eyebrow = "HOME / ${tab.label}",
    title = if (error != null) "加载失败。" else "这个频道暂时没有内容。",
    subtitle = if (error != null) {
        "下屏重试；网络恢复后上屏会继续更新。"
    } else {
        "换一个 tab，或稍后再回来看看。"
    },
    glyph = tab.topGlyph(),
    accent = tab.topAccent(),
)

private fun PgcSeasonItem.toTopPreview(tab: HomeTab): TopPreviewUi = TopPreviewUi(
    bvid = "",
    title = title,
    coverUrl = fixPic(cover),
    ownerName = "哔哩哔哩${tab.label}",
    ownerLabel = if (tab.isPgc) "番剧 / 影视" else "UP 主",
    description = listOfNotNull(
        rating.takeIf { it.isNotBlank() },
        badge.takeIf { it.isNotBlank() },
        indexShow.takeIf { it.isNotBlank() },
    ).joinToString(" · "),
    category = tab.label,
    coverFit = true,
)

@Composable
private fun HomeVideoCard(
    item: HomeVideoItem,
    focusRequester: FocusRequester?,
    onFocusChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.hasFocus)
            }
            .duoClickable(onClick = onClick)
            .padding(vertical = CardFocusClearance),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .duoFocusRing(RoundedCornerShape(6.dp), focused = focused)
                .padding(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(HomeCardBg),
            ) {
                AsyncImage(
                    model = fixPic(item.pic),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (item.duration > 0) {
                    Text(
                        text = formatDuration(item.duration),
                        color = Color.White,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.title,
                color = HomeText,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    item.owner?.name?.takeIf { it.isNotBlank() }?.let { append(it) }
                    val views = item.stat?.view ?: 0L
                    if (views > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${formatCount(views)}播放")
                    }
                    item.reason?.content?.takeIf { it.isNotBlank() }?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it)
                    }
                },
                color = HomeSecondary,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun HomeVideoItem.toTopPreview(): TopPreviewUi = TopPreviewUi(
    bvid = bvid,
    title = title,
    coverUrl = pic,
    ownerName = owner?.name.orEmpty(),
    ownerFace = owner?.face.orEmpty(),
    playCount = stat?.view ?: 0L,
    danmakuCount = stat?.danmaku ?: 0L,
    pubdate = pubdate,
    duration = if (duration > 0) formatDuration(duration) else "",
)

private fun LiveRoomItem.toTopPreview(): TopPreviewUi {
    val area = listOf(parentName, areaName)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" · ")
    return TopPreviewUi(
        // Live rooms have no bvid; the top-screen host shows this seed as-is
        // instead of trying to fetch UGC detail.
        bvid = "",
        title = title,
        coverUrl = fixPic(displayCover),
        ownerName = uname,
        ownerFace = face,
        category = area,
        description = listOf("正在直播", area)
            .filter { it.isNotBlank() }
            .joinToString(" · "),
        eyebrow = "● 直播中 · 即将进入",
        metaLine = buildString {
            if (online > 0) append("${formatCount(online)} 人气")
            if (area.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(area)
            }
        },
        ownerLabel = "主播",
        actionHint = "进入直播间 · B 返回",
    )
}

private fun formatDuration(seconds: Long): String {
    val total = seconds.coerceAtLeast(0L)
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
