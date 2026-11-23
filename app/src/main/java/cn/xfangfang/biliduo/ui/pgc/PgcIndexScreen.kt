package cn.xfangfang.biliduo.ui.pgc

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import cn.xfangfang.biliduo.data.local.GamepadStyleStore
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import cn.xfangfang.biliduo.data.model.PgcIndexFilter
import cn.xfangfang.biliduo.data.model.PgcIndexItem
import cn.xfangfang.biliduo.data.model.PgcIndexOrder
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.common.DuoEmptyState
import cn.xfangfang.biliduo.ui.common.DuoIconAction
import cn.xfangfang.biliduo.ui.common.DuoPageHeader
import cn.xfangfang.biliduo.ui.common.DuoPalette
import cn.xfangfang.biliduo.ui.duo.GridFocusNavigator
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.ui.duo.duoGridVertical
import cn.xfangfang.biliduo.ui.duo.duoTabCycling
import cn.xfangfang.biliduo.ui.duo.rememberGridFocusNavigator
import cn.xfangfang.biliduo.ui.duo.requestPageFocus
import cn.xfangfang.biliduo.ui.duo.restoreDuoFocus
import cn.xfangfang.biliduo.ui.duo.restoreDuoFocusNow
import cn.xfangfang.biliduo.ui.top.TopAmbientUi
import cn.xfangfang.biliduo.ui.top.TopGlyph
import cn.xfangfang.biliduo.ui.top.TopPreviewUi
import cn.xfangfang.biliduo.util.fixPic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val FilterPriority = listOf(
    "area",
    "style_id",
    "release_date",
    "year",
    "is_finish",
    "producer_id",
    "season_status",
    "season_version",
    "copyright",
    "spoken_language_type",
)

