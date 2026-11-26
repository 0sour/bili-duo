package cn.xfangfang.biliduo.ui.messages

import cn.xfangfang.biliduo.data.model.MessageFeedKind
import cn.xfangfang.biliduo.data.model.MsgFeedItem
import cn.xfangfang.biliduo.data.model.MsgFeedSubject
import cn.xfangfang.biliduo.util.cleanHtmlText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Normalized row for the message center; pure data so it is easy to test. */
data class MessageRowUi(
    val key: String,
    val avatars: List<String>,
    val actorText: String,
    val actionText: String,
    val content: String,
    val sourceTitle: String,
    val sourceImage: String,
    val typeLabel: String,
    val timeSeconds: Long,
    val bvid: String?,
    val aid: Long?,
)

private val BvRegex = Regex("BV[0-9A-Za-z]{10}")
private val AidRegex = Regex(
    "(?:bilibili\\.com/video/av|bilibili://video/)(\\d+)",
    RegexOption.IGNORE_CASE,
)

fun MsgFeedItem.toMessageRowUi(kind: MessageFeedKind): MessageRowUi? {
    val subject = item
    val timeSeconds = when (kind) {
        MessageFeedKind.REPLY -> replyTime ?: 0L
        MessageFeedKind.AT -> atTime ?: 0L
        MessageFeedKind.LIKE -> likeTime ?: 0L
    }
    val users = when (kind) {
        MessageFeedKind.LIKE -> users.orEmpty()
        else -> listOfNotNull(user)
    }
    val names = users.mapNotNull { it.nickname?.takeIf(String::isNotBlank) }
    val totalLikes = counts?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
    val actorText = when {
        kind != MessageFeedKind.LIKE -> names.firstOrNull().orEmpty()
        names.isEmpty() -> "有人"
        totalLikes > names.size -> "${names.joinToString("、")} 等 $totalLikes 人"
        else -> names.joinToString("、")
    }
    val actionText = when (kind) {
        MessageFeedKind.REPLY -> "回复了我"
        MessageFeedKind.AT -> "@了我"
        MessageFeedKind.LIKE -> "赞了我"
    }
    val content = cleanHtmlText(
        subject?.sourceContent?.takeIf(String::isNotBlank) ?: "",
    ).trim()
    val target = parseVideoTarget(subject, kind)
    val id = id ?: 0L
    if (id == 0L && content.isEmpty() && names.isEmpty()) return null
    return MessageRowUi(
        key = "${kind.name}_${id}_${timeSeconds}_${users.firstOrNull()?.mid ?: 0}",
        avatars = users.mapNotNull { it.avatar?.takeIf(String::isNotBlank) },
        actorText = actorText,
        actionText = actionText,
        content = content,
        sourceTitle = cleanHtmlText(subject?.title.orEmpty()).trim(),
        sourceImage = subject?.image.orEmpty(),
        typeLabel = subjectTypeLabel(subject?.type),
        timeSeconds = timeSeconds,
        bvid = target.first,
        aid = target.second,
    )
}

/** Resolves the video a notification points at, if any. */
fun parseVideoTarget(
    subject: MsgFeedSubject?,
    kind: MessageFeedKind,
): Pair<String?, Long?> {
    val uri = subject?.uri.orEmpty()
    BvRegex.find(uri)?.value?.let { return it to null }
    AidRegex.find(uri)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { return null to it }
    if (kind != MessageFeedKind.LIKE && subject?.type == "video") {
        subject.subjectId?.takeIf { it > 0 }?.let { return null to it }
    }
    return null to null
}

fun subjectTypeLabel(type: String?): String = when (type) {
    "video" -> "视频"
    "reply" -> "评论"
    "dynamic" -> "动态"
    "opus" -> "图文"
    "article" -> "专栏"
    "live" -> "直播"
    "bangumi", "pgc" -> "番剧"
    "music" -> "音频"
    else -> "内容"
}

/**
 * Relative time for message rows. [nowSeconds] is injectable so the pure
 * function can be unit tested.
 */
fun messageTimeLabel(
    seconds: Long,
    nowSeconds: Long = System.currentTimeMillis() / 1000L,
): String {
    if (seconds <= 0L) return ""
    val diff = nowSeconds - seconds
    if (diff < 0) return formatMessageDate(seconds, nowSeconds)
    return when {
        diff < 60L -> "刚刚"
        diff < 3600L -> "${diff / 60L} 分钟前"
        diff < 86_400L -> "${diff / 3600L} 小时前"
        diff < 172_800L -> "昨天"
        diff < 7L * 86_400L -> "${diff / 86_400L} 天前"
        else -> formatMessageDate(seconds, nowSeconds)
    }
}

private fun formatMessageDate(seconds: Long, nowSeconds: Long): String {
    val date = Date(seconds * 1000L)
    val then = Calendar.getInstance().apply { time = date }
    val now = Calendar.getInstance().apply { timeInMillis = nowSeconds * 1000L }
    val pattern = if (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
        "M月d日"
    } else {
        "yyyy年M月d日"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
}
