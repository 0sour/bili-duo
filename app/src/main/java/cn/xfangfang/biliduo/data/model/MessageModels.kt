package cn.xfangfang.biliduo.data.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * Bilibili message-center payloads (x/msgfeed/...). Every field is nullable:
 * Gson does not run Kotlin default values, and the three feeds (reply/at/like)
 * share one model but leave different fields out.
 */
data class MsgFeedUser(
    val mid: Long? = null,
    val nickname: String? = null,
    val avatar: String? = null,
)

data class MsgFeedSubject(
    @SerializedName("subject_id") val subjectId: Long? = null,
    @SerializedName("source_id") val sourceId: Long? = null,
    val type: String? = null,
    val title: String? = null,
    val image: String? = null,
    /** The comment/reply text the notification is about. */
    @SerializedName("source_content") val sourceContent: String? = null,
    val uri: String? = null,
)

data class MsgFeedItem(
    val id: Long? = null,
    val user: MsgFeedUser? = null,
    /** Likes can bundle several users into one row. */
    val users: List<MsgFeedUser>? = null,
    val item: MsgFeedSubject? = null,
    @SerializedName("reply_time") val replyTime: Long? = null,
    @SerializedName("at_time") val atTime: Long? = null,
    @SerializedName("like_time") val likeTime: Long? = null,
    /**
     * Like feeds send a number; reply feeds send an object. Kept untyped so a
     * shape mismatch never fails the whole response.
     */
    val counts: JsonElement? = null,
)

data class MsgFeedCursor(
    @SerializedName("is_end") val isEnd: Boolean? = null,
    val id: Long? = null,
    val time: Long? = null,
)

data class MsgFeedData(
    val cursor: MsgFeedCursor? = null,
    val items: List<MsgFeedItem>? = null,
)

/** `x/msgfeed/like` nests its list under `total`. */
data class MsgLikeData(
    val total: MsgFeedData? = null,
)

data class MsgUnreadData(
    @SerializedName("unread_count") val unreadCount: Int? = null,
    @SerializedName("unread_reply") val unreadReply: Int? = null,
    @SerializedName("unread_at") val unreadAt: Int? = null,
    @SerializedName("unread_like") val unreadLike: Int? = null,
)

/** Unread counts in the shape the UI needs (total + per tab). */
data class MessageUnread(
    val total: Int,
    val reply: Int,
    val at: Int,
    val like: Int,
)

/** One page of a message feed with the cursor needed for the next call. */
data class MsgFeedPage(
    val items: List<MsgFeedItem>,
    val nextId: Long,
    val nextTime: Long,
    val isEnd: Boolean,
)

enum class MessageFeedKind { REPLY, AT, LIKE }
