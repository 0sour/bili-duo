package cn.xfangfang.biliduo.ui.history

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cn.xfangfang.biliduo.data.model.HistoryItem
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.common.DuoConfirmOverlay
import cn.xfangfang.biliduo.ui.common.DuoEmptyState
import cn.xfangfang.biliduo.ui.common.DuoIconAction
import cn.xfangfang.biliduo.ui.common.DuoPageHeader
import cn.xfangfang.biliduo.ui.common.DuoPalette
import cn.xfangfang.biliduo.ui.common.DuoPillButton
import cn.xfangfang.biliduo.ui.common.DuoSearchField
import cn.xfangfang.biliduo.ui.duo.requestPageFocus
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.ui.top.TopAmbientUi
import cn.xfangfang.biliduo.ui.top.TopGlyph
import cn.xfangfang.biliduo.ui.top.TopPreviewUi
import cn.xfangfang.biliduo.util.fixPic
import cn.xfangfang.biliduo.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onVideoClick: (bvid: String) -> Unit,
    onLogin: () -> Unit = {},
    onTopAmbient: (TopAmbientUi) -> Unit = {},
    onVideoFocused: (TopPreviewUi) -> Unit = {},
    onFocusCleared: () -> Unit = {},
    session: DuoSessionViewModel? = null,
    viewModel: HistoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val searchFocus = remember { FocusRequester() }
    val searchCancelFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }
    var searchMode by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<HistoryItem?>(null) }
    var deleteFocus by remember { mutableStateOf<FocusRequester?>(null) }

    val displayItems by remember {
        derivedStateOf {
            if (uiState.searchQuery.isBlank()) uiState.items else uiState.searchResults
        }
    }

    val loggedIn = cn.xfangfang.biliduo.data.api.BilibiliApi.parseSelfMid() != null

    LaunchedEffect(Unit) {
        if (loggedIn) viewModel.loadHistory()
    }
    LaunchedEffect(loggedIn) {
        if (!loggedIn) {
            delay(80)
            runCatching { retryFocus.requestPageFocus() }
        }
    }
    LaunchedEffect(loggedIn, uiState.isLoading, displayItems.size, uiState.searchQuery) {
        when {
            !loggedIn -> onTopAmbient(
                TopAmbientUi(
                    eyebrow = "HISTORY / 历史",
                    title = "登录后，\n找回看过的视频。",
                    subtitle = "云端观看记录与进度会显示在这里；下屏可搜索和删除单条记录。",
                    glyph = TopGlyph.HISTORY,
                )
            )
            !uiState.isLoading && displayItems.isEmpty() -> onTopAmbient(
                TopAmbientUi(
                    eyebrow = "HISTORY / 历史",
                    title = if (uiState.searchQuery.isBlank()) "还没有观看记录。" else "没有找到匹配的视频。",
                    subtitle = if (uiState.searchQuery.isBlank()) {
                        "播放过的视频会按日期出现在这里，上屏会预览封面与进度。"
                    } else {
                        "换一个关键词，或清空搜索查看全部历史。"
                    },
                    glyph = TopGlyph.HISTORY,
                )
            )
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 3 && totalItems > 0 &&
                uiState.hasMore && !uiState.isLoadingMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    LaunchedEffect(uiState.isLoading, searchMode) {
        if (!searchMode && displayItems.isNotEmpty() && !uiState.isLoading) {
            delay(80)
            runCatching { firstItemFocus.requestPageFocus() }
        }
        if (displayItems.isEmpty()) onFocusCleared()
    }

    LaunchedEffect(searchMode) {
        if (searchMode) {
            // C: keep the ring on 取消 instead of focusing the input.
            delay(80)
            runCatching { searchCancelFocus.requestPageFocus() }
        } else {
            keyboard?.hide()
            viewModel.setSearchQuery("")
        }
    }

    LaunchedEffect(uiState.actionError) {
        uiState.actionError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearActionError()
        }
    }

    fun closeDeleteConfirm() {
        val focus = deleteFocus
        confirmDelete = null
        scope.launch {
            delay(120)
            runCatching { focus?.requestPageFocus() }
        }
    }

    LaunchedEffect(searchMode, confirmDelete, session) {
        when {
            confirmDelete != null -> session?.setBackInterceptor {
                closeDeleteConfirm()
                true
            }
            searchMode -> session?.setBackInterceptor {
                searchMode = false
                true
            }
            else -> session?.setBackInterceptor(null)
        }
    }
    DisposableEffect(session) {
        onDispose { session?.setBackInterceptor(null) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoPalette.Bg),
    ) {
        DuoPageHeader(
            title = "观看历史",
            onBack = {
                if (searchMode) searchMode = false else {
                    keyboard?.hide()
                    onBack()
                }
            },
            actions = {
                if (loggedIn && !searchMode) {
                    DuoIconAction(
                        icon = Icons.Default.Search,
                        contentDescription = "搜索历史",
                        tint = DuoPalette.Text,
                        onClick = { searchMode = true },
                    )
                }
            },
        )

        if (searchMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DuoSearchField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = "搜索观看历史",
                    focusRequester = searchFocus,
                    modifier = Modifier.weight(1f),
                    onSubmit = { keyboard?.hide() },
                    onClear = { viewModel.setSearchQuery("") },
                )
                Spacer(Modifier.width(8.dp))
                DuoPillButton(
                    text = "取消",
                    onClick = { searchMode = false },
                    focusRequester = searchCancelFocus,
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !loggedIn -> {
                    DuoEmptyState(
                        title = "登录后查看观看历史",
                        hint = "登录后可同步云端观看记录与进度",
                        actionText = "去登录",
                        onAction = onLogin,
                        focusRequester = retryFocus,
                    )
                }
                uiState.isLoading && uiState.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = DuoPalette.Pink,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                uiState.error != null && uiState.items.isEmpty() -> {
                    DuoEmptyState(
                        title = "加载失败",
                        hint = uiState.error,
                        actionText = "重试",
                        onAction = viewModel::loadHistory,
                        focusRequester = retryFocus,
                    )
                }
                uiState.isSearchLoading && displayItems.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = DuoPalette.Pink,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                displayItems.isEmpty() -> {
                    DuoEmptyState(
                        title = if (uiState.searchQuery.isBlank()) "暂无观看记录" else "没有找到匹配的视频",
                        hint = if (uiState.searchQuery.isBlank()) {
                            "播放过的视频会出现在这里"
                        } else {
                            null
                        },
                    )
                }
                else -> {
                    val grouped = if (uiState.searchQuery.isBlank()) {
                        displayItems.groupBy { historyDateLabel(it.viewAt) }
                    } else {
                        linkedMapOf("搜索结果" to displayItems)
                    }
                    val firstKey = displayItems.firstOrNull()?.let {
                        "${it.bvid}_${it.viewAt}_${it.cid}"
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        grouped.forEach { (label, list) ->
                            item(key = "header_$label") {
                                Text(
                                    text = label,
                                    color = DuoPalette.Tertiary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
                                )
                            }
                            items(
                                items = list,
                                key = { "${it.bvid}_${it.viewAt}_${it.cid}" },
                            ) { item ->
                                HistoryRow(
                                    item = item,
                                    firstFocus = if (
                                        "${item.bvid}_${item.viewAt}_${item.cid}" == firstKey
                                    ) {
                                        firstItemFocus
                                    } else {
                                        null
                                    },
                                    onFocusChanged = { focused ->
                                        if (focused) {
                                            onVideoFocused(item.toTopPreview())
                                        } else {
                                            onFocusCleared()
                                        }
                                    },
                                    onClick = { onVideoClick(item.bvid) },
                                    onDelete = { focus ->
                                        deleteFocus = focus
                                        confirmDelete = item
                                    },
                                )
                            }
                        }
                        item(key = "footer") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    uiState.isLoadingMore -> CircularProgressIndicator(
                                        color = DuoPalette.Pink,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    uiState.hasMore -> Text(
                                        text = "继续向下滚动加载",
                                        color = DuoPalette.Tertiary,
                                        fontSize = 10.sp,
                                    )
                                    else -> Text(
                                        text = "共 ${displayItems.size} 条记录",
                                        color = DuoPalette.Tertiary,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { item ->
        DuoConfirmOverlay(
            title = "删除观看历史",
            message = "将从云端历史中删除「${item.title}」，不会删除本地播放进度。",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                confirmDelete = null
                deleteFocus = null
                viewModel.deleteHistory(item)
            },
            onCancel = {
                closeDeleteConfirm()
            },
            session = null,
        )
    }
}

@Composable
private fun HistoryRow(
    item: HistoryItem,
    firstFocus: FocusRequester?,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: (FocusRequester) -> Unit,
) {
    var contentFocused by remember { mutableStateOf(false) }
    var actionFocused by remember { mutableStateOf(false) }
    val actionFocus = remember { FocusRequester() }
    val duration = item.page?.duration?.takeIf { it > 0 } ?: item.duration
    val progressFraction = if (duration > 0) {
        (item.progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val timeText = remember(item.viewAt) {
        if (item.viewAt <= 0) "" else SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            .format(Date(item.viewAt * 1000))
    }
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(shape)
            .background(DuoPalette.Surface)
            .border(
                width = 2.dp,
                // The delete button owns its own ring; lighting the whole
                // card for it made the card border look clipped.
                color = if (contentFocused) DuoPalette.Text else Color.Transparent,
                shape = shape,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(if (firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
                .onFocusChanged {
                    contentFocused = it.isFocused
                    onFocusChanged(it.isFocused)
                }
                .duoClickable(onClick = onClick)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DuoPalette.SurfaceHigh),
        ) {
            AsyncImage(
                model = fixPic(item.pic.orEmpty()),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (duration > 0) {
                Text(
                    text = formatDuration(duration),
                    color = DuoPalette.Text,
                    fontSize = 8.5.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(DuoPalette.Scrim)
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
        }

        Spacer(Modifier.width(9.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = DuoPalette.Text,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append(item.owner.name)
                    if (item.videos > 1) {
                        append(" · P${item.page?.page ?: 1}")
                    }
                },
                color = DuoPalette.Secondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(DuoPalette.SurfaceHighest),
            ) {
                if (progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .height(2.dp)
                            .background(DuoPalette.Pink),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    if (duration > 0) {
                        append(formatDuration(item.progress))
                        append(" / ")
                        append(formatDuration(duration))
                    }
                    if (timeText.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(timeText)
                    }
                },
                color = DuoPalette.Tertiary,
                fontSize = 9.sp,
                maxLines = 1,
            )
        }

        }

        Spacer(Modifier.width(4.dp))

        DuoIconAction(
            icon = Icons.Outlined.DeleteOutline,
            contentDescription = "删除历史",
            tint = if (actionFocused) DuoPalette.Secondary else DuoPalette.Tertiary,
            onFocusChanged = { actionFocused = it },
            modifier = Modifier
                // 8dp leaves room for the 5.75dp ring inside the card clip.
                .padding(end = 8.dp)
                .focusRequester(actionFocus),
            onClick = { onDelete(actionFocus) },
        )
    }
}

private fun historyDateLabel(viewAt: Long): String {
    if (viewAt <= 0L) return "更早"
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = viewAt * 1000 }
    val todayStart = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterdayStart = todayStart - 24 * 60 * 60 * 1000L
    val ts = then.timeInMillis
    return when {
        ts >= todayStart -> "今天"
        ts >= yesterdayStart -> "昨天"
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) ->
            SimpleDateFormat("MM月dd日", Locale.getDefault()).format(Date(ts))
        else -> SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(Date(ts))
    }
}

private fun HistoryItem.toTopPreview(): TopPreviewUi = TopPreviewUi(
    bvid = bvid,
    title = title,
    coverUrl = pic.orEmpty(),
    ownerName = owner.name,
    ownerFace = owner.face,
    playCount = stat.view,
    danmakuCount = stat.danmaku,
    pubdate = 0L,
    duration = formatDuration(page?.duration?.takeIf { it > 0 } ?: duration),
    description = "",
)