package cn.xfangfang.biliduo.ui.channel

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cn.xfangfang.biliduo.data.model.SeasonArchiveItem
import cn.xfangfang.biliduo.data.model.SeasonSummary
import cn.xfangfang.biliduo.data.model.SpaceAccInfo
import cn.xfangfang.biliduo.data.model.UserCardData
import cn.xfangfang.biliduo.data.model.UserVideoItem
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.common.DuoConfirmOverlay
import cn.xfangfang.biliduo.ui.common.DuoIconAction
import cn.xfangfang.biliduo.ui.common.DuoPageHeader
import cn.xfangfang.biliduo.ui.common.DuoPalette
import cn.xfangfang.biliduo.ui.common.DuoPillButton
import cn.xfangfang.biliduo.ui.common.DuoSearchField
import cn.xfangfang.biliduo.ui.common.ScrollToTopFab
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.ui.duo.duoTabCycling
import cn.xfangfang.biliduo.ui.detail.FollowAction
import cn.xfangfang.biliduo.ui.top.TopAmbientUi
import cn.xfangfang.biliduo.ui.top.TopGlyph
import cn.xfangfang.biliduo.ui.top.TopPersonUi
import cn.xfangfang.biliduo.ui.top.TopPreviewUi
import cn.xfangfang.biliduo.ui.detail.followActionForClick
import cn.xfangfang.biliduo.ui.detail.followButtonLabel
import cn.xfangfang.biliduo.ui.common.VideoCard
import cn.xfangfang.biliduo.ui.common.VideoCardSpec
import cn.xfangfang.biliduo.util.fixPic
import cn.xfangfang.biliduo.util.formatCount
import cn.xfangfang.biliduo.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    mid: Long,
    onBack: () -> Unit,
    onVideoClick: (bvid: String) -> Unit,
    onVideoFocused: (TopPreviewUi) -> Unit = {},
    onTopPerson: (TopPersonUi) -> Unit = {},
    onFocusCleared: () -> Unit = {},
    onTopAmbient: (TopAmbientUi) -> Unit = {},
    session: DuoSessionViewModel? = null,
    viewModel: ChannelViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showUnfollowConfirm by remember { mutableStateOf(false) }
    var profileFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val searchActionFocus = remember { FocusRequester() }
    val followFocus = remember { FocusRequester() }
    val firstVideoFocus = remember { FocusRequester() }
    val firstSeasonFocus = remember { FocusRequester() }
    val modeFocusers = remember { List(2) { FocusRequester() } }
    var pendingModeFocus by remember { mutableStateOf(false) }
    var focusContentOnModeShortcut by remember { mutableStateOf(false) }
    var lastModeShortcutAt by remember { mutableStateOf(0L) }
    LaunchedEffect(uiState.viewMode, pendingModeFocus) {
        if (pendingModeFocus) {
            delay(80L)
            val index = if (uiState.viewMode == ChannelViewMode.VIDEOS) 0 else 1
            modeFocusers[index].let { runCatching { it.requestFocus() } }
            pendingModeFocus = false
        }
    }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val inSeason = uiState.viewMode == ChannelViewMode.SEASON_DETAIL
    val inSearch = isSearching || uiState.viewMode == ChannelViewMode.SEARCH

    fun channelPerson(): TopPersonUi = buildChannelPersonUi(
        mid = mid,
        info = uiState.spaceAccInfo,
        card = uiState.userCard,
        videos = uiState.videos,
        moments = uiState.moments,
    )

    LaunchedEffect(
        uiState.spaceAccInfo?.name,
        uiState.userCard?.follower,
        uiState.userCard?.archive_count,
        uiState.videos.size,
        uiState.moments.size,
        profileFocused,
    ) {
        if (profileFocused) onTopPerson(channelPerson())
    }

    fun closeUnfollowConfirm() {
        showUnfollowConfirm = false
        scope.launch {
            delay(120)
            runCatching { followFocus.requestFocus() }
        }
    }

    LaunchedEffect(showUnfollowConfirm, inSeason, inSearch) {
        when {
            showUnfollowConfirm -> session?.setBackInterceptor {
                closeUnfollowConfirm()
                true
            }
            inSearch -> session?.setBackInterceptor {
                keyboardController?.hide()
                isSearching = false
                viewModel.clearSearch()
                true
            }
            inSeason -> session?.setBackInterceptor {
                viewModel.backFromSeason()
                true
            }
            else -> session?.setBackInterceptor(null)
        }
    }
    DisposableEffect(session) {
        onDispose { session?.setBackInterceptor(null) }
    }

    LaunchedEffect(mid) {
        viewModel.load(mid)
    }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            searchQuery = ""
            // C: keep focus on the header search icon, not the input.
            delay(200)
            runCatching { searchActionFocus.requestFocus() }
        }
    }

    DisposableEffect(Unit) {
        onDispose { onFocusCleared() }
    }

    // Initial load focuses the first video. L1/R1 tab switches move into the
    // list of the selected tab; direct chip activation keeps the chip ring.
    var initialVideoFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(
        uiState.viewMode,
        uiState.isLoading,
        uiState.isLoadingSeasons,
        uiState.videos.size,
        uiState.seasons.size,
        focusContentOnModeShortcut,
    ) {
        if (focusContentOnModeShortcut) {
            if (uiState.isLoading || uiState.isLoadingSeasons) return@LaunchedEffect
            delay(120)
            val target = when (uiState.viewMode) {
                ChannelViewMode.VIDEOS -> {
                    if (uiState.videos.isNotEmpty()) firstVideoFocus else modeFocusers[0]
                }
                ChannelViewMode.SEASONS -> {
                    if (uiState.seasons.isNotEmpty()) firstSeasonFocus else modeFocusers[1]
                }
                else -> return@LaunchedEffect
            }
            runCatching { target.requestFocus() }
            focusContentOnModeShortcut = false
            return@LaunchedEffect
        }
        if (!initialVideoFocusDone && uiState.viewMode == ChannelViewMode.VIDEOS &&
            !uiState.isLoading && uiState.videos.isNotEmpty()
        ) {
            delay(120)
            if (SystemClock.uptimeMillis() - lastModeShortcutAt < 600L) return@LaunchedEffect
            runCatching { firstVideoFocus.requestFocus() }
            initialVideoFocusDone = true
        }
    }

    LaunchedEffect(uiState.spaceAccInfo?.name) {
        val name = uiState.spaceAccInfo?.name?.takeIf { it.isNotBlank() }
        onTopAmbient(
            TopAmbientUi(
                eyebrow = "SPACE / UP 主空间",
                title = name ?: "UP 主空间",
                subtitle = "下屏浏览视频与合集；A 播放，B 返回。",
                hint = "上屏只读 · 焦点视频显示预览",
                glyph = TopGlyph.PERSON,
            )
        )
    }

    BackHandler(enabled = inSeason || inSearch) {
        when {
            inSearch -> {
                isSearching = false
                viewModel.clearSearch()
            }
            inSeason -> viewModel.backFromSeason()
        }
    }
    BackHandler(showUnfollowConfirm) { closeUnfollowConfirm() }

    // L1/R1 mirror the 视频/合集 chips. Both directions toggle: with two tabs
    // there is no meaningful "further left/right" direction.
    val tabShortcut = if (
        uiState.viewMode == ChannelViewMode.VIDEOS || uiState.viewMode == ChannelViewMode.SEASONS
    ) {
        val other = if (uiState.viewMode == ChannelViewMode.SEASONS) {
            ChannelViewMode.VIDEOS
        } else {
            ChannelViewMode.SEASONS
        }
        Modifier.duoTabCycling(
            onPreviousTab = {
                viewModel.switchMode(other)
                pendingModeFocus = false
                focusContentOnModeShortcut = true
                lastModeShortcutAt = SystemClock.uptimeMillis()
            },
            onNextTab = {
                viewModel.switchMode(other)
                pendingModeFocus = false
                focusContentOnModeShortcut = true
                lastModeShortcutAt = SystemClock.uptimeMillis()
            },
        )
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoPalette.Bg)
            .then(tabShortcut),
    ) {
        DuoPageHeader(
            title = when {
                inSeason -> uiState.seasonMeta?.name ?: "合集"
                uiState.viewMode == ChannelViewMode.SEARCH -> "搜索结果"
                else -> "UP 主空间"
            },
            onBack = {
                when {
                    inSearch -> {
                        isSearching = false
                        viewModel.clearSearch()
                    }
                    inSeason -> viewModel.backFromSeason()
                    else -> onBack()
                }
            },
            actions = {
                if (!inSeason) {
                    DuoIconAction(
                        icon = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (isSearching) "取消搜索" else "搜索",
                        tint = if (isSearching) DuoPalette.Pink else DuoPalette.Text,
                        modifier = Modifier.focusRequester(searchActionFocus),
                        onClick = {
                            if (isSearching) {
                                isSearching = false
                                if (uiState.viewMode == ChannelViewMode.SEARCH) {
                                    viewModel.clearSearch()
                                }
                            } else {
                                isSearching = true
                            }
                        },
                    )
                }
            },
        )
        if (isSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DuoSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "搜索 UP 主视频 / 合集",
                    focusRequester = focusRequester,
                    modifier = Modifier.weight(1f),
                    onSubmit = {
                        if (searchQuery.isNotBlank()) {
                            isSearching = false
                            keyboardController?.hide()
                            viewModel.search(searchQuery.trim())
                        }
                    },
                    onClear = { searchQuery = "" },
                )
            }
        }

        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            ChannelBody(
                uiState = uiState,
                onVideoClick = onVideoClick,
                onRetry = { viewModel.retry() },
                onVideoFocused = { video ->
                    onVideoFocused(
                        TopPreviewUi(
                            bvid = video.bvid,
                            title = video.title,
                            coverUrl = video.pic,
                            ownerName = video.author.ifBlank { uiState.spaceAccInfo?.name.orEmpty() },
                            playCount = video.playCount,
                            danmakuCount = video.danmakuCount,
                            pubdate = video.pubdate,
                            duration = video.duration,
                            eyebrow = "● 空间视频 · 即将播放",
                            ownerLabel = "UP 主",
                        )
                    )
                },
                onProfileFocused = { focused ->
                    profileFocused = focused
                    if (focused) onTopPerson(channelPerson())
                },
                onSeasonClick = { seasonId, firstBvid -> viewModel.openSeason(seasonId, firstBvid) },
                onSwitchMode = { mode -> viewModel.switchMode(mode) },
                onLoadMore = { viewModel.loadMore() },
                onToggleFollow = {
                    when (followActionForClick(uiState.isFollowed)) {
                        FollowAction.Follow -> viewModel.toggleFollow()
                        FollowAction.ConfirmUnfollow -> showUnfollowConfirm = true
                    }
                },
                followFocus = followFocus,
                firstVideoFocus = firstVideoFocus,
                firstSeasonFocus = firstSeasonFocus,
                modeFocusRequesters = modeFocusers,
                listState = listState,
                modifier = Modifier.fillMaxSize()
            )
            ScrollToTopFab(listState = listState)
        }
    }

    if (showUnfollowConfirm) {
        val upName = uiState.spaceAccInfo?.name ?: ""
        DuoConfirmOverlay(
            title = "取消关注",
            message = "确定取消关注「$upName」？",
            confirmText = "确定",
            danger = true,
            onConfirm = {
                closeUnfollowConfirm()
                viewModel.toggleFollow()
            },
            onCancel = { closeUnfollowConfirm() },
            session = session,
        )
    }
}

