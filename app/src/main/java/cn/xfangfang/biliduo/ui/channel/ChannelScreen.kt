package cn.xfangfang.biliduo.ui.channel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import cn.xfangfang.biliduo.data.model.UserVideoItem
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.common.DuoConfirmOverlay
import cn.xfangfang.biliduo.ui.common.DuoPalette
import cn.xfangfang.biliduo.ui.common.ScrollToTopFab
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.ui.duo.duoTabCycling
import cn.xfangfang.biliduo.ui.detail.FollowAction
import cn.xfangfang.biliduo.ui.top.TopAmbientUi
import cn.xfangfang.biliduo.ui.top.TopGlyph
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
    onFocusCleared: () -> Unit = {},
    onTopAmbient: (TopAmbientUi) -> Unit = {},
    session: DuoSessionViewModel? = null,
    viewModel: ChannelViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showUnfollowConfirm by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val followFocus = remember { FocusRequester() }
    val firstVideoFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    fun closeUnfollowConfirm() {
        showUnfollowConfirm = false
        scope.launch {
            delay(120)
            runCatching { followFocus.requestFocus() }
        }
    }

    LaunchedEffect(showUnfollowConfirm) {
        if (showUnfollowConfirm) {
            session?.setBackInterceptor {
                closeUnfollowConfirm()
                true
            }
        } else {
            session?.setBackInterceptor(null)
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
            delay(200)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val inSeason = uiState.viewMode == ChannelViewMode.SEASON_DETAIL
    val inSearch = isSearching || uiState.viewMode == ChannelViewMode.SEARCH

    DisposableEffect(Unit) {
        onDispose { onFocusCleared() }
    }

    LaunchedEffect(uiState.videos.size, uiState.viewMode) {
        if (uiState.viewMode == ChannelViewMode.VIDEOS && uiState.videos.isNotEmpty()) {
            delay(120)
            runCatching { firstVideoFocus.requestFocus() }
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
            onPreviousTab = { viewModel.switchMode(other) },
            onNextTab = { viewModel.switchMode(other) },
        )
    } else {
        Modifier
    }

    Scaffold(
        modifier = tabShortcut,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                inSearch -> {
                                    isSearching = false
                                    viewModel.clearSearch()
                                }
                                inSeason -> viewModel.backFromSeason()
                                else -> onBack()
                            }
                        },
                        modifier = Modifier.duoFocusRing(CircleShape),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isSearching) "取消" else "返回"
                        )
                    }
                },
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = { Text("搜索UP主视频/合集") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (searchQuery.isNotBlank()) {
                                        isSearching = false
                                        keyboardController?.hide()
                                        viewModel.search(searchQuery.trim())
                                    }
                                }
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.duoFocusRing(CircleShape),
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "清除")
                                    }
                                }
                            }
                        )
                    } else if (inSeason) {
                        Text(uiState.seasonMeta?.name ?: "合集")
                    } else if (uiState.viewMode == ChannelViewMode.SEARCH) {
                        Text("搜索结果")
                    } else {
                        Text("UP主视频")
                    }
                },
                actions = {
                    if (!isSearching) {
                        IconButton(
                            onClick = { isSearching = true },
                            modifier = Modifier.duoFocusRing(CircleShape),
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
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
    onSeasonClick: (seasonId: Long, firstBvid: String?) -> Unit,
    onSwitchMode: (ChannelViewMode) -> Unit,
    onLoadMore: () -> Unit,
    onToggleFollow: () -> Unit,
    followFocus: FocusRequester? = null,
    firstVideoFocus: FocusRequester? = null,
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
            UpInfoCard(
                spaceAccInfo = uiState.spaceAccInfo!!,
                isLoggedIn = uiState.isLoggedIn,
                isFollowed = uiState.isFollowed,
                isTogglingFollow = uiState.isTogglingFollow,
                followStatusLoading = uiState.followStatusLoading,
                onToggleFollow = onToggleFollow,
                followFocus = followFocus,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().weight(1f),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "mode_tabs") {
            if (uiState.viewMode == ChannelViewMode.VIDEOS || uiState.viewMode == ChannelViewMode.SEASONS) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !seasonsSelected,
                        onClick = { onSwitchMode(ChannelViewMode.VIDEOS) },
                        label = { Text("视频") },
                        modifier = Modifier.duoFocusRing(RoundedCornerShape(8.dp)),
                    )
                    FilterChip(
                        selected = seasonsSelected,
                        onClick = { onSwitchMode(ChannelViewMode.SEASONS) },
                        label = { Text("合集") },
                        modifier = Modifier.duoFocusRing(RoundedCornerShape(8.dp)),
                    )
                }
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
                                TextButton(
                                    onClick = onRetry,
                                    modifier = Modifier.duoFocusRing(RoundedCornerShape(6.dp)),
                                ) { Text("重试", color = DuoPalette.Pink) }
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
                            Text(
                                text = "共 ${uiState.videos.size} 个视频",
                                style = MaterialTheme.typography.titleMedium
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
                                    uiState.loadingMore -> CircularProgressIndicator(Modifier.size(24.dp))
                                    uiState.hasMore -> TextButton(
                                        onClick = onLoadMore,
                                        modifier = Modifier.duoFocusRing(RoundedCornerShape(6.dp)),
                                    ) {
                                        Text("加载更多", style = MaterialTheme.typography.bodySmall)
                                    }
                                    else -> Text(
                                        text = "已全部加载",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                } else if (uiState.seasonError != null) {
                    item(key = "serror") {
                        Text("合集加载失败：${uiState.seasonError}")
                    }
                } else if (uiState.seasons.isEmpty()) {
                    item(key = "sempty") {
                        Text("该UP主暂无合集", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    item(key = "sheader") {
                        Text(
                            text = "共 ${uiState.seasons.size} 个合集",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(uiState.seasons, key = { it.meta?.seasonId ?: it.hashCode() }) { season ->
                        SeasonCard(season = season, onClick = {
                            season.meta?.seasonId?.let { id ->
                                onSeasonClick(id, season.archives?.firstOrNull()?.bvid)
                            }
                        })
                    }
                }
            }

            ChannelViewMode.SEASON_DETAIL -> {
                if (uiState.isLoadingSeason) {
                    item(key = "sdloading") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                } else if (uiState.seasonError != null) {
                    item(key = "sderror") {
                        Text("加载失败：${uiState.seasonError}")
                    }
                } else if (uiState.seasonVideos.isEmpty()) {
                    item(key = "sdempty") {
                        Text("该合集暂无视频", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    item(key = "sdheader") {
                        Text(
                            text = "共 ${uiState.seasonMeta?.total ?: uiState.seasonVideos.size} 个视频",
                            style = MaterialTheme.typography.titleMedium
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
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                } else {
                    if (uiState.searchSeasons.isNotEmpty()) {
                        item(key = "sch_s_header") {
                            Text(
                                text = "合集 (${uiState.searchSeasons.size})",
                                style = MaterialTheme.typography.titleMedium
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
                            Text(
                                text = "视频 (${uiState.searchVideos.size})",
                                style = MaterialTheme.typography.titleMedium
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
                            Text("未找到相关内容", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
internal fun UpInfoCard(
    spaceAccInfo: SpaceAccInfo,
    isLoggedIn: Boolean = false,
    isFollowed: Boolean = false,
    isTogglingFollow: Boolean = false,
    followStatusLoading: Boolean = false,
    onToggleFollow: () -> Unit = {},
    followFocus: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (spaceAccInfo.face.isNotBlank()) {
                AsyncImage(
                    model = fixPic(spaceAccInfo.face),
                    contentDescription = spaceAccInfo.name,
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = spaceAccInfo.name,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                SelectionContainer {
                    Text(
                        text = spaceAccInfo.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (spaceAccInfo.sign.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    SelectionContainer {
                        Text(
                            text = spaceAccInfo.sign,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (isLoggedIn) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onToggleFollow,
                    enabled = !isTogglingFollow && !followStatusLoading,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier
                        .duoFocusRing(RoundedCornerShape(20.dp))
                        .then(
                            if (followFocus != null) Modifier.focusRequester(followFocus) else Modifier
                        ),
                ) {
                    if (followStatusLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = followButtonLabel(isFollowed),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SeasonCard(season: SeasonSummary, onClick: () -> Unit) {
    val meta = season.meta
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
