package cn.xfangfang.biliduo.ui.player

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.Player
import cn.xfangfang.biliduo.data.model.DanmakuItem
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.controller.IDanmakuView
import master.flame.danmaku.danmaku.model.AbsDanmakuSync
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.Duration
import master.flame.danmaku.danmaku.model.IDanmakus
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.AndroidDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.model.android.DanmakuFactory
import master.flame.danmaku.danmaku.model.android.Danmakus
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import master.flame.danmaku.ui.widget.DanmakuView as DfmView

/**
 * Video danmaku powered by DanmakuFlameMaster.
 *
 * The app still owns XML parsing, filtering and preferences; DFM only allocates
 * lanes and draws. Critical rules:
 *  - `setCallback` must happen before `prepare`; `start` must wait for the
 *    `prepared()` callback. Calling start() earlier wipes DFM's PREPARE retry.
 *  - `seekTo` removes DFM's update/resume messages; always follow it with
 *    `resume()` or `pause()`.
 *  - Every BaseDanmaku needs a non-null timer, flags, and a unique index.
 *  - ExoPlayer may only be read on the main thread; DFM's sync runs on its own
 *    handler thread, so a main-thread sampler publishes a volatile snapshot.
 *
 * A new instance is expected for every video/engine/list change; TopScreen
 * keys by cid, engine, list identity and layout-affecting settings.
 */
