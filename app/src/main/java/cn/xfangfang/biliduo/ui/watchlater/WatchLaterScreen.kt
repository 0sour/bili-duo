package cn.xfangfang.biliduo.ui.watchlater

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import cn.xfangfang.biliduo.data.api.BilibiliApi
import cn.xfangfang.biliduo.data.model.WatchLaterItem
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.common.DuoConfirmOverlay
import cn.xfangfang.biliduo.ui.common.DuoEmptyState
import cn.xfangfang.biliduo.ui.common.DuoIconAction
import cn.xfangfang.biliduo.ui.common.DuoPageHeader
import cn.xfangfang.biliduo.ui.common.DuoPalette
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.requestPageFocus
import cn.xfangfang.biliduo.ui.top.TopAmbientUi
import cn.xfangfang.biliduo.ui.top.TopGlyph
import cn.xfangfang.biliduo.ui.top.TopPreviewUi
import cn.xfangfang.biliduo.util.fixPic
import cn.xfangfang.biliduo.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WatchLaterScreen(
    onBack: () -> Unit,
    onVideoClick: (bvid: String) -> Unit,
    onHome: () -> Unit = {},
    onLogin: () -> Unit = {},
    onTopAmbient: (TopAmbientUi) -> Unit = {},
    onVideoFocused: (TopPreviewUi) -> Unit = {},
    onFocusCleared: () -> Unit = {},
    session: DuoSessionViewModel? = null,
    viewModel: WatchLaterViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val firstItemFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    var confirmRemove by remember { mutableStateOf<WatchLaterItem?>(null) }
    var removeFocus by remember { mutableStateOf<FocusRequester?>(null) }

    val loggedIn = BilibiliApi.parseSelfMid() != null

    LaunchedEffect(Unit) {
        if (loggedIn) viewModel.load()
    }
    LaunchedEffect(loggedIn) {
        if (!loggedIn) {
            delay(80)
            runCatching { retryFocus.requestPageFocus() }
        }
    }
    LaunchedEffect(loggedIn, uiState.isLoading, uiState.items.size) {
        when {
            !loggedIn -> onTopAmbient(
                TopAmbientUi(
                    eyebrow = "WATCH LATER / 稍后再看",
                    title = "登录后，\n把想看的留到以后。",
                    subtitle = "在播放控制台加入「稍后再看」，之后在这里继续。",
                    glyph = TopGlyph.HISTORY,
                )
            )
            !uiState.isLoading && uiState.items.isEmpty() -> onTopAmbient(
                TopAmbientUi(
                    eyebrow = "WATCH LATER / 稍后再看",
                    title = "稍后再看还是空的。",
                    subtitle = "在播放页把视频加入稍后再看，上屏会预览封面与进度。",
                    glyph = TopGlyph.HISTORY,
                )
            )
        }
    }
    LaunchedEffect(uiState.isLoading, uiState.items.size) {
        if (uiState.items.isNotEmpty() && !uiState.isLoading) {
            delay(80)
            runCatching { firstItemFocus.requestPageFocus() }
        }
        if (uiState.items.isEmpty()) onFocusCleared()
    }
    LaunchedEffect(uiState.actionError) {
        uiState.actionError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearActionError()
        }
    }

    fun closeRemoveConfirm() {
        val focus = removeFocus
        confirmRemove = null
        scope.launch {
            delay(120)
            runCatching { focus?.requestPageFocus() }
        }
    }

    LaunchedEffect(confirmRemove, session) {
        if (confirmRemove != null) {
            session?.setBackInterceptor {
                closeRemoveConfirm()
                true
            }
        } else {
            session?.setBackInterceptor(null)
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
            title = "稍后再看",
            onBack = onBack,
            actions = {
                if (loggedIn) {
                    DuoIconAction(
                        icon = Icons.Default.Refresh,
                        contentDescription = "刷新稍后再看",
                        tint = DuoPalette.Text,
                        onClick = viewModel::load,
                    )
                }
            },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !loggedIn -> {
                    DuoEmptyState(
                        title = "登录后使用稍后再看",
                        hint = "登录后可跨设备保存想看的视频",
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
                        onAction = viewModel::load,
                        focusRequester = retryFocus,
                    )
                }
                uiState.items.isEmpty() -> {
                    DuoEmptyState(
                        title = "稍后再看还是空的",
                        hint = "在播放页把视频加入稍后再看",
                        actionText = "去首页找视频",
                        onAction = onHome,
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
                            top = 12.dp,
                            bottom = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(
                            items = uiState.items,
                            key = { _, it -> "${it.aid}_${it.cid}_${it.addAt}" },
                        ) { index, item ->
                            WatchLaterRow(
                                item = item,
                                firstFocus = if (index == 0) firstItemFocus else null,
                                onFocusChanged = { focused ->
                                    if (focused) {
                                        onVideoFocused(item.toTopPreview())
                                    } else {
                                        onFocusCleared()
                                    }
                                },
                                onClick = { onVideoClick(item.bvid.orEmpty()) },
                                onRemove = { focus ->
                                    removeFocus = focus
                                    confirmRemove = item
                                },
                            )
                        }
                        item(key = "footer") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "共 ${uiState.items.size} 个视频",
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

    confirmRemove?.let { item ->
        DuoConfirmOverlay(
            title = "移出稍后再看",
            message = "将把「${item.title.orEmpty()}」从稍后再看中移除，不会删除观看进度。",
            confirmText = "移除",
            danger = true,
            onConfirm = {
                confirmRemove = null
                removeFocus = null
                viewModel.remove(item)
            },
            onCancel = { closeRemoveConfirm() },
            session = null,
        )
    }
}

@Composable
private fun WatchLaterRow(
    item: WatchLaterItem,
    firstFocus: FocusRequester?,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    onRemove: (FocusRequester) -> Unit,
) {
    var contentFocused by remember { mutableStateOf(false) }
    var actionFocused by remember { mutableStateOf(false) }
    val actionFocus = remember { FocusRequester() }
    val duration = item.duration.coerceAtLeast(0)
    val progress = item.progress.coerceIn(0L, duration)
    val progressFraction = if (duration > 0) {
        (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val addedText = remember(item.addAt) {
        if (item.addAt <= 0) "" else SimpleDateFormat("MM/dd", Locale.getDefault())
            .format(Date(item.addAt * 1000))
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
                    text = item.title.orEmpty(),
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
                        append(item.owner?.name.orEmpty())
                        if (item.videos > 1) append(" · 共 ${item.videos}P")
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
                            append(formatDuration(progress))
                            append(" / ")
                            append(formatDuration(duration))
                        }
                        if (addedText.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append("加入于 $addedText")
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
            contentDescription = "移出稍后再看",
            tint = if (actionFocused) DuoPalette.Secondary else DuoPalette.Tertiary,
            onFocusChanged = { actionFocused = it },
            modifier = Modifier
                .padding(end = 8.dp)
                .focusRequester(actionFocus),
            onClick = { onRemove(actionFocus) },
        )
    }
}

private fun WatchLaterItem.toTopPreview(): TopPreviewUi = TopPreviewUi(
    bvid = bvid.orEmpty(),
    title = title.orEmpty(),
    coverUrl = pic.orEmpty(),
    ownerName = owner?.name.orEmpty(),
    ownerFace = owner?.face.orEmpty(),
    playCount = stat?.view ?: 0L,
    danmakuCount = stat?.danmaku ?: 0L,
    pubdate = 0L,
    duration = formatDuration(duration),
    description = "",
    eyebrow = "● 稍后再看 · 即将播放",
)