@Composable
fun PgcIndexScreen(
    initialType: PgcIndexType,
    onBack: () -> Unit,
    onPgcClick: (Long) -> Unit,
    onVideoFocused: (TopPreviewUi) -> Unit = {},
    onFocusCleared: () -> Unit = {},
    onTopAmbient: (TopAmbientUi) -> Unit = {},
    session: DuoSessionViewModel,
    viewModel: PgcIndexViewModel = viewModel(),
) {
    val ui by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val firstItemFocus = remember { FocusRequester() }
    val navigator = rememberGridFocusNavigator(4, gridState, firstItemFocus)
    val retryFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val typeFocuses = remember {
        List(PgcIndexType.entries.size) { FocusRequester() }
    }
    val visibleFilters = remember(ui.filters) { pickVisibleFilters(ui.filters) }
    val filterFocuses = remember(visibleFilters) {
        List(visibleFilters.size) { FocusRequester() }
    }
    val orderFocus = remember { FocusRequester() }
    var pendingContentFocus by remember { mutableStateOf(false) }
    var initialFocusDone by remember { mutableStateOf(false) }
    var pickerField by remember { mutableStateOf<String?>(null) }
    var pickerReturnFocus by remember { mutableStateOf<FocusRequester?>(null) }

    LaunchedEffect(initialType) { viewModel.start(initialType) }

    LaunchedEffect(ui.items.size, ui.isLoading, ui.error) {
        if (initialFocusDone || ui.items.isEmpty() || ui.isLoading) return@LaunchedEffect
        initialFocusDone = true
        delay(80)
        runCatching { firstItemFocus.requestPageFocus() }
    }

    LaunchedEffect(ui.selectedType) {
        onTopAmbient(
            TopAmbientUi(
                eyebrow = "PGC INDEX / 番剧影视索引",
                title = "${ui.selectedType.label}索引",
                subtitle = "按地区、风格、年份筛选，A 进入剧集。",
                glyph = if (ui.selectedType == PgcIndexType.ANIME) {
                    TopGlyph.BANGUMI
                } else {
                    TopGlyph.MOVIE
                },
                accent = if (ui.selectedType == PgcIndexType.ANIME) 0xFFFB7299 else 0xFF00A1D6,
            )
        )
    }

    LaunchedEffect(ui.error, ui.items.size, ui.isLoading) {
        if (ui.error != null && ui.items.isEmpty() && !ui.isLoading) {
            delay(80)
            runCatching { retryFocus.requestPageFocus() }
        }
        if (ui.items.isEmpty()) onFocusCleared()
    }

    // L1/R1 switches the index kind and, like other tabs, lands on the first
    // card once the new page has loaded.
    LaunchedEffect(
        pendingContentFocus,
        ui.selectedType,
        ui.items.size,
        ui.isLoading,
        ui.error,
    ) {
        if (!pendingContentFocus) return@LaunchedEffect
        when {
            ui.items.isNotEmpty() && !ui.isLoading -> {
                delay(80)
                runCatching { firstItemFocus.requestPageFocus() }
                pendingContentFocus = false
            }
            ui.error != null && !ui.isLoading -> {
                delay(80)
                runCatching { retryFocus.requestPageFocus() }
                pendingContentFocus = false
            }
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

    fun cycleType(delta: Int) {
        val entries = PgcIndexType.entries
        val next = entries[(ui.selectedType.ordinal + delta + entries.size) % entries.size]
        pendingContentFocus = true
        initialFocusDone = true
        viewModel.selectType(next)
    }

    fun focusGrid(): Boolean {
        if (ui.items.isEmpty()) return false
        runCatching { firstItemFocus.requestPageFocus() }
        return true
    }

    fun focusFilters(): Boolean {
        val target = filterFocuses.firstOrNull() ?: return false
        runCatching { target.requestPageFocus() }
        return true
    }

    val picker = pickerField?.let { field ->
        when (field) {
            ORDER_FIELD -> PickerData(
                title = "排序",
                options = ui.orders.map { it.field to it.name },
                selected = ui.selectedOrder,
            )
            else -> visibleFilters.firstOrNull { it.field == field }?.let { filter ->
                PickerData(
                    title = filter.name,
                    options = filter.values.orEmpty().map { it.keyword to it.name },
                    selected = ui.selectedFilters[field].orEmpty(),
                )
            }
        }
    }

    fun closePicker(restoreFocus: Boolean = true) {
        val target = pickerReturnFocus
        pickerField = null
        if (restoreFocus && target != null) {
            if (!restoreDuoFocusNow(target)) {
                scope.launch { restoreDuoFocus(target) }
            }
        }
    }

    DisposableEffect(pickerField) {
        if (pickerField != null) {
            session.setBackInterceptor {
                closePicker()
                true
            }
        } else {
            // The picker just closed. Drop its interceptor so B can pop the
            // index route again instead of being swallowed forever.
            session.setBackInterceptor(null)
        }
        onDispose { session.setBackInterceptor(null) }
    }

    Box(Modifier.fillMaxSize().background(DuoPalette.Bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .duoTabCycling(
                    onPreviousTab = { if (picker == null) cycleType(-1) },
                    onNextTab = { if (picker == null) cycleType(1) },
                ),
        ) {
            DuoPageHeader(
                title = "番剧 / 影视索引",
                onBack = onBack,
                backModifier = Modifier.focusRequester(backFocus),
                actions = {
                    DuoIconAction(
                        icon = Icons.Default.Refresh,
                        contentDescription = "刷新索引",
                        onClick = viewModel::refresh,
                    )
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PgcIndexType.entries.forEach { type ->
                    IndexChip(
                        text = type.label,
                        selected = type == ui.selectedType,
                        focusRequester = typeFocuses[type.ordinal],
                        modifier = Modifier.onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionUp -> {
                                    runCatching { backFocus.requestPageFocus() }
                                    true
                                }
                                Key.DirectionDown -> {
                                    if (!focusFilters()) focusGrid()
                                    true
                                }
                                else -> false
                            }
                        },
                        onClick = {
                            initialFocusDone = true
                            viewModel.selectType(type)
                        },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                visibleFilters.forEachIndexed { index, filter ->
                    val keyword = ui.selectedFilters[filter.field].orEmpty()
                    val valueName = filter.values.orEmpty()
                        .firstOrNull { it.keyword == keyword }?.name.orEmpty()
                    val label = if (keyword == "-1" || keyword.isBlank()) {
                        filter.name
                    } else {
                        "${filter.name}·$valueName"
                    }
                    val focus = filterFocuses[index]
                    IndexChip(
                        text = label,
                        selected = keyword != "-1" && keyword.isNotBlank(),
                        focusRequester = focus,
                        modifier = Modifier
                            .focusProperties {
                                up = typeFocuses[ui.selectedType.ordinal]
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) {
                                    return@onPreviewKeyEvent false
                                }
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        focusGrid()
                                        true
                                    }
                                    else -> false
                                }
                            },
                        onClick = {
                            initialFocusDone = true
                            pickerReturnFocus = focus
                            pickerField = filter.field
                        },
                    )
                }
                val orderName = ui.orders
                    .firstOrNull { it.field == ui.selectedOrder }?.name.orEmpty()
                IndexChip(
                    text = if (ui.selectedOrder == "0" || orderName.isBlank()) {
                        "排序"
                    } else {
                        "排序·$orderName"
                    },
                    selected = ui.selectedOrder != "0",
                    focusRequester = orderFocus,
                    modifier = Modifier
                        .focusProperties { up = typeFocuses[ui.selectedType.ordinal] }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) {
                                return@onPreviewKeyEvent false
                            }
                            when (event.key) {
                                Key.DirectionDown -> {
                                    focusGrid()
                                    true
                                }
                                else -> false
                            }
                        },
                    onClick = {
                        initialFocusDone = true
                        pickerReturnFocus = orderFocus
                        pickerField = ORDER_FIELD
                    },
                )
                if (ui.isLoading && ui.items.isNotEmpty()) {
                    Spacer(Modifier.width(2.dp))
                    CircularProgressIndicator(
                        color = DuoPalette.Pink,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            if (ui.error != null && ui.items.isNotEmpty()) {
                Text(
                    text = ui.error.orEmpty(),
                    color = DuoPalette.Error,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
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
                        upFocus = filterFocuses.firstOrNull() ?: backFocus,
                    )
                    ui.items.isEmpty() -> DuoEmptyState(
                        title = "没有找到内容",
                        hint = "换个筛选条件试试",
                        actionText = "重试",
                        onAction = viewModel::refresh,
                        focusRequester = retryFocus,
                        upFocus = filterFocuses.firstOrNull() ?: backFocus,
                    )
                    else -> LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 10.dp,
                            end = 10.dp,
                            bottom = 12.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        itemsIndexed(
                            ui.items,
                            key = { _, item -> item.seasonId },
                        ) { index, item ->
                            val modifier = Modifier
                                .duoGridVertical(index, ui.items.size, navigator)
                                .then(
                                    if (index < 4) {
                                        Modifier.focusProperties {
                                            up = filterFocuses.firstOrNull() ?: backFocus
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                            IndexCard(
                                item = item,
                                type = ui.selectedType,
                                modifier = modifier,
                                focusRequester = if (index == 0) {
                                    firstItemFocus
                                } else {
                                    navigator.requester(index)
                                },
                                onFocusChanged = { focused ->
                                    if (focused) {
                                        onVideoFocused(item.toTopPreview(ui.selectedType))
                                    } else {
                                        onFocusCleared()
                                    }
                                },
                                onClick = { onPgcClick(item.seasonId) },
                            )
                        }
                    }
                }
            }
        }

        if (picker != null) {
            IndexPicker(
                picker = picker,
                onDismiss = { closePicker() },
                onSelect = { keyword ->
                    val field = pickerField
                    closePicker()
                    if (field == ORDER_FIELD) {
                        viewModel.selectOrder(keyword)
                    } else if (field != null) {
                        viewModel.selectFilter(field, keyword)
                    }
                },
            )
        }
    }
}

private const val ORDER_FIELD = "__order__"

private data class PickerData(
    val title: String,
    val options: List<Pair<String, String>>,
    val selected: String,
)

private fun pickVisibleFilters(filters: List<PgcIndexFilter>): List<PgcIndexFilter> =
    filters
        .filter { it.field in FilterPriority }
        .sortedBy { FilterPriority.indexOf(it.field).let { i -> if (i < 0) 99 else i } }
        .take(4)

private fun PgcIndexItem.toTopPreview(type: PgcIndexType): TopPreviewUi = TopPreviewUi(
    bvid = "",
    title = title.orEmpty(),
    coverUrl = fixPic(cover.orEmpty()),
    ownerName = "哔哩哔哩${seasonTypeName.orEmpty().ifBlank { "番剧 / 影视" }}",
    ownerLabel = "番剧 / 影视",
    description = listOfNotNull(
        rating?.score?.takeIf { it > 0f }?.let { "$it 分" },
        order?.takeIf { it.isNotBlank() },
        indexShow?.takeIf { it.isNotBlank() },
    ).joinToString(" · "),
    category = seasonTypeName.orEmpty().ifBlank { type.label },
    coverFit = true,
    eyebrow = "● ${type.label}索引 · 即将播放",
)

@Composable
private fun IndexChip(
    text: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .duoFocusRing(RoundedCornerShape(8.dp), focused = focused)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DuoPalette.Pink.copy(alpha = 0.18f) else DuoPalette.Surface)
            .duoClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) DuoPalette.Pink else DuoPalette.Secondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun IndexCard(
    item: PgcIndexItem,
    type: PgcIndexType,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
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
                .duoFocusRing(RoundedCornerShape(6.dp), focused = focused)
                .padding(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(6.dp))
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
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(DuoPalette.Pink.copy(alpha = 0.9f))
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
            val meta = listOfNotNull(
                item.indexShow?.takeIf { it.isNotBlank() },
                item.order?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
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

@Composable
private fun IndexPicker(
    picker: PickerData,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val options = picker.options
    val selectedIndex = options.indexOfFirst { it.first == picker.selected }
        .coerceAtLeast(0)
    var focusIndex by remember(picker.title) { mutableIntStateOf(selectedIndex) }
    val focusers = remember(options) { List(options.size) { FocusRequester() } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val gamepadStyle by GamepadStyleStore.style.collectAsState()

    LaunchedEffect(picker.title) {
        if (options.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(focusIndex)
        delay(80)
        runCatching { focusers[focusIndex].requestFocus() }
    }

    fun move(delta: Int) {
        if (options.isEmpty()) return
        focusIndex = (focusIndex + delta).coerceIn(0, options.lastIndex)
        // Move the ring immediately with the key press. Waiting for
        // animateScrollToItem made the indicator visibly lag behind the list.
        runCatching { focusers[focusIndex].requestFocus() }
        scope.launch {
            listState.animateScrollToItem(focusIndex)
            // If the target row was not composed yet, the first request failed;
            // retry now that the scroll has brought it into the viewport.
            delay(30)
            runCatching { focusers[focusIndex].requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        move(-1)
                        true
                    }
                    Key.DirectionDown -> {
                        move(1)
                        true
                    }
                    // Single-column picker: keep horizontal/trigger keys inside.
                    Key.DirectionLeft, Key.DirectionRight,
                    Key.ButtonL1, Key.ButtonR1, Key.ButtonL2, Key.ButtonR2 -> true
                    else -> false
                }
            }
            .duoClickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.82f)
                .heightIn(max = 320.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DuoPalette.Surface)
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .focusGroup(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = picker.title,
                    color = DuoPalette.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${gamepadStyle.backLabel} 关闭",
                    color = DuoPalette.Tertiary,
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (options.isEmpty()) {
                Text("暂无可选项", color = DuoPalette.Secondary, fontSize = 12.sp)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    // duoFocusRing draws outside the row bounds. Inset the
                    // scroll content so the first/last row ring is not clipped
                    // by the viewport or the card's rounded clip.
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
                ) {
                    itemsIndexed(
                        options,
                        key = { _, option -> option.first },
                    ) { index, option ->
                        val selected = option.first == picker.selected
                        var focused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusers[index])
                                .onFocusChanged { focused = it.isFocused }
                                .duoFocusRing(RoundedCornerShape(6.dp), focused = focused)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (selected) {
                                        DuoPalette.Pink.copy(alpha = 0.14f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .duoClickable { onSelect(option.first) }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option.second,
                                color = if (selected) DuoPalette.Pink else DuoPalette.Text,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = DuoPalette.Pink,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
