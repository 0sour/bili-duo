package cn.xfangfang.biliduo.ui.top

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import coil.compose.AsyncImage
import cn.xfangfang.biliduo.data.model.DanmakuItem
import cn.xfangfang.biliduo.data.model.LiveChatMessage
import cn.xfangfang.biliduo.data.model.SubtitleCue
import cn.xfangfang.biliduo.ui.common.DuoLogo
import cn.xfangfang.biliduo.ui.live.LiveChatStore
import cn.xfangfang.biliduo.ui.live.LiveDanmakuView
import cn.xfangfang.biliduo.ui.player.DanmakuView
import cn.xfangfang.biliduo.ui.player.PlayerController
import cn.xfangfang.biliduo.ui.player.PlayerViewModel
import cn.xfangfang.biliduo.ui.player.TopPlayerSurface
import cn.xfangfang.biliduo.util.SubtitleParser
import cn.xfangfang.biliduo.util.fixPic
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TopBg = Color(0xFF0B0B0E)
private val TopSurface = Color(0xFF141418)
private val TopSurface3 = Color(0xFF26262E)
private val TopLine = Color(0xFF2E2E38)
private val TopText = Color(0xFFF5F5F7)
private val TopSecondary = Color(0xFFA9A9B4)
private val TopTertiary = Color(0xFF73737F)
private val TopPink = Color(0xFFFB7299)
private val TopBlue = Color(0xFF00A1D6)

/**
 * Top display host: the player Surface stays composed; the preview layer is
 * only drawn until the first video frame is rendered. It is read-only and
 * consumes touch so the top screen never grows its own controls.
 */
