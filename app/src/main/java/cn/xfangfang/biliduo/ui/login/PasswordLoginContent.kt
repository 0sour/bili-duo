package cn.xfangfang.biliduo.ui.login

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

@Composable
fun PasswordLoginContent(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    session: DuoSessionViewModel? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { BilibiliRepository() }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var captchaToken by remember { mutableStateOf("") }
    var captchaGt by remember { mutableStateOf("") }
    var captchaChallenge by remember { mutableStateOf("") }
    var showCaptcha by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(showCaptcha) {
        if (showCaptcha) keyboard?.hide()
    }

    LaunchedEffect(Unit) {
        delay(100)
        // A: focus the account field, but the read-only gate keeps the IME shut.
        runCatching { usernameFocus.requestFocus() }
    }

    fun loginWithCaptcha(challenge: String, validate: String, seccode: String) {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                repository.getWebKey()
                    .onSuccess { keyData ->
                        val encryptedPassword = withContext(Dispatchers.Default) {
                            encryptPassword(keyData.hash, keyData.key, password)
                        }
                        repository.passwordLoginRaw(
                            username = username,
                            password = encryptedPassword,
                            token = captchaToken,
                            challenge = challenge,
                            validate = validate,
                            seccode = seccode,
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
                    }
                    .onFailure {
                        errorMessage = it.message ?: "获取登录密钥失败"
                    }
            } finally {
                isLoading = false
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LoginFormColumn(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "账号密码登录",
            color = loginTextColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(14.dp))

        LoginTextField(
            value = username,
            onValueChange = { username = it.trim() },
            label = "手机号 / 邮箱",
            focusRequester = usernameFocus,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        LoginTextField(
            value = password,
            onValueChange = { password = it },
            label = "密码",
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
            focusRequester = passwordFocus,
            modifier = Modifier.fillMaxWidth(),
        )

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
            enabled = username.isNotBlank() && password.isNotBlank(),
            loading = isLoading,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    errorMessage = "请输入账号和密码"
                    return@LoginPrimaryButton
                }
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

        Spacer(Modifier.height(10.dp))
        Text(
            text = "密码登录需要完成极验验证",
            color = loginTertiaryColor,
            fontSize = 10.sp,
        )
        }

        // Rendered after the form so the overlay is on top.
        if (showCaptcha) {
            GeetestCaptcha(
                gt = captchaGt,
                challenge = captchaChallenge,
                onResult = { result ->
                    showCaptcha = false
                    loginWithCaptcha(result.challenge, result.validate, result.seccode)
                },
                onDismiss = { showCaptcha = false },
                session = session,
            )
        }
    }
}

private fun encryptPassword(salt: String, publicKeyPem: String, password: String): String {
    val pem = publicKeyPem
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace("\n", "")
        .replace("\r", "")
    val keyBytes = Base64.getDecoder().decode(pem)
    val keySpec = X509EncodedKeySpec(keyBytes)
    val keyFactory = KeyFactory.getInstance("RSA")
    val publicKey = keyFactory.generatePublic(keySpec)
    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.ENCRYPT_MODE, publicKey)
    val encrypted = cipher.doFinal((salt + password).toByteArray())
    return Base64.getEncoder().encodeToString(encrypted)
}
