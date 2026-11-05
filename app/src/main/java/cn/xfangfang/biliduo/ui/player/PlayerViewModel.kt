package cn.xfangfang.biliduo.ui.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.xfangfang.biliduo.BilibiliApp
import cn.xfangfang.biliduo.data.api.BilibiliApi
import cn.xfangfang.biliduo.data.local.DanmakuPreference
import cn.xfangfang.biliduo.data.local.DanmakuPreferenceManager
import cn.xfangfang.biliduo.data.local.PendingReportLogic
import cn.xfangfang.biliduo.data.local.PendingReportManager
import cn.xfangfang.biliduo.data.local.PlaybackProgressManager
import cn.xfangfang.biliduo.data.local.QueueAction
import cn.xfangfang.biliduo.data.local.SubtitlePreference
import cn.xfangfang.biliduo.data.local.SubtitlePreferenceManager
import cn.xfangfang.biliduo.data.local.SubtitleStylePreference
import cn.xfangfang.biliduo.data.model.*
import cn.xfangfang.biliduo.data.download.DownloadManager
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import cn.xfangfang.biliduo.data.repository.PlaybackMeta
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class PlaybackSource { ONLINE, LOCAL }

/** Cap the wait for player/v2 so a slow metadata call never blocks playback. */
private const val META_WAIT_MS = 1_600L

data class PlayerUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val videoUrl: String? = null,
    val videoInfo: VideoInfo? = null,
    val currentPage: VideoPage? = null,
    val pages: List<VideoPage> = emptyList(),
    val title: String = "",
    val resumePositionMs: Long = 0L,
    val availableQualities: List<QualityOption> = emptyList(),
    val currentQuality: QualityOption? = null,
    val dashVideo: DashStream? = null,
    val dashAudio: DashStream? = null,
    val isDash: Boolean = false,
    val localFiles: Map<Long, String> = emptyMap(),
    val localAid: Long = 0L,
    val availableSubtitles: List<SubtitleTrack> = emptyList(),
    val currentSubtitle: SubtitleTrack? = null,
    val subtitleCues: List<SubtitleCue> = emptyList(),
    val subtitleEnabled: Boolean = false,
    val subtitleOffsetX: Float = 0f,
    val subtitleOffsetY: Float = 0f,
    val subtitleTextSizeSp: Float = SubtitleStylePreference.DEFAULT_TEXT_SIZE_SP,
    val subtitleBackgroundAlpha: Float = SubtitleStylePreference.DEFAULT_BACKGROUND_ALPHA,
    val subtitleBottomPaddingDp: Int = SubtitleStylePreference.DEFAULT_BOTTOM_PADDING_DP,
    val danmaku: List<DanmakuItem> = emptyList(),
    val danmakuEnabled: Boolean = true,
    val danmakuOpacity: Float = 0.9f,
    val danmakuSpeed: Float = 1f,
    val danmakuTextSize: Float = 16f,
    val danmakuArea: Float = 1f,
    val danmakuBlockedWords: Set<String> = emptySet(),
    /** 0 = unlimited. */
    val danmakuMaxOnScreen: Int = 0,
    val isPgc: Boolean = false,
    val isLive: Boolean = false,
    val liveRoomId: Long = 0L,
    val liveDetail: LiveRoomDetail? = null,
    val liveQualities: List<QualityOption> = emptyList(),
    val liveQn: Int = 10000,
)

