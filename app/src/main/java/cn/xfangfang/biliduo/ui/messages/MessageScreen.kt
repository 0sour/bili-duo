@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package cn.xfangfang.biliduo.ui.messages

import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import cn.xfangfang.biliduo.data.api.BilibiliApi
import cn.xfangfang.biliduo.data.model.MessageFeedKind
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.common.DuoEmptyState
import cn.xfangfang.biliduo.ui.common.DuoIconAction
import cn.xfangfang.biliduo.ui.common.DuoPageHeader
import cn.xfangfang.biliduo.ui.common.DuoPalette
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.ui.duo.duoTabCycling
import cn.xfangfang.biliduo.ui.duo.requestPageFocus
import cn.xfangfang.biliduo.ui.top.TopAmbientUi
import cn.xfangfang.biliduo.ui.top.TopGlyph
import cn.xfangfang.biliduo.util.fixPic
import kotlinx.coroutines.delay

private val messageTabs = listOf(
    MessageFeedKind.REPLY to "回复我的",
    MessageFeedKind.AT to "@我的",
    MessageFeedKind.LIKE to "收到的赞",
)

@Composable
fun MessageScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenVideo: (bvid: String) -> Unit,
    onTopAmbient: (TopAmbientUi) -> Unit = {},
    onFocusCleared: () -> Unit = {},
    session: DuoSessionViewModel? = null,
    viewModel: MessageViewModel = viewModel(),
) {
    val ui by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val loggedIn = BilibiliApi.parseSelfMid() != null
    val tabFocusers = remember { List(messageTabs.size) { FocusRequester() } }
    val firstRowFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val footerFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var contentFocusWanted by remember { mutableStateOf(true) }

    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            viewModel.load()
            viewModel.refreshUnread()
        }
    }
    LaunchedEffect(ui.tab) {
        onTopAmbient(
            TopAmbientUi(
                eyebrow = "MESSAGE / 消息",
                title = "回复、@ 和赞，\n都在这里。",
                subtitle = "方向键浏览通知，确认键直接打开相关视频。",
                glyph = TopGlyph.MESSAGE,
            )
        )
    }
    LaunchedEffect(loggedIn, ui.isLoading, ui.rows.size, ui.error) {
        if (!loggedIn) {
            delay(80)
            runCatching { retryFocus.requestPageFocus() }
            return@LaunchedEffect
        }
        if (ui.isLoading) return@LaunchedEffect
        delay(80)
        if (ui.rows.isNotEmpty()) {
            if (contentFocusWanted) {
                contentFocusWanted = false
                runCatching { firstRowFocus.requestPageFocus() }
            }
        } else {
            runCatching { retryFocus.requestPageFocus() }
        }
    }
    LaunchedEffect(ui.rows.size) {
        if (ui.rows.isEmpty()) onFocusCleared()
    }

    fun openRow(row: MessageRowUi) {
        val bvid = row.bvid
        val aid = row.aid
        when {
            !bvid.isNullOrBlank() -> onOpenVideo(bvid)
            aid != null -> viewModel.resolveBvid(
                aid = aid,
                onResolved = onOpenVideo,
                onError = { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                },
            )
            else -> Toast.makeText(context, "该消息没有可打开的视频", Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun cycleTab(delta: Int) {
        val index = messageTabs.indexOfFirst { it.first == ui.tab }.coerceAtLeast(0)
        val next = messageTabs[(index + delta + messageTabs.size) % messageTabs.size].first
        if (next == ui.tab) return
        contentFocusWanted = true
        viewModel.selectTab(next)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoPalette.Bg)
            .duoTabCycling(
                onPreviousTab = { cycleTab(-1) },
                onNextTab = { cycleTab(1) },
            ),
    ) {
        DuoPageHeader(
            title = "消息",
            onBack = onBack,
            actions = {
                if (loggedIn) {
                    DuoIconAction(
                        icon = Icons.Default.Refresh,
                        contentDescription = "刷新消息",
                        tint = DuoPalette.Text,
                        onClick = {
                            viewModel.refresh()
                            viewModel.refreshUnread()
                        },
                    )
                }
            },
        )

        MessageTabRow(
            selected = ui.tab,
            focusRequesters = tabFocusers,
            unreadByKind = mapOf(
                MessageFeedKind.REPLY to ui.unreadReply,
                MessageFeedKind.AT to ui.unreadAt,
                MessageFeedKind.LIKE to ui.unreadLike,
            ),
            contentFocus = if (ui.rows.isNotEmpty()) firstRowFocus else retryFocus,
            onSelect = { tab ->
                if (tab != ui.tab) {
                    // Direct activation keeps the ring on the chip; only the
                    // L1/R1 shortcut moves into the list.
                    contentFocusWanted = false
                    viewModel.selectTab(tab)
                }
            },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !loggedIn -> {
                    DuoEmptyState(
                        title = "登录后查看消息",
                        hint = "回复、@ 和收到的赞会集中在这里",
                        actionText = "去登录",
                        onAction = onLogin,
                        focusRequester = retryFocus,
                    )
                }
                ui.isLoading && ui.rows.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = DuoPalette.Pink,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                ui.error != null && ui.rows.isEmpty() -> {
                    DuoEmptyState(
                        title = "加载失败",
                        hint = ui.error,
                        actionText = "重试",
                        onAction = viewModel::refresh,
                        focusRequester = retryFocus,
                    )
                }
                ui.rows.isEmpty() -> {
                    DuoEmptyState(
                        title = "暂无消息",
                        hint = "有人回复或提到你时会出现在这里",
                        actionText = "刷新",
                        onAction = viewModel::refresh,
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
                            top = 4.dp,
                            bottom = 10.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        itemsIndexed(
                            items = ui.rows,
                            key = { index, row -> "${row.key}_$index" },
                        ) { index, row ->
                            MessageRow(
                                row = row,
                                modifier = if (index == 0) {
                                    Modifier.focusRequester(firstRowFocus)
                                } else {
                                    Modifier
                                },
                                onClick = { openRow(row) },
                            )
                        }
                        item(key = "footer") {
                            MessageFooter(
                                isLoadingMore = ui.isLoadingMore,
                                error = ui.loadMoreError,
                                isEnd = ui.isEnd,
                                footerFocus = footerFocus,
                                onLoadMore = viewModel::loadMore,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageTabRow(
    selected: MessageFeedKind,
    focusRequesters: List<FocusRequester>,
    unreadByKind: Map<MessageFeedKind, Int>,
    contentFocus: FocusRequester,
    onSelect: (MessageFeedKind) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        messageTabs.forEachIndexed { index, (kind, label) ->
            val isSelected = kind == selected
            Row(
                modifier = Modifier
                    .height(28.dp)
                    .focusRequester(focusRequesters[index])
                    .focusProperties { down = contentFocus }
                    .duoFocusRing(RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) {
                            DuoPalette.Pink.copy(alpha = 0.16f)
                        } else {
                            DuoPalette.SurfaceHigh
                        }
                    )
                    .duoClickable { onSelect(kind) }
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    color = if (isSelected) DuoPalette.Pink else DuoPalette.Secondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                )
                val unread = unreadByKind[kind] ?: 0
                if (unread > 0) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (unread > 99) "99+" else "$unread",
                        color = if (isSelected) {
                            DuoPalette.Pink.copy(alpha = 0.8f)
                        } else {
                            DuoPalette.Tertiary
                        },
                        fontSize = 9.sp,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "L1/R1 切换",
            color = DuoPalette.Tertiary,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun MessageRow(
    row: MessageRowUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.hasFocus }
            .duoClickable(onClick = onClick)
            // Inside the focus target: the ring needs 7dp of breathing room
            // or Lazy bring-into-view clips its top/bottom edge.
            .padding(vertical = 7.dp)
            .heightIn(min = 86.dp)
            .duoFocusRing(shape, focused = focused)
            .clip(shape)
            .background(DuoPalette.Surface)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(DuoPalette.SurfaceHigh),
        ) {
            AsyncImage(
                model = fixPic(row.avatars.firstOrNull().orEmpty()),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.actorText,
                    color = DuoPalette.Text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = row.actionText,
                    color = DuoPalette.Secondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = messageTimeLabel(row.timeSeconds),
                    color = DuoPalette.Tertiary,
                    fontSize = 9.sp,
                    maxLines = 1,
                )
            }
            if (row.content.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = row.content,
                    color = DuoPalette.Text.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (row.sourceTitle.isNotBlank() || row.sourceImage.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(DuoPalette.SurfaceHigh)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (row.sourceImage.isNotBlank()) {
                        AsyncImage(
                            model = fixPic(row.sourceImage),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(64.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(DuoPalette.SurfaceHighest),
                        )
                        Spacer(Modifier.width(7.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = row.typeLabel,
                            color = DuoPalette.Tertiary,
                            fontSize = 8.5.sp,
                        )
                        if (row.sourceTitle.isNotBlank()) {
                            Text(
                                text = row.sourceTitle,
                                color = DuoPalette.Secondary,
                                fontSize = 10.5.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageFooter(
    isLoadingMore: Boolean,
    error: String?,
    isEnd: Boolean,
    footerFocus: FocusRequester,
    onLoadMore: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoadingMore -> CircularProgressIndicator(
                color = DuoPalette.Pink,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
            error != null -> Box(
                modifier = Modifier
                    .focusRequester(footerFocus)
                    .duoFocusRing(RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .duoClickable(onClick = onLoadMore)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "加载失败 · $error",
                    color = DuoPalette.Error,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            isEnd -> Text(
                text = "没有更多消息了",
                color = DuoPalette.Tertiary,
                fontSize = 9.5.sp,
            )
            else -> Box(
                modifier = Modifier
                    .focusRequester(footerFocus)
                    .duoFocusRing(RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .background(DuoPalette.SurfaceHigh)
                    .duoClickable(onClick = onLoadMore)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Text("加载更多", color = DuoPalette.Secondary, fontSize = 10.sp)
            }
        }
    }
}
