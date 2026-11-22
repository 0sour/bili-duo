package cn.xfangfang.biliduo.data.update

import android.os.Build
import cn.xfangfang.biliduo.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Reads the Bili-Duo-specific update manifest.
 *
 * This client deliberately does not reuse BilibiliApi.httpClient: update
 * requests must never carry Bilibili cookies or WBI/rate-limit interceptors.
 */
class UpdateClient(
    private val client: OkHttpClient = defaultClient(),
) {

    suspend fun check(currentVersionCode: Int): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (url in MANIFEST_URLS) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-cache")
                    .header("User-Agent", "Bili-Duo/${BuildConfig.VERSION_NAME}")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    val body = response.body?.string()
                        ?: throw IOException("empty response")
                    val info = UpdateManifestParser.parse(body)
                        ?: throw IOException("invalid manifest")
                    if (info.minSdk != null && info.minSdk > Build.VERSION.SDK_INT) {
                        return@withContext Result.failure(
                            IOException("当前系统版本过低，无法安装新版本")
                        )
                    }
                    return@withContext Result.success(
                        info.takeIf { it.isNewerThan(currentVersionCode) }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        Result.failure(lastError ?: IOException("无法连接更新服务器"))
    }

    companion object {
        const val SERVER_MANIFEST_URL = "https://bili-duo.sourden.xyz/update-beta.json"
        const val GITHUB_MANIFEST_URL =
            "https://raw.githubusercontent.com/0sour/bili-duo/main/update-beta.json"

        private val MANIFEST_URLS = listOf(SERVER_MANIFEST_URL, GITHUB_MANIFEST_URL)

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(16, TimeUnit.SECONDS)
                .build()
    }
}
