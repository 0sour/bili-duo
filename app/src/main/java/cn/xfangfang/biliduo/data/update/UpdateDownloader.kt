package cn.xfangfang.biliduo.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import cn.xfangfang.biliduo.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Downloads and verifies an update APK.
 *
 * The download uses its own client, never BilibiliApi.httpClient, so update
 * requests cannot accidentally carry login cookies or Bilibili headers.
 */
class UpdateDownloader(private val context: Context) {

    companion object {
        private const val FILE_PREFIX = "bili-duo-"

        /** Builds a safe, versioned APK filename for the update directory. */
        fun apkFileName(tag: String): String {
            val safe = tag.replace(Regex("[^A-Za-z0-9._-]"), "_")
            return "$FILE_PREFIX$safe.apk"
        }

        /** A local APK is reusable only when it exists, is non-empty and matches the size. */
        fun isValidApk(file: File, expectedSize: Long): Boolean {
            if (expectedSize <= 0) return false
            return file.exists() && file.isFile && file.length() == expectedSize && file.length() > 0
        }

        /**
         * Identity check for a locally parsed APK archive: it must belong to
         * [expectedPackage] and its internal versionName must equal [tag].
         * Kept as a pure helper for tests and diagnostics.
         */
        fun matchesRelease(
            pkg: String?,
            versionName: String?,
            expectedPackage: String,
            tag: String,
        ): Boolean {
            if (pkg == null || versionName == null) return false
            return pkg == expectedPackage && versionName == tag.removePrefix("v")
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun download(
        info: UpdateInfo,
        source: UpdateSource,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        val url = info.downloadUrl(source)
            ?: return@withContext Result.failure(IOException("该下载源不可用"))
        val dir = updatesDir()
        dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".apk") && it.name != apkFileName(info.tag) }
            ?.forEach { runCatching { it.delete() } }
        val target = File(dir, apkFileName(info.tag))
        target.delete()

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Bili-Duo/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("空响应")
                val total = info.size.coerceAtLeast(body.contentLength().coerceAtLeast(0L))
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                var lastEmit = 0L
                body.byteStream().use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmit >= 150L || (total > 0 && downloaded >= total)) {
                                lastEmit = now
                                onProgress(downloaded, total)
                            }
                        }
                        output.flush()
                    }
                }
                if (total > 0 && downloaded != total) {
                    throw IOException("安装包不完整：$downloaded/$total 字节")
                }
                if (target.length() != info.size) {
                    throw IOException("安装包大小不一致")
                }
                val actualSha = digest.digest().toHex()
                if (!actualSha.equals(info.sha256, ignoreCase = true)) {
                    throw IOException("SHA-256 校验失败")
                }
                verifyApk(target, info)
                Result.success(target)
            }
        } catch (e: CancellationException) {
            target.delete()
            throw e
        } catch (e: Exception) {
            target.delete()
            Result.failure(e)
        }
    }

    /**
     * Verifies package name, versionCode and the signing certificate. The
     * Android installer enforces the signature too; failing early gives the
     * user a useful message instead of a generic "应用未安装".
     */
    private fun verifyApk(file: File, info: UpdateInfo) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IOException("无法解析安装包")
        if (archive.packageName != context.packageName) {
            throw IOException("安装包包名不匹配")
        }
        val archiveCode = versionCodeOf(archive)
        if (archiveCode != info.versionCode) {
            throw IOException("安装包版本号不匹配")
        }
        if (archiveCode <= BuildConfig.VERSION_CODE.toLong()) {
            throw IOException("安装包版本不高于当前版本")
        }
        val expected = signaturesOf(
            pm.getPackageInfo(context.packageName, flags)
        )
        val actual = signaturesOf(archive)
        if (expected.isEmpty() || actual.isEmpty() || expected.intersect(actual).isEmpty()) {
            throw IOException("安装包签名与当前应用不一致")
        }
    }

    fun hasInstallPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } catch (_: Exception) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_SECURITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    fun install(apkFile: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun updatesDir(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "updates").apply { mkdirs() }
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }

    @Suppress("DEPRECATION")
    private fun signaturesOf(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return signatures.orEmpty().map { it.toCharsString() }.toSet()
    }

    private fun ByteArray.toHex(): String {
        val digits = "0123456789abcdef"
        return buildString(size * 2) {
            for (byte in this@toHex) {
                val value = byte.toInt() and 0xff
                append(digits[value ushr 4])
                append(digits[value and 0x0f])
            }
        }
    }
}
