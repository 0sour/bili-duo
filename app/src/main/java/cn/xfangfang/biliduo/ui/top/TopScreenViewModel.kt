package cn.xfangfang.biliduo.ui.top

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.xfangfang.biliduo.BilibiliApp
import cn.xfangfang.biliduo.data.model.VideoInfo
import cn.xfangfang.biliduo.data.model.toCardUi
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import cn.xfangfang.biliduo.util.formatCount
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TopScreenMode {
    IDLE,
    PREVIEW,
    PERSON,
    PANEL,
    AMBIENT,
    DYNAMIC,
}

enum class TopDynamicKind {
    DETAIL,
    GALLERY,
    TEXT,
}

/** Icons available to the read-only top scenes (mapped to Material icons in UI). */
enum class TopGlyph {
    LIVE,
    HOME,
    PLAY,
    SEARCH,
    HISTORY,
    FAVORITE,
    FOLLOWING,
    DOWNLOAD,
    PERSON,
    LOGIN,
    NETWORK,
    THEME,
    DANMAKU,
    STORAGE,
    ABOUT,
    TRENDING,
    RANKING,
    BANGUMI,
    MOVIE,
    DYNAMIC,
}

data class TopPersonStat(
    val value: String,
    val label: String,
)

data class TopPersonVideo(
    val title: String,
    val coverUrl: String = "",
    val meta: String = "",
)

data class TopPersonMoment(
    val text: String,
    val meta: String = "",
    val coverUrl: String = "",
)

data class TopPersonUi(
    val eyebrow: String = "PROFILE",
    val mid: Long = 0,
    val name: String,
    val subtitle: String = "",
    val description: String = "",
    val badge: String = "",
    val avatarUrl: String = "",
    val glyph: TopGlyph = TopGlyph.PERSON,
    val accent: Long = 0xFFFB7299,
    val stats: List<TopPersonStat> = emptyList(),
    val recentVideos: List<TopPersonVideo> = emptyList(),
    val recentMoments: List<TopPersonMoment> = emptyList(),
)

data class TopPanelUi(
    val eyebrow: String = "SETTINGS",
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val detail: String = "",
    val items: List<String> = emptyList(),
    val activeIndex: Int = 0,
    val glyph: TopGlyph = TopGlyph.PLAY,
    val accent: Long = 0xFFFB7299,
)

data class TopAmbientUi(
    val eyebrow: String = "",
    val title: String,
    val subtitle: String = "",
    val hint: String = "",
    val glyph: TopGlyph = TopGlyph.HOME,
    val accent: Long = 0xFFFB7299,
)

/**
 * Read-only data shown on the top screen while no video frame is available.
 * This is deliberately independent of the player state: the top host decides
 * whether to show the preview layer or the PlayerView.
 */
/**
 * Read-only payload for the dynamic reader scenes. The bottom detail/gallery
 * updates [imageIndex]; [kind] selects the top composition.
 */
data class TopDynamicReaderUi(
    val kind: TopDynamicKind,
    val title: String,
    val text: String,
    val authorName: String = "",
    val authorFace: String = "",
    val pubTime: String = "",
    val images: List<String> = emptyList(),
    val imageIndex: Int = 0,
    val kindLabel: String = "动态",
    val commentCount: Int = 0,
    val forwardCount: Int = 0,
    val likeCount: Int = 0,
) {
    val activeImage: String? get() = images.getOrNull(imageIndex) ?: images.firstOrNull()
}

data class TopPreviewUi(
    val bvid: String,
    val title: String,
    val coverUrl: String,
    val ownerName: String = "",
    val ownerFace: String = "",
    val playCount: Long = 0L,
    val danmakuCount: Long = 0L,
    val pubdate: Long = 0L,
    val duration: String = "",
    val description: String = "",
    val category: String = "",
    val coverFit: Boolean = false,
    val eyebrow: String = "● 已选择 · 即将播放",
    val metaLine: String? = null,
    val ownerLabel: String = "UP 主",
    val actionHint: String = "开始播放 · A 返回",
)

