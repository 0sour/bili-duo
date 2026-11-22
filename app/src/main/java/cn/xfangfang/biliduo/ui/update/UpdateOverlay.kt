@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package cn.xfangfang.biliduo.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xfangfang.biliduo.BuildConfig
import cn.xfangfang.biliduo.data.local.GamepadStyleStore
import cn.xfangfang.biliduo.data.update.UpdateInfo
import cn.xfangfang.biliduo.data.update.UpdateSource
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.common.DuoPalette
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bottom-screen update sheet. This is deliberately an in-tree overlay, not a
 * Compose Dialog: Dialog/AlertDialog cannot be hosted by a Presentation.
 *
 * Left: release notes, scrollable with Up/Down; Right: download/install actions.
 */
@Composable
fun UpdateOverlay(
    viewModel: UpdateViewModel,
    session: DuoSessionViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val update = viewModel.available.collectAsState().value
    val gamepadStyle by GamepadStyleStore.style.collectAsState()
    if (update == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val infoFocus = remember { FocusRequester() }
    val cnFocus = remember { FocusRequester() }
    val githubFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val installFocus = remember { FocusRequester() }
    val permissionFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    var firstState by remember { mutableStateOf(true) }

    val cnUrl = update.downloadUrl(UpdateSource.CN)
    val githubUrl = update.downloadUrl(UpdateSource.GITHUB)
    val sourceFocus = if (cnUrl != null) cnFocus else githubFocus

    fun close() {
        if (state is UpdateUiState.Downloading) viewModel.cancelDownload()
        onClose()
    }

    val currentClose by rememberUpdatedState { close() }
    DisposableEffect(session) {
        session.setOverlayBackInterceptor {
            currentClose()
            true
        }
        onDispose { session.setOverlayBackInterceptor(null) }
    }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { infoFocus.requestFocus() }
    }

    // After the initial Available screen, follow the action the user just
    // triggered: cancel -> install -> permission -> retry/source.
    LaunchedEffect(state) {
        if (firstState) {
            firstState = false
            return@LaunchedEffect
        }
        delay(70)
        val target = when (val current = state) {
            is UpdateUiState.Downloading -> cancelFocus
            is UpdateUiState.Ready -> installFocus
            is UpdateUiState.NeedsPermission -> permissionFocus
            is UpdateUiState.Error ->
                if (current.kind == UpdateErrorKind.INSTALL) installFocus else sourceFocus
            else -> sourceFocus
        }
        runCatching { target.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoPalette.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { close() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 680.dp)
                .heightIn(min = 330.dp, max = 420.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(DuoPalette.SurfaceHigh)
                .border(1.dp, DuoPalette.Outline, RoundedCornerShape(18.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                UpdateInfoPane(
                    update = update,
                    state = state,
                    scrollState = scrollState,
                    focusRequester = infoFocus,
                    onScrollPage = { delta ->
                        scope.launch {
                            scrollState.animateScrollBy(delta * density.density)
                        }
                    },
                    onRight = {
                        val target = when (val current = state) {
                            is UpdateUiState.Downloading -> cancelFocus
                            is UpdateUiState.Ready -> installFocus
                            is UpdateUiState.NeedsPermission -> permissionFocus
                            is UpdateUiState.Error ->
                                if (current.kind == UpdateErrorKind.INSTALL) installFocus else sourceFocus
                            else -> sourceFocus
                        }
                        runCatching { target.requestFocus() }
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                UpdateActionPane(
                    update = update,
                    state = state,
                    cnUrl = cnUrl,
                    githubUrl = githubUrl,
                    cnFocus = cnFocus,
                    githubFocus = githubFocus,
                    infoFocus = infoFocus,
                    cancelFocus = cancelFocus,
                    installFocus = installFocus,
                    permissionFocus = permissionFocus,
                    retryFocus = retryFocus,
                    viewModel = viewModel,
                    modifier = Modifier.width(184.dp).fillMaxHeight(),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "↑↓ 滚动更新内容 · → 切换操作 · ${gamepadStyle.backLabel} 关闭",
                color = DuoPalette.Tertiary,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun UpdateInfoPane(
    update: UpdateInfo,
    state: UpdateUiState,
    scrollState: androidx.compose.foundation.ScrollState,
    focusRequester: FocusRequester,
    onScrollPage: (Float) -> Unit,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusProperties {
                left = FocusRequester.Cancel
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
            }
            .duoFocusRing(shape)
            .clip(shape)
            .background(DuoPalette.Surface)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        onScrollPage(-120f)
                        true
                    }
                    Key.DirectionDown -> {
                        onScrollPage(120f)
                        true
                    }
                    Key.DirectionRight,
                    Key.DirectionCenter,
                    Key.Enter,
                    Key.NumPadEnter -> {
                        onRight()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .verticalScroll(scrollState)
            .padding(14.dp),
    ) {
        Text(
            text = "发现新版本",
            color = DuoPalette.Tertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "v${update.versionName}",
            color = DuoPalette.Text,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "当前 v${BuildConfig.VERSION_NAME}" +
                if (update.channel.isNotBlank() && !update.channel.equals("stable", true)) {
                    " · 测试版"
                } else {
                    ""
                },
            color = DuoPalette.Secondary,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "${formatBytes(update.size)}" +
                if (update.publishedAt.isNotBlank()) " · ${shortDate(update.publishedAt)}" else "",
            color = DuoPalette.Tertiary,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DuoPalette.Outline)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "更新内容",
            color = DuoPalette.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = update.notes.ifBlank { "本次更新未提供说明。" },
            color = DuoPalette.Secondary,
            fontSize = 11.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "SHA-256\n${update.sha256}",
            color = DuoPalette.Tertiary,
            fontSize = 8.sp,
            lineHeight = 12.sp,
        )
        if (state is UpdateUiState.Installing) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "正在打开系统安装器…",
                color = DuoPalette.Pink,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun UpdateActionPane(
    update: UpdateInfo,
    state: UpdateUiState,
    cnUrl: String?,
    githubUrl: String?,
    cnFocus: FocusRequester,
    githubFocus: FocusRequester,
    infoFocus: FocusRequester,
    cancelFocus: FocusRequester,
    installFocus: FocusRequester,
    permissionFocus: FocusRequester,
    retryFocus: FocusRequester,
    viewModel: UpdateViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = when (val current = state) {
                is UpdateUiState.Downloading -> "下载中"
                is UpdateUiState.Ready -> "安装更新"
                is UpdateUiState.NeedsPermission -> "安装权限"
                is UpdateUiState.Installing -> "正在安装"
                is UpdateUiState.Error ->
                    if (current.kind == UpdateErrorKind.INSTALL) "安装失败" else "下载源"
                else -> "选择下载源"
            },
            color = DuoPalette.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))

        when (val current = state) {
            is UpdateUiState.Downloading -> DownloadProgressPane(
                state = current,
                infoFocus = infoFocus,
                cancelFocus = cancelFocus,
                onCancel = { viewModel.cancelDownload() },
            )
            is UpdateUiState.Ready -> ReadyPane(
                state = current,
                infoFocus = infoFocus,
                installFocus = installFocus,
                onInstall = { viewModel.install() },
            )
            is UpdateUiState.NeedsPermission -> PermissionPane(
                infoFocus = infoFocus,
                permissionFocus = permissionFocus,
                retryFocus = retryFocus,
                onOpenSettings = { viewModel.openInstallPermissionSettings() },
                onRetry = { viewModel.install() },
            )
            is UpdateUiState.Installing -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = DuoPalette.Pink,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("正在打开系统安装器…", color = DuoPalette.Secondary, fontSize = 10.sp)
                }
            }
            is UpdateUiState.Error -> {
                Text(
                    text = current.message,
                    color = DuoPalette.Error,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
                Spacer(Modifier.height(10.dp))
                if (current.kind == UpdateErrorKind.INSTALL) {
                    UpdateActionButton(
                        text = "重试安装",
                        primary = true,
                        focusRequester = installFocus,
                        infoFocus = infoFocus,
                        onClick = { viewModel.install() },
                    )
                    Spacer(Modifier.height(8.dp))
                    UpdateActionButton(
                        text = "重新下载",
                        primary = false,
                        focusRequester = cancelFocus,
                        infoFocus = infoFocus,
                        onClick = { viewModel.dismissError() },
                    )
                } else {
                    SourceButtons(
                        cnUrl = cnUrl,
                        githubUrl = githubUrl,
                        cnFocus = cnFocus,
                        githubFocus = githubFocus,
                        infoFocus = infoFocus,
                        onCn = { viewModel.startDownload(UpdateSource.CN) },
                        onGithub = { viewModel.startDownload(UpdateSource.GITHUB) },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("国内源失败时请改用 GitHub。", color = DuoPalette.Tertiary, fontSize = 9.sp)
                }
            }
            else -> {
                SourceButtons(
                    cnUrl = cnUrl,
                    githubUrl = githubUrl,
                    cnFocus = cnFocus,
                    githubFocus = githubFocus,
                    infoFocus = infoFocus,
                    onCn = { viewModel.startDownload(UpdateSource.CN) },
                    onGithub = { viewModel.startDownload(UpdateSource.GITHUB) },
                )
                Spacer(Modifier.height(10.dp))
                Text("国内源优先；GitHub 作为备用。", color = DuoPalette.Tertiary, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "v${update.versionName}",
            color = DuoPalette.Tertiary,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun SourceButtons(
    cnUrl: String?,
    githubUrl: String?,
    cnFocus: FocusRequester,
    githubFocus: FocusRequester,
    infoFocus: FocusRequester,
    onCn: () -> Unit,
    onGithub: () -> Unit,
) {
    UpdateSourceButton(
        title = UpdateSource.CN.title,
        subtitle = if (cnUrl != null) UpdateSource.CN.subtitle else "当前清单未提供",
        primary = true,
        enabled = cnUrl != null,
        focusRequester = cnFocus,
        infoFocus = infoFocus,
        otherFocus = githubFocus,
        onClick = onCn,
    )
    Spacer(Modifier.height(8.dp))
    UpdateSourceButton(
        title = UpdateSource.GITHUB.title,
        subtitle = if (githubUrl != null) UpdateSource.GITHUB.subtitle else "当前清单未提供",
        primary = false,
        enabled = githubUrl != null,
        focusRequester = githubFocus,
        infoFocus = infoFocus,
        otherFocus = cnFocus,
        onClick = onGithub,
    )
}

@Composable
private fun UpdateSourceButton(
    title: String,
    subtitle: String,
    primary: Boolean,
    enabled: Boolean,
    focusRequester: FocusRequester,
    infoFocus: FocusRequester,
    otherFocus: FocusRequester,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .focusRequester(focusRequester)
            .focusProperties {
                left = infoFocus
                up = otherFocus
                down = otherFocus
            }
            .duoFocusRing(shape)
            .clip(shape)
            .background(if (primary) DuoPalette.Pink else DuoPalette.Surface)
            .border(
                1.dp,
                if (primary) Color.Transparent else DuoPalette.Outline,
                shape,
            )
            .duoClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = when {
                    primary -> DuoPalette.OnPink
                    enabled -> DuoPalette.Text
                    else -> DuoPalette.Tertiary
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = when {
                    primary -> DuoPalette.OnPink.copy(alpha = 0.72f)
                    else -> DuoPalette.Tertiary
                },
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DownloadProgressPane(
    state: UpdateUiState.Downloading,
    infoFocus: FocusRequester,
    cancelFocus: FocusRequester,
    onCancel: () -> Unit,
) {
    Text(
        text = state.source.title,
        color = DuoPalette.Pink,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(10.dp))
    UpdateProgressBar(
        fraction = if (state.total > 0) state.downloaded.toFloat() / state.total else 0f,
    )
    Spacer(Modifier.height(7.dp))
    Text(
        text = "${percent(state.downloaded, state.total)} · " +
            "${formatBytes(state.downloaded)} / ${formatBytes(state.total)}",
        color = DuoPalette.Secondary,
        fontSize = 9.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = formatSpeed(state.speedBytesPerSec),
        color = DuoPalette.Tertiary,
        fontSize = 9.sp,
    )
    Spacer(Modifier.height(14.dp))
    UpdateActionButton(
        text = "取消下载",
        primary = false,
        focusRequester = cancelFocus,
        infoFocus = infoFocus,
        onClick = onCancel,
    )
}

@Composable
private fun ReadyPane(
    state: UpdateUiState.Ready,
    infoFocus: FocusRequester,
    installFocus: FocusRequester,
    onInstall: () -> Unit,
) {
    Text(
        text = "安装包已下载并校验通过",
        color = DuoPalette.Secondary,
        fontSize = 10.sp,
        lineHeight = 15.sp,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = formatBytes(state.file.length()),
        color = DuoPalette.Tertiary,
        fontSize = 9.sp,
    )
    Spacer(Modifier.height(14.dp))
    UpdateActionButton(
        text = "安装更新",
        primary = true,
        focusRequester = installFocus,
        infoFocus = infoFocus,
        onClick = onInstall,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "安装会重启应用；登录、设置和历史会保留。",
        color = DuoPalette.Tertiary,
        fontSize = 9.sp,
        lineHeight = 14.sp,
    )
}

@Composable
private fun PermissionPane(
    infoFocus: FocusRequester,
    permissionFocus: FocusRequester,
    retryFocus: FocusRequester,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    Text(
        text = "需要允许 Bili-Duo 安装未知应用",
        color = DuoPalette.Secondary,
        fontSize = 10.sp,
        lineHeight = 15.sp,
    )
    Spacer(Modifier.height(14.dp))
    UpdateActionButton(
        text = "打开权限设置",
        primary = true,
        focusRequester = permissionFocus,
        infoFocus = infoFocus,
        onClick = onOpenSettings,
    )
    Spacer(Modifier.height(8.dp))
    UpdateActionButton(
        text = "已允许，安装",
        primary = false,
        focusRequester = retryFocus,
        infoFocus = infoFocus,
        onClick = onRetry,
    )
}

@Composable
private fun UpdateActionButton(
    text: String,
    primary: Boolean,
    focusRequester: FocusRequester,
    infoFocus: FocusRequester,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .focusRequester(focusRequester)
            .focusProperties {
                left = infoFocus
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
            }
            .duoFocusRing(shape)
            .clip(shape)
            .background(if (primary) DuoPalette.Pink else DuoPalette.Surface)
            .border(
                1.dp,
                if (primary) Color.Transparent else DuoPalette.Outline,
                shape,
            )
            .duoClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (primary) DuoPalette.OnPink else DuoPalette.Text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun UpdateProgressBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(DuoPalette.SurfaceHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(DuoPalette.Pink),
        )
    }
}

private fun percent(downloaded: Long, total: Long): String =
    if (total <= 0L) "0%" else "${((downloaded * 100L) / total).coerceIn(0L, 100L)}%"

private fun shortDate(value: String): String =
    value.take(10)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format(java.util.Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatSpeed(bytesPerSec: Long): String =
    if (bytesPerSec <= 0L) "--" else "${formatBytes(bytesPerSec)}/s"
