package cn.xfangfang.biliduo.ui.login

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.ui.duo.DuoTextFieldGate
import cn.xfangfang.biliduo.ui.duo.duoTextFieldNavigation
import cn.xfangfang.biliduo.ui.duo.rememberDuoTextFieldState
import cn.xfangfang.biliduo.ui.top.TopAmbientUi
import cn.xfangfang.biliduo.ui.top.TopGlyph
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay

private const val QR_PIXELS = 600
private const val QR_EXPIRY_MS = 180_000L

private val LoginBg = Color(0xFF0F0F0F)
private val LoginSurface = Color(0xFF17171A)
private val LoginSurface2 = Color(0xFF1F1F24)
private val LoginText = Color(0xFFF2F2F4)
private val LoginSecondary = Color(0xFFA6A6AE)
private val LoginTertiary = Color(0xFF73737F)
private val LoginPink = Color(0xFFFB7299)
private val LoginOnPink = Color(0xFF1A0710)
private val LoginOutline = Color(0xFF2A2A31)
private val LoginError = Color(0xFFF2555A)

private val tabLabels = listOf("扫码登录", "短信登录", "密码登录")

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    onTopAmbient: (TopAmbientUi) -> Unit = {},
    session: DuoSessionViewModel? = null,
    viewModel: LoginViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val loginSuccess by viewModel.loginSuccess.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var lastQrUrl by remember { mutableStateOf("") }
    var qrDeadline by remember { mutableLongStateOf(0L) }
    var nowTick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var localExpired by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        val title: String
        val subtitle: String
        when (state) {
            is LoginUiState.QRReady -> {
                title = "扫码登录，\n继续你的收藏。"
                subtitle = "打开哔哩哔哩 App 扫一扫；二维码、验证码和密码表单都在下屏操作。"
            }
            is LoginUiState.Scanned -> {
                title = "已扫码，\n请在手机上确认。"
                subtitle = "确认后会自动返回个人中心，上屏随即切换为你的主页。"
            }
            is LoginUiState.Success -> {
                title = "登录成功，\n正在返回。"
                subtitle = "历史、收藏与关注会在下屏加载，上屏展示账号信息。"
            }
            is LoginUiState.Expired -> {
                title = "二维码已失效。"
                subtitle = "在下屏按 A 刷新二维码，或改用短信、密码登录。"
            }
            is LoginUiState.Error -> {
                title = "登录没有完成。"
                subtitle = "请在下屏检查信息后重试，或更换其他登录方式。"
            }
            else -> {
                title = "登录 Bili-Duo。"
                subtitle = "扫码、短信或密码登录；上屏只负责展示，所有输入都在下屏。"
            }
        }
        onTopAmbient(
            TopAmbientUi(
                eyebrow = "LOGIN / 登录",
                title = title,
                subtitle = subtitle,
                glyph = TopGlyph.LOGIN,
            )
        )
    }

    LaunchedEffect(state) {
        val s = state
        if (s is LoginUiState.QRReady) {
            lastQrUrl = s.url
            qrDeadline = SystemClock.elapsedRealtime() + QR_EXPIRY_MS
            localExpired = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowTick = SystemClock.elapsedRealtime()
            val s = viewModel.state.value
            if (s is LoginUiState.QRReady &&
                qrDeadline > 0L &&
                SystemClock.elapsedRealtime() >= qrDeadline
            ) {
                localExpired = true
            }
        }
    }

    LaunchedEffect(loginSuccess) {
        if (loginSuccess) {
            delay(900)
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LoginBg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .duoFocusRing(RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .duoClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = LoginText,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(2.dp))
            Text(
                text = "登录",
                color = LoginText,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabLabels.forEachIndexed { index, label ->
                val selected = selectedTab == index
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .padding(horizontal = 2.dp)
                        .duoFocusRing(RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) LoginPink.copy(alpha = 0.14f) else Color.Transparent
                        )
                        .duoClickable { selectedTab = index }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (selected) LoginPink else LoginSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(horizontal = 16.dp)
                .background(LoginOutline),
        )

        when (selectedTab) {
            0 -> QrLoginContent(
                state = state,
                loginSuccess = loginSuccess,
                qrUrl = lastQrUrl,
                remainingSeconds = if (qrDeadline > 0L) {
                    ((qrDeadline - nowTick) / 1000L).toInt().coerceAtLeast(0)
                } else {
                    180
                },
                locallyExpired = localExpired,
                onRefresh = viewModel::retry,
                modifier = Modifier.weight(1f),
            )
            1 -> SmsLoginContent(
                onLoginSuccess = onLoginSuccess,
                modifier = Modifier.weight(1f),
                session = session,
            )
            else -> PasswordLoginContent(
                onLoginSuccess = onLoginSuccess,
                modifier = Modifier.weight(1f),
                session = session,
            )
        }
    }
}

