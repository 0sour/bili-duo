@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package cn.xfangfang.biliduo.ui.player

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import cn.xfangfang.biliduo.ui.common.DuoPillButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cn.xfangfang.biliduo.BilibiliApp
import cn.xfangfang.biliduo.BuildConfig
import cn.xfangfang.biliduo.data.api.BilibiliApi
import cn.xfangfang.biliduo.data.download.DownloadManager
import cn.xfangfang.biliduo.data.local.AppSettings
import cn.xfangfang.biliduo.data.model.CommentItem
import cn.xfangfang.biliduo.data.model.CommentPicture
import cn.xfangfang.biliduo.data.model.QualityOption
import cn.xfangfang.biliduo.data.model.UserVideoItem
import cn.xfangfang.biliduo.data.model.VideoPage
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import cn.xfangfang.biliduo.duo.DuoMode
import cn.xfangfang.biliduo.duo.DuoOverlay
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.detail.favouredFolderIds
import cn.xfangfang.biliduo.ui.detail.parseDedeUserId
import cn.xfangfang.biliduo.ui.detail.pickDefaultFolderId
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.ui.duo.duoTextFieldNavigation
import cn.xfangfang.biliduo.ui.duo.duoTabCycling
import cn.xfangfang.biliduo.util.fixPic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DuoBg = Color(0xFF0B0B0E)
private val DuoSurface = Color(0xFF141418)
private val DuoSurfaceHigh = Color(0xFF26262E)
private val DuoPink = Color(0xFFFB7299)
private val DuoTextSecondary = Color(0xFFA9A9B4)
private val DuoTextTertiary = Color(0xFF73737F)
private val DuoDivider = Color(0xFF2E2E38)

private val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * Bottom-screen player console. Owns no ExoPlayer: all transport commands go to
 * [PlayerController], all metadata comes from the Activity-scoped [PlayerViewModel].
 */
