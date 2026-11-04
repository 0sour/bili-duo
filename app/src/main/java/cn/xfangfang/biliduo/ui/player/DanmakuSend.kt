package cn.xfangfang.biliduo.ui.player

/**
 * Danmaku text rules shared by the console UI and its tests.
 *
 * Bilibili counts characters (code points), so an emoji counts once even though
 * Kotlin's [String.length] counts two UTF-16 units.
 */
internal const val DANMAKU_MAX_LENGTH = 100

internal fun danmakuLength(text: String): Int =
    Character.codePointCount(text, 0, text.length)

internal fun validateDanmakuText(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return "弹幕不能为空"
    if (trimmed.contains('\n') || trimmed.contains('\r')) return "弹幕不能包含换行"
    if (danmakuLength(trimmed) > DANMAKU_MAX_LENGTH) {
        return "弹幕最多 $DANMAKU_MAX_LENGTH 个字符"
    }
    return null
}
