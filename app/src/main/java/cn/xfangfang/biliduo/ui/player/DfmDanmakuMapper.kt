package cn.xfangfang.biliduo.ui.player

import cn.xfangfang.biliduo.data.model.DanmakuItem

/**
 * Pure mapping from the app's [DanmakuItem] to the values DFM needs.
 *
 * Kept free of Android/DFM classes so the dense-danmaku rules can be unit
 * tested on the JVM. The adapter converts these specs to BaseDanmaku on DFM's
 * parser thread.
 */
enum class DfmDanmakuType {
    SCROLL_RL,
    SCROLL_LR,
    FIX_TOP,
    FIX_BOTTOM,
}

data class DfmDanmakuSpec(
    /** Unique, monotonically increasing order within one video. */
    val index: Int,
    val text: String,
    val timeMs: Long,
    val type: DfmDanmakuType,
    val textSizePx: Float,
    val textColor: Int,
    val shadowColor: Int = BLACK,
    /** Fixed (top/bottom) danmaku stay for 4 s, matching the legacy engine. */
    val fixedDurationMs: Long?,
    val userHash: String?,
) {
    companion object {
        const val BLACK: Int = -0x1000000 // 0xFF000000
    }
}

/** Matches the legacy engine: one screen height drives the base text size. */
fun danmakuBaseTextPx(viewHeightPx: Int, textSizeSp: Float): Float =
    viewHeightPx.coerceAtLeast(1) * 0.034f * (textSizeSp.coerceIn(12f, 24f) / 16f)

/**
 * DFM's scroll factor is a duration multiplier: larger value => slower.
 * The app's speed is px/ms: larger value => faster. Target 8 s per screen at
 * 1x, like the legacy engine.
 */
fun danmakuScrollSpeedFactor(speed: Float, realDurationMs: Long): Float {
    val duration = realDurationMs.coerceAtLeast(1L).toFloat()
    return (8_000f / duration) / speed.coerceIn(0.5f, 2f)
}

/**
 * Applies the same filters as the legacy view, then assigns unique indexes.
 *
 * DFM's DanmakuUtils.compare() falls back to `index`; its final fallback is
 * `obj1.hashCode() - obj1.hashCode()` (always 0). Bilibili XML has 10 ms time
 * resolution, so many items share a timestamp. Without unique indexes DFM's
 * TreeSet silently drops all but one of them.
 */
fun mapDanmakuItems(
    items: List<DanmakuItem>,
    viewHeightPx: Int,
    textSizeSp: Float,
    blockedWords: Set<String> = emptySet(),
    blockedRegex: String = "",
    shieldLevel: Int = 0,
    showScroll: Boolean = true,
    showTop: Boolean = true,
    showBottom: Boolean = true,
    blockedUidHashes: Set<String> = emptySet(),
): List<DfmDanmakuSpec> {
    val filtered = filterDanmaku(
        items = items,
        blockedWords = blockedWords,
        blockedRegex = compileDanmakuRegex(blockedRegex),
        shieldLevel = shieldLevel,
        showScroll = showScroll,
        showTop = showTop,
        showBottom = showBottom,
        blockedUidHashes = blockedUidHashes,
    )
    val base = danmakuBaseTextPx(viewHeightPx, textSizeSp)
    return filtered.mapIndexed { index, item ->
        val type = when (item.mode) {
            4 -> DfmDanmakuType.FIX_BOTTOM
            5 -> DfmDanmakuType.FIX_TOP
            6 -> DfmDanmakuType.SCROLL_LR
            else -> DfmDanmakuType.SCROLL_RL
        }
        DfmDanmakuSpec(
            index = index,
            text = item.text,
            timeMs = item.timeMs,
            type = type,
            textSizePx = (base * (item.fontSize / 25f)).coerceIn(base * 0.75f, base * 1.3f),
            textColor = DfmDanmakuSpec.BLACK or (item.color and 0xFFFFFF),
            fixedDurationMs = if (type == DfmDanmakuType.FIX_TOP || type == DfmDanmakuType.FIX_BOTTOM) {
                FIXED_DURATION_MS
            } else {
                null
            },
            userHash = item.uidHash.takeIf { it.isNotBlank() },
        )
    }
}

private const val FIXED_DURATION_MS = 4_000L
