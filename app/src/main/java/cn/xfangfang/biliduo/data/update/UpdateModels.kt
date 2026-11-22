package cn.xfangfang.biliduo.data.update

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/** A download endpoint published in the update manifest. */
data class UpdateDownloadLink(
    val name: String,
    val url: String,
)

enum class UpdateSource(
    val key: String,
    val title: String,
    val subtitle: String,
) {
    CN("cn", "国内源下载", "bili-duo.sourden.xyz · 推荐"),
    GITHUB("github", "GitHub 下载", "github.com · 备用"),
}

/** Parsed and validated update manifest. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val tag: String,
    val downloads: List<UpdateDownloadLink>,
    val size: Long,
    val sha256: String,
    val notes: String,
    val publishedAt: String,
    val minSdk: Int?,
    val channel: String,
) {
    fun isNewerThan(currentVersionCode: Int): Boolean =
        versionCode > currentVersionCode

    /** Prefer an explicitly named source; fall back to the first CN/mirror URL. */
    fun downloadUrl(source: UpdateSource): String? {
        val exact = downloads.firstOrNull { it.name.equals(source.key, ignoreCase = true) }
        if (exact != null) return exact.url
        if (source != UpdateSource.CN) return null
        val first = downloads.firstOrNull() ?: return null
        // A GitHub-only manifest must not be offered through the "国内源" button.
        return if (first.name.equals(UpdateSource.GITHUB.key, ignoreCase = true)) null else first.url
    }
}

/**
 * Pure manifest parsing kept outside the network layer so it can be unit tested.
 *
 * Gson does not execute Kotlin default values; every field is nullable here and
 * validated before it reaches the UI.
 */
object UpdateManifestParser {

    private val gson = Gson()

    private data class ManifestDto(
        @SerializedName("versionCode") val versionCode: Long? = null,
        @SerializedName("versionName") val versionName: String? = null,
        @SerializedName("tag") val tag: String? = null,
        @SerializedName("downloads") val downloads: List<DownloadDto>? = null,
        @SerializedName("size") val size: Long? = null,
        @SerializedName("sha256") val sha256: String? = null,
        @SerializedName("notes") val notes: String? = null,
        @SerializedName("publishedAt") val publishedAt: String? = null,
        @SerializedName("minSdk") val minSdk: Int? = null,
        @SerializedName("channel") val channel: String? = null,
        // Backwards-compatible single-URL field.
        @SerializedName("apkUrl") val apkUrl: String? = null,
    )

    private data class DownloadDto(
        @SerializedName("name") val name: String? = null,
        @SerializedName("url") val url: String? = null,
    )

    fun parse(raw: String): UpdateInfo? {
        val dto = runCatching {
            gson.fromJson(raw, ManifestDto::class.java)
        }.getOrNull() ?: return null

        val versionCode = dto.versionCode ?: return null
        if (versionCode <= 0L) return null

        val versionName = dto.versionName?.trim().orEmpty()
        val tag = dto.tag?.trim().orEmpty()
        val displayName = versionName.ifBlank { tag }
        if (displayName.isBlank()) return null

        val downloads = dto.downloads.orEmpty()
            .mapNotNull { item ->
                val url = item.url?.trim().orEmpty()
                if (!isHttpsUrl(url)) return@mapNotNull null
                val name = item.name?.trim().orEmpty().ifBlank { "cn" }
                UpdateDownloadLink(name = name, url = url)
            }
            .ifEmpty {
                val legacy = dto.apkUrl?.trim().orEmpty()
                if (isHttpsUrl(legacy)) listOf(UpdateDownloadLink("cn", legacy)) else emptyList()
            }
            .distinctBy { it.name.lowercase() }
            .ifEmpty { return null }

        val size = dto.size ?: 0L
        if (size <= 0L) return null

        val sha256 = dto.sha256?.trim()?.lowercase().orEmpty()
        if (!isSha256(sha256)) return null

        return UpdateInfo(
            versionCode = versionCode,
            versionName = displayName,
            tag = tag.ifBlank { displayName },
            downloads = downloads,
            size = size,
            sha256 = sha256,
            notes = dto.notes?.trim().orEmpty(),
            publishedAt = dto.publishedAt?.trim().orEmpty(),
            minSdk = dto.minSdk,
            channel = dto.channel?.trim().orEmpty(),
        )
    }

    private fun isHttpsUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) && value.length > "https://".length

    private fun isSha256(value: String): Boolean =
        value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
}