@Composable
fun TopScreenHost(
    controller: PlayerController,
    viewModel: TopScreenViewModel,
    playerViewModel: PlayerViewModel,
) {
    val playback by controller.state.collectAsState()
    val topState by viewModel.state.collectAsState()
    val playerUi by playerViewModel.uiState.collectAsState()
    val liveChat by LiveChatStore.snapshot.collectAsState()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        TopPlayerSurface(controller, Modifier.fillMaxSize())
        if (playback.hasRenderedFrame &&
            playerUi.danmakuEnabled &&
            playerUi.danmaku.isNotEmpty()
        ) {
            DanmakuOverlay(
                player = controller.player,
                items = playerUi.danmaku,
                opacity = playerUi.danmakuOpacity,
                speed = playerUi.danmakuSpeed,
                textSizeSp = playerUi.danmakuTextSize,
                area = playerUi.danmakuArea,
                blockedWords = playerUi.danmakuBlockedWords,
                maxOnScreen = playerUi.danmakuMaxOnScreen,
            )
        }
        if (playback.hasRenderedFrame && playerUi.isLive) {
            LiveDanmakuOverlay(
                roomId = playerUi.liveRoomId,
                messages = liveChat.messages,
                enabled = playerUi.danmakuEnabled,
                opacity = playerUi.danmakuOpacity,
                speed = playerUi.danmakuSpeed,
                textSizeSp = playerUi.danmakuTextSize,
                area = playerUi.danmakuArea,
                blockedWords = playerUi.danmakuBlockedWords,
                maxOnScreen = playerUi.danmakuMaxOnScreen,
            )
        }
        if (playback.hasRenderedFrame &&
            playerUi.subtitleEnabled &&
            playerUi.subtitleCues.isNotEmpty()
        ) {
            TopSubtitleOverlay(
                player = controller.player,
                cues = playerUi.subtitleCues,
                textSizeSp = playerUi.subtitleTextSizeSp,
                backgroundAlpha = playerUi.subtitleBackgroundAlpha,
                bottomPaddingDp = playerUi.subtitleBottomPaddingDp,
            )
        }
        if (!playback.hasRenderedFrame || topState.mode == TopScreenMode.DYNAMIC) {
            TopReadOnlyLayer(
                state = topState,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TopSubtitleOverlay(
    player: Player,
    cues: List<SubtitleCue>,
    textSizeSp: Float,
    backgroundAlpha: Float,
    bottomPaddingDp: Int,
) {
    var positionMs by remember(player) { mutableLongStateOf(player.currentPosition) }
    LaunchedEffect(player, cues) {
        while (true) {
            positionMs = player.currentPosition
            delay(120)
        }
    }
    val cue = remember(cues, positionMs) {
        SubtitleParser.findCurrentCue(cues, positionMs)
    } ?: return

    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = bottomPaddingDp.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = cue.content,
            color = Color.White,
            fontSize = textSizeSp.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = backgroundAlpha))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun DanmakuOverlay(
    player: Player,
    items: List<DanmakuItem>,
    opacity: Float,
    speed: Float,
    textSizeSp: Float,
    area: Float,
    blockedWords: Set<String>,
    maxOnScreen: Int,
) {
    AndroidView(
        factory = { context -> DanmakuView(context) },
        update = { view ->
            view.bind(
                player = player,
                items = items,
                opacity = opacity,
                speed = speed,
                textSizeSp = textSizeSp,
                area = area,
                blockedWords = blockedWords,
                maxOnScreen = maxOnScreen,
            )
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun LiveDanmakuOverlay(
    roomId: Long,
    messages: List<LiveChatMessage>,
    enabled: Boolean,
    opacity: Float,
    speed: Float,
    textSizeSp: Float,
    area: Float,
    blockedWords: Set<String>,
    maxOnScreen: Int,
) {
    AndroidView(
        factory = { context -> LiveDanmakuView(context) },
        update = { view ->
            view.bind(
                roomId = roomId,
                messages = messages,
                enabled = enabled,
                opacity = opacity,
                speed = speed,
                textSizeSp = textSizeSp,
                area = area,
                blockedWords = blockedWords,
                maxOnScreen = maxOnScreen,
            )
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun TopReadOnlyLayer(
    state: TopScreenUiState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(TopBg)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                            .changes
                            .forEach { it.consume() }
                    }
                }
            }
    ) {
        when (state.mode) {
            TopScreenMode.PREVIEW -> {
                val preview = state.preview
                if (preview != null) {
                    TopPreviewScreen(
                        preview = preview,
                        descriptionLoading = state.descriptionLoading,
                    )
                } else {
                    TopIdleScreen()
                }
            }
            TopScreenMode.PERSON -> state.person?.let { TopPersonScreen(it) } ?: TopIdleScreen()
            TopScreenMode.PANEL -> state.panel?.let { TopPanelScreen(it) } ?: TopIdleScreen()
            TopScreenMode.AMBIENT -> state.ambient?.let { TopAmbientScreen(it) } ?: TopIdleScreen()
            TopScreenMode.DYNAMIC -> state.dynamic?.let { TopDynamicScreen(it) } ?: TopIdleScreen()
            TopScreenMode.IDLE -> TopIdleScreen()
        }
    }
}

@Composable
private fun TopIdleScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF120C12),
                        TopBg,
                        Color(0xFF071218),
                    )
                )
            )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(TopPink.copy(alpha = 0.16f), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(TopBlue.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
        )

        Column(Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 28.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DuoLogo(size = 24.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Bili-Duo",
                    color = TopText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(TopSurface.copy(alpha = 0.75f))
                        .border(1.dp, TopLine, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(5.dp).background(TopPink, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("待机 · 下屏浏览", color = TopSecondary, fontSize = 8.sp)
                }
            }

            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "DUAL-SCREEN BILIBILI CLIENT",
                        color = TopPink,
                        fontSize = 8.sp,
                        letterSpacing = 1.6.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "下屏选视频，",
                        color = TopText,
                        fontSize = 36.sp,
                        lineHeight = 41.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "上屏先预览。",
                        color = TopPink,
                        fontSize = 36.sp,
                        lineHeight = 41.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "移动焦点或触摸视频卡片，上屏会展示封面、简介和 UP 主信息；开始播放后自动切换为视频画面。",
                        color = TopSecondary,
                        fontSize = 11.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.width(300.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(TopSurface.copy(alpha = 0.82f))
                            .border(1.dp, TopLine, RoundedCornerShape(7.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(17.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(TopPink),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("A", color = Color(0xFF1A0710), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(7.dp))
                        Text("开始播放", color = TopText, fontSize = 9.sp)
                        Spacer(Modifier.width(5.dp))
                        Text("· 移动焦点即可预览", color = TopTertiary, fontSize = 9.sp)
                    }
                }
                Spacer(Modifier.width(18.dp))
                DeviceSketch()
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("上屏只负责显示", color = TopTertiary, fontSize = 8.sp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.width(22.dp).height(1.dp).background(TopLine))
                Spacer(Modifier.width(10.dp))
                Text("不响应触摸或手柄", color = TopTertiary, fontSize = 8.sp)
                Spacer(Modifier.weight(1f))
                Text("下屏触摸与手柄共用一套焦点", color = TopTertiary, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun DeviceSketch() {
    Box(Modifier.width(315.dp).height(310.dp)) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(
                    Brush.radialGradient(
                        listOf(TopPink.copy(alpha = 0.14f), Color.Transparent)
                    )
                )
                .align(Alignment.Center)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 22.dp, y = 8.dp)
                .width(270.dp)
                .height(152.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color(0xFF14171F))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(11.dp))
        ) {
            Text(
                "TOP · VIDEO",
                color = TopTertiary,
                fontSize = 7.sp,
                letterSpacing = 1.1.sp,
                modifier = Modifier.padding(start = 10.dp, top = 7.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(196.dp)
                    .height(110.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1B2637), Color(0xFF2C1D2C))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(TopPink, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("▶", color = Color(0xFF1A0710), fontSize = 15.sp)
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 22.dp, y = -2.dp)
                .width(210.dp)
                .height(142.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color(0xFF141418))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(11.dp))
        ) {
            Text(
                "BOTTOM · UI",
                color = TopTertiary,
                fontSize = 7.sp,
                letterSpacing = 1.1.sp,
                modifier = Modifier.padding(start = 10.dp, top = 7.dp),
            )
            Box(
                Modifier
                    .padding(start = 0.dp, top = 0.dp)
                    .width(22.dp)
                    .fillMaxHeight()
                    .background(TopSurface.copy(alpha = 0.7f))
            )
            Row(
                modifier = Modifier
                    .padding(start = 32.dp, top = 28.dp, end = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.25f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (index == 0) TopPink.copy(alpha = 0.16f) else TopSurface3)
                            .border(
                                1.dp,
                                if (index == 0) TopPink.copy(alpha = 0.7f) else Color.Transparent,
                                RoundedCornerShape(4.dp),
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBackdrop(
    imageUrl: String?,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(TopBg)) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = fixPic(imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.18f),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            TopBg.copy(alpha = 0.42f),
                            TopBg.copy(alpha = 0.88f),
                            TopBg,
                        )
                    )
                )
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.15f), Color.Transparent)
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(TopBlue.copy(alpha = 0.08f), Color.Transparent)
                )
            )
        )
        Canvas(Modifier.fillMaxSize()) {
            val step = 72.dp.toPx()
            var x = 0f
            while (x <= size.width) {
                drawLine(
                    color = Color.White.copy(alpha = 0.022f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f,
                )
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(
                    color = Color.White.copy(alpha = 0.022f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
                y += step
            }
        }
        content()
    }
}

@Composable
private fun TopSceneHeader(right: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DuoLogo(size = 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Bili-Duo",
            color = TopText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(TopSurface.copy(alpha = 0.78f))
                .border(1.dp, TopLine, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(5.dp).background(accent, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                text = right,
                color = TopSecondary,
                fontSize = 7.5.sp,
                letterSpacing = 0.6.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TopSceneFooter(left: String, right: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(left, color = TopTertiary, fontSize = 8.sp, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(22.dp).height(1.dp).background(TopLine))
        Spacer(Modifier.width(10.dp))
        Text(right, color = TopTertiary, fontSize = 8.sp, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text("上屏只读", color = TopTertiary, fontSize = 8.sp)
    }
}

@Composable
private fun TopEyebrow(text: String, accent: Color) {
    Text(
        text = text.uppercase(),
        color = accent,
        fontSize = 8.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}

@Composable
private fun TopPill(
    text: String,
    accent: Color,
    filled: Boolean = false,
) {
    Text(
        text = text,
        color = if (filled) Color(0xFF1A0710) else accent,
        fontSize = 8.sp,
        fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (filled) accent else accent.copy(alpha = 0.10f))
            .border(
                width = 1.dp,
                color = accent.copy(alpha = if (filled) 1f else 0.55f),
                shape = RoundedCornerShape(5.dp),
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun TopGlyphBadge(
    glyph: TopGlyph,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(TopSurface.copy(alpha = 0.86f))
            .border(1.dp, accent.copy(alpha = 0.32f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = topGlyphIcon(glyph),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(52.dp),
        )
    }
}

private fun topGlyphIcon(glyph: TopGlyph): ImageVector = when (glyph) {
    TopGlyph.LIVE -> Icons.Outlined.Sensors
    TopGlyph.HOME -> Icons.Outlined.Home
    TopGlyph.PLAY -> Icons.Outlined.PlayArrow
    TopGlyph.SEARCH -> Icons.Outlined.Search
    TopGlyph.HISTORY -> Icons.Outlined.History
    TopGlyph.FAVORITE -> Icons.Outlined.FavoriteBorder
    TopGlyph.FOLLOWING -> Icons.Outlined.PersonAdd
    TopGlyph.DOWNLOAD -> Icons.Outlined.FileDownload
    TopGlyph.PERSON, TopGlyph.LOGIN -> Icons.Outlined.Person
    TopGlyph.NETWORK -> Icons.Outlined.Wifi
    TopGlyph.GAMEPAD -> Icons.Outlined.SportsEsports
    TopGlyph.THEME -> Icons.Outlined.DarkMode
    TopGlyph.DANMAKU -> Icons.Outlined.Subtitles
    TopGlyph.STORAGE -> Icons.Outlined.Storage
    TopGlyph.ABOUT -> Icons.Outlined.Info
    TopGlyph.TRENDING -> Icons.Outlined.Whatshot
    TopGlyph.RANKING -> Icons.Outlined.Leaderboard
    TopGlyph.BANGUMI -> Icons.Outlined.LiveTv
    TopGlyph.MOVIE -> Icons.Outlined.Movie
    TopGlyph.DYNAMIC -> Icons.Outlined.DynamicFeed
}

@Composable
private fun TopPersonScreen(person: TopPersonUi) {
    val accent = Color(person.accent.toInt())
    val rich = person.stats.isNotEmpty() ||
        person.recentVideos.isNotEmpty() ||
        person.recentMoments.isNotEmpty()
    TopBackdrop(person.avatarUrl, accent) {
        Column(Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 26.dp)) {
            TopSceneHeader(
                right = person.badge.ifBlank { person.eyebrow },
                accent = accent,
            )
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1.12f)) {
                    TopEyebrow(person.eyebrow, accent)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = person.name,
                        color = TopText,
                        fontSize = if (rich) 32.sp else 38.sp,
                        lineHeight = if (rich) 36.sp else 42.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (person.subtitle.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = person.subtitle,
                            color = TopSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (person.stats.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                            person.stats.take(3).forEach { stat ->
                                Column {
                                    Text(
                                        text = stat.value,
                                        color = TopText,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = stat.label,
                                        color = TopTertiary,
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    if (person.description.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = person.description,
                            color = TopSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            maxLines = if (rich) 2 else 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 340.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (person.badge.isNotBlank()) {
                            TopPill(person.badge, accent, filled = true)
                        }
                        TopPill("下屏焦点联动", accent)
                    }
                }
                Column(
                    modifier = Modifier.weight(0.88f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val avatarSize = if (rich) 186.dp else 208.dp
                    Box(
                        modifier = Modifier.size(avatarSize + if (rich) 54.dp else 82.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(avatarSize + if (rich) 26.dp else 36.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(accent.copy(alpha = 0.20f), Color.Transparent)
                                    )
                                )
                        )
                        Box(
                            Modifier
                                .size(avatarSize)
                                .clip(CircleShape)
                                .background(TopSurface)
                                .border(2.dp, accent.copy(alpha = 0.78f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (person.avatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = fixPic(person.avatarUrl),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                )
                            } else {
                                Icon(
                                    imageVector = topGlyphIcon(person.glyph),
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(64.dp),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = if (rich) 11.dp else 22.dp)
                                .size(8.dp)
                                .background(accent, CircleShape)
                        )
                    }
                }
            }
            if (person.recentMoments.isNotEmpty() || person.recentVideos.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    if (person.recentMoments.isNotEmpty()) {
                        TopPersonMoments(
                            moments = person.recentMoments,
                            accent = accent,
                            modifier = Modifier.width(
                                if (person.recentVideos.isEmpty()) 460.dp else 300.dp
                            ),
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    if (person.recentVideos.isNotEmpty()) {
                        TopPersonVideos(
                            videos = person.recentVideos,
                            accent = accent,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TopSceneFooter("上屏只读 · 不响应触摸或手柄", "下屏选择，上屏展示")
        }
    }
}

@Composable
private fun TopPersonMoments(
    moments: List<TopPersonMoment>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(TopSurface.copy(alpha = 0.92f))
            .border(1.dp, TopLine, RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "最近动态",
                color = accent,
                fontSize = 8.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${moments.size} 条",
                color = TopTertiary,
                fontSize = 7.sp,
            )
        }
        moments.take(2).forEach { moment ->
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .size(4.dp)
                        .background(accent.copy(alpha = 0.8f), CircleShape)
                )
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = moment.text.ifBlank { "（无文字动态）" },
                        color = TopSecondary,
                        fontSize = 9.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (moment.meta.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = moment.meta,
                            color = TopTertiary,
                            fontSize = 7.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopPersonVideos(
    videos: List<TopPersonVideo>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "最近视频",
                color = accent,
                fontSize = 8.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "RECENT · ${videos.size}",
                color = TopTertiary,
                fontSize = 7.sp,
            )
        }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            videos.take(3).forEach { video ->
                Column(Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(TopSurface3),
                    ) {
                        if (video.coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = fixPic(video.coverUrl),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        if (video.meta.isNotBlank()) {
                            Text(
                                text = video.meta,
                                color = Color.White,
                                fontSize = 7.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0x99000000))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = video.title,
                        color = TopSecondary,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopPanelScreen(panel: TopPanelUi) {
    val accent = Color(panel.accent.toInt())
    TopBackdrop(null, accent) {
        Column(Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 26.dp)) {
            TopSceneHeader(right = "SETTINGS · 设置", accent = accent)
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1.08f)) {
                    TopEyebrow(panel.eyebrow, accent)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = panel.title,
                        color = TopText,
                        fontSize = 40.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    if (panel.subtitle.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = panel.subtitle,
                            color = TopSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                    if (panel.description.isNotBlank()) {
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = panel.description,
                            color = TopTertiary,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            maxLines = 2,
                            modifier = Modifier.widthIn(max = 300.dp),
                        )
                    }
                    if (panel.detail.isNotBlank()) {
                        Spacer(Modifier.height(9.dp))
                        Text(
                            text = panel.detail,
                            color = accent,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(accent.copy(alpha = 0.08f))
                                .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(5.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    panel.items.forEachIndexed { index, item ->
                        val active = index == panel.activeIndex
                        Row(
                            modifier = Modifier.fillMaxWidth().height(25.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .height(if (active) 17.dp else 6.dp)
                                    .background(
                                        if (active) accent else TopLine,
                                        RoundedCornerShape(2.dp),
                                    )
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = item,
                                color = if (active) TopText else TopSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                            )
                            if (active) {
                                Spacer(Modifier.width(8.dp))
                                Text("当前", color = accent, fontSize = 7.5.sp)
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier.weight(0.92f).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(300.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(accent.copy(alpha = 0.18f), Color.Transparent)
                                )
                            )
                    )
                    Box(
                        Modifier
                            .size(256.dp)
                            .clip(CircleShape)
                            .border(1.dp, TopLine, CircleShape)
                    )
                    TopGlyphBadge(
                        glyph = panel.glyph,
                        accent = accent,
                        modifier = Modifier.size(176.dp),
                    )
                    Text(
                        text = "下屏左右切换分类",
                        color = TopSecondary,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = (-8).dp)
                            .clip(RoundedCornerShape(50))
                            .background(TopSurface.copy(alpha = 0.9f))
                            .border(1.dp, TopLine, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            TopSceneFooter("下屏左右切换分类 · 上下调整设置", "6 个分类")
        }
    }
}

@Composable
private fun TopAmbientScreen(ambient: TopAmbientUi) {
    val accent = Color(ambient.accent.toInt())
    TopBackdrop(null, accent) {
        Column(Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 26.dp)) {
            TopSceneHeader(
                right = ambient.eyebrow.ifBlank { "BILI-DUO" },
                accent = accent,
            )
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1.18f)) {
                    if (ambient.eyebrow.isNotBlank()) {
                        TopEyebrow(ambient.eyebrow, accent)
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(
                        text = ambient.title,
                        color = TopText,
                        fontSize = 40.sp,
                        lineHeight = 45.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (ambient.subtitle.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = ambient.subtitle,
                            color = TopSecondary,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            maxLines = 3,
                            modifier = Modifier.widthIn(max = 320.dp),
                        )
                    }
                    if (ambient.hint.isNotBlank()) {
                        Spacer(Modifier.height(18.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(TopSurface.copy(alpha = 0.82f))
                                .border(1.dp, TopLine, RoundedCornerShape(7.dp))
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(17.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(accent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "A",
                                    color = Color(0xFF1A0710),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.width(7.dp))
                            Text(ambient.hint, color = TopText, fontSize = 9.sp)
                        }
                    }
                }
                Box(
                    modifier = Modifier.weight(0.82f).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(290.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(accent.copy(alpha = 0.18f), Color.Transparent)
                                )
                            )
                    )
                    Box(
                        Modifier
                            .size(252.dp)
                            .clip(CircleShape)
                            .border(1.dp, TopLine, CircleShape)
                    )
                    TopGlyphBadge(
                        glyph = ambient.glyph,
                        accent = accent,
                        modifier = Modifier.size(168.dp),
                    )
                }
            }
            TopSceneFooter("上屏只读 · 操作在下屏", "Bili-Duo")
        }
    }
}

@Composable
private fun TopPreviewScreen(
    preview: TopPreviewUi,
    descriptionLoading: Boolean,
) {
    TopBackdrop(preview.coverUrl, TopPink) {
        Column(Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 26.dp)) {
            TopSceneHeader(right = "PREVIEW · 上屏预览", accent = TopPink)
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1.65f).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(TopSurface3)
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                    ) {
                        if (preview.coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = fixPic(preview.coverUrl),
                                contentDescription = null,
                                contentScale = if (preview.coverFit) ContentScale.Fit else ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF1B2637), Color(0xFF2C1D2C))
                                        )
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = preview.title,
                                    color = TopSecondary,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                        if (preview.duration.isNotBlank()) {
                            Text(
                                text = preview.duration,
                                color = Color.White,
                                fontSize = 9.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.62f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TopSurface.copy(alpha = 0.92f))
                        .border(1.dp, TopLine, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    TopEyebrow(preview.eyebrow, TopPink)
                    Spacer(Modifier.height(9.dp))
                    Text(
                        text = preview.title,
                        color = TopText,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = preview.metaLine ?: buildString {
                        if (preview.playCount > 0) append("${formatCount(preview.playCount)}播放")
                        if (preview.danmakuCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${formatCount(preview.danmakuCount)}弹幕")
                        }
                        val date = formatDate(preview.pubdate)
                        if (date.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(date)
                        }
                    }
                    if (meta.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = meta,
                            color = TopTertiary,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(11.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(TopLine))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (preview.ownerName.isNotBlank()) {
                            AsyncImage(
                                model = fixPic(preview.ownerFace),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(TopSurface3),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = preview.ownerName,
                                    color = TopText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = preview.ownerLabel,
                                    color = TopTertiary,
                                    fontSize = 8.sp,
                                )
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        TopPill(preview.category.ifBlank { "视频" }, TopPink)
                    }
                    Spacer(Modifier.height(11.dp))
                    Text("简介", color = TopTertiary, fontSize = 8.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = when {
                            preview.description.isNotBlank() -> preview.description
                            descriptionLoading -> "简介加载中…"
                            else -> "暂无简介"
                        },
                        color = TopSecondary,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(17.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(TopPink),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "A",
                                color = Color(0xFF1A0710),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(preview.actionHint, color = TopText, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
            TopSceneFooter("上屏只读 · 播放后自动切换视频", "PREVIEW")
        }
    }
}

@Composable
private fun TopDynamicScreen(reader: TopDynamicReaderUi) {
    when (reader.kind) {
        TopDynamicKind.DETAIL -> TopDynamicDetailScreen(reader)
        TopDynamicKind.GALLERY -> TopDynamicGalleryScreen(reader)
        TopDynamicKind.TEXT -> TopDynamicTextScreen(reader)
    }
}

@Composable
private fun TopDynamicDetailScreen(reader: TopDynamicReaderUi) {
    val image = reader.activeImage
    val body = dynamicReaderBody(reader.text)
    TopBackdrop(image, TopPink) {
        Column(Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 26.dp)) {
            TopSceneHeader("DETAIL · 动态详情", TopPink)
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TopSurface.copy(alpha = 0.92f))
                        .border(1.dp, TopLine, RoundedCornerShape(12.dp))
                        .padding(15.dp),
                ) {
                    TopEyebrow("DYNAMIC / 图文阅读", TopPink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = reader.title.ifBlank { "动态图文" },
                        color = TopText,
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = body,
                        color = TopSecondary,
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        maxLines = 9,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = fixPic(reader.authorFace),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(TopSurface3),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = reader.authorName.ifBlank { "未知用户" },
                                color = TopText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = listOf(reader.pubTime, reader.kindLabel)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                color = TopTertiary,
                                fontSize = 8.sp,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "评论 ${formatCount(reader.commentCount.toLong())}  ·  " +
                            "转发 ${formatCount(reader.forwardCount.toLong())}  ·  " +
                            "赞 ${formatCount(reader.likeCount.toLong())}",
                        color = TopTertiary,
                        fontSize = 8.sp,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1.05f).fillMaxHeight()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(TopSurface3)
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                    ) {
                        if (!image.isNullOrBlank()) {
                            AsyncImage(
                                model = fixPic(image),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text = reader.title,
                                color = TopSecondary,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            )
                        }
                        if (reader.images.size > 1) {
                            Text(
                                text = "${reader.imageIndex + 1} / ${reader.images.size}",
                                color = Color.White,
                                fontSize = 9.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                    if (reader.images.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        // Keep the current image visible in the strip even when
                        // the dynamic has more than five pictures.
                        val thumbCount = minOf(5, reader.images.size)
                        val thumbStart = (reader.imageIndex - thumbCount / 2)
                            .coerceIn(0, (reader.images.size - thumbCount).coerceAtLeast(0))
                        val thumbnails = reader.images.drop(thumbStart).take(thumbCount)
                        Row(Modifier.fillMaxWidth().height(56.dp)) {
                            thumbnails.forEachIndexed { offset, url ->
                                val index = thumbStart + offset
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(TopSurface3)
                                        .border(
                                            width = if (index == reader.imageIndex) 2.dp else 1.dp,
                                            color = if (index == reader.imageIndex) TopPink
                                            else Color.White.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(8.dp),
                                        ),
                                ) {
                                    AsyncImage(
                                        model = fixPic(url),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                if (offset != thumbnails.lastIndex) {
                                    Spacer(Modifier.width(7.dp))
                                }
                            }
                        }
                    }
                }
            }
            TopSceneFooter("上屏只读 · 下屏滚动阅读", "DETAIL")
        }
    }
}

@Composable
private fun TopDynamicGalleryScreen(reader: TopDynamicReaderUi) {
    val image = reader.activeImage
    Box(Modifier.fillMaxSize().background(TopBg)) {
        if (!image.isNullOrBlank()) {
            AsyncImage(
                model = fixPic(image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.24f),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        TopBg.copy(alpha = 0.55f),
                        TopBg.copy(alpha = 0.30f),
                        TopBg.copy(alpha = 0.94f),
                    )
                )
            )
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 26.dp)) {
            TopSceneHeader(
                "GALLERY · 图片 ${reader.imageIndex + 1} / ${reader.images.size.coerceAtLeast(1)}",
                TopPink,
            )
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .fillMaxHeight(0.84f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TopSurface3)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!image.isNullOrBlank()) {
                        AsyncImage(
                            model = fixPic(image),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Text(
                        text = "${reader.imageIndex + 1} / ${reader.images.size.coerceAtLeast(1)}",
                        color = Color.White,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    TopEyebrow(
                        listOf(reader.authorName, reader.pubTime)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                            .ifBlank { "DYNAMIC / 图片画册" },
                        TopPink,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = reader.title.ifBlank { "动态图片" },
                        color = TopText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    reader.images.take(8).forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .padding(start = 5.dp)
                                .height(4.dp)
                                .width(if (index == reader.imageIndex) 18.dp else 5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (index == reader.imageIndex) TopPink
                                    else Color.White.copy(alpha = 0.22f)
                                ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            TopSceneFooter("上屏只读 · 下屏切换图片", "GALLERY")
        }
    }
}

@Composable
private fun TopDynamicTextScreen(reader: TopDynamicReaderUi) {
    val body = dynamicReaderBody(reader.text)
    TopBackdrop(null, TopPink) {
        Column(Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 26.dp)) {
            TopSceneHeader("TEXT · 纯文字动态", TopPink)
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp),
            ) {
                Column(Modifier.weight(1.3f).fillMaxHeight()) {
                    TopEyebrow("DYNAMIC / 纯文字", TopPink)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = reader.title.ifBlank { "纯文字动态" },
                        color = TopText,
                        fontSize = 28.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = body,
                        color = TopSecondary,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        maxLines = 9,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = fixPic(reader.authorFace),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(TopSurface3),
                        )
                        Spacer(Modifier.width(9.dp))
                        Column {
                            Text(
                                text = reader.authorName.ifBlank { "未知用户" },
                                color = TopText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                            Text(
                                text = listOf(reader.pubTime, reader.kindLabel)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                color = TopTertiary,
                                fontSize = 8.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(26.dp))
                Column(Modifier.weight(0.7f).fillMaxHeight()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(TopLine))
                    Spacer(Modifier.height(10.dp))
                    TopEyebrow("NO IMAGES", TopPink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "没有配图时，上屏用排版呈现正文，而不是留一块空封面。",
                        color = TopSecondary,
                        fontSize = 9.sp,
                        lineHeight = 15.sp,
                        maxLines = 4,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "评论 ${formatCount(reader.commentCount.toLong())}",
                        color = TopText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text("评论", color = TopTertiary, fontSize = 8.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "转发 ${formatCount(reader.forwardCount.toLong())}",
                        color = TopText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text("转发", color = TopTertiary, fontSize = 8.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "赞 ${formatCount(reader.likeCount.toLong())}",
                        color = TopText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text("赞", color = TopTertiary, fontSize = 8.sp)
                }
            }
            TopSceneFooter("上屏只读 · 纯文字阅读", "TEXT")
        }
    }
}

/**
 * The first non-blank line is the poster title, so the body must not repeat it.
 * A single-line dynamic has no body; do not fall back to the full text.
 */
private fun dynamicReaderBody(text: String): String {
    val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
    return if (lines.size > 1) lines.drop(1).joinToString("\n").trim() else ""
}

private fun formatCount(value: Long): String = when {
    value >= 100_000_000L -> "%.1f亿".format(value / 100_000_000.0)
    value >= 10_000L -> "%.1f万".format(value / 10_000.0)
    else -> value.toString()
}

private fun formatDate(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return ""
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        .format(Date(epochSeconds * 1000L))
}
