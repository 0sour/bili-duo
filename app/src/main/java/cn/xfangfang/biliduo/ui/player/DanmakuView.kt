package cn.xfangfang.biliduo.ui.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.media3.common.Player
import cn.xfangfang.biliduo.data.model.DanmakuItem
import kotlin.math.max

/**
 * Canvas danmaku layer for the top screen. It reads the player position on
 * every animation frame, so scrolling stays smooth while the bottom console
 * only samples state at 4Hz. The view is transparent, non-focusable and
 * non-clickable.
 *
 * Lanes are assigned once per data/size change in chronological order, which
 * keeps dense sections readable without doing collision work every frame.
 */
class DanmakuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

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

    private var player: Player? = null
    private var sourceItems: List<DanmakuItem> = emptyList()
    private var items: List<DanmakuItem> = emptyList()
    private var blockedWords: Set<String> = emptySet()
    private var maxOnScreen: Int = 0
    private var opacity: Float = 0.9f
    private var speed: Float = 1f
    private var baseTextSizeSp: Float = 16f
    private var area: Float = 1f
    private var running = false
    private var attached = false
    private var listenerPlayer: Player? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateTicker()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateTicker()
        }
    }

    // Layout cache, rebuilt only when the input data or the view size changes.
    private var layoutWidth = 0
    private var layoutHeight = 0
    private var layoutItems: List<DanmakuItem>? = null
    private var layoutSpeed = 0f
    private var layoutBaseSp = 0f
    private var layoutArea = 0f
    private var laneOf = IntArray(0)
    private var zoneOf = IntArray(0)
    private var fixedLaneCount = 1
    private var scrollLaneCount = 1

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            // 在 Choreographer 回调里直接 invalidate() 对硬件层不一定触发重绘；
            // postInvalidate() 会走一次消息队列，稳定在下一帧调用 onDraw。
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
        player: Player?,
        items: List<DanmakuItem>,
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
        val unchanged = this.player === player &&
            this.sourceItems === items &&
            this.blockedWords == nextWords &&
            this.maxOnScreen == nextMax &&
            this.opacity == nextOpacity &&
            this.speed == nextSpeed &&
            this.baseTextSizeSp == nextSize &&
            this.area == nextArea
        if (unchanged) {
            updateTicker()
            return
        }
        this.player = player
        this.sourceItems = items
        this.blockedWords = nextWords
        this.items = filterBlockedDanmaku(items, nextWords)
        this.maxOnScreen = nextMax
        this.opacity = nextOpacity
        this.speed = nextSpeed
        this.baseTextSizeSp = nextSize
        this.area = nextArea
        observePlayer(player)
        updateTicker()
        postInvalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        observePlayer(player)
        updateTicker()
    }

    override fun onDetachedFromWindow() {
        attached = false
        listenerPlayer?.removeListener(playerListener)
        listenerPlayer = null
        stopTicker()
        super.onDetachedFromWindow()
    }

    private fun observePlayer(player: Player?) {
        if (listenerPlayer === player) return
        listenerPlayer?.removeListener(playerListener)
        listenerPlayer = player
        player?.addListener(playerListener)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        laneOf = IntArray(0)
        zoneOf = IntArray(0)
        layoutItems = null
    }

    private fun updateTicker() {
        val playing = player?.isPlaying == true
        if (attached && playing && items.isNotEmpty()) {
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
        val player = player ?: return
        if (items.isEmpty() || width == 0 || height == 0) return
        ensureLayout()

        val positionMs = player.currentPosition.coerceAtLeast(0L)
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val baseSize = baseTextSize()
        val laneHeight = laneHeight(baseSize)
        val topInset = laneHeight * 0.35f
        val bottomInset = laneHeight * 0.35f
        val contentHeight = viewHeight * area
        val windowMs = 12_000L

        var index = lowerBound(positionMs - windowMs)
        var drawnCount = 0
        while (index < items.size && items[index].timeMs <= positionMs) {
            val item = items[index]
            val age = positionMs - item.timeMs
            val fixed = item.mode == 4 || item.mode == 5
            val duration = durationMs(fixed)
            if (age <= duration) {
                if (!limitDanmakuOnScreen(drawnCount, maxOnScreen)) break
                val textSize = itemTextSize(item, baseSize)
                fillPaint.textSize = textSize
                strokePaint.textSize = textSize
                strokePaint.strokeWidth = textSize * 0.08f
                fillPaint.color = 0xFF000000.toInt() or (item.color and 0x00FFFFFF)
                strokePaint.color = Color.BLACK
                fillPaint.alpha = (255 * opacity).toInt().coerceIn(0, 255)
                strokePaint.alpha = (170 * opacity).toInt().coerceIn(0, 255)

                val textWidth = fillPaint.measureText(item.text)
                val zone = zoneOf[index]
                val lane = laneOf[index]
                val y = when (zone) {
                    ZONE_FIXED_TOP ->
                        topInset + laneHeight * (lane + 1) - baseSize * 0.18f
                    ZONE_FIXED_BOTTOM ->
                        contentHeight - bottomInset - laneHeight * lane - baseSize * 0.12f
                    else ->
                        topInset + laneHeight * (fixedLaneCount + lane + 1) - baseSize * 0.18f
                }
                val x = if (fixed) {
                    (viewWidth - textWidth) / 2f
                } else {
                    viewWidth - (age / duration) * (viewWidth + textWidth)
                }
                if (x < viewWidth && x + textWidth > 0f) {
                    canvas.drawText(item.text, x, y, strokePaint)
                    canvas.drawText(item.text, x, y, fillPaint)
                    drawnCount++
                }
            }
            index++
        }
    }

    private fun ensureLayout() {
        if (layoutWidth == width &&
            layoutHeight == height &&
            layoutItems === items &&
            layoutSpeed == speed &&
            layoutBaseSp == baseTextSizeSp &&
            layoutArea == area
        ) {
            return
        }
        layoutWidth = width
        layoutHeight = height
        layoutItems = items
        layoutSpeed = speed
        layoutBaseSp = baseTextSizeSp
        layoutArea = area

        val baseSize = baseTextSize()
        val laneHeight = laneHeight(baseSize)
        val topInset = laneHeight * 0.35f
        val bottomInset = laneHeight * 0.35f
        val contentHeight = height * area
        val usable = contentHeight * 0.82f - topInset - bottomInset
        val laneCount = (usable / laneHeight).toInt().coerceIn(3, 20)
        fixedLaneCount = (laneCount / 5).coerceIn(1, 3)
        scrollLaneCount = max(1, laneCount - fixedLaneCount * 2)

        laneOf = IntArray(items.size)
        zoneOf = IntArray(items.size)
        val scrollFree = FloatArray(scrollLaneCount)
        val topFree = FloatArray(fixedLaneCount)
        val bottomFree = FloatArray(fixedLaneCount)
        val gap = baseSize * 1.5f

        for (i in items.indices) {
            val item = items[i]
            val fixed = item.mode == 4 || item.mode == 5
            if (fixed) {
                val isBottom = item.mode == 4
                val free = if (isBottom) bottomFree else topFree
                val lane = earliestFreeLane(free, item.timeMs)
                zoneOf[i] = if (isBottom) ZONE_FIXED_BOTTOM else ZONE_FIXED_TOP
                laneOf[i] = lane
                free[lane] = max(free[lane], item.timeMs + FIXED_DURATION_MS)
            } else {
                measurePaint.textSize = itemTextSize(item, baseSize)
                val textWidth = measurePaint.measureText(item.text)
                val lane = earliestFreeLane(scrollFree, item.timeMs)
                zoneOf[i] = ZONE_SCROLL
                laneOf[i] = lane
                val duration = durationMs(fixed = false)
                val busy = duration * (textWidth + gap) / (width + textWidth)
                scrollFree[lane] = max(scrollFree[lane], item.timeMs + busy)
            }
        }
    }

    private fun earliestFreeLane(free: FloatArray, timeMs: Long): Int {
        var best = 0
        for (i in free.indices) {
            if (free[i] <= timeMs) return i
            if (free[i] < free[best]) best = i
        }
        return best
    }

    private fun baseTextSize(): Float =
        height * 0.026f * (baseTextSizeSp / 16f)

    private fun laneHeight(baseSize: Float): Float = baseSize * 1.6f

    private fun itemTextSize(item: DanmakuItem, baseSize: Float): Float =
        (baseSize * (item.fontSize / 25f)).coerceIn(baseSize * 0.75f, baseSize * 1.3f)

    private fun durationMs(fixed: Boolean): Float = if (fixed) {
        FIXED_DURATION_MS
    } else {
        (8_000f / speed).coerceAtLeast(2_000f)
    }

    private fun lowerBound(timeMs: Long): Int {
        var low = 0
        var high = items.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (items[mid].timeMs < timeMs) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return low
    }

    private companion object {
        const val ZONE_SCROLL = 0
        const val ZONE_FIXED_TOP = 1
        const val ZONE_FIXED_BOTTOM = 2
        const val FIXED_DURATION_MS = 4_000f
    }
}
