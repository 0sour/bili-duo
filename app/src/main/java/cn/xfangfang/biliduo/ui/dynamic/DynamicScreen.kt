package cn.xfangfang.biliduo.ui.dynamic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.xfangfang.biliduo.data.api.BilibiliApi
import cn.xfangfang.biliduo.data.model.CommentItem
import cn.xfangfang.biliduo.data.model.DynamicAuthorUi
import cn.xfangfang.biliduo.data.model.DynamicCardUi
import cn.xfangfang.biliduo.data.model.DynamicFilter
import cn.xfangfang.biliduo.data.model.DynamicForwardUi
import cn.xfangfang.biliduo.data.model.DynamicVideoUi
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.common.DuoEmptyState
import cn.xfangfang.biliduo.ui.common.DuoPalette
import cn.xfangfang.biliduo.ui.common.DuoZoomableImage
import cn.xfangfang.biliduo.ui.common.ImageZoomState
import cn.xfangfang.biliduo.ui.common.rememberImageZoomState
import cn.xfangfang.biliduo.ui.common.DuoPillButton
import cn.xfangfang.biliduo.ui.duo.requestPageFocus
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.DuoTextFieldGate
import cn.xfangfang.biliduo.ui.duo.rememberDuoTextFieldState
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.ui.duo.duoTabCycling
import cn.xfangfang.biliduo.ui.top.TopAmbientUi
import cn.xfangfang.biliduo.ui.top.TopDynamicKind
import cn.xfangfang.biliduo.ui.top.TopDynamicReaderUi
import cn.xfangfang.biliduo.ui.top.TopGlyph
import cn.xfangfang.biliduo.ui.top.TopPreviewUi
import cn.xfangfang.biliduo.util.fixPic
import cn.xfangfang.biliduo.util.formatCount
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(10.dp)
private val CardFocusClearance = 7.dp

private sealed interface DynamicOverlay {
    val card: DynamicCardUi
    val imageIndex: Int
    val returnFocus: FocusRequester
    val kind: TopDynamicKind

    data class Detail(
        override val card: DynamicCardUi,
        override val imageIndex: Int,
        override val returnFocus: FocusRequester,
    ) : DynamicOverlay {
        override val kind: TopDynamicKind = TopDynamicKind.DETAIL
    }

    data class Gallery(
        override val card: DynamicCardUi,
        override val imageIndex: Int,
        override val returnFocus: FocusRequester,
    ) : DynamicOverlay {
        override val kind: TopDynamicKind = TopDynamicKind.GALLERY
    }

    data class Text(
        override val card: DynamicCardUi,
        override val returnFocus: FocusRequester,
    ) : DynamicOverlay {
        override val imageIndex: Int = 0
        override val kind: TopDynamicKind = TopDynamicKind.TEXT
    }
}

private fun DynamicOverlay.withImage(index: Int): DynamicOverlay = when (this) {
    is DynamicOverlay.Detail -> copy(imageIndex = index)
    is DynamicOverlay.Gallery -> copy(imageIndex = index)
    is DynamicOverlay.Text -> this
}

