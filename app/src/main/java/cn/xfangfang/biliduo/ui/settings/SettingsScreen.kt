@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package cn.xfangfang.biliduo.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xfangfang.biliduo.BuildConfig
import cn.xfangfang.biliduo.data.download.DownloadLocation
import cn.xfangfang.biliduo.data.download.DownloadManager
import cn.xfangfang.biliduo.data.local.AppSettings
import cn.xfangfang.biliduo.data.local.GamepadStyle
import cn.xfangfang.biliduo.data.local.GamepadStyleStore
import cn.xfangfang.biliduo.data.local.SearchHistoryManager
import cn.xfangfang.biliduo.data.local.VideoPreference
import cn.xfangfang.biliduo.data.local.VideoPreferenceManager
import cn.xfangfang.biliduo.data.local.videoQualityLabel
import cn.xfangfang.biliduo.data.local.videoQualityOptions
import cn.xfangfang.biliduo.data.local.videoSpeedLabel
import cn.xfangfang.biliduo.data.local.videoSpeedOptions
import cn.xfangfang.biliduo.data.local.videoSubtitleBackgroundLabel
import cn.xfangfang.biliduo.data.local.videoSubtitleBackgroundOptions
import cn.xfangfang.biliduo.data.local.videoSubtitleBottomOptions
import cn.xfangfang.biliduo.data.local.videoSubtitlePositionLabel
import cn.xfangfang.biliduo.data.local.videoSubtitleSizeLabel
import cn.xfangfang.biliduo.data.local.videoSubtitleSizeOptions
import cn.xfangfang.biliduo.data.model.DownloadInfo
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.common.DuoConfirmOverlay
import cn.xfangfang.biliduo.ui.common.DuoPageHeader
import cn.xfangfang.biliduo.ui.common.DuoPalette
import cn.xfangfang.biliduo.ui.duo.requestPageFocus
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoTabCycling
import cn.xfangfang.biliduo.ui.theme.SunSchedule
import cn.xfangfang.biliduo.ui.theme.THEME_MANUAL
import cn.xfangfang.biliduo.ui.theme.themeModeLabels
import cn.xfangfang.biliduo.ui.theme.themeModeOptions
import cn.xfangfang.biliduo.ui.top.TopGlyph
import cn.xfangfang.biliduo.ui.top.TopPanelUi
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.ui.player.PlayerUiState
import cn.xfangfang.biliduo.ui.player.PlayerViewModel
import coil.imageLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class SettingsCategory(
    val label: String,
    val icon: ImageVector,
)

private val settingsCategories = listOf(
    SettingsCategory("播放", Icons.Outlined.Bolt),
    SettingsCategory("手柄", Icons.Outlined.SportsEsports),
    SettingsCategory("网络", Icons.Outlined.Wifi),
    SettingsCategory("外观", Icons.Outlined.DarkMode),
    SettingsCategory("弹幕", Icons.Outlined.Subtitles),
    SettingsCategory("存储", Icons.Outlined.Storage),
    SettingsCategory("关于", Icons.Outlined.Info),
)

/**
 * The settings content is a closed focus loop: Left goes back to the
 * category rail and Up/Down stop at the list edges. Without this, focus
 * can fall through to the global app rail (e.g. Down from the last row).
 */
private fun settingsTopEdge(backFocus: FocusRequester) =
    Modifier.focusProperties { up = backFocus }

private const val REPOSITORY_URL = "https://github.com/0sour/bili-duo"
private const val ISSUES_URL = "https://github.com/0sour/bili-duo/issues"

