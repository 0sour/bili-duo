package cn.xfangfang.biliduo.data.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

const val LIVE_CHAT_MAX_LENGTH = 20

data class LiveDanmakuConfData(
    val token: String = "",
    @SerializedName("host_server_list") val hosts: List<LiveDanmakuHost>? = null,
    val host: String = "",
)

data class LiveDanmakuHost(
    val host: String = "",
    val port: Int = 2243,
    @SerializedName("wss_port") val wssPort: Int = 443,
    @SerializedName("ws_port") val wsPort: Int = 2244,
)

data class LiveDanmakuConfig(
    val token: String = "",
    val hosts: List<LiveDanmakuHost> = emptyList(),
)

enum class LiveChatState { CONNECTING, CONNECTED, DISCONNECTED }

data class LiveChatMessage(
    /** Locally assigned, monotonically increasing list key. */
    val id: Long,
    val uid: Long = 0,
    val uname: String = "",
    val text: String = "",
    val medal: String? = null,
    val isAdmin: Boolean = false,
    val isSuperChat: Boolean = false,
    /** Server-side SC id, used by SUPER_CHAT_MESSAGE_DELETE. */
    val superChatId: Long = 0L,
    val price: Int = 0,
    val face: String = "",
    /** 0xFFRRGGBB, used by SC cards. */
    val background: Long? = null,
    val timestampSec: Long = 0,
    /** Server end time for SC banners; 0 when the frame did not carry it. */
    val endTimeSec: Long = 0L,
)

sealed interface LiveChatEvent {
    data class Message(val message: LiveChatMessage) : LiveChatEvent
    data class DeleteSuperChat(val id: Long) : LiveChatEvent
}

/** Live danmaku is capped at 20 characters by Bilibili. */
fun validateLiveChatText(text: String): String? {
    val trimmed = text.trim()
    return when {
        trimmed.isEmpty() -> "弹幕不能为空"
        trimmed.contains('\n') || trimmed.contains('\r') -> "弹幕不能包含换行"
        liveChatLength(trimmed) > LIVE_CHAT_MAX_LENGTH -> "弹幕最多 $LIVE_CHAT_MAX_LENGTH 个字符"
        else -> null
    }
}

fun liveChatLength(text: String): Int = text.codePointCount(0, text.length)

/**
 * Parses the JSON bodies carried by Bilibili live WebSocket op=5 frames.
 * Entry/gift/rank messages are intentionally dropped: the console only shows
 * chat and Super Chat.
 */
object LiveChatParser {

    fun parse(json: String, nextId: () -> Long): List<LiveChatEvent> {
        val root = runCatching { JsonParser().parse(json).asJsonObject }.getOrNull()
            ?: return emptyList()
        return when (root.string("cmd")?.substringBefore(':')) {
            "DANMU_MSG" -> listOfNotNull(parseDanmaku(root, nextId()))
            "SUPER_CHAT_MESSAGE" -> listOfNotNull(parseSuperChat(root, nextId()))
            "SUPER_CHAT_MESSAGE_DELETE" -> parseSuperChatDelete(root)
            else -> emptyList()
        }
    }

    private fun parseDanmaku(root: JsonObject, id: Long): LiveChatEvent.Message? {
        val info = root.array("info") ?: return null
        val text = info.stringAt(1)?.takeIf { it.isNotBlank() } ?: return null
        val user = info.arrayAt(2)
        val uid = user?.longAt(0) ?: 0L
        val uname = user?.stringAt(1).orEmpty()
        val admin = user?.intAt(2) == 1
        val medalArray = info.arrayAt(3)
        val medalName = medalArray?.stringAt(1).orEmpty()
        val medalLevel = medalArray?.intAt(0) ?: 0
        val medal = medalName.takeIf { it.isNotBlank() }?.let {
            if (medalLevel > 0) "$it $medalLevel" else it
        }
        val timestamp = info.arrayAt(0)?.longAt(4) ?: 0L
        return LiveChatEvent.Message(
            LiveChatMessage(
                id = id,
                uid = uid,
                uname = uname.ifBlank { "匿名用户" },
                text = text,
                medal = medal,
                isAdmin = admin,
                timestampSec = timestamp,
            )
        )
    }

    private fun parseSuperChat(root: JsonObject, id: Long): LiveChatEvent.Message? {
        val data = root.obj("data") ?: return null
        val scId = data.long("id") ?: return null
        val user = data.obj("user_info")
        val background = parseColor(
            data.string("background_bottom_color") ?: data.string("background_color")
        )
        val text = data.string("message").orEmpty()
        return LiveChatEvent.Message(
            LiveChatMessage(
                id = id,
                uid = user?.long("uid") ?: 0L,
                uname = user?.string("uname").orEmpty().ifBlank { "匿名用户" },
                text = text,
                isSuperChat = true,
                superChatId = scId,
                price = data.int("price") ?: 0,
                face = user?.string("face").orEmpty(),
                background = background,
                timestampSec = data.long("ts") ?: data.long("start_time") ?: 0L,
                endTimeSec = data.long("end_time") ?: 0L,
            )
        )
    }

    private fun parseSuperChatDelete(root: JsonObject): List<LiveChatEvent> {
        val data = root.obj("data") ?: return emptyList()
        val single = data.long("id")
        if (single != null) return listOf(LiveChatEvent.DeleteSuperChat(single))
        val ids = data.array("ids") ?: return emptyList()
        return (0 until ids.size()).mapNotNull { index ->
            ids.get(index)?.takeIf { it.isJsonPrimitive }?.asLong?.let {
                LiveChatEvent.DeleteSuperChat(it)
            }
        }
    }
}

/** Parses "#RRGGBB" / "#AARRGGBB" without depending on android.graphics.Color. */
fun parseLiveColor(value: String?): Long? {
    val hex = value?.trim()?.removePrefix("#") ?: return null
    val parsed = hex.toLongOrNull(16) ?: return null
    return when (hex.length) {
        6 -> 0xFF000000L or parsed
        8 -> parsed
        else -> null
    }
}

private fun parseColor(value: String?): Long? = parseLiveColor(value)

private fun JsonObject.string(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString

private fun JsonObject.long(key: String): Long? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asLong

private fun JsonObject.int(key: String): Int? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asInt

private fun JsonObject.obj(key: String): JsonObject? =
    get(key)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.array(key: String): JsonArray? =
    get(key)?.takeIf { it.isJsonArray }?.asJsonArray

private fun JsonArray.elementAt(index: Int): JsonElement? =
    if (index in 0 until size()) get(index) else null

private fun JsonArray.stringAt(index: Int): String? =
    elementAt(index)?.takeIf { it.isJsonPrimitive }?.asString

private fun JsonArray.longAt(index: Int): Long? =
    elementAt(index)?.takeIf { it.isJsonPrimitive }?.asLong

private fun JsonArray.intAt(index: Int): Int? =
    elementAt(index)?.takeIf { it.isJsonPrimitive }?.asInt

private fun JsonArray.arrayAt(index: Int): JsonArray? =
    elementAt(index)?.takeIf { it.isJsonArray }?.asJsonArray
