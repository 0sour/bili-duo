package cn.xfangfang.biliduo.ui.favorites

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cn.xfangfang.biliduo.data.model.FavFolder
import cn.xfangfang.biliduo.data.model.FavResourceItem
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
import cn.xfangfang.biliduo.ui.duo.duoTextFieldNavigation
import cn.xfangfang.biliduo.ui.top.TopAmbientUi
import cn.xfangfang.biliduo.ui.top.TopGlyph
import cn.xfangfang.biliduo.ui.top.TopPreviewUi
import cn.xfangfang.biliduo.util.fixPic
import cn.xfangfang.biliduo.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onVideoClick: (bvid: String) -> Unit,
    onLogin: () -> Unit = {},
    onTopAmbient: (TopAmbientUi) -> Unit = {},
    onVideoFocused: (TopPreviewUi) -> Unit = {},
    onFocusCleared: () -> Unit = {},
    session: DuoSessionViewModel? = null,
    viewModel: FavoritesViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val folderListState = rememberLazyListState()
    val resourceListState = rememberLazyListState()
    val searchFocus = remember { FocusRequester() }
    val firstFolderFocus = remember { FocusRequester() }
    val firstResourceFocus = remember { FocusRequester() }
    val loginFocus = remember { FocusRequester() }
    val formTitleFocus = remember { FocusRequester() }
    var searchMode by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var folderAction by remember { mutableStateOf<FavFolder?>(null) }
    var folderActionFocus by remember { mutableStateOf<FocusRequester?>(null) }
    var confirmDeleteFolder by remember { mutableStateOf<FavFolder?>(null) }
    var confirmRemove by remember { mutableStateOf<FavResourceItem?>(null) }
    var removeActionFocus by remember { mutableStateOf<FocusRequester?>(null) }
    var createTitle by remember { mutableStateOf("") }
    var createIntro by remember { mutableStateOf("") }
    var createPrivacy by remember { mutableIntStateOf(0) }
    var formError by remember { mutableStateOf<String?>(null) }

    val inFolder = uiState.selectedFolderId != null
    val loggedIn = cn.xfangfang.biliduo.data.api.BilibiliApi.parseSelfMid() != null
    val displayFolders: List<FavFolder> = remember(uiState.folders, searchText, inFolder) {
        if (inFolder) emptyList() else filterFolders(uiState.folders, searchText)
    }

    fun exitSearch() {
        searchMode = false
        searchText = ""
        if (inFolder) viewModel.clearFavSearch()
    }

    fun restoreFocus(focus: FocusRequester?) {
        scope.launch {
            delay(120)
            runCatching { focus?.requestFocus() }
        }
    }

    fun closeFolderAction() {
        val focus = folderActionFocus
        folderAction = null
        restoreFocus(focus)
    }

    fun closeDeleteFolder() {
        val focus = folderActionFocus
        confirmDeleteFolder = null
        restoreFocus(focus)
    }

    fun closeRemove() {
        val focus = removeActionFocus
        confirmRemove = null
        restoreFocus(focus)
    }

    fun closeCreateFolder() {
        keyboard?.hide()
        viewModel.dismissCreateFolder()
    }

    fun closeEditFolder() {
        keyboard?.hide()
        viewModel.dismissEditFolder()
    }

    LaunchedEffect(Unit) { if (loggedIn) viewModel.loadFolders() }
    LaunchedEffect(loggedIn) {
        if (!loggedIn) {
            delay(80)
            runCatching { loginFocus.requestFocus() }
        }
    }
    LaunchedEffect(
        loggedIn,
        inFolder,
        uiState.isLoadingFolders,
        uiState.isLoadingResources,
        uiState.folders.size,
        uiState.resources.size,
        searchText,
        displayFolders.size,
    ) {
        when {
            !loggedIn -> onTopAmbient(
                TopAmbientUi(
                    eyebrow = "FAVORITES / 收藏",
                    title = "登录后，\n收藏夹会亮起来。",
                    subtitle = "收藏夹和视频会显示在这里；下屏支持新建、编辑、删除和取消收藏。",
                    glyph = TopGlyph.FAVORITE,
                )
            )
            !inFolder && !uiState.isLoadingFolders && uiState.folders.isEmpty() -> onTopAmbient(
                TopAmbientUi(
                    eyebrow = "FAVORITES / 收藏",
                    title = "还没有收藏夹。",
                    subtitle = "在视频简介或播放控制台点收藏，下屏会创建默认收藏夹。",
                    glyph = TopGlyph.FAVORITE,
                )
            )
            !inFolder && searchText.isNotBlank() && displayFolders.isEmpty() -> onTopAmbient(
                TopAmbientUi(
                    eyebrow = "FAVORITES / 收藏",
                    title = "没有找到匹配的收藏夹。",
                    subtitle = "换一个关键词，或清空搜索查看全部收藏夹。",
                    glyph = TopGlyph.SEARCH,
                )
            )
            inFolder && !uiState.isLoadingResources && uiState.resources.isEmpty() -> onTopAmbient(
                TopAmbientUi(
                    eyebrow = "FAVORITES / 收藏",
                    title = "这个收藏夹还是空的。",
                    subtitle = "在视频简介或播放控制台点收藏，视频会出现在这里。",
                    glyph = TopGlyph.FAVORITE,
                )
            )
        }
    }
    LaunchedEffect(inFolder, uiState.folders.size, uiState.resources.size, uiState.isLoadingFolders, uiState.isLoadingResources, searchMode) {
        if (!searchMode) {
            delay(80)
            runCatching {
                if (inFolder) {
                    if (uiState.resources.isNotEmpty()) firstResourceFocus.requestFocus()
                } else {
                    if (uiState.folders.isNotEmpty()) firstFolderFocus.requestFocus()
                }
            }
        }
    }
    LaunchedEffect(uiState.showCreateFolder, uiState.showEditFolder, searchMode) {
        if (!uiState.showCreateFolder && !uiState.showEditFolder && !searchMode) {
            delay(100)
            runCatching {
                if (inFolder) firstResourceFocus.requestFocus() else firstFolderFocus.requestFocus()
            }
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
    LaunchedEffect(uiState.showCreateFolder) {
        if (uiState.showCreateFolder) {
            formError = null
            delay(80)
            runCatching { formTitleFocus.requestFocus() }
        }
    }
    LaunchedEffect(uiState.showEditFolder) {
        if (uiState.showEditFolder) {
            formError = null
            delay(120)
            runCatching { formTitleFocus.requestFocus() }
        }
    }
    LaunchedEffect(uiState.opError) {
        uiState.opError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearOpError()
        }
    }
    LaunchedEffect(uiState.actionError) {
        uiState.actionError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearActionError()
        }
    }
    LaunchedEffect(uiState.currentPage) {
        if (uiState.currentPage == 1) resourceListState.scrollToItem(0)
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = resourceListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = resourceListState.layoutInfo.totalItemsCount
            inFolder && total > 0 && last >= total - 3 && uiState.hasMore &&
                !uiState.isLoadingMore && !uiState.isLoadingResources
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    LaunchedEffect(
        searchMode,
        folderAction,
        confirmDeleteFolder,
        confirmRemove,
        uiState.showCreateFolder,
        uiState.showEditFolder,
        session,
    ) {
        val cancel: (() -> Unit)? = when {
            folderAction != null -> ({ closeFolderAction() })
            confirmDeleteFolder != null -> ({ closeDeleteFolder() })
            confirmRemove != null -> ({ closeRemove() })
            uiState.showCreateFolder -> ({ closeCreateFolder() })
            uiState.showEditFolder -> ({ closeEditFolder() })
            searchMode -> ({ exitSearch() })
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
    BackHandler(searchMode) { exitSearch() }

    fun back() {
        when {
            searchMode -> exitSearch()
            inFolder -> viewModel.backToFolders()
            else -> onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoPalette.Bg),
    ) {
        DuoPageHeader(
            title = if (inFolder) uiState.selectedFolderTitle else "我的收藏",
            onBack = { back() },
            actions = {
                if (loggedIn) {
                    if (!inFolder) {
                        DuoIconAction(
                            icon = Icons.Default.Add,
                            contentDescription = "新建收藏夹",
                            onClick = {
                                createTitle = ""
                                createIntro = ""
                                createPrivacy = 0
                                viewModel.openCreateFolder()
                            },
                        )
                    }
                    DuoIconAction(
                        icon = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = if (searchMode) DuoPalette.Pink else DuoPalette.Text,
                        onClick = { if (searchMode) exitSearch() else searchMode = true },
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
                    placeholder = if (inFolder) "搜索收藏夹视频" else "搜索收藏夹",
                    focusRequester = searchFocus,
                    modifier = Modifier.weight(1f),
                    onSubmit = {
                        keyboard?.hide()
                        if (inFolder) {
                            if (searchText.isBlank()) viewModel.clearFavSearch()
                            else viewModel.searchFav(searchText.trim())
                        }
                    },
                    onClear = {
                        searchText = ""
                        if (inFolder) viewModel.clearFavSearch()
                    },
                    onRequestIme = { keyboard?.show() },
                )
                Spacer(Modifier.width(8.dp))
                DuoPillButton(text = "取消", onClick = { exitSearch() })
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!loggedIn) {
                DuoEmptyState(
                    title = "登录后查看收藏",
                    hint = "登录后可同步收藏夹与收藏的视频",
                    actionText = "去登录",
                    onAction = onLogin,
                    focusRequester = loginFocus,
                )
            } else if (inFolder) {
                ResourceList(
                    state = uiState,
                    listState = resourceListState,
                    firstFocus = firstResourceFocus,
                    onVideoClick = onVideoClick,
                    onVideoFocused = onVideoFocused,
                    onFocusCleared = onFocusCleared,
                    onRemove = { item, focus ->
                        removeActionFocus = focus
                        confirmRemove = item
                    },
                )
            } else {
                FolderList(
                    folders = displayFolders,
                    allEmpty = uiState.folders.isEmpty(),
                    loading = uiState.isLoadingFolders,
                    error = uiState.error,
                    covers = uiState.folderCovers,
                    listState = folderListState,
                    firstFocus = firstFolderFocus,
                    onRetry = viewModel::loadFolders,
                    onSelect = { searchText = ""; viewModel.selectFolder(it) },
                    onAction = { folder, focus ->
                        folderActionFocus = focus
                        folderAction = folder
                    },
                    onFocusPreview = { folder, cover ->
                        onVideoFocused(
                            TopPreviewUi(
                                bvid = "",
                                title = folder.title,
                                coverUrl = (cover ?: folder.cover).orEmpty(),
                                category = "收藏夹",
                                eyebrow = "● 收藏夹",
                                description = "打开收藏夹后，可在下屏浏览其中的视频。",
                                metaLine = "${folder.mediaCount} 个内容 · ${privacyLabel(folder.attr)}",
                                ownerName = "",
                                actionHint = "打开收藏夹 · B 返回",
                            )
                        )
                    },
                )
            }
        }
    }

    if (uiState.showCreateFolder) {
        FolderFormOverlay(
            title = "新建收藏夹",
            titleValue = createTitle,
            introValue = createIntro,
            privacy = createPrivacy,
            saving = uiState.creatingFolder,
            error = formError,
            titleFocus = formTitleFocus,
            onTitle = { createTitle = it },
            onIntro = { createIntro = it },
            onPrivacy = { createPrivacy = it },
            onConfirm = {
                val validation = validateFolderTitle(createTitle)
                if (validation != null) {
                    formError = validation
                } else {
                    formError = null
                    viewModel.createFavFolder(createTitle.trim(), createIntro.trim(), createPrivacy)
                }
            },
            onCancel = {
                closeCreateFolder()
            },
        )
    }

    if (uiState.showEditFolder) {
        FolderFormOverlay(
            title = "编辑收藏夹",
            titleValue = uiState.editTitle,
            introValue = uiState.editIntro,
            privacy = uiState.editPrivacy,
            saving = uiState.editingFolder,
            loading = uiState.editDetailLoading,
            error = formError,
            titleFocus = formTitleFocus,
            onTitle = viewModel::updateEditTitle,
            onIntro = viewModel::updateEditIntro,
            onPrivacy = viewModel::updateEditPrivacy,
            onConfirm = {
                val validation = validateFolderTitle(uiState.editTitle)
                if (validation != null) formError = validation else {
                    formError = null
                    viewModel.confirmEditFolder()
                }
            },
            onCancel = {
                closeEditFolder()
            },
        )
    }

    folderAction?.let { folder ->
        FolderActionOverlay(
            folder = folder,
            onEdit = {
                folderAction = null
                viewModel.startEditFolder(folder)
            },
            onDelete = {
                folderAction = null
                confirmDeleteFolder = folder
            },
            onCancel = {
                closeFolderAction()
            },
        )
    }

    confirmDeleteFolder?.let { folder ->
        DuoConfirmOverlay(
            title = "删除收藏夹",
            message = "确定删除收藏夹「${folder.title}」吗？收藏夹内的视频不会被删除。",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                confirmDeleteFolder = null
                folderActionFocus = null
                viewModel.deleteFolder(folder.id)
            },
            onCancel = {
                closeDeleteFolder()
            },
            session = null,
        )
    }

    confirmRemove?.let { item ->
        DuoConfirmOverlay(
            title = "取消收藏",
            message = "将「${item.title}」从当前收藏夹移除？",
            confirmText = "取消收藏",
            danger = true,
            onConfirm = {
                confirmRemove = null
                removeActionFocus = null
                viewModel.removeFavResource(item)
            },
            onCancel = {
                closeRemove()
            },
            session = null,
        )
    }
}

@Composable
private fun FolderList(
    folders: List<FavFolder>,
    allEmpty: Boolean,
    loading: Boolean,
    error: String?,
    covers: Map<Long, String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    firstFocus: FocusRequester,
    onRetry: () -> Unit,
    onSelect: (FavFolder) -> Unit,
    onAction: (FavFolder, FocusRequester) -> Unit,
    onFocusPreview: (FavFolder, String?) -> Unit = { _, _ -> },
) {
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DuoPalette.Pink, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        }
        error != null && allEmpty -> DuoEmptyState(
            title = "加载失败",
            hint = error,
            actionText = "重试",
            onAction = onRetry,
        )
        folders.isEmpty() -> DuoEmptyState(
            title = if (allEmpty) "暂无收藏夹" else "未找到匹配的收藏夹",
            hint = if (allEmpty) "在视频简介或播放控制台点收藏" else null,
        )
        else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(folders, key = { it.id }) { folder ->
                FolderRow(
                    folder = folder,
                    cover = covers[folder.id] ?: folder.cover,
                    firstFocus = if (folder.id == folders.first().id) firstFocus else null,
                    onClick = { onSelect(folder) },
                    onAction = { focus -> onAction(folder, focus) },
                    onFocused = { onFocusPreview(folder, covers[folder.id] ?: folder.cover) },
                )
            }
        }
    }
}