class DfmDanmakuView(context: Context) : FrameLayout(context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var dfm: DfmView? = null
    private var config: DanmakuContext? = null
    private var areaDisplayer: AreaAndroidDisplayer? = null
    private var prepared = false
    private var preparing = false
    private var attached = false
    private var viewHeight = 0
    private var generation = 0

    private var pending: Binding? = null

    private var observedPlayer: Player? = null
    private var playerListener: Player.Listener? = null
    private var sampler: Runnable? = null
    private var sync: PlayerSync? = null

    private var appliedPlayer: Player? = null
    private var appliedSource: List<DanmakuItem>? = null
    private var appliedRenderKey: RenderKey? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
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
        allowOverlap: Boolean = false,
    ) {
        val nextOpacity = opacity.coerceIn(0.2f, 1f)
        val nextSpeed = speed.coerceIn(0.5f, 2f)
        val nextSize = textSizeSp.coerceIn(12f, 24f)
        val nextArea = area.coerceIn(0.25f, 1f)
        val nextMax = maxOnScreen.coerceIn(0, 200)
        val renderKey = RenderKey(
            textSizeSp = nextSize,
            area = nextArea,
            blockedWords = blockedWords.map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            blockedRegex = blockedRegex.trim(),
            shieldLevel = shieldLevel.coerceIn(0, 10),
            showScroll = showScroll,
            showTop = showTop,
            showBottom = showBottom,
            blockedUidHashes = blockedUidHashes.filter { it.isNotBlank() }.toSet(),
        )

        val needsRebuild = player !== appliedPlayer ||
            items !== appliedSource ||
            renderKey != appliedRenderKey
        appliedPlayer = player
        appliedSource = items
        appliedRenderKey = renderKey

        val binding = Binding(
            player = player,
            items = items,
            opacity = nextOpacity,
            speed = nextSpeed,
            textSizeSp = nextSize,
            area = nextArea,
            maxOnScreen = nextMax,
            allowOverlap = allowOverlap,
            renderKey = renderKey,
        )
        pending = binding
        areaDisplayer?.area = nextArea

        if (!needsRebuild) {
            applyLiveSettings(binding)
            return
        }

        if (!attached) return
        if (!prepared) {
            if (!preparing) prepare(binding)
            return
        }
        // List/layout changes are handled by recreating this view in TopScreen
        // (DFM's prepare is one-shot). Only live appearance changes remain here.
        applyLiveSettings(binding)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        ensureDfm()
        pending?.let { if (!prepared && !preparing) prepare(it) }
    }

    override fun onDetachedFromWindow() {
        attached = false
        generation++
        stopPlayerSync()
        detachPlayer()
        dfm?.release()
        dfm?.let { removeView(it) }
        dfm = null
        config = null
        areaDisplayer = null
        prepared = false
        preparing = false
        pending = null
        appliedPlayer = null
        appliedSource = null
        appliedRenderKey = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h > 0) viewHeight = h
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (!prepared) return
        if (visibility == View.VISIBLE) {
            if (observedPlayer?.isPlaying == true) dfm?.resume()
        } else {
            dfm?.pause()
        }
    }

    private fun ensureDfm(): DfmView {
        dfm?.let { return it }
        val view = DfmView(context)
        view.isClickable = false
        view.isFocusable = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        view.setDrawingThreadType(IDanmakuView.THREAD_TYPE_HIGH_PRIORITY)
        // Cached glyphs are the main win on dense scenes; transparency is
        // applied through the displayer paint, not by rebuilding the cache.
        view.enableDanmakuDrawingCache(true)
        addView(
            view,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        dfm = view
        return view
    }

    private fun prepare(binding: Binding) {
        if (prepared || preparing) return
        val player = binding.player ?: return
        val view = ensureDfm()
        preparePlayer(player)

        val playerSync = PlayerSync().also {
            it.update(player.currentPosition.coerceAtLeast(0L), player.isPlaying)
        }
        sync = playerSync

        val areaView = AreaAndroidDisplayer(binding.area)
        val context = DanmakuContext.create()
        context.mDisplayer = areaView
        context.setDuplicateMergingEnabled(false)
        context.setDanmakuTransparency(binding.opacity)
        context.setMaximumVisibleSizeInScreen(binding.maxOnScreen)
        context.setDanmakuMargin((resources.displayMetrics.density * 6f).toInt())
        context.setDanmakuSync(playerSync)
        // Pre-calibrate to ~8 s at 1x; DFM computes its real base duration from
        // the viewport when the parser creates the first item.
        context.setScrollSpeedFactor(0.88f / binding.speed)
        applyOverlapPolicy(context, binding.allowOverlap)
        applyTextStyle(context, binding.textSizeSp)
        areaDisplayer = areaView
        config = context

        preparing = true
        val prepareGeneration = generation
        view.setCallback(object : DrawHandler.Callback {
            override fun prepared() {
                mainHandler.post {
                    if (!attached || generation != prepareGeneration) return@post
                    onPrepared(binding)
                }
            }

            override fun updateTimer(timer: DanmakuTimer) = Unit

            override fun danmakuShown(danmaku: BaseDanmaku) = Unit

            override fun drawingFinished() = Unit
        })
        // Do not call start() here. DFM re-posts PREPARE until onLayout has run;
        // start() removes those messages and leaves the view permanently empty.
        view.prepare(ListParser(binding, ::currentViewHeight), context)
        startPlayerSync(player, playerSync)
    }

    private fun onPrepared(binding: Binding) {
        val view = dfm ?: return
        val player = observedPlayer ?: return
        prepared = true
        preparing = false

        // The viewport is now known; calibrate the scroll duration.
        applySpeed(binding.speed)
        applyTextStyle(config, binding.textSizeSp)
        view.show()
        val position = player.currentPosition.coerceAtLeast(0L)
        view.start(position)
        if (!player.isPlaying) view.pause()
        applyLiveSettings(binding)
    }

    private fun applyLiveSettings(binding: Binding) {
        if (!prepared) return
        val context = config ?: return
        val view = dfm ?: return
        context.setDanmakuTransparency(binding.opacity)
        context.setMaximumVisibleSizeInScreen(binding.maxOnScreen)
        applyOverlapPolicy(context, binding.allowOverlap)
        applySpeed(binding.speed)
        view.forceRender()
    }

    private fun applySpeed(speed: Float) {
        val context = config ?: return
        context.setScrollSpeedFactor(
            danmakuScrollSpeedFactor(speed, context.mDanmakuFactory.REAL_DANMAKU_DURATION),
        )
    }

    private fun applyOverlapPolicy(context: DanmakuContext, allowOverlap: Boolean) {
        val prevent = !allowOverlap
        context.preventOverlapping(
            mapOf(
                BaseDanmaku.TYPE_SCROLL_RL to prevent,
                BaseDanmaku.TYPE_SCROLL_LR to prevent,
                BaseDanmaku.TYPE_FIX_TOP to prevent,
                BaseDanmaku.TYPE_FIX_BOTTOM to prevent,
            ),
        )
    }

    private fun applyTextStyle(context: DanmakuContext?, textSizeSp: Float) {
        context ?: return
        val base = danmakuBaseTextPx(currentViewHeight(), textSizeSp)
        context.setDanmakuBold(true)
        // Do not call setMarginTop(): it collapses the custom area displayer
        // to a single row. The displayer already uses the full view height.
        context.setDanmakuStyle(
            IDisplayer.DANMAKU_STYLE_STROKEN,
            base * 0.08f,
        )
    }

    private fun preparePlayer(player: Player) {
        if (observedPlayer === player && playerListener != null) return
        detachPlayer()
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!prepared) return
                if (isPlaying) dfm?.resume() else dfm?.pause()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (!prepared) return
                val view = dfm ?: return
                view.seekTo(newPosition.positionMs)
                if (player.isPlaying) view.resume() else view.pause()
            }
        }
        observedPlayer = player
        playerListener = listener
        player.addListener(listener)
    }

    private fun detachPlayer() {
        val player = observedPlayer
        val listener = playerListener
        if (player != null && listener != null) player.removeListener(listener)
        observedPlayer = null
        playerListener = null
    }

    private fun startPlayerSync(player: Player, playerSync: PlayerSync) {
        stopSampler()
        sync = playerSync
        val runnable = object : Runnable {
            override fun run() {
                playerSync.update(
                    player.currentPosition.coerceAtLeast(0L),
                    player.isPlaying,
                )
                mainHandler.postDelayed(this, 30L)
            }
        }
        sampler = runnable
        mainHandler.post(runnable)
    }

    private fun stopPlayerSync() {
        stopSampler()
        sync = null
    }

    private fun stopSampler() {
        sampler?.let { mainHandler.removeCallbacks(it) }
        sampler = null
    }

    private fun currentViewHeight(): Int =
        dfm?.height?.takeIf { it > 0 }
            ?: viewHeight.takeIf { it > 0 }
            ?: height.takeIf { it > 0 }
            ?: 1

    private data class RenderKey(
        val textSizeSp: Float,
        val area: Float,
        val blockedWords: Set<String>,
        val blockedRegex: String,
        val shieldLevel: Int,
        val showScroll: Boolean,
        val showTop: Boolean,
        val showBottom: Boolean,
        val blockedUidHashes: Set<String>,
    )

    private data class Binding(
        val player: Player?,
        val items: List<DanmakuItem>,
        val opacity: Float,
        val speed: Float,
        val textSizeSp: Float,
        val area: Float,
        val maxOnScreen: Int,
        val allowOverlap: Boolean,
        val renderKey: RenderKey,
    )

    /** Runs on DFM's parser thread; filtering/mapping stays off the main thread. */
    private class ListParser(
        private val binding: Binding,
        private val viewHeightProvider: () -> Int,
    ) : BaseDanmakuParser() {
        override fun parse(): IDanmakus {
            val context = mContext ?: return Danmakus()
            val height = viewHeightProvider()
            val specs = mapDanmakuItems(
                items = binding.items,
                viewHeightPx = height,
                textSizeSp = binding.textSizeSp,
                blockedWords = binding.renderKey.blockedWords,
                blockedRegex = binding.renderKey.blockedRegex,
                shieldLevel = binding.renderKey.shieldLevel,
                showScroll = binding.renderKey.showScroll,
                showTop = binding.renderKey.showTop,
                showBottom = binding.renderKey.showBottom,
                blockedUidHashes = binding.renderKey.blockedUidHashes,
            )
            val result = Danmakus()
            for (spec in specs) {
                val danmaku = buildDanmaku(spec, context) ?: continue
                // DFM's isTimeOut() returns true for a null timer and would
                // silently drop the whole list.
                danmaku.setTimer(mTimer)
                result.addItem(danmaku)
            }
            return result
        }
    }

    private class PlayerSync : AbsDanmakuSync() {
        @Volatile private var position = 0L
        @Volatile private var playing = false

        fun update(positionMs: Long, isPlaying: Boolean) {
            position = positionMs
            playing = isPlaying
        }

        override fun getUptimeMillis(): Long = position.coerceAtLeast(0L)

        override fun getSyncState(): Int =
            if (playing) SYNC_STATE_PLAYING else SYNC_STATE_HALT

        override fun isSyncPlayingState(): Boolean = true

        // The player is the clock. A large threshold makes 1.25x/1.5x/2x
        // playback visibly drift; synchronize within a frame or two.
        override fun getThresholdTimeMills(): Long = 32L
    }

    private class AreaAndroidDisplayer(
        initialArea: Float,
    ) : AndroidDisplayer() {
        @Volatile
        var area: Float = initialArea.coerceIn(0.25f, 1f)

        override fun getHeight(): Int {
            return (super.getHeight() * area.coerceIn(0.25f, 1f))
                .toInt()
                .coerceAtLeast(1)
        }
    }
}