private val danmakuOpacityOptions = listOf(0.3f, 0.5f, 0.7f, 0.9f, 1f)
private val danmakuSpeedOptions = listOf(0.7f, 1f, 1.4f, 1.8f)
private val danmakuSizeOptions = listOf(13f, 16f, 20f, 24f)
private val danmakuAreaOptions = listOf(0.3f, 0.55f, 0.8f, 1f)
private val danmakuMaxOptions = listOf(0, 30, 60, 100)

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onTopPanel: (TopPanelUi) -> Unit = {},
    session: DuoSessionViewModel? = null,
    playerViewModel: PlayerViewModel? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val videoPreferenceManager = remember {
        VideoPreferenceManager(
            context.getSharedPreferences("bili_prefs", android.content.Context.MODE_PRIVATE)
        )
    }
    var videoPreference by remember { mutableStateOf(videoPreferenceManager.load()) }
    var gamepadStyle by remember { mutableStateOf(GamepadStyleStore.current()) }
    fun updateVideoPreference(next: VideoPreference) {
        videoPreference = next
        videoPreferenceManager.save(next)
    }
    fun updateGamepadStyle(next: GamepadStyle) {
        gamepadStyle = next
        GamepadStyleStore.save(next)
    }
    var category by remember { mutableIntStateOf(0) }
    var wifiOnlyPlayback by remember { mutableStateOf(AppSettings.wifiOnlyPlayback) }
    var wifiOnlyDownload by remember { mutableStateOf(AppSettings.wifiOnlyDownload) }
    var confirmClearDownloads by remember { mutableStateOf(false) }
    var confirmClearAllDownloads by remember { mutableStateOf(false) }
    var confirmClearSearchHistory by remember { mutableStateOf(false) }
    var confirmClearImageCache by remember { mutableStateOf(false) }
    var storageTick by remember { mutableIntStateOf(0) }
    val clearRowFocus = remember { FocusRequester() }
    val clearAllFocus = remember { FocusRequester() }
    val firstRowFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val categoryFocus = remember { List(settingsCategories.size) { FocusRequester() } }
    val scrollState = rememberScrollState()

    val playerUiFlow = playerViewModel?.uiState
    val playerUi: PlayerUiState? = if (playerUiFlow != null) {
        playerUiFlow.collectAsState().value
    } else {
        null
    }
    val downloadManager = remember { DownloadManager.getInstance(context) }
    val downloads = remember(storageTick) { downloadManager.getDownloads() }
    val completed = remember(downloads) {
        downloads.filter { it.status == DownloadInfo.STATUS_COMPLETED }
    }
    val storageBytes = remember(downloads) {
        downloads.sumOf { if (it.fileSize > 0) it.fileSize else it.totalSize.coerceAtLeast(0) }
    }
    val searchHistory = remember(storageTick) {
        SearchHistoryManager(
            context.getSharedPreferences("bili_prefs", android.content.Context.MODE_PRIVATE)
        ).load()
    }
    val downloadTree by DownloadLocation.treeUri.collectAsState()
    val downloadDir = downloadTree?.let {
        "系统目录 · ${DownloadLocation.displayName(it) ?: "已选择"}"
    } ?: "应用私有目录 / downloads"

    LaunchedEffect(
        category,
        wifiOnlyPlayback,
        wifiOnlyDownload,
        videoPreference,
        gamepadStyle,
        playerUi?.danmakuEnabled,
        playerUi?.danmakuOpacity,
        playerUi?.danmakuSpeed,
        downloads.size,
        storageBytes,
        AppSettings.themeMode,
        AppSettings.manualDark,
    ) {
        val meta = when (category) {
            1 -> Triple(TopGlyph.GAMEPAD, 0xFF9AA7FF, "实体按键位置固定，只切换固件映射。")
            2 -> Triple(TopGlyph.NETWORK, 0xFF00A1D6, "网络策略决定缓冲与流量。")
            3 -> Triple(TopGlyph.THEME, 0xFFB18CFF, "亮暗色、日出日落与侧栏快捷切换。")
            4 -> Triple(TopGlyph.DANMAKU, 0xFFFFB35C, "弹幕的密度、速度与屏蔽词。")
            5 -> Triple(TopGlyph.STORAGE, 0xFF6FD3A2, "本地缓存与已完成下载。")
            6 -> Triple(TopGlyph.ABOUT, 0xFFA9A9B4, "版本、许可证与开源仓库。")
            else -> Triple(TopGlyph.PLAY, 0xFFFB7299, "清晰度、倍速与后台行为。")
        }
        val wifi = AppSettings.isWifiConnected(context)
        val detail = when (category) {
            0 -> "默认 ${videoQualityLabel(videoPreference.quality)} · " +
                "${videoSpeedLabel(videoPreference.speed)} · " +
                "字幕${if (videoPreference.subtitleEnabled) "开" else "关"}"
            1 -> "${gamepadStyle.label} · B 确认 / A 返回 / Y 刷新"
            2 -> "仅 WiFi 下载：${if (wifiOnlyDownload) "开" else "关"} · 当前 ${if (wifi) "WiFi" else "移动网络"}"
            3 -> "${themeModeLabels[AppSettings.themeMode] ?: "不自动切换"} · ${if (AppSettings.manualDark) "深色" else "浅色"}"
            4 -> if (playerUi == null) {
                "跟随播放控制台"
            } else {
                "弹幕：${if (playerUi.danmakuEnabled) "开" else "关"} · " +
                    "${(playerUi.danmakuOpacity * 100).toInt()}% · ${playerUi.danmakuSpeed}x"
            }
            5 -> "已下载 ${downloads.size} 项 · ${formatBytes(storageBytes)}"
            else -> "v${BuildConfig.VERSION_NAME} · MIT License"
        }
        onTopPanel(
            TopPanelUi(
                eyebrow = "SETTINGS / 设置",
                title = settingsCategories[category].label,
                subtitle = "第 ${category + 1} / ${settingsCategories.size} 个分类",
                description = meta.third,
                detail = detail,
                items = settingsCategories.map { it.label },
                activeIndex = category,
                glyph = meta.first,
                accent = meta.second,
            )
        )
    }
    // Entering the page lands on the first row. Category changes caused by
    // moving focus in the category rail must not steal focus back to content.
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { firstRowFocus.requestPageFocus() }
    }
    LaunchedEffect(category) {
        // A long category can leave the next one scrolled halfway down.
        scrollState.scrollTo(0)
    }
    fun selectCategory(index: Int, focusContent: Boolean) {
        category = index
        if (focusContent) {
            scope.launch {
                delay(120)
                runCatching { firstRowFocus.requestPageFocus() }
            }
        }
    }
    // The bottom row also scrolls to the end so its focus ring is not clipped
    // by the scroll viewport (bring-into-view only guarantees the row bounds).
    val bottomEdgeModifier = Modifier
        .focusProperties { down = FocusRequester.Cancel }
        .onFocusChanged {
            if (it.isFocused) {
                scope.launch {
                    // Bring-into-view runs after the focus event and would
                    // cancel an immediate scroll; let it settle first.
                    delay(80L)
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }
        }
    fun closeClearDownloads() {
        confirmClearDownloads = false
        scope.launch {
            delay(120)
            runCatching { clearRowFocus.requestPageFocus() }
        }
    }

    fun closeClearAllDownloads() {
        confirmClearAllDownloads = false
        scope.launch {
            delay(120)
            runCatching { clearAllFocus.requestPageFocus() }
        }
    }

    fun closeClearSearchHistory() {
        confirmClearSearchHistory = false
        scope.launch {
            delay(120)
            runCatching { clearRowFocus.requestPageFocus() }
        }
    }

    fun closeClearImageCache() {
        confirmClearImageCache = false
        scope.launch {
            delay(120)
            runCatching { clearRowFocus.requestPageFocus() }
        }
    }

    LaunchedEffect(
        confirmClearDownloads,
        confirmClearAllDownloads,
        confirmClearSearchHistory,
        confirmClearImageCache,
        session,
    ) {
        val close = when {
            confirmClearDownloads -> ({ closeClearDownloads() })
            confirmClearAllDownloads -> ({ closeClearAllDownloads() })
            confirmClearSearchHistory -> ({ closeClearSearchHistory() })
            confirmClearImageCache -> ({ closeClearImageCache() })
            else -> null
        }
        if (close != null) {
            session?.setBackInterceptor {
                close()
                true
            }
        } else {
            session?.setBackInterceptor(null)
        }
    }
    DisposableEffect(session) {
        onDispose { session?.setBackInterceptor(null) }
    }

    fun openRepository(url: String = REPOSITORY_URL) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            // Presentation context is not an Activity; NEW_TASK is required.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "未找到浏览器，无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoPalette.Bg)
            .duoTabCycling(
                onPreviousTab = {
                    selectCategory(
                        (category - 1 + settingsCategories.size) % settingsCategories.size,
                        focusContent = true,
                    )
                },
                onNextTab = {
                    selectCategory(
                        (category + 1) % settingsCategories.size,
                        focusContent = true,
                    )
                },
            ),
    ) {
        DuoPageHeader(
            title = "设置",
            onBack = onBack,
            backModifier = Modifier
                .focusRequester(backFocus)
                .focusProperties { up = FocusRequester.Cancel },
        )

        Row(Modifier.fillMaxSize()) {
            CategoryRail(
                selected = category,
                onSelect = { category = it },
                onEnter = { selectCategory(it, focusContent = true) },
                focusRequesters = categoryFocus,
                backFocus = backFocus,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        when (event.key) {
                            // Left returns to the settings category rail.
                            // Right has nowhere to go; swallow it so focus
                            // cannot leak into the global app rail.
                            Key.DirectionLeft -> {
                                scope.launch {
                                    delay(40)
                                    runCatching { categoryFocus[category].requestPageFocus() }
                                }
                                true
                            }
                            Key.DirectionRight -> true
                            else -> false
                        }
                    }
                    .verticalScroll(scrollState)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (category) {
                    0 -> PlaySettings(
                        preference = videoPreference,
                        onPreference = { updateVideoPreference(it) },
                        wifiOnlyPlayback = wifiOnlyPlayback,
                        onWifiOnlyPlayback = {
                            wifiOnlyPlayback = it
                            AppSettings.wifiOnlyPlayback = it
                        },
                        firstFocus = firstRowFocus,
                        backFocus = backFocus,
                        bottomModifier = bottomEdgeModifier,
                    )
                    1 -> GamepadSettings(
                        gamepadStyle = gamepadStyle,
                        onGamepadStyle = { updateGamepadStyle(it) },
                        firstFocus = firstRowFocus,
                        backFocus = backFocus,
                        bottomModifier = bottomEdgeModifier,
                    )
                    2 -> NetworkSettings(
                        wifiOnlyDownload = wifiOnlyDownload,
                        onWifiOnlyDownload = {
                            wifiOnlyDownload = it
                            AppSettings.wifiOnlyDownload = it
                        },
                        firstFocus = firstRowFocus,
                        backFocus = backFocus,
                        bottomModifier = bottomEdgeModifier,
                    )
                    3 -> AppearanceSettings(
                        firstFocus = firstRowFocus,
                        backFocus = backFocus,
                        bottomModifier = bottomEdgeModifier,
                    )
                    4 -> DanmakuSettings(
                        ui = playerUi,
                        firstFocus = firstRowFocus,
                        backFocus = backFocus,
                        bottomModifier = bottomEdgeModifier,
                        onEnabled = { playerViewModel?.setDanmakuEnabled(it) },
                        onOpacity = { playerViewModel?.setDanmakuOpacity(it) },
                        onSpeed = { playerViewModel?.setDanmakuSpeed(it) },
                        onSize = { playerViewModel?.setDanmakuTextSize(it) },
                        onArea = { playerViewModel?.setDanmakuArea(it) },
                        onMax = { playerViewModel?.setDanmakuMaxOnScreen(it) },
                    )
                    5 -> StorageSettings(
                        total = downloads.size,
                        completed = completed.size,
                        bytes = storageBytes,
                        searchHistoryCount = searchHistory.size,
                        downloadDir = downloadDir,
                        hasCustomDir = downloadTree != null,
                        firstFocus = firstRowFocus,
                        backFocus = backFocus,
                        bottomModifier = bottomEdgeModifier,
                        clearFocus = clearRowFocus,
                        clearAllFocus = clearAllFocus,
                        onClearCompleted = {
                            confirmClearDownloads = true
                        },
                        onClearAll = {
                            confirmClearAllDownloads = true
                        },
                        onClearSearchHistory = {
                            confirmClearSearchHistory = true
                        },
                        onClearImageCache = {
                            confirmClearImageCache = true
                        },
                        onPickDownloadDir = { DownloadLocation.requestPicker() },
                        onResetDownloadDir = { DownloadLocation.clear() },
                    )
                    else -> AboutSettings(
                        firstFocus = firstRowFocus,
                        backFocus = backFocus,
                        bottomModifier = bottomEdgeModifier,
                        onOpenRepository = { openRepository() },
                        onOpenIssues = { openRepository(ISSUES_URL) },
                    )
                }
                // Extra tail lets the last row scroll clear of the viewport
                // edge so its focus ring is never clipped.
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (confirmClearDownloads) {
        DuoConfirmOverlay(
            title = "清除已完成下载",
            message = "确定删除全部 ${completed.size} 个已完成下载吗？本地文件会被删除，无法恢复。",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                confirmClearDownloads = false
                downloadManager.clearCompleted()
                storageTick++
                scope.launch {
                    delay(120)
                    runCatching { firstRowFocus.requestPageFocus() }
                }
            },
            onCancel = {
                closeClearDownloads()
            },
            session = null,
        )
    }
    if (confirmClearAllDownloads) {
        DuoConfirmOverlay(
            title = "清除全部下载",
            message = "确定删除全部 ${downloads.size} 个下载任务和本地文件吗？此操作不可恢复。",
            confirmText = "全部删除",
            danger = true,
            onConfirm = {
                confirmClearAllDownloads = false
                downloadManager.clearAll()
                storageTick++
                scope.launch {
                    delay(120)
                    runCatching { firstRowFocus.requestPageFocus() }
                }
            },
            onCancel = {
                closeClearAllDownloads()
            },
            session = null,
        )
    }
    if (confirmClearSearchHistory) {
        DuoConfirmOverlay(
            title = "清除搜索历史",
            message = "确定删除全部 ${searchHistory.size} 条本地搜索记录吗？",
            confirmText = "清除",
            danger = true,
            onConfirm = {
                confirmClearSearchHistory = false
                SearchHistoryManager(
                    context.getSharedPreferences("bili_prefs", android.content.Context.MODE_PRIVATE)
                ).clear()
                storageTick++
                Toast.makeText(context, "搜索历史已清除", Toast.LENGTH_SHORT).show()
                scope.launch {
                    delay(120)
                    runCatching { clearRowFocus.requestPageFocus() }
                }
            },
            onCancel = {
                closeClearSearchHistory()
            },
            session = null,
        )
    }
    if (confirmClearImageCache) {
        DuoConfirmOverlay(
            title = "清除图片缓存",
            message = "将清空封面和头像的内存与磁盘缓存，之后浏览时会重新加载。",
            confirmText = "清除",
            danger = false,
            onConfirm = {
                confirmClearImageCache = false
                runCatching {
                    context.imageLoader.memoryCache?.clear()
                    context.imageLoader.diskCache?.clear()
                }
                Toast.makeText(context, "图片缓存已清除", Toast.LENGTH_SHORT).show()
                scope.launch {
                    delay(120)
                    runCatching { clearRowFocus.requestPageFocus() }
                }
            },
            onCancel = {
                closeClearImageCache()
            },
            session = null,
        )
    }

}