@Composable
private fun FolderRow(
    folder: FavFolder,
    cover: String?,
    firstFocus: FocusRequester?,
    onClick: () -> Unit,
    onAction: (FocusRequester) -> Unit,
    onFocused: () -> Unit = {},
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
                    if (it.isFocused) onFocused()
                }
                .duoClickable(onClick = onClick)
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
                if (!cover.isNullOrBlank()) {
                    AsyncImage(
                        model = fixPic(cover),
                        contentDescription = folder.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = folder.title,
                    color = DuoPalette.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val private = privacyFromAttr(folder.attr) == 1
                    Icon(
                        imageVector = if (private) Icons.Outlined.Lock else Icons.Outlined.Public,
                        contentDescription = null,
                        tint = DuoPalette.Tertiary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${folder.mediaCount} 个内容 · ${privacyLabel(folder.attr)}",
                        color = DuoPalette.Secondary,
                        fontSize = 10.sp,
                    )
                }
            }
        }
        DuoIconAction(
            icon = Icons.Outlined.MoreHoriz,
            contentDescription = "收藏夹操作",
            tint = if (actionFocused) DuoPalette.Pink else DuoPalette.Secondary,
            onFocusChanged = { actionFocused = it },
            modifier = Modifier.padding(end = 4.dp).focusRequester(actionFocus),
            onClick = { onAction(actionFocus) },
        )
    }
}

