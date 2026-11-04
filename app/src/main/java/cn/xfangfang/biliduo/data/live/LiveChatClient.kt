package cn.xfangfang.biliduo.data.live

import android.util.Log
import cn.xfangfang.biliduo.BilibiliApp
import cn.xfangfang.biliduo.BuildConfig
import cn.xfangfang.biliduo.data.api.BilibiliApi
import cn.xfangfang.biliduo.data.model.LiveChatEvent
import cn.xfangfang.biliduo.data.model.LiveChatParser
import cn.xfangfang.biliduo.data.model.LiveChatState
import cn.xfangfang.biliduo.data.model.LiveDanmakuConfig
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.Inflater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * Read-only Bilibili live chat WebSocket.
 *
 * The endpoint sends binary protocol frames: a 16-byte header followed by a
 * JSON body (zlib-compressed when protover=2). Auth and heartbeat packets use
 * the same header. We only request protover=2 so the app does not need a brotli
 * decoder; a brotli frame is ignored instead of crashing.
 */
class LiveChatClient(
    private val roomId: Long,
    private val scope: CoroutineScope,
    private val onEvent: (LiveChatEvent) -> Unit,
    private val onState: (LiveChatState) -> Unit,
    private val repository: BilibiliRepository = BilibiliRepository(),
) {
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var connectJob: Job? = null
    private var reconnectJob: Job? = null
    private val messageSeq = AtomicLong(0L)

    @Volatile
    private var stopped = false

    fun start() {
        if (!stopped && webSocket != null) return
        stopped = false
        connect()
    }

    fun stop() {
        stopped = true
        connectJob?.cancel()
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        webSocket?.close(1000, "leave")
        webSocket = null
    }

    private fun connect() {
        if (stopped) return
        connectJob?.cancel()
        connectJob = scope.launch {
            closeCurrentSocket()
            postState(LiveChatState.CONNECTING)
            val config = repository.getLiveDanmakuConfig(roomId).getOrElse {
                if (!stopped) {
                    postState(LiveChatState.DISCONNECTED)
                    scheduleReconnect()
                }
                return@launch
            }
            if (stopped) return@launch
            val host = config.hosts.firstOrNull()?.host
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_HOST
            val request = Request.Builder()
                .url("wss://$host/sub")
                .build()
            webSocket = BilibiliApi.httpClient.newWebSocket(request, listener(config))
        }
    }

    private fun closeCurrentSocket() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        webSocket?.close(1000, "reconnect")
        webSocket = null
    }

    private fun scheduleReconnect() {
        if (stopped || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (!stopped) connect()
        }
    }

    private fun listener(config: LiveDanmakuConfig): WebSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (stopped) return
            webSocket.send(authPacket(config).toByteString())
            heartbeatJob?.cancel()
            heartbeatJob = scope.launch {
                while (isActive && !stopped) {
                    delay(HEARTBEAT_INTERVAL_MS)
                    webSocket.send(buildPacket(OP_HEARTBEAT, ByteArray(0)).toByteString())
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!stopped) parseJsonBody(text.toByteArray(Charsets.UTF_8))
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (!stopped) parsePackets(bytes.toByteArray())
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (stopped) return
            postState(LiveChatState.DISCONNECTED)
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (stopped) return
            if (BuildConfig.DEBUG) {
                Log.w(BilibiliApp.TAG, "LiveChatClient: disconnected room=$roomId (${t.javaClass.simpleName})")
            }
            postState(LiveChatState.DISCONNECTED)
            scheduleReconnect()
        }
    }

    private fun authPacket(config: LiveDanmakuConfig): ByteArray {
        val auth = JsonObject().apply {
            addProperty("uid", BilibiliApi.parseSelfMid() ?: 0L)
            addProperty("roomid", roomId)
            addProperty("protover", 2)
            addProperty("platform", "web")
            addProperty("type", 2)
            addProperty("key", config.token)
        }
        return buildPacket(OP_AUTH, auth.toString().toByteArray(Charsets.UTF_8))
    }

    private fun buildPacket(op: Int, body: ByteArray): ByteArray {
        val total = HEADER_LENGTH + body.size
        val buffer = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(total)
        buffer.putShort(HEADER_LENGTH.toShort())
        buffer.putShort(PROTOCOL_VERSION.toShort())
        buffer.putInt(op)
        buffer.putInt(1)
        buffer.put(body)
        return buffer.array()
    }

    private fun parsePackets(bytes: ByteArray) {
        var offset = 0
        while (offset + HEADER_LENGTH <= bytes.size) {
            val header = ByteBuffer.wrap(bytes, offset, bytes.size - offset).order(ByteOrder.BIG_ENDIAN)
            val total = header.int
            if (total < HEADER_LENGTH || offset + total > bytes.size) return
            val headerLength = header.short.toInt() and 0xFFFF
            val version = header.short.toInt() and 0xFFFF
            val operation = header.int
            if (headerLength < HEADER_LENGTH || headerLength > total) return
            val body = bytes.copyOfRange(offset + headerLength, offset + total)
            handlePacket(operation, version, body)
            offset += total
        }
    }

    private fun handlePacket(operation: Int, version: Int, body: ByteArray) {
        when (operation) {
            OP_HEARTBEAT_REPLY -> Unit
            OP_MESSAGE -> when (version) {
                PROTOCOL_VERSION_ZLIB -> {
                    val inflated = inflate(body)
                    if (inflated != null) {
                        val text = String(inflated, Charsets.UTF_8)
                        if (text.trimStart().startsWith("{")) {
                            parseJsonBody(inflated)
                        } else {
                            parsePackets(inflated)
                        }
                    }
                }
                // Brotli is not bundled; requesting protover=2 should avoid it.
                PROTOCOL_VERSION_BROTLI -> Unit
                else -> parseJsonBody(body)
            }
            OP_AUTH_REPLY -> {
                val code = runCatching {
                    JsonParser().parse(String(body, Charsets.UTF_8))
                        .asJsonObject.get("code")?.asInt
                }.getOrNull()
                if (code == 0) {
                    postState(LiveChatState.CONNECTED)
                } else {
                    postState(LiveChatState.DISCONNECTED)
                    scheduleReconnect()
                }
            }
        }
    }

    private fun parseJsonBody(body: ByteArray) {
        val text = String(body, Charsets.UTF_8)
        if (!text.trimStart().startsWith("{")) return
        LiveChatParser.parse(text) { messageSeq.incrementAndGet() }
            .forEach { event ->
                scope.launch {
                    if (!stopped) onEvent(event)
                }
            }
    }

    private fun inflate(data: ByteArray): ByteArray? {
        if (data.isEmpty()) return null
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val output = ByteArrayOutputStream(data.size * 4)
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                    continue
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray().takeIf { it.isNotEmpty() }
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) {
                Log.w(BilibiliApp.TAG, "LiveChatClient: zlib frame ignored (${t.javaClass.simpleName})")
            }
            null
        } finally {
            inflater.end()
        }
    }

    private fun postState(state: LiveChatState) {
        scope.launch {
            if (!stopped) onState(state)
        }
    }

    private companion object {
        const val DEFAULT_HOST = "broadcastlv.chat.bilibili.com"
        const val HEADER_LENGTH = 16
        const val PROTOCOL_VERSION = 1
        const val PROTOCOL_VERSION_ZLIB = 2
        const val PROTOCOL_VERSION_BROTLI = 3
        const val OP_HEARTBEAT = 2
        const val OP_HEARTBEAT_REPLY = 3
        const val OP_MESSAGE = 5
        const val OP_AUTH = 7
        const val OP_AUTH_REPLY = 8
        const val HEARTBEAT_INTERVAL_MS = 30_000L
        const val RECONNECT_DELAY_MS = 3_000L
    }
}
