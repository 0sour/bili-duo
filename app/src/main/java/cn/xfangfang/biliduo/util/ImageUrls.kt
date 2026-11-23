package cn.xfangfang.biliduo.util

/**
 * Rewrite a Bilibili image URL to a CDN-resized WebP.
 *
 * Cover/avatar originals are often several hundred KB even when the composable
 * only draws them at 64–400 px. Bilibili's image CDN accepts a width suffix
 * (`@480w.webp`), which cuts a typical cover from ~450 KB to ~30 KB and an
 * avatar from ~470 KB to ~5 KB.
 *
 * Returns null when the URL is already processed, is animated, or is not
 * hosted on a Bilibili image domain.
 */
fun bilibiliThumbnailUrl(url: String, targetWidthPx: Int): String? {
    if (targetWidthPx <= 0) return null
    val normalized = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") -> "https:${url.removePrefix("http:")}"
        url.startsWith("https://") -> url
        else -> return null
    }
    if (normalized.contains('@')) return null

    val withoutScheme = normalized.substringAfter("://", "")
    val host = withoutScheme
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringBefore(':')
        .lowercase()
    if (!isBilibiliImageHost(host)) return null

    val path = withoutScheme
        .substringAfter('/', "")
        .substringBefore('?')
        .substringBefore('#')
        .lowercase()
    if (path.endsWith(".gif") || path.endsWith(".svg")) return null

    val width = when {
        targetWidthPx <= 160 -> 160
        targetWidthPx <= 240 -> 240
        targetWidthPx <= 320 -> 320
        targetWidthPx <= 480 -> 480
        targetWidthPx <= 640 -> 640
        targetWidthPx <= 960 -> 960
        targetWidthPx <= 1280 -> 1280
        else -> 1600
    }

    val base = normalized.substringBefore('?').substringBefore('#')
    val suffix = normalized.removePrefix(base)
    return "$base@${width}w.webp$suffix"
}

/**
 * Full-screen viewers need the image's intrinsic pixels, not a decode sized to
 * the viewport; a tall infographic would otherwise be downsampled to the box
 * height and then stretched back to fit-width. Cap the CDN width at 1600 but
 * keep the full aspect ratio. Pair this with Coil `Size.ORIGINAL`.
 */
fun galleryImageUrl(url: String): String {
    val fixed = fixPic(url)
    return bilibiliThumbnailUrl(fixed, 1600) ?: fixed
}

private fun isBilibiliImageHost(host: String): Boolean =
    host == "hdslb.com" || host.endsWith(".hdslb.com") ||
        host == "biliimg.com" || host.endsWith(".biliimg.com") ||
        host == "bilivideo.com" || host.endsWith(".bilivideo.com")
