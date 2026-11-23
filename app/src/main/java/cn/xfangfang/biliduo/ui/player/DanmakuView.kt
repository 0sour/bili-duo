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
    private var blockedRegex: String = ""
    private var shieldLevel: Int = 0
    private var showScroll: Boolean = true
    private var showTop: Boolean = true
    private var showBottom: Boolean = true
    private var blockedUidHashes: Set<String> = emptySet()
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
    /** Text widths measured during lane assignment; avoids per-frame measureText. */
    private var widthOf = FloatArray(0)
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
        blockedRegex: String = "",
        shieldLevel: Int = 0,
        showScroll: Boolean = true,
        showTop: Boolean = true,
        showBottom: Boolean = true,
        blockedUidHashes: Set<String> = emptySet(),
        maxOnScreen: Int = 0,
    ) {
        val nextOpacity = opacity.coerceIn(0.2f, 1f)
        val nextSpeed = speed.coerceIn(0.5f, 2f)
        val nextSize = textSizeSp.coerceIn(12f, 24f)
        val nextArea = area.coerceIn(0.25f, 1f)
        val nextWords = blockedWords.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val nextRegex = blockedRegex.trim()
        val nextLevel = shieldLevel.coerceIn(0, 10)
        val nextBlockedUids = blockedUidHashes.filter { it.isNotBlank() }.toSet()
        val nextMax = maxOnScreen.coerceIn(0, 200)
        val unchanged = this.player === player &&
            this.sourceItems === items &&
            this.blockedWords == nextWords &&
            this.blockedRegex == nextRegex &&
            this.shieldLevel == nextLevel &&
            this.showScroll == showScroll &&
            this.showTop == showTop &&
            this.showBottom == showBottom &&
            this.blockedUidHashes == nextBlockedUids &&
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
        // Rebuilt when the list changes; the view may be reused by Compose.
        layoutItems = null
        this.blockedWords = nextWords
        this.blockedRegex = nextRegex
        this.shieldLevel = nextLevel
        this.showScroll = showScroll
        this.showTop = showTop
        this.showBottom = showBottom
        this.blockedUidHashes = nextBlockedUids
        this.items = filterDanmaku(
            items = items,
            blockedWords = nextWords,
            blockedRegex = compileDanmakuRegex(nextRegex),
            shieldLevel = nextLevel,
            showScroll = showScroll,
            showTop = showTop,
            showBottom = showBottom,
            blockedUidHashes = nextBlockedUids,
        )
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
        widthOf = FloatArray(0)
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
        val pxPerMs = scrollPxPerMs(viewWidth)
        // Wide or slow items can stay on screen longer than 12s. The lookup
        // window has to cover the slowest text at the current speed setting.
        val windowMs = ((viewWidth * 2.4f) / pxPerMs).toLong().coerceAtLeast(12_000L)

        var index = lowerBound(positionMs - windowMs)
        var drawnCount = 0
        while (index < items.size && items[index].timeMs <= positionMs) {
            val item = items[index]
            val zone = zoneOf[index]
            val lane = laneOf[index]
            val age = positionMs - item.timeMs
            val fixed = zone != ZONE_SCROLL
            // lane < 0 means the scene was too dense for a free lane; showing
            // it would overlap the item already in that lane.
            if (lane >= 0 && (!fixed || age <= FIXED_DURATION_MS)) {
                val textWidth = if (fixed) {
                    measurePaint.textSize = itemTextSize(item, baseSize)
                    measurePaint.measureText(item.text)
                } else {
                    widthOf[index]
                }
                // Cheap off-screen rejection before touching the paint setup.
                if (!fixed && viewWidth - age * pxPerMs + textWidth <= 0f) {
                    index++
                    continue
                }
                if (!limitDanmakuOnScreen(drawnCount, maxOnScreen)) break
                val textSize = itemTextSize(item, baseSize)
                fillPaint.textSize = textSize
                strokePaint.textSize = textSize
                strokePaint.strokeWidth = textSize * 0.08f
                fillPaint.color = 0xFF000000.toInt() or (item.color and 0x00FFFFFF)
                strokePaint.color = Color.BLACK
                fillPaint.alpha = (255 * opacity).toInt().coerceIn(0, 255)
                strokePaint.alpha = (170 * opacity).toInt().coerceIn(0, 255)

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
                    // Constant px/ms for every scrolling item, matching the
                    // lane reservation in ensureLayout().
                    viewWidth - age * pxPerMs
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
        val laneCount = (usable / laneHeight).toInt().coerceIn(3, 24)
        fixedLaneCount = (laneCount / 5).coerceIn(1, 3)
        scrollLaneCount = max(1, laneCount - fixedLaneCount * 2)

        laneOf = IntArray(items.size)
        zoneOf = IntArray(items.size)
        widthOf = FloatArray(items.size)
        val scrollFree = FloatArray(scrollLaneCount)
        val topFree = FloatArray(fixedLaneCount)
        val bottomFree = FloatArray(fixedLaneCount)
        val pxPerMs = scrollPxPerMs(width.toFloat())
        val gap = baseSize * 1.2f

        for (i in items.indices) {
            val item = items[i]
            val fixed = item.mode == 4 || item.mode == 5
            if (fixed) {
                val isBottom = item.mode == 4
                val free = if (isBottom) bottomFree else topFree
                val lane = earliestFreeLane(free, item.timeMs)
                zoneOf[i] = if (isBottom) ZONE_FIXED_BOTTOM else ZONE_FIXED_TOP
                laneOf[i] = lane ?: -1
                if (lane != null) {
                    free[lane] = max(free[lane], item.timeMs + FIXED_DURATION_MS)
                }
            } else {
                measurePaint.textSize = itemTextSize(item, baseSize)
                val textWidth = measurePaint.measureText(item.text)
                widthOf[i] = textWidth
                val lane = earliestFreeLane(scrollFree, item.timeMs)
                zoneOf[i] = ZONE_SCROLL
                laneOf[i] = lane ?: -1
                if (lane != null) {
                    // All scrolling items share one px/ms speed, so a lane is
                    // free as soon as the previous tail has entered the screen
                    // plus a gap. The old same-duration scheme made wide texts
                    // faster, so they could catch up and overlap narrower ones.
                    scrollFree[lane] = max(
                        scrollFree[lane],
                        item.timeMs + (textWidth + gap) / pxPerMs,
                    )
                }
            }
        }
    }

    /** Returns a lane free at [timeMs], or null when the scene is too dense. */
    private fun earliestFreeLane(free: FloatArray, timeMs: Long): Int? {
        for (i in free.indices) {
            if (free[i] <= timeMs) return i
        }
        return null
    }

    private fun baseTextSize(): Float =
        // 16sp is the "standard" tier. At 1080p this is ~37px (was ~28px);
        // 24sp reaches ~55px so the largest tier is no longer smaller than
        // other players' default.
        height * 0.034f * (baseTextSizeSp / 16f)

    private fun laneHeight(baseSize: Float): Float = baseSize * 1.45f

    private fun itemTextSize(item: DanmakuItem, baseSize: Float): Float =
        (baseSize * (item.fontSize / 25f)).coerceIn(baseSize * 0.75f, baseSize * 1.3f)

    /** Constant scrolling speed for every item: one screen width in 8s at 1x. */
    private fun scrollPxPerMs(viewWidth: Float): Float =
        (viewWidth / 8_000f) * speed.coerceAtLeast(0.1f)

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
