package cn.xfangfang.biliduo.ui.following

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import cn.xfangfang.biliduo.data.model.FollowingItem
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
import cn.xfangfang.biliduo.ui.top.TopPersonUi
import cn.xfangfang.biliduo.util.fixPic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FollowingListScreen(
    onBack: () -> Unit = {},
    onUploaderClick: (mid: Long) -> Unit = {},
    onLogin: () -> Unit = {},
    onTopPerson: (TopPersonUi) -> Unit = {},
    onTopAmbient: (TopAmbientUi) -> Unit = {},
    session: DuoSessionViewModel? = null,
    viewModel: FollowingListViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val searchFocus = remember { FocusRequester() }
    val searchCancelFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val loginFocus = remember { FocusRequester() }
    var searchMode by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var confirmUnfollow by remember { mutableStateOf<FollowingItem?>(null) }
    var actionFocus by remember { mutableStateOf<FocusRequester?>(null) }

    fun exitSearch() {
        searchMode = false
        searchText = ""
        if (uiState.searchQuery != null) viewModel.clearSearch()
    }

    val loggedIn = cn.xfangfang.biliduo.data.api.BilibiliApi.parseSelfMid() != null

    LaunchedEffect(
        loggedIn,
        uiState.items.size,
        uiState.isLoading,
        uiState.error,
        uiState.searchQuery,
    ) {
        when {
            !loggedIn -> onTopAmbient(
                TopAmbientUi(
                    eyebrow = "FOLLOWING / 关注",
                    title = "登录后，\n看看关注的人。",
                    subtitle = "关注列表、UP 主头像与签名会显示在这里；下屏负责搜索和取关。",
                    glyph = TopGlyph.FOLLOWING,
                )
            )
            uiState.items.isEmpty() && !uiState.isLoading -> onTopAmbient(
                TopAmbientUi(
                    eyebrow = "FOLLOWING / 关注",
                    title = when {
                        uiState.error != null -> "关注列表加载失败。"
                        uiState.searchQuery != null -> "没有找到相关 UP 主。"
                        else -> "还没有关注任何 UP 主。"
                    },
                    subtitle = when {
                        uiState.error != null -> "请在下屏重试；网络恢复后会重新同步关注列表。"
                        uiState.searchQuery != null -> "换一个关键词，或退出搜索查看全部关注。"
                        else -> "在视频简介或 UP 主页点关注后，会出现在下屏列表和上屏展示。"
                    },
                    glyph = TopGlyph.FOLLOWING,
                )
            )
        }
    }

    LaunchedEffect(Unit) { if (loggedIn) viewModel.load() }
    LaunchedEffect(loggedIn) {
        if (!loggedIn) {
            delay(80)
            runCatching { loginFocus.requestPageFocus() }
        }
    }

    val nearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearBottom, uiState.loadingMore, uiState.total) {
        if (nearBottom && !uiState.loadingMore && uiState.items.size < uiState.total) {
            delay(300)
            viewModel.loadMore()
        }
    }

    LaunchedEffect(uiState.isLoading, searchMode) {
        if (!searchMode && uiState.items.isNotEmpty() && !uiState.isLoading) {
            delay(80)
            runCatching { firstItemFocus.requestPageFocus() }
        }
    }
    LaunchedEffect(searchMode) {
        if (searchMode) {
            searchText = uiState.searchQuery.orEmpty()
            // C: keep the ring on 取消 instead of focusing the input.
            delay(80)
            runCatching { searchCancelFocus.requestPageFocus() }
        } else {
            keyboard?.hide()
        }
    }
    LaunchedEffect(uiState.actionError) {
        uiState.actionError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearActionError()
        }
    }

    fun closeUnfollowConfirm() {
        val focus = actionFocus
        confirmUnfollow = null
        scope.launch {
            delay(120)
            runCatching { focus?.requestPageFocus() }
        }
    }

    LaunchedEffect(searchMode, confirmUnfollow, session) {
        when {
            confirmUnfollow != null -> session?.setBackInterceptor {
                closeUnfollowConfirm()
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
    BackHandler(searchMode) { exitSearch() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoPalette.Bg),
    ) {
        DuoPageHeader(
            title = "我的关注",
            onBack = { if (searchMode) exitSearch() else onBack() },
            actions = {
                if (loggedIn && !searchMode) {
                    DuoIconAction(
                        icon = Icons.Default.Search,
                        contentDescription = "搜索关注",
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
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = "搜索 UP 主",
                    focusRequester = searchFocus,
                    modifier = Modifier.weight(1f),
                    onSubmit = {
                        keyboard?.hide()
                        if (searchText.isBlank()) viewModel.clearSearch() else viewModel.search(searchText.trim())
                    },
                    onClear = {
                        searchText = ""
                        viewModel.clearSearch()
                    },
                )
                Spacer(Modifier.width(8.dp))
                DuoPillButton(text = "取消", onClick = { exitSearch() }, focusRequester = searchCancelFocus)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !loggedIn -> {
                    DuoEmptyState(
                        title = "登录后查看关注",
                        hint = "登录后可同步关注列表与 UP 主动态",
                        actionText = "去登录",
                        onAction = onLogin,
                        focusRequester = loginFocus,
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
                        title = if (uiState.searchQuery != null) "未找到相关 UP 主" else "还没有关注任何 UP 主",
                        hint = if (uiState.searchQuery == null) "在视频简介或 UP 主页点关注后会出现在这里" else null,
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(uiState.items, key = { it.mid }) { item ->
                            FollowingRow(
                                item = item,
                                firstFocus = if (item.mid == uiState.items.firstOrNull()?.mid) firstItemFocus else null,
                                onClick = { onUploaderClick(item.mid) },
                                onUnfollow = { focus ->
                                    actionFocus = focus
                                    confirmUnfollow = item
                                },
                                onFocused = { focused ->
                                    onTopPerson(
                                        TopPersonUi(
                                            eyebrow = "FOLLOWING / 关注",
                                            mid = focused.mid,
                                            name = focused.uname,
                                            subtitle = "UID ${focused.mid}",
                                            description = focused.sign.ifBlank { "这个 UP 主还没有填写签名。" },
                                            badge = "已关注",
                                            avatarUrl = focused.face,
                                            glyph = TopGlyph.FOLLOWING,
                                        )
                                    )
                                },
                            )
                        }
                        item(key = "footer") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    uiState.loadingMore -> CircularProgressIndicator(
                                        color = DuoPalette.Pink,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    uiState.items.size < uiState.total -> Text(
                                        text = "继续向下滚动加载",
                                        color = DuoPalette.Tertiary,
                                        fontSize = 10.sp,
                                    )
                                    else -> Text(
                                        text = "共 ${uiState.total} 位关注",
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

    confirmUnfollow?.let { item ->
        DuoConfirmOverlay(
            title = "取消关注",
            message = "确定不再关注「${item.uname}」吗？",
            confirmText = "取消关注",
            danger = true,
            onConfirm = {
                confirmUnfollow = null
                actionFocus = null
                viewModel.unfollow(item.mid)
            },
            onCancel = {
                closeUnfollowConfirm()
            },
            session = null,
        )
    }
}

@Composable
private fun FollowingRow(
    item: FollowingItem,
    firstFocus: FocusRequester?,
    onClick: () -> Unit,
    onUnfollow: (FocusRequester) -> Unit,
    onFocused: (FollowingItem) -> Unit = {},
) {
    var contentFocused by remember { mutableStateOf(false) }
    var actionFocused by remember { mutableStateOf(false) }
    val actionFocus = remember { FocusRequester() }
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
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
                    if (it.isFocused) onFocused(item)
                }
                .duoClickable(onClick = onClick)
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = fixPic(item.face),
                contentDescription = item.uname,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(DuoPalette.SurfaceHigh),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.uname,
                    color = DuoPalette.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.sign.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.sign,
                        color = DuoPalette.Secondary,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .focusRequester(actionFocus)
                .height(28.dp)
                .duoFocusRing(RoundedCornerShape(7.dp))
                .clip(RoundedCornerShape(7.dp))
                .border(1.dp, DuoPalette.Outline, RoundedCornerShape(7.dp))
                .onFocusChanged { actionFocused = it.isFocused }
                .duoClickable { onUnfollow(actionFocus) }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "已关注",
                color = if (actionFocused) DuoPalette.Pink else DuoPalette.Secondary,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
    }
}