@Composable
fun DynamicScreen(
    onVideoClick: (String) -> Unit,
    onLogin: () -> Unit,
    session: DuoSessionViewModel? = null,
    onVideoFocused: (TopPreviewUi) -> Unit = {},
    onFocusCleared: () -> Unit = {},
    onDynamicReader: (TopDynamicReaderUi) -> Unit = {},
    onTopAmbient: (TopAmbientUi) -> Unit = {},
    viewModel: DynamicViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    val authorFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var firstFocused by remember { mutableStateOf(false) }
    val filterFocusers = remember { List(DynamicFilter.entries.size) { FocusRequester() } }
    val authorFocusers = remember(state.authors) {
        List(state.authors.size + 1) { FocusRequester() }
    }
    var pendingFilterFocus by remember { mutableStateOf(false) }
    var pendingAuthorFocus by remember { mutableStateOf(false) }
    var focusContentOnFilterShortcut by remember { mutableStateOf(false) }
    var lastShortcutAt by remember { mutableStateOf(0L) }

    LaunchedEffect(state.filter, pendingFilterFocus) {
        if (pendingFilterFocus) {
            delay(80L)
            runCatching { filterFocusers[state.filter.ordinal].requestPageFocus() }
            pendingFilterFocus = false
        }
    }
    LaunchedEffect(state.authorMid, state.authors.size, pendingAuthorFocus) {
        if (pendingAuthorFocus) {
            delay(80L)
            val index = if (state.authorMid == null) {
                0
            } else {
                state.authors.indexOfFirst { it.mid == state.authorMid } + 1
            }
            if (index in authorFocusers.indices) {
                runCatching { authorFocusers[index].requestPageFocus() }
            }
            pendingAuthorFocus = false
        }
    }
    var focusedIndex by remember { mutableStateOf<Int?>(null) }
    var overlay by remember { mutableStateOf<DynamicOverlay?>(null) }
    val loggedIn = BilibiliApi.parseSelfMid() != null

    fun closeToList() {
        val current = overlay ?: return
        overlay = null
        onVideoFocused(current.card.toTopPreview())
        scope.launch {
            delay(100L)
            runCatching { current.returnFocus.requestPageFocus() }
        }
    }

    fun openGallery(current: DynamicOverlay, index: Int) {
        if (current.card.images.isEmpty()) return
        val safe = index.coerceIn(0, current.card.images.lastIndex)
        val next = DynamicOverlay.Gallery(current.card, safe, current.returnFocus)
        overlay = next
        onDynamicReader(current.card.toTopReader(TopDynamicKind.GALLERY, safe))
    }

    fun changeImage(current: DynamicOverlay, index: Int) {
        if (current.card.images.isEmpty()) return
        val safe = index.coerceIn(0, current.card.images.lastIndex)
        val next = current.withImage(safe)
        overlay = next
        onDynamicReader(current.card.toTopReader(next.kind, safe))
    }

    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            viewModel.refresh()
        } else {
            delay(80L)
            runCatching { retryFocus.requestPageFocus() }
        }
    }
    LaunchedEffect(state.filter, state.authorMid) {
        // Keep the initial first-card focus only for the first load. On a
        // user-initiated filter change the chip keeps focus; the list is
        // reachable with Down.
        runCatching { listState.scrollToItem(0) }
    }
    LaunchedEffect(
        state.filter,
        state.authorMid,
        state.items.size,
        state.isLoading,
        focusContentOnFilterShortcut,
    ) {
        // Wait for the new filter's response; while loading we keep the old
        // cards visible, but focusing them would leave focus on a card that
        // is about to be replaced.
        val needsContentFocus = !firstFocused || focusContentOnFilterShortcut
        if (needsContentFocus && !state.isLoading && state.items.isNotEmpty()) {
            delay(120L)
            if (!focusContentOnFilterShortcut &&
                SystemClock.uptimeMillis() - lastShortcutAt < 600L
            ) {
                return@LaunchedEffect
            }
            runCatching { firstFocus.requestPageFocus() }
            firstFocused = true
            focusContentOnFilterShortcut = false
        }
    }
    LaunchedEffect(loggedIn, state.filter, state.isLoading, state.items.size, state.error) {
        onTopAmbient(dynamicAmbient(loggedIn, state))
    }
    DisposableEffect(Unit) {
        onDispose { onFocusCleared() }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3 && state.hasMore &&
                state.error == null && !state.isLoadingMore && !state.isLoading
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }
    // A dynamic card can be taller than the viewport. Compose's bring-into-view
    // refuses to scroll when both card edges are outside, leaving the focus ring
    // clipped at the top. Align tall/focused-above cards explicitly.
    LaunchedEffect(focusedIndex) {
        val index = focusedIndex ?: return@LaunchedEffect
        delay(60L)
        val info = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == index } ?: return@LaunchedEffect
        val viewport = listState.layoutInfo.viewportEndOffset -
            listState.layoutInfo.viewportStartOffset
        if (info.offset < 0 || info.size > viewport) {
            runCatching { listState.scrollToItem(index) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DuoPalette.Bg)
            .duoTabCycling(
                // Without a session the page is a login prompt; switching the
                // filters there would fire anonymous requests for nothing.
                // While a reader/gallery is open, leave L1/R1/L2/R2 to it.
                onPreviousTab = if (loggedIn && overlay == null) ({
                    viewModel.cycleFilter(-1)
                    pendingFilterFocus = false
                    focusContentOnFilterShortcut = true
                    lastShortcutAt = SystemClock.uptimeMillis()
                }) else null,
                onNextTab = if (loggedIn && overlay == null) ({
                    viewModel.cycleFilter(1)
                    pendingFilterFocus = false
                    focusContentOnFilterShortcut = true
                    lastShortcutAt = SystemClock.uptimeMillis()
                }) else null,
                onPreviousSection = if (loggedIn && overlay == null) ({
                    viewModel.cycleAuthor(-1)
                    pendingAuthorFocus = true
                    lastShortcutAt = SystemClock.uptimeMillis()
                }) else null,
                onNextSection = if (loggedIn && overlay == null) ({
                    viewModel.cycleAuthor(1)
                    pendingAuthorFocus = true
                    lastShortcutAt = SystemClock.uptimeMillis()
                }) else null,
            ),
    ) {
        DynamicHeader(
            selectedFilter = state.filter,
            onFilterSelected = viewModel::selectFilter,
            onRefresh = viewModel::refresh,
            focusRequesters = filterFocusers,
        )
        if (state.authors.isNotEmpty()) {
            DynamicAuthorRow(
                authors = state.authors,
                selectedMid = state.authorMid,
                firstFocus = authorFocus,
                downFocusRequester = firstFocus,
                focusRequesters = authorFocusers,
                onAll = viewModel::clearAuthor,
                onSelect = viewModel::selectAuthor,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !loggedIn -> DuoEmptyState(
                    title = "登录后查看关注动态",
                    hint = "关注 UP 主的视频、图文与转发会集中显示在这里",
                    actionText = "去登录",
                    onAction = onLogin,
                    focusRequester = retryFocus,
                )
                state.isLoading && state.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = DuoPalette.Pink,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                state.error != null && state.items.isEmpty() -> DuoEmptyState(
                    title = "动态加载失败",
                    hint = state.error,
                    actionText = "重试",
                    onAction = viewModel::retry,
                    focusRequester = retryFocus,
                )
                state.items.isEmpty() -> DuoEmptyState(
                    title = if (state.filter == DynamicFilter.ALL) "还没有关注动态" else "这个分类暂时没有动态",
                    hint = "关注 UP 主后，他们的更新会出现在这里",
                )
                else -> LazyColumn(
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 0.dp,
                        bottom = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(state.items, key = { _, item -> item.id }) { index, card ->
                        val itemFocus = remember(card.id) { FocusRequester() }
                        DynamicCard(
                            card = card,
                            focusRequester = itemFocus,
                            firstFocus = if (index == 0) firstFocus else null,
                            upFocusRequester = if (index == 0 && state.authors.isNotEmpty()) {
                                authorFocus
                            } else {
                                null
                            },
                            onFocused = {
                                focusedIndex = index
                                onVideoFocused(it.toTopPreview())
                            },
                            onFocusCleared = {
                                if (overlay == null) onFocusCleared()
                            },
                            onClick = {
                                val readerCard = card.withReaderImages()
                                when {
                                    card.playbackBvid != null -> onVideoClick(card.playbackBvid!!)
                                    readerCard.images.isNotEmpty() -> {
                                        overlay = DynamicOverlay.Detail(readerCard, 0, itemFocus)
                                        onDynamicReader(
                                            readerCard.toTopReader(TopDynamicKind.DETAIL, 0)
                                        )
                                    }
                                    else -> {
                                        overlay = DynamicOverlay.Text(readerCard, itemFocus)
                                        onDynamicReader(
                                            readerCard.toTopReader(TopDynamicKind.TEXT, 0)
                                        )
                                    }
                                }
                            },
                            onToggleLike = { viewModel.toggleLike(card) },
                        )
                    }
                    item(key = "dynamic_footer") {
                        DynamicFooter(
                            isLoadingMore = state.isLoadingMore,
                            hasMore = state.hasMore,
                            error = state.error,
                            onRetry = viewModel::retry,
                        )
                    }
                }
            }
            state.actionMessage?.let { message ->
                Text(
                    text = message,
                    color = if (message.startsWith("已")) DuoPalette.Pink else DuoPalette.Error,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DuoPalette.SurfaceHighest)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }

    overlay?.let { current ->
        DynamicReaderLayer(
            overlay = current,
            session = session,
            onBack = {
                if (current is DynamicOverlay.Gallery) {
                    val detail = DynamicOverlay.Detail(
                        card = current.card,
                        imageIndex = current.imageIndex,
                        returnFocus = current.returnFocus,
                    )
                    overlay = detail
                    onDynamicReader(current.card.toTopReader(TopDynamicKind.DETAIL, current.imageIndex))
                } else {
                    closeToList()
                }
            },
            onOpenGallery = { openGallery(current, it) },
            onIndexChange = { changeImage(current, it) },
        )
    }
}

@Composable
private fun DynamicHeader(
    selectedFilter: DynamicFilter,
    onFilterSelected: (DynamicFilter) -> Unit,
    onRefresh: () -> Unit,
    focusRequesters: List<FocusRequester> = emptyList(),
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(start = 16.dp, top = 6.dp, end = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "动态",
            color = DuoPalette.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(14.dp))
        DynamicFilter.entries.forEachIndexed { index, filter ->
            DynamicChip(
                text = filter.label,
                selected = filter == selectedFilter,
                focusRequester = focusRequesters.getOrNull(index),
                onClick = { onFilterSelected(filter) },
            )
            Spacer(Modifier.width(6.dp))
        }
        Spacer(Modifier.weight(1f))
        DynamicChip(text = "刷新", selected = false, onClick = onRefresh)
    }
}

