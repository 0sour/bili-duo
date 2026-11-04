package cn.xfangfang.biliduo.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import cn.xfangfang.biliduo.data.download.DownloadManager
import cn.xfangfang.biliduo.data.local.AppSettings
import cn.xfangfang.biliduo.data.model.DownloadInfo
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.common.DuoConfirmOverlay
import cn.xfangfang.biliduo.ui.common.DuoPageHeader
import cn.xfangfang.biliduo.ui.common.DuoPalette
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.top.TopGlyph
import cn.xfangfang.biliduo.ui.top.TopPanelUi
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.ui.player.PlayerUiState
import cn.xfangfang.biliduo.ui.player.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class SettingsCategory(
    val label: String,
    val icon: ImageVector,
)

private val settingsCategories = listOf(
    SettingsCategory("播放", Icons.Outlined.Bolt),
    SettingsCategory("网络", Icons.Outlined.Wifi),
    SettingsCategory("外观", Icons.Outlined.DarkMode),
    SettingsCategory("弹幕", Icons.Outlined.Subtitles),
    SettingsCategory("存储", Icons.Outlined.Storage),
    SettingsCategory("关于", Icons.Outlined.Info),
)

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
    var category by remember { mutableIntStateOf(0) }
    var wifiOnlyPlayback by remember { mutableStateOf(AppSettings.wifiOnlyPlayback) }
    var wifiOnlyDownload by remember { mutableStateOf(AppSettings.wifiOnlyDownload) }
    var confirmClearDownloads by remember { mutableStateOf(false) }
    var storageTick by remember { mutableIntStateOf(0) }
    val clearRowFocus = remember { FocusRequester() }
    val firstRowFocus = remember { FocusRequester() }

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

    LaunchedEffect(
        category,
        wifiOnlyPlayback,
        wifiOnlyDownload,
        playerUi?.danmakuEnabled,
        playerUi?.danmakuOpacity,
        playerUi?.danmakuSpeed,
        downloads.size,
        storageBytes,
    ) {
        val meta = when (category) {
            1 -> Triple(TopGlyph.NETWORK, 0xFF00A1D6, "网络策略决定缓冲与流量。")
            2 -> Triple(TopGlyph.THEME, 0xFFB18CFF, "深色界面为双屏掌机准备。")
            3 -> Triple(TopGlyph.DANMAKU, 0xFFFFB35C, "弹幕的密度、速度与屏蔽词。")
            4 -> Triple(TopGlyph.STORAGE, 0xFF6FD3A2, "本地缓存与已完成下载。")
            5 -> Triple(TopGlyph.ABOUT, 0xFFA9A9B4, "版本、许可证与开源仓库。")
            else -> Triple(TopGlyph.PLAY, 0xFFFB7299, "清晰度、倍速与后台行为。")
        }
        val wifi = AppSettings.isWifiConnected(context)
        val detail = when (category) {
            0 -> "仅 WiFi 播放：${if (wifiOnlyPlayback) "开" else "关"}"
            1 -> "仅 WiFi 下载：${if (wifiOnlyDownload) "开" else "关"} · 当前 ${if (wifi) "WiFi" else "移动网络"}"
            2 -> "深色主题 · 固定"
            3 -> if (playerUi == null) {
                "跟随播放控制台"
            } else {
                "弹幕：${if (playerUi.danmakuEnabled) "开" else "关"} · " +
                    "${(playerUi.danmakuOpacity * 100).toInt()}% · ${playerUi.danmakuSpeed}x"
            }
            4 -> "已下载 ${downloads.size} 项 · ${formatBytes(storageBytes)}"
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
        delay(120)
        runCatching { firstRowFocus.requestFocus() }
    }
    fun closeClearDownloads() {
        confirmClearDownloads = false
        scope.launch {
            delay(120)
            runCatching { clearRowFocus.requestFocus() }
        }
    }

    LaunchedEffect(confirmClearDownloads, session) {
        if (confirmClearDownloads) {
            session?.setBackInterceptor {
                closeClearDownloads()
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
            .background(DuoPalette.Bg),
    ) {
        DuoPageHeader(title = "设置", onBack = onBack)

        Row(Modifier.fillMaxSize()) {
            CategoryRail(
                selected = category,
                onSelect = { category = it },
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
                            Key.DirectionLeft -> {
                                category = (category - 1 + settingsCategories.size) % settingsCategories.size
                                true
                            }
                            Key.DirectionRight -> {
                                category = (category + 1) % settingsCategories.size
                                true
                            }
                            else -> false
                        }
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                when (category) {
                    0 -> PlaySettings(
                        wifiOnlyPlayback = wifiOnlyPlayback,
                        onWifiOnlyPlayback = {
                            wifiOnlyPlayback = it
                            AppSettings.wifiOnlyPlayback = it
                        },
                        firstFocus = firstRowFocus,
                    )
                    1 -> NetworkSettings(
                        wifiOnlyDownload = wifiOnlyDownload,
                        onWifiOnlyDownload = {
                            wifiOnlyDownload = it
                            AppSettings.wifiOnlyDownload = it
                        },
                        firstFocus = firstRowFocus,
                    )
                    2 -> AppearanceSettings(firstFocus = firstRowFocus)
                    3 -> DanmakuSettings(
                        ui = playerUi,
                        firstFocus = firstRowFocus,
                        onEnabled = { playerViewModel?.setDanmakuEnabled(it) },
                        onOpacity = { playerViewModel?.setDanmakuOpacity(it) },
                        onSpeed = { playerViewModel?.setDanmakuSpeed(it) },
                        onSize = { playerViewModel?.setDanmakuTextSize(it) },
                        onArea = { playerViewModel?.setDanmakuArea(it) },
                        onMax = { playerViewModel?.setDanmakuMaxOnScreen(it) },
                    )
                    4 -> StorageSettings(
                        total = downloads.size,
                        completed = completed.size,
                        bytes = storageBytes,
                        firstFocus = firstRowFocus,
                        clearFocus = clearRowFocus,
                        onClearCompleted = {
                            confirmClearDownloads = true
                        },
                    )
                    else -> AboutSettings(
                        firstFocus = firstRowFocus,
                        onOpenRepository = { openRepository() },
                        onOpenIssues = { openRepository(ISSUES_URL) },
                    )
                }
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
                    runCatching { firstRowFocus.requestFocus() }
                }
            },
            onCancel = {
                closeClearDownloads()
            },
            session = null,
        )
    }

}