@Composable
private fun QrLoginContent(
    state: LoginUiState,
    loginSuccess: Boolean,
    qrUrl: String,
    remainingSeconds: Int,
    locallyExpired: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                loginSuccess || state is LoginUiState.Success -> {
                    SuccessBlock()
                }
                state is LoginUiState.Loading -> {
                    CircularProgressIndicator(
                        color = LoginPink,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("正在生成二维码…", color = LoginSecondary, fontSize = 12.sp)
                }
                state is LoginUiState.Error -> {
                    Text(
                        text = state.message,
                        color = LoginError,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    RefreshButton(onRefresh)
                }
                locallyExpired || state is LoginUiState.Expired -> {
                    ExpiredQrBlock(onRefresh)
                }
                state is LoginUiState.Scanned -> {
                    QrCard(qrUrl, dimmed = true)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = LoginPink,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "已扫描，请在手机上确认登录",
                            color = LoginPink,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "确认后会自动登录",
                        color = LoginTertiary,
                        fontSize = 11.sp,
                    )
                }
                else -> {
                    QrCard(qrUrl, dimmed = false)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "打开哔哩哔哩 App 扫一扫",
                        color = LoginText,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "二维码 $remainingSeconds 秒后失效，A 刷新",
                        color = LoginTertiary,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    RefreshButton(onRefresh)
                }
            }
        }
    }
}

@Composable
private fun SuccessBlock() {
    Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = null,
        tint = LoginPink,
        modifier = Modifier.size(48.dp),
    )
    Spacer(Modifier.height(12.dp))
    Text(text = "登录成功", color = LoginText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text(text = "正在返回…", color = LoginSecondary, fontSize = 12.sp)
}

@Composable
private fun ExpiredQrBlock(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .size(216.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(LoginSurface2)
            .border(1.dp, LoginOutline, RoundedCornerShape(8.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "二维码已失效", color = LoginSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        RefreshButton(onRefresh)
    }
    Spacer(Modifier.height(12.dp))
    Text(
        text = "刷新后重新扫码",
        color = LoginTertiary,
        fontSize = 11.sp,
    )
}

@Composable
private fun RefreshButton(onRefresh: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focusRequester.requestFocus() }
    }
    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .height(32.dp)
            .duoFocusRing(RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, LoginOutline, RoundedCornerShape(8.dp))
            .duoClickable(onClick = onRefresh)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "刷新二维码", color = LoginPink, fontSize = 11.sp)
    }
}

@Composable
private fun QrCard(content: String, dimmed: Boolean) {
    val bitmap = remember(content) { generateQrBitmap(content, QR_PIXELS) }
    Box(
        modifier = Modifier
            .size(216.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "登录二维码",
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (dimmed) 0.35f else 1f),
            )
        } else {
            Text(text = "无法生成二维码", color = Color.DarkGray, fontSize = 11.sp)
        }
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val black = android.graphics.Color.BLACK
        val white = android.graphics.Color.WHITE
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) black else white)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

/** Shared dark input used by the SMS and password forms. */
@Composable
internal fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: androidx.compose.ui.text.input.KeyboardType =
        androidx.compose.ui.text.input.KeyboardType.Text,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    focusRequester: FocusRequester? = null,
) {
    val fieldState = rememberDuoTextFieldState()
    val localFocus = remember { FocusRequester() }
    val requester = focusRequester ?: localFocus
    DuoTextFieldGate(
        state = fieldState,
        focusRequester = requester,
        modifier = modifier,
    ) { editing, gateFocused, fieldModifier ->
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = editing,
            label = { Text(label, fontSize = 12.sp) },
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = keyboardType,
            ),
            shape = RoundedCornerShape(8.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = LoginText,
                unfocusedTextColor = LoginText,
                disabledTextColor = LoginText,
                focusedContainerColor = LoginSurface,
                unfocusedContainerColor = LoginSurface,
                disabledContainerColor = LoginSurface,
                focusedBorderColor = LoginPink,
                unfocusedBorderColor = LoginOutline,
                disabledBorderColor = if (gateFocused) LoginPink else LoginOutline,
                focusedLabelColor = LoginPink,
                unfocusedLabelColor = LoginSecondary,
                disabledLabelColor = if (gateFocused) LoginPink else LoginSecondary,
                cursorColor = LoginPink,
                focusedPlaceholderColor = LoginTertiary,
                unfocusedPlaceholderColor = LoginTertiary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(fieldModifier)
                .then(if (editing) Modifier.duoTextFieldNavigation() else Modifier),
        )
    }
}

/** Shared primary action for the login forms. */
@Composable
internal fun LoginPrimaryButton(
    text: String,
    enabled: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .duoFocusRing(RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) LoginPink else LoginPink.copy(alpha = 0.38f))
            .duoClickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = LoginOnPink,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(
                text = text,
                color = LoginOnPink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
internal fun LoginFormColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .width(332.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            content = content,
        )
    }
}

internal val loginErrorColor: Color get() = LoginError
internal val loginSecondaryColor: Color get() = LoginSecondary
internal val loginTertiaryColor: Color get() = LoginTertiary
internal val loginSurfaceColor: Color get() = LoginSurface
internal val loginTextColor: Color get() = LoginText