@Composable
fun PlayerConsoleScreen(
    bvid: String,
    controller: PlayerController,
    vm: PlayerViewModel,
    session: DuoSessionViewModel,
    onBack: () -> Unit,
    startCid: Long? = null,
    source: PlaybackSource = PlaybackSource.ONLINE,
    pgcSeasonId: Long? = null,
    onUploaderClick: (Long) -> Unit = {},
) {
    val ui by vm.uiState.collectAsState()
    val playback by controller.state.collectAsState()
    val overlay by session.overlay.collectAsState()
    val focusEpoch by session.focusEpoch.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { BilibiliRepository() }
    val keyboard = LocalSoftwareKeyboardController.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var seekDrag by remember { mutableStateOf<Float?>(null) }
    val playFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val sliderFocus = remember { FocusRequester() }
    val tabFocus = remember { List(4) { FocusRequester() } }
    val nextPageFocus = remember { FocusRequester() }
    val qualityFocus = remember { FocusRequester() }
    val speedFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val danmakuSendFocus = remember { FocusRequester() }
    var playFocused by remember { mutableStateOf(false) }
    var rightPaneFocused by remember { mutableStateOf(false) }
    val rightPaneFocusedState by rememberUpdatedState(rightPaneFocused)
    var initialFocusApplied by remember { mutableStateOf(false) }
    var pendingOverlayFocus by remember { mutableStateOf<FocusRequester?>(null) }
    var observedOverlay by remember { mutableStateOf<DuoOverlay?>(null) }
    var danmakuDraft by remember { mutableStateOf("") }
    var danmakuSending by remember { mutableStateOf(false) }
    var danmakuSendError by remember { mutableStateOf<String?>(null) }
    var lastSentDmid by remember { mutableStateOf(0L) }
    var lastSentCid by remember { mutableStateOf(0L) }
    var danmakuMode by remember { mutableStateOf(1) }
    var danmakuColor by remember { mutableStateOf(0xFFFFFFL) }
    var recallingDanmaku by remember { mutableStateOf(false) }
    var downloadQuality by remember { mutableStateOf<QualityOption?>(null) }
    var downloadAllPages by remember { mutableStateOf(false) }

    val aid = ui.videoInfo?.aid
    val videoBvid = ui.videoInfo?.bvid
    val ownerMid = ui.videoInfo?.owner?.mid
    var isFollowed by remember(ownerMid) { mutableStateOf<Boolean?>(null) }
    var followBusy by remember(ownerMid) { mutableStateOf(false) }
    var isFavorited by remember(aid) { mutableStateOf<Boolean?>(null) }
    var favBusy by remember(aid) { mutableStateOf(false) }

    var comments by remember(aid) { mutableStateOf<List<CommentItem>>(emptyList()) }
    var commentsLoading by remember(aid) { mutableStateOf(false) }
    var commentsEnded by remember(aid) { mutableStateOf(false) }
    var commentsError by remember(aid) { mutableStateOf<String?>(null) }
    val selfMid = remember { BilibiliApi.parseSelfMid() }
    val commentComposeFocus = remember { FocusRequester() }
    var commentDraft by remember { mutableStateOf("") }
    var commentReplyTarget by remember { mutableStateOf<CommentItem?>(null) }
    var commentReplyRoot by remember { mutableStateOf<CommentItem?>(null) }
    var commentSending by remember { mutableStateOf(false) }
    var commentSendError by remember { mutableStateOf<String?>(null) }
    var commentDeleteTarget by remember { mutableStateOf<CommentItem?>(null) }
    var commentDeleting by remember { mutableStateOf(false) }
    var commentDeleteError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(overlay) {
        val previous = observedOverlay
        observedOverlay = overlay
        if (previous != null && overlay == null) {
            val target = pendingOverlayFocus ?: playFocus
            pendingOverlayFocus = null
            // The overlay's focused row is removed in the same frames that clear
            // its focus; a short 60-120ms restore can be overwritten. 300ms is
            // measured on the Duo/MuMu bottom display and still feels immediate.
            delay(300)
            runCatching { target.requestFocus() }
        }
    }
    var nextCursor by remember(aid) { mutableIntStateOf(0) }
    var commentsLoaded by remember(aid) { mutableStateOf(false) }
    var commentsRequestId by remember(aid) { mutableIntStateOf(0) }
    var commentSort by remember(aid) { mutableIntStateOf(3) }
    var commentLikeState by remember(aid) { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    var commentLikeDelta by remember(aid) { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var commentDislikeState by remember(aid) { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    var expandedReplies by remember(aid) { mutableStateOf<Set<Long>>(emptySet()) }
    var repliesByRoot by remember(aid) { mutableStateOf<Map<Long, List<CommentItem>>>(emptyMap()) }
    var repliesLoading by remember(aid) { mutableStateOf<Set<Long>>(emptySet()) }
    var repliesError by remember(aid) { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var repliesPage by remember(aid) { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var expandedComments by remember(aid) { mutableStateOf<Set<Long>>(emptySet()) }
    var viewerPictures by remember { mutableStateOf<List<CommentPicture>>(emptyList()) }
    var viewerStartIndex by remember { mutableIntStateOf(0) }

    var related by remember(ownerMid, videoBvid) { mutableStateOf<List<UserVideoItem>>(emptyList()) }
    var relatedLoading by remember(ownerMid, videoBvid) { mutableStateOf(false) }
    var relatedLoaded by remember(ownerMid, videoBvid) { mutableStateOf(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun recallLastDanmaku() {
        if (recallingDanmaku) return
        val dmid = lastSentDmid
        val cid = lastSentCid
        if (dmid <= 0L || cid <= 0L) return
        recallingDanmaku = true
        scope.launch {
            repository.recallDanmaku(cid = cid, dmid = dmid)
                .onSuccess {
                    recallingDanmaku = false
                    lastSentDmid = 0L
                    lastSentCid = 0L
                    toast("弹幕已撤回")
                    vm.refreshDanmaku()
                    delay(120)
                    runCatching { danmakuSendFocus.requestFocus() }
                }
                .onFailure { error ->
                    recallingDanmaku = false
                    toast(error.message ?: "撤回失败")
                }
        }
    }

    LaunchedEffect(overlay) {
        if (overlay != DuoOverlay.DANMAKU_SEND && overlay != DuoOverlay.COMMENT_SEND) {
            keyboard?.hide()
        }
    }

    fun submitDanmaku() {
        if (danmakuSending) return
        val text = danmakuDraft.trim()
        val validation = validateDanmakuText(text)
        if (validation != null) {
            danmakuSendError = validation
            return
        }
        if (ui.isPgc) {
            danmakuSendError = "番剧弹幕发送暂未开放"
            return
        }
        if (BilibiliApi.loginCookies.isBlank() || BilibiliApi.biliJct.isBlank()) {
            danmakuSendError = "请先登录"
            return
        }
        val aid = ui.videoInfo?.aid ?: 0L
        val cid = ui.currentPage?.cid ?: 0L
        if (aid <= 0L || cid <= 0L) {
            danmakuSendError = "当前视频暂不支持发送弹幕"
            return
        }
        danmakuSending = true
        danmakuSendError = null
        scope.launch {
            repository.sendDanmaku(
                aid = aid,
                cid = cid,
                bvid = ui.videoInfo?.bvid ?: bvid,
                message = text,
                progressMs = playback.positionMs,
                mode = danmakuMode,
                color = danmakuColor,
            )
                .onSuccess { dmid ->
                    danmakuSending = false
                    danmakuDraft = ""
                    danmakuSendError = null
                    lastSentDmid = dmid
                    lastSentCid = cid
                    session.closeOverlay()
                    keyboard?.hide()
                    toast("弹幕已发送")
                    vm.refreshDanmaku()
                }
                .onFailure { error ->
                    danmakuSending = false
                    danmakuSendError = error.message ?: "发送失败"
                }
        }
    }

    fun openImages(pictures: List<CommentPicture>, index: Int) {
        if (pictures.isEmpty()) return
        viewerPictures = pictures
        viewerStartIndex = index.coerceIn(0, pictures.lastIndex)
        session.openOverlay(DuoOverlay.IMAGE)
    }

    fun leave() {
        controller.stop()
        vm.reset()
        session.onPlayerExited()
        onBack()
    }

    BackHandler { leave() }

    LaunchedEffect(Unit) {
        session.onPlayerEntered()
        when {
            pgcSeasonId != null -> vm.loadPgc(pgcSeasonId, startCid)
            bvid.isNotBlank() -> vm.load(bvid, startCid, source)
        }
    }

    LaunchedEffect(focusEpoch) {
        delay(120L)
        if (session.mode.value == DuoMode.PLAYER && overlay == null) {
            val result = runCatching { playFocus.requestFocus() }
            if (result.isSuccess) initialFocusApplied = true
            if (BuildConfig.DEBUG) {
                Log.d(
                    BilibiliApp.TAG,
                    "PlayerConsole: focus play result=${result.isSuccess} " +
                        "error=${result.exceptionOrNull()?.message}",
                )
            }
        }
    }
    // The play button is disabled while playurl loads, so the first attempt can
    // fail. Focus it once media becomes active, but never steal focus later.
    LaunchedEffect(playback.isActive) {
        if (!initialFocusApplied && playback.isActive) {
            delay(120L)
            if (runCatching { playFocus.requestFocus() }.isSuccess) {
                initialFocusApplied = true
            }
        }
    }
    DisposableEffect(Unit) {
        session.setFocusBackInterceptor {
            if (rightPaneFocusedState) {
                scope.launch {
                    delay(80L)
                    runCatching { playFocus.requestFocus() }
                }
                true
            } else {
                false
            }
        }
        onDispose { session.setFocusBackInterceptor(null) }
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.stop()
            vm.reset()
            session.onPlayerExited()
        }
    }

    LaunchedEffect(aid) {
        val info = ui.videoInfo ?: return@LaunchedEffect
        isFollowed = null
        isFavorited = null
        if (BilibiliApi.loginCookies.isNotBlank()) {
            repository.checkRelation(info.owner.mid).onSuccess { isFollowed = it }
            repository.checkFavoured(info.aid).onSuccess { isFavorited = it }
        }
    }

    suspend fun loadComments(cursor: Int, force: Boolean = false) {
        val videoAid = ui.videoInfo?.aid ?: return
        if (commentsLoading && !force) return
        val requestId = ++commentsRequestId
        commentsLoading = true
        commentsError = null
        try {
            val result = repository.getComments(videoAid, page = cursor, mode = commentSort)
            result.onSuccess { list ->
                // A sort switch may have started a newer request; discard this
                // response instead of mixing two sort orders.
                if (requestId != commentsRequestId) return@onSuccess
                val fresh = (list.topReplies ?: emptyList()) + (list.replies ?: emptyList())
                comments = if (cursor == 0) {
                    fresh.distinctBy { it.rpid }
                } else {
                    (comments + fresh).distinctBy { it.rpid }
                }
                nextCursor = list.cursor?.next ?: 0
                commentsEnded = list.cursor?.isEnd ?: true
                commentsLoaded = true
            }.onFailure {
                if (requestId != commentsRequestId) return@onFailure
                commentsError = it.message ?: "评论加载失败"
            }
        } finally {
            if (requestId == commentsRequestId) commentsLoading = false
        }
    }

    LaunchedEffect(selectedTab, aid, commentSort) {
        if (selectedTab == 0 && aid != null && !commentsLoaded) {
            loadComments(0, force = true)
        }
    }

    LaunchedEffect(selectedTab, ui.videoInfo?.bvid) {
        val relatedBvid = ui.videoInfo?.bvid ?: return@LaunchedEffect
        if (selectedTab == 1 && !relatedLoaded) {
            relatedLoading = true
            repository.getRelatedVideos(relatedBvid)
                .onSuccess { videos ->
                    related = videos.filter { it.bvid != relatedBvid }.take(12)
                    relatedLoaded = true
                }
            relatedLoading = false
        }
    }

    fun toggleCommentLike(comment: CommentItem) {
        val videoAid = ui.videoInfo?.aid ?: return
        if (BilibiliApi.loginCookies.isBlank()) {
            toast("请先登录")
            return
        }
        val previousLiked = commentLikeState[comment.rpid] ?: (comment.action == 1)
        val previousDisliked = commentDislikeState[comment.rpid] ?: (comment.action == 2)
        val previousDelta = commentLikeDelta[comment.rpid] ?: 0
        val next = toggleLikeReaction(previousLiked, previousDisliked, previousDelta)
        commentLikeState = commentLikeState + (comment.rpid to next.liked)
        commentLikeDelta = commentLikeDelta + (comment.rpid to next.likeDelta)
        commentDislikeState = commentDislikeState + (comment.rpid to next.disliked)
        scope.launch {
            repository.likeComment(videoAid, comment.rpid, next.liked)
                .onFailure {
                    commentLikeState = commentLikeState + (comment.rpid to previousLiked)
                    commentLikeDelta = commentLikeDelta + (comment.rpid to previousDelta)
                    commentDislikeState = commentDislikeState + (comment.rpid to previousDisliked)
                    toast(it.message ?: "点赞失败")
                }
        }
    }

    fun toggleCommentDislike(comment: CommentItem) {
        val videoAid = ui.videoInfo?.aid ?: return
        if (BilibiliApi.loginCookies.isBlank() || BilibiliApi.biliJct.isBlank()) {
            toast("请先登录")
            return
        }
        val previousLiked = commentLikeState[comment.rpid] ?: (comment.action == 1)
        val previousDisliked = commentDislikeState[comment.rpid] ?: (comment.action == 2)
        val previousDelta = commentLikeDelta[comment.rpid] ?: 0
        val next = toggleDislikeReaction(previousLiked, previousDisliked, previousDelta)
        commentDislikeState = commentDislikeState + (comment.rpid to next.disliked)
        commentLikeState = commentLikeState + (comment.rpid to next.liked)
        commentLikeDelta = commentLikeDelta + (comment.rpid to next.likeDelta)
        scope.launch {
            repository.hateComment(videoAid, comment.rpid, next.disliked)
                .onFailure {
                    commentDislikeState = commentDislikeState + (comment.rpid to previousDisliked)
                    commentLikeState = commentLikeState + (comment.rpid to previousLiked)
                    commentLikeDelta = commentLikeDelta + (comment.rpid to previousDelta)
                    toast(it.message ?: "点踩失败")
                }
        }
    }

    fun openCommentComposer(target: CommentItem? = null, root: CommentItem? = null) {
        if (BilibiliApi.loginCookies.isBlank() || BilibiliApi.biliJct.isBlank()) {
            toast("请先登录")
            return
        }
        commentDraft = ""
        commentReplyTarget = target
        commentReplyRoot = root ?: target
        commentSendError = null
        pendingOverlayFocus = tabFocus[0]
        session.openOverlay(DuoOverlay.COMMENT_SEND)
    }

    fun submitComment() {
        if (commentSending) return
        val videoAid = ui.videoInfo?.aid ?: return
        if (BilibiliApi.loginCookies.isBlank() || BilibiliApi.biliJct.isBlank()) {
            commentSendError = "请先登录"
            return
        }
        val validation = validateComment(commentDraft)
        if (validation != null) {
            commentSendError = validation
            return
        }
        val text = commentDraft.trim()
        val target = commentReplyTarget
        val root = commentReplyRoot
        commentSending = true
        commentSendError = null
        scope.launch {
            repository.addComment(
                aid = videoAid,
                message = text,
                root = root?.rpid,
                parent = target?.rpid,
            ).onSuccess {
                commentSending = false
                session.closeOverlay()
                toast(if (root != null) "回复已发布" else "评论已发布")
                if (root == null) {
                    commentSort = 2
                    comments = emptyList()
                    commentsLoaded = false
                    nextCursor = 0
                    commentsEnded = false
                    expandedReplies = emptySet()
                    repliesByRoot = emptyMap()
                    repliesPage = emptyMap()
                    expandedComments = emptySet()
                    loadComments(0)
                } else {
                    repliesByRoot = repliesByRoot - root.rpid
                    repliesPage = repliesPage - root.rpid
                    expandedReplies = expandedReplies + root.rpid
                    repliesLoading = repliesLoading + root.rpid
                    repliesError = repliesError - root.rpid
                    val refreshed = repository.getReplies(videoAid, root.rpid, 1)
                    refreshed.onSuccess { list ->
                        repliesByRoot = repliesByRoot + (
                            root.rpid to list.replies.orEmpty().filter { it.rpid != root.rpid }
                        )
                        repliesPage = repliesPage + (root.rpid to 1)
                    }.onFailure { error ->
                        repliesError = repliesError + (root.rpid to (error.message ?: "回复加载失败"))
                    }
                    repliesLoading = repliesLoading - root.rpid
                }
            }.onFailure { error ->
                commentSending = false
                commentSendError = error.message ?: "发送失败"
            }
        }
    }

    fun requestDeleteComment(comment: CommentItem) {
        if (selfMid == null) {
            toast("请先登录")
            return
        }
        if (comment.member.mid != selfMid) {
            toast("只能删除自己的评论")
            return
        }
        commentDeleteTarget = comment
        commentDeleteError = null
        pendingOverlayFocus = tabFocus[0]
        session.openOverlay(DuoOverlay.COMMENT_DELETE)
    }

    fun confirmDeleteComment() {
        val target = commentDeleteTarget ?: return
        val videoAid = ui.videoInfo?.aid ?: return
        if (commentDeleting) return
        commentDeleting = true
        commentDeleteError = null
        scope.launch {
            repository.deleteComment(videoAid, target.rpid)
                .onSuccess {
                    commentDeleting = false
                    comments = comments.filterNot { it.rpid == target.rpid }
                    repliesByRoot = repliesByRoot.mapValues { (_, list) ->
                        list.filterNot { it.rpid == target.rpid }
                    }
                    session.closeOverlay()
                    toast("评论已删除")
                }
                .onFailure { error ->
                    commentDeleting = false
                    commentDeleteError = error.message ?: "删除失败"
                }
        }
    }

    fun toggleReplies(comment: CommentItem) {
        if (comment.rcount <= 0) return
        val root = comment.rpid
        if (root in expandedReplies) {
            expandedReplies = expandedReplies - root
            return
        }
        expandedReplies = expandedReplies + root
        if (repliesByRoot.containsKey(root) || root in repliesLoading) return
        val videoAid = ui.videoInfo?.aid ?: return
        repliesLoading = repliesLoading + root
        repliesError = repliesError - root
        scope.launch {
            repository.getReplies(videoAid, root = root, page = 1)
                .onSuccess { list ->
                    repliesByRoot = repliesByRoot + (
                        root to (list.replies.orEmpty().filter { it.rpid != root })
                    )
                    repliesPage = repliesPage + (root to 1)
                }
                .onFailure { error ->
                    repliesError = repliesError + (root to (error.message ?: "回复加载失败"))
                }
            repliesLoading = repliesLoading - root
        }
    }

    fun loadMoreReplies(comment: CommentItem) {
        val root = comment.rpid
        if (root in repliesLoading) return
        val videoAid = ui.videoInfo?.aid ?: return
        val nextPage = (repliesPage[root] ?: 1) + 1
        repliesLoading = repliesLoading + root
        repliesError = repliesError - root
        scope.launch {
            repository.getReplies(videoAid, root = root, page = nextPage)
                .onSuccess { list ->
                    val more = list.replies.orEmpty().filter { it.rpid != root }
                    val merged = (repliesByRoot[root].orEmpty() + more)
                        .distinctBy { it.rpid }
                    repliesByRoot = repliesByRoot + (root to merged)
                    repliesPage = repliesPage + (root to nextPage)
                }
                .onFailure { error ->
                    repliesError = repliesError + (root to (error.message ?: "回复加载失败"))
                }
            repliesLoading = repliesLoading - root
        }
    }

    fun toggleFollow() {
        val info = ui.videoInfo ?: return
        if (BilibiliApi.loginCookies.isBlank()) {
            toast("请先登录")
            return
        }
        if (followBusy) return
        if (isFollowed == null) {
            toast("关注状态加载中")
            return
        }
        val current = isFollowed == true
        scope.launch {
            followBusy = true
            repository.modifyRelation(info.owner.mid, if (current) 2 else 1)
                .onSuccess {
                    isFollowed = !current
                    toast(if (current) "已取消关注" else "已关注")
                }
                .onFailure { toast(it.message ?: "操作失败") }
            followBusy = false
        }
    }

    fun toggleFavorite() {
        val info = ui.videoInfo ?: return
        if (BilibiliApi.loginCookies.isBlank()) {
            toast("请先登录")
            return
        }
        if (favBusy || isFavorited == null) return
        val uid = parseDedeUserId(BilibiliApi.loginCookies)
        if (uid == null) {
            toast("登录状态异常")
            return
        }
        val current = isFavorited == true
        scope.launch {
            favBusy = true
            val folders = repository.getFavFolders(uid, rid = info.aid).getOrElse {
                toast(it.message ?: "收藏夹加载失败")
                favBusy = false
                return@launch
            }
            val result = if (current) {
                val ids = favouredFolderIds(folders)
                if (ids.isEmpty()) {
                    Result.success(Unit)
                } else {
                    repository.dealFavResource(info.aid, "", ids.joinToString(","))
                }
            } else {
                val folderId = pickDefaultFolderId(folders)
                if (folderId == null) {
                    toast("请先在网页端创建收藏夹")
                    favBusy = false
                    return@launch
                }
                repository.dealFavResource(info.aid, folderId.toString(), "")
            }
            result.onSuccess {
                isFavorited = !current
                toast(if (current) "已取消收藏" else "已收藏")
            }.onFailure { toast(it.message ?: "操作失败") }
            favBusy = false
        }
    }

    fun startDownload() {
        val info = ui.videoInfo ?: return
        if (ui.isPgc) {
            toast("番剧下载暂未开放")
            return
        }
        if (AppSettings.wifiOnlyDownload && !AppSettings.isWifiConnected(context)) {
            toast("已开启仅 Wi-Fi 下载")
            return
        }
        downloadQuality = ui.currentQuality ?: ui.availableQualities.firstOrNull()
        downloadAllPages = false
        pendingOverlayFocus = null
        session.openOverlay(DuoOverlay.DOWNLOAD)
    }

    fun enqueueDownload() {
        val info = ui.videoInfo ?: return
        val page = ui.currentPage ?: return
        val quality = downloadQuality ?: ui.currentQuality ?: return
        val manager = DownloadManager.getInstance(context)
        if (downloadAllPages && ui.pages.size > 1) {
            val queued = manager.startBatchDownload(
                bvid = info.bvid,
                videoTitle = info.title,
                cover = info.pic,
                pages = ui.pages.map {
                    DownloadManager.BatchPageInfo(
                        cid = it.cid,
                        page = it.page,
                        part = it.part,
                        aid = info.aid,
                    )
                },
                quality = quality.quality,
                qualityDesc = quality.description,
            )
            toast(if (queued > 0) "已加入 $queued 个分P" else "没有可加入的分P")
            session.closeOverlay()
        } else {
            scope.launch {
                repository.getPlayUrl(info.bvid, page.cid, quality.quality)
                    .onSuccess { url ->
                        manager.startDownload(
                            bvid = info.bvid,
                            cid = page.cid,
                            title = info.title,
                            cover = info.pic,
                            quality = quality.quality,
                            qualityDesc = quality.description,
                            url = url,
                            page = page.page,
                            part = page.part,
                            aid = info.aid,
                        )
                        toast("已加入下载")
                        session.closeOverlay()
                    }
                    .onFailure { toast(it.message ?: "获取下载地址失败") }
            }
        }
    }

    fun switchPage(page: VideoPage) {
        if (page.cid == ui.currentPage?.cid) return
        controller.reportNow()
        vm.selectPage(page)
    }

    fun playRelated(item: UserVideoItem) {
        controller.reportNow()
        selectedTab = 0
        vm.load(item.bvid)
    }

    fun cycleTab(step: Int) {
        val next = (selectedTab + step + 4) % 4
        selectedTab = next
        scope.launch {
            delay(80L)
            runCatching { tabFocus[next].requestFocus() }
        }
    }

    val currentIndex = ui.pages.indexOfFirst { it.cid == ui.currentPage?.cid }
    val hasPrev = currentIndex > 0
    val hasNext = currentIndex in 0 until ui.pages.size - 1
    val nextPage = if (hasNext) ui.pages[currentIndex + 1] else null
    val info = ui.videoInfo
    val duration = playback.durationMs.coerceAtLeast(1L)
    val sliderValue = seekDrag ?: playback.positionMs.coerceIn(0L, duration).toFloat()

    @Composable
    fun ConsoleLeftPane() {
            // Left: playback controls, fixed 272dp like the 544px mockup column.
            Column(
                modifier = Modifier
                    .width(272.dp)
                    .fillMaxHeight(),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .focusRequester(backFocus)
                            .focusProperties {
                                up = FocusRequester.Cancel
                                down = sliderFocus
                            }
                            .duoFocusRing(CircleShape)
                            .clip(CircleShape)
                            .duoClickable(onClick = ::leave),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "退出播放",
                            tint = DuoTextSecondary,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = ui.title.ifBlank { if (ui.isLoading) "正在加载…" else "未选择视频" },
                        color = Color(0xFFF5F5F7),
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(5.dp))
                Text(
                    text = buildString {
                        val owner = info?.owner?.name
                        if (!owner.isNullOrBlank()) append(owner)
                        val views = info?.stat?.view ?: 0L
                        if (views > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${formatCount(views)}播放")
                        }
                        val danmaku = info?.stat?.danmaku ?: 0L
                        if (danmaku > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${formatCount(danmaku)}弹幕")
                        }
                        if (isEmpty()) {
                            append(if (source == PlaybackSource.LOCAL) "本地视频" else "在线视频")
                        }
                    },
                    color = DuoTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTime(seekDrag?.toLong() ?: playback.positionMs),
                        color = DuoTextTertiary,
                        fontSize = 10.5.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatTime(playback.durationMs),
                        color = DuoTextTertiary,
                        fontSize = 10.5.sp,
                    )
                }
                var sliderFocused by remember { mutableStateOf(false) }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .duoFocusRing(RoundedCornerShape(6.dp), focused = sliderFocused),
                    contentAlignment = Alignment.Center,
                ) {
                    Slider(
                        value = sliderValue,
                        onValueChange = { seekDrag = it },
                        onValueChangeFinished = {
                            seekDrag?.let { controller.seekTo(it.toLong()) }
                            seekDrag = null
                        },
                        valueRange = 0f..duration.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = DuoPink,
                            inactiveTrackColor = DuoDivider,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .focusRequester(sliderFocus)
                            .focusProperties {
                                up = backFocus
                                down = playFocus
                            }
                            .onFocusChanged { sliderFocused = it.hasFocus }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) {
                                    false
                                } else {
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            controller.seekBy(-10_000L)
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            controller.seekBy(10_000L)
                                            true
                                        }
                                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                            controller.togglePlay()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                            },
                    )
                }

                // The slider ring extends ~5.75dp outside its 30dp box; keep
                // enough gap so it never touches the play/pause button.
                Spacer(Modifier.height(10.dp))
                val transportNav = Modifier.focusProperties {
                    up = sliderFocus
                    down = if (!ui.isPgc) danmakuSendFocus else qualityFocus
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TransportButton(
                        icon = Icons.Filled.SkipPrevious,
                        description = "上一P",
                        enabled = hasPrev,
                        onClick = { if (hasPrev) switchPage(ui.pages[currentIndex - 1]) },
                        modifier = transportNav,
                    )
                    TransportButton(
                        icon = Icons.Filled.Replay10,
                        description = "后退10秒",
                        enabled = playback.isActive,
                        onClick = { controller.seekBy(-10_000L) },
                        modifier = transportNav,
                    )
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (playback.isActive) DuoPink else DuoSurfaceHigh)
                            .focusRequester(playFocus)
                            .focusProperties {
                                up = sliderFocus
                                down = if (!ui.isPgc) danmakuSendFocus else qualityFocus
                            }
                            .onFocusChanged {
                                playFocused = it.isFocused
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        BilibiliApp.TAG,
                                        "PlayerConsole: play focus=${it.isFocused}",
                                    )
                                }
                            }
                            .then(
                                if (playFocused) {
                                    Modifier.border(2.dp, Color.White, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            // Keep this focusable while playurl is loading so
                            // initial D-pad focus lands here; click is a no-op
                            // until media is active.
                            .duoClickable(enabled = true) {
                                if (playback.isActive) controller.togglePlay()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (playback.isBuffering || ui.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playback.isPlaying) "暂停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                    TransportButton(
                        icon = Icons.Filled.Forward10,
                        description = "前进10秒",
                        enabled = playback.isActive,
                        onClick = { controller.seekBy(10_000L) },
                        modifier = transportNav,
                    )
                    TransportButton(
                        icon = Icons.Filled.SkipNext,
                        description = "下一P",
                        enabled = hasNext,
                        onClick = { if (hasNext) switchPage(ui.pages[currentIndex + 1]) },
                        modifier = transportNav,
                    )
                }

                if (!ui.isPgc) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Match the chips row's 8dp scroll inset so the
                            // input box and quality chip share a left edge.
                            .padding(horizontal = 8.dp)
                            .height(30.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .focusRequester(danmakuSendFocus)
                                .focusProperties {
                                    up = playFocus
                                    down = qualityFocus
                                }
                                .duoFocusRing(RoundedCornerShape(7.dp))
                                .clip(RoundedCornerShape(7.dp))
                                .background(DuoSurfaceHigh)
                                .border(1.dp, DuoDivider, RoundedCornerShape(7.dp))
                                .duoClickable {
                                    danmakuDraft = ""
                                    danmakuSendError = null
                                    pendingOverlayFocus = danmakuSendFocus
                                    session.openOverlay(DuoOverlay.DANMAKU_SEND)
                                }
                                .padding(horizontal = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = DuoPink,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (BilibiliApi.loginCookies.isBlank()) {
                                    "登录后发弹幕…"
                                } else {
                                    "发一条弹幕…"
                                },
                                color = DuoTextTertiary,
                                fontSize = 9.5.sp,
                            )
                            Spacer(Modifier.weight(1f))
                            Text("滚动", color = DuoTextTertiary, fontSize = 8.sp)
                        }
                        if (lastSentDmid > 0L && lastSentCid == ui.currentPage?.cid) {
                            Box(
                                modifier = Modifier
                                    .width(54.dp)
                                    .height(30.dp)
                                    .duoFocusRing(RoundedCornerShape(7.dp))
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(DuoPink.copy(alpha = 0.10f))
                                    .border(1.dp, DuoPink.copy(alpha = 0.55f), RoundedCornerShape(7.dp))
                                    .duoClickable(enabled = !recallingDanmaku) {
                                        recallLastDanmaku()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (recallingDanmaku) "撤回中" else "撤回",
                                    color = DuoPink,
                                    fontSize = 9.sp,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                val chipUp = if (!ui.isPgc) danmakuSendFocus else playFocus
                val chipDown = Modifier.focusProperties {
                    up = chipUp
                    // No next-page card means this is the bottom of the left
                    // pane; Down must not jump into the comment pane.
                    down = if (nextPage != null) nextPageFocus else FocusRequester.Cancel
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .horizontalScroll(rememberScrollState())
                        // Inset the chips inside the scroll viewport so the
                        // first/last chip's focus ring is not clipped.
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ConsoleChip(
                        text = ui.currentQuality?.description?.let(::shortQualityLabel) ?: "清晰度",
                        active = false,
                        modifier = chipDown.then(Modifier.focusRequester(qualityFocus)),
                        onClick = {
                            pendingOverlayFocus = qualityFocus
                            session.openOverlay(DuoOverlay.QUALITY)
                        },
                    )
                    ConsoleChip(
                        text = formatSpeed(playback.speed),
                        active = playback.speed != 1f,
                        modifier = chipDown.then(Modifier.focusRequester(speedFocus)),
                        onClick = {
                            pendingOverlayFocus = speedFocus
                            session.openOverlay(DuoOverlay.SPEED)
                        },
                    )
                    ConsoleChip(
                        text = if (ui.danmakuEnabled) "弹幕 开" else "弹幕 关",
                        active = ui.danmakuEnabled,
                        modifier = chipDown,
                        onClick = { vm.toggleDanmaku() },
                    )
                    ConsoleChip(
                        text = "音量 ${(playback.volume * 100).toInt()}%",
                        active = false,
                        modifier = chipDown,
                        onClick = {
                            controller.setVolume(
                                if (playback.volume >= 0.99f) 0f
                                else (playback.volume + 0.25f).coerceAtMost(1f)
                            )
                        },
                    )
                    ConsoleIconChip(
                        icon = Icons.Filled.Settings,
                        description = "播放设置",
                        modifier = chipDown.then(Modifier.focusRequester(settingsFocus)),
                        onClick = {
                            pendingOverlayFocus = settingsFocus
                            session.openOverlay(DuoOverlay.SETTINGS)
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(DuoDivider))
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("接下来播放", color = DuoTextTertiary, fontSize = 10.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = when {
                            ui.isPgc -> "共 ${ui.pages.size} 集"
                            ui.pages.size > 1 -> "共 ${ui.pages.size}P"
                            else -> "单P"
                        },
                        color = DuoTextTertiary,
                        fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (nextPage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nextPageFocus)
                            .focusProperties {
                                up = qualityFocus
                                down = FocusRequester.Cancel
                            }
                            .duoFocusRing(RoundedCornerShape(7.dp))
                            .clip(RoundedCornerShape(7.dp))
                            .background(DuoSurface)
                            .duoClickable { switchPage(nextPage) }
                            .padding(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .height(32.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(DuoSurfaceHigh),
                        ) {
                            val preview = info?.pic.orEmpty()
                            if (preview.isNotBlank()) {
                                AsyncImage(
                                    model = fixPic(preview),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            if (nextPage.duration > 0) {
                                Text(
                                    text = formatTime(nextPage.duration * 1000L),
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .padding(horizontal = 3.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (ui.isPgc) {
                                    "第 ${nextPage.page} 集 ${nextPage.part.ifBlank { "" }}"
                                } else {
                                    "P${nextPage.page} ${nextPage.part.ifBlank { "第 ${nextPage.page} 话" }}"
                                },
                                color = Color(0xFFF5F5F7),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = buildString {
                                    info?.owner?.name?.takeIf { it.isNotBlank() }?.let { append(it) }
                                    val views = info?.stat?.view ?: 0L
                                    if (views > 0) {
                                        if (isNotEmpty()) append(" · ")
                                        append("${formatCount(views)}播放")
                                    }
                                },
                                color = DuoTextTertiary,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    Text(
                        text = when {
                            ui.isPgc -> "已是最后一集"
                            ui.pages.size <= 1 -> "单P视频"
                            else -> "已是最后一P"
                        },
                        color = DuoTextTertiary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }

                Spacer(Modifier.weight(1f))
                Text(
                    text = "方向键选择 · A 确认 · B 返回\n" +
                        if (selectedTab == 0) {
                            "LB/RB 切 tab · L2/R2 评论排序 · X 弹幕 · START 菜单"
                        } else {
                            "LB/RB 切换 tab · X 弹幕 · START 菜单"
                        },
                    color = DuoTextTertiary,
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp,
                )
            }

    }

    @Composable
    fun RowScope.ConsoleRightPane() {
            // Right: comments / related / intro / parts panel.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onFocusChanged { rightPaneFocused = it.hasFocus }
                    .clip(RoundedCornerShape(12.dp))
                    .background(DuoSurface)
                    .border(1.dp, DuoDivider, RoundedCornerShape(12.dp)),
            ) {
                // The right pane's top edge: Up stays here instead of jumping
                // back to the left column.
                val tabUp = Modifier.focusProperties { up = FocusRequester.Cancel }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Keep 6dp below the 34dp tabs so the ring does not
                        // collide with the divider/content underneath.
                        .height(46.dp)
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    ConsoleTab(
                        text = "评论",
                        count = ui.videoInfo?.stat?.reply ?: 0L,
                        selected = selectedTab == 0,
                        modifier = tabUp,
                        focusRequester = tabFocus[0],
                        onClick = { selectedTab = 0 },
                    )
                    ConsoleTab(
                        text = "推荐",
                        selected = selectedTab == 1,
                        modifier = tabUp,
                        focusRequester = tabFocus[1],
                        onClick = { selectedTab = 1 },
                    )
                    ConsoleTab(
                        text = "简介",
                        selected = selectedTab == 2,
                        modifier = tabUp,
                        focusRequester = tabFocus[2],
                        onClick = { selectedTab = 2 },
                    )
                    ConsoleTab(
                        text = if (ui.isPgc) "选集" else "分P",
                        selected = selectedTab == 3,
                        modifier = tabUp,
                        focusRequester = tabFocus[3],
                        onClick = { selectedTab = 3 },
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(DuoDivider))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> CommentsTab(
                            comments = comments,
                            loading = commentsLoading,
                            ended = commentsEnded,
                            error = commentsError,
                            sortMode = commentSort,
                            likeState = commentLikeState,
                            likeDelta = commentLikeDelta,
                            dislikeState = commentDislikeState,
                            expandedReplies = expandedReplies,
                            repliesByRoot = repliesByRoot,
                            repliesLoading = repliesLoading,
                            repliesError = repliesError,
                            onSort = { sort ->
                                if (sort != commentSort) {
                                    commentSort = sort
                                    comments = emptyList()
                                    commentsLoaded = false
                                    nextCursor = 0
                                    commentsEnded = false
                                    expandedReplies = emptySet()
                                    repliesByRoot = emptyMap()
                                    repliesPage = emptyMap()
                                    expandedComments = emptySet()
                                }
                            },
                            onRetry = { scope.launch { loadComments(0) } },
                            onLoadMore = { scope.launch { loadComments(nextCursor) } },
                            onLike = ::toggleCommentLike,
                            onDislike = ::toggleCommentDislike,
                            onToggleReplies = ::toggleReplies,
                            onLoadMoreReplies = ::loadMoreReplies,
                            expandedComments = expandedComments,
                            onToggleCommentBody = { comment ->
                                expandedComments = if (comment.rpid in expandedComments) {
                                    expandedComments - comment.rpid
                                } else {
                                    expandedComments + comment.rpid
                                }
                            },
                            onOpenImages = ::openImages,
                            selfMid = selfMid,
                            composeFocus = commentComposeFocus,
                            onCompose = { openCommentComposer() },
                            onReply = { comment -> openCommentComposer(target = comment) },
                            onReplyToReply = { root, reply ->
                                openCommentComposer(target = reply, root = root)
                            },
                            onDelete = ::requestDeleteComment,
                            onAuthorClick = onUploaderClick,
                        )
                        1 -> RelatedTab(
                            items = related,
                            loading = relatedLoading,
                            onPlay = { playRelated(it) },
                        )
                        2 -> IntroTab(
                            uiState = ui,
                            isPgc = ui.isPgc,
                            isFollowed = isFollowed,
                            followBusy = followBusy,
                            isFavorited = isFavorited,
                            favBusy = favBusy,
                            onFollow = { toggleFollow() },
                            onFavorite = { toggleFavorite() },
                            onDownload = { startDownload() },
                            onPlay = { controller.togglePlay() },
                            onUploaderClick = { ownerMid?.let(onUploaderClick) },
                        )
                        3 -> PartsTab(
                            pages = ui.pages,
                            isPgc = ui.isPgc,
                            currentCid = ui.currentPage?.cid,
                            onSelect = { switchPage(it) },
                        )
                    }
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoBg)
            .onPreviewKeyEvent { event ->
                if (overlay != null || event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key.nativeKeyCode) {
                        android.view.KeyEvent.KEYCODE_BUTTON_L1 -> {
                            cycleTab(-1)
                            true
                        }
                        android.view.KeyEvent.KEYCODE_BUTTON_R1 -> {
                            cycleTab(1)
                            true
                        }
                        android.view.KeyEvent.KEYCODE_BUTTON_X,
                        android.view.KeyEvent.KEYCODE_X -> {
                            vm.toggleDanmaku()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_BUTTON_START,
                        android.view.KeyEvent.KEYCODE_MENU -> {
                            session.openOverlay(DuoOverlay.QUALITY)
                            true
                        }
                        else -> false
                    }
                }
            },
    ) {
        if (ui.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = DuoPink,
                trackColor = Color.Transparent,
            )
        }

        val errorText = ui.error ?: playback.error
        if (errorText != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF3A1519))
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = errorText,
                    color = Color(0xFFFF9AA9),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "重试",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .duoFocusRing(RoundedCornerShape(5.dp))
                        .clip(RoundedCornerShape(5.dp))
                        .duoClickable { controller.retry() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConsoleLeftPane()

            ConsoleRightPane()
        }
    }

    if (overlay == DuoOverlay.QUALITY) {
        val selectedIndex = ui.availableQualities
            .indexOfFirst { it.quality == ui.currentQuality?.quality }
            .coerceAtLeast(0)
        val qualityRowFocus = remember { FocusRequester() }
        val qualityCloseFocus = remember { FocusRequester() }
        var qualityFocusedRow by remember { mutableStateOf(selectedIndex) }
        val focusTrap = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                // Only one column: horizontal moves have nowhere to go, and
                // Down on the last row must not reach the console behind.
                Key.DirectionLeft, Key.DirectionRight -> true
                Key.DirectionDown -> qualityFocusedRow >= ui.availableQualities.lastIndex
                else -> false
            }
        }
        LaunchedEffect(focusEpoch, ui.availableQualities.size) {
            delay(120L)
            runCatching {
                if (ui.availableQualities.isEmpty()) {
                    qualityCloseFocus.requestFocus()
                } else {
                    qualityRowFocus.requestFocus()
                }
            }
        }
        OverlayCard(
            title = "清晰度",
            onDismiss = { session.closeOverlay() },
            closeFocus = qualityCloseFocus,
            modifier = focusTrap,
        ) {
            if (ui.availableQualities.isEmpty()) {
                Text("清晰度加载中…", color = DuoTextSecondary, fontSize = 12.sp)
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        // The scroll container clips to its own bounds. Inset
                        // rows so their focus rings are not cut on the sides.
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                ) {
                    ui.availableQualities.forEachIndexed { index, quality ->
                        OverlayRow(
                            label = quality.description,
                            selected = quality.quality == ui.currentQuality?.quality,
                            focusRequester = if (index == selectedIndex) qualityRowFocus else null,
                            onFocus = { qualityFocusedRow = index },
                            onClick = {
                                session.closeOverlay()
                                if (quality.quality != ui.currentQuality?.quality) {
                                    controller.reportNow()
                                    vm.selectQuality(quality)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (overlay == DuoOverlay.SPEED) {
        val speedIndex = speedOptions
            .indexOfFirst { kotlin.math.abs(playback.speed - it) < 0.01f }
            .coerceAtLeast(0)
        val speedRowFocus = remember { FocusRequester() }
        var speedFocusedRow by remember { mutableStateOf(speedIndex) }
        LaunchedEffect(focusEpoch) {
            delay(120L)
            runCatching { speedRowFocus.requestFocus() }
        }
        OverlayCard(
            title = "倍速",
            onDismiss = { session.closeOverlay() },
            modifier = Modifier.onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight -> true
                    Key.DirectionDown -> speedFocusedRow >= speedOptions.lastIndex
                    else -> false
                }
            },
        ) {
            Column(Modifier.fillMaxWidth()) {
                speedOptions.forEachIndexed { index, speed ->
                    OverlayRow(
                        label = formatSpeed(speed),
                        selected = kotlin.math.abs(playback.speed - speed) < 0.01f,
                        focusRequester = if (index == speedIndex) speedRowFocus else null,
                        onFocus = { speedFocusedRow = index },
                        onClick = {
                            session.closeOverlay()
                            controller.setSpeed(speed)
                        },
                    )
                }
            }
        }
    }

    if (overlay == DuoOverlay.DOWNLOAD) {
        val selectedQuality = downloadQuality ?: ui.currentQuality
        val qualityFocus = remember { FocusRequester() }
        val startFocus = remember { FocusRequester() }
        var downloadStartFocused by remember { mutableStateOf(false) }
        LaunchedEffect(focusEpoch) {
            delay(120L)
            runCatching {
                if (ui.availableQualities.isEmpty()) {
                    startFocus.requestFocus()
                } else {
                    qualityFocus.requestFocus()
                }
            }
        }
        OverlayCard(
            title = "下载视频",
            onDismiss = { session.closeOverlay() },
            modifier = Modifier.onPreviewKeyEvent { event ->
                // The start button is the last focus stop; Down must not reach
                // the console behind the scrim.
                event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown && downloadStartFocused
            },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    // Keep chip focus rings clear of the scroll viewport.
                    .padding(horizontal = 8.dp, vertical = 7.dp),
            ) {
                Text("清晰度", color = DuoTextSecondary, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                if (ui.availableQualities.isEmpty()) {
                    Text("清晰度加载中…", color = DuoTextSecondary, fontSize = 12.sp)
                }
                var qualityIndex = 0
                ui.availableQualities.chunked(2).forEach { rowQualities ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowQualities.forEach { quality ->
                            val index = qualityIndex++
                            DownloadQualityChip(
                                label = quality.description,
                                selected = quality.quality == selectedQuality?.quality,
                                focusRequester = if (index == 0) qualityFocus else null,
                                onClick = { downloadQuality = quality },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowQualities.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (ui.pages.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Text("范围", color = DuoTextSecondary, fontSize = 10.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DownloadQualityChip(
                            label = "当前分P · P${ui.currentPage?.page ?: 1}",
                            selected = !downloadAllPages,
                            focusRequester = null,
                            onClick = { downloadAllPages = false },
                            modifier = Modifier.weight(1f),
                        )
                        DownloadQualityChip(
                            label = "全部分P · ${ui.pages.size} 个",
                            selected = downloadAllPages,
                            focusRequester = null,
                            onClick = { downloadAllPages = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                DuoPillButton(
                    text = "开始下载",
                    primary = true,
                    enabled = selectedQuality != null,
                    focusRequester = startFocus,
                    onClick = { enqueueDownload() },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .onFocusChanged { downloadStartFocused = it.isFocused },
                )
            }
        }
    }

    if (overlay == DuoOverlay.SETTINGS) {
        val danmakuFirstFocus = remember { FocusRequester() }
        val subtitleFirstFocus = remember { FocusRequester() }
        val sectionTabFocus = remember { List(2) { FocusRequester() } }
        val blockedFieldFocus = remember { FocusRequester() }
        var settingsSection by remember { mutableIntStateOf(0) }
        var editingBlocked by remember { mutableStateOf(false) }
        var blockedDraft by remember { mutableStateOf("") }
        val keyboard = LocalSoftwareKeyboardController.current
        val hasSubtitles = ui.availableSubtitles.isNotEmpty()

        fun applyBlockedWords() {
            vm.setDanmakuBlockedWords(parseBlockedDanmakuWords(blockedDraft))
            editingBlocked = false
            keyboard?.hide()
        }

        fun selectSection(section: Int, focusContent: Boolean) {
            settingsSection = section
            scope.launch {
                delay(80L)
                runCatching {
                    when {
                        !focusContent -> sectionTabFocus[section].requestFocus()
                        section == 0 -> danmakuFirstFocus.requestFocus()
                        hasSubtitles -> subtitleFirstFocus.requestFocus()
                        else -> sectionTabFocus[1].requestFocus()
                    }
                }
            }
        }

        fun handleSectionKey(event: androidx.compose.ui.input.key.KeyEvent, current: Int): Boolean {
            if (event.type != KeyEventType.KeyDown) return false
            return when (event.key) {
                Key.DirectionLeft -> {
                    if (current != 0) selectSection(0, focusContent = false)
                    true
                }
                Key.DirectionRight -> {
                    if (current != 1) selectSection(1, focusContent = false)
                    true
                }
                else -> false
            }
        }

        fun cycleSubtitleTrack() {
            val subs = ui.availableSubtitles
            if (subs.isEmpty()) return
            val index = subs.indexOfFirst { it.id == ui.currentSubtitle?.id }
            val next = when {
                index < 0 -> subs.first()
                ui.subtitleEnabled -> subs[(index + 1) % subs.size]
                else -> subs[index]
            }
            if (next.id != ui.currentSubtitle?.id || !ui.subtitleEnabled) {
                vm.selectSubtitle(next)
            }
        }

        LaunchedEffect(focusEpoch, editingBlocked) {
            delay(120L)
            runCatching {
                if (editingBlocked) blockedFieldFocus.requestFocus() else danmakuFirstFocus.requestFocus()
            }
        }
        LaunchedEffect(editingBlocked) {
            if (editingBlocked) {
                blockedDraft = ui.danmakuBlockedWords.joinToString(",")
            } else {
                keyboard?.hide()
            }
        }
        DisposableEffect(editingBlocked) {
            if (editingBlocked) {
                session.setOverlayBackInterceptor {
                    editingBlocked = false
                    true
                }
            }
            onDispose { session.setOverlayBackInterceptor(null) }
        }

        OverlayCard(
            title = if (editingBlocked) "屏蔽词" else "播放设置",
            onDismiss = { session.closeOverlay() },
            // The settings menu is a single column; Left/Right must not move
            // focus to the console behind the scrim. The section tabs handle
            // their own Left/Right first (preview key).
            modifier = if (editingBlocked) {
                Modifier
            } else {
                Modifier.onKeyEvent { event ->
                    event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)
                }
            },
        ) {
            if (editingBlocked) {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = blockedDraft,
                        onValueChange = { blockedDraft = it },
                        label = { Text("用逗号或空格分隔", fontSize = 11.sp) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { applyBlockedWords() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = DuoSurfaceHigh,
                            unfocusedContainerColor = DuoSurfaceHigh,
                            focusedBorderColor = DuoPink,
                            unfocusedBorderColor = DuoDivider,
                            focusedLabelColor = DuoPink,
                            unfocusedLabelColor = DuoTextSecondary,
                            cursorColor = DuoPink,
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(blockedFieldFocus)
                            .duoTextFieldNavigation(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OverlayActionButton(
                            text = "取消",
                            primary = false,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                editingBlocked = false
                                keyboard?.hide()
                            },
                        )
                        OverlayActionButton(
                            text = "保存",
                            primary = true,
                            modifier = Modifier.weight(1f),
                            onClick = { applyBlockedWords() },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "最多 50 个；保存后立即作用于上屏弹幕",
                        color = DuoTextTertiary,
                        fontSize = 10.sp,
                    )
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ConsoleChip(
                            text = "弹幕",
                            active = settingsSection == 0,
                            modifier = Modifier
                                .focusRequester(sectionTabFocus[0])
                                .focusProperties { down = danmakuFirstFocus }
                                .onPreviewKeyEvent { event -> handleSectionKey(event, 0) },
                            onClick = { selectSection(0, focusContent = true) },
                        )
                        ConsoleChip(
                            text = "字幕",
                            active = settingsSection == 1,
                            modifier = Modifier
                                .focusRequester(sectionTabFocus[1])
                                .focusProperties {
                                    down = if (hasSubtitles) {
                                        subtitleFirstFocus
                                    } else {
                                        FocusRequester.Cancel
                                    }
                                }
                                .onPreviewKeyEvent { event -> handleSectionKey(event, 1) },
                            onClick = { selectSection(1, focusContent = true) },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    if (settingsSection == 0) {
                        DanmakuSettingRow(
                            label = "显示弹幕",
                            value = if (ui.danmakuEnabled) "开" else "关",
                            focusRequester = danmakuFirstFocus,
                            modifier = Modifier.focusProperties { up = sectionTabFocus[0] },
                            onClick = { vm.setDanmakuEnabled(!ui.danmakuEnabled) },
                        )
                        DanmakuSettingRow(
                            label = "不透明度",
                            value = "${(ui.danmakuOpacity * 100).toInt()}%",
                            onClick = {
                                vm.setDanmakuOpacity(
                                    nextDanmakuValue(ui.danmakuOpacity, danmakuOpacityOptions)
                                )
                            },
                        )
                        DanmakuSettingRow(
                            label = "弹幕速度",
                            value = danmakuSpeedLabel(ui.danmakuSpeed),
                            onClick = {
                                vm.setDanmakuSpeed(
                                    nextDanmakuValue(ui.danmakuSpeed, danmakuSpeedOptions)
                                )
                            },
                        )
                        DanmakuSettingRow(
                            label = "字号",
                            value = danmakuSizeLabel(ui.danmakuTextSize),
                            onClick = {
                                vm.setDanmakuTextSize(
                                    nextDanmakuValue(ui.danmakuTextSize, danmakuTextSizeOptions)
                                )
                            },
                        )
                        DanmakuSettingRow(
                            label = "显示区域",
                            value = danmakuAreaLabel(ui.danmakuArea),
                            onClick = {
                                vm.setDanmakuArea(
                                    nextDanmakuValue(ui.danmakuArea, danmakuAreaOptions)
                                )
                            },
                        )
                        DanmakuSettingRow(
                            label = "屏蔽词",
                            value = if (ui.danmakuBlockedWords.isEmpty()) {
                                "未设置"
                            } else {
                                "${ui.danmakuBlockedWords.size} 个"
                            },
                            onClick = { editingBlocked = true },
                        )
                        DanmakuSettingRow(
                            label = "同屏上限",
                            value = danmakuMaxOnScreenLabel(ui.danmakuMaxOnScreen),
                            modifier = Modifier.focusProperties { down = FocusRequester.Cancel },
                            onClick = {
                                vm.setDanmakuMaxOnScreen(
                                    nextDanmakuMaxOnScreen(ui.danmakuMaxOnScreen)
                                )
                            },
                        )
                    } else if (!hasSubtitles) {
                        Text(
                            text = "当前视频没有字幕",
                            color = DuoTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                        )
                    } else {
                        DanmakuSettingRow(
                            label = "显示字幕",
                            value = if (ui.subtitleEnabled) "开" else "关",
                            focusRequester = subtitleFirstFocus,
                            modifier = Modifier.focusProperties { up = sectionTabFocus[1] },
                            onClick = { vm.toggleSubtitle() },
                        )
                        DanmakuSettingRow(
                            label = "字幕轨道",
                            value = ui.currentSubtitle?.displayName ?: "未选择",
                            onClick = { cycleSubtitleTrack() },
                        )
                        DanmakuSettingRow(
                            label = "字号",
                            value = subtitleSizeLabel(ui.subtitleTextSizeSp),
                            onClick = {
                                vm.setSubtitleTextSize(
                                    nextSubtitleValue(
                                        ui.subtitleTextSizeSp,
                                        subtitleTextSizeOptions
                                    )
                                )
                            },
                        )
                        DanmakuSettingRow(
                            label = "背景",
                            value = subtitleBackgroundLabel(ui.subtitleBackgroundAlpha),
                            onClick = {
                                vm.setSubtitleBackgroundAlpha(
                                    nextSubtitleValue(
                                        ui.subtitleBackgroundAlpha,
                                        subtitleBackgroundOptions
                                    )
                                )
                            },
                        )
                        DanmakuSettingRow(
                            label = "位置",
                            value = subtitlePositionLabel(ui.subtitleBottomPaddingDp),
                            modifier = Modifier.focusProperties { down = FocusRequester.Cancel },
                            onClick = {
                                vm.setSubtitleBottomPadding(
                                    nextSubtitleBottomPadding(ui.subtitleBottomPaddingDp)
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    if (overlay == DuoOverlay.DANMAKU_SEND) {
        DanmakuSendOverlay(
            text = danmakuDraft,
            sending = danmakuSending,
            error = danmakuSendError,
            positionMs = playback.positionMs,
            loggedIn = BilibiliApi.loginCookies.isNotBlank(),
            mode = danmakuMode,
            color = danmakuColor,
            onMode = { danmakuMode = it },
            onColor = { danmakuColor = it },
            onText = { danmakuDraft = it },
            onSend = { submitDanmaku() },
            onCancel = {
                session.closeOverlay()
                keyboard?.hide()
            },
        )
    }

    if (overlay == DuoOverlay.COMMENT_SEND) {
        CommentSendOverlay(
            title = commentReplyTarget?.let { "回复 @${it.member.uname}" } ?: "写评论",
            text = commentDraft,
            sending = commentSending,
            error = commentSendError,
            loggedIn = BilibiliApi.loginCookies.isNotBlank(),
            onText = { commentDraft = it },
            onSend = { submitComment() },
            onCancel = {
                session.closeOverlay()
                keyboard?.hide()
            },
        )
    }

    if (overlay == DuoOverlay.COMMENT_DELETE) {
        CommentDeleteOverlay(
            userName = commentDeleteTarget?.member?.uname.orEmpty(),
            deleting = commentDeleting,
            error = commentDeleteError,
            onConfirm = { confirmDeleteComment() },
            onCancel = { session.closeOverlay() },
        )
    }

    if (overlay == DuoOverlay.IMAGE && viewerPictures.isNotEmpty()) {
        CommentImageViewerOverlay(
            pictures = viewerPictures,
            initialIndex = viewerStartIndex,
            onClose = { session.closeOverlay() },
        )
    }
}

@Composable
private fun IntroTab(
    uiState: PlayerUiState,
    isPgc: Boolean,
    isFollowed: Boolean?,
    followBusy: Boolean,
    isFavorited: Boolean?,
    favBusy: Boolean,
    onFollow: () -> Unit,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
    onPlay: () -> Unit,
    onUploaderClick: () -> Unit = {},
) {
    val info = uiState.videoInfo
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (info == null) {
            Text(
                text = uiState.title.ifBlank { "本地视频" },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "本地下载播放 · 暂无在线简介",
                color = DuoTextSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onPlay) { Text("播放 / 暂停", color = DuoPink) }
        } else {
            Row(verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = fixPic(info.pic),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(112.dp)
                    .height(63.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DuoSurfaceHigh),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = info.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatText("▶ ${formatCount(info.stat.view)}")
                    Spacer(Modifier.width(8.dp))
                    StatText("👍 ${formatCount(info.stat.like)}")
                    Spacer(Modifier.width(8.dp))
                    StatText("⭐ ${formatCount(info.stat.favorite)}")
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (isPgc) {
                        "${info.tname.ifBlank { "番剧" }} · 共 ${uiState.pages.size} 集"
                    } else {
                        "${formatDate(info.pubdate)} · ${info.tname.ifBlank { "视频" }}"
                    },
                    color = DuoTextSecondary,
                    fontSize = 9.sp,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (!isPgc) {
            // UP card with the follow button on the right.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DuoSurface)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .duoFocusRing(RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .duoClickable(onClick = onUploaderClick)
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = fixPic(info.owner.face),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(DuoSurfaceHigh),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = info.owner.name,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "UP 主 · mid ${info.owner.mid} · 空间 ›",
                            color = DuoTextSecondary,
                            fontSize = 9.sp,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                ConsoleChip(
                    text = when {
                        followBusy -> "处理中…"
                        isFollowed == true -> "已关注"
                        isFollowed == false -> "关注"
                        else -> "关注"
                    },
                    active = isFollowed == true,
                    onClick = onFollow,
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionPill(
                    icon = if (isFavorited == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    text = when {
                        favBusy -> "处理中"
                        isFavorited == true -> "已收藏"
                        else -> "收藏"
                    },
                    active = isFavorited == true,
                    onClick = onFavorite,
                    modifier = Modifier.focusProperties { down = FocusRequester.Cancel },
                )
                ActionPill(
                    icon = Icons.Filled.FileDownload,
                    text = "下载",
                    active = false,
                    onClick = onDownload,
                    modifier = Modifier.focusProperties { down = FocusRequester.Cancel },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("简介", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(3.dp))
        Text(
            text = info.desc.ifBlank { "暂无简介" },
            color = DuoTextSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        if (!isPgc) {
            Spacer(Modifier.height(6.dp))
            Text("BV ${info.bvid}", color = DuoTextSecondary, fontSize = 9.sp)
        }
        }
    }
}

@Composable
private fun CommentsTab(
    comments: List<CommentItem>,
    loading: Boolean,
    ended: Boolean,
    error: String?,
    sortMode: Int,
    likeState: Map<Long, Boolean>,
    likeDelta: Map<Long, Int>,
    dislikeState: Map<Long, Boolean>,
    expandedReplies: Set<Long>,
    repliesByRoot: Map<Long, List<CommentItem>>,
    repliesLoading: Set<Long>,
    repliesError: Map<Long, String>,
    onSort: (Int) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onLike: (CommentItem) -> Unit,
    onDislike: (CommentItem) -> Unit,
    onToggleReplies: (CommentItem) -> Unit,
    onLoadMoreReplies: (CommentItem) -> Unit,
    expandedComments: Set<Long>,
    onToggleCommentBody: (CommentItem) -> Unit,
    onOpenImages: (List<CommentPicture>, Int) -> Unit,
    selfMid: Long?,
    composeFocus: FocusRequester,
    onCompose: () -> Unit,
    onReply: (CommentItem) -> Unit,
    onReplyToReply: (CommentItem, CommentItem) -> Unit,
    onDelete: (CommentItem) -> Unit,
    onAuthorClick: (Long) -> Unit,
) {
    // The first comment's expand link has no focusable above it inside the
    // list; without this anchor Up falls back to the left transport column.
    val sortFocus = remember { FocusRequester() }
    Column(
        Modifier
            .fillMaxSize()
            // L2/R2 switch the comment sort without stealing L1/R1 from the
            // right-pane tab cycling handled by the console root.
            .duoTabCycling(
                onPreviousSection = { if (sortMode != 3) onSort(3) },
                onNextSection = { if (sortMode != 2) onSort(2) },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 5.dp)
                .height(30.dp)
                .focusRequester(composeFocus)
                .duoFocusRing(RoundedCornerShape(7.dp))
                .clip(RoundedCornerShape(7.dp))
                .background(DuoSurfaceHigh)
                .duoClickable(onClick = onCompose)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("写评论…", color = DuoTextTertiary, fontSize = 10.sp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // The 22dp sort chips need 6dp+ above/below for their ring.
                .height(40.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (sortMode == 3) "按热度排序" else "按时间排序",
                color = DuoTextTertiary,
                fontSize = 10.sp,
            )
            Spacer(Modifier.weight(1f))
            CommentSortChip(
                text = "热门",
                selected = sortMode == 3,
                onClick = { onSort(3) },
                modifier = Modifier.focusRequester(sortFocus),
            )
            Spacer(Modifier.width(4.dp))
            CommentSortChip(
                text = "最新",
                selected = sortMode == 2,
                onClick = { onSort(2) },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                error != null && comments.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(error, color = Color(0xFFFF9AA9), fontSize = 11.sp)
                        Text(
                            text = "重试",
                            color = DuoPink,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .duoFocusRing(RoundedCornerShape(6.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .duoClickable(onClick = onRetry)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                comments.isEmpty() && loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DuoPink, modifier = Modifier.size(24.dp))
                    }
                }
                comments.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无评论", color = DuoTextSecondary, fontSize = 11.sp)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                    ) {
                        itemsIndexed(comments, key = { _, comment -> comment.rpid }) { index, comment ->
                            CommentRow(
                                comment = comment,
                                upTarget = if (index == 0) sortFocus else null,
                                liked = likeState[comment.rpid] ?: (comment.action == 1),
                                likeDelta = likeDelta[comment.rpid] ?: 0,
                                disliked = dislikeState[comment.rpid] ?: (comment.action == 2),
                                replyLikeState = likeState,
                                replyLikeDelta = likeDelta,
                                replyDislikeState = dislikeState,
                                expanded = comment.rpid in expandedReplies,
                                replies = repliesByRoot[comment.rpid].orEmpty(),
                                repliesLoading = comment.rpid in repliesLoading,
                                repliesError = repliesError[comment.rpid],
                                onLike = { onLike(comment) },
                                onDislike = { onDislike(comment) },
                                onToggleReplies = { onToggleReplies(comment) },
                                onLoadMoreReplies = { onLoadMoreReplies(comment) },
                                bodyExpanded = comment.rpid in expandedComments,
                                onToggleBody = { onToggleCommentBody(comment) },
                                onLikeReply = onLike,
                                onDislikeReply = onDislike,
                                onOpenImages = onOpenImages,
                                selfMid = selfMid,
                                onReply = { onReply(comment) },
                                canDelete = selfMid != null && comment.member.mid == selfMid,
                                onDelete = { onDelete(comment) },
                                onReplyReply = { reply -> onReplyToReply(comment, reply) },
                                onDeleteReply = { reply -> onDelete(reply) },
                                onAuthorClick = onAuthorClick,
                            )
                        }
                        item {
                            if (loading) {
                                Box(
                                    Modifier.fillMaxWidth().padding(8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        color = DuoPink,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            } else if (!ended) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .duoFocusRing(RoundedCornerShape(6.dp))
                                        .clip(RoundedCornerShape(6.dp))
                                        .focusProperties {
                                            down = FocusRequester.Cancel
                                        }
                                        .duoClickable(onClick = onLoadMore)
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("加载更多", color = DuoPink, fontSize = 11.sp)
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .duoFocusRing(RoundedCornerShape(6.dp))
                                        .clip(RoundedCornerShape(6.dp))
                                        .focusProperties {
                                            down = FocusRequester.Cancel
                                        }
                                        .duoClickable(onClick = {})
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "没有更多评论了",
                                        color = DuoTextTertiary,
                                        fontSize = 9.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentSortChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier = modifier
            .height(22.dp)
            .duoFocusRing(shape)
            .clip(shape)
            .background(if (selected) DuoPink.copy(alpha = 0.14f) else DuoSurfaceHigh)
            .then(
                if (selected) Modifier.border(1.dp, DuoPink, shape) else Modifier
            )
            .duoClickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) DuoPink else DuoTextSecondary,
            fontSize = 9.5.sp,
        )
    }
}

@Composable
private fun CommentRow(
    comment: CommentItem,
    liked: Boolean,
    likeDelta: Int,
    disliked: Boolean,
    replyLikeState: Map<Long, Boolean>,
    replyLikeDelta: Map<Long, Int>,
    replyDislikeState: Map<Long, Boolean>,
    expanded: Boolean,
    replies: List<CommentItem>,
    repliesLoading: Boolean,
    repliesError: String?,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onToggleReplies: () -> Unit,
    onLoadMoreReplies: () -> Unit,
    bodyExpanded: Boolean,
    onToggleBody: () -> Unit,
    upTarget: FocusRequester? = null,
    onLikeReply: (CommentItem) -> Unit,
    onDislikeReply: (CommentItem) -> Unit,
    onOpenImages: (List<CommentPicture>, Int) -> Unit,
    selfMid: Long?,
    onReply: () -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onReplyReply: (CommentItem) -> Unit,
    onDeleteReply: (CommentItem) -> Unit,
    onAuthorClick: (Long) -> Unit,
) {
    val avatarFocus = remember { FocusRequester() }
    var avatarFocused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .focusRequester(avatarFocus)
                    .focusProperties { if (upTarget != null) up = upTarget }
                    .onFocusChanged { avatarFocused = it.isFocused }
                    .duoClickable { onAuthorClick(comment.member.mid) },
            ) {
                AsyncImage(
                    model = fixPic(comment.member.avatar),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        // 6dp clearance keeps the ring inside the focus
                        // target, so bring-into-view cannot clip it at the
                        // top of the comment list.
                        .padding(6.dp)
                        .duoFocusRing(CircleShape, focused = avatarFocused)
                        .clip(CircleShape)
                        .background(DuoSurfaceHigh),
                )
            }
            Spacer(Modifier.width(2.dp))
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.member.uname,
                        color = Color(0xFFF5F5F7),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = " · ${formatRelativeTime(comment.ctime)}",
                        color = DuoTextTertiary,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(3.dp))
                val bodyLong = comment.content.message.length > 60 ||
                    comment.content.message.count { it == '\n' } >= 3
                Text(
                    text = comment.content.message,
                    color = Color(0xFFF5F5F7),
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    maxLines = if (bodyExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (bodyLong) {
                    Text(
                        text = if (bodyExpanded) "收起" else "展开全文",
                        color = DuoPink,
                        fontSize = 9.5.sp,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .duoFocusRing(RoundedCornerShape(3.dp))
                            .clip(RoundedCornerShape(3.dp))
                            .focusProperties {
                                // Up from the link goes to the comment avatar;
                                // only the first comment's avatar jumps to the
                                // sort chip, keeping the ring in the right pane.
                                if (upTarget != null) up = avatarFocus
                            }
                            .duoClickable(onClick = onToggleBody)
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                    )
                }
                comment.content.pictures?.takeIf { it.isNotEmpty() }?.let { pictures ->
                    CommentPicturesRow(pictures = pictures, onOpenImages = onOpenImages)
                }
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CommentAction(
                        text = formatCount((comment.like + likeDelta).coerceAtLeast(0).toLong()),
                        icon = if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        active = liked,
                        onClick = onLike,
                    )
                    CommentAction(
                        text = if (disliked) "已踩" else "踩",
                        icon = if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        active = disliked,
                        onClick = onDislike,
                    )
                    CommentAction(text = "回复", onClick = onReply)
                    if (canDelete) {
                        CommentAction(text = "删除", onClick = onDelete)
                    }
                    if (comment.rcount > 0) {
                        CommentAction(
                            text = if (expanded) {
                                "收起 ${formatCount(comment.rcount.toLong())}"
                            } else {
                                "回复 ${formatCount(comment.rcount.toLong())}"
                            },
                            onClick = onToggleReplies,
                        )
                    }
                }
            }
        }

        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 40.dp, bottom = 4.dp),
            ) {
                when {
                    repliesLoading && replies.isEmpty() -> {
                        Row(
                            modifier = Modifier.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                color = DuoPink,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("加载回复…", color = DuoTextTertiary, fontSize = 9.sp)
                        }
                    }
                    repliesError != null && replies.isEmpty() -> {
                        Text(
                            text = repliesError,
                            color = Color(0xFFFF9AA9),
                            fontSize = 9.sp,
                            modifier = Modifier.padding(vertical = 5.dp),
                        )
                    }
                    replies.isEmpty() -> {
                        Text(
                            text = "暂无回复",
                            color = DuoTextTertiary,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(vertical = 5.dp),
                        )
                    }
                    else -> {
                        replies.forEach { reply ->
                            ReplyRow(
                                reply = reply,
                                liked = replyLikeState[reply.rpid] ?: (reply.action == 1),
                                likeDelta = replyLikeDelta[reply.rpid] ?: 0,
                                disliked = replyDislikeState[reply.rpid] ?: (reply.action == 2),
                                onLike = { onLikeReply(reply) },
                                onDislike = { onDislikeReply(reply) },
                                onOpenImages = onOpenImages,
                                onReply = { onReplyReply(reply) },
                                canDelete = selfMid != null && reply.member.mid == selfMid,
                                onDelete = { onDeleteReply(reply) },
                            )
                        }
                        if (comment.rcount > replies.size) {
                            if (repliesLoading) {
                                Row(
                                    modifier = Modifier.padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        color = DuoPink,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("加载更多回复…", color = DuoTextTertiary, fontSize = 9.sp)
                                }
                            } else {
                                Text(
                                    text = "查看更多回复 (${comment.rcount - replies.size})",
                                    color = DuoPink,
                                    fontSize = 9.sp,
                                    modifier = Modifier
                                        .padding(vertical = 3.dp)
                                        .duoFocusRing(RoundedCornerShape(3.dp))
                                        .clip(RoundedCornerShape(3.dp))
                                        .duoClickable(onClick = onLoadMoreReplies)
                                        .padding(horizontal = 3.dp, vertical = 2.dp),
                                )
                            }
                        }
                        if (repliesError != null && replies.isNotEmpty()) {
                            Text(
                                text = repliesError,
                                color = Color(0xFFFF9AA9),
                                fontSize = 8.5.sp,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DuoDivider))
    }
}

@Composable
private fun ReplyRow(
    reply: CommentItem,
    liked: Boolean,
    likeDelta: Int,
    disliked: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onOpenImages: (List<CommentPicture>, Int) -> Unit,
    onReply: () -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        AsyncImage(
            model = fixPic(reply.member.avatar),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(24.dp).clip(CircleShape).background(DuoSurfaceHigh),
        )
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.member.uname,
                    color = DuoTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = " · ${formatRelativeTime(reply.ctime)}",
                    color = DuoTextTertiary,
                    fontSize = 8.5.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = reply.content.message,
                color = Color(0xFFE8E8EE),
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            reply.content.pictures?.takeIf { it.isNotEmpty() }?.let { pictures ->
                CommentPicturesRow(pictures = pictures, onOpenImages = onOpenImages)
            }
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CommentAction(
                    text = formatCount((reply.like + likeDelta).coerceAtLeast(0).toLong()),
                    icon = if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    active = liked,
                    onClick = onLike,
                )
                CommentAction(
                    text = if (disliked) "已踩" else "踩",
                    icon = if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    active = disliked,
                    onClick = onDislike,
                )
                CommentAction(text = "回复", onClick = onReply)
                if (canDelete) {
                    CommentAction(text = "删除", onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun CommentAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .duoFocusRing(RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .duoClickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) DuoPink else DuoTextTertiary,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text = text,
            color = if (active) DuoPink else DuoTextTertiary,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun RelatedTab(
    items: List<UserVideoItem>,
    loading: Boolean,
    onPlay: (UserVideoItem) -> Unit,
) {
    when {
        items.isEmpty() && loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DuoPink, modifier = Modifier.size(24.dp))
            }
        }
        items.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无推荐", color = DuoTextSecondary, fontSize = 11.sp)
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
            ) {
                itemsIndexed(items, key = { _, item -> item.bvid }) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusProperties {
                                if (index == items.lastIndex) down = FocusRequester.Cancel
                            }
                            .duoFocusRing(RoundedCornerShape(6.dp))
                            .duoClickable { onPlay(item) }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = fixPic(item.pic),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(92.dp)
                                .height(52.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(DuoSurfaceHigh),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "${item.author} · ${formatCount(item.playCount)}播放",
                                color = DuoTextSecondary,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (item.duration.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Text(item.duration, color = DuoTextSecondary, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartsTab(
    pages: List<VideoPage>,
    isPgc: Boolean,
    currentCid: Long?,
    onSelect: (VideoPage) -> Unit,
) {
    if (pages.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (isPgc) "暂无剧集" else "暂无分P", color = DuoTextSecondary, fontSize = 11.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
        ) {
            itemsIndexed(pages, key = { _, page -> page.cid }) { index, page ->
                val selected = page.cid == currentCid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties {
                            if (index == pages.lastIndex) down = FocusRequester.Cancel
                        }
                        .duoFocusRing(RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) DuoPink.copy(alpha = 0.14f) else Color.Transparent)
                        .duoClickable { onSelect(page) }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isPgc) "第 ${page.page} 集" else "P${page.page}",
                        color = if (selected) DuoPink else DuoTextSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.width(if (isPgc) 48.dp else 30.dp),
                    )
                    Text(
                        text = page.part.ifBlank { "第 ${page.page} 话" },
                        color = if (selected) Color.White else Color(0xFFD8D8DE),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val badge = page.badge.orEmpty()
                    if (badge.isNotBlank()) {
                        Text(
                            text = badge,
                            color = DuoPink,
                            fontSize = 8.sp,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(DuoPink.copy(alpha = 0.14f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                    if (page.duration > 0) {
                        Text(
                            text = formatTime(page.duration * 1000L),
                            color = DuoTextSecondary,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentPicturesRow(
    pictures: List<CommentPicture>,
    onOpenImages: (List<CommentPicture>, Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        pictures.take(3).forEachIndexed { index, picture ->
            val ratio = if (picture.imgWidth > 0 && picture.imgHeight > 0) {
                (picture.imgWidth.toFloat() / picture.imgHeight)
                    .coerceIn(0.65f, 1.8f)
            } else {
                1f
            }
            AsyncImage(
                model = fixPic(picture.imgSrc),
                contentDescription = "评论图片 ${index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(64.dp)
                    .aspectRatio(ratio, matchHeightConstraintsFirst = true)
                    .duoFocusRing(RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(DuoSurfaceHigh)
                    .duoClickable { onOpenImages(pictures, index) },
            )
        }
    }
}

@Composable
private fun CommentImageViewerOverlay(
    pictures: List<CommentPicture>,
    initialIndex: Int,
    onClose: () -> Unit,
) {
    var index by remember(pictures) {
        mutableIntStateOf(initialIndex.coerceIn(0, pictures.lastIndex.coerceAtLeast(0)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(pictures) {
        delay(80L)
        runCatching { focus.requestFocus() }
    }
    fun step(delta: Int) {
        if (pictures.size > 1) index = (index + delta + pictures.size) % pictures.size
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            step(-1)
                            true
                        }
                        Key.DirectionRight -> {
                            step(1)
                            true
                        }
                        // The viewer is modal: keep D-pad focus from leaking
                        // onto the console controls behind it.
                        Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter -> true
                        else -> false
                    }
                }
            },
    ) {
        AsyncImage(
            model = fullCommentImage(pictures.getOrNull(index)?.imgSrc.orEmpty()),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 40.dp, end = 40.dp, top = 44.dp, bottom = 44.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${index + 1} / ${pictures.size}",
                color = DuoTextSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "关闭",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (pictures.size > 1) {
            Text(
                text = "‹",
                color = Color.White,
                fontSize = 30.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .clickable { step(-1) },
            )
            Text(
                text = "›",
                color = Color.White,
                fontSize = 30.sp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .clickable { step(1) },
            )
        }
        Text(
            text = "方向键切换 · B 关闭",
            color = DuoTextTertiary,
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
        )
    }
}

private fun fullCommentImage(url: String): String =
    fixPic(url).substringBefore("@")

@Composable
private fun DanmakuInputField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onIme: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        focusManager.moveFocus(FocusDirection.Down)
                        true
                    }
                    Key.DirectionUp -> {
                        focusManager.moveFocus(FocusDirection.Up)
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onIme()
                        true
                    }
                    else -> false
                }
            }
            .clip(RoundedCornerShape(8.dp))
            .background(DuoSurfaceHigh)
            .border(
                width = 1.dp,
                color = if (focused) DuoPink else DuoDivider,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
        cursorBrush = SolidColor(DuoPink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onIme() }),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text("发一条友善的弹幕…", color = DuoTextTertiary, fontSize = 12.sp)
                }
                inner()
            }
        },
    )
}

@Composable
private fun CommentSendOverlay(
    title: String,
    text: String,
    sending: Boolean,
    error: String?,
    loggedIn: Boolean,
    onText: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        runCatching { fieldFocus.requestFocus() }
        keyboard?.show()
    }
    val length = text.trim().length
    val valid = validateComment(text) == null
    OverlayCard(title = title, onDismiss = onCancel) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "评论会公开显示在当前视频下",
                color = DuoTextTertiary,
                fontSize = 9.sp,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                minLines = 3,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DuoSurfaceHigh,
                    unfocusedContainerColor = DuoSurfaceHigh,
                    focusedBorderColor = DuoPink,
                    unfocusedBorderColor = DuoDivider,
                    cursorColor = DuoPink,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 92.dp)
                    .focusRequester(fieldFocus)
                    .duoTextFieldNavigation(),
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$length/$COMMENT_MAX_LENGTH",
                    color = if (length > COMMENT_MAX_LENGTH) DuoPink else DuoTextTertiary,
                    fontSize = 9.sp,
                )
                Spacer(Modifier.weight(1f))
                if (sending) Text("发布中…", color = DuoPink, fontSize = 9.sp)
            }
            if (!loggedIn) {
                Spacer(Modifier.height(6.dp))
                Text("请先登录后再发表评论", color = DuoPink, fontSize = 10.sp)
            }
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(error, color = DuoPink, fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DuoPillButton(
                    text = "取消",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
                DuoPillButton(
                    text = if (sending) "发布中…" else "发布",
                    onClick = onSend,
                    modifier = Modifier.weight(1f),
                    primary = loggedIn && valid && !sending,
                    enabled = loggedIn && valid && !sending,
                )
            }
        }
    }
}

@Composable
private fun CommentDeleteOverlay(
    userName: String,
    deleting: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
    OverlayCard(title = "删除评论", onDismiss = onCancel) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = if (userName.isBlank()) {
                    "确定删除这条评论？删除后无法恢复。"
                } else {
                    "确定删除 @$userName 的这条评论？删除后无法恢复。"
                },
                color = DuoTextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(error, color = DuoPink, fontSize = 10.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DuoPillButton(
                    text = "取消",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).focusRequester(cancelFocus),
                )
                DuoPillButton(
                    text = if (deleting) "删除中…" else "删除",
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    primary = !deleting,
                    enabled = !deleting,
                )
            }
        }
    }
}

@Composable
private fun DanmakuSendOverlay(
    text: String,
    sending: Boolean,
    error: String?,
    positionMs: Long,
    loggedIn: Boolean,
    mode: Int,
    color: Long,
    onMode: (Int) -> Unit,
    onColor: (Long) -> Unit,
    onText: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        runCatching { fieldFocus.requestFocus() }
    }
    val length = danmakuLength(text.trim())
    OverlayCard(title = "发送弹幕", onDismiss = onCancel) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "发送在 ${formatTime(positionMs)} · ${danmakuModeLabel(mode)}弹幕",
                color = DuoTextTertiary,
                fontSize = 9.sp,
            )
            Spacer(Modifier.height(8.dp))
            DanmakuInputField(
                value = text,
                onValueChange = onText,
                focusRequester = fieldFocus,
                onIme = { keyboard?.show() },
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$length/$DANMAKU_MAX_LENGTH",
                    color = if (length > DANMAKU_MAX_LENGTH) DuoPink else DuoTextTertiary,
                    fontSize = 9.sp,
                )
                Spacer(Modifier.weight(1f))
                if (sending) {
                    Text("发送中…", color = DuoPink, fontSize = 9.sp)
                }
            }
            if (!loggedIn) {
                Spacer(Modifier.height(6.dp))
                Text("请先登录后再发送弹幕", color = DuoPink, fontSize = 10.sp)
            }
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(error, color = DuoPink, fontSize = 10.sp)
            }
            Spacer(Modifier.height(6.dp))
            DanmakuSettingRow(
                label = "位置",
                value = danmakuModeLabel(mode),
                onClick = { onMode(nextDanmakuMode(mode)) },
            )
            DanmakuColorRow(color = color, onColor = onColor)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DuoPillButton(
                    text = "取消",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
                DuoPillButton(
                    text = if (sending) "发送中…" else "发送",
                    onClick = onSend,
                    modifier = Modifier.weight(1f),
                    primary = loggedIn && !sending && length in 1..DANMAKU_MAX_LENGTH,
                    enabled = loggedIn && !sending && length in 1..DANMAKU_MAX_LENGTH,
                )
            }
        }
    }
}

@Composable
private fun OverlayCard(
    title: String,
    onDismiss: () -> Unit,
    closeFocus: FocusRequester? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .heightIn(max = 400.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DuoSurface)
                .clickable { /* consume */ }
                .focusGroup()
                .then(modifier)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "关闭",
                    color = DuoTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .then(
                            if (closeFocus != null) Modifier.focusRequester(closeFocus) else Modifier
                        )
                        .duoFocusRing(RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .duoClickable(onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun DownloadQualityChip(
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(32.dp)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .duoFocusRing(RoundedCornerShape(7.dp))
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) DuoPink.copy(alpha = 0.14f) else DuoSurfaceHigh)
            .border(
                width = 1.dp,
                color = if (selected) DuoPink.copy(alpha = 0.45f) else Color.Transparent,
                shape = RoundedCornerShape(7.dp),
            )
            .duoClickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) DuoPink else Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text("✓", color = DuoPink, fontSize = 11.sp)
        }
    }
}

@Composable
private fun OverlayRow(
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    onFocus: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .duoFocusRing(RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) DuoPink.copy(alpha = 0.14f) else Color.Transparent)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocus?.invoke() }
            .duoClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) DuoPink else Color.White,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = DuoPink, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .duoFocusRing(CircleShape)
            .clip(CircleShape)
            .background(DuoSurfaceHigh)
            .duoClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) Color.White else Color(0xFF5A5A60),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ConsoleChip(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .height(26.dp)
            .duoFocusRing(shape)
            .clip(shape)
            .background(if (active) DuoPink.copy(alpha = 0.14f) else DuoSurfaceHigh)
            .then(
                if (active) {
                    Modifier.border(1.dp, DuoPink, shape)
                } else {
                    Modifier
                }
            )
            .duoClickable(onClick = onClick)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (active) DuoPink else Color(0xFFE4E4EA),
            fontSize = 9.5.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun ConsoleIconChip(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .height(26.dp)
            .duoFocusRing(shape)
            .clip(shape)
            .background(DuoSurfaceHigh)
            .duoClickable(onClick = onClick)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color(0xFFE4E4EA),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun DanmakuSettingRow(
    label: String,
    value: String,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .duoFocusRing(RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .duoClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, color = DuoPink, fontSize = 12.sp)
    }
}

private val danmakuOpacityOptions = listOf(0.3f, 0.5f, 0.7f, 0.9f, 1f)
private val danmakuSpeedOptions = listOf(0.7f, 1f, 1.4f, 1.8f)
private val danmakuTextSizeOptions = listOf(13f, 16f, 20f, 24f)
private val danmakuAreaOptions = listOf(0.3f, 0.55f, 0.8f, 1f)
private val danmakuMaxOnScreenOptions = listOf(0, 30, 60, 100)
private val subtitleTextSizeOptions = listOf(18f, 22f, 26f, 30f)
private val subtitleBackgroundOptions = listOf(0f, 0.35f, 0.55f, 0.8f)
private val subtitleBottomPaddingOptions = listOf(32, 58, 90)

@Composable
private fun OverlayActionButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .duoFocusRing(RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(if (primary) DuoPink else DuoSurfaceHigh)
            .duoClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (primary) Color(0xFF1A0710) else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun nextDanmakuMaxOnScreen(current: Int): Int {
    val index = danmakuMaxOnScreenOptions.indexOf(current)
    val nextIndex = if (index >= 0) (index + 1) % danmakuMaxOnScreenOptions.size else 1
    return danmakuMaxOnScreenOptions[nextIndex]
}

private fun danmakuMaxOnScreenLabel(value: Int): String =
    if (value <= 0) "不限" else value.toString()

private fun nextDanmakuValue(current: Float, options: List<Float>): Float {
    val index = options.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
    return options[(index + 1).mod(options.size)]
}

private fun danmakuSpeedLabel(value: Float): String = when {
    value <= 0.8f -> "0.7×"
    value <= 1.2f -> "1.0×"
    value <= 1.6f -> "1.4×"
    else -> "1.8×"
}

private fun danmakuSizeLabel(value: Float): String = when {
    value <= 14f -> "小"
    value <= 18f -> "标准"
    value <= 22f -> "大"
    else -> "特大"
}

private fun danmakuAreaLabel(value: Float): String = when {
    value <= 0.4f -> "1/4"
    value <= 0.65f -> "1/2"
    value <= 0.9f -> "3/4"
    else -> "全屏"
}

private fun subtitleSizeLabel(value: Float): String = when {
    value <= 19f -> "小"
    value <= 23f -> "标准"
    value <= 27f -> "大"
    else -> "特大"
}

private fun subtitleBackgroundLabel(value: Float): String = when {
    value <= 0.01f -> "无"
    value <= 0.4f -> "淡"
    value <= 0.6f -> "标准"
    else -> "深"
}

private fun subtitlePositionLabel(value: Int): String = when {
    value <= 40 -> "靠下"
    value <= 70 -> "标准"
    else -> "靠上"
}

private fun nextSubtitleValue(current: Float, options: List<Float>): Float {
    val index = options.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
    return options[(index + 1).mod(options.size)]
}

private fun nextSubtitleBottomPadding(current: Int): Int {
    val index = subtitleBottomPaddingOptions.indexOf(current)
    return subtitleBottomPaddingOptions[(index + 1).mod(subtitleBottomPaddingOptions.size)]
}

@Composable
private fun ConsoleTab(
    text: String,
    count: Long? = null,
    selected: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(IntrinsicSize.Max)
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .duoFocusRing(RoundedCornerShape(4.dp))
            .duoClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = text,
                color = if (selected) Color(0xFFF5F5F7) else DuoTextSecondary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            if (count != null && count > 0) {
                Text(
                    text = formatCount(count),
                    color = DuoTextTertiary,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(start = 3.dp, bottom = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                .background(if (selected) DuoPink else Color.Transparent),
        )
    }
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(28.dp)
            .duoFocusRing(RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(if (active) DuoPink.copy(alpha = 0.16f) else DuoSurfaceHigh)
            .duoClickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (active) DuoPink else Color(0xFFD8D8DE),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(text, color = if (active) DuoPink else Color(0xFFD8D8DE), fontSize = 10.sp)
    }
}

@Composable
private fun StatText(text: String) {
    Text(text = text, color = DuoTextSecondary, fontSize = 9.sp)
}

private fun nextDanmakuMode(mode: Int): Int = when (mode) {
    1 -> 5
    5 -> 4
    else -> 1
}

private fun danmakuModeLabel(mode: Int): String = when (mode) {
    5 -> "顶部"
    4 -> "底部"
    else -> "滚动"
}

private val danmakuSendColors = listOf(
    0xFFFFFFL, 0xFB7299L, 0xFFD700L, 0x66CCFFL, 0x7BDD7BL, 0xB18CFFL,
)

@Composable
private fun DanmakuColorRow(color: Long, onColor: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "颜色",
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            danmakuSendColors.forEach { value ->
                val selected = value == color
                val checkColor = if (value == 0xFFFFFFL || value == 0xFFD700L ||
                    value == 0x66CCFFL || value == 0x7BDD7BL
                ) {
                    Color.Black
                } else {
                    Color.White
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .duoFocusRing(CircleShape)
                        .clip(CircleShape)
                        // The send API stores RGB; add the opaque alpha for
                        // Compose or every swatch renders transparent.
                        .background(Color((0xFF000000L or value).toInt()))
                        .duoClickable { onColor(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Text(
                            text = "✓",
                            color = checkColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun formatCount(value: Long): String = when {
    value >= 100_000_000L -> "%.1f亿".format(value / 100_000_000.0)
    value >= 10_000L -> "%.1f万".format(value / 10_000.0)
    else -> value.toString()
}

private fun formatSpeed(speed: Float): String {
    val text = "%.2f".format(speed).trimEnd('0').trimEnd('.')
    return if ('.' in text) "${text}×" else "$text.0×"
}

private fun shortQualityLabel(label: String): String {
    val short = label
        .replace("高清", "")
        .replace("超清", "")
        .replace("流畅", "")
        .replace(" ", "")
        .trim()
    return short.ifBlank { label }
}

private fun formatRelativeTime(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return ""
    val diff = (System.currentTimeMillis() / 1000L - epochSeconds).coerceAtLeast(0L)
    return when {
        diff < 60L -> "刚刚"
        diff < 3600L -> "${diff / 60L}分钟前"
        diff < 86_400L -> "${diff / 3600L}小时前"
        diff < 2_592_000L -> "${diff / 86_400L}天前"
        else -> formatDate(epochSeconds)
    }
}

private fun formatDate(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return ""
    return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        .format(java.util.Date(epochSeconds * 1000L))
}
