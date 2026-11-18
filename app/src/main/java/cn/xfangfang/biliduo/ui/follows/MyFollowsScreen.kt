package cn.xfangfang.biliduo.ui.follows

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import cn.xfangfang.biliduo.data.api.BilibiliApi
import cn.xfangfang.biliduo.data.model.PgcFollowItem
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
import cn.xfangfang.biliduo.ui.top.TopPreviewUi
import cn.xfangfang.biliduo.util.fixPic
import kotlinx.coroutines.delay

@Composable
fun MyFollowsScreen(
    onBack: () -> Unit,
    onPgcClick: (Long) -> Unit,
    onHome: () -> Unit = {},
    onLogin: () -> Unit = {},
    onTopAmbient: (TopAmbientUi) -> Unit = {},
    onVideoFocused: (TopPreviewUi) -> Unit = {},
    onFocusCleared: () -> Unit = {},
    viewModel: MyFollowsViewModel = viewModel(),
) {
    val ui by viewModel.uiState.collectAsState()
    val loggedIn = BilibiliApi.parseSelfMid() != null
    val gridState = rememberLazyGridState()
    val firstItemFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val typeFocuses = remember { List(2) { FocusRequester() } }

    LaunchedEffect(loggedIn) {
        if (loggedIn) viewModel.load()
    }
    LaunchedEffect(loggedIn, ui.isLoading, ui.items.size) {
        when {
            !loggedIn -> onTopAmbient(
                TopAmbientUi(
                    eyebrow = "MY FOLLOWS / 追番追剧",
                    title = "登录后，\n把在追的都放在这里。",
                    subtitle = "追番、追剧都会出现在这个列表。",
                    glyph = TopGlyph.HISTORY,
                )
            )
            !ui.isLoading && ui.items.isEmpty() -> onTopAmbient(
                TopAmbientUi(
                    eyebrow = "MY FOLLOWS / 追番追剧",
                    title = "还没有追番。",
                    subtitle = "在番剧/影视播放页按「追番」，之后从这里继续。",
                    glyph = TopGlyph.HISTORY,
                )
            )
        }
    }
    LaunchedEffect(ui.isLoading, ui.type) {
        if (ui.items.isNotEmpty() && !ui.isLoading) {
            delay(80)
            runCatching { firstItemFocus.requestPageFocus() }
        }
        if (ui.items.isEmpty()) onFocusCleared()
    }
    LaunchedEffect(ui.error, ui.items.isEmpty()) {
        if (ui.error != null && ui.items.isEmpty()) {
            delay(80)
            runCatching { retryFocus.requestPageFocus() }
        }
    }

    val lastVisible by remember {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    }
    LaunchedEffect(lastVisible, ui.items.size, ui.hasMore) {
        if (ui.hasMore && ui.items.isNotEmpty() && lastVisible >= ui.items.size - 4) {
            viewModel.loadMore()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoPalette.Bg)
            .duoTabCycling(
                onPreviousTab = { viewModel.selectType(if (ui.type == 1) 2 else 1) },
                onNextTab = { viewModel.selectType(if (ui.type == 1) 2 else 1) },
            ),
    ) {
        DuoPageHeader(
            title = "追番追剧",
            onBack = onBack,
            actions = {
                if (loggedIn) {
                    DuoIconAction(
                        icon = Icons.Default.Refresh,
                        contentDescription = "刷新追番",
                        tint = DuoPalette.Text,
                        onClick = viewModel::refresh,
                    )
                }
            },
        )

        if (loggedIn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FollowTypeChip(
                    text = "番剧",
                    active = ui.type == 1,
                    focusRequester = typeFocuses[0],
                    onClick = { viewModel.selectType(1) },
                )
                FollowTypeChip(
                    text = "影视",
                    active = ui.type == 2,
                    focusRequester = typeFocuses[1],
                    onClick = { viewModel.selectType(2) },
                )
                Spacer(Modifier.weight(1f))
                if (ui.isLoading || ui.isLoadingMore) {
                    CircularProgressIndicator(
                        color = DuoPalette.Pink,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !loggedIn -> DuoEmptyState(
                    title = "登录后查看追番",
                    hint = "登录后可同步番剧/影视追番列表",
                    actionText = "去登录",
                    onAction = onLogin,
                    focusRequester = retryFocus,
                )
                ui.isLoading && ui.items.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = DuoPalette.Pink,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(26.dp),
                    )
                }
                ui.error != null && ui.items.isEmpty() -> DuoEmptyState(
                    title = "加载失败",
                    hint = ui.error,
                    actionText = "重试",
                    onAction = viewModel::refresh,
                    focusRequester = retryFocus,
                )
                ui.items.isEmpty() -> DuoEmptyState(
                    title = "还没有追番",
                    hint = "在番剧/影视播放页按「追番」后，这里会出现",
                    actionText = "去首页找番剧",
                    onAction = onHome,
                    focusRequester = retryFocus,
                )
                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(ui.items, key = { _, item -> item.seasonId }) { index, item ->
                        FollowCard(
                            item = item,
                            firstFocus = if (index == 0) firstItemFocus else null,
                            onFocusChanged = { focused ->
                                if (focused) onVideoFocused(item.toTopPreview()) else onFocusCleared()
                            },
                            onClick = { onPgcClick(item.seasonId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowTypeChip(
    text: String,
    active: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .duoFocusRing(RoundedCornerShape(8.dp), focused = focused)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) DuoPalette.Pink.copy(alpha = 0.18f) else DuoPalette.Surface)
            .duoClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (active) DuoPalette.Pink else DuoPalette.Secondary,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun FollowCard(
    item: PgcFollowItem,
    firstFocus: FocusRequester?,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val progress = item.progress.orEmpty().takeUnless { it == "-" }.orEmpty()
    val newEp = item.newEp?.indexShow.orEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.hasFocus)
            }
            .duoClickable(onClick = onClick)
            .padding(vertical = 7.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .duoFocusRing(RoundedCornerShape(8.dp), focused = focused)
                .padding(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DuoPalette.SurfaceHigh),
            ) {
                AsyncImage(
                    model = fixPic(item.cover.orEmpty()),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                item.badge?.takeIf { it.isNotBlank() }?.let { badge ->
                    Text(
                        text = badge,
                        color = Color.White,
                        fontSize = 8.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(DuoPalette.Pink.copy(alpha = 0.9f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                if (progress.isNotEmpty()) {
                    Text(
                        text = progress,
                        color = Color.White,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(DuoPalette.Scrim)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.title.orEmpty(),
                color = DuoPalette.Text,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = newEp.ifBlank {
                item.seasonTypeName.orEmpty()
            }
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = meta,
                    color = DuoPalette.Secondary,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun PgcFollowItem.toTopPreview(): TopPreviewUi = TopPreviewUi(
    bvid = "",
    title = title.orEmpty(),
    coverUrl = fixPic(cover.orEmpty()),
    ownerName = "哔哩哔哩${seasonTypeName.orEmpty().ifBlank { "番剧 / 影视" }}",
    ownerLabel = "番剧 / 影视",
    description = listOfNotNull(
        rating?.score?.takeIf { it > 0f }?.let { "$it 分" },
        newEp?.indexShow?.takeIf { it.isNotBlank() },
        progress?.takeIf { it.isNotBlank() && it != "-" },
    ).joinToString(" · "),
    category = seasonTypeName.orEmpty(),
    coverFit = true,
    eyebrow = "● 我的追番 · 即将播放",
)
