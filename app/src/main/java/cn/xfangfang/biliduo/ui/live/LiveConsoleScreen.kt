@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package cn.xfangfang.biliduo.ui.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cn.xfangfang.biliduo.data.api.BilibiliApi
import cn.xfangfang.biliduo.data.live.LiveChatClient
import cn.xfangfang.biliduo.data.model.LiveChatEvent
import cn.xfangfang.biliduo.data.model.LiveChatMessage
import cn.xfangfang.biliduo.data.model.LiveChatState
import cn.xfangfang.biliduo.data.model.LiveRoomDetail
import cn.xfangfang.biliduo.data.model.QualityOption
import cn.xfangfang.biliduo.data.model.LIVE_CHAT_MAX_LENGTH
import cn.xfangfang.biliduo.data.model.liveChatLength
import cn.xfangfang.biliduo.data.model.validateLiveChatText
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import cn.xfangfang.biliduo.duo.DuoMode
import cn.xfangfang.biliduo.duo.DuoOverlay
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.theme.DuoTheme
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.ui.duo.DuoTextFieldGate
import cn.xfangfang.biliduo.ui.duo.rememberDuoTextFieldState
import cn.xfangfang.biliduo.ui.player.PlayerController
import cn.xfangfang.biliduo.ui.player.PlayerUiState
import cn.xfangfang.biliduo.ui.player.PlayerViewModel
import cn.xfangfang.biliduo.util.fixPic
import cn.xfangfang.biliduo.util.formatCount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LiveBg: Color @Composable get() = DuoTheme.colors.bg
private val LiveSurface: Color @Composable get() = DuoTheme.colors.surface1
private val LiveSurfaceHigh: Color @Composable get() = DuoTheme.colors.surface3
private val LivePink: Color @Composable get() = DuoTheme.colors.pinkInk
private val LiveText: Color @Composable get() = DuoTheme.colors.text
private val LiveTextPrimary: Color @Composable get() = DuoTheme.colors.text
private val LiveOnPink: Color @Composable get() = DuoTheme.colors.onPink
private val LiveTextSecondary: Color @Composable get() = DuoTheme.colors.secondary
private val LiveTextTertiary: Color @Composable get() = DuoTheme.colors.tertiary
private val LiveDivider: Color @Composable get() = DuoTheme.colors.outline
private val LiveUsername: Color @Composable get() = DuoTheme.colors.blue
private val LiveMedal: Color @Composable get() = DuoTheme.colors.warning

private const val MAX_CHAT_MESSAGES = 240
private const val CHAT_TAB = 0
private const val INFO_TAB = 1

/**
 * Bottom-screen live console, matching the VOD console layout:
 * a fixed 272dp control column plus a two-tab panel (live chat / room info).
 *
 * Live chat is received over Bilibili's WebSocket protocol. Sending is a
 * separate REST call; it is disabled when not logged in.
 */
