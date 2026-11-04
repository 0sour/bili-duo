package cn.xfangfang.biliduo.ui.player

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import cn.xfangfang.biliduo.BilibiliApp
import cn.xfangfang.biliduo.BuildConfig
import cn.xfangfang.biliduo.data.api.BilibiliApi
import cn.xfangfang.biliduo.data.local.AppSettings
import cn.xfangfang.biliduo.data.local.WifiMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The only ExoPlayer owner in the app.
 *
 * The top screen renders [player] through a PlayerView with `useController=false`;
 * the bottom console sends transport commands here. The controller reacts to
 * [PlayerViewModel.uiState] (play URL / page / quality changes) but never loads
 * metadata itself.
 */
class PlayerController(
    context: Context,
    private val viewModel: PlayerViewModel,
    private val scope: CoroutineScope,
) {
    data class State(
        val isActive: Boolean = false,
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val playbackState: Int = Player.STATE_IDLE,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val bufferedMs: Long = 0L,
        val speed: Float = 1f,
        val volume: Float = 1f,
        val error: String? = null,
        val ended: Boolean = false,
        val hasRenderedFrame: Boolean = false,
    )

    private val appContext = context.applicationContext
    private val dataSourceFactory = DefaultDataSource.Factory(
        appContext,
        OkHttpDataSource.Factory(BilibiliApi.httpClient),
    )

    val player: ExoPlayer = ExoPlayer.Builder(appContext).build().apply {
        playWhenReady = false
        repeatMode = Player.REPEAT_MODE_OFF
        setHandleAudioBecomingNoisy(true)
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (BuildConfig.DEBUG) {
                    Log.d(BilibiliApp.TAG, "PlayerController: state=${stateName(state)}")
                }
                if (state == Player.STATE_ENDED) {
                    handleEnded()
                }
                syncState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (BuildConfig.DEBUG) {
                    Log.d(BilibiliApp.TAG, "PlayerController: isPlaying=$isPlaying")
                }
                if (!isPlaying) reportNow()
                syncState()
            }

            override fun onPlayerError(error: PlaybackException) {
                // Bilibili's HLS live window is very short. ExoPlayer often
                // reports BehindLiveWindow right after prepare; recreate the
                // media source at the live edge instead of showing an error.
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW &&
                    viewModel.uiState.value.isLive
                ) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastLiveRecoveryAt > 5_000L) {
                        lastLiveRecoveryAt = now
                        lastUrl = null
                        lastCid = null
                        _state.update {
                            it.copy(error = null, isBuffering = true, isPlaying = false)
                        }
                        if (BuildConfig.DEBUG) {
                            Log.d(BilibiliApp.TAG, "PlayerController: live behind window, refreshing")
                        }
                        viewModel.refreshLive()
                        return
                    }
                }
                Log.e(BilibiliApp.TAG, "PlayerController: error=${error.message}", error)
                _state.update {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false,
                        error = error.message ?: "播放出错",
                    )
                }
            }

            override fun onRenderedFirstFrame() {
                _state.update { it.copy(hasRenderedFrame = true) }
            }
        })
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var enabled = false
    private var released = false
    private var lastUrl: String? = null
    private var lastCid: Long? = null
    private var pendingSeekMs: Long? = null
    private var activeAid: Long = 0L
    private var activeCid: Long? = null
    private var pausedByWifi = false
    private var handlingEnded = false
    private var lastLiveRecoveryAt = 0L

    init {
        scope.launch {
            viewModel.uiState.collect { uiState ->
                if (!enabled) return@collect
                handleUiState(uiState)
            }
        }
        scope.launch {
            while (true) {
                if (enabled && _state.value.isActive) syncState()
                delay(250L)
            }
        }
        scope.launch {
            while (true) {
                delay(30_000L)
                if (enabled && player.isPlaying) reportNow()
            }
        }
        scope.launch {
            WifiMonitor.wifiLost.collect {
                if (!enabled || !AppSettings.wifiOnlyPlayback) return@collect
                val ui = viewModel.uiState.value
                if ((ui.videoInfo == null && !ui.isLive) || !player.playWhenReady) return@collect
                pausedByWifi = true
                reportNow()
                player.pause()
            }
        }
        scope.launch {
            WifiMonitor.wifiRestored.collect {
                if (!enabled || !pausedByWifi) return@collect
                pausedByWifi = false
                player.play()
            }
        }
    }

    /**
     * Enable only when the second screen is actually hosting the console.
     * In single-screen fallback the legacy PlayerScreen owns playback itself.
     */
    fun setEnabled(value: Boolean) {
        if (released || enabled == value) return
        enabled = value
        if (!value) {
            stop()
        } else {
            handleUiState(viewModel.uiState.value)
        }
    }

    fun togglePlay() {
        if (released) return
        when {
            _state.value.ended -> {
                player.seekTo(0L)
                _state.update { it.copy(ended = false) }
                player.play()
            }
            player.isPlaying -> player.pause()
            else -> {
                if (_state.value.error != null) retry() else player.play()
            }
        }
    }

    fun play() {
        if (released) return
        if (_state.value.ended) {
            player.seekTo(0L)
            _state.update { it.copy(ended = false) }
        }
        if (_state.value.error != null) retry() else player.play()
    }

    fun pause() {
        if (!released) player.pause()
    }

    fun seekTo(positionMs: Long) {
        if (released) return
        val duration = player.duration
        val max = if (duration == C.TIME_UNSET || duration <= 0L) Long.MAX_VALUE else duration
        val target = positionMs.coerceIn(0L, max)
        player.seekTo(target)
        syncState()
    }

    fun seekBy(deltaMs: Long) {
        if (released) return
        seekTo(player.currentPosition + deltaMs)
    }

    /** Jump back to the live edge (or default window start) and resume. */
    fun seekToLive() {
        if (released) return
        player.seekToDefaultPosition()
        if (!player.isPlaying) player.play()
    }

    fun setSpeed(speed: Float) {
        if (released) return
        player.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun setVolume(volume: Float) {
        if (released) return
        val v = volume.coerceIn(0f, 1f)
        player.volume = v
        _state.update { it.copy(volume = v) }
    }

    fun previousPage() = shiftPage(-1)

    fun nextPage() = shiftPage(+1)

    private fun shiftPage(delta: Int) {
        if (released) return
        reportNow()
        val ui = viewModel.uiState.value
        val index = ui.pages.indexOfFirst { it.cid == ui.currentPage?.cid }
        if (index < 0) return
        val target = ui.pages.getOrNull(index + delta) ?: return
        viewModel.selectPage(target)
    }

    fun retry() {
        if (released) return
        _state.update { it.copy(error = null, isBuffering = true) }
        val ui = viewModel.uiState.value
        if (ui.isLive) {
            // A live URL is short-lived; fetch a fresh play URL instead of
            // reusing the expired one.
            viewModel.refreshLive()
            return
        }
        if (ui.videoInfo != null || ui.videoUrl == null) {
            // Online: fetch a fresh play URL (CDN URLs expire). Local with no
            // URL is also handled by reloadCurrent's no-op path.
            viewModel.reloadCurrent()
        } else {
            // Local/unknown source: re-prepare the same URL.
            lastUrl = null
            handleUiState(ui)
        }
    }

    /** Called from Activity.onStop: pause, but keep the media session for return. */
    fun onHostStop() {
        if (released) return
        if (player.playWhenReady || player.isPlaying) {
            reportNow()
            player.pause()
        }
    }

    /** Leave the player: report progress and clear the top surface. */
    fun stop() {
        if (released) return
        reportNow()
        player.stop()
        player.clearMediaItems()
        lastUrl = null
        lastCid = null
        pendingSeekMs = null
        activeAid = 0L
        activeCid = null
        handlingEnded = false
        _state.value = State()
    }

    fun release() {
        if (released) return
        stop()
        released = true
        player.release()
    }

    /** Debug/test hook: keep playback from touching the account's watch history. */
    fun setProgressReportingEnabled(enabled: Boolean) {
        progressReportingEnabled = enabled
    }

    private var progressReportingEnabled = true

    /** Report the current position immediately (used before page/quality changes). */
    fun reportNow() {
        if (released || !progressReportingEnabled) return
        val aid = activeAid
        val cid = activeCid ?: return
        if (aid == 0L) return
        val position = player.currentPosition
        if (position <= 0L) return
        val positionSec = (position / 1000L).coerceAtLeast(1L)
        val durationSec = (player.duration.coerceAtLeast(0L) / 1000L)
        viewModel.reportProgress(aid, cid, positionSec, durationSec)
    }

    private fun handleUiState(uiState: PlayerUiState) {
        if (released) return
        if (uiState.error != null) {
            _state.update {
                it.copy(
                    error = uiState.error,
                    isPlaying = false,
                    isBuffering = false,
                )
            }
            return
        }

        val url = uiState.videoUrl
        if (url == null) {
            // A new online video starts with a cleared state; stop the old one.
            // Page/quality changes keep videoInfo and only clear the URL briefly.
            if (uiState.isLoading && uiState.videoInfo == null && uiState.localAid == 0L) {
                stopInternal()
            }
            return
        }

        val cid = uiState.currentPage?.cid
        if (url == lastUrl && cid == lastCid) return

        val firstPrepare = lastCid == null
        val pageChanged = lastCid != null && cid != null && cid != lastCid
        val targetPosition = when {
            firstPrepare || pageChanged -> uiState.resumePositionMs
            else -> player.currentPosition
        }
        // Opening a video starts playback; a quality change keeps the current
        // paused/playing state, and a part change follows the previous intent.
        val shouldPlay = firstPrepare || player.playWhenReady || player.isPlaying

        activeAid = uiState.videoInfo?.aid ?: uiState.localAid
        activeCid = cid
        lastUrl = url
        lastCid = cid
        pausedByWifi = false
        handlingEnded = false
        pendingSeekMs = targetPosition.takeIf { it > 0L }

        if (BuildConfig.DEBUG) {
            Log.d(
                BilibiliApp.TAG,
                "PlayerController: prepare url=${url.take(80)}... isDash=${uiState.isDash} " +
                    "cid=$cid target=$targetPosition first=$firstPrepare pageChanged=$pageChanged",
            )
        }

        player.setMediaSource(buildMediaSource(uiState))
        player.prepare()
        player.playWhenReady = shouldPlay
        _state.update {
            it.copy(
                isActive = true,
                error = null,
                ended = false,
            )
        }
        syncState()
    }

    private fun buildMediaSource(uiState: PlayerUiState): MediaSource {
        val url = uiState.videoUrl.orEmpty()
        if (uiState.isLive) {
            return if (url.contains(".m3u8", ignoreCase = true)) {
                HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url))
            } else {
                // Some lines only expose FLV.
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url))
            }
        }
        val dashVideo = uiState.dashVideo
        if (uiState.isDash && dashVideo != null) {
            val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(dashVideo.getUrl()))
            val audioSource = uiState.dashAudio?.let {
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(it.getUrl()))
            }
            return if (audioSource != null) {
                MergingMediaSource(videoSource, audioSource)
            } else {
                videoSource
            }
        }
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uiState.videoUrl ?: ""))
    }

    private fun handleEnded() {
        if (released || handlingEnded) return
        if (viewModel.uiState.value.isLive) {
            // A live HLS window can report ENDED when the CDN rotates; do not
            // advance pages or mark it as a finished VOD.
            _state.update { it.copy(ended = false, isPlaying = false) }
            return
        }
        handlingEnded = true
        reportNow()
        val ui = viewModel.uiState.value
        val index = ui.pages.indexOfFirst { it.cid == ui.currentPage?.cid }
        val next = if (index in 0 until ui.pages.size - 1) ui.pages[index + 1] else null
        if (next != null) {
            if (BuildConfig.DEBUG) {
                Log.d(BilibiliApp.TAG, "PlayerController: ended, next page=${next.page}")
            }
            viewModel.selectPage(next)
        } else {
            _state.update { it.copy(ended = true, isPlaying = false) }
        }
        handlingEnded = false
    }

    private fun stopInternal() {
        reportNow()
        player.stop()
        player.clearMediaItems()
        lastUrl = null
        lastCid = null
        pendingSeekMs = null
        activeAid = 0L
        activeCid = null
        _state.value = State()
    }

    private fun syncState() {
        if (released) return
        val duration = player.duration.let { if (it == C.TIME_UNSET) 0L else it.coerceAtLeast(0L) }
        val buffered = player.bufferedPosition.coerceAtLeast(0L)
        val pending = pendingSeekMs
        if (pending != null && player.playbackState == Player.STATE_READY) {
            player.seekTo(pending)
            pendingSeekMs = null
        }
        _state.update {
            it.copy(
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                playbackState = player.playbackState,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                bufferedMs = buffered,
                speed = player.playbackParameters.speed,
                volume = player.volume,
            )
        }
    }

    private fun stateName(state: Int): String = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN"
    }
}
