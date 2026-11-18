package cn.xfangfang.biliduo.ui.player

import cn.xfangfang.biliduo.data.model.DanmakuItem

/** Splits the settings text field into words; accepts Chinese/English separators. */
fun parseBlockedDanmakuWords(raw: String): Set<String> =
    raw.split(',', '，', ';', '；', '|', '\n', '\r', '\t', ' ')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(50)
        .toSet()

/** Compiles the optional user regex; invalid patterns are ignored. */
fun compileDanmakuRegex(raw: String): Regex? =
    raw.trim().takeIf { it.isNotEmpty() }
        ?.let { pattern -> runCatching { Regex(pattern) }.getOrNull() }

/** True for modes rendered as fixed top/bottom rows; everything else scrolls. */
fun DanmakuItem.isFixedTop(): Boolean = mode == 5
fun DanmakuItem.isFixedBottom(): Boolean = mode == 4

/**
 * Applies the video danmaku filters in one pass:
 * keyword/regex blocks, per-author blocks, official-style shield level
 * (weight >= level), and the scroll/top/bottom visibility switches.
 * Level 0 disables the weight filter.
 */
fun filterDanmaku(
    items: List<DanmakuItem>,
    blockedWords: Set<String>,
    blockedRegex: Regex?,
    shieldLevel: Int,
    showScroll: Boolean,
    showTop: Boolean,
    showBottom: Boolean,
    blockedUidHashes: Set<String> = emptySet(),
): List<DanmakuItem> {
    val words = blockedWords.map { it.trim() }.filter { it.isNotEmpty() }
    val level = shieldLevel.coerceIn(0, 10)
    if (items.isEmpty()) return items
    return items.filter { item ->
        val typeVisible = when {
            item.isFixedTop() -> showTop
            item.isFixedBottom() -> showBottom
            else -> showScroll
        }
        typeVisible &&
            (level == 0 || item.weight >= level) &&
            (item.uidHash.isBlank() || item.uidHash !in blockedUidHashes) &&
            words.none { word -> item.text.contains(word, ignoreCase = true) } &&
            (blockedRegex == null || !blockedRegex.containsMatchIn(item.text))
    }
}

/** Case-insensitive substring match; kept for callers that only use keywords. */
fun filterBlockedDanmaku(
    items: List<DanmakuItem>,
    blockedWords: Set<String>,
): List<DanmakuItem> {
    val words = blockedWords.map { it.trim() }.filter { it.isNotEmpty() }
    if (words.isEmpty() || items.isEmpty()) return items
    return items.filterNot { item ->
        words.any { word -> item.text.contains(word, ignoreCase = true) }
    }
}

/** 0 means unlimited; keeps rendering bounded for busy scenes. */
fun limitDanmakuOnScreen(count: Int, maxOnScreen: Int): Boolean =
    maxOnScreen <= 0 || count < maxOnScreen
