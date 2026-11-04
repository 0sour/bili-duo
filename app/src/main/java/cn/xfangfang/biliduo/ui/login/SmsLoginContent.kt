package cn.xfangfang.biliduo.ui.login

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SmsLoginContent(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    session: DuoSessionViewModel? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { BilibiliRepository() }

    var phone by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var captchaToken by remember { mutableStateOf("") }
    var captchaGt by remember { mutableStateOf("") }
    var captchaChallenge by remember { mutableStateOf("") }
    var showCaptcha by remember { mutableStateOf(false) }
    var captchaKey by remember { mutableStateOf("") }
    var smsSent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val phoneFocus = remember { FocusRequester() }
    val codeFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(showCaptcha) {
        if (showCaptcha) keyboard?.hide()
    }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { phoneFocus.requestFocus() }
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    LaunchedEffect(smsSent) {
        if (smsSent) {
            delay(120)
            runCatching { codeFocus.requestFocus() }
        }
    }

    fun sendCode(token: String, challenge: String, validate: String, seccode: String) {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                repository.sendSmsCode(
                    tel = phone,
                    token = token,
                    challenge = challenge,
                    validate = validate,
                    seccode = seccode,
                ).onSuccess {
                    smsSent = true
                    countdown = 60
                    captchaKey = it.captchaKey
                    Toast.makeText(context, "验证码已发送", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    errorMessage = it.message ?: "验证码发送失败"
                }
            } finally {
                isLoading = false
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LoginFormColumn(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "手机号验证码登录",
            color = loginTextColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(14.dp))

        LoginTextField(
            value = phone,
            onValueChange = { phone = it.filter { c -> c.isDigit() }.take(11) },
            label = "手机号",
            keyboardType = KeyboardType.Phone,
            focusRequester = phoneFocus,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            LoginTextField(
                value = smsCode,
                onValueChange = { smsCode = it.filter { c -> c.isDigit() }.take(6) },
                label = "验证码",
                keyboardType = KeyboardType.Number,
                focusRequester = codeFocus,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            SendCodeButton(
                countdown = countdown,
                enabled = !isLoading && phone.length == 11,
                onClick = {
                    errorMessage = null
                    scope.launch {
                        isLoading = true
                        try {
                            repository.getCaptcha()
                                .onSuccess {
                                    captchaToken = it.token
                                    captchaGt = it.geetest?.gt ?: ""
                                    captchaChallenge = it.geetest?.challenge ?: ""
                                    if (captchaGt.isBlank() || captchaChallenge.isBlank()) {
                                        errorMessage = "验证服务不可用，请稍后重试"
                                    } else {
                                        showCaptcha = true
                                    }
                                }
                                .onFailure {
                                    errorMessage = it.message ?: "获取验证失败"
                                }
                        } finally {
                            isLoading = false
                        }
                    }
                },
            )
        }

        if (smsSent) {
            Spacer(Modifier.height(8.dp))
            Text(text = "验证码已发送，请查看短信", color = loginSecondaryColor, fontSize = 11.sp)
        }
        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = errorMessage.orEmpty(),
                color = loginErrorColor,
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(18.dp))

        LoginPrimaryButton(
            text = "登录",
            enabled = smsCode.length >= 4 && captchaKey.isNotEmpty(),
            loading = isLoading,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (smsCode.length < 4) {
                    errorMessage = "请输入验证码"
                    return@LoginPrimaryButton
                }
                if (captchaKey.isEmpty()) {
                    errorMessage = "请先获取验证码"
                    return@LoginPrimaryButton
                }
                scope.launch {
                    isLoading = true
                    errorMessage = null
                    try {
                        repository.smsLoginRaw(
                            tel = phone,
                            code = smsCode.toInt(),
                            captchaKey = captchaKey,
                        ).onSuccess { cookies ->
                            if (saveLoginSession(context, cookies)) {
                                Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                                onLoginSuccess()
                            } else {
                                errorMessage = "登录失败：未获取到完整凭证"
                            }
                        }.onFailure {
                            errorMessage = it.message ?: "登录失败"
                        }
                    } finally {
                        isLoading = false
                    }
                }
            },
        )

        Spacer(Modifier.height(10.dp))
        Text(
            text = "未注册的手机号登录后将自动注册",
            color = loginTertiaryColor,
            fontSize = 10.sp,
        )
        }

        // Rendered after the form so the overlay is on top; Compose draws
        // siblings in declaration order.
        if (showCaptcha) {
            GeetestCaptcha(
                gt = captchaGt,
                challenge = captchaChallenge,
                onResult = { result ->
                    showCaptcha = false
                    sendCode(captchaToken, result.challenge, result.validate, result.seccode)
                },
                onDismiss = { showCaptcha = false },
                session = session,
            )
        }
    }
}

@Composable
private fun SendCodeButton(
    countdown: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val active = enabled && countdown == 0
    Box(
        modifier = Modifier
            .width(106.dp)
            .height(56.dp)
            .duoFocusRing(RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(loginSurfaceColor)
            .duoClickable(enabled = active, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when {
                countdown > 0 -> "${countdown}s 后重发"
                else -> "获取验证码"
            },
            color = if (active) loginTextColor else loginTertiaryColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