class PlayerViewModel(
    private val repository: BilibiliRepository = BilibiliRepository(),
    private val playbackProgressManager: PlaybackProgressManager = PlaybackProgressManager(
        BilibiliApp.instance.getSharedPreferences("bili_prefs", Context.MODE_PRIVATE)
    ),
    private val subtitlePreferenceManager: SubtitlePreferenceManager = SubtitlePreferenceManager(
        BilibiliApp.instance.getSharedPreferences("bili_prefs", Context.MODE_PRIVATE)
    ),
    private val danmakuPreferenceManager: DanmakuPreferenceManager = DanmakuPreferenceManager(
        BilibiliApp.instance.getSharedPreferences("bili_prefs", Context.MODE_PRIVATE)
    ),
    private val pendingReportManager: PendingReportManager = PendingReportManager(
        BilibiliApp.instance.getSharedPreferences("bili_prefs", Context.MODE_PRIVATE)
    )
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var cachedDashData: DashData? = null
    private var loadJob: Job? = null
    private var playUrlJob: Job? = null
    private var danmakuJob: Job? = null
    private var liveJob: Job? = null
    /** player/wbi/v2 result shared by subtitles and cloud resume. */
    private var metaJob: Deferred<Result<PlaybackMeta>>? = null
    private val pgcEpisodeByCid = mutableMapOf<Long, PgcEpisode>()

    init {
        val saved = danmakuPreferenceManager.load()
        val subtitleStyle = subtitlePreferenceManager.loadStyle()
        _uiState.value = _uiState.value.copy(
            danmakuEnabled = saved.enabled,
            danmakuOpacity = saved.opacity,
            danmakuSpeed = saved.speed,
            danmakuTextSize = saved.textSizeSp,
            danmakuArea = saved.area,
            danmakuBlockedWords = saved.blockedWords,
            danmakuMaxOnScreen = saved.maxOnScreen,
            subtitleTextSizeSp = subtitleStyle.textSizeSp,
            subtitleBackgroundAlpha = subtitleStyle.backgroundAlpha,
            subtitleBottomPaddingDp = subtitleStyle.bottomPaddingDp,
        )
        viewModelScope.launch { repository.flushPendingReports(pendingReportManager) }
    }

    fun load(bvid: String, cid: Long? = null, source: PlaybackSource = PlaybackSource.ONLINE) {
        Log.d(BilibiliApp.TAG, "PlayerVM: load bvid=$bvid cid=$cid source=$source")
        loadJob?.cancel()
        playUrlJob?.cancel()
        metaJob?.cancel()
        metaJob = null
        liveJob?.cancel()
        cachedDashData = null
        loadJob = viewModelScope.launch {
            if (source == PlaybackSource.LOCAL) {
                // 下载会话：只查「已完成」的下载记录，离线可活；不碰网络
                val completed = DownloadManager.getInstance(BilibiliApp.instance)
                    .getDownloads()
                    .filter { it.bvid == bvid && it.status == DownloadInfo.STATUS_COMPLETED }
                    .sortedBy { it.page }
                if (completed.isEmpty()) {
                    _uiState.value = PlayerUiState(isLoading = false, error = "未找到本地下载")
                    return@launch
                }
                val pages = completed.map { VideoPage(cid = it.cid, page = it.page, part = it.part, duration = 0) }
                val current = completed.find { it.cid == cid } ?: completed.first()
                val currentPage = pages.find { it.cid == current.cid } ?: pages.first()
                val localFiles = completed.associate { it.cid to it.filePath }

                // Load local subtitles
                val videoFile = java.io.File(current.filePath)
                val dir = videoFile.parentFile
                val baseName = videoFile.nameWithoutExtension
                val subTracks = mutableListOf<SubtitleTrack>()
                val subCuesMap = mutableMapOf<String, List<SubtitleCue>>()
                if (dir != null && dir.exists()) {
                    dir.listFiles()?.filter {
                        it.name.startsWith(baseName) && it.name.endsWith(".json") && it.name != "${baseName}.json"
                    }?.forEach { f ->
                        try {
                            val json = f.readText()
                            val body = com.google.gson.Gson().fromJson(json, SubtitleBody::class.java)
                            val lang = f.name.removePrefix("${baseName}_").removeSuffix(".json")
                            val track = SubtitleTrack(
                                id = lang.hashCode().toLong(),
                                lan = lang,
                                lanDoc = lang,
                                subtitleUrl = f.absolutePath,
                                type = 0,
                                aiStatus = 0
                            )
                            subTracks.add(track)
                            subCuesMap[lang] = body.body
                        } catch (e: Exception) {
                            Log.w(BilibiliApp.TAG, "Local subtitle parse failed: ${f.name}: ${e.message}")
                        }
                    }
                }

                val aid = current.aid
                val resumeMs = (playbackProgressManager.load(aid, current.cid) ?: 0L) * 1000L

                val savedSub = subtitlePreferenceManager.load(aid, current.cid)
                Log.d(BilibiliApp.TAG, "LOCAL load: aid=$aid cid=${current.cid} resumeMs=$resumeMs savedSub=${savedSub?.let { "lan=${it.lan} en=${it.enabled} offX=${it.offsetX} offY=${it.offsetY}" } ?: "null"}")
                var enabled = false
                var currentSub: SubtitleTrack? = null
                var cues = emptyList<SubtitleCue>()
                if (savedSub != null && savedSub.enabled && savedSub.lan.isNotBlank()) {
                    val matched = subTracks.find { it.lan == savedSub.lan }
                    if (matched != null) {
                        currentSub = matched
                        enabled = true
                        cues = subCuesMap[matched.lan] ?: emptyList()
                    }
                }

                _uiState.value = withCurrentDanmakuSettings(
                    PlayerUiState(
                        isLoading = false,
                        videoUrl = "file://${current.filePath}",
                        pages = pages,
                        currentPage = currentPage,
                        title = completed.first().title,
                        isDash = false,
                        localFiles = localFiles,
                        localAid = aid,
                        availableSubtitles = subTracks,
                        currentSubtitle = currentSub,
                        subtitleCues = cues,
                        subtitleEnabled = enabled,
                        subtitleOffsetX = savedSub?.offsetX ?: 0f,
                        subtitleOffsetY = savedSub?.offsetY ?: 0f,
                        resumePositionMs = resumeMs
                    )
                )
                Log.d(BilibiliApp.TAG, "LOCAL load result: enabled=$enabled currentSub=${currentSub?.lan} subtitleEnabled=$enabled subTracks=${subTracks.size}")
                if (subTracks.isNotEmpty()) {
                    Log.d(BilibiliApp.TAG, "Local subtitles loaded: ${subTracks.size} tracks")
                }
                return@launch
            }

            // 在线会话：纯联网，localFiles 留空（不混本地文件）
            _uiState.value = withCurrentDanmakuSettings(
                PlayerUiState(isLoading = true)
            )

            repository.getVideoInfo(bvid)
                .onSuccess { info ->
                val pages = info.pages?.ifEmpty {
                    listOf(VideoPage(info.cid, 1, "", info.pages?.firstOrNull()?.duration ?: 0))
                } ?: listOf(VideoPage(info.cid, 1, "", 0))

                val firstPage = pages.first()

                val lastPageCid = playbackProgressManager.lastCid(info.aid)
                val startPage = cid?.let { pages.find { p -> p.cid == cid } }
                    ?: lastPageCid?.let { pages.find { p -> p.cid == it } }
                    ?: firstPage

                val resumeMs = (playbackProgressManager.load(info.aid, startPage.cid) ?: 0L) * 1000L

                _uiState.value = _uiState.value.copy(
                    videoInfo = info,
                    pages = pages,
                    currentPage = startPage,
                    title = info.title,
                    resumePositionMs = resumeMs,
                    localFiles = emptyMap()
                )
                Log.d(BilibiliApp.TAG, "PlayerVM: resume aid=${info.aid} -> pageCid=${startPage.cid} resumeMs=${resumeMs}")
                loadPlaybackMeta(info.aid, startPage.cid)
                loadPlayUrlDash(bvid, startPage.cid)
                loadDanmaku(startPage.cid)
            }
            .onFailure { e ->
                Log.e(BilibiliApp.TAG, "PlayerVM: load failed", e)
                _uiState.value = withCurrentDanmakuSettings(
                    PlayerUiState(isLoading = false, error = e.message ?: "加载失败")
                )
            }
        }
    }

    /**
     * Enter a live room. Metadata and play URL are fetched separately so a
     * quality change can reuse the cached room info and keep the old stream
     * visible until the new URL is ready.
     */
    fun loadLive(roomId: Long, qn: Int = 10000, refreshDetail: Boolean = true) {
        if (roomId <= 0L) return
        Log.d(BilibiliApp.TAG, "PlayerVM: loadLive roomId=$roomId qn=$qn refreshDetail=$refreshDetail")
        loadJob?.cancel()
        playUrlJob?.cancel()
        metaJob?.cancel()
        metaJob = null
        danmakuJob?.cancel()
        liveJob?.cancel()
        cachedDashData = null
        pgcEpisodeByCid.clear()

        val current = _uiState.value
        val cachedDetail = current.liveDetail?.takeIf { it.roomId == roomId }
        val sameRoom = cachedDetail != null && current.isLive
        _uiState.value = withCurrentDanmakuSettings(
            PlayerUiState(
                isLoading = true,
                isLive = true,
                liveRoomId = roomId,
                title = cachedDetail?.title.orEmpty(),
                // Keep the current frame while switching quality in the same room.
                videoUrl = current.videoUrl.takeIf { sameRoom },
                liveDetail = cachedDetail,
                liveQualities = current.liveQualities.takeIf { sameRoom }.orEmpty(),
                currentQuality = current.currentQuality.takeIf { sameRoom },
                liveQn = qn,
            )
        )

        liveJob = viewModelScope.launch {
            val detail = if (!refreshDetail && cachedDetail != null) {
                cachedDetail
            } else {
                repository.getLiveRoomDetail(roomId).getOrElse { error ->
                    failLive(error.message ?: "直播间信息加载失败", cachedDetail)
                    return@launch
                }
            }
            // No room-id guard here: on the first load liveDetail is still null,
            // and a superseded load is already cancelled by liveJob.
            _uiState.value = _uiState.value.copy(liveDetail = detail, title = detail.title)

            repository.getLivePlayUrl(roomId, qn)
                .onSuccess { play ->
                    if (_uiState.value.liveDetail?.roomId != roomId) return@onSuccess
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = null,
                        isLive = true,
                        videoUrl = play.url,
                        videoInfo = null,
                        currentPage = null,
                        pages = emptyList(),
                        title = detail.title,
                        liveDetail = detail,
                        liveQualities = play.qualities,
                        currentQuality = play.currentQuality,
                        liveQn = play.currentQuality?.quality ?: qn,
                        isDash = false,
                        availableSubtitles = emptyList(),
                        currentSubtitle = null,
                        subtitleCues = emptyList(),
                        subtitleEnabled = false,
                        danmaku = emptyList(),
                        resumePositionMs = 0L,
                    )
                }
                .onFailure { error ->
                    if (_uiState.value.liveDetail?.roomId != roomId) return@onFailure
                    failLive(error.message ?: "直播地址加载失败", detail)
                }
        }
    }

    fun selectLiveQuality(qn: Int) {
        val state = _uiState.value
        val detail = state.liveDetail ?: return
        if (state.liveQn == qn && state.videoUrl != null) return
        loadLive(detail.roomId, qn, refreshDetail = false)
    }

    /** Reload metadata and play URL (retry button / stream error). */
    fun refreshLive() {
        val state = _uiState.value
        val roomId = state.liveRoomId.takeIf { it > 0L }
            ?: state.liveDetail?.roomId
            ?: return
        loadLive(roomId, state.liveQn, refreshDetail = true)
    }

    /** Poll viewer count / live status without touching playback. */
    fun refreshLiveDetail() {
        val roomId = _uiState.value.liveRoomId.takeIf { it > 0L }
            ?: _uiState.value.liveDetail?.roomId
            ?: return
        viewModelScope.launch {
            repository.getLiveRoomDetail(roomId).onSuccess { detail ->
                if (_uiState.value.isLive && _uiState.value.liveRoomId == roomId) {
                    _uiState.value = _uiState.value.copy(
                        liveDetail = detail,
                        title = detail.title.ifBlank { _uiState.value.title },
                    )
                }
            }
        }
    }

    private fun failLive(message: String, detail: LiveRoomDetail?) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = message,
            liveDetail = detail ?: _uiState.value.liveDetail,
        )
    }

    fun loadPgc(seasonId: Long, startEpId: Long? = null) {
        Log.d(BilibiliApp.TAG, "PlayerVM: loadPgc seasonId=$seasonId startEp=$startEpId")
        loadJob?.cancel()
        playUrlJob?.cancel()
        metaJob?.cancel()
        metaJob = null
        danmakuJob?.cancel()
        liveJob?.cancel()
        cachedDashData = null
        pgcEpisodeByCid.clear()
        _uiState.value = withCurrentDanmakuSettings(
            PlayerUiState(isLoading = true, isPgc = true)
        )
        loadJob = viewModelScope.launch {
            repository.getPgcSeason(seasonId)
                .onSuccess { detail ->
                    val episodes = detail.episodes.orEmpty().filter { it.cid > 0 }
                    if (episodes.isEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "该番剧暂无可播放剧集，可能需要大会员",
                        )
                        return@onSuccess
                    }
                    episodes.forEach { pgcEpisodeByCid[it.cid] = it }
                    val pages = episodes.mapIndexed { index, ep ->
                        VideoPage(
                            cid = ep.cid,
                            page = index + 1,
                            part = ep.longTitle.ifBlank { ep.title },
                            duration = ep.duration / 1000L,
                            badge = ep.badge,
                        )
                    }
                    val episode = startEpId?.let { id -> episodes.find { it.epId == id } }
                        ?: episodes.first()
                    val page = pages.find { it.cid == episode.cid } ?: pages.first()
                    val resumeMs = (playbackProgressManager.load(episode.aid, episode.cid) ?: 0L) * 1000L
                    _uiState.value = withCurrentDanmakuSettings(
                        PlayerUiState(
                            isLoading = true,
                            isPgc = true,
                            videoInfo = buildPgcVideoInfo(detail, episode),
                            pages = pages,
                            currentPage = page,
                            title = detail.title.ifBlank { episode.longTitle },
                            resumePositionMs = resumeMs,
                        )
                    )
                    loadPlaybackMeta(episode.aid, episode.cid, applySubtitles = false)
                    loadPgcPlayUrl(episode)
                    loadDanmaku(episode.cid)
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "PlayerVM: loadPgc failed", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "番剧加载失败",
                    )
                }
        }
    }

    private fun buildPgcVideoInfo(detail: PgcSeasonDetail, episode: PgcEpisode): VideoInfo {
        val owner = detail.upInfo
        val stat = detail.stat
        return VideoInfo(
            bvid = episode.bvid,
            aid = episode.aid,
            title = detail.title,
            pic = detail.cover,
            desc = detail.evaluate,
            owner = VideoOwner(
                mid = owner?.mid ?: 0,
                name = owner?.uname.orEmpty(),
                face = owner?.avatar.orEmpty(),
            ),
            stat = VideoStat(
                view = stat?.views ?: 0,
                like = stat?.likes ?: 0,
                favorite = stat?.favorites ?: 0,
                danmaku = stat?.danmakus ?: 0,
                reply = stat?.reply ?: 0,
            ),
            cid = episode.cid,
            tname = if (detail.type == 2) "影视" else "番剧",
        )
    }

    private fun loadPgcPlayUrl(episode: PgcEpisode) {
        playUrlJob?.cancel()
        playUrlJob = viewModelScope.launch {
            val pendingMeta = metaJob
            val playUrlDeferred = async { repository.getPgcPlayUrl(episode.epId, episode.cid) }
            val meta = pendingMeta?.let { withTimeoutOrNull(META_WAIT_MS) { it.await() } }?.getOrNull()
            playUrlDeferred.await()
                .onSuccess { playUrlInfo ->
                    if (_uiState.value.currentPage?.cid != episode.cid) return@onSuccess
                    applyCloudResume(meta?.lastPlayTimeMs ?: 0L, episode.cid, episode.duration)
                    applyPlayUrlData(playUrlInfo)
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "PlayerVM: loadPgcPlayUrl failed", e)
                    val message = e.message ?: "番剧播放链接加载失败"
                    val episodeBadge = episode.badge.orEmpty()
                    val badgeHint = if (
                        episodeBadge.contains("会员") && !message.contains("大会员")
                    ) {
                        "；该集为${episodeBadge}专享，请确认大会员状态"
                    } else {
                        ""
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = message + badgeHint,
                    )
                }
        }
    }

    fun selectPage(page: VideoPage) {
        if (_uiState.value.isPgc) {
            val episode = pgcEpisodeByCid[page.cid] ?: return
            val info = _uiState.value.videoInfo
            val resumeMs = (playbackProgressManager.load(episode.aid, page.cid) ?: 0L) * 1000L
            _uiState.value = _uiState.value.copy(
                currentPage = page,
                resumePositionMs = resumeMs,
                isLoading = true,
                videoUrl = null,
                videoInfo = info?.copy(
                    bvid = episode.bvid,
                    aid = episode.aid,
                    cid = episode.cid,
                ),
            )
            playUrlJob?.cancel()
            loadPlaybackMeta(episode.aid, page.cid, applySubtitles = false)
            loadPgcPlayUrl(episode)
            loadDanmaku(page.cid)
            return
        }
        // 本地已下载的分 P：直接切本地文件，不联网
        val localPath = _uiState.value.localFiles[page.cid]
        if (localPath != null) {
            Log.d(BilibiliApp.TAG, "PlayerVM: selectPage local page=${page.page} cid=${page.cid}")
            // Load subtitles for the new page
            val videoFile = java.io.File(localPath)
            val dir = videoFile.parentFile
            val baseName = videoFile.nameWithoutExtension
            val subTracks = mutableListOf<SubtitleTrack>()
            val subCuesMap = mutableMapOf<String, List<SubtitleCue>>()
            if (dir != null && dir.exists()) {
                dir.listFiles()?.filter {
                    it.name.startsWith(baseName) && it.name.endsWith(".json") && it.name != "${baseName}.json"
                }?.forEach { f ->
                    try {
                        val json = f.readText()
                        val body = com.google.gson.Gson().fromJson(json, SubtitleBody::class.java)
                        val lang = f.name.removePrefix("${baseName}_").removeSuffix(".json")
                        val track = SubtitleTrack(
                            id = lang.hashCode().toLong(),
                            lan = lang,
                            lanDoc = lang,
                            subtitleUrl = f.absolutePath,
                            type = 0,
                            aiStatus = 0
                        )
                        subTracks.add(track)
                        subCuesMap[lang] = body.body
                    } catch (e: Exception) {
                        Log.w(BilibiliApp.TAG, "Local subtitle parse failed: ${f.name}: ${e.message}")
                    }
                }
            }

            val completed = DownloadManager.getInstance(BilibiliApp.instance)
                .getDownloads()
                .filter { it.bvid == _uiState.value.currentPage?.let { p -> 
                    DownloadManager.getInstance(BilibiliApp.instance).getDownloads().find { it.cid == p.cid }?.bvid 
                } && it.status == DownloadInfo.STATUS_COMPLETED }
            val downloadInfo = completed.find { it.cid == page.cid }
            val aid = downloadInfo?.aid ?: _uiState.value.localAid
            val resumeMs = (playbackProgressManager.load(aid, page.cid) ?: 0L) * 1000L

            val savedSub = subtitlePreferenceManager.load(aid, page.cid)
            Log.d(BilibiliApp.TAG, "selectPage LOCAL: aid=$aid cid=${page.cid} resumeMs=$resumeMs savedSub=${savedSub?.let { "lan=${it.lan} en=${it.enabled} offX=${it.offsetX} offY=${it.offsetY}" } ?: "null"}")
            var enabled = false
            var currentSub: SubtitleTrack? = null
            var cues = emptyList<SubtitleCue>()
            if (savedSub != null && savedSub.enabled && savedSub.lan.isNotBlank()) {
                val matched = subTracks.find { it.lan == savedSub.lan }
                if (matched != null) {
                    currentSub = matched
                    enabled = true
                    cues = subCuesMap[matched.lan] ?: emptyList()
                }
            }

            _uiState.value = _uiState.value.copy(
                currentPage = page,
                videoUrl = "file://$localPath",
                isDash = false,
                isLoading = false,
                resumePositionMs = resumeMs,
                localAid = aid,
                availableSubtitles = subTracks,
                currentSubtitle = currentSub,
                subtitleCues = cues,
                subtitleEnabled = enabled,
                subtitleOffsetX = savedSub?.offsetX ?: 0f,
                subtitleOffsetY = savedSub?.offsetY ?: 0f,
                danmaku = emptyList()
            )
            Log.d(BilibiliApp.TAG, "selectPage LOCAL result: enabled=$enabled currentSub=${currentSub?.lan} subtitleEnabled=$enabled subTracks=${subTracks.size}")
            return
        }

        val info = _uiState.value.videoInfo ?: return
        val bvid = info.bvid
        Log.d(BilibiliApp.TAG, "PlayerVM: selectPage page=${page.page} cid=${page.cid} part=${page.part}")

        // Resume this page's own progress (never inherit the previous page's position).
        val resumeMs = (playbackProgressManager.load(info.aid, page.cid) ?: 0L) * 1000L

        _uiState.value = _uiState.value.copy(
            currentPage = page,
            resumePositionMs = resumeMs,
            isLoading = true,
            videoUrl = null
        )
        playUrlJob?.cancel()
        loadPlaybackMeta(info.aid, page.cid)
        loadPlayUrlDash(bvid, page.cid)
        loadDanmaku(page.cid)
    }

    /** Clear the current session when leaving the player. */
    fun reset() {
        loadJob?.cancel()
        playUrlJob?.cancel()
        metaJob?.cancel()
        metaJob = null
        danmakuJob?.cancel()
        liveJob?.cancel()
        cachedDashData = null
        pgcEpisodeByCid.clear()
        _uiState.value = withCurrentDanmakuSettings(PlayerUiState(isLoading = false))
    }

    /**
     * Rebuilding [PlayerUiState] on load/reset must not wipe the user's global
     * danmaku appearance (and enabled) preference.
     */
    private fun withCurrentDanmakuSettings(state: PlayerUiState): PlayerUiState {
        val current = _uiState.value
        return state.copy(
            danmakuEnabled = current.danmakuEnabled,
            danmakuOpacity = current.danmakuOpacity,
            danmakuSpeed = current.danmakuSpeed,
            danmakuTextSize = current.danmakuTextSize,
            danmakuArea = current.danmakuArea,
            danmakuBlockedWords = current.danmakuBlockedWords,
            danmakuMaxOnScreen = current.danmakuMaxOnScreen,
        )
    }

    fun toggleDanmaku() {
        setDanmakuEnabled(!_uiState.value.danmakuEnabled)
    }

    fun setDanmakuEnabled(enabled: Boolean) {
        updateDanmakuState { it.copy(danmakuEnabled = enabled) }
    }

    fun setDanmakuOpacity(value: Float) {
        updateDanmakuState { it.copy(danmakuOpacity = value.coerceIn(0.2f, 1f)) }
    }

    fun setDanmakuSpeed(value: Float) {
        updateDanmakuState { it.copy(danmakuSpeed = value.coerceIn(0.5f, 2f)) }
    }

    fun setDanmakuTextSize(value: Float) {
        updateDanmakuState { it.copy(danmakuTextSize = value.coerceIn(12f, 24f)) }
    }

    fun setDanmakuArea(value: Float) {
        updateDanmakuState { it.copy(danmakuArea = value.coerceIn(0.25f, 1f)) }
    }

    fun setDanmakuBlockedWords(words: Set<String>) {
        val sanitized = words.map { it.trim() }.filter { it.isNotEmpty() }.take(50).toSet()
        updateDanmakuState { it.copy(danmakuBlockedWords = sanitized) }
    }

    fun setDanmakuMaxOnScreen(value: Int) {
        updateDanmakuState { it.copy(danmakuMaxOnScreen = value.coerceIn(0, 200)) }
    }

    private fun updateDanmakuState(transform: (PlayerUiState) -> PlayerUiState) {
        val next = transform(_uiState.value)
        _uiState.value = next
        danmakuPreferenceManager.save(
            DanmakuPreference(
                enabled = next.danmakuEnabled,
                opacity = next.danmakuOpacity,
                speed = next.danmakuSpeed,
                textSizeSp = next.danmakuTextSize,
                area = next.danmakuArea,
                blockedWords = next.danmakuBlockedWords,
                maxOnScreen = next.danmakuMaxOnScreen,
            )
        )
    }

    private fun loadDanmaku(cid: Long, clearFirst: Boolean = true) {
        danmakuJob?.cancel()
        if (clearFirst) {
            _uiState.value = _uiState.value.copy(danmaku = emptyList())
        }
        danmakuJob = viewModelScope.launch {
            repository.getDanmaku(cid)
                .onSuccess { list ->
                    if (_uiState.value.currentPage?.cid == cid) {
                        _uiState.value = _uiState.value.copy(danmaku = list)
                    }
                }
                .onFailure { error ->
                    Log.w(BilibiliApp.TAG, "PlayerVM: danmaku load failed: ${error.message}")
                }
        }
    }

    /** Re-fetch danmaku after sending, keeping the current list until the new one arrives. */
    fun refreshDanmaku() {
        val cid = _uiState.value.currentPage?.cid ?: return
        if (cid > 0L) loadDanmaku(cid, clearFirst = false)
    }

    /** Reload the current page's play URL without losing the video/page info. */
    fun reloadCurrent() {
        val bvid = _uiState.value.videoInfo?.bvid ?: return
        val cid = _uiState.value.currentPage?.cid ?: return
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null,
            videoUrl = null
        )
        playUrlJob?.cancel()
        loadPlayUrlDash(bvid, cid)
    }

    fun selectQuality(quality: QualityOption) {
        if (quality.quality == _uiState.value.currentQuality?.quality) return
        Log.d(BilibiliApp.TAG, "PlayerVM: selectQuality qn=${quality.quality} desc=${quality.description}")

        val dash = cachedDashData ?: return
        val videoStream = pickVideoStream(dash.video, quality.quality) ?: return
        val audioStream = dash.audio?.firstOrNull() ?: dash.flac?.audio

        val url = videoStream.getUrl()
        _uiState.value = _uiState.value.copy(
            currentQuality = quality,
            videoUrl = url,
            dashVideo = videoStream,
            dashAudio = audioStream,
            isDash = true
        )
    }

    fun reportProgress(aid: Long, cid: Long, progress: Long, duration: Long = 0L) {
        val loggedIn = BilibiliApi.loginCookies.isNotBlank()
        if (loggedIn) {
            val mid = BilibiliApi.parseSelfMid() ?: 0L
            viewModelScope.launch {
                // NonCancellable: exit-time reports must survive viewModelScope cancellation.
                val result = withContext(NonCancellable) {
                    repository.reportProgress(aid, cid, progress)
                }
                when (PendingReportLogic.queueAction(loggedIn = true, reportSuccess = result.isSuccess)) {
                    QueueAction.REMOVE -> pendingReportManager.remove(aid, cid, mid)
                    QueueAction.ADD -> pendingReportManager.add(aid, cid, progress, mid)
                    QueueAction.NONE -> {}
                }
            }
        }
        if (duration > 0) {
            playbackProgressManager.save(aid, cid, progress, duration)
        }
    }

    private fun loadPlayUrlDash(bvid: String, cid: Long) {
        playUrlJob = viewModelScope.launch {
            // Metadata and play URL run in parallel; metadata only delays the
            // start when it answers within META_WAIT_MS.
            val pendingMeta = metaJob
            val playUrlDeferred = async { repository.getPlayUrlDash(bvid, cid) }
            val meta = pendingMeta?.let { withTimeoutOrNull(META_WAIT_MS) { it.await() } }?.getOrNull()
            playUrlDeferred.await()
                .onSuccess { playUrlInfo ->
                    Log.d(BilibiliApp.TAG, "PlayerVM: dash loaded, accept=${playUrlInfo.accept_quality}")
                    val durationMs = (_uiState.value.currentPage?.duration ?: 0L) * 1000L
                    applyCloudResume(meta?.lastPlayTimeMs ?: 0L, cid, durationMs)
                    applyPlayUrlData(playUrlInfo)
                }
                .onFailure { e ->
                    Log.e(BilibiliApp.TAG, "PlayerVM: loadPlayUrlDash failed", e)
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "加载播放链接失败")
                }
        }
    }

    private fun applyPlayUrlData(playUrlInfo: PlayUrlInfo) {
        val dash = playUrlInfo.dash
        if (dash != null) {
            cachedDashData = dash

            val qualities = buildQualityList(playUrlInfo, dash)
            val defaultQuality = qualities.firstOrNull()

            val videoStream = defaultQuality?.let { pickVideoStream(dash.video, it.quality) }
            val audioStream = dash.audio?.firstOrNull() ?: dash.flac?.audio

            val videoUrl = videoStream?.getUrl()

            _uiState.value = _uiState.value.copy(
                videoUrl = videoUrl,
                availableQualities = qualities,
                currentQuality = defaultQuality,
                dashVideo = videoStream,
                dashAudio = audioStream,
                isDash = true,
                isLoading = false
            )
            Log.d(BilibiliApp.TAG, "PlayerVM: default quality=${defaultQuality?.description} videoUrl=${videoUrl?.take(80)}")
        } else {
            val url = playUrlInfo.durl?.firstOrNull()?.url
            if (url != null) {
                _uiState.value = _uiState.value.copy(
                    videoUrl = url,
                    isDash = false,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "无法获取播放链接"
                )
            }
        }
    }

    private fun buildQualityList(
        playUrlInfo: PlayUrlInfo,
        dash: DashData?,
    ): List<QualityOption> {
        val acceptQuality = playUrlInfo.accept_quality ?: return emptyList()
        val acceptDesc = playUrlInfo.accept_description ?: return emptyList()

        val all = acceptQuality.zip(acceptDesc).map { (q, desc) ->
            QualityOption(quality = q, description = desc)
        }
        // accept_quality 是账号/接口允许的画质，但 DASH 实际返回的 video 轨道
        // 可能更少（未登录、版权或编码不支持）。只保留有轨道的档位，避免
        // 默认选中 1080P+ 却实际播放 480P、chip 仍显示高画质。
        val availableIds = dash?.video?.map { it.id }?.toSet().orEmpty()
        if (availableIds.isEmpty()) return all
        return all.filter { it.quality in availableIds }.ifEmpty { all }
    }

    private fun pickVideoStream(videos: List<DashStream>?, targetQuality: Int): DashStream? {
        if (videos.isNullOrEmpty()) return null

        val matching = videos.filter { it.id == targetQuality }
        if (matching.isNotEmpty()) {
            return matching.minByOrNull { it.bandwidth } ?: matching.first()
        }

        val lower = videos.filter { it.id < targetQuality }
        if (lower.isNotEmpty()) {
            return lower.maxByOrNull { it.id } ?: lower.first()
        }

        return videos.minByOrNull { it.id }
    }

    /**
     * Fetches player/v2 once for both subtitles and cloud resume. The play URL
     * job awaits the same deferred (bounded by [META_WAIT_MS]), so a cloud
     * position is known before the media source is prepared.
     */
    private fun loadPlaybackMeta(aid: Long, cid: Long, applySubtitles: Boolean = true) {
        metaJob?.cancel()
        metaJob = null
        if (aid <= 0L) return
        // PGC only needs cloud progress; guests have nothing to resume.
        if (!applySubtitles && BilibiliApi.loginCookies.isBlank()) return
        val job = viewModelScope.async { repository.getPlaybackMeta(aid, cid) }
        metaJob = job
        if (!applySubtitles) return
        viewModelScope.launch {
            val meta = job.await()
                .onFailure { e -> Log.e(BilibiliApp.TAG, "loadPlaybackMeta failed", e) }
                .getOrNull() ?: return@launch
            if (_uiState.value.currentPage?.cid != cid) return@launch
            applySubtitleTracks(aid, cid, meta.subtitleTracks)
        }
    }

    private fun applyCloudResume(cloudMs: Long, cid: Long, durationMs: Long) {
        val state = _uiState.value
        if (state.currentPage?.cid != cid) return
        val position = pickResumePosition(state.resumePositionMs, cloudMs, durationMs)
        if (position == state.resumePositionMs) return
        Log.d(BilibiliApp.TAG, "PlayerVM: cloud resume cid=$cid position=$position")
        _uiState.value = state.copy(resumePositionMs = position)
    }

    private fun applySubtitleTracks(aid: Long, cid: Long, tracks: List<SubtitleTrack>) {
        Log.d(BilibiliApp.TAG, "loadSubtitles: aid=$aid cid=$cid ${tracks.size} tracks")
        val sortedTracks = tracks.sortedWith(
            compareBy<SubtitleTrack> { it.type }.thenBy { it.lan }
        )
        _uiState.value = _uiState.value.copy(availableSubtitles = sortedTracks)

        val saved = subtitlePreferenceManager.load(aid, cid)
        Log.d(BilibiliApp.TAG, "loadSubtitles saved pref: aid=$aid cid=$cid saved=${saved?.let { "lan=${it.lan} en=${it.enabled} offX=${it.offsetX} offY=${it.offsetY}" } ?: "null"}")
        if (saved != null) {
            _uiState.value = _uiState.value.copy(
                subtitleOffsetX = saved.offsetX,
                subtitleOffsetY = saved.offsetY
            )
            if (saved.enabled && saved.lan.isNotBlank()) {
                val matched = sortedTracks.find { it.lan == saved.lan }
                if (matched != null) {
                    selectSubtitle(matched)
                }
            }
        }
    }

    fun selectSubtitle(track: SubtitleTrack?) {
        val aid = _uiState.value.videoInfo?.aid ?: _uiState.value.localAid ?: 0L
        val cid = _uiState.value.currentPage?.cid ?: return
        Log.d(BilibiliApp.TAG, "selectSubtitle: track=${track?.lan} aid=$aid cid=$cid isLocal=${_uiState.value.videoInfo==null}")

        if (track == null) {
            _uiState.value = _uiState.value.copy(
                currentSubtitle = null,
                subtitleCues = emptyList(),
                subtitleEnabled = false
            )
            if (aid != 0L) {
                Log.d(BilibiliApp.TAG, "selectSubtitle DISABLE: aid=$aid cid=$cid")
                subtitlePreferenceManager.save(aid, cid, SubtitlePreference(
                    lan = "", enabled = false,
                    offsetX = _uiState.value.subtitleOffsetX,
                    offsetY = _uiState.value.subtitleOffsetY
                ))
            }
            return
        }

        _uiState.value = _uiState.value.copy(
            currentSubtitle = track,
            subtitleEnabled = true
        )
        if (aid != 0L) {
            Log.d(BilibiliApp.TAG, "selectSubtitle SAVE: aid=$aid cid=$cid lan=${track.lan} enabled=true")
            subtitlePreferenceManager.save(aid, cid, SubtitlePreference(
                lan = track.lan, enabled = true,
                offsetX = _uiState.value.subtitleOffsetX,
                offsetY = _uiState.value.subtitleOffsetY
            ))
        }

        viewModelScope.launch {
            try {
                val body = if (track.subtitleUrl.startsWith("/")) {
                    // Local file
                    val file = java.io.File(track.subtitleUrl)
                    if (file.exists()) {
                        com.google.gson.Gson().fromJson(file.readText(), SubtitleBody::class.java)
                    } else null
                } else {
                    repository.getSubtitleContent(track.subtitleUrl).getOrNull()
                }
                if (body != null) {
                    Log.d(BilibiliApp.TAG, "selectSubtitle: ${body.body.size} cues")
                    _uiState.value = _uiState.value.copy(subtitleCues = body.body)
                }
            } catch (e: Exception) {
                Log.e(BilibiliApp.TAG, "selectSubtitle failed", e)
            }
        }
    }

    fun toggleSubtitle() {
        val aid = _uiState.value.videoInfo?.aid ?: _uiState.value.localAid ?: return
        val cid = _uiState.value.currentPage?.cid ?: return
        val current = _uiState.value.currentSubtitle
        val enabled = _uiState.value.subtitleEnabled

        if (current == null) {
            val firstTrack = _uiState.value.availableSubtitles.firstOrNull()
            if (firstTrack != null) {
                selectSubtitle(firstTrack)
            }
        } else {
            val newEnabled = !enabled
            Log.d(BilibiliApp.TAG, "toggleSubtitle: aid=$aid cid=$cid current=${current.lan} enabled=$enabled -> newEnabled=$newEnabled")
            _uiState.value = _uiState.value.copy(subtitleEnabled = newEnabled)
            subtitlePreferenceManager.save(aid, cid, SubtitlePreference(
                lan = current.lan, enabled = newEnabled,
                offsetX = _uiState.value.subtitleOffsetX,
                offsetY = _uiState.value.subtitleOffsetY
            ))
        }
    }

    fun updateSubtitlePosition(offsetX: Float, offsetY: Float) {
        val aid = _uiState.value.videoInfo?.aid ?: _uiState.value.localAid
        val cid = _uiState.value.currentPage?.cid
        Log.d(BilibiliApp.TAG, "updateSubtitlePosition: aid=$aid cid=$cid offX=$offsetX offY=$offsetY")
        _uiState.value = _uiState.value.copy(
            subtitleOffsetX = offsetX,
            subtitleOffsetY = offsetY
        )
        if (aid != null && cid != null && aid != 0L) {
            val current = _uiState.value.currentSubtitle
            subtitlePreferenceManager.save(aid, cid, SubtitlePreference(
                lan = current?.lan ?: "",
                enabled = _uiState.value.subtitleEnabled,
                offsetX = offsetX,
                offsetY = offsetY
            ))
        }
    }

    fun setSubtitleTextSize(textSizeSp: Float) {
        _uiState.value = _uiState.value.copy(subtitleTextSizeSp = textSizeSp)
        saveSubtitleStyle()
    }

    fun setSubtitleBackgroundAlpha(alpha: Float) {
        _uiState.value = _uiState.value.copy(subtitleBackgroundAlpha = alpha)
        saveSubtitleStyle()
    }

    fun setSubtitleBottomPadding(bottomPaddingDp: Int) {
        _uiState.value = _uiState.value.copy(subtitleBottomPaddingDp = bottomPaddingDp)
        saveSubtitleStyle()
    }

    private fun saveSubtitleStyle() {
        val state = _uiState.value
        subtitlePreferenceManager.saveStyle(
            SubtitleStylePreference(
                textSizeSp = state.subtitleTextSizeSp,
                backgroundAlpha = state.subtitleBackgroundAlpha,
                bottomPaddingDp = state.subtitleBottomPaddingDp,
            )
        )
    }
}
