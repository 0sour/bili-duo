package cn.xfangfang.biliduo.ui.live

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import cn.xfangfang.biliduo.data.model.LiveChatMessage

/**
 * Scrolling live danmaku for the top screen. Unlike the VOD [DanmakuView] this
 * layer has no player timeline: messages are queued as they arrive and each one
 * is assigned a free lane when it can start.
 *
 * The view is transparent, non-focusable and non-clickable.
 */
class LiveDanmakuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private data class Node(
        val text: String,
        val width: Float,
        val startMs: Long,
        val durationMs: Float,
        val lane: Int,
    )

    private val nodes = mutableListOf<Node>()
    private val pending = ArrayDeque<String>()
    private var laneFreeAt = FloatArray(0)
    private var roomId = 0L
    private var lastMessageId = 0L
    private var enabled = true
    private var opacity = 0.9f
    private var speed = 1f
    private var textSizeSp = 16f
    private var area = 1f
    private var maxOnScreen = 0
    private var blockedWords: Set<String> = emptySet()
    private var running = false
    private var attached = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        color = Color.BLACK
        style = Paint.Style.STROKE
    }
    private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            postInvalidate()
            postOnAnimation(this)
        }
    }

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun bind(
        roomId: Long,
        messages: List<LiveChatMessage>,
        enabled: Boolean = true,
        opacity: Float = 0.9f,
        speed: Float = 1f,
        textSizeSp: Float = 16f,
        area: Float = 1f,
        blockedWords: Set<String> = emptySet(),
        maxOnScreen: Int = 0,
    ) {
        val nextOpacity = opacity.coerceIn(0.2f, 1f)
        val nextSpeed = speed.coerceIn(0.5f, 2f)
        val nextSize = textSizeSp.coerceIn(12f, 24f)
        val nextArea = area.coerceIn(0.25f, 1f)
        val nextWords = blockedWords.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val nextMax = maxOnScreen.coerceIn(0, 200)

        val reset = roomId != this.roomId ||
            (messages.isNotEmpty() && messages.last().id < lastMessageId)
        if (reset) {
            nodes.clear()
            pending.clear()
            laneFreeAt = FloatArray(0)
            lastMessageId = 0L
        }
        val metricsChanged = nextSize != this.textSizeSp || nextArea != this.area
        if (metricsChanged) {
            // Existing nodes were measured with the old size/area.
            nodes.clear()
            pending.clear()
        }
        if (!enabled && this.enabled) {
            nodes.clear()
            pending.clear()
        }

        this.roomId = roomId
        this.enabled = enabled
        this.opacity = nextOpacity
        this.speed = nextSpeed
        this.textSizeSp = nextSize
        this.area = nextArea
        this.blockedWords = nextWords
        this.maxOnScreen = nextMax

        for (message in messages) {
            if (message.id <= lastMessageId) continue
            lastMessageId = message.id
            if (!enabled) continue
            if (message.isSuperChat) continue
            val text = message.text.trim()
            if (text.isEmpty()) continue
            if (nextWords.any { text.contains(it, ignoreCase = true) }) continue
            pending.addLast(text)
        }
        while (pending.size > MAX_PENDING) pending.removeFirst()
        if (!enabled) {
            stopTicker()
            postInvalidate()
            return
        }
        updateTicker()
        postInvalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        updateTicker()
    }

    override fun onDetachedFromWindow() {
        attached = false
        stopTicker()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        nodes.clear()
        laneFreeAt = FloatArray(0)
    }

    private fun updateTicker() {
        if (attached && enabled && (nodes.isNotEmpty() || pending.isNotEmpty())) {
            if (!running) {
                running = true
                postOnAnimation(ticker)
            }
        } else {
            stopTicker()
        }
    }

    private fun stopTicker() {
        running = false
        removeCallbacks(ticker)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!enabled || width == 0 || height == 0) return
        val now = SystemClock.uptimeMillis()
        val baseSize = height * 0.026f * (textSizeSp / 16f)
        val laneHeight = baseSize * 1.6f
        val contentHeight = height * area
        val usable = contentHeight * 0.82f - laneHeight * 0.7f
        val laneCount = (usable / laneHeight).toInt().coerceIn(2, 20)
        if (laneFreeAt.size != laneCount) {
            laneFreeAt = FloatArray(laneCount)
            nodes.clear()
        }

        drainPending(now, laneCount, baseSize)
        nodes.removeAll { now - it.startMs > it.durationMs }

        val viewWidth = width.toFloat()
        val topInset = laneHeight * 0.35f
        fillPaint.textSize = baseSize
        strokePaint.textSize = baseSize
        strokePaint.strokeWidth = baseSize * 0.08f
        fillPaint.alpha = (255 * opacity).toInt().coerceIn(0, 255)
        strokePaint.alpha = (170 * opacity).toInt().coerceIn(0, 255)

        for (node in nodes) {
            val age = (now - node.startMs).toFloat()
            if (age > node.durationMs) continue
            val x = viewWidth - (age / node.durationMs) * (viewWidth + node.width)
            val y = topInset + laneHeight * (node.lane + 1) - baseSize * 0.18f
            canvas.drawText(node.text, x, y, strokePaint)
            canvas.drawText(node.text, x, y, fillPaint)
        }
        updateTicker()
    }

    private fun drainPending(now: Long, laneCount: Int, baseSize: Float) {
        if (pending.isEmpty()) return
        val viewWidth = width.toFloat()
        val duration = (8_000f / speed).coerceAtLeast(2_000f)
        val gap = baseSize * 1.5f
        while (pending.isNotEmpty()) {
            if (maxOnScreen > 0 && nodes.size >= maxOnScreen) {
                pending.clear()
                break
            }
            var lane = -1
            for (i in 0 until laneCount) {
                if (laneFreeAt[i] <= now) {
                    lane = i
                    break
                }
            }
            if (lane < 0) break
            val text = pending.removeFirst()
            measurePaint.textSize = baseSize
            val textWidth = measurePaint.measureText(text)
            laneFreeAt[lane] = now + duration * (textWidth + gap) / (viewWidth + textWidth)
            nodes += Node(
                text = text,
                width = textWidth,
                startMs = now,
                durationMs = duration,
                lane = lane,
            )
        }
    }

    private companion object {
        const val MAX_PENDING = 60
    }
}
