package cn.xfangfang.biliduo.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import cn.xfangfang.biliduo.data.model.SearchUserItem
import cn.xfangfang.biliduo.data.model.SearchVideoItem
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoTextFieldNavigation
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.ui.top.TopAmbientUi
import cn.xfangfang.biliduo.ui.top.TopGlyph
import cn.xfangfang.biliduo.ui.top.TopPersonStat
import cn.xfangfang.biliduo.ui.top.TopPersonUi
import cn.xfangfang.biliduo.ui.top.TopPreviewUi
import cn.xfangfang.biliduo.util.fixPic
import cn.xfangfang.biliduo.util.formatCount
import kotlinx.coroutines.delay

private val SearchBg = Color(0xFF0F0F0F)
private val SearchFieldBg = Color(0xFF1B1B1D)
private val SearchCardBg = Color(0xFF17171A)
private val SearchText = Color(0xFFF2F2F4)
private val SearchSecondary = Color(0xFFA6A6AE)
private val SearchPink = Color(0xFFFB7299)

@Composable
fun SearchScreen(
    onVideoClick: (String) -> Unit,
    onUserClick: (Long) -> Unit = {},
    onBack: () -> Unit = {},
    onVideoFocused: (TopPreviewUi) -> Unit = {},
    onUserFocused: (TopPersonUi) -> Unit = {},
    onFocusCleared: () -> Unit = {},
    onTopAmbient: (TopAmbientUi) -> Unit = {},
    viewModel: SearchViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val searchFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var searchFocused by remember { mutableStateOf(false) }
    val editingQuery = state.query.isNotBlank() && state.query.trim() != state.submittedQuery
    val showSuggestions = editingQuery && state.suggestions.isNotEmpty()

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            total > 0 && lastVisible >= total - 3 && state.hasMore &&
                !state.isLoadingMore && state.moreError == null
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    LaunchedEffect(
        state.hasSearched,
        state.searchType,
        state.query,
        state.results.size,
        state.users.size,
        state.isLoading,
        state.error,
        searchFocused,
        editingQuery,
        state.suggestions.size,
    ) {
        val hasItems = if (state.searchType == SearchType.USER) {
            state.users.isNotEmpty()
        } else {
            state.results.isNotEmpty()
        }
        when {
            editingQuery -> onTopAmbient(
                TopAmbientUi(
                    eyebrow = "SEARCH / 搜索",
                    title = "正在输入「${state.query.trim()}」。",
                    subtitle = "下屏选择搜索建议，A 直接搜索；上屏只展示结果预览。",
                    glyph = TopGlyph.SEARCH,
                )
            )
            !state.hasSearched || !hasItems || state.isLoading || searchFocused ->
                onTopAmbient(searchAmbient(state, searchFocused))
        }
    }

    LaunchedEffect(
        state.hasSearched,
        state.searchType,
        state.results.firstOrNull()?.bvid,
        state.users.firstOrNull()?.mid,
    ) {
        delay(120L)
        val hasItems = if (state.searchType == SearchType.USER) {
            state.users.isNotEmpty()
        } else {
            state.results.isNotEmpty()
        }
        val target = if (state.hasSearched && hasItems) {
            firstResultFocus
        } else {
            searchFocus
        }
        runCatching { target.requestFocus() }
    }

    val submitSearch: () -> Unit = {
        viewModel.search()
        focusManager.clearFocus()
        keyboard?.hide()
        Unit
    }

    val sortKeyModifier = if (!searchFocused && state.hasSearched) {
        Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val types = SearchType.values()
            val typeIndex = types.indexOf(state.searchType)
            when (event.key) {
                Key.ButtonR1 -> {
                    viewModel.setSearchType(types[(typeIndex + 1) % types.size])
                    true
                }
                Key.ButtonL1 -> {
                    viewModel.setSearchType(types[(typeIndex - 1 + types.size) % types.size])
                    true
                }
                Key.Y, Key.ButtonR2 -> {
                    if (state.searchType == SearchType.VIDEO) {
                        viewModel.nextSort()
                        true
                    } else {
                        false
                    }
                }
                Key.ButtonL2 -> {
                    if (state.searchType == SearchType.VIDEO) {
                        viewModel.prevSort()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SearchBg)
            .then(sortKeyModifier)
    ) {
        SearchHeader(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            onClear = {
                viewModel.clearResults()
                runCatching { searchFocus.requestFocus() }
                Unit
            },
            onSubmit = submitSearch,
            onBack = onBack,
            searchFocus = searchFocus,
            onFocusChanged = { searchFocused = it },
            onRequestIme = {
                keyboard?.show()
                Unit
            },
        )

        if (showSuggestions) {
            SearchSuggestions(
                suggestions = state.suggestions,
                onPick = { keyword ->
                    focusManager.clearFocus()
                    keyboard?.hide()
                    viewModel.searchSuggestion(keyword)
                },
            )
        } else if (!state.hasSearched) {
            SearchInitialContent(
                history = state.searchHistory,
                hotSearches = state.hotSearches,
                onHistoryClick = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    viewModel.searchFromHistory(it)
                },
                onHistoryDelete = viewModel::removeFromHistory,
                onClearHistory = viewModel::clearHistory,
                onHotClick = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    viewModel.searchHot(it)
                },
            )
        } else {
            SearchTypeRow(
                selected = state.searchType,
                onSelect = viewModel::setSearchType,
            )
            val hasItems = if (state.searchType == SearchType.USER) {
                state.users.isNotEmpty()
            } else {
                state.results.isNotEmpty()
            }
            when {
                state.isLoading && !hasItems -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SearchPink)
                    }
                }

                state.error != null && !hasItems -> {
                    SearchMessage(
                        text = "加载失败：${state.error}",
                        action = "重试",
                        onAction = submitSearch,
                    )
                }

                !hasItems -> {
                    SearchMessage(
                        text = if (state.searchType == SearchType.USER) {
                            "没有找到相关用户"
                        } else {
                            "没有找到相关视频"
                        }
                    )
                }

                state.searchType == SearchType.USER -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(
                            items = state.users,
                            key = { _, item -> item.mid },
                        ) { index, item ->
                            SearchUserRow(
                                item = item,
                                onClick = { onUserClick(item.mid) },
                                focusRequester = if (index == 0) firstResultFocus else null,
                                onFocusChanged = { focused ->
                                    if (focused) {
                                        onUserFocused(item.toTopPerson())
                                    } else {
                                        onFocusCleared()
                                    }
                                },
                            )
                        }
                        item {
                            SearchListFooter(
                                isLoadingMore = state.isLoadingMore,
                                hasMore = state.hasMore,
                                moreError = state.moreError,
                                onRetry = viewModel::retryLoadMore,
                            )
                        }
                    }
                }

                else -> {
                    SortRow(
                        selected = state.sortBy,
                        onSelect = viewModel::setSortBy,
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(
                            items = state.results,
                            key = { _, item -> item.bvid },
                        ) { index, item ->
                            SearchResultRow(
                                item = item,
                                onClick = { onVideoClick(item.bvid) },
                                focusRequester = if (index == 0) firstResultFocus else null,
                                onFocusChanged = { focused ->
                                    if (focused) {
                                        onVideoFocused(item.toTopPreview())
                                    } else {
                                        onFocusCleared()
                                    }
                                },
                            )
                        }
                        item {
                            SearchListFooter(
                                isLoadingMore = state.isLoadingMore,
                                hasMore = state.hasMore,
                                moreError = state.moreError,
                                onRetry = viewModel::retryLoadMore,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    searchFocus: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onRequestIme: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .duoFocusRing(CircleShape)
                .clip(CircleShape)
                .duoClickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("‹", color = SearchText, fontSize = 28.sp, lineHeight = 28.sp)
        }
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .focusRequester(searchFocus)
                .duoTextFieldNavigation()
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChanged(it.isFocused)
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionCenter) {
                        onRequestIme()
                        true
                    } else {
                        false
                    }
                }
                .clip(RoundedCornerShape(22.dp))
                .background(SearchFieldBg)
                .border(
                    width = 1.dp,
                    color = if (focused) SearchPink else Color.Transparent,
                    shape = RoundedCornerShape(22.dp),
                ),
            textStyle = TextStyle(color = SearchText, fontSize = 14.sp),
            singleLine = true,
            cursorBrush = SolidColor(SearchPink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = "搜索视频、UP 主…",
                                color = SearchSecondary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                    if (query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .duoFocusRing(CircleShape)
                                .clip(CircleShape)
                                .duoClickable(onClick = onClear),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清除",
                                tint = SearchSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .duoFocusRing(CircleShape)
                            .clip(CircleShape)
                            .duoClickable(onClick = onSubmit),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = SearchPink,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun SearchSuggestions(
    suggestions: List<String>,
    onPick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
    ) {
        item(key = "suggest_header") {
            SectionHeader(title = "搜索建议")
        }
        items(suggestions, key = { "suggest_$it" }) { keyword ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .duoFocusRing(RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .duoClickable { onPick(keyword) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = SearchSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = keyword,
                    color = SearchText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchInitialContent(
    history: List<String>,
    hotSearches: List<String>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    onClearHistory: () -> Unit,
    onHotClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
    ) {
        item(key = "history_header") {
            SectionHeader(
                title = "搜索历史",
                action = "清空",
                actionEnabled = history.isNotEmpty(),
                onAction = onClearHistory,
            )
        }
        if (history.isEmpty()) {
            item(key = "history_empty") {
                Text(
                    text = "暂无搜索历史",
                    color = SearchSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 10.dp),
                )
            }
        } else {
            items(history, key = { "history_$it" }) { keyword ->
                HistoryRow(
                    keyword = keyword,
                    onClick = { onHistoryClick(keyword) },
                    onDelete = { onHistoryDelete(keyword) },
                )
            }
        }

        item(key = "hot_header") {
            Spacer(Modifier.height(10.dp))
            SectionHeader(title = "热门搜索")
        }
        item(key = "hot_chips") {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                hotSearches.take(6).forEach { keyword ->
                    HotChip(keyword = keyword, onClick = { onHotClick(keyword) })
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = SearchText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Box(
                modifier = Modifier
                    .duoFocusRing(RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .duoClickable(enabled = actionEnabled, onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text(
                    text = action,
                    color = if (actionEnabled) SearchSecondary else SearchSecondary.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    keyword: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .duoFocusRing(RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .duoClickable(onClick = onClick)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = keyword,
            color = SearchText,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .duoFocusRing(CircleShape)
                .clip(CircleShape)
                .duoClickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "删除",
                tint = SearchSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun HotChip(
    keyword: String,
    onClick: () -> Unit,
) {
    Text(
        text = keyword,
        color = SearchText,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 220.dp)
            .duoFocusRing(RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(SearchFieldBg)
            .duoClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
private fun SearchTypeRow(
    selected: SearchType,
    onSelect: (SearchType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchType.values().forEach { type ->
            val isSelected = type == selected
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .duoFocusRing(RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) SearchPink.copy(alpha = 0.16f) else SearchFieldBg
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) SearchPink.copy(alpha = 0.45f) else Color.Transparent,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .duoClickable { onSelect(type) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = type.label,
                    color = if (isSelected) SearchPink else SearchSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "L1/R1 切类型",
            color = SearchSecondary.copy(alpha = 0.7f),
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun SortRow(
    selected: SearchSortOption,
    onSelect: (SearchSortOption) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchSort.options.forEach { option ->
            val isSelected = option.value == selected.value
            Column(
                modifier = Modifier
                    .height(32.dp)
                    .duoFocusRing(RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .duoClickable { onSelect(option) }
                    .padding(horizontal = 7.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = option.label,
                    color = if (isSelected) SearchPink else SearchSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (isSelected) SearchPink else Color.Transparent),
                )
            }
            Spacer(Modifier.width(2.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "Y 切排序",
            color = SearchSecondary.copy(alpha = 0.7f),
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun SearchResultRow(
    item: SearchVideoItem,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .duoFocusRing(RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(SearchCardBg)
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .duoClickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(128.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SearchFieldBg),
        ) {
            AsyncImage(
                model = fixPic(item.pic),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (item.duration.isNotBlank()) {
                Text(
                    text = item.duration,
                    color = Color.White,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = item.title,
                color = SearchText,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = buildString {
                    item.author.takeIf { it.isNotBlank() }?.let { append(it) }
                    if (item.playCount > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${formatCount(item.playCount)}播放")
                    }
                    if (item.duration.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(item.duration)
                    }
                },
                color = SearchSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchUserRow(
    item: SearchUserItem,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .duoFocusRing(RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(SearchCardBg)
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .duoClickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(SearchFieldBg),
        ) {
            AsyncImage(
                model = fixPic(item.avatar),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.uname,
                    color = SearchText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.badgeLabel(),
                    color = SearchPink,
                    fontSize = 9.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SearchPink.copy(alpha = 0.12f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.sign.ifBlank { "这个 UP 主还没有填写签名。" },
                color = SearchSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = buildString {
                    append("UID ${item.mid}")
                    item.metaLine().takeIf { it.isNotEmpty() }?.let { append(" · ").append(it) }
                    if (item.level > 0) append(" · Lv${item.level}")
                },
                color = SearchSecondary.copy(alpha = 0.8f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchListFooter(
    isLoadingMore: Boolean,
    hasMore: Boolean,
    moreError: String?,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoadingMore -> {
                CircularProgressIndicator(
                    color = SearchPink,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            }

            moreError != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "加载失败：$moreError",
                        color = SearchSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 360.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .duoFocusRing(RoundedCornerShape(6.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .duoClickable(onClick = onRetry)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("重试", color = SearchPink, fontSize = 11.sp)
                    }
                }
            }

            !hasMore -> {
                Text(
                    text = "没有更多了",
                    color = SearchSecondary.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun SearchMessage(
    text: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = SearchSecondary,
            fontSize = 13.sp,
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .duoFocusRing(RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .duoClickable(onClick = onAction)
                    .background(SearchFieldBg)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(action, color = SearchPink, fontSize = 12.sp)
            }
        }
    }
}

private fun searchAmbient(state: SearchUiState, searchFocused: Boolean): TopAmbientUi {
    val isUser = state.searchType == SearchType.USER
    val itemCount = if (isUser) state.users.size else state.results.size
    val unit = if (isUser) "位用户" else "条结果"
    val title: String
    val subtitle: String
    when {
        !state.hasSearched -> {
            title = "搜索 B 站。"
            subtitle = "下屏输入关键词；历史、热门搜索和排序都在下屏，上屏展示结果预览。"
        }
        state.isLoading -> {
            title = "正在搜索「${state.query}」。"
            subtitle = "结果返回后，聚焦任意条目即可在上屏查看预览。"
        }
        state.error != null && itemCount == 0 -> {
            title = "搜索没有完成。"
            subtitle = "请在下屏重试，或换一个关键词。"
        }
        itemCount == 0 -> {
            title = "没有找到「${state.query}」。"
            subtitle = "换一个关键词，或回到下屏看看热门搜索。"
        }
        searchFocused -> {
            title = "搜索「${state.query}」。"
            subtitle = if (isUser) {
                "共 $itemCount 位用户；方向键回到结果列表，A 进入 UP 主空间。"
            } else {
                "共 $itemCount 条结果；方向键回到结果列表，A 直接播放。"
            }
        }
        else -> {
            title = "搜索「${state.query}」。"
            subtitle = "共 $itemCount $unit。"
        }
    }
    return TopAmbientUi(
        eyebrow = "SEARCH / 搜索",
        title = title,
        subtitle = subtitle,
        glyph = TopGlyph.SEARCH,
    )
}

private fun SearchVideoItem.toTopPreview(): TopPreviewUi = TopPreviewUi(
    bvid = bvid,
    title = title,
    coverUrl = pic,
    ownerName = author,
    ownerFace = authorAvatar.orEmpty(),
    playCount = playCount,
    danmakuCount = danmakuCount,
    pubdate = pubdate,
    duration = duration,
    description = description,
)

private fun SearchUserItem.toTopPerson(): TopPersonUi = TopPersonUi(
    eyebrow = "SEARCH / 用户",
    mid = mid,
    name = uname,
    subtitle = buildString {
        append("UID $mid")
        if (level > 0) append(" · Lv$level")
    },
    description = sign.ifBlank { "这个 UP 主还没有填写签名。" },
    badge = badgeLabel(),
    avatarUrl = avatar,
    glyph = TopGlyph.PERSON,
    stats = listOf(
        TopPersonStat(formatCount(fans), "粉丝"),
        TopPersonStat(formatCount(videos), "视频"),
    ),
)
