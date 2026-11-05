package cn.xfangfang.biliduo.ui.player

const val COMMENT_MAX_LENGTH = 1000

/** Returns a user-facing error, or null when the draft can be posted. */
fun validateComment(text: String): String? {
    val trimmed = text.trim()
    return when {
        trimmed.isEmpty() -> "评论不能为空"
        trimmed.length > COMMENT_MAX_LENGTH -> "评论最多 $COMMENT_MAX_LENGTH 字"
        else -> null
    }
}