@Composable
private fun ResourceList(
    state: FavoritesUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    firstFocus: FocusRequester,
    onVideoClick: (String) -> Unit,
    onVideoFocused: (TopPreviewUi) -> Unit,
    onFocusCleared: () -> Unit,
    onRemove: (FavResourceItem, FocusRequester) -> Unit,
) {
    when {
        state.isLoadingResources && state.resources.isEmpty() -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = DuoPalette.Pink, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        }
        state.error != null && state.resources.isEmpty() -> DuoEmptyState(
            title = "加载失败",
            hint = state.error,
        )
        state.resources.isEmpty() -> DuoEmptyState(
            title = if (state.searchKeyword != null) "未找到相关视频" else "收藏夹为空",
            hint = if (state.searchKeyword == null) "在视频简介或播放控制台点收藏" else null,
        )
        else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.resources, key = { it.id }) { item ->
                ResourceRow(
                    item = item,
                    firstFocus = if (item.id == state.resources.first().id) firstFocus else null,
                    onFocusChanged = { focused ->
                        if (focused) onVideoFocused(item.toTopPreview()) else onFocusCleared()
                    },
                    onClick = { onVideoClick(item.bvid) },
                    onRemove = { focus -> onRemove(item, focus) },
                )
            }
            item(key = "footer") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        state.isLoadingMore -> CircularProgressIndicator(
                            color = DuoPalette.Pink,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        state.hasMore -> Text(
                            text = "继续向下滚动加载",
                            color = DuoPalette.Tertiary,
                            fontSize = 10.sp,
                        )
                        else -> Text(
                            text = "共 ${state.resources.size} 个视频",
                            color = DuoPalette.Tertiary,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourceRow(
    item: FavResourceItem,
    firstFocus: FocusRequester?,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    onRemove: (FocusRequester) -> Unit,
) {
    var contentFocused by remember { mutableStateOf(false) }
    var actionFocused by remember { mutableStateOf(false) }
    val actionFocus = remember { FocusRequester() }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
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
                    model = fixPic(item.cover),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (item.duration > 0) {
                    Text(
                        text = formatDuration(item.duration),
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
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.upper.name,
                    color = DuoPalette.Secondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.type != 2) {
                    Spacer(Modifier.height(2.dp))
                    Text("非视频收藏，暂不支持取消", color = DuoPalette.Tertiary, fontSize = 9.sp)
                }
            }
        }
        DuoIconAction(
            icon = Icons.Outlined.DeleteOutline,
            contentDescription = "取消收藏",
            tint = if (actionFocused) DuoPalette.Error else DuoPalette.Tertiary,
            onFocusChanged = { actionFocused = it },
            modifier = Modifier.padding(end = 2.dp).focusRequester(actionFocus),
            onClick = { onRemove(actionFocus) },
        )
    }
}

