package cn.xfangfang.biliduo.ui.downloads

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import cn.xfangfang.biliduo.data.local.AppSettings
import cn.xfangfang.biliduo.data.model.DownloadInfo
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.common.DuoConfirmOverlay
import cn.xfangfang.biliduo.ui.common.DuoEmptyState
import cn.xfangfang.biliduo.ui.common.DuoIconAction
import cn.xfangfang.biliduo.ui.common.DuoPageHeader
import cn.xfangfang.biliduo.ui.common.DuoPalette
import cn.xfangfang.biliduo.ui.common.DuoPillButton
import cn.xfangfang.biliduo.ui.common.DuoSearchField
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.ui.top.TopAmbientUi
import cn.xfangfang.biliduo.ui.top.TopGlyph
import cn.xfangfang.biliduo.ui.top.TopPreviewUi
import cn.xfangfang.biliduo.util.fixPic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlay: (bvid: String, cid: Long) -> Unit,
    onVideoFocused: (TopPreviewUi) -> Unit = {},
    onFocusCleared: () -> Unit = {},
    onTopAmbient: (TopAmbientUi) -> Unit = {},
    session: DuoSessionViewModel? = null,
    viewModel: DownloadsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val items by viewModel.items.collectAsState()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val searchFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }
    var searchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var confirmResume by remember { mutableStateOf<DownloadInfo?>(null) }
    var confirmDelete by remember { mutableStateOf<DownloadInfo?>(null) }
    var confirmDeleteGroup by remember { mutableStateOf<DownloadGroup?>(null) }
    var confirmClearCompleted by remember { mutableStateOf(false) }
    var pendingActionFocus by remember { mutableStateOf<FocusRequester?>(null) }
    val expandedMap = remember { mutableStateMapOf<String, Boolean>() }

    val displayItems = remember(items, searchQuery) {
        filterDownloadItems(items, searchQuery)
    }
    val completedCount = remember(items) {
        items.sumOf { item ->
            when (item) {
                is DownloadListItem.Single -> if (item.download.status == DownloadInfo.STATUS_COMPLETED) 1 else 0
                is DownloadListItem.Group -> item.group.downloads.count { it.status == DownloadInfo.STATUS_COMPLETED }
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(items.size, displayItems.size, searchMode) {
        if (!searchMode) {
            when {
                items.isEmpty() -> onTopAmbient(
                    TopAmbientUi(
                        eyebrow = "DOWNLOADS / 本地",
                        title = "本地缓存，\n离线也能看。",
                        subtitle = "在视频简介里点「下载」，完成后会出现在下屏；上屏展示封面、清晰度与文件信息。",
                        glyph = TopGlyph.DOWNLOAD,
                    )
                )
                displayItems.isEmpty() -> onTopAmbient(
                    TopAmbientUi(
                        eyebrow = "DOWNLOADS / 本地",
                        title = "没有找到匹配的下载。",
                        subtitle = "换一个关键词，或退出搜索查看全部本地缓存。",
                        glyph = TopGlyph.SEARCH,
                    )
                )
            }
        }
    }
    LaunchedEffect(displayItems.size, searchMode) {
        if (!searchMode && displayItems.isNotEmpty()) {
            delay(80)
            runCatching { firstItemFocus.requestFocus() }
        }
    }
    LaunchedEffect(searchMode) {
        if (searchMode) {
            delay(80)
            runCatching { searchFocus.requestFocus() }
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }
    fun restoreFocus(focus: FocusRequester?) {
        scope.launch {
            delay(120)
            runCatching { focus?.requestFocus() }
        }
    }

    fun closeConfirmResume() {
        val focus = pendingActionFocus
        confirmResume = null
        restoreFocus(focus)
    }

    fun closeConfirmDelete() {
        val focus = pendingActionFocus
        confirmDelete = null
        restoreFocus(focus)
    }

    fun closeConfirmDeleteGroup() {
        val focus = pendingActionFocus
        confirmDeleteGroup = null
        restoreFocus(focus)
    }

    fun closeConfirmClearCompleted() {
        val focus = pendingActionFocus
        confirmClearCompleted = false
        restoreFocus(focus)
    }

    LaunchedEffect(confirmResume, confirmDelete, confirmDeleteGroup, confirmClearCompleted, searchMode, session) {
        val cancel = when {
            confirmResume != null -> ({ closeConfirmResume() })
            confirmDelete != null -> ({ closeConfirmDelete() })
            confirmDeleteGroup != null -> ({ closeConfirmDeleteGroup() })
            confirmClearCompleted -> ({ closeConfirmClearCompleted() })
            searchMode -> ({
                searchMode = false
                searchQuery = ""
            })
            else -> null
        }
        if (cancel != null) {
            session?.setBackInterceptor {
                cancel()
                true
            }
        } else {
            session?.setBackInterceptor(null)
        }
    }
    DisposableEffect(session) {
        onDispose { session?.setBackInterceptor(null) }
    }
    BackHandler(searchMode) {
        searchMode = false
        searchQuery = ""
    }

    fun exitSearch() {
        searchMode = false
        searchQuery = ""
    }

    fun requestResume(download: DownloadInfo) {
        if (AppSettings.wifiOnlyDownload && !AppSettings.isWifiConnected(context)) {
            pendingActionFocus = null
            confirmResume = download
        } else {
            viewModel.resumeDownload(download)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoPalette.Bg),
    ) {
        DuoPageHeader(
            title = "我的下载",
            onBack = { if (searchMode) exitSearch() else onBack() },
            actions = {
                if (completedCount > 0) {
                    DuoIconAction(
                        icon = Icons.Outlined.DeleteSweep,
                        contentDescription = "清除已完成",
                        tint = DuoPalette.Secondary,
                        onClick = {
                            pendingActionFocus = null
                            confirmClearCompleted = true
                        },
                    )
                }
                DuoIconAction(
                    icon = Icons.Default.Search,
                    contentDescription = "搜索下载",
                    tint = if (searchMode) DuoPalette.Pink else DuoPalette.Text,
                    onClick = {
                        if (searchMode) exitSearch() else searchMode = true
                    },
                )
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
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "搜索下载视频",
                    focusRequester = searchFocus,
                    modifier = Modifier.weight(1f),
                    onSubmit = { keyboard?.hide() },
                    onClear = { searchQuery = "" },
                    onRequestIme = { keyboard?.show() },
                )
                Spacer(Modifier.width(8.dp))
                DuoPillButton(text = "取消", onClick = { exitSearch() })
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                items.isEmpty() -> {
                    DuoEmptyState(
                        title = "暂无下载",
                        hint = "在视频简介里点「下载」后会出现在这里",
                    )
                }
                displayItems.isEmpty() -> {
                    DuoEmptyState(title = "未找到匹配的下载")
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(
                            items = displayItems,
                            key = {
                                when (it) {
                                    is DownloadListItem.Single -> it.download.id
                                    is DownloadListItem.Group -> "group_${it.group.bvid}"
                                }
                            },
                        ) { item ->
                            when (item) {
                                is DownloadListItem.Single -> DownloadCard(
                                    download = item.download,
                                    firstFocus = if (
                                        displayItems.firstOrNull() === item
                                    ) firstItemFocus else null,
                                    onPlay = { onPlay(item.download.bvid, item.download.cid) },
                                    onDelete = { focus ->
                                        pendingActionFocus = focus
                                        confirmDelete = item.download
                                    },
                                    onPause = { viewModel.pauseDownload(item.download) },
                                    onResume = { requestResume(item.download) },
                                    onFocused = { onVideoFocused(downloadTopPreview(it)) },
                                )
                                is DownloadListItem.Group -> {
                                    val expanded = expandedMap[item.group.bvid] ?: false
                                    DownloadGroupHeader(
                                        group = item.group,
                                        expanded = expanded,
                                        firstFocus = if (
                                            displayItems.firstOrNull() === item
                                        ) firstItemFocus else null,
                                        onToggle = { expandedMap[item.group.bvid] = !expanded },
                                        onDelete = { focus ->
                                            pendingActionFocus = focus
                                            confirmDeleteGroup = item.group
                                        },
                                        onFocused = { onVideoFocused(groupTopPreview(it)) },
                                    )
                                    if (expanded) {
                                        item.group.downloads.forEach { download ->
                                            DownloadSubRow(
                                                download = download,
                                                onPlay = { onPlay(download.bvid, download.cid) },
                                                onDelete = { focus ->
                                                    pendingActionFocus = focus
                                                    confirmDelete = download
                                                },
                                                onPause = { viewModel.pauseDownload(download) },
                                                onResume = { requestResume(download) },
                                                onFocused = { onVideoFocused(downloadTopPreview(it)) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item(key = "footer") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "共 ${displayItems.size} 项下载 · ${formatFileSize(
                                        items.sumOf { item ->
                                            when (item) {
                                                is DownloadListItem.Single -> item.download.fileSize
                                                is DownloadListItem.Group ->
                                                    item.group.downloads.sumOf { it.fileSize }
                                            }
                                        }
                                    )}",
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

    confirmResume?.let { download ->
        DuoConfirmOverlay(
            title = "移动网络下载",
            message = "当前不是 WiFi，继续下载「${download.title}」会消耗流量。",
            confirmText = "继续下载",
            danger = false,
            onConfirm = {
                confirmResume = null
                viewModel.resumeDownload(download)
            },
            onCancel = {
                closeConfirmResume()
            },
            session = null,
        )
    }
    confirmDelete?.let { download ->
        DuoConfirmOverlay(
            title = "删除下载",
            message = "确定删除「${download.title}」的本地文件吗？",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                confirmDelete = null
                pendingActionFocus = null
                viewModel.deleteDownload(download)
            },
            onCancel = {
                closeConfirmDelete()
            },
            session = null,
        )
    }
    confirmDeleteGroup?.let { group ->
        DuoConfirmOverlay(
            title = "删除整组分P",
            message = "确定删除「${group.title}」的全部 ${group.downloads.size} 个分P吗？",
            confirmText = "全部删除",
            danger = true,
            onConfirm = {
                confirmDeleteGroup = null
                pendingActionFocus = null
                viewModel.deleteGroup(group)
            },
            onCancel = {
                closeConfirmDeleteGroup()
            },
            session = null,
        )
    }
    if (confirmClearCompleted) {
        DuoConfirmOverlay(
            title = "清除已完成",
            message = "确定删除全部 $completedCount 个已完成下载吗？此操作不可恢复。",
            confirmText = "清除",
            danger = true,
            onConfirm = {
                confirmClearCompleted = false
                pendingActionFocus = null
                viewModel.clearCompleted()
            },
            onCancel = {
                closeConfirmClearCompleted()
            },
            session = null,
        )
    }
}

@Composable
private fun DownloadCard(
    download: DownloadInfo,
    firstFocus: FocusRequester?,
    onPlay: () -> Unit,
    onDelete: (FocusRequester) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFocused: (DownloadInfo) -> Unit = {},
) {
    var contentFocused by remember { mutableStateOf(false) }
    var actionFocused by remember { mutableStateOf(false) }
    val actionFocus = remember { FocusRequester() }
    val shape = RoundedCornerShape(10.dp)
    val active = download.status == DownloadInfo.STATUS_DOWNLOADING
    val resumable = download.status == DownloadInfo.STATUS_PAUSED ||
        download.status == DownloadInfo.STATUS_FAILED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(shape)
            .background(DuoPalette.Surface)
            .border(
                width = 2.dp,
                color = if (contentFocused || actionFocused) Color.White else Color.Transparent,
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
                    if (it.isFocused) onFocused(download)
                }
                .duoClickable {
                    when {
                        download.status == DownloadInfo.STATUS_COMPLETED -> onPlay()
                        active -> onPause()
                        resumable -> onResume()
                    }
                }
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
                    model = fixPic(download.cover),
                    contentDescription = download.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = download.title,
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
                        append(download.qualityDesc)
                        if (download.page > 0) {
                            append(" · P${download.page}")
                            if (download.part.isNotBlank()) append(" ${download.part}")
                        }
                    },
                    color = DuoPalette.Secondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                DownloadStatusText(download)
                if (active || download.status == DownloadInfo.STATUS_PAUSED) {
                    Spacer(Modifier.height(4.dp))
                    DownloadProgress(download)
                }
            }
        }

        Column(
            modifier = Modifier.padding(end = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                download.status == DownloadInfo.STATUS_COMPLETED -> {
                    DuoIconAction(
                        icon = Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        tint = DuoPalette.Pink,
                        onClick = onPlay,
                    )
                }
                active -> {
                    DuoIconAction(
                        icon = Icons.Default.Pause,
                        contentDescription = "暂停",
                        tint = DuoPalette.Secondary,
                        onClick = onPause,
                    )
                }
                resumable -> {
                    DuoIconAction(
                        icon = Icons.Default.Refresh,
                        contentDescription = "继续",
                        tint = DuoPalette.Pink,
                        onClick = onResume,
                    )
                }
            }
            DuoIconAction(
                icon = Icons.Default.Delete,
                contentDescription = "删除",
                tint = if (actionFocused) DuoPalette.Error else DuoPalette.Tertiary,
                onFocusChanged = { actionFocused = it },
                modifier = Modifier.focusRequester(actionFocus),
                onClick = { onDelete(actionFocus) },
            )
        }
    }
}

@Composable
private fun DownloadGroupHeader(
    group: DownloadGroup,
    expanded: Boolean,
    firstFocus: FocusRequester?,
    onToggle: () -> Unit,
    onDelete: (FocusRequester) -> Unit,
    onFocused: (DownloadGroup) -> Unit = {},
) {
    var contentFocused by remember { mutableStateOf(false) }
    var actionFocused by remember { mutableStateOf(false) }
    val actionFocus = remember { FocusRequester() }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(shape)
            .background(DuoPalette.Surface)
            .border(
                width = 2.dp,
                color = if (contentFocused || actionFocused) Color.White else Color.Transparent,
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
                    if (it.isFocused) onFocused(group)
                }
                .duoClickable(onClick = onToggle)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(104.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DuoPalette.SurfaceHigh),
            ) {
                AsyncImage(
                    model = fixPic(group.cover),
                    contentDescription = group.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.title,
                    color = DuoPalette.Text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "共 ${group.downloads.size} 个分P · ${group.downloads.first().qualityDesc}",
                    color = DuoPalette.Secondary,
                    fontSize = 10.sp,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = DuoPalette.Tertiary,
                modifier = Modifier.size(18.dp),
            )
        }
        DuoIconAction(
            icon = Icons.Default.Delete,
            contentDescription = "删除整组",
            tint = if (actionFocused) DuoPalette.Error else DuoPalette.Tertiary,
            onFocusChanged = { actionFocused = it },
            modifier = Modifier.padding(end = 4.dp).focusRequester(actionFocus),
            onClick = { onDelete(actionFocus) },
        )
    }
}

@Composable
private fun DownloadSubRow(
    download: DownloadInfo,
    onPlay: () -> Unit,
    onDelete: (FocusRequester) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFocused: (DownloadInfo) -> Unit = {},
) {
    var contentFocused by remember { mutableStateOf(false) }
    val actionFocus = remember { FocusRequester() }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(start = 14.dp)
            .clip(shape)
            .background(DuoPalette.SurfaceHigh)
            .border(
                width = 2.dp,
                color = if (contentFocused) Color.White else Color.Transparent,
                shape = shape,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onFocusChanged {
                    contentFocused = it.isFocused
                    if (it.isFocused) onFocused(download)
                }
                .duoClickable {
                    when {
                        download.status == DownloadInfo.STATUS_COMPLETED -> onPlay()
                        download.status == DownloadInfo.STATUS_DOWNLOADING -> onPause()
                        else -> onResume()
                    }
                }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "P${download.page}",
                color = DuoPalette.Pink,
                fontSize = 10.sp,
                modifier = Modifier.width(26.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = download.part.ifBlank { download.title },
                    color = DuoPalette.Text,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DownloadStatusText(download)
            }
            DownloadProgress(download, Modifier.width(70.dp))
        }
        DuoIconAction(
            icon = Icons.Default.Delete,
            contentDescription = "删除分P",
            tint = DuoPalette.Tertiary,
            modifier = Modifier.padding(end = 4.dp).focusRequester(actionFocus),
            onClick = { onDelete(actionFocus) },
        )
    }
}

private fun downloadTopPreview(download: DownloadInfo): TopPreviewUi {
    val size = if (download.fileSize > 0) download.fileSize else download.totalSize
    val status = when (download.status) {
        DownloadInfo.STATUS_COMPLETED -> "已完成"
        DownloadInfo.STATUS_DOWNLOADING -> "下载中"
        DownloadInfo.STATUS_PAUSED -> "已暂停"
        DownloadInfo.STATUS_FAILED -> "下载失败"
        else -> "等待下载"
    }
    val meta = buildString {
        if (download.qualityDesc.isNotBlank()) append(download.qualityDesc)
        if (size > 0) {
            if (isNotEmpty()) append(" · ")
            append(formatFileSize(size))
        }
        if (download.page > 0) {
            if (isNotEmpty()) append(" · ")
            append("P${download.page}")
        }
    }
    return TopPreviewUi(
        bvid = "",
        title = download.title,
        coverUrl = download.cover,
        description = download.part.ifBlank { "本地缓存，可离线播放。" },
        category = "本地下载",
        eyebrow = "● 本地缓存 · $status",
        metaLine = meta,
        ownerName = "",
        actionHint = "播放 · B 返回",
    )
}

private fun groupTopPreview(group: DownloadGroup): TopPreviewUi {
    return TopPreviewUi(
        bvid = "",
        title = group.title,
        coverUrl = group.cover,
        duration = "",
        description = "共 ${group.downloads.size} 个分P，可在下屏展开选择。",
        category = "本地下载",
        eyebrow = "● 本地缓存 · 分P合集",
        metaLine = "${group.downloads.size} 个分P",
        ownerName = "",
        actionHint = "展开 / 播放 · B 返回",
    )
}

@Composable
private fun DownloadStatusText(download: DownloadInfo) {
    val text = when (download.status) {
        DownloadInfo.STATUS_COMPLETED -> formatFileSize(download.fileSize)
        DownloadInfo.STATUS_DOWNLOADING -> buildString {
            append(formatFileSize(download.fileSize))
            if (download.totalSize > 0) append(" / ${formatFileSize(download.totalSize)}")
            if (download.speed > 0) {
                append(" · ${formatSpeed(download.speed)}")
                remainingText(download)?.let { append(" · $it") }
            }
        }
        DownloadInfo.STATUS_PAUSED -> "已暂停 · ${formatFileSize(download.fileSize)}"
        DownloadInfo.STATUS_FAILED -> "下载失败 · 点击继续"
        else -> if (download.totalSize > 0) formatFileSize(download.totalSize) else "等待下载"
    }
    Text(
        text = text,
        color = when (download.status) {
            DownloadInfo.STATUS_FAILED -> DuoPalette.Error
            DownloadInfo.STATUS_COMPLETED -> DuoPalette.Secondary
            DownloadInfo.STATUS_DOWNLOADING -> DuoPalette.Pink
            else -> DuoPalette.Tertiary
        },
        fontSize = 9.5.sp,
        maxLines = 1,
    )
}

@Composable
private fun DownloadProgress(download: DownloadInfo, modifier: Modifier = Modifier.fillMaxWidth()) {
    if (download.totalSize <= 0) {
        Text("等待下载", color = DuoPalette.Tertiary, fontSize = 9.sp)
    } else {
        val fraction = (download.fileSize.toFloat() / download.totalSize).coerceIn(0f, 1f)
        Box(
            modifier = modifier
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(DuoPalette.SurfaceHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(2.dp)
                    .background(DuoPalette.Pink),
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatSpeed(bytesPerSecond: Long): String =
    "${formatFileSize(bytesPerSecond)}/s"

private fun remainingText(download: DownloadInfo): String? {
    if (download.speed <= 0 || download.totalSize <= 0) return null
    val remain = download.totalSize - download.fileSize
    if (remain <= 0) return null
    val seconds = remain / download.speed
    return when {
        seconds >= 3600 -> "${seconds / 3600}小时${(seconds % 3600) / 60}分"
        seconds >= 60 -> "${seconds / 60}分${seconds % 60}秒"
        else -> "${seconds}秒"
    }.let { "剩余 $it" }
}
