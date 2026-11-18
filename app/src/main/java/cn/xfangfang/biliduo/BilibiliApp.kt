package cn.xfangfang.biliduo

import android.app.Application
import android.net.Uri
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.intercept.Interceptor
import coil.memory.MemoryCache
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageResult
import coil.size.Dimension
import okhttp3.HttpUrl
import cn.xfangfang.biliduo.data.api.BilibiliApi
import cn.xfangfang.biliduo.data.download.DownloadManager
import cn.xfangfang.biliduo.data.local.AppSettings
import cn.xfangfang.biliduo.data.local.WifiMonitor
import cn.xfangfang.biliduo.util.bilibiliThumbnailUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class BilibiliApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppSettings.init(this)
        val prefs = getSharedPreferences("bili_prefs", MODE_PRIVATE)
        if (!prefs.contains("buvid3")) {
            prefs.edit().putString("buvid3", UUID.randomUUID().toString()).apply()
        }
        BilibiliApi.buvid3 = prefs.getString("buvid3", "")!!
        BilibiliApi.activateBuvidIfNeeded(appScope)
        val sessdata = prefs.getString("sessdata", null)
        val biliJct = prefs.getString("bili_jct", null)
        val dedeUserId = prefs.getString("dede_userid", null)
        if (!sessdata.isNullOrEmpty() && !biliJct.isNullOrEmpty() && !dedeUserId.isNullOrEmpty()) {
            BilibiliApi.setLoginCookies(sessdata, biliJct, dedeUserId)
            Log.d(TAG, "Restored login session for UID=$dedeUserId")
        }
        Log.d(TAG, "App started")
        WifiMonitor.start(this)
        appScope.launch {
            WifiMonitor.wifiLost.collect {
                DownloadManager.getInstance(this@BilibiliApp).pauseForWifi()
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache(
                MemoryCache.Builder(this)
                    .maxSizeBytes(16 * 1024 * 1024)
                    .build()
            )
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cache"))
                    .maxSizeBytes(50 * 1024 * 1024)
                    .build()
            }
            .components {
                // Ask the image CDN for a width-appropriate WebP instead of
                // downloading multi-hundred-KB originals for small cards.
                add(BilibiliImageResizeInterceptor())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .okHttpClient { BilibiliApi.httpClient }
            .crossfade(true)
            .build()
    }

    companion object {
        const val TAG = "bili-duo"
        lateinit var instance: BilibiliApp
            private set
    }

    private class BilibiliImageResizeInterceptor : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val request = chain.request
            val data = when (val value = request.data) {
                is String -> value
                is Uri -> value.toString()
                is HttpUrl -> value.toString()
                else -> null
            }
            val targetWidth = (chain.size.width as? Dimension.Pixels)?.px ?: 0
            val resized = data?.let { bilibiliThumbnailUrl(it, targetWidth) }
                ?: return chain.proceed(request)
            return chain.proceed(
                request.newBuilder()
                    .data(resized)
                    // Cache under the rewritten URL; otherwise an old full-size
                    // entry could be reused for the same original URL.
                    .memoryCacheKey(resized)
                    .diskCacheKey(resized)
                    .build()
            )
        }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
