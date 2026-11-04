package cn.xfangfang.biliduo.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.ui.top.TopAmbientUi
import cn.xfangfang.biliduo.ui.top.TopGlyph
import cn.xfangfang.biliduo.ui.top.TopPersonUi
import kotlinx.coroutines.delay

private val ProfileBg = Color(0xFF0F0F0F)
private val ProfileSurface = Color(0xFF17171A)
private val ProfileSurface2 = Color(0xFF1F1F24)
private val ProfileText = Color(0xFFF2F2F4)
private val ProfileSecondary = Color(0xFFA6A6AE)
private val ProfileTertiary = Color(0xFF73737F)
private val ProfilePink = Color(0xFFFB7299)
private val ProfileOnPink = Color(0xFF1A0710)
private val ProfileOutline = Color(0xFF2A2A31)
private val ProfileError = Color(0xFFF2555A)

@Composable
fun ProfileScreen(
    onLoginClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onFollowingClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onTopPerson: (TopPersonUi) -> Unit = {},
    onTopAmbient: (TopAmbientUi) -> Unit = {},
    session: DuoSessionViewModel? = null,
    viewModel: ProfileViewModel = viewModel(),
) {
    val state by profileStateWithLifecycleEvents(viewModel)
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
    val menuFocuses = remember { List(5) { FocusRequester() } }
    val logoutFocus = remember { FocusRequester() }
    var restoreLogoutFocus by remember { mutableStateOf(false) }

    LaunchedEffect(
        state.isLoggedIn,
        state.uname,
        state.face,
        state.mid,
        state.level,
    ) {
        if (state.isLoggedIn) {
            onTopPerson(
                TopPersonUi(
                    eyebrow = "PROFILE / 我的",
                    name = state.uname.ifBlank { "哔哩哔哩用户" },
                    subtitle = buildString {
                        append("UID ${state.mid}")
                        if (state.level > 0) append(" · Lv${state.level}")
                    },
                    description = if (state.error != null) {
                        "资料刷新失败，稍后重试。"
                    } else {
                        "账号已登录。观看历史、收藏与关注会在下屏同步展示。"
                    },
                    badge = "已登录",
                    avatarUrl = state.face,
                    glyph = TopGlyph.PERSON,
                )
            )
        } else if (!state.isLoading) {
            onTopAmbient(
                TopAmbientUi(
                    eyebrow = "PROFILE / 我的",
                    title = "登录后，\n上屏就是你的主页。",
                    subtitle = "头像、昵称、等级与观看数据会显示在这里；二维码、验证码和密码表单都在下屏操作。",
                    glyph = TopGlyph.LOGIN,
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { menuFocuses[0].requestFocus() }
    }

    LaunchedEffect(showLogoutConfirm) {
        if (!showLogoutConfirm && restoreLogoutFocus) {
            restoreLogoutFocus = false
            delay(120)
            runCatching { logoutFocus.requestFocus() }
        }
    }
    // B is routed by InputRouter -> DuoSessionViewModel; while an in-screen
    // card is open it must close the card instead of popping the whole screen.
    LaunchedEffect(showLogoutConfirm, session) {
        when {
            showLogoutConfirm -> session?.setBackInterceptor {
                showLogoutConfirm = false
                true
            }
            else -> session?.setBackInterceptor(null)
        }
    }
    DisposableEffect(session) {
        onDispose { session?.setBackInterceptor(null) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ProfileBg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "我的",
                    color = ProfileText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                if (state.isLoading && state.isLoggedIn) {
                    CircularProgressIndicator(
                        color = ProfilePink,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            ProfileHeader(
                state = state,
                logoutFocus = logoutFocus,
                onLoginClick = onLoginClick,
                onLogoutClick = {
                    restoreLogoutFocus = true
                    showLogoutConfirm = true
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                val entries = listOf(
                    ProfileMenuEntry("观看历史", Icons.Outlined.History, onHistoryClick),
                    ProfileMenuEntry("我的收藏", Icons.Outlined.FavoriteBorder, onFavoritesClick),
                    ProfileMenuEntry("我的关注", Icons.Outlined.PersonAdd, onFollowingClick),
                    ProfileMenuEntry("我的下载", Icons.Outlined.FileDownload, onDownloadsClick),
                    ProfileMenuEntry("设置", Icons.Outlined.Settings, onSettingsClick),
                )
                entries.forEachIndexed { index, entry ->
                    ProfileMenuRow(
                        entry = entry,
                        modifier = Modifier.focusRequester(menuFocuses[index]),
                    )
                    if (index != entries.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 44.dp, end = 10.dp)
                                .height(1.dp)
                                .background(ProfileOutline),
                        )
                    }
                }
            }

            if (!state.isLoggedIn) {
                Text(
                    text = "登录后可在云端同步收藏、关注与观看进度",
                    color = ProfileTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            } else {
                Spacer(Modifier.height(12.dp))
            }
        }

        if (showLogoutConfirm) {
            LogoutConfirmOverlay(
                onCancel = { showLogoutConfirm = false },
                onConfirm = {
                    showLogoutConfirm = false
                    viewModel.logout()
                },
            )
        }
    }
}

/**
 * Collects [ProfileViewModel.state] and refreshes it on ON_RESUME. The login
 * screen sits on the same back stack, so returning to Profile must re-read the
 * session instead of keeping a remembered logged-out state.
 */
@Composable
private fun profileStateWithLifecycleEvents(viewModel: ProfileViewModel): State<ProfileUiState> {
    val state = viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

private data class ProfileMenuEntry(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun ProfileHeader(
    state: ProfileUiState,
    logoutFocus: FocusRequester,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(ProfileSurface2),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isLoggedIn && state.face.isNotEmpty()) {
                AsyncImage(
                    model = state.face,
                    contentDescription = "头像",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = if (state.isLoggedIn) ProfileSecondary else ProfileTertiary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    !state.isLoggedIn -> "未登录"
                    state.uname.isNotBlank() -> state.uname
                    else -> "哔哩哔哩用户"
                },
                color = ProfileText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.height(5.dp))
            if (state.isLoggedIn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "UID ${state.mid}",
                        color = ProfileSecondary,
                        fontSize = 11.sp,
                    )
                    if (state.level > 0) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ProfilePink.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "Lv${state.level}",
                                color = ProfilePink,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                if (state.error != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "资料刷新失败，稍后重试",
                        color = ProfileTertiary,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            } else {
                Text(
                    text = "登录后同步历史与收藏",
                    color = ProfileSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        if (state.isLoggedIn) {
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .focusRequester(logoutFocus)
                    .duoFocusRing(RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, ProfileOutline, RoundedCornerShape(8.dp))
                    .duoClickable(onClick = onLogoutClick)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "退出登录", color = ProfileError, fontSize = 11.sp)
            }
        } else {
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .duoFocusRing(RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(ProfilePink)
                    .duoClickable(onClick = onLoginClick)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "登录",
                    color = ProfileOnPink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ProfileMenuRow(
    entry: ProfileMenuEntry,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .duoFocusRing(RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) ProfileSurface else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .duoClickable(onClick = entry.onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = null,
            tint = if (focused) ProfilePink else ProfileSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = entry.label,
            color = ProfileText,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = ProfileTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LogoutConfirmOverlay(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    var confirmSelected by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.66f))
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        confirmSelected = false
                        true
                    }
                    Key.DirectionRight -> {
                        confirmSelected = true
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown -> true
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (confirmSelected) onConfirm() else onCancel()
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        // Scrim: tap outside cancels.
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
                .clip(RoundedCornerShape(16.dp))
                .background(ProfileSurface2)
                .border(1.dp, ProfileOutline, RoundedCornerShape(16.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
        ) {
            Text(
                text = "退出登录",
                color = ProfileText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "退出后本地历史保留，云端同步停止",
                color = ProfileSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogButton(
                    text = "取消",
                    selected = !confirmSelected,
                    danger = false,
                    modifier = Modifier.weight(1f),
                    onClick = onCancel,
                )
                DialogButton(
                    text = "退出",
                    selected = confirmSelected,
                    danger = true,
                    modifier = Modifier.weight(1f),
                    onClick = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    selected: Boolean,
    danger: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background = when {
        danger && selected -> ProfileError.copy(alpha = 0.18f)
        danger -> ProfileError.copy(alpha = 0.10f)
        selected -> ProfileSurface
        else -> ProfileSurface.copy(alpha = 0.55f)
    }
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = when {
                    selected && danger -> ProfileError
                    selected -> Color.White
                    else -> ProfileOutline
                },
                shape = RoundedCornerShape(8.dp),
            )
            .duoClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (danger) ProfileError else ProfileText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
