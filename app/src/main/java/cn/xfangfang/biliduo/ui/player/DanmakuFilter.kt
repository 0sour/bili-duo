package cn.xfangfang.biliduo.ui.player

import cn.xfangfang.biliduo.data.model.DanmakuItem

/** Splits the settings text field into words; accepts Chinese/English separators. */
fun parseBlockedDanmakuWords(raw: String): Set<String> =
    raw.split(',', '，', ';', '；', '|', '\n', '\r', '\t', ' ')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(50)
        .toSet()

/** Case-insensitive substring match; an empty filter returns the original list. */
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