@Composable
fun LiveConsoleScreen(
    roomId: Long,
    controller: PlayerController,
    vm: PlayerViewModel,
    session: DuoSessionViewModel,
    onBack: () -> Unit,
) {
    val ui by vm.uiState.collectAsState()
    val playback by controller.state.collectAsState()
    val overlay by session.overlay.collectAsState()
    val focusEpoch by session.focusEpoch.collectAsState()
    val scope = rememberCoroutineScope()
    val repository = remember { BilibiliRepository() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var selectedTab by remember { mutableIntStateOf(CHAT_TAB) }
    var unread by remember { mutableIntStateOf(0) }
    var messages by remember(roomId) { mutableStateOf<List<LiveChatMessage>>(emptyList()) }
    // Fetched once over HTTP; kept out of LiveChatStore so the top-screen
    // overlay never replays old messages as if they just arrived.
    var historyMessages by remember(roomId) { mutableStateOf<List<LiveChatMessage>>(emptyList()) }
    val visibleMessages = remember(historyMessages, messages) { historyMessages + messages }
    var chatState by remember(roomId) { mutableStateOf(LiveChatState.CONNECTING) }
    var draft by remember(roomId) { mutableStateOf("") }
    var sending by remember(roomId) { mutableStateOf(false) }
    var sendError by remember(roomId) { mutableStateOf<String?>(null) }
    var isFollowed by remember(roomId) { mutableStateOf<Boolean?>(null) }
    var followBusy by remember(roomId) { mutableStateOf(false) }
    var followError by remember(roomId) { mutableStateOf<String?>(null) }
    var observedOverlay by remember { mutableStateOf<DuoOverlay?>(null) }
    var rightPaneFocused by remember { mutableStateOf(false) }
    val rightPaneFocusedState by rememberUpdatedState(rightPaneFocused)
    var initialFocusApplied by remember { mutableStateOf(false) }
    val currentTab by rememberUpdatedState(selectedTab)

    val playFocus = remember { FocusRequester() }
    val refreshFocus = remember { FocusRequester() }
    val qualityFocus = remember { FocusRequester() }
    val volumeFocus = remember { FocusRequester() }
    val tabFocus = remember { List(2) { FocusRequester() } }
    val qualityRowFocus = remember { FocusRequester() }
    val qualityCloseFocus = remember { FocusRequester() }
    val danmakuSettingsFocus = remember { FocusRequester() }
    val danmakuSettingsFirstFocus = remember { FocusRequester() }
    val danmakuSettingsCloseFocus = remember { FocusRequester() }
    var qualityOpener by remember { mutableStateOf<FocusRequester?>(null) }
    var danmakuSettingsOpener by remember { mutableStateOf<FocusRequester?>(null) }

    val detail = ui.liveDetail
    val qualities = ui.liveQualities
    val selectedQuality = ui.currentQuality
    val selectedIndex = qualities
        .indexOfFirst { it.quality == selectedQuality?.quality }
        .coerceAtLeast(0)
    val loggedIn = BilibiliApi.loginCookies.isNotBlank()

    val chatClient = remember(roomId) {
        LiveChatClient(
            roomId = roomId,
            scope = scope,
            repository = repository,
            onEvent = { event ->
                when (event) {
                    is LiveChatEvent.Message -> {
                        val next = (messages + event.message).takeLast(MAX_CHAT_MESSAGES)
                        messages = next
                        LiveChatStore.publish(roomId, next)
                        if (currentTab != CHAT_TAB) {
                            unread = (unread + 1).coerceAtMost(99)
                        }
                    }
                    is LiveChatEvent.DeleteSuperChat -> {
                        val next = messages.filterNot {
                            it.isSuperChat && it.superChatId == event.id
                        }
                        messages = next
                        LiveChatStore.publish(roomId, next)
                    }
                }
            },
            onState = { chatState = it },
        )
    }

    LaunchedEffect(roomId) {
        LiveChatStore.publish(roomId, messages)
    }
    LaunchedEffect(roomId) {
        repository.getLiveHistoryDanmaku(roomId)
            .onSuccess { history ->
                if (history.isNotEmpty()) historyMessages = history
            }
    }
    DisposableEffect(roomId) {
        onDispose { LiveChatStore.clear(roomId) }
    }

    fun leave() {
        chatClient.stop()
        controller.stop()
        vm.reset()
        session.onPlayerExited()
        onBack()
    }

    fun switchTab(delta: Int) {
        selectedTab = (selectedTab + delta + 2) % 2
        keyboard?.hide()
        focusManager.clearFocus()
        scope.launch {
            delay(80L)
            runCatching { tabFocus[selectedTab].requestFocus() }
        }
    }

    fun sendChat() {
        if (sending) return
        val text = draft.trim()
        val error = validateLiveChatText(text)
        if (error != null) {
            sendError = error
            return
        }
        if (!loggedIn) {
            sendError = "请先登录后再发送弹幕"
            return
        }
        sending = true
        sendError = null
        scope.launch {
            repository.sendLiveDanmaku(roomId, text)
                .onSuccess {
                    draft = ""
                    sending = false
                }
                .onFailure { failure ->
                    sending = false
                    sendError = failure.message ?: "发送失败"
                }
        }
    }

    fun toggleFollow() {
        val uid = detail?.uid ?: return
        if (!loggedIn || followBusy) return
        followBusy = true
        followError = null
        val target = if (isFollowed == true) 2 else 1
        scope.launch {
            repository.modifyRelation(uid, target)
                .onSuccess {
                    isFollowed = target == 1
                    followBusy = false
                }
                .onFailure { failure ->
                    followBusy = false
                    followError = failure.message ?: "关注操作失败"
                }
        }
    }

    BackHandler { if (overlay != null) session.closeOverlay() else leave() }

    LaunchedEffect(Unit) {
        session.onPlayerEntered()
        vm.loadLive(roomId)
    }

    LaunchedEffect(roomId) {
        delay(5_000L)
        while (true) {
            vm.refreshLiveDetail()
            delay(30_000L)
        }
    }

    DisposableEffect(roomId) {
        chatClient.start()
        onDispose { chatClient.stop() }
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.stop()
            vm.reset()
            session.onPlayerExited()
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == CHAT_TAB) unread = 0
    }

    LaunchedEffect(detail?.uid, loggedIn) {
        val uid = detail?.uid ?: return@LaunchedEffect
        isFollowed = null
        followError = null
        if (loggedIn && uid > 0L) {
            repository.checkRelation(uid).onSuccess { isFollowed = it }
        }
    }

    LaunchedEffect(focusEpoch) {
        delay(150L)
        if (session.mode.value == DuoMode.PLAYER && overlay == null) {
            if (runCatching { playFocus.requestFocus() }.isSuccess) {
                initialFocusApplied = true
            }
        }
    }
    LaunchedEffect(playback.isActive) {
        if (!initialFocusApplied && playback.isActive) {
            delay(150L)
            if (runCatching { playFocus.requestFocus() }.isSuccess) {
                initialFocusApplied = true
            }
        }
    }
    DisposableEffect(Unit) {
        session.setFocusBackInterceptor {
            if (rightPaneFocusedState) {
                scope.launch {
                    delay(80L)
                    runCatching { playFocus.requestFocus() }
                }
                true
            } else {
                false
            }
        }
        onDispose { session.setFocusBackInterceptor(null) }
    }

    LaunchedEffect(overlay, focusEpoch, qualities.size) {
        val previous = observedOverlay
        observedOverlay = overlay
        when {
            overlay == DuoOverlay.QUALITY -> {
                delay(140L)
                runCatching {
                    if (qualities.isEmpty()) {
                        qualityCloseFocus.requestFocus()
                    } else {
                        qualityRowFocus.requestFocus()
                    }
                }
            }
            overlay == DuoOverlay.LIVE_DANMAKU_SETTINGS -> {
                delay(140L)
                runCatching { danmakuSettingsFirstFocus.requestFocus() }
            }
            previous == DuoOverlay.QUALITY && overlay == null -> {
                delay(300L)
                runCatching { (qualityOpener ?: playFocus).requestFocus() }
            }
            previous == DuoOverlay.LIVE_DANMAKU_SETTINGS && overlay == null -> {
                delay(300L)
                runCatching { (danmakuSettingsOpener ?: playFocus).requestFocus() }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiveBg)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || overlay != null) return@onPreviewKeyEvent false
                when (event.key.nativeKeyCode) {
                    android.view.KeyEvent.KEYCODE_BUTTON_L1 -> {
                        switchTab(-1)
                        true
                    }
                    android.view.KeyEvent.KEYCODE_BUTTON_R1 -> {
                        switchTab(1)
                        true
                    }
                    android.view.KeyEvent.KEYCODE_BUTTON_X -> {
                        vm.toggleLiveDanmaku()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_BUTTON_START,
                    android.view.KeyEvent.KEYCODE_MENU -> {
                        qualityOpener = null
                        danmakuSettingsOpener = null
                        session.openOverlay(DuoOverlay.LIVE_DANMAKU_SETTINGS)
                        true
                    }
                    else -> false
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LiveLeftPane(
                ui = ui,
                isPlaying = playback.isPlaying,
                isBuffering = playback.isBuffering,
                volume = playback.volume,
                error = ui.error ?: playback.error,
                playFocus = playFocus,
                refreshFocus = refreshFocus,
                qualityFocus = qualityFocus,
                volumeFocus = volumeFocus,
                tabFocus = tabFocus,
                selectedTab = selectedTab,
                onBack = ::leave,
                onTogglePlay = { controller.togglePlay() },
                onRefresh = { vm.refreshLive() },
                onQuality = {
                    qualityOpener = qualityFocus
                    session.openOverlay(DuoOverlay.QUALITY)
                },
                onVolume = {
                    controller.setVolume(
                        if (playback.volume >= 0.99f) 0f
                        else (playback.volume + 0.25f).coerceAtMost(1f)
                    )
                },
                onToggleDanmaku = { vm.toggleLiveDanmaku() },
                danmakuSettingsFocus = danmakuSettingsFocus,
                onDanmakuSettings = {
                    danmakuSettingsOpener = danmakuSettingsFocus
                    session.openOverlay(DuoOverlay.LIVE_DANMAKU_SETTINGS)
                },
                onRetry = { vm.refreshLive() },
            )

            LiveRightPane(
                selectedTab = selectedTab,
                unread = unread,
                detail = detail,
                isLoading = ui.isLoading,
                chatState = chatState,
                messages = visibleMessages,
                draft = draft,
                sending = sending,
                sendError = sendError,
                loggedIn = loggedIn,
                isFollowed = isFollowed,
                followBusy = followBusy,
                followError = followError,
                tabFocus = tabFocus,
                playFocus = playFocus,
                onTab = { tab ->
                    selectedTab = tab
                    keyboard?.hide()
                },
                onDraft = {
                    if (liveChatLength(it) <= LIVE_CHAT_MAX_LENGTH) {
                        draft = it
                        sendError = null
                    }
                },
                onSend = ::sendChat,
                onToggleFollow = ::toggleFollow,
                onFocusChanged = { rightPaneFocused = it },
                session = session,
            )
        }

        if (overlay == DuoOverlay.QUALITY) {
            LiveQualityOverlay(
                qualities = qualities,
                selectedQuality = selectedQuality,
                selectedIndex = selectedIndex,
                selectedFocus = qualityRowFocus,
                closeFocus = qualityCloseFocus,
                onDismiss = { session.closeOverlay() },
                onSelect = { quality ->
                    session.closeOverlay()
                    if (quality.quality != selectedQuality?.quality) {
                        vm.selectLiveQuality(quality.quality)
                    }
                },
            )
        }

        if (overlay == DuoOverlay.LIVE_DANMAKU_SETTINGS) {
            LiveDanmakuSettingsOverlay(
                ui = ui,
                firstFocus = danmakuSettingsFirstFocus,
                closeFocus = danmakuSettingsCloseFocus,
                onDismiss = { session.closeOverlay() },
                onToggleEnabled = { vm.setLiveDanmakuEnabled(!ui.liveDanmakuEnabled) },
                onCycleOpacity = {
                    vm.setLiveDanmakuOpacity(
                        nextLiveDanmakuValue(ui.liveDanmakuOpacity, liveDanmakuOpacityOptions)
                    )
                },
                onCycleSpeed = {
                    vm.setLiveDanmakuSpeed(
                        nextLiveDanmakuValue(ui.liveDanmakuSpeed, liveDanmakuSpeedOptions)
                    )
                },
                onCycleSize = {
                    vm.setLiveDanmakuTextSize(
                        nextLiveDanmakuValue(ui.liveDanmakuTextSize, liveDanmakuSizeOptions)
                    )
                },
                onCycleArea = {
                    vm.setLiveDanmakuArea(
                        nextLiveDanmakuValue(ui.liveDanmakuArea, liveDanmakuAreaOptions)
                    )
                },
            )
        }
    }
}

@Composable
private fun LiveLeftPane(
    ui: PlayerUiState,
    isPlaying: Boolean,
    isBuffering: Boolean,
    volume: Float,
    error: String?,
    playFocus: FocusRequester,
    refreshFocus: FocusRequester,
    qualityFocus: FocusRequester,
    volumeFocus: FocusRequester,
    tabFocus: List<FocusRequester>,
    selectedTab: Int,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onRefresh: () -> Unit,
    onQuality: () -> Unit,
    onVolume: () -> Unit,
    onToggleDanmaku: () -> Unit,
    danmakuSettingsFocus: FocusRequester,
    onDanmakuSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    val detail = ui.liveDetail
    val live = detail?.isLive == true
    val title = detail?.title?.takeIf { it.isNotBlank() }
        ?: if (ui.isLoading) "正在连接直播间…" else "直播间"
    val meta = buildString {
        val anchor = detail?.anchorName.orEmpty()
        if (anchor.isNotBlank()) append(anchor)
        val online = detail?.online ?: 0L
        if (online > 0) {
            if (isNotEmpty()) append(" · ")
            append("${formatCount(online)}人气")
        }
        val area = detail?.displayArea.orEmpty()
        if (area.isNotBlank()) {
            if (isNotEmpty()) append(" · ")
            append(area)
        }
    }.ifBlank { "Bilibili 直播" }
    val chipDown = Modifier.focusProperties { down = tabFocus[selectedTab] }

    Column(
        Modifier
            .width(272.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .duoFocusRing(CircleShape)
                    .clip(CircleShape)
                    .duoClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "退出直播间",
                    tint = LiveTextSecondary,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(5.dp))
            Text(
                text = title,
                color = LiveText,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            LiveStatusPill(loading = ui.isLoading, live = live)
        }

        Spacer(Modifier.height(5.dp))
        Text(
            text = meta,
            color = LiveTextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(8.dp))
        LiveCoverCard(detail = detail)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                LiveTransportButton(
                    icon = Icons.Filled.Refresh,
                    description = "刷新直播",
                    focusRequester = refreshFocus,
                    enabled = !ui.isLoading,
                    onClick = onRefresh,
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .duoFocusRing(CircleShape)
                        .clip(CircleShape)
                        .background(LivePink)
                        .focusRequester(playFocus)
                        .duoClickable { onTogglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isBuffering || ui.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = LiveOnPink,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = LiveOnPink,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
            // Keep the third column empty so the two round buttons above and the
            // three chips below share one optical grid.
            Spacer(Modifier.weight(1f))
        }

        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            LiveErrorBanner(message = error, onRetry = onRetry)
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(46.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveChip(
                text = selectedQualityText(ui),
                modifier = Modifier
                    .weight(1f)
                    .then(chipDown)
                    .focusRequester(qualityFocus),
                onClick = onQuality,
            )
            LiveChip(
                text = "音量 ${(volume * 100).toInt()}%",
                modifier = Modifier
                    .weight(1f)
                    .then(chipDown)
                    .focusRequester(volumeFocus),
                onClick = onVolume,
            )
            Box(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiveChip(
                        text = if (ui.liveDanmakuEnabled) "弹幕 开" else "弹幕 关",
                        modifier = Modifier
                            .weight(1f)
                            .focusProperties { down = danmakuSettingsFocus },
                        onClick = onToggleDanmaku,
                    )
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .focusRequester(danmakuSettingsFocus)
                            .focusProperties {
                                up = playFocus
                                down = tabFocus[selectedTab]
                            }
                            .duoFocusRing(RoundedCornerShape(6.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .background(LiveSurfaceHigh)
                            .duoClickable(onClick = onDanmakuSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "直播弹幕设置",
                            tint = LiveTextSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(LiveDivider))
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(LiveSurfaceHigh),
            ) {
                val face = detail?.anchorFace.orEmpty()
                if (face.isNotBlank()) {
                    AsyncImage(
                        model = fixPic(face),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = detail?.anchorName?.ifBlank { "未知主播" } ?: "未知主播",
                    color = LiveTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail?.anchorOfficial?.takeIf { it.isNotBlank() }
                        ?: detail?.displayArea.orEmpty().ifBlank { "Bilibili 直播" },
                    color = LiveTextTertiary,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = "关注 ${formatCount(detail?.attention ?: 0L)}",
                color = LiveTextSecondary,
                fontSize = 9.sp,
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Y 或弹幕 chip 开关 · START 菜单",
            color = LiveTextTertiary,
            fontSize = 8.sp,
        )
    }
}

@Composable
private fun RowScope.LiveRightPane(
    selectedTab: Int,
    unread: Int,
    detail: LiveRoomDetail?,
    isLoading: Boolean,
    chatState: LiveChatState,
    messages: List<LiveChatMessage>,
    draft: String,
    sending: Boolean,
    sendError: String?,
    loggedIn: Boolean,
    isFollowed: Boolean?,
    followBusy: Boolean,
    followError: String?,
    tabFocus: List<FocusRequester>,
    playFocus: FocusRequester,
    onTab: (Int) -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onToggleFollow: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    session: DuoSessionViewModel,
) {
    val tabUp = Modifier.focusProperties { up = playFocus }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .onFocusChanged { onFocusChanged(it.hasFocus) }
            .clip(RoundedCornerShape(12.dp))
            .background(LiveSurface)
            .border(1.dp, LiveDivider, RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(start = 12.dp, top = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            LiveTab(
                text = "直播评论",
                count = unread,
                selected = selectedTab == CHAT_TAB,
                modifier = tabUp,
                focusRequester = tabFocus[CHAT_TAB],
                onClick = { onTab(CHAT_TAB) },
            )
            LiveTab(
                text = "直播信息",
                selected = selectedTab == INFO_TAB,
                modifier = tabUp,
                focusRequester = tabFocus[INFO_TAB],
                onClick = { onTab(INFO_TAB) },
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LiveDivider))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                CHAT_TAB -> LiveChatTab(
                    messages = messages,
                    chatState = chatState,
                    draft = draft,
                    sending = sending,
                    sendError = sendError,
                    loggedIn = loggedIn,
                    onDraft = onDraft,
                    onSend = onSend,
                    playFocus = playFocus,
                    session = session,
                )
                else -> LiveInfoTab(
                    detail = detail,
                    isLoading = isLoading,
                    loggedIn = loggedIn,
                    isFollowed = isFollowed,
                    followBusy = followBusy,
                    followError = followError,
                    onToggleFollow = onToggleFollow,
                )
            }
        }
    }
}

@Composable
private fun LiveChatTab(
    messages: List<LiveChatMessage>,
    chatState: LiveChatState,
    draft: String,
    sending: Boolean,
    sendError: String?,
    loggedIn: Boolean,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    playFocus: FocusRequester,
    session: DuoSessionViewModel,
) {
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldFocus = remember { FocusRequester() }
    var fieldFocused by remember { mutableStateOf(false) }
    var scrolledUp by remember { mutableStateOf(false) }
    val currentDraft by rememberUpdatedState(draft)
    val chatMessages = remember(messages) { messages.filterNot { it.isSuperChat } }
    var nowSec by remember { mutableStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(Unit) {
        while (true) {
            nowSec = System.currentTimeMillis() / 1000L
            delay(1_000L)
        }
    }
    val activeSuperChat = messages.asReversed().firstOrNull { message ->
        if (!message.isSuperChat) return@firstOrNull false
        when {
            message.endTimeSec > 0L -> message.endTimeSec > nowSec
            message.timestampSec > 0L -> nowSec - message.timestampSec < 60L
            else -> true
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                scrolledUp = listState.canScrollForward
            }
        }
    }

    val lastId = chatMessages.lastOrNull()?.id
    LaunchedEffect(lastId) {
        if (chatMessages.isNotEmpty() && !scrolledUp) {
            runCatching { listState.animateScrollToItem(chatMessages.lastIndex) }
        }
    }

    DisposableEffect(fieldFocused) {
        if (fieldFocused) {
            session.setBackInterceptor {
                keyboard?.hide()
                runCatching { playFocus.requestFocus() }
                true
            }
        }
        onDispose {
            session.setBackInterceptor(null)
            if (fieldFocused) keyboard?.hide()
        }
    }

    Column(Modifier.fillMaxSize()) {
        LiveConnectionBar(
            state = chatState,
            sendError = sendError,
            draftLength = liveChatLength(currentDraft),
            showCounter = currentDraft.isNotEmpty(),
        )

        activeSuperChat?.let { sc ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                LiveSuperChatCard(sc)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (chatMessages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = when (chatState) {
                            LiveChatState.CONNECTED -> "弹幕已连接，等待第一条弹幕…"
                            LiveChatState.CONNECTING -> "正在连接直播弹幕…"
                            LiveChatState.DISCONNECTED -> "弹幕连接断开，正在重连…"
                        },
                        color = LiveTextTertiary,
                        fontSize = 10.sp,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    items(chatMessages, key = { it.id }) { message ->
                        LiveChatLine(message)
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(LiveDivider))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveChatInput(
                value = draft,
                enabled = loggedIn && !sending,
                placeholder = if (loggedIn) "发一条弹幕…" else "登录后发弹幕…",
                focusRequester = fieldFocus,
                onFocusChanged = { fieldFocused = it },
                onValueChange = onDraft,
                onImeSend = onSend,
                modifier = Modifier.weight(1f),
            )
            LiveSendButton(
                enabled = loggedIn && !sending && validateLiveChatText(draft) == null,
                sending = sending,
                onClick = onSend,
            )
        }
    }
}

@Composable
private fun LiveConnectionBar(
    state: LiveChatState,
    sendError: String?,
    draftLength: Int,
    showCounter: Boolean,
) {
    val (label, color) = when (state) {
        LiveChatState.CONNECTED -> "弹幕已连接" to LivePink
        LiveChatState.CONNECTING -> "连接弹幕…" to LiveTextSecondary
        LiveChatState.DISCONNECTED -> "弹幕断开 · 重连中" to DuoTheme.colors.warning
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(4.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, color = color, fontSize = 8.sp, maxLines = 1)
        Spacer(Modifier.weight(1f))
        if (sendError != null) {
            Text(
                text = sendError,
                color = DuoTheme.colors.error,
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
        } else if (showCounter) {
            Text(
                text = "$draftLength/$LIVE_CHAT_MAX_LENGTH",
                color = if (draftLength > LIVE_CHAT_MAX_LENGTH) LivePink else LiveTextTertiary,
                fontSize = 8.sp,
            )
        }
    }
}

@Composable
private fun LiveChatLine(message: LiveChatMessage) {
    Text(
        text = buildAnnotatedString {
            if (!message.medal.isNullOrBlank()) {
                withStyle(
                    SpanStyle(
                        color = LiveMedal,
                        background = LiveMedal.copy(alpha = 0.12f),
                        fontSize = 8.sp,
                    )
                ) {
                    append("[${message.medal}] ")
                }
            }
            withStyle(
                SpanStyle(
                    color = if (message.isAdmin) LivePink else LiveUsername,
                    fontWeight = FontWeight.Medium,
                )
            ) {
                append(message.uname)
            }
            append("：")
            withStyle(SpanStyle(color = LiveTextPrimary)) {
                append(message.text)
            }
        },
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LiveSuperChatCard(message: LiveChatMessage) {
    val accent = Color(message.background ?: 0xFFFB7299)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.20f))
            .border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(LiveSurfaceHigh),
        ) {
            if (message.face.isNotBlank()) {
                AsyncImage(
                    model = fixPic(message.face),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.uname,
                    color = LiveTextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "SC ¥${message.price}",
                    color = accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = message.text,
                color = LiveTextPrimary,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LiveChatInput(
    value: String,
    enabled: Boolean,
    placeholder: String,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    onImeSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val fieldState = rememberDuoTextFieldState()
    val focusManager = LocalFocusManager.current
    DuoTextFieldGate(
        state = fieldState,
        focusRequester = focusRequester,
        modifier = modifier.height(38.dp),
        enabled = enabled,
        onGateFocusChanged = onFocusChanged,
    ) { editing, gateFocused, fieldModifier ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = editing && enabled,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .then(fieldModifier)
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChanged(it.isFocused)
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> {
                            focusManager.moveFocus(FocusDirection.Up)
                            true
                        }
                        Key.DirectionDown -> {
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        }
                        else -> false
                    }
                }
                .clip(RoundedCornerShape(7.dp))
                .background(LiveSurfaceHigh)
                .border(
                    width = 1.dp,
                    color = if (focused || gateFocused) LivePink else LiveDivider,
                    shape = RoundedCornerShape(7.dp),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        textStyle = TextStyle(color = LiveTextPrimary, fontSize = 11.sp),
        cursorBrush = SolidColor(LivePink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onImeSend() }),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = LiveTextTertiary, fontSize = 11.sp)
                }
                inner()
            }
        },
    )
    }
}

@Composable
private fun LiveSendButton(
    enabled: Boolean,
    sending: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .width(58.dp)
            .height(34.dp)
            .duoFocusRing(shape)
            .clip(shape)
            .background(if (enabled) LivePink else LiveSurfaceHigh)
            .duoClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (sending) "发送中" else "发送",
            color = if (enabled) LiveOnPink else LiveTextTertiary,
            fontSize = 10.sp,
            fontWeight = if (enabled) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun LiveInfoTab(
    detail: LiveRoomDetail?,
    isLoading: Boolean,
    loggedIn: Boolean,
    isFollowed: Boolean?,
    followBusy: Boolean,
    followError: String?,
    onToggleFollow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(LiveSurfaceHigh),
            ) {
                val face = detail?.anchorFace.orEmpty()
                if (face.isNotBlank()) {
                    AsyncImage(
                        model = fixPic(face),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = detail?.anchorName?.ifBlank { "未知主播" } ?: "加载中…",
                    color = LiveTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail?.anchorOfficial?.takeIf { it.isNotBlank() }
                        ?: "Bilibili 直播",
                    color = LiveTextTertiary,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LiveStatusPill(loading = isLoading, live = detail?.isLive == true)
        }

        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveFollowButton(
                loggedIn = loggedIn,
                isFollowed = isFollowed,
                busy = followBusy,
                onClick = onToggleFollow,
            )
            Spacer(Modifier.weight(1f))
            if (!followError.isNullOrBlank()) {
                Text(
                    text = followError,
                    color = DuoTheme.colors.error,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiveStatCard("人气", formatCount(detail?.online ?: 0L), Modifier.weight(1f))
            LiveStatCard("关注", formatCount(detail?.attention ?: 0L), Modifier.weight(1f))
            LiveStatCard("分区", detail?.areaName.orEmpty().ifBlank { "—" }, Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))
        LiveInfoRow("直播状态", if (detail?.isLive == true) "直播中" else if (detail == null) "加载中" else "已下播")
        LiveInfoRow("开播时间", formatLiveTime(detail?.liveTime))
        LiveInfoRow("直播间号", detail?.roomId?.toString() ?: "—")
        LiveInfoRow("分区", detail?.displayArea.orEmpty().ifBlank { "—" })

        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(LiveDivider))
        Spacer(Modifier.height(9.dp))
        Text("直播间简介", color = LiveTextTertiary, fontSize = 9.sp)
        Spacer(Modifier.height(5.dp))
        Text(
            text = detail?.description?.takeIf { it.isNotBlank() }
                ?: "主播还没有填写直播间简介。",
            color = LiveTextSecondary,
            fontSize = 10.sp,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun LiveFollowButton(
    loggedIn: Boolean,
    isFollowed: Boolean?,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val followed = isFollowed == true
    val enabled = loggedIn && !busy
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .height(30.dp)
            .duoFocusRing(shape)
            .clip(shape)
            .background(
                when {
                    !loggedIn || followed -> LiveSurfaceHigh
                    else -> LivePink
                }
            )
            .then(
                if (followed) Modifier.border(1.dp, LivePink.copy(alpha = 0.5f), shape)
                else Modifier
            )
            .duoClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when {
                busy -> "处理中…"
                !loggedIn -> "登录后关注"
                followed -> "已关注"
                else -> "关注"
            },
            color = when {
                !loggedIn -> LiveTextTertiary
                followed -> LivePink
                else -> LiveTextPrimary
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LiveStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(LiveSurfaceHigh)
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Text(label, color = LiveTextTertiary, fontSize = 8.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = LiveTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiveInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = LiveTextTertiary, fontSize = 9.sp, modifier = Modifier.width(56.dp))
        Text(
            text = value,
            color = LiveTextSecondary,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiveTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    count: Int = 0,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(IntrinsicSize.Max)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .duoFocusRing(RoundedCornerShape(4.dp))
            .duoClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = text,
                color = if (selected) LiveText else LiveTextSecondary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            if (count > 0) {
                Text(
                    text = if (count > 99) "99+" else count.toString(),
                    color = LivePink,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(start = 3.dp, bottom = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                .background(if (selected) LivePink else Color.Transparent),
        )
    }
}

@Composable
private fun LiveTransportButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .duoFocusRing(CircleShape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(CircleShape)
            .background(LiveSurfaceHigh)
            .duoClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) LiveTextPrimary else LiveTextTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun LiveChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .height(26.dp)
            .duoFocusRing(shape)
            .clip(shape)
            .background(LiveSurfaceHigh)
            .duoClickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = LiveTextSecondary, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun LiveStatusPill(loading: Boolean, live: Boolean) {
    val text = when {
        loading -> "连接中"
        live -> "直播中"
        else -> "已下播"
    }
    val color = when {
        loading -> LiveTextSecondary
        live -> LivePink
        else -> LiveTextTertiary
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = color,
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(9.dp),
            )
        } else {
            Box(Modifier.size(4.dp).clip(CircleShape).background(color))
        }
        Spacer(Modifier.width(4.dp))
        Text(text, color = color, fontSize = 8.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LiveCoverCard(detail: LiveRoomDetail?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(9.dp))
            .background(LiveSurfaceHigh),
    ) {
        val cover = detail?.displayCover.orEmpty()
        if (cover.isNotBlank()) {
            AsyncImage(
                model = fixPic(cover),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.52f)),
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (detail?.isLive == true) LivePink else LiveTextTertiary)
                .padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(4.dp).clip(CircleShape).background(LiveOnPink))
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (detail?.isLive == true) "直播中" else "已下播",
                color = LiveOnPink,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if ((detail?.online ?: 0L) > 0) {
            Text(
                text = "${formatCount(detail!!.online)} 人气",
                color = Color.White,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun LiveErrorBanner(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(DuoTheme.colors.error.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = DuoTheme.colors.error,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "重试",
            color = LiveTextPrimary,
            fontSize = 9.sp,
            modifier = Modifier
                .duoFocusRing(RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .duoClickable(onClick = onRetry)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun LiveDanmakuSettingsOverlay(
    ui: PlayerUiState,
    firstFocus: FocusRequester,
    closeFocus: FocusRequester,
    onDismiss: () -> Unit,
    onToggleEnabled: () -> Unit,
    onCycleOpacity: () -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleSize: () -> Unit,
    onCycleArea: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 300.dp, max = 380.dp)
                .heightIn(max = 400.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(LiveSurface)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .focusGroup()
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "直播弹幕设置",
                    color = LiveTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "关闭",
                    color = LiveTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .focusRequester(closeFocus)
                        .focusProperties { down = firstFocus }
                        .duoFocusRing(RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .duoClickable(onClick = onDismiss)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 7.dp),
            ) {
                LiveDanmakuSettingRow(
                    label = "显示弹幕",
                    value = if (ui.liveDanmakuEnabled) "开" else "关",
                    focusRequester = firstFocus,
                    modifier = Modifier.focusProperties { up = closeFocus },
                    onClick = onToggleEnabled,
                )
                LiveDanmakuSettingRow(
                    label = "不透明度",
                    value = "${(ui.liveDanmakuOpacity * 100).toInt()}%",
                    onClick = onCycleOpacity,
                )
                LiveDanmakuSettingRow(
                    label = "字号",
                    value = liveDanmakuSizeLabel(ui.liveDanmakuTextSize),
                    onClick = onCycleSize,
                )
                LiveDanmakuSettingRow(
                    label = "速度",
                    value = liveDanmakuSpeedLabel(ui.liveDanmakuSpeed),
                    onClick = onCycleSpeed,
                )
                LiveDanmakuSettingRow(
                    label = "显示区域",
                    value = liveDanmakuAreaLabel(ui.liveDanmakuArea),
                    modifier = Modifier.focusProperties { down = FocusRequester.Cancel },
                    onClick = onCycleArea,
                )
            }
            Text(
                "B/返回关闭 · 调整立即生效",
                color = LiveTextTertiary,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun LiveDanmakuSettingRow(
    label: String,
    value: String,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .duoFocusRing(RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .duoClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = LiveTextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(text = value, color = LivePink, fontSize = 12.sp)
    }
}

@Composable
private fun LiveQualityOverlay(
    qualities: List<QualityOption>,
    selectedQuality: QualityOption?,
    selectedIndex: Int,
    selectedFocus: FocusRequester,
    closeFocus: FocusRequester,
    onDismiss: () -> Unit,
    onSelect: (QualityOption) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 300.dp, max = 380.dp)
                .heightIn(max = 360.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(LiveSurface)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .focusGroup()
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("清晰度", color = LiveTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(
                    "关闭",
                    color = LiveTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .focusRequester(closeFocus)
                        .duoFocusRing(RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .duoClickable(onClick = onDismiss)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            if (qualities.isEmpty()) {
                Text("清晰度加载中…", color = LiveTextSecondary, fontSize = 12.sp)
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 7.dp),
                ) {
                    qualities.forEachIndexed { index, quality ->
                        LiveQualityRow(
                            label = quality.description,
                            selected = quality.quality == selectedQuality?.quality,
                            focusRequester = if (index == selectedIndex) selectedFocus else null,
                            onClick = { onSelect(quality) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveQualityRow(
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .duoFocusRing(RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) LivePink.copy(alpha = 0.14f) else Color.Transparent)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .duoClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) LivePink else LiveTextPrimary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = LivePink,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun selectedQualityText(ui: PlayerUiState): String =
    ui.currentQuality?.description?.takeIf { it.isNotBlank() } ?: "清晰度"

private val liveDanmakuOpacityOptions = listOf(0.3f, 0.5f, 0.7f, 0.9f, 1f)
private val liveDanmakuSpeedOptions = listOf(0.7f, 1f, 1.4f, 1.8f)
private val liveDanmakuSizeOptions = listOf(13f, 16f, 20f, 24f)
private val liveDanmakuAreaOptions = listOf(0.3f, 0.55f, 0.8f, 1f)

private fun nextLiveDanmakuValue(current: Float, options: List<Float>): Float {
    val index = options.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
    return options[(index + 1).mod(options.size)]
}

private fun liveDanmakuSizeLabel(value: Float): String = when {
    value <= 14f -> "小"
    value <= 18f -> "标准"
    value <= 22f -> "大"
    else -> "特大"
}

private fun liveDanmakuSpeedLabel(value: Float): String = when {
    value <= 0.8f -> "0.7×"
    value <= 1.2f -> "1.0×"
    value <= 1.6f -> "1.4×"
    else -> "1.8×"
}

private fun liveDanmakuAreaLabel(value: Float): String = when {
    value <= 0.4f -> "1/4"
    value <= 0.65f -> "1/2"
    value <= 0.9f -> "3/4"
    else -> "全屏"
}

private fun formatLiveTime(value: String?): String {
    val text = value.orEmpty()
    return if (text.length >= 16) text.substring(5, 16) else text.ifBlank { "—" }
}