@Composable
private fun DynamicAuthorRow(
    authors: List<DynamicAuthorUi>,
    selectedMid: Long?,
    firstFocus: FocusRequester,
    downFocusRequester: FocusRequester,
    focusRequesters: List<FocusRequester> = emptyList(),
    onAll: () -> Unit,
    onSelect: (DynamicAuthorUi) -> Unit,
) {
    // L2/R2 can pick an author that is scrolled out of the row; keep the
    // selected chip visible so the shortcut has visible feedback.
    val rowState = rememberLazyListState()
    LaunchedEffect(selectedMid, authors.size) {
        val index = if (selectedMid == null) {
            0
        } else {
            authors.indexOfFirst { it.mid == selectedMid } + 1
        }
        if (selectedMid == null || index > 0) {
            rowState.scrollToItem(index)
        }
    }
    LazyRow(
        state = rowState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp,
            end = 12.dp,
            // duoFocusRing draws outside the 30dp chip; leave the full outer
            // extent inside the LazyRow or the top/bottom of the ring clips.
            top = 7.dp,
            bottom = 7.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item(key = "dynamic_author_all") {
            DynamicAuthorChip(
                author = null,
                selected = selectedMid == null,
                focusRequester = firstFocus,
                downFocusRequester = downFocusRequester,
                onClick = onAll,
            )
        }
        itemsIndexed(authors, key = { _, author -> author.mid }) { index, author ->
            DynamicAuthorChip(
                author = author,
                selected = selectedMid == author.mid,
                focusRequester = focusRequesters.getOrNull(index + 1),
                downFocusRequester = downFocusRequester,
                onClick = { onSelect(author) },
            )
        }
    }
}

@Composable
private fun DynamicAuthorChip(
    author: DynamicAuthorUi?,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier = Modifier
            .height(30.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(
                if (downFocusRequester != null) {
                    Modifier.focusProperties { down = downFocusRequester }
                } else {
                    Modifier
                }
            )
            .duoFocusRing(shape)
            .clip(shape)
            .background(
                if (selected) DuoPalette.Pink.copy(alpha = 0.16f)
                else DuoPalette.SurfaceHigh
            )
            .border(
                width = 1.dp,
                color = if (selected) DuoPalette.Pink.copy(alpha = 0.7f)
                else DuoPalette.Outline,
                shape = shape,
            )
            .duoClickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (author == null) {
            Text(
                text = "全部 UP",
                color = if (selected) DuoPalette.Pink else DuoPalette.Secondary,
                fontSize = 10.sp,
                maxLines = 1,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(DuoPalette.SurfaceHigh),
            ) {
                if (author.face.isNotBlank()) {
                    AsyncImage(
                        model = fixPic(author.face),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(5.dp))
            Text(
                text = author.name,
                color = if (selected) DuoPalette.Pink else DuoPalette.Secondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp),
            )
        }
    }
}

@Composable
private fun DynamicChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .height(28.dp)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .duoFocusRing(shape)
            .clip(shape)
            .background(
                if (selected) DuoPalette.Pink.copy(alpha = 0.18f)
                else DuoPalette.SurfaceHigh
            )
            .border(
                width = 1.dp,
                color = if (selected) DuoPalette.Pink.copy(alpha = 0.8f)
                else DuoPalette.Outline,
                shape = shape,
            )
            .duoClickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) DuoPalette.Pink else DuoPalette.Secondary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun DynamicCard(
    card: DynamicCardUi,
    focusRequester: FocusRequester,
    firstFocus: FocusRequester?,
    upFocusRequester: FocusRequester?,
    onFocused: (DynamicCardUi) -> Unit,
    onFocusCleared: () -> Unit,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
) {
    var focused by remember(card.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
            .focusRequester(focusRequester)
            .then(
                if (upFocusRequester != null) {
                    Modifier.focusProperties { up = upFocusRequester }
                } else {
                    Modifier
                }
            )
            .onFocusChanged {
                focused = it.hasFocus
                if (it.hasFocus) onFocused(card) else onFocusCleared()
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.X || event.key == Key.ButtonX)
                ) {
                    onToggleLike()
                    true
                } else {
                    false
                }
            }
            .duoClickable(onClick = onClick)
            .padding(vertical = CardFocusClearance),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .duoFocusRing(CardShape, focused = focused)
                .clip(CardShape)
                .background(DuoPalette.Surface)
                .padding(12.dp),
        ) {
            DynamicAuthorRow(
                card = card,
                showLikeHint = focused,
                onToggleLike = onToggleLike,
            )
            if (card.text.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = card.text,
                    color = DuoPalette.Text,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            card.video?.let {
                Spacer(Modifier.height(10.dp))
                DynamicVideoBlock(it)
            }
            if (card.images.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                DynamicImageGrid(card.images)
            }
            card.forward?.let {
                Spacer(Modifier.height(10.dp))
                DynamicForwardBlock(it)
            }
            Spacer(Modifier.height(10.dp))
            DynamicStatsRow(card = card)
        }
    }
}