@Composable
internal fun ChannelBody(
    uiState: ChannelUiState,
    onVideoClick: (bvid: String) -> Unit,
    onRetry: () -> Unit = {},
    onVideoFocused: (UserVideoItem) -> Unit = {},
    onProfileFocused: (Boolean) -> Unit = {},
    onSeasonClick: (seasonId: Long, firstBvid: String?) -> Unit,
    onSwitchMode: (ChannelViewMode) -> Unit,
    onLoadMore: () -> Unit,
    onToggleFollow: () -> Unit,
    followFocus: FocusRequester? = null,
    firstVideoFocus: FocusRequester? = null,
    firstSeasonFocus: FocusRequester? = null,
    modeFocusRequesters: List<FocusRequester> = emptyList(),
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val nearBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }

    LaunchedEffect(nearBottom, uiState.hasMore, uiState.loadingMore, uiState.viewMode) {
        if (uiState.viewMode == ChannelViewMode.VIDEOS && nearBottom && uiState.hasMore && !uiState.loadingMore) {
            delay(500)
            if (uiState.hasMore && !uiState.loadingMore) {
                onLoadMore()
            }
        }
    }

    val seasonsSelected = uiState.viewMode == ChannelViewMode.SEASONS ||
            uiState.viewMode == ChannelViewMode.SEASON_DETAIL

    Column(modifier = modifier.fillMaxSize()) {
        if (uiState.spaceAccInfo?.name?.isNotBlank() == true) {
            UpProfileCard(
                mid = uiState.mid,
                spaceAccInfo = uiState.spaceAccInfo!!,
                userCard = uiState.userCard,
                isLoggedIn = uiState.isLoggedIn,
                isFollowed = uiState.isFollowed,
                isTogglingFollow = uiState.isTogglingFollow,
                followStatusLoading = uiState.followStatusLoading,
                onToggleFollow = onToggleFollow,
                followFocus = followFocus,
                onFocusChanged = onProfileFocused,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp),
            )
        }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().weight(1f),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "mode_tabs") {
            if (uiState.viewMode == ChannelViewMode.VIDEOS || uiState.viewMode == ChannelViewMode.SEASONS) {
                ChannelModeTabs(
                    videosSelected = !seasonsSelected,
                    videoCount = uiState.userCard?.archive_count?.takeIf { it > 0 }
                        ?: uiState.videos.size,
                    seasonCount = uiState.seasons.size.takeIf { uiState.seasons.isNotEmpty() },
                    focusRequesters = modeFocusRequesters,
                    onSelectVideos = { onSwitchMode(ChannelViewMode.VIDEOS) },
                    onSelectSeasons = { onSwitchMode(ChannelViewMode.SEASONS) },
                )
            }
        }

        when (uiState.viewMode) {
            ChannelViewMode.VIDEOS -> {
                when {
                    uiState.isLoading && uiState.videos.isEmpty() -> {
                        item(key = "vloading") {
                            Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(26.dp))
                            }
                        }
                    }
                    uiState.error != null && uiState.videos.isEmpty() -> {
                        item(key = "verror") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "视频加载失败",
                                    color = DuoPalette.Text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = uiState.error,
                                    color = DuoPalette.Tertiary,
                                    fontSize = 10.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "重试",
                                    color = DuoPalette.Pink,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .duoFocusRing(RoundedCornerShape(6.dp))
                                        .clip(RoundedCornerShape(6.dp))
                                        .duoClickable(onClick = onRetry)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    uiState.videos.isEmpty() -> {
                        item(key = "vempty") {
                            Text(
                                text = "该 UP 主暂无视频",
                                color = DuoPalette.Tertiary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 32.dp),
                            )
                        }
                    }
                    else -> {
                        item(key = "vheader") {
                            ChannelSectionHeader(
                                title = "视频",
                                meta = "共 ${uiState.userCard?.archive_count?.takeIf { it > 0 } ?: uiState.videos.size} 个",
                            )
                        }
                        itemsIndexed(uiState.videos, key = { _, video -> video.bvid }) { index, video ->
                            ChannelVideoCard(
                                video = video,
                                onClick = { onVideoClick(video.bvid) },
                                onFocus = { onVideoFocused(video) },
                                focusRequester = if (index == 0) firstVideoFocus else null,
                            )
                        }
                        item(key = "vbottom") {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                when {
                                    uiState.loadingMore -> CircularProgressIndicator(
                                        Modifier.size(24.dp),
                                        color = DuoPalette.Pink,
                                        strokeWidth = 2.dp,
                                    )
                                    uiState.hasMore -> TextButton(
                                        onClick = onLoadMore,
                                        modifier = Modifier.duoFocusRing(RoundedCornerShape(6.dp)),
                                    ) {
                                        Text("加载更多", color = DuoPalette.Secondary, fontSize = 11.sp)
                                    }
                                    else -> Text(
                                        text = "已全部加载",
                                        color = DuoPalette.Tertiary,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ChannelViewMode.SEASONS -> {
                if (uiState.isLoadingSeasons) {
                    item(key = "sloading") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = DuoPalette.Pink, strokeWidth = 2.dp)
                        }
                    }
                } else if (uiState.seasonError != null) {
                    item(key = "serror") {
                        Text(
                            text = "合集加载失败：${uiState.seasonError}",
                            color = DuoPalette.Tertiary,
                            fontSize = 11.sp,
                        )
                    }
                } else if (uiState.seasons.isEmpty()) {
                    item(key = "sempty") {
                        Text(
                            text = "该 UP 主暂无合集",
                            color = DuoPalette.Tertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 20.dp),
                        )
                    }
                } else {
                    item(key = "sheader") {
                        ChannelSectionHeader(title = "合集", meta = "共 ${uiState.seasons.size} 个")
                    }
                    itemsIndexed(
                        items = uiState.seasons,
                        key = { _, season -> season.meta?.seasonId ?: season.hashCode() },
                    ) { index, season ->
                        SeasonCard(
                            season = season,
                            focusRequester = if (index == 0) firstSeasonFocus else null,
                            onClick = {
                                season.meta?.seasonId?.let { id ->
                                    onSeasonClick(id, season.archives?.firstOrNull()?.bvid)
                                }
                            },
                        )
                    }
                }
            }

            ChannelViewMode.SEASON_DETAIL -> {
                if (uiState.isLoadingSeason) {
                    item(key = "sdloading") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = DuoPalette.Pink, strokeWidth = 2.dp)
                        }
                    }
                } else if (uiState.seasonError != null) {
                    item(key = "sderror") {
                        Text(
                            text = "加载失败：${uiState.seasonError}",
                            color = DuoPalette.Tertiary,
                            fontSize = 11.sp,
                        )
                    }
                } else if (uiState.seasonVideos.isEmpty()) {
                    item(key = "sdempty") {
                        Text(
                            text = "该合集暂无视频",
                            color = DuoPalette.Tertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 20.dp),
                        )
                    }
                } else {
                    item(key = "sdheader") {
                        ChannelSectionHeader(
                            title = "选集",
                            meta = "共 ${uiState.seasonMeta?.total ?: uiState.seasonVideos.size} 个视频",
                        )
                    }
                    items(uiState.seasonVideos, key = { it.bvid }) { item ->
                        SeasonVideoRow(item = item, onClick = { onVideoClick(item.bvid) })
                    }
                }
            }

            ChannelViewMode.SEARCH -> {
                if (uiState.isLoading) {
                    item(key = "schloading") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = DuoPalette.Pink, strokeWidth = 2.dp)
                        }
                    }
                } else {
                    if (uiState.searchSeasons.isNotEmpty()) {
                        item(key = "sch_s_header") {
                            ChannelSectionHeader(
                                title = "合集",
                                meta = "${uiState.searchSeasons.size} 个",
                            )
                        }
                        items(uiState.searchSeasons, key = { "s_${it.meta?.seasonId ?: it.hashCode()}" }) { season ->
                            SeasonCard(season = season, onClick = {
                                season.meta?.seasonId?.let { id ->
                                    onSeasonClick(id, season.archives?.firstOrNull()?.bvid)
                                }
                            })
                        }
                    }
                    if (uiState.searchVideos.isNotEmpty()) {
                        item(key = "sch_v_header") {
                            ChannelSectionHeader(
                                title = "视频",
                                meta = "${uiState.searchVideos.size} 个",
                            )
                        }
                        items(uiState.searchVideos, key = { "v_${it.bvid}" }) { video ->
                            ChannelVideoCard(
                                video = video,
                                onClick = { onVideoClick(video.bvid) },
                                onFocus = { onVideoFocused(video) },
                            )
                        }
                    }
                    if (uiState.searchSeasons.isEmpty() && uiState.searchVideos.isEmpty()) {
                        item(key = "schempty") {
                            Text(
                                text = "未找到相关内容",
                                color = DuoPalette.Tertiary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 20.dp),
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
private fun ChannelSectionHeader(title: String, meta: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = DuoPalette.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = meta,
            color = DuoPalette.Tertiary,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun ChannelModeTabs(
    videosSelected: Boolean,
    videoCount: Int?,
    seasonCount: Int?,
    onSelectVideos: () -> Unit,
    onSelectSeasons: () -> Unit,
    focusRequesters: List<FocusRequester> = emptyList(),
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Room for the 30dp tabs' 5.75dp focus ring.
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelModeTab(
            label = "视频",
            count = videoCount,
            selected = videosSelected,
            focusRequester = focusRequesters.getOrNull(0),
            onClick = onSelectVideos,
        )
        Spacer(Modifier.width(8.dp))
        ChannelModeTab(
            label = "合集",
            count = seasonCount,
            selected = !videosSelected,
            focusRequester = focusRequesters.getOrNull(1),
            onClick = onSelectSeasons,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "L1 / R1 切换",
            color = DuoPalette.Tertiary,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun ChannelModeTab(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    Row(
        modifier = Modifier
            .height(30.dp)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .duoFocusRing(RoundedCornerShape(15.dp))
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) DuoPalette.Pink.copy(alpha = 0.16f) else DuoPalette.SurfaceHigh)
            .border(
                width = 1.dp,
                color = if (selected) DuoPalette.Pink.copy(alpha = 0.45f) else Color.Transparent,
                shape = RoundedCornerShape(15.dp),
            )
            .duoClickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) DuoPalette.Pink else DuoPalette.Secondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
        if (count != null && count > 0) {
            Spacer(Modifier.width(5.dp))
            Text(
                text = count.toString(),
                color = if (selected) DuoPalette.Pink.copy(alpha = 0.8f) else DuoPalette.Tertiary,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
internal fun UpProfileCard(
    mid: Long,
    spaceAccInfo: SpaceAccInfo,
    userCard: UserCardData?,
    isLoggedIn: Boolean = false,
    isFollowed: Boolean = false,
    isTogglingFollow: Boolean = false,
    followStatusLoading: Boolean = false,
    onToggleFollow: () -> Unit = {},
    followFocus: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val fans = userCard?.follower ?: 0
    val archiveCount = userCard?.archive_count?.takeIf { it > 0 }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .duoFocusRing(RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(DuoPalette.Surface)
            .onFocusChanged { onFocusChanged(it.hasFocus) }
            .focusable()
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(DuoPalette.SurfaceHigh)
                    .border(1.5.dp, DuoPalette.Pink.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (spaceAccInfo.face.isNotBlank()) {
                    AsyncImage(
                        model = fixPic(spaceAccInfo.face),
                        contentDescription = spaceAccInfo.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = spaceAccInfo.name,
                        modifier = Modifier.size(30.dp),
                        tint = DuoPalette.Secondary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = spaceAccInfo.name,
                        color = DuoPalette.Text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "UP 主",
                        color = DuoPalette.Pink,
                        fontSize = 8.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DuoPalette.Pink.copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                    if (isFollowed) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "已关注",
                            color = DuoPalette.Secondary,
                            fontSize = 8.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(DuoPalette.SurfaceHigh)
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "UID $mid",
                    color = DuoPalette.Tertiary,
                    fontSize = 9.sp,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = spaceAccInfo.sign.ifBlank { "这个 UP 主还没有填写签名。" },
                    color = DuoPalette.Secondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isLoggedIn) {
                Spacer(Modifier.width(10.dp))
                DuoPillButton(
                    text = if (followStatusLoading) "…" else followButtonLabel(isFollowed),
                    primary = !isFollowed,
                    enabled = !isTogglingFollow && !followStatusLoading,
                    focusRequester = followFocus,
                    onClick = onToggleFollow,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileStat(value = formatCount(fans.toLong()), label = "粉丝")
            Spacer(Modifier.width(18.dp))
            Box(Modifier.width(1.dp).height(24.dp).background(DuoPalette.Outline))
            Spacer(Modifier.width(18.dp))
            ProfileStat(
                value = archiveCount?.let { formatCount(it.toLong()) } ?: "—",
                label = "视频",
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "选中资料卡 · 上屏动态/视频",
                color = DuoPalette.Tertiary,
                fontSize = 8.sp,
            )
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column {
        Text(
            text = value,
            color = DuoPalette.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = label,
            color = DuoPalette.Tertiary,
            fontSize = 8.sp,
        )
    }
}

@Composable
internal fun SeasonCard(
    season: SeasonSummary,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val meta = season.meta
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .duoFocusRing(RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .duoClickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 112.dp, height = 63.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(DuoPalette.SurfaceHigh),
        ) {
            AsyncImage(
                model = fixPic(meta?.cover ?: ""),
                contentDescription = meta?.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = meta?.name ?: "合集",
                color = DuoPalette.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "共 ${meta?.total ?: 0} 个视频",
                color = DuoPalette.Tertiary,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
internal fun SeasonVideoRow(item: SeasonArchiveItem, onClick: () -> Unit) {
    ChannelVideoRow(
        coverUrl = item.pic,
        title = item.title,
        playCount = item.stat?.view ?: 0,
        durationText = formatDuration(item.duration),
        pubdate = item.pubdate,
        onClick = onClick,
    )
}