@Composable
private fun CategoryRail(
    selected: Int,
    onSelect: (Int) -> Unit,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) DuoPalette.Pink.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (active) DuoPalette.Pink else DuoPalette.Secondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = item.label,
                    color = if (active) DuoPalette.Pink else DuoPalette.Text,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun PlaySettings(
    wifiOnlyPlayback: Boolean,
    onWifiOnlyPlayback: (Boolean) -> Unit,
    firstFocus: FocusRequester,
) {
    SettingsSection("播放")
    SettingsSwitchRow(
        title = "仅 WiFi 播放视频",
        subtitle = "移动网络下不开始播放，避免消耗流量",
        checked = wifiOnlyPlayback,
        focusRequester = firstFocus,
        onToggle = { onWifiOnlyPlayback(!wifiOnlyPlayback) },
    )
    Spacer(Modifier.height(10.dp))
    SettingsSection("播放器")
    SettingsInfoRow("清晰度 / 倍速", "在播放控制台内调整")
    SettingsInfoRow("弹幕外观", "在「弹幕」分类调整默认值")
    SettingsInfoRow("后台行为", "离开 App 会暂停并保留进度")
}

@Composable
private fun NetworkSettings(
    wifiOnlyDownload: Boolean,
    onWifiOnlyDownload: (Boolean) -> Unit,
    firstFocus: FocusRequester,
) {
    val context = LocalContext.current
    val onWifi = remember { AppSettings.isWifiConnected(context) }
    SettingsSection("网络")
    SettingsSwitchRow(
        title = "仅 WiFi 下载视频",
        subtitle = "移动网络下暂停下载任务",
        checked = wifiOnlyDownload,
        focusRequester = firstFocus,
        onToggle = { onWifiOnlyDownload(!wifiOnlyDownload) },
    )
    Spacer(Modifier.height(10.dp))
    SettingsSection("当前状态")
    SettingsInfoRow("网络类型", if (onWifi) "WiFi" else "移动网络 / 离线")
}

@Composable
private fun AppearanceSettings(firstFocus: FocusRequester) {
    SettingsSection("外观")
    SettingsInfoRow("主题", "深色（固定）", focusRequester = firstFocus)
    SettingsInfoRow("模式条", "底部常驻，显示当前手柄所有权")
    SettingsInfoRow("焦点样式", "白色焦点环 + 粉色选中态")
    Spacer(Modifier.height(10.dp))
    Text(
        text = "Bili-Duo 只提供深色主题：上屏视频场景和下屏 OLED 需要稳定的对比度。",
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
    SettingsInfoRow("屏蔽词", "在播放控制台「弹幕设置」内编辑")
}

@Composable
private fun StorageSettings(
    total: Int,
    completed: Int,
    bytes: Long,
    firstFocus: FocusRequester,
    clearFocus: FocusRequester,
    onClearCompleted: () -> Unit,
) {
    SettingsSection("下载")
    SettingsInfoRow("已下载", "$completed 个已完成 / 共 $total 个", focusRequester = firstFocus)
    SettingsInfoRow("占用空间", formatBytes(bytes))
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
}

@Composable
private fun AboutSettings(
    firstFocus: FocusRequester,
    onOpenRepository: () -> Unit,
    onOpenIssues: () -> Unit,
) {
    SettingsSection("关于 Bili-Duo")
    SettingsInfoRow("版本", "v${BuildConfig.VERSION_NAME}", focusRequester = firstFocus)
    SettingsInfoRow("状态", "未测试 RC")
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
    focusRequester: FocusRequester,
    onToggle: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .focusRequester(focusRequester)
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
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
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
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (focusRequester != null) Modifier.duoFocusRing(RoundedCornerShape(8.dp)) else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) DuoPalette.SurfaceHigh else DuoPalette.Surface)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focusRequester != null) Modifier.duoClickable(onClick = {}) else Modifier)
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
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
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