private fun buildDanmaku(
    spec: DfmDanmakuSpec,
    config: DanmakuContext,
): BaseDanmaku? {
    val type = when (spec.type) {
        DfmDanmakuType.SCROLL_RL -> BaseDanmaku.TYPE_SCROLL_RL
        DfmDanmakuType.SCROLL_LR -> BaseDanmaku.TYPE_SCROLL_LR
        DfmDanmakuType.FIX_TOP -> BaseDanmaku.TYPE_FIX_TOP
        DfmDanmakuType.FIX_BOTTOM -> BaseDanmaku.TYPE_FIX_BOTTOM
    }
    val danmaku = config.mDanmakuFactory.createDanmaku(type, config) ?: return null
    danmaku.text = spec.text
    danmaku.setTime(spec.timeMs)
    danmaku.textSize = spec.textSizePx
    danmaku.textColor = spec.textColor
    danmaku.textShadowColor = spec.shadowColor
    danmaku.userHash = spec.userHash.orEmpty()
    danmaku.index = spec.index
    // priority > 0 makes DFM skip every filter/overlap limit; weight is already
    // applied by the app-side filter.
    danmaku.priority = 0
    danmaku.isLive = false
    danmaku.flags = config.mGlobalFlagValues
    spec.fixedDurationMs?.let { danmaku.setDuration(Duration(it)) }
    return danmaku
}