@Composable
private fun DynamicAuthorRow(
    card: DynamicCardUi,
    showLikeHint: Boolean,
    onToggleLike: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(DuoPalette.SurfaceHigh),
        ) {
            if (card.authorFace.isNotBlank()) {
                AsyncImage(
                    model = fixPic(card.authorFace),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.authorName,
                    color = DuoPalette.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                KindBadge(card.kindLabel)
            }
            if (card.pubTime.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = card.pubTime,
                    color = DuoPalette.Tertiary,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }
        // Tall cards hide the bottom stats while focused; keep the like state
        // and count in the header, with an X shortcut hint while focused.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onToggleLike)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (card.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (card.liked) "取消点赞" else "点赞",
                tint = if (card.liked) DuoPalette.Pink else DuoPalette.Tertiary,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = formatCount(card.likeCount.toLong()),
                color = if (card.liked) DuoPalette.Pink else DuoPalette.Tertiary,
                fontSize = 9.sp,
            )
            if (showLikeHint) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "X",
                    color = DuoPalette.Tertiary,
                    fontSize = 8.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(DuoPalette.SurfaceHigh)
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun KindBadge(text: String) {
    Text(
        text = text,
        color = DuoPalette.Pink,
        fontSize = 9.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(DuoPalette.Pink.copy(alpha = 0.12f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
private fun DynamicVideoBlock(video: DynamicVideoUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DuoPalette.SurfaceHigh)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DuoPalette.SurfaceHighest),
        ) {
            if (video.cover.isNotBlank()) {
                AsyncImage(
                    model = fixPic(video.cover),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (video.duration.isNotBlank()) {
                Text(
                    text = video.duration,
                    color = Color.White,
                    fontSize = 8.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(
                text = video.title,
                color = DuoPalette.Text,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            val meta = buildString {
                if (video.play.isNotBlank()) append("${video.play}播放")
                if (video.description.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(video.description)
                }
            }
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    color = DuoPalette.Secondary,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DynamicImageGrid(images: List<String>) {
    val shown = images.take(6)
    val extra = images.size - shown.size
    if (shown.size == 1) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(DuoPalette.SurfaceHigh),
        ) {
            AsyncImage(
                model = fixPic(shown.first()),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (extra > 0) ExtraImagesBadge(extra, Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (shown.size == 2) 2 else 3
        val gap = 6.dp
        val cellWidth = (maxWidth - gap * (columns - 1)) / columns
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            shown.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEachIndexed { columnIndex, url ->
                        Box(
                            modifier = Modifier
                                .width(cellWidth)
                                .height(100.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DuoPalette.SurfaceHigh),
                        ) {
                            AsyncImage(
                                model = fixPic(url),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            val index = rowIndex * columns + columnIndex
                            if (extra > 0 && index == shown.lastIndex) {
                                ExtraImagesBadge(
                                    extra,
                                    Modifier.align(Alignment.BottomEnd).padding(5.dp),
                                )
                            }
                        }
                    }
                    repeat(columns - row.size) {
                        Spacer(Modifier.width(cellWidth))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraImagesBadge(extra: Int, modifier: Modifier = Modifier) {
    Text(
        text = "+$extra",
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun DynamicForwardBlock(forward: DynamicForwardUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DuoPalette.SurfaceHigh)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(DuoPalette.SurfaceHighest),
            ) {
                if (forward.authorFace.isNotBlank()) {
                    AsyncImage(
                        model = fixPic(forward.authorFace),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = forward.authorName,
                color = DuoPalette.Secondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (forward.video != null) {
                Text(text = "视频", color = DuoPalette.Pink, fontSize = 9.sp)
            }
        }
        if (forward.text.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = forward.text,
                color = DuoPalette.Text,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val preview = forward.video?.cover?.takeIf { it.isNotBlank() } ?: forward.image
        if (!preview.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DuoPalette.SurfaceHighest),
            ) {
                AsyncImage(
                    model = fixPic(preview),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (forward.video != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
        }
        if (forward.video != null && forward.video.title.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = forward.video.title,
                color = DuoPalette.Text,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DynamicStatsRow(
    card: DynamicCardUi,
    showLike: Boolean = false,
    onComments: (() -> Unit)? = null,
) {
    val text = buildString {
        append("评论 ${formatCount(card.commentCount.toLong())}  ·  ")
        append("转发 ${formatCount(card.forwardCount.toLong())}")
        if (showLike) append("  ·  赞 ${formatCount(card.likeCount.toLong())}")
    }
    Text(
        text = text,
        color = DuoPalette.Tertiary,
        fontSize = 10.sp,
        maxLines = 1,
        modifier = if (onComments != null) {
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .pointerInput(Unit) { detectTapGestures { onComments() } }
        } else {
            Modifier
        },
    )
}

@Composable
private fun DynamicFooter(
    isLoadingMore: Boolean,
    hasMore: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoadingMore -> CircularProgressIndicator(
                color = DuoPalette.Pink,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
            error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "加载失败",
                    color = DuoPalette.Error,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.width(8.dp))
                DuoPillButton(text = "重试", onClick = onRetry)
            }
            !hasMore -> Text(
                text = "没有更多动态了",
                color = DuoPalette.Tertiary,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun DynamicReaderLayer(
    overlay: DynamicOverlay,
    session: DuoSessionViewModel?,
    onBack: () -> Unit,
    onOpenGallery: (Int) -> Unit,
    onIndexChange: (Int) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = LocalDensity.current
    val imageCount = overlay.card.images.size
    val step = with(density) { 96.dp.toPx() }
    var detailTab by remember(overlay.card.id, overlay.kind) { mutableStateOf(0) }
    var sentRpid by remember(overlay.card.id, overlay.kind) { mutableStateOf<Long?>(null) }
    var composerDismiss by remember(overlay.card.id, overlay.kind) {
        mutableStateOf<(() -> Unit)?>(null)
    }
    var sentNotice by remember(overlay.card.id, overlay.kind) { mutableStateOf<String?>(null) }
    val detailBodyFocus = remember { FocusRequester() }
    val detailCommentsFocus = remember { FocusRequester() }
    val galleryZoom = rememberImageZoomState()
    LaunchedEffect(overlay.card.id, overlay.kind, overlay.imageIndex) {
        galleryZoom.reset()
    }
    DisposableEffect(session, overlay, detailTab) {
        session?.setHintOverride(
            when (overlay) {
                is DynamicOverlay.Detail -> if (detailTab == 1) {
                    "LB/RB 正文/评论 · Y 点赞 · 方向键滚动"
                } else {
                    "LB/RB 正文/评论 · Y 复制链接 · 方向键滚动"
                }
                is DynamicOverlay.Gallery ->
                    "R2/L2 缩放 · 左摇杆/拖动移动 · 方向键切换"
                is DynamicOverlay.Text -> "Y 复制链接 · 方向键滚动"
            },
        )
        onDispose { session?.setHintOverride(null) }
    }

    LaunchedEffect(overlay.card.id, overlay.kind) {
        delay(40L)
        runCatching { focus.requestPageFocus() }
    }
    // imageIndex is part of the key: B from the gallery must restore the
    // currently shown picture, not the index captured when the gallery opened.
    DisposableEffect(session, overlay.card.id, overlay.kind, overlay.imageIndex, detailTab) {
        session?.setBackInterceptor {
            val dismissComposer = composerDismiss
            when {
                dismissComposer != null -> {
                    composerDismiss = null
                    dismissComposer()
                }
                overlay is DynamicOverlay.Detail && detailTab == 1 -> detailTab = 0
                else -> onBack()
            }
            true
        }
        onDispose { session?.setBackInterceptor(null) }
    }

    val shareCurrent = { shareDynamic(context, overlay.card) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoPalette.Scrim)
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (overlay is DynamicOverlay.Detail && detailTab == 1) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.ButtonL1 -> {
                            detailTab = 0
                            true
                        }
                        Key.ButtonR1 -> true
                        else -> false
                    }
                }
                when (event.key) {
                    Key.DirectionUp -> {
                        if (overlay !is DynamicOverlay.Gallery) {
                            scope.launch { scroll.scrollBy(-step) }
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        if (overlay !is DynamicOverlay.Gallery) {
                            scope.launch { scroll.scrollBy(step) }
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (imageCount > 1) {
                            onIndexChange((overlay.imageIndex - 1 + imageCount) % imageCount)
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (imageCount > 1) {
                            onIndexChange((overlay.imageIndex + 1) % imageCount)
                        }
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        if (overlay is DynamicOverlay.Detail && imageCount > 0) {
                            onOpenGallery(overlay.imageIndex)
                        }
                        true
                    }
                    Key.ButtonL1 -> {
                        if (overlay is DynamicOverlay.Detail) {
                            detailTab = 0
                            true
                        } else {
                            false
                        }
                    }
                    Key.ButtonR1 -> {
                        if (overlay is DynamicOverlay.Detail) {
                            detailTab = 1
                            true
                        } else {
                            false
                        }
                    }
                    Key.ButtonL2 -> {
                        if (overlay is DynamicOverlay.Gallery) {
                            galleryZoom.zoomBy(1f / 1.25f)
                            true
                        } else {
                            false
                        }
                    }
                    Key.ButtonR2 -> {
                        if (overlay is DynamicOverlay.Gallery) {
                            galleryZoom.zoomBy(1.25f)
                            true
                        } else {
                            false
                        }
                    }
                    Key.X, Key.ButtonX -> {
                        shareCurrent()
                        true
                    }
                    else -> false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {}
            .focusable(),
    ) {
        when (overlay) {
            is DynamicOverlay.Detail -> DynamicDetailOverlay(
                card = overlay.card,
                selectedImage = overlay.imageIndex,
                scroll = scroll,
                onBack = onBack,
                onOpenGallery = onOpenGallery,
                onShare = shareCurrent,
                tab = detailTab,
                onTab = { detailTab = it },
                bodyTabFocus = detailBodyFocus,
                commentsTabFocus = detailCommentsFocus,
                commentsContent = {
                    DynamicCommentsOverlay(
                        card = overlay.card,
                        session = session,
                        onReaderBack = onBack,
                        onClose = { detailTab = 0 },
                        embedded = true,
                        tabFocus = detailCommentsFocus,
                        sentRpid = sentRpid,
                        onSentRpid = { sentRpid = it },
                        onComposerDismiss = { composerDismiss = it },
                        sentNotice = sentNotice,
                        onSentNotice = { sentNotice = it },
                    )
                },
            )
            is DynamicOverlay.Gallery -> DynamicGalleryOverlay(
                card = overlay.card,
                imageIndex = overlay.imageIndex,
                zoom = galleryZoom,
                onBack = onBack,
                onIndexChange = onIndexChange,
                onShare = shareCurrent,
            )
            is DynamicOverlay.Text -> DynamicTextOverlay(
                card = overlay.card,
                scroll = scroll,
                onBack = onBack,
                onShare = shareCurrent,
            )
        }
    }
}

@Composable
private fun DynamicCommentsOverlay(
    card: DynamicCardUi,
    session: DuoSessionViewModel?,
    onReaderBack: () -> Unit,
    onClose: () -> Unit,
    embedded: Boolean = false,
    tabFocus: FocusRequester? = null,
    sentRpid: Long? = null,
    onSentRpid: (Long?) -> Unit = {},
    onComposerDismiss: (((() -> Unit)?) -> Unit) = {},
    sentNotice: String? = null,
    onSentNotice: (String?) -> Unit = {},
) {
    val repository = remember { BilibiliRepository() }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val loadMoreFocus = remember { FocusRequester() }
    val composeFocus = remember { FocusRequester() }
    val composerState = rememberDuoTextFieldState()
    val deleteConfirmFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var draft by remember(card.id) { mutableStateOf("") }
    var sending by remember(card.id) { mutableStateOf(false) }
    var sendError by remember(card.id) { mutableStateOf<String?>(null) }
    val selfMid = BilibiliApi.parseSelfMid()
    var deleteTarget by remember(card.id) { mutableStateOf<CommentItem?>(null) }
    var deleting by remember(card.id) { mutableStateOf(false) }
    var deleteError by remember(card.id) { mutableStateOf<String?>(null) }
    var recalling by remember(card.id) { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    var sendFocused by remember { mutableStateOf(false) }
    var recallFocused by remember { mutableStateOf(false) }
    var comments by remember(card.id) { mutableStateOf<List<CommentItem>>(emptyList()) }
    var loading by remember(card.id) { mutableStateOf(true) }
    var loadingMore by remember(card.id) { mutableStateOf(false) }
    var ended by remember(card.id) { mutableStateOf(false) }
    var error by remember(card.id) { mutableStateOf<String?>(null) }
    var page by remember(card.id) { mutableStateOf(1) }
    var focusedOnce by remember(card.id) { mutableStateOf(false) }
    var focusedRpid by remember(card.id) { mutableStateOf<Long?>(null) }
    var likedState by remember(card.id) { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    var likeDelta by remember(card.id) { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    val dynamicId = card.commentOid.takeIf { it > 0L }

    suspend fun load(targetPage: Int) {
        if (dynamicId == null) {
            loading = false
            error = "动态 ID 无效"
            return
        }
        repository.getDynamicComments(dynamicId, type = card.commentType, page = targetPage)
            .onSuccess { list ->
                val fresh = (list.topReplies ?: emptyList()) + (list.replies ?: emptyList())
                comments = if (targetPage == 1) {
                    fresh.distinctBy { it.rpid }
                } else {
                    (comments + fresh).distinctBy { it.rpid }
                }
                page = targetPage
                ended = fresh.size < 20
                error = null
            }
            .onFailure { error = it.message ?: "评论加载失败" }
        loading = false
        loadingMore = false
    }

    LaunchedEffect(card.id) { load(1) }
    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty() && !focusedOnce) {
            focusedOnce = true
            delay(80L)
            runCatching { firstFocus.requestPageFocus() }
        }
    }
    fun submitComment() {
        val oid = dynamicId ?: return
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        if (text.length > 1000) {
            sendError = "评论最多 1000 字"
            return
        }
        sending = true
        sendError = null
        scope.launch {
            repository.addComment(oid, text, type = card.commentType)
                .onSuccess {
                    draft = ""
                    onSentRpid(it.rpid.takeIf { rpid -> rpid > 0L })
                    onSentNotice(it.successToast.ifBlank { "评论已提交" })
                    load(1)
                    runCatching { composeFocus.requestPageFocus() }
                    // 评论索引有延迟，稍后再拉一次，避免要手动刷新才看到。
                    delay(700L)
                    load(1)
                }
                .onFailure { sendError = it.message ?: "评论发送失败" }
            sending = false
        }
    }

    fun deleteComment(comment: CommentItem) {
        val oid = dynamicId ?: return
        if (deleting) return
        deleting = true
        deleteError = null
        scope.launch {
            repository.deleteComment(oid, comment.rpid, type = card.commentType)
                .onSuccess {
                    deleteTarget = null
                    deleteError = null
                    comments = comments.filterNot { it.rpid == comment.rpid }
                    load(1)
                    runCatching { firstFocus.requestPageFocus() }
                }
                .onFailure { deleteError = it.message ?: "删除失败" }
            deleting = false
        }
    }

    val composerActive = fieldFocused || sendFocused || recallFocused
    suspend fun restoreCommentFocus() {
        delay(60L)
        runCatching { firstFocus.requestPageFocus() }
    }
    LaunchedEffect(deleteTarget, composerActive) {
        onComposerDismiss(
            when {
                deleteTarget != null -> {
                    {
                        deleteTarget = null
                        deleteError = null
                        scope.launch { restoreCommentFocus() }
                    }
                }
                composerActive -> {
                    {
                        keyboard?.hide()
                        fieldFocused = false
                        sendFocused = false
                        recallFocused = false
                        tabFocus?.let { runCatching { it.requestPageFocus() } }
                    }
                }
                else -> null
            },
        )
    }
    DisposableEffect(Unit) {
        onDispose { onComposerDismiss(null) }
    }
    LaunchedEffect(deleteTarget) {
        if (deleteTarget != null) {
            delay(60L)
            runCatching { deleteConfirmFocus.requestPageFocus() }
        }
    }

    fun toggleLike(comment: CommentItem) {
        val oid = dynamicId ?: return
        if (BilibiliApi.loginCookies.isBlank()) return
        val current = likedState[comment.rpid] == true
        likedState = likedState + (comment.rpid to !current)
        likeDelta = likeDelta + (comment.rpid to ((likeDelta[comment.rpid] ?: 0) + if (current) -1 else 1))
        scope.launch {
            repository.likeComment(oid, comment.rpid, !current, type = card.commentType)
                .onFailure {
                    likedState = likedState + (comment.rpid to current)
                    likeDelta = likeDelta + (comment.rpid to ((likeDelta[comment.rpid] ?: 0) + if (current) 1 else -1))
                }
        }
    }

    if (!embedded) DisposableEffect(session, card.id) {
        session?.setBackInterceptor {
            onClose()
            true
        }
        onDispose {
            // The reader layer stays mounted behind this overlay; restore its
            // B action instead of clearing the interceptor.
            session?.setBackInterceptor {
                onReaderBack()
                true
            }
        }
    }

    Column(
        Modifier
            .then(if (embedded) Modifier.fillMaxSize() else Modifier.fillMaxSize().background(DuoPalette.Bg))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.X || event.key == Key.ButtonX)
                ) {
                    val id = focusedRpid ?: return@onPreviewKeyEvent false
                    comments.firstOrNull { it.rpid == id }?.let { toggleLike(it) }
                    true
                } else {
                    false
                }
            },
    ) {
        if (!embedded) {
            Row(
                modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(DuoPalette.SurfaceHigh)
                        .pointerInput(Unit) { detectTapGestures { onClose() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("←", color = DuoPalette.Text, fontSize = 18.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "动态评论",
                        color = DuoPalette.Text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${card.authorName} · ${formatCount(card.commentCount.toLong())} 条 · Y 点赞",
                        color = DuoPalette.Tertiary,
                        fontSize = 10.sp,
                    )
                }
                Text("A 返回", color = DuoPalette.Tertiary, fontSize = 10.sp)
            }
        }
        if (deleteTarget != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .height(38.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.DirectionUp || event.key == Key.DirectionDown)
                        ) {
                            true
                        } else {
                            false
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "删除这条评论？",
                    color = DuoPalette.Text,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(DuoPalette.SurfaceHigh)
                        .duoClickable {
                            deleteTarget = null
                            deleteError = null
                            scope.launch { restoreCommentFocus() }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("取消", color = DuoPalette.Secondary, fontSize = 11.sp)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(32.dp)
                        .focusRequester(deleteConfirmFocus)
                        .duoFocusRing(RoundedCornerShape(7.dp))
                        .clip(RoundedCornerShape(7.dp))
                        .background(DuoPalette.Pink)
                        .duoClickable(enabled = !deleting) {
                            deleteTarget?.let(::deleteComment)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (deleting) "删除中" else "删除",
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                }
            }
            deleteError?.let {
                Text(
                    it,
                    color = DuoPalette.Error,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
        } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .height(38.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DuoTextFieldGate(
                state = composerState,
                focusRequester = composeFocus,
                modifier = Modifier.weight(1f),
                downFocus = if (comments.isNotEmpty()) firstFocus else null,
                onGateFocusChanged = { fieldFocused = it },
            ) { editing, gateFocused, fieldModifier ->
                BasicTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        sendError = null
                    },
                    enabled = editing,
                    singleLine = true,
                    textStyle = TextStyle(color = DuoPalette.Text, fontSize = 12.sp),
                    cursorBrush = SolidColor(DuoPalette.Pink),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(fieldModifier)
                        .onFocusChanged { fieldFocused = it.isFocused }
                        .clip(RoundedCornerShape(7.dp))
                        .background(DuoPalette.SurfaceHigh)
                        .border(
                            width = 1.dp,
                            color = if (fieldFocused || gateFocused) DuoPalette.Pink else Color.Transparent,
                            shape = RoundedCornerShape(7.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text(
                                "写评论…",
                                color = DuoPalette.Tertiary,
                                fontSize = 11.sp,
                            )
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(34.dp)
                    .duoFocusRing(RoundedCornerShape(7.dp))
                    .clip(RoundedCornerShape(7.dp))
                    .background(DuoPalette.Pink.copy(alpha = if (draft.isBlank()) 0.3f else 1f))
                    .onFocusChanged { sendFocused = it.isFocused }
                    .duoClickable(enabled = draft.isNotBlank() && !sending) { submitComment() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (sending) "发送中" else "发送",
                    color = Color.White,
                    fontSize = 11.sp,
                )
            }
            sentRpid?.let { rpid ->
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(34.dp)
                        .duoFocusRing(RoundedCornerShape(7.dp))
                        .clip(RoundedCornerShape(7.dp))
                        .background(DuoPalette.SurfaceHigh)
                        .onFocusChanged { recallFocused = it.isFocused }
                        .duoClickable(enabled = !recalling) {
                            val oid = dynamicId ?: return@duoClickable
                            recalling = true
                            scope.launch {
                                repository.deleteComment(oid, rpid, type = card.commentType)
                                    .onSuccess {
                                        onSentRpid(null)
                                        onSentNotice(null)
                                        load(1)
                                    }
                                    .onFailure { sendError = it.message ?: "撤回失败" }
                                recalling = false
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (recalling) "撤回中" else "撤回",
                        color = DuoPalette.Secondary,
                        fontSize = 10.sp,
                    )
                }
            }
        }
        }
        sentNotice?.let {
            Text(
                it,
                color = DuoPalette.Secondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        sendError?.let {
            Text(
                it,
                color = DuoPalette.Error,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DuoPalette.Pink, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
            error != null && comments.isEmpty() -> Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error.orEmpty(), color = DuoPalette.Error, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                DuoPillButton(
                    text = "重试",
                    onClick = {
                        loading = true
                        error = null
                        scope.launch { load(1) }
                    },
                )
            }
            comments.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("暂无评论", color = DuoPalette.Tertiary, fontSize = 11.sp)
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(comments, key = { _, item -> item.rpid }) { index, comment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                            )
                            .duoFocusRing(RoundedCornerShape(6.dp))
                            .onFocusChanged { if (it.isFocused) focusedRpid = comment.rpid }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    event.key == Key.DirectionCenter &&
                                    selfMid != null && selfMid > 0L && comment.member.mid == selfMid
                                ) {
                                    deleteTarget = comment
                                    deleteError = null
                                    true
                                } else {
                                    false
                                }
                            }
                            .focusable()
                            .padding(4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier.size(30.dp).clip(CircleShape).background(DuoPalette.SurfaceHigh),
                        ) {
                            if (comment.member.avatar.isNotBlank()) {
                                AsyncImage(
                                    model = fixPic(comment.member.avatar),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    comment.member.uname,
                                    color = DuoPalette.Secondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                val liked = likedState[comment.rpid] == true
                                Text(
                                    text = "赞 ${(comment.like + (likeDelta[comment.rpid] ?: 0)).coerceAtLeast(0)}",
                                    color = if (liked) DuoPalette.Pink else DuoPalette.Tertiary,
                                    fontSize = 9.sp,
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                comment.content.message,
                                color = DuoPalette.Text,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
                item(key = "comments_footer") {
                    if (!ended) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(loadMoreFocus)
                                .duoFocusRing(RoundedCornerShape(6.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .background(DuoPalette.Surface)
                                .duoClickable {
                                    if (!loadingMore) {
                                        loadingMore = true
                                        scope.launch { load(page + 1) }
                                    }
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (loadingMore) {
                                CircularProgressIndicator(
                                    color = DuoPalette.Pink,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                            } else {
                                Text("加载更多评论", color = DuoPalette.Pink, fontSize = 10.sp)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .duoFocusRing(RoundedCornerShape(6.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .focusable()
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("没有更多评论了", color = DuoPalette.Tertiary, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun shareDynamic(context: Context, card: DynamicCardUi) {
    val url = card.playbackBvid?.let { "https://www.bilibili.com/video/$it" }
        ?: "https://t.bilibili.com/${card.id}"
    val headline = card.video?.title?.trim().orEmpty()
        .ifBlank { card.text.trim().take(60) }
    val text = if (headline.isBlank()) url else "【$headline】$url"
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard == null) {
        Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show()
        return
    }
    clipboard.setPrimaryClip(ClipData.newPlainText("动态链接", text))
    Toast.makeText(context, "链接已复制，可粘贴分享", Toast.LENGTH_SHORT).show()
}

@Composable
private fun ReaderHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onShare: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(DuoPalette.SurfaceHigh)
                .pointerInput(Unit) { detectTapGestures { onBack() } },
            contentAlignment = Alignment.Center,
        ) {
            Text("←", color = DuoPalette.Text, fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = DuoPalette.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = DuoPalette.Tertiary,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }
        if (onShare != null) {
            Text(
                text = "复制链接",
                color = DuoPalette.Pink,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(DuoPalette.Pink.copy(alpha = 0.12f))
                    .pointerInput(Unit) { detectTapGestures { onShare() } }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text("A 返回", color = DuoPalette.Tertiary, fontSize = 10.sp)
    }
}

@Composable
private fun DynamicDetailOverlay(
    card: DynamicCardUi,
    selectedImage: Int,
    scroll: androidx.compose.foundation.ScrollState,
    onBack: () -> Unit,
    onOpenGallery: (Int) -> Unit,
    onShare: () -> Unit,
    tab: Int,
    onTab: (Int) -> Unit,
    bodyTabFocus: FocusRequester,
    commentsTabFocus: FocusRequester,
    commentsContent: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(DuoPalette.Bg)) {
        ReaderHeader(
            title = "动态详情",
            subtitle = listOf(card.authorName, card.pubTime).filter { it.isNotBlank() }.joinToString(" · "),
            onBack = onBack,
            onShare = onShare,
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ReaderTab("正文", tab == 0, bodyTabFocus) { onTab(0) }
            ReaderTab("评论 ${formatCount(card.commentCount.toLong())}", tab == 1, commentsTabFocus) { onTab(1) }
        }
        LaunchedEffect(tab) {
            delay(80L)
            runCatching {
                if (tab == 0) bodyTabFocus.requestPageFocus() else commentsTabFocus.requestPageFocus()
            }
        }
        if (tab == 1) {
            commentsContent()
        } else {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(DuoPalette.SurfaceHigh),
                ) {
                    if (card.authorFace.isNotBlank()) {
                        AsyncImage(
                            model = fixPic(card.authorFace),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = card.authorName,
                        color = DuoPalette.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Text(
                        text = listOf(card.pubTime, card.kindLabel).filter { it.isNotBlank() }
                            .joinToString(" · "),
                        color = DuoPalette.Tertiary,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
                Text(
                    text = card.kindLabel,
                    color = DuoPalette.Pink,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DuoPalette.Pink.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = card.text.ifBlank { "这条动态没有正文。" },
                color = DuoPalette.Text,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )
            card.forward?.let {
                Spacer(Modifier.height(12.dp))
                DynamicForwardBlock(it)
            }
            if (card.images.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "图片 ${selectedImage + 1} / ${card.images.size}",
                        color = DuoPalette.Secondary,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("B 查看大图", color = DuoPalette.Pink, fontSize = 10.sp)
                }
                Spacer(Modifier.height(6.dp))
                DetailImageGrid(
                    images = card.images,
                    selected = selectedImage,
                    onOpen = onOpenGallery,
                )
            }
            Spacer(Modifier.height(12.dp))
            DynamicStatsRow(card, showLike = true, onComments = { onTab(1) })
            Spacer(Modifier.height(10.dp))
            Text(
                text = "方向键滚动 · LB/RB 切换 正文/评论 · Y 复制链接 · A 返回",
                color = DuoPalette.Tertiary,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(16.dp))
        }
        }
    }
}

@Composable
private fun ReaderTab(
    text: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .height(38.dp)
            .focusRequester(focusRequester)
            .duoFocusRing(RoundedCornerShape(6.dp))
            .duoClickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            color = if (selected) DuoPalette.Pink else DuoPalette.Secondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .width(18.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (selected) DuoPalette.Pink else Color.Transparent),
        )
    }
}

@Composable
private fun DetailImageGrid(
    images: List<String>,
    selected: Int,
    onOpen: (Int) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 6.dp
        val cellWidth = (maxWidth - gap * 2) / 3
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            images.chunked(3).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEachIndexed { columnIndex, url ->
                        val index = rowIndex * 3 + columnIndex
                        Box(
                            modifier = Modifier
                                .width(cellWidth)
                                .height(96.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DuoPalette.SurfaceHigh)
                                .border(
                                    width = if (index == selected) 2.dp else 1.dp,
                                    color = if (index == selected) DuoPalette.Text
                                    else DuoPalette.Outline,
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .pointerInput(index) {
                                    detectTapGestures { onOpen(index) }
                                },
                        ) {
                            AsyncImage(
                                model = fixPic(url),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    repeat(3 - row.size) {
                        Spacer(Modifier.width(cellWidth))
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicGalleryOverlay(
    card: DynamicCardUi,
    imageIndex: Int,
    zoom: ImageZoomState,
    onBack: () -> Unit,
    onIndexChange: (Int) -> Unit,
    onShare: () -> Unit,
) {
    val count = card.images.size
    Column(Modifier.fillMaxSize().background(DuoPalette.Bg)) {
        ReaderHeader(
            title = "图片画册",
            subtitle = if (count > 1) {
                "${imageIndex + 1} / $count · 方向键切换"
            } else {
                "${imageIndex + 1} / 1"
            },
            onBack = onBack,
            onShare = onShare,
        )
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (count > 1) {
                GalleryArrow(text = "‹") {
                    onIndexChange((imageIndex - 1 + count) % count)
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .fillMaxHeight(0.92f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DuoPalette.SurfaceHigh)
                        .border(1.dp, DuoPalette.Outline, RoundedCornerShape(10.dp)),
                ) {
                    val url = card.images.getOrNull(imageIndex)
                    if (!url.isNullOrBlank()) {
                        DuoZoomableImage(
                            model = fixPic(url),
                            contentDescription = null,
                            state = zoom,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Text(
                        text = "${imageIndex + 1} / ${count.coerceAtLeast(1)}",
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            if (count > 1) {
                GalleryArrow(text = "›") {
                    onIndexChange((imageIndex + 1) % count)
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
        if (count > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                card.images.forEachIndexed { index, url ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DuoPalette.SurfaceHigh)
                            .border(
                                width = if (index == imageIndex) 2.dp else 1.dp,
                                color = if (index == imageIndex) DuoPalette.Text
                                else DuoPalette.Outline,
                                shape = RoundedCornerShape(6.dp),
                            )
                            .pointerInput(index) {
                                detectTapGestures { onIndexChange(index) }
                            },
                    ) {
                        AsyncImage(
                            model = fixPic(url),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        Text(
            text = if (count > 1) {
                "A 返回详情 · 方向键切换 · R2/L2 缩放 · 左摇杆/拖动移动 · 双指缩放"
            } else {
                "A 返回详情 · R2/L2 缩放 · 左摇杆/拖动移动 · 双指缩放"
            },
            color = DuoPalette.Tertiary,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun GalleryArrow(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(DuoPalette.SurfaceHigh)
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = DuoPalette.Text, fontSize = 24.sp)
    }
}

@Composable
private fun DynamicTextOverlay(
    card: DynamicCardUi,
    scroll: androidx.compose.foundation.ScrollState,
    onBack: () -> Unit,
    onShare: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(DuoPalette.Bg)) {
        ReaderHeader(
            title = "纯文字动态",
            subtitle = listOf(card.authorName, card.pubTime).filter { it.isNotBlank() }.joinToString(" · "),
            onBack = onBack,
            onShare = onShare,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(DuoPalette.SurfaceHigh),
                ) {
                    if (card.authorFace.isNotBlank()) {
                        AsyncImage(
                            model = fixPic(card.authorFace),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(
                        text = card.authorName,
                        color = DuoPalette.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = listOf(card.pubTime, card.kindLabel).filter { it.isNotBlank() }
                            .joinToString(" · "),
                        color = DuoPalette.Tertiary,
                        fontSize = 10.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = card.text.ifBlank { "这条动态没有正文。" },
                color = DuoPalette.Text,
                fontSize = 16.sp,
                lineHeight = 26.sp,
            )
            card.forward?.let {
                Spacer(Modifier.height(12.dp))
                DynamicForwardBlock(it)
            }
            Spacer(Modifier.height(16.dp))
            DynamicStatsRow(card, showLike = true)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "方向键滚动 · LB/RB 评论 · Y 复制链接 · A 返回列表",
                color = DuoPalette.Tertiary,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun DynamicCardUi.withReaderImages(): DynamicCardUi {
    if (images.isNotEmpty()) return this
    val forwardImage = forward?.image?.takeIf { it.isNotBlank() } ?: return this
    return copy(images = listOf(forwardImage))
}

private fun DynamicCardUi.toTopReader(kind: TopDynamicKind, index: Int): TopDynamicReaderUi {
    val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
    val title = lines.firstOrNull().orEmpty()
        .ifBlank { if (images.isNotEmpty()) "动态图文" else "纯文字动态" }
    return TopDynamicReaderUi(
        kind = kind,
        title = title,
        text = text,
        authorName = authorName,
        authorFace = authorFace,
        pubTime = pubTime,
        images = images,
        imageIndex = index.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
        kindLabel = kindLabel,
        commentCount = commentCount,
        forwardCount = forwardCount,
        likeCount = likeCount,
    )
}

private fun DynamicCardUi.toTopPreview(): TopPreviewUi {
    val video = video ?: forward?.video
    val image = images.firstOrNull() ?: forward?.image
    val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
    val title = video?.title?.takeIf { it.isNotBlank() }
        ?: lines.firstOrNull().orEmpty().ifBlank { "关注动态" }
    val description = when {
        video != null -> text
        lines.size > 1 -> lines.drop(1).joinToString("\n")
        else -> listOf(kindLabel, authorName).filter { it.isNotBlank() }.joinToString(" · ")
    }
    return TopPreviewUi(
        bvid = video?.bvid.orEmpty(),
        title = title,
        coverUrl = fixPic(video?.cover?.takeIf { it.isNotBlank() } ?: image.orEmpty()),
        ownerName = authorName,
        ownerFace = authorFace,
        description = description,
        category = kindLabel,
        coverFit = video == null && !image.isNullOrBlank(),
        eyebrow = "● 动态预览",
        ownerLabel = "UP 主",
        actionHint = if (video != null) "开始播放 · A 返回" else "仅预览 · A 返回",
    )
}

private fun dynamicAmbient(loggedIn: Boolean, state: DynamicUiState): TopAmbientUi = when {
    !loggedIn -> TopAmbientUi(
        eyebrow = "DYNAMIC / 动态",
        title = "登录后，\n看看关注的人在做什么。",
        subtitle = "关注 UP 主的视频、图文与转发会集中显示在这里；下屏可切换全部、视频和图文。",
        glyph = TopGlyph.DYNAMIC,
    )
    state.isLoading && state.items.isEmpty() -> TopAmbientUi(
        eyebrow = "DYNAMIC / 动态",
        title = "正在同步关注动态。",
        subtitle = "稍等片刻；加载完成后聚焦卡片，上屏会显示作者与正文预览。",
        glyph = TopGlyph.DYNAMIC,
    )
    state.error != null && state.items.isEmpty() -> TopAmbientUi(
        eyebrow = "DYNAMIC / 动态",
        title = "动态加载失败。",
        subtitle = "下屏可重试；网络恢复后会继续同步关注动态。",
        glyph = TopGlyph.DYNAMIC,
        accent = 0xFFF2555A,
    )
    state.items.isEmpty() -> TopAmbientUi(
        eyebrow = "DYNAMIC / 动态",
        title = when (state.filter) {
            DynamicFilter.ALL -> "关注的人还没有新动态。"
            DynamicFilter.VIDEO -> "关注的人还没有视频动态。"
            DynamicFilter.IMAGE -> "关注的人还没有图文动态。"
        },
        subtitle = "去首页或搜索关注更多 UP 主，他们的更新会出现在这里。",
        glyph = TopGlyph.DYNAMIC,
    )
    else -> TopAmbientUi(
        eyebrow = "DYNAMIC / 动态",
        title = "关注动态。",
        subtitle = "聚焦卡片查看作者、正文与配图；带视频的动态按 B 直接播放。",
        glyph = TopGlyph.DYNAMIC,
    )
}