@Composable
private fun CategoryRail(
    selected: Int,
    onSelect: (Int) -> Unit,
    onEnter: (Int) -> Unit,
    focusRequesters: List<FocusRequester>,
    backFocus: FocusRequester? = null,
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .fillMaxHeight()
            .background(DuoPalette.Surface)
            .padding(vertical = 8.dp),
    ) {
        settingsCategories.forEachIndexed { index, item ->
            val active = index == selected
            var focused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .focusRequester(focusRequesters[index])
                    .focusProperties {
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                        if (index == 0) up = backFocus ?: FocusRequester.Cancel
                        if (index == settingsCategories.lastIndex) down = FocusRequester.Cancel
                    }
                    .duoFocusRing(RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) DuoPalette.Pink.copy(alpha = 0.12f) else Color.Transparent)
                    .onFocusChanged {
                        focused = it.isFocused
                        // Moving through the rail previews each category
                        // without stealing focus away from the rail.
                        if (it.isFocused) onSelect(index)
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionRight
                        ) {
                            onEnter(index)
                            true
                        } else {
                            false
                        }
                    }
                    .duoClickable { onEnter(index) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (active || focused) DuoPalette.Pink else DuoPalette.Secondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = item.label,
                    color = if (active || focused) DuoPalette.Pink else DuoPalette.Text,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "↑ / ↓ 选择分类 · → 进入",
            color = DuoPalette.Tertiary,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PlaySettings(
    preference: VideoPreference,
    onPreference: (VideoPreference) -> Unit,
    wifiOnlyPlayback: Boolean,
    onWifiOnlyPlayback: (Boolean) -> Unit,
    firstFocus: FocusRequester,
    backFocus: FocusRequester,
    bottomModifier: Modifier,
) {
    val subtitleFocus = remember { FocusRequester() }

    SettingsSection("视频设置 · 每次打开视频时应用")
    SettingsValueRow(
        title = "默认清晰度",
        value = videoQualityLabel(preference.quality),
        focusRequester = firstFocus,
        modifier = settingsTopEdge(backFocus),
        onClick = {
            val index = videoQualityOptions.indexOfFirst { it.qn == preference.quality }
            val next = videoQualityOptions[
                (if (index >= 0) index + 1 else 0) % videoQualityOptions.size
            ]
            onPreference(preference.copy(quality = next.qn))
        },
    )
    SettingsValueRow(
        title = "默认倍速",
        value = videoSpeedLabel(preference.speed),
        onClick = {
            onPreference(preference.copy(speed = nextValue(preference.speed, videoSpeedOptions)))
        },
    )
    SettingsSwitchRow(
        title = "默认开启字幕",
        subtitle = "打开视频时自动载入第一条字幕轨",
        checked = preference.subtitleEnabled,
        focusRequester = subtitleFocus,
        onToggle = {
            onPreference(preference.copy(subtitleEnabled = !preference.subtitleEnabled))
        },
    )
    SettingsValueRow(
        title = "字幕字号",
        value = videoSubtitleSizeLabel(preference.subtitleTextSizeSp),
        onClick = {
            onPreference(
                preference.copy(
                    subtitleTextSizeSp = nextValue(
                        preference.subtitleTextSizeSp,
                        videoSubtitleSizeOptions,
                    ),
                )
            )
        },
    )
    SettingsValueRow(
        title = "字幕背景",
        value = videoSubtitleBackgroundLabel(preference.subtitleBackgroundAlpha),
        onClick = {
            onPreference(
                preference.copy(
                    subtitleBackgroundAlpha = nextValue(
                        preference.subtitleBackgroundAlpha,
                        videoSubtitleBackgroundOptions,
                    ),
                )
            )
        },
    )
    SettingsValueRow(
        title = "字幕位置",
        value = videoSubtitlePositionLabel(preference.subtitleBottomPaddingDp),
        onClick = {
            val index = videoSubtitleBottomOptions.indexOf(preference.subtitleBottomPaddingDp)
            val next = videoSubtitleBottomOptions[
                (if (index >= 0) index + 1 else 0) % videoSubtitleBottomOptions.size
            ]
            onPreference(preference.copy(subtitleBottomPaddingDp = next))
        },
    )
    Text(
        text = "播放控制台里修改清晰度 / 倍速 / 字幕只影响当前视频；" +
            "弹幕设置与「弹幕」分类双向同步。",
        color = DuoPalette.Tertiary,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
    )
    Spacer(Modifier.height(6.dp))
    SettingsSection("播放行为")
    SettingsSwitchRow(
        title = "仅 WiFi 播放视频",
        subtitle = "移动网络下不开始播放，避免消耗流量",
        checked = wifiOnlyPlayback,
        onToggle = { onWifiOnlyPlayback(!wifiOnlyPlayback) },
    )
    SettingsInfoRow(
        title = "后台行为",
        value = "离开 App 会暂停并保留进度",
        modifier = bottomModifier,
    )
}

@Composable
private fun GamepadSettings(
    gamepadStyle: GamepadStyle,
    onGamepadStyle: (GamepadStyle) -> Unit,
    firstFocus: FocusRequester,
    backFocus: FocusRequester,
    bottomModifier: Modifier,
) {
    SettingsSection("手柄风格")
    SettingsValueRow(
        title = "当前风格",
        value = gamepadStyle.label,
        focusRequester = firstFocus,
        modifier = settingsTopEdge(backFocus),
        onClick = {
            onGamepadStyle(
                if (gamepadStyle == GamepadStyle.XBOX) GamepadStyle.RETRO else GamepadStyle.XBOX
            )
        },
    )
    Text(
        text = "请与 RP Duo 系统设置里的「手柄风格」保持一致。实体位置固定，" +
            "功能不随风格变化；屏幕提示始终按丝印标注。切换后立即生效，无需重启。",
        color = DuoPalette.Tertiary,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
    )
    Spacer(Modifier.height(6.dp))
    SettingsSection("实体按键")
    SettingsInfoRow(title = "下 · B", value = "确认 / 播放 / 输入")
    SettingsInfoRow(title = "右 · A", value = "返回 / 关闭 / 收键盘")
    SettingsInfoRow(title = "左 · Y", value = "刷新 / 点赞 / 弹幕开关")
    SettingsInfoRow(
        title = "上 · X",
        value = "未绑定",
        modifier = bottomModifier,
    )
}

@Composable
private fun NetworkSettings(
    wifiOnlyDownload: Boolean,
    onWifiOnlyDownload: (Boolean) -> Unit,
    firstFocus: FocusRequester,
    backFocus: FocusRequester,
    bottomModifier: Modifier,
) {
    val context = LocalContext.current
    val onWifi = remember { AppSettings.isWifiConnected(context) }
    SettingsSection("网络")
    SettingsSwitchRow(
        title = "仅 WiFi 下载视频",
        subtitle = "移动网络下暂停下载任务",
        checked = wifiOnlyDownload,
        focusRequester = firstFocus,
        modifier = settingsTopEdge(backFocus),
        onToggle = { onWifiOnlyDownload(!wifiOnlyDownload) },
    )
    Spacer(Modifier.height(10.dp))
    SettingsSection("当前状态")
    SettingsInfoRow(
        title = "网络类型",
        value = if (onWifi) "WiFi" else "移动网络 / 离线",
        modifier = bottomModifier,
    )
}

@Composable
private fun AppearanceSettings(
    firstFocus: FocusRequester,
    backFocus: FocusRequester,
    bottomModifier: Modifier,
) {
    val mode = AppSettings.themeMode
    val modeIndex = themeModeOptions.indexOf(mode).coerceAtLeast(0)
    SettingsSection("亮暗色")
    SettingsValueRow(
        title = "自动切换",
        value = themeModeLabels[mode] ?: "不自动切换",
        focusRequester = firstFocus,
        modifier = settingsTopEdge(backFocus),
        onClick = {
            AppSettings.themeMode = themeModeOptions[(modeIndex + 1) % themeModeOptions.size]
        },
    )
    SettingsValueRow(
        title = "手动外观",
        value = if (AppSettings.manualDark) "深色" else "浅色",
        onClick = {
            AppSettings.themeMode = THEME_MANUAL
            AppSettings.manualDark = !AppSettings.manualDark
        },
    )
    SettingsInfoRow(
        title = "日出日落",
        value = SunSchedule.describe(),
    )
    SettingsInfoRow(
        title = "快捷切换",
        value = "侧栏左下角",
        modifier = bottomModifier,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = "「日出日落」按本地时间与季节估算，约每分钟检查一次；" +
            "侧栏快捷切换会改为手动模式。上屏播放留边始终保持黑色。",
        color = DuoPalette.Tertiary,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
private fun DanmakuSettings(
    ui: PlayerUiState?,
    firstFocus: FocusRequester,
    backFocus: FocusRequester,
    bottomModifier: Modifier,
    onEnabled: (Boolean) -> Unit,
    onOpacity: (Float) -> Unit,
    onSpeed: (Float) -> Unit,
    onSize: (Float) -> Unit,
    onArea: (Float) -> Unit,
    onMax: (Int) -> Unit,
) {
    val enabled = ui?.danmakuEnabled ?: true
    val opacity = ui?.danmakuOpacity ?: 0.9f
    val speed = ui?.danmakuSpeed ?: 1f
    val size = ui?.danmakuTextSize ?: 16f
    val area = ui?.danmakuArea ?: 1f
    val max = ui?.danmakuMaxOnScreen ?: 0

    SettingsSection("弹幕")
    SettingsSwitchRow(
        title = "默认显示弹幕",
        subtitle = "进入播放时沿用上次开关",
        checked = enabled,
        focusRequester = firstFocus,
        modifier = settingsTopEdge(backFocus),
        onToggle = { onEnabled(!enabled) },
    )
    SettingsValueRow(
        title = "不透明度",
        value = "${(opacity * 100).toInt()}%",
        onClick = { onOpacity(nextValue(opacity, danmakuOpacityOptions)) },
    )
    SettingsValueRow(
        title = "速度",
        value = speedLabel(speed),
        onClick = { onSpeed(nextValue(speed, danmakuSpeedOptions)) },
    )
    SettingsValueRow(
        title = "字号",
        value = sizeLabel(size),
        onClick = { onSize(nextValue(size, danmakuSizeOptions)) },
    )
    SettingsValueRow(
        title = "显示区域",
        value = areaLabel(area),
        onClick = { onArea(nextValue(area, danmakuAreaOptions)) },
    )
    SettingsValueRow(
        title = "同屏上限",
        value = if (max <= 0) "不限" else "$max",
        onClick = {
            val index = danmakuMaxOptions.indexOf(max)
            onMax(danmakuMaxOptions[(if (index >= 0) index + 1 else 0) % danmakuMaxOptions.size])
        },
    )
    Spacer(Modifier.height(10.dp))
    SettingsInfoRow(
        title = "屏蔽词",
        value = "在播放控制台「播放设置」内编辑",
        modifier = bottomModifier,
    )
}

@Composable
private fun StorageSettings(
    total: Int,
    completed: Int,
    bytes: Long,
    searchHistoryCount: Int,
    downloadDir: String,
    hasCustomDir: Boolean,
    firstFocus: FocusRequester,
    backFocus: FocusRequester,
    bottomModifier: Modifier,
    clearFocus: FocusRequester,
    clearAllFocus: FocusRequester,
    onClearCompleted: () -> Unit,
    onClearAll: () -> Unit,
    onClearSearchHistory: () -> Unit,
    onClearImageCache: () -> Unit,
    onPickDownloadDir: () -> Unit,
    onResetDownloadDir: () -> Unit,
) {
    SettingsSection("下载")
    SettingsInfoRow(
        title = "已下载",
        value = "$completed 个已完成 / 共 $total 个",
        focusRequester = firstFocus,
        modifier = settingsTopEdge(backFocus),
    )
    SettingsInfoRow("占用空间", formatBytes(bytes))
    SettingsActionRow(
        title = "下载目录",
        subtitle = downloadDir,
        onClick = onPickDownloadDir,
    )
    if (hasCustomDir) {
        SettingsActionRow(
            title = "恢复默认下载目录",
            subtitle = "新的下载保存到应用私有目录",
            onClick = onResetDownloadDir,
        )
    }
    Spacer(Modifier.height(10.dp))
    SettingsSection("清理")
    SettingsActionRow(
        title = "清除已完成下载",
        subtitle = if (completed > 0) "删除 $completed 个文件，无法恢复" else "暂无已完成的下载",
        danger = true,
        enabled = completed > 0,
        focusRequester = clearFocus,
        onClick = onClearCompleted,
    )
    SettingsActionRow(
        title = "清除全部下载",
        subtitle = if (total > 0) "删除全部 $total 个任务和本地文件" else "暂无下载",
        danger = true,
        enabled = total > 0,
        focusRequester = clearAllFocus,
        onClick = onClearAll,
    )
    SettingsActionRow(
        title = "清除搜索历史",
        subtitle = if (searchHistoryCount > 0) "$searchHistoryCount 条本地记录" else "暂无搜索历史",
        enabled = searchHistoryCount > 0,
        onClick = onClearSearchHistory,
    )
    SettingsActionRow(
        title = "清除图片缓存",
        subtitle = "封面和头像的磁盘/内存缓存",
        modifier = bottomModifier,
        onClick = onClearImageCache,
    )
}

@Composable
private fun AboutSettings(
    firstFocus: FocusRequester,
    backFocus: FocusRequester,
    bottomModifier: Modifier,
    onOpenRepository: () -> Unit,
    onOpenIssues: () -> Unit,
) {
    SettingsSection("关于 Bili-Duo")
    SettingsInfoRow(
        title = "版本",
        value = "v${BuildConfig.VERSION_NAME}",
        focusRequester = firstFocus,
        modifier = settingsTopEdge(backFocus),
    )
    SettingsInfoRow("状态", "测试版 RC")
    SettingsActionRow(
        title = "开源仓库",
        subtitle = "github.com/0sour/bili-duo",
        icon = Icons.Outlined.Language,
        onClick = onOpenRepository,
    )
    SettingsInfoRow("许可证", "MIT License · NOTICE")
    SettingsInfoRow("上游", "bilibili-pure-apk（MIT）")
    Spacer(Modifier.height(10.dp))
    SettingsSection("反馈")
    SettingsActionRow(
        title = "GitHub Issues",
        subtitle = "问题反馈与功能建议",
        icon = Icons.Outlined.Language,
        modifier = bottomModifier,
        onClick = onOpenIssues,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = "本应用不会连接 BiliPure 自动更新；更新入口已移除。",
        color = DuoPalette.Tertiary,
        fontSize = 10.sp,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        color = DuoPalette.Tertiary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .duoFocusRing(RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) DuoPalette.SurfaceHigh else DuoPalette.Surface)
            .onFocusChanged { focused = it.isFocused }
            .duoClickable(onClick = onToggle)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, color = DuoPalette.Text, fontSize = 12.sp)
            Text(
                text = subtitle,
                color = DuoPalette.Tertiary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SwitchVisual(checked)
    }
}

@Composable
private fun SettingsValueRow(
    title: String,
    value: String,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .duoFocusRing(RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) DuoPalette.SurfaceHigh else DuoPalette.Surface)
            .onFocusChanged { focused = it.isFocused }
            .duoClickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, color = DuoPalette.Text, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(text = value, color = DuoPalette.Pink, fontSize = 11.sp)
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = DuoPalette.Tertiary,
            modifier = Modifier.size(0.dp),
        )
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    value: String,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .duoFocusRing(RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) DuoPalette.SurfaceHigh else DuoPalette.Surface)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, color = DuoPalette.Text, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = DuoPalette.Secondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    danger: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .duoFocusRing(RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) DuoPalette.SurfaceHigh else DuoPalette.Surface)
            .onFocusChanged { focused = it.isFocused }
            .duoClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (danger) DuoPalette.Error else DuoPalette.Secondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) {
                    if (danger) DuoPalette.Error else DuoPalette.Text
                } else {
                    DuoPalette.Tertiary
                },
                fontSize = 12.sp,
            )
            Text(
                text = subtitle,
                color = DuoPalette.Tertiary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SwitchVisual(checked: Boolean) {
    Box(
        modifier = Modifier
            .width(38.dp)
            .height(22.dp)
            .clip(CircleShape)
            .background(if (checked) DuoPalette.Pink else DuoPalette.SurfaceHighest),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) DuoPalette.OnPink else DuoPalette.Secondary),
        )
    }
}

private fun nextValue(current: Float, options: List<Float>): Float {
    val index = options.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
    return options[(if (index >= 0) index + 1 else 0) % options.size]
}

private fun speedLabel(value: Float): String = when {
    value <= 0.8f -> "0.7×"
    value <= 1.2f -> "1.0×"
    value <= 1.6f -> "1.4×"
    else -> "1.8×"
}

private fun sizeLabel(value: Float): String = when {
    value <= 14f -> "小"
    value <= 18f -> "标准"
    value <= 22f -> "大"
    else -> "特大"
}

private fun areaLabel(value: Float): String = when {
    value <= 0.4f -> "1/4"
    value <= 0.65f -> "1/2"
    value <= 0.9f -> "3/4"
    else -> "全屏"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format(java.util.Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}