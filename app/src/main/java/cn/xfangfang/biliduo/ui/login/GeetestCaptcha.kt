package cn.xfangfang.biliduo.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.JsPromptResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import org.json.JSONObject

data class GeetestResult(
    val challenge: String,
    val validate: String,
    val seccode: String
)

private const val TAG = "bili-duo"

/**
 * In-composition captcha overlay.
 *
 * Compose Dialog cannot be used here: the bottom UI runs inside a Presentation
 * (window type 2037), and Dialog.show() rejects a TYPE_APPLICATION window on
 * that context ("Window type mismatch"), crashing the app on Android 12+.
 * Rendering the WebView as an overlay keeps it in the Presentation window and
 * lets gamepad B close it through the Duo back interceptor.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GeetestCaptcha(
    gt: String,
    challenge: String,
    onResult: (GeetestResult) -> Unit,
    onDismiss: () -> Unit,
    session: DuoSessionViewModel? = null,
) {
    var resultHandled by remember { mutableStateOf(false) }

    DisposableEffect(session) {
        session?.setBackInterceptor {
            onDismiss()
            true
        }
        onDispose { session?.setBackInterceptor(null) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "请完成验证",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFF2F2F4),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color(0xFFA6A6AE),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

                            webChromeClient = object : WebChromeClient() {
                                // Swallow console output: Geetest logs validate/seccode.
                                override fun onConsoleMessage(
                                    consoleMessage: android.webkit.ConsoleMessage?
                                ): Boolean = true

                                override fun onJsPrompt(
                                    view: WebView?,
                                    url: String?,
                                    message: String?,
                                    defaultValue: String?,
                                    result: JsPromptResult?
                                ): Boolean {
                                    if (message != null && !resultHandled) {
                                        val colonIndex = message.indexOf(':')
                                        if (colonIndex > 0) {
                                            val type = message.substring(0, colonIndex)
                                            val jsonStr = message.substring(colonIndex + 1)
                                            when (type) {
                                                "success" -> {
                                                    try {
                                                        val json = JSONObject(jsonStr)
                                                        val gChallenge = json.optString("geetest_challenge", "")
                                                        val validate = json.optString("geetest_validate", "")
                                                        val seccode = json.optString("geetest_seccode", "")
                                                        if (gChallenge.isNotEmpty() && validate.isNotEmpty() && seccode.isNotEmpty()) {
                                                            resultHandled = true
                                                            onResult(GeetestResult(gChallenge, validate, seccode))
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e(TAG, "Geetest parse error")
                                                    }
                                                }
                                                "error" -> Log.e(TAG, "Geetest error from JS")
                                                "close" -> Unit
                                            }
                                        }
                                    }
                                    result?.confirm("")
                                    return true
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) = Unit

                                override fun onPageFinished(view: WebView?, url: String?) = Unit

                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                    val code = error?.errorCode ?: 0
                                    val desc = error?.description?.toString() ?: "unknown"
                                    Log.e(TAG, "Geetest WebView error: code=$code desc=$desc")
                                }
                            }

                            val html = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=2.0, user-scalable=yes">
                                <style>
                                    * { margin: 0; padding: 0; box-sizing: border-box; }
                                    body { background: #f5f5f5; display: flex; justify-content: center; align-items: flex-start; padding-top: 20px; min-height: 300px; }
                                    #captcha-box { background: white; border-radius: 8px; padding: 10px; width: 100%; max-width: 340px; }
                                </style>
                                </head>
                                <body>
                                <div id="captcha-box"></div>
                                <script src="https://static.geetest.com/static/tools/gt.js"></script>
                                <script>
                                    function R(type, data) {
                                        try {
                                            prompt(type + ':' + JSON.stringify(data));
                                        } catch(e) {
                                            console.log('R error: ' + e.message);
                                        }
                                    }
                                    try {
                                        console.log('Geetest: init, gt=$gt challenge=${challenge.take(20)}');
                                        initGeetest({
                                            gt: "$gt",
                                            challenge: "$challenge",
                                            lang: "zh-cn",
                                            width: "100%",
                                            product: "float",
                                            offline: false
                                        }, function(captchaObj) {
                                            console.log('Geetest: captchaObj created');
                                            captchaObj.appendTo("#captcha-box");
                                            captchaObj.onSuccess(function() {
                                                console.log('Geetest: onSuccess');
                                                try {
                                                    var r = captchaObj.getValidate();
                                                    console.log('Geetest: validate=' + JSON.stringify(r));
                                                    R("success", r);
                                                } catch(e) {
                                                    console.log('Geetest: getValidate error: ' + e.message);
                                                    R("error", {"msg": e.message});
                                                }
                                            });
                                            captchaObj.onError(function(e) {
                                                console.log('Geetest: onError: ' + JSON.stringify(e));
                                                R("error", e);
                                            });
                                            captchaObj.onClose(function() {
                                                console.log('Geetest: onClose');
                                                R("close", {});
                                            });
                                        });
                                    } catch(e) {
                                        console.log('Geetest init error: ' + e.message);
                                        R("error", {"msg": e.message});
                                    }
                                </script>
                                </body>
                                </html>
                            """.trimIndent()

                            loadDataWithBaseURL("https://www.bilibili.com", html, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