data class TopScreenUiState(
    val mode: TopScreenMode = TopScreenMode.IDLE,
    val preview: TopPreviewUi? = null,
    val descriptionLoading: Boolean = false,
    val person: TopPersonUi? = null,
    val panel: TopPanelUi? = null,
    val ambient: TopAmbientUi? = null,
    val dynamic: TopDynamicReaderUi? = null,
)

/**
 * Activity-scoped source of truth for the top-screen idle/preview layer.
 * Bottom-screen lists report the focused card; detail info (description) is
 * fetched after a short debounce and cached so moving the D-pad is cheap.
 */
class TopScreenViewModel(
    private val repository: BilibiliRepository = BilibiliRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(TopScreenUiState())
    val state: StateFlow<TopScreenUiState> = _state.asStateFlow()

    private val detailCache = object : LinkedHashMap<String, VideoInfo>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VideoInfo>?): Boolean =
            size > 8
    }

    private data class PersonRecent(
        val stats: List<TopPersonStat> = emptyList(),
        val videos: List<TopPersonVideo> = emptyList(),
        val moments: List<TopPersonMoment> = emptyList(),
    )

    private val personRecentCache = object : LinkedHashMap<Long, PersonRecent>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, PersonRecent>?): Boolean =
            size > 16
    }

    private var detailJob: Job? = null
    private var clearJob: Job? = null
    private var personJob: Job? = null
    // Focus events for adjacent cards can arrive out of order (new focus
    // before the old card's clear). Ignore a clear that follows a fresh
    // focus immediately, otherwise repeated Down flashes the idle screen.
    private var lastFocusAt = 0L

    fun onVideoFocused(seed: TopPreviewUi) {
        lastFocusAt = SystemClock.uptimeMillis()
        clearJob?.cancel()
        detailJob?.cancel()
        if (seed.bvid.isBlank()) {
            // PGC season cards have no bvid; show the seed as-is without
            // trying to fetch UGC detail.
            if (seed.title.isBlank() && seed.coverUrl.isBlank()) {
                onFocusCleared()
            } else {
                _state.value = TopScreenUiState(
                    mode = TopScreenMode.PREVIEW,
                    preview = seed,
                    descriptionLoading = false,
                )
            }
            return
        }

        val cached = detailCache[seed.bvid]
        val merged = if (cached != null) merge(seed, cached) else seed
        _state.value = TopScreenUiState(
            mode = TopScreenMode.PREVIEW,
            preview = merged,
            descriptionLoading = merged.description.isBlank() && cached == null,
        )

        if (cached == null && seed.description.isBlank()) {
            detailJob = viewModelScope.launch {
                delay(250L)
                repository.getVideoInfo(seed.bvid)
                    .onSuccess { info ->
                        detailCache[seed.bvid] = info
                        if (_state.value.preview?.bvid == seed.bvid) {
                            _state.value = _state.value.copy(
                                preview = merge(_state.value.preview ?: seed, info),
                                descriptionLoading = false,
                            )
                        }
                    }
                    .onFailure {
                        if (_state.value.preview?.bvid == seed.bvid) {
                            _state.value = _state.value.copy(descriptionLoading = false)
                        }
                    }
            }
        }
    }

    fun showPerson(person: TopPersonUi) {
        detailJob?.cancel()
        clearJob?.cancel()
        _state.value = TopScreenUiState(mode = TopScreenMode.PERSON, person = person)
        val mid = person.mid
        if (mid <= 0) return
        if (person.recentVideos.isNotEmpty() || person.recentMoments.isNotEmpty()) return
        personJob?.cancel()
        val cached = personRecentCache[mid]
        if (cached != null) {
            _state.value = TopScreenUiState(
                mode = TopScreenMode.PERSON,
                person = person.withRecent(cached),
            )
            return
        }
        personJob = viewModelScope.launch {
            delay(220)
            val recent = loadPersonRecent(mid)
            personRecentCache[mid] = recent
            val current = _state.value
            if (current.mode == TopScreenMode.PERSON && current.person?.mid == mid) {
                _state.value = current.copy(person = current.person?.withRecent(recent))
            }
        }
    }

    private suspend fun loadPersonRecent(mid: Long): PersonRecent = coroutineScope {
        val cardDeferred = async { repository.getUserCard(mid).getOrNull() }
        val videosDeferred = async { repository.getUserVideos(mid).getOrNull()?.first.orEmpty() }
        val momentsDeferred = async {
            repository.getDynamicSpaceFeed(mid).getOrNull()?.items.orEmpty()
                .mapNotNull { it.toCardUi() }
        }
        val card = cardDeferred.await()
        val videos = videosDeferred.await()
        val moments = momentsDeferred.await()
        Log.d(BilibiliApp.TAG, "person recent: mid=$mid videos=${videos.size} moments=${moments.size}")
        PersonRecent(
            stats = listOfNotNull(
                card?.follower?.takeIf { it > 0 }?.let {
                    TopPersonStat(formatCount(it.toLong()), "粉丝")
                },
                (card?.archive_count?.takeIf { it > 0 } ?: videos.size.takeIf { it > 0 })?.let {
                    TopPersonStat(formatCount(it.toLong()), "视频")
                },
            ),
            videos = videos.take(3).map { video ->
                TopPersonVideo(
                    title = video.title,
                    coverUrl = video.pic,
                    meta = video.duration,
                )
            },
            moments = moments.take(2).map { moment ->
                TopPersonMoment(
                    text = moment.forward?.text?.takeIf { it.isNotBlank() }
                        ?: moment.forward?.video?.title?.takeIf { it.isNotBlank() }
                        ?: moment.text.takeIf { it.isNotBlank() }
                        ?: moment.video?.title.orEmpty().takeIf { it.isNotBlank() }
                        ?: "（无文字动态）",
                    meta = listOf(moment.kindLabel, moment.pubTime)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    coverUrl = moment.video?.cover ?: moment.images.firstOrNull().orEmpty(),
                )
            },
        )
    }

    private fun TopPersonUi.withRecent(recent: PersonRecent): TopPersonUi = copy(
        stats = stats.ifEmpty { recent.stats },
        recentVideos = recent.videos,
        recentMoments = recent.moments,
    )

    fun showPanel(panel: TopPanelUi) {
        detailJob?.cancel()
        clearJob?.cancel()
        _state.value = TopScreenUiState(mode = TopScreenMode.PANEL, panel = panel)
    }

    fun showDynamicReader(reader: TopDynamicReaderUi) {
        detailJob?.cancel()
        clearJob?.cancel()
        _state.value = TopScreenUiState(mode = TopScreenMode.DYNAMIC, dynamic = reader)
    }

    fun showAmbient(ambient: TopAmbientUi) {
        detailJob?.cancel()
        clearJob?.cancel()
        _state.value = TopScreenUiState(mode = TopScreenMode.AMBIENT, ambient = ambient)
    }

    fun showIdle() {
        detailJob?.cancel()
        clearJob?.cancel()
        _state.value = TopScreenUiState(mode = TopScreenMode.IDLE)
    }

    fun onFocusCleared() {
        if (SystemClock.uptimeMillis() - lastFocusAt < 250L) {
            // The clear belongs to the card we just left; the new card's
            // preview is already on screen.
            return
        }
        detailJob?.cancel()
        clearJob?.cancel()
        clearJob = viewModelScope.launch {
            delay(250L)
            if (_state.value.mode == TopScreenMode.PREVIEW ||
                _state.value.mode == TopScreenMode.DYNAMIC
            ) {
                _state.value = TopScreenUiState()
            }
        }
    }

    private fun merge(seed: TopPreviewUi, info: VideoInfo): TopPreviewUi = seed.copy(
        title = info.title.ifBlank { seed.title },
        coverUrl = info.pic.ifBlank { seed.coverUrl },
        ownerName = info.owner.name.ifBlank { seed.ownerName },
        ownerFace = info.owner.face.ifBlank { seed.ownerFace },
        playCount = info.stat.view.takeIf { it > 0 } ?: seed.playCount,
        danmakuCount = info.stat.danmaku.takeIf { it > 0 } ?: seed.danmakuCount,
        pubdate = info.pubdate.takeIf { it > 0 } ?: seed.pubdate,
        description = info.desc
            .takeIf { it.isNotBlank() && it.trim() != "-" }
            ?: seed.description,
        category = info.tname.ifBlank { seed.category },
    )
}