@Composable
private fun FolderActionOverlay(
    folder: FavFolder,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val options = listOf("编辑收藏夹", "删除收藏夹")

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoPalette.Scrim)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        selected = (selected - 1 + options.size) % options.size
                        true
                    }
                    Key.DirectionDown -> {
                        selected = (selected + 1) % options.size
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (selected == 0) onEdit() else onDelete()
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCancel,
                ),
        )
        Column(
            modifier = Modifier
                .width(320.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(14.dp))
                .background(DuoPalette.SurfaceHigh)
                .border(1.dp, DuoPalette.Outline, RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(16.dp),
        ) {
            Text(folder.title, color = DuoPalette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            options.forEachIndexed { index, option ->
                val active = index == selected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (active) {
                                if (index == 1) DuoPalette.Error.copy(alpha = 0.16f)
                                else DuoPalette.Surface
                            } else {
                                Color.Transparent
                            }
                        )
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = when {
                                active && index == 1 -> DuoPalette.Error
                                active -> Color.White
                                else -> DuoPalette.Outline
                            },
                            shape = RoundedCornerShape(8.dp),
                        )
                        .duoClickable {
                            selected = index
                            if (index == 0) onEdit() else onDelete()
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = option,
                        color = if (index == 1) DuoPalette.Error else DuoPalette.Text,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun FolderFormOverlay(
    title: String,
    titleValue: String,
    introValue: String,
    privacy: Int,
    saving: Boolean,
    loading: Boolean = false,
    error: String?,
    titleFocus: FocusRequester,
    onTitle: (String) -> Unit,
    onIntro: (String) -> Unit,
    onPrivacy: (Int) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoPalette.Scrim),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCancel,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .width(400.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DuoPalette.SurfaceHigh)
                .border(1.dp, DuoPalette.Outline, RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(14.dp),
        ) {
            Text(title, color = DuoPalette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (loading) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = DuoPalette.Pink,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("正在读取收藏夹…", color = DuoPalette.Secondary, fontSize = 11.sp)
                }
            } else {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = titleValue,
                    onValueChange = onTitle,
                    label = { Text("标题", fontSize = 11.sp) },
                    textStyle = TextStyle(color = DuoPalette.Text, fontSize = 12.sp),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Next,
                    ),
                    colors = favoritesFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocus)
                        .duoTextFieldNavigation()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionCenter) {
                                keyboard?.show()
                                true
                            } else {
                                false
                            }
                        },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = introValue,
                    onValueChange = onIntro,
                    label = { Text("简介（选填）", fontSize = 11.sp) },
                    textStyle = TextStyle(color = DuoPalette.Text, fontSize = 12.sp),
                    minLines = 1,
                    maxLines = 2,
                    colors = favoritesFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                var privacyFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .duoFocusRing(RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(DuoPalette.Surface)
                        .onFocusChanged { privacyFocused = it.isFocused }
                        .duoClickable { onPrivacy(if (privacy == 1) 0 else 1) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "私密收藏夹",
                        color = DuoPalette.Text,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (privacy == 1) "开" else "关",
                        color = if (privacy == 1) DuoPalette.Pink else DuoPalette.Tertiary,
                        fontSize = 11.sp,
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, color = DuoPalette.Error, fontSize = 10.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DuoPillButton(
                        text = "取消",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            keyboard?.hide()
                            onCancel()
                        },
                    )
                    DuoPillButton(
                        text = if (saving) "保存中…" else "保存",
                        primary = true,
                        enabled = !saving,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            keyboard?.hide()
                            onConfirm()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun favoritesFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DuoPalette.Text,
    unfocusedTextColor = DuoPalette.Text,
    focusedContainerColor = DuoPalette.Surface,
    unfocusedContainerColor = DuoPalette.Surface,
    focusedBorderColor = DuoPalette.Pink,
    unfocusedBorderColor = DuoPalette.Outline,
    focusedLabelColor = DuoPalette.Pink,
    unfocusedLabelColor = DuoPalette.Secondary,
    cursorColor = DuoPalette.Pink,
)

private fun FavResourceItem.toTopPreview(): TopPreviewUi = TopPreviewUi(
    bvid = bvid,
    title = title,
    coverUrl = cover,
    ownerName = upper.name,
    ownerFace = upper.face.orEmpty(),
    playCount = cntInfo?.play ?: 0,
    danmakuCount = cntInfo?.danmaku ?: 0,
    pubdate = pubtime,
    duration = formatDuration(duration),
    description = "",
)
