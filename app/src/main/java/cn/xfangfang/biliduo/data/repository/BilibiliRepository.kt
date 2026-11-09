package cn.xfangfang.biliduo.data.repository

import android.util.Log
import android.util.Xml
import cn.xfangfang.biliduo.BilibiliApp
import cn.xfangfang.biliduo.BuildConfig
import cn.xfangfang.biliduo.data.api.BilibiliApi
import cn.xfangfang.biliduo.data.api.PassportApi
import cn.xfangfang.biliduo.data.local.PendingReportLogic
import cn.xfangfang.biliduo.data.local.PendingReportManager
import cn.xfangfang.biliduo.data.model.*
import cn.xfangfang.biliduo.util.cleanHtmlText
import cn.xfangfang.biliduo.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import org.xmlpull.v1.XmlPullParser

/** Player metadata shared by subtitles and cross-device resume. */
data class PlaybackMeta(
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    /** Cloud playback position in milliseconds; 0 when the account has none. */
    val lastPlayTimeMs: Long = 0L,
    val lastPlayCid: Long = 0L,
)

class BilibiliRepository(
    private val api: BilibiliApi = BilibiliApi.create(),
    private val passportApi: PassportApi = PassportApi.create()
) {
    suspend fun search(keyword: String, page: Int = 1, order: String? = null): Result<Pair<List<SearchVideoItem>, Int>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "search: keyword=$keyword page=$page order=$order")
        return runCatching<Pair<List<SearchVideoItem>, Int>> {
            val response = api.search(keyword = keyword, page = page, order = order?.ifEmpty { null })
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "search response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                val data = response.data
                Pair(data?.result ?: emptyList(), data?.totalPages ?: 1)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "search failed", it) }
    }

    suspend fun searchUsers(keyword: String, page: Int = 1): Result<Pair<List<SearchUserItem>, Int>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "searchUsers: keyword=$keyword page=$page")
        return runCatching<Pair<List<SearchUserItem>, Int>> {
            val response = api.searchUsers(keyword = keyword, page = page)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "searchUsers response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                val data = response.data
                Pair(data?.result ?: emptyList(), data?.totalPages ?: 1)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "searchUsers failed", it) }
    }

    suspend fun getHotSearches(limit: Int = 20): Result<List<String>> {
        return runCatching {
            val response = api.getSearchSquare(limit = limit)
            if (response.code == 0) {
                response.data?.trending?.list
                    ?.map { it.showName.ifBlank { it.keyword }.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.distinct()
                    ?.take(limit)
                    ?: emptyList()
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getHotSearches failed", it) }
    }

    suspend fun getVideoInfo(bvid: String): Result<VideoInfo> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getVideoInfo: bvid=$bvid")
        return runCatching {
            val response = api.getVideoInfo(bvid)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getVideoInfo response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                response.data ?: throw Exception("Video not found")
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getVideoInfo failed", it) }
    }

    suspend fun getRelatedVideos(bvid: String): Result<List<UserVideoItem>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getRelatedVideos: bvid=$bvid")
        return runCatching {
            val response = api.getRelatedVideos(bvid)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getRelatedVideos response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                response.data.orEmpty().map { item ->
                    UserVideoItem(
                        bvid = item.bvid,
                        aid = item.aid,
                        title = cleanHtmlText(item.title),
                        pic = item.pic,
                        playCount = item.stat?.view ?: 0L,
                        danmakuCount = item.stat?.danmaku ?: 0L,
                        pubdate = item.pubdate,
                        description = cleanHtmlText(item.desc),
                        mid = item.owner?.mid ?: 0L,
                        author = cleanHtmlText(item.owner?.name.orEmpty()),
                        duration = formatDuration(item.duration),
                    )
                }.filter { it.bvid.isNotBlank() }
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getRelatedVideos failed", it) }
    }

    suspend fun getDanmaku(cid: Long): Result<List<DanmakuItem>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getDanmaku: cid=$cid")
        return runCatching {
            val response = api.getDanmakuXml(cid)
            if (!response.isSuccessful) throw Exception("弹幕 HTTP ${response.code()}")
            val body = response.body() ?: throw Exception("弹幕为空")
            val raw = body.bytes()
            val encoding = response.headers()["Content-Encoding"].orEmpty().lowercase()
            val bytes = when (encoding) {
                "deflate" -> {
                    // B 站 dm/list.so 返回 raw deflate（无 zlib 头），个别 CDN 节点
                    // 仍带 zlib 头，所以先按 raw 解，失败再退回带头的解一次。
                    val inflated = runCatching { inflate(raw, nowrap = true) }
                        .getOrDefault(ByteArray(0))
                    if (inflated.isNotEmpty()) inflated else inflate(raw, nowrap = false)
                }
                "gzip" -> GZIPInputStream(raw.inputStream()).use { it.readBytes() }
                else -> raw
            }
            val items = parseDanmakuXml(String(bytes, Charsets.UTF_8))
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getDanmaku: ${items.size} items")
            items
        }.onFailure { Log.e(BilibiliApp.TAG, "getDanmaku failed", it) }
    }

    private fun inflate(data: ByteArray, nowrap: Boolean): ByteArray {
        val inflater = Inflater(nowrap)
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(data.size.coerceAtLeast(1024))
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                    continue
                }
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private fun parseDanmakuXml(xml: String): List<DanmakuItem> {
        val result = mutableListOf<DanmakuItem>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "d") {
                val raw = parser.getAttributeValue(null, "p")
                val text = parser.nextText().trim()
                if (!raw.isNullOrBlank() && text.isNotEmpty()) {
                    val parts = raw.split(',')
                    val seconds = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                    if (seconds > 0.0) {
                        result += DanmakuItem(
                            timeMs = (seconds * 1000.0).toLong(),
                            mode = parts.getOrNull(1)?.toIntOrNull() ?: 1,
                            fontSize = parts.getOrNull(2)?.toFloatOrNull() ?: 25f,
                            color = (parts.getOrNull(3)?.toLongOrNull() ?: 0xFFFFFFL).toInt() and 0xFFFFFF,
                            text = text,
                        )
                    }
                }
                event = parser.eventType
            }
            event = parser.next()
        }
        return result.sortedBy { it.timeMs }
    }

    suspend fun getHomeFeed(
        tab: HomeTab,
        page: Int = 1,
        rankingRid: Int = 0,
    ): Result<Pair<List<HomeVideoItem>, Boolean>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getHomeFeed: tab=$tab page=$page rid=$rankingRid")
        return runCatching<Pair<List<HomeVideoItem>, Boolean>> {
            when (tab) {
                HomeTab.RECOMMEND -> {
                    val response = api.getRecommendFeed(
                        freshIdx = page,
                        freshIdx1h = page,
                    )
                    if (BuildConfig.DEBUG) {
                        Log.d(BilibiliApp.TAG, "recommend response: page=$page code=${response.code} msg=${response.message}")
                    }
                    if (response.code != 0) throw Exception(response.message)
                    val items = response.data?.items.orEmpty()
                        .filter { it.bvid.isNotBlank() && (it.goto.isEmpty() || it.goto == "av") }
                    // fresh_idx has no explicit end marker; stop when a page
                    // comes back empty. The ViewModel also stops when a page
                    // adds no new bvid.
                    items to items.isNotEmpty()
                }
                HomeTab.POPULAR -> {
                    val response = api.getPopular(page = page)
                    if (BuildConfig.DEBUG) {
                        Log.d(BilibiliApp.TAG, "popular response: code=${response.code} msg=${response.message}")
                    }
                    if (response.code != 0) throw Exception(response.message)
                    val data = response.data
                    val items = data?.list.orEmpty().filter { it.bvid.isNotBlank() }
                    val hasMore = items.size >= 20 && data?.noMore != true
                    items to hasMore
                }
                HomeTab.RANKING -> {
                    val response = api.getRanking(rid = rankingRid)
                    if (BuildConfig.DEBUG) {
                        Log.d(BilibiliApp.TAG, "ranking response: code=${response.code} msg=${response.message}")
                    }
                    if (response.code != 0) throw Exception(response.message)
                    val items = response.data?.list.orEmpty().filter { it.bvid.isNotBlank() }
                    // Ranking is a fixed top-100 list; no pagination.
                    items to false
                }
                HomeTab.ANIME, HomeTab.MOVIE ->
                    throw IllegalArgumentException("PGC tab must use getPgcRank")
                HomeTab.LIVE ->
                    throw IllegalArgumentException("LIVE tab must use getLiveRooms")
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getHomeFeed failed", it) }
    }

    suspend fun getPgcRank(seasonType: Int): Result<List<PgcSeasonItem>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getPgcRank: seasonType=$seasonType")
        return runCatching {
            val response = api.getPgcRank(seasonType = seasonType)
            if (response.code != 0) throw Exception(response.message)
            response.result?.list.orEmpty().filter { it.seasonId > 0 }
        }.onFailure { Log.e(BilibiliApp.TAG, "getPgcRank failed", it) }
    }

    suspend fun getPgcSeason(seasonId: Long): Result<PgcSeasonDetail> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getPgcSeason: seasonId=$seasonId")
        return runCatching {
            val response = api.getPgcSeason(seasonId)
            if (response.code != 0) throw Exception(response.message)
            response.result ?: throw Exception("番剧信息为空")
        }.onFailure { Log.e(BilibiliApp.TAG, "getPgcSeason failed", it) }
    }

    suspend fun getPgcPlayUrl(epId: Long, cid: Long): Result<PlayUrlInfo> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getPgcPlayUrl: epId=$epId cid=$cid")
        return runCatching {
            val response = api.getPgcPlayUrl(epId = epId, cid = cid)
            if (response.code != 0) throw Exception(response.message)
            response.result ?: throw Exception("无法获取番剧播放链接")
        }.onFailure { Log.e(BilibiliApp.TAG, "getPgcPlayUrl failed", it) }
    }

    suspend fun getLiveRooms(
        parentAreaId: Int = 0,
        areaId: Int = 0,
        page: Int = 1,
        pageSize: Int = 24,
    ): Result<List<LiveRoomItem>> {
        if (BuildConfig.DEBUG) {
            Log.d(BilibiliApp.TAG, "getLiveRooms: parentArea=$parentAreaId area=$areaId page=$page")
        }
        return runCatching {
            val response = api.getLiveRooms(
                parentAreaId = parentAreaId,
                areaId = areaId,
                page = page,
                pageSize = pageSize,
            )
            if (response.code != 0) throw Exception(response.message)
            response.data.orEmpty()
        }.onFailure { Log.e(BilibiliApp.TAG, "getLiveRooms failed", it) }
    }

    suspend fun getLiveAreas(): Result<List<LiveAreaGroup>> {
        return runCatching {
            val response = api.getLiveAreas()
            if (response.code != 0) throw Exception(response.message)
            response.data.orEmpty().filter { it.id > 0 && it.name.isNotBlank() }
        }.onFailure { Log.e(BilibiliApp.TAG, "getLiveAreas failed", it) }
    }

    suspend fun getDynamicFeed(
        type: String = "all",
        page: Int = 1,
        offset: String = "",
    ): Result<DynamicFeedData> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getDynamicFeed: type=$type page=$page")
        return runCatching {
            val response = api.getDynamicFeed(type = type, page = page, offset = offset)
            if (response.code != 0) throw Exception(response.message)
            response.data ?: DynamicFeedData()
        }.onFailure { Log.e(BilibiliApp.TAG, "getDynamicFeed failed", it) }
    }

    suspend fun getDynamicSpaceFeed(
        hostMid: Long,
        offset: String = "",
    ): Result<DynamicFeedData> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getDynamicSpaceFeed: hostMid=$hostMid")
        return runCatching {
            val response = api.getDynamicSpaceFeed(hostMid = hostMid, offset = offset)
            if (response.code != 0) throw Exception(response.message)
            response.data ?: DynamicFeedData()
        }.onFailure { Log.e(BilibiliApp.TAG, "getDynamicSpaceFeed failed", it) }
    }

    suspend fun likeDynamic(dynId: String, liked: Boolean): Result<Unit> {
        if (BilibiliApi.loginCookies.isBlank() || BilibiliApi.biliJct.isBlank()) {
            return Result.failure(Exception("请先登录"))
        }
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "likeDynamic: dynId=$dynId up=${if (liked) 1 else 2}")
        return runCatching {
            val response = api.likeDynamic(
                csrf = BilibiliApi.biliJct,
                body = DynamicLikeRequest(dynIdStr = dynId, up = if (liked) 1 else 2),
            )
            if (response.code != 0) throw Exception(response.message)
        }.onFailure { Log.e(BilibiliApp.TAG, "likeDynamic failed: dynId=$dynId", it) }
    }

    suspend fun getFollowedLiveAnchors(): Result<List<LiveFollowedAnchor>> {
        val selfMid = BilibiliApi.parseSelfMid()
            ?: return Result.success(emptyList())
        return runCatching {
            val followings = getFollowings(selfMid, page = 1).getOrThrow().first
            val uids = followings.map { it.mid }
                .filter { it > 0L }
                .distinct()
                .take(50)
            if (uids.isEmpty()) return@runCatching emptyList<LiveFollowedAnchor>()
            val response = api.getLiveStatusByUids(uids)
            if (response.code != 0) throw Exception(response.message)
            response.data.orEmpty().values
                .filter { it.liveStatus == 1 && it.roomId > 0L }
                .sortedByDescending { it.online }
                .map { item ->
                    val area = listOf(item.parentName, item.areaName)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(" · ")
                    LiveFollowedAnchor(
                        uid = item.uid,
                        uname = item.uname,
                        face = item.face,
                        roomId = item.roomId,
                        title = item.title,
                        areaName = area,
                        online = item.online,
                    )
                }
        }.onFailure { Log.e(BilibiliApp.TAG, "getFollowedLiveAnchors failed", it) }
    }

    suspend fun getLiveRoomDetail(roomId: Long): Result<LiveRoomDetail> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getLiveRoomDetail: roomId=$roomId")
        return runCatching {
            coroutineScope {
                // Room info and anchor card are independent; the anchor request
                // used to add a full round trip before the play URL could start.
                val infoDeferred = async { api.getLiveRoomInfo(roomId) }
                val anchorDeferred = async { runCatching { api.getLiveAnchor(roomId) } }
                val infoResponse = infoDeferred.await()
                if (infoResponse.code != 0) throw Exception(infoResponse.message)
                val info = infoResponse.data ?: throw Exception("直播间不存在")
                val anchor = anchorDeferred.await()
                    .getOrNull()
                    ?.takeIf { it.code == 0 }
                    ?.data
                    ?.info
                LiveRoomDetail(
                    roomId = info.roomId.takeIf { it > 0 } ?: roomId,
                    uid = info.uid,
                    title = info.title,
                    description = info.description,
                    cover = info.userCover,
                    keyframe = info.keyframe,
                    liveStatus = info.liveStatus,
                    online = info.online,
                    attention = info.attention,
                    areaName = info.areaName,
                    parentAreaName = info.parentAreaName,
                    liveTime = info.liveTime,
                    anchorName = anchor?.uname.orEmpty(),
                    anchorFace = anchor?.face.orEmpty(),
                    anchorOfficial = anchor?.officialVerify?.desc.orEmpty(),
                )
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getLiveRoomDetail failed", it) }
    }

    suspend fun getLivePlayUrl(roomId: Long, qn: Int = 10000): Result<LivePlayResult> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getLivePlayUrl: roomId=$roomId qn=$qn")
        return runCatching {
            val response = api.getLivePlayInfo(roomId = roomId, qn = qn)
            if (response.code != 0) throw Exception(response.message)
            val data = response.data ?: throw Exception("无法获取直播播放地址")
            if (data.liveStatus == 0) throw Exception("主播暂未开播")
            data.playurlInfo?.playurl
                ?.selectLivePlayResult(liveStatus = data.liveStatus)
                ?: throw Exception("该直播间暂无可播放线路")
        }.onFailure { Log.e(BilibiliApp.TAG, "getLivePlayUrl failed", it) }
    }

    suspend fun getLiveDanmakuConfig(roomId: Long): Result<LiveDanmakuConfig> {
        return runCatching {
            val response = api.getLiveDanmakuConf(roomId)
            if (response.code != 0) throw Exception(response.message)
            val data = response.data ?: throw Exception("无法获取直播弹幕配置")
            val fallbackHost = data.host
            val hosts = mutableListOf<LiveDanmakuHost>()
            data.hosts.orEmpty()
                .filter { it.host.isNotBlank() }
                .distinctBy { it.host }
                .forEach { hosts.add(it) }
            if (fallbackHost.isNotBlank() && hosts.none { it.host == fallbackHost }) {
                hosts.add(LiveDanmakuHost(host = fallbackHost))
            }
            if (hosts.isEmpty()) throw Exception("直播弹幕服务器不可用")
            if (data.token.isBlank()) throw Exception("直播弹幕 token 为空")
            LiveDanmakuConfig(token = data.token, hosts = hosts)
        }.onFailure { Log.e(BilibiliApp.TAG, "getLiveDanmakuConfig failed", it) }
    }

    suspend fun sendLiveDanmaku(roomId: Long, message: String): Result<Unit> {
        val text = message.trim()
        validateLiveChatText(text)?.let { return Result.failure(IllegalArgumentException(it)) }
        if (BilibiliApi.loginCookies.isBlank()) {
            return Result.failure(IllegalStateException("请先登录后再发送弹幕"))
        }
        if (BilibiliApi.biliJct.isBlank()) {
            return Result.failure(IllegalStateException("登录状态已失效，请重新登录"))
        }
        if (BuildConfig.DEBUG) {
            Log.d(BilibiliApp.TAG, "sendLiveDanmaku: roomId=$roomId length=${liveChatLength(text)}")
        }
        return runCatching {
            val response = api.sendLiveDanmaku(
                roomId = roomId,
                message = text,
                csrf = BilibiliApi.biliJct,
                csrfToken = BilibiliApi.biliJct,
            )
            if (BuildConfig.DEBUG) {
                Log.d(BilibiliApp.TAG, "sendLiveDanmaku response: code=${response.code} msg=${response.message}")
            }
            if (response.code != 0) throw Exception(response.message)
        }.onFailure { Log.e(BilibiliApp.TAG, "sendLiveDanmaku failed", it) }
    }

    suspend fun getArticleView(cvid: Long): Result<NoteArticle> {
        return runCatching {
            val response = api.getArticleView(id = cvid)
            if (response.code == 0) {
                response.data ?: throw Exception("笔记不存在")
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getArticleView failed", it) }
    }

    suspend fun getHistory(page: Int = 1, pageSize: Int = 20): Result<List<HistoryItem>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getHistory: page=$page pageSize=$pageSize")
        return runCatching {
            val response = api.getHistory(page = page, pageSize = pageSize)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getHistory response: code=${response.code}")
            if (response.code == 0) {
                response.data ?: emptyList()
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getHistory failed", it) }
    }

    private var fullHistoryCache: List<HistoryItem>? = null

    suspend fun getAllHistory(): Result<List<HistoryItem>> {
        fullHistoryCache?.let { return Result.success(it) }
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getAllHistory: start")
        val all = mutableListOf<HistoryItem>()
        var page = 1
        while (true) {
            val r = getHistory(page = page)
            if (r.isFailure) return Result.failure(
                r.exceptionOrNull() ?: Exception("Unknown error")
            )
            val list = r.getOrNull() ?: break
            all.addAll(list)
            if (list.size < 20) break
            page++
        }
        fullHistoryCache = all
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getAllHistory: done, ${all.size} items")
        return Result.success(all)
    }

    fun clearHistoryCache() {
        fullHistoryCache = null
    }

    suspend fun searchHistory(keyword: String, page: Int = 1): Result<List<HistorySearchItem>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "searchHistory: keyword=$keyword page=$page")
        return runCatching {
            val response = api.searchHistory(keyword = keyword, page = page)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "searchHistory response: code=${response.code}")
            if (response.code == 0) {
                response.data?.list ?: emptyList()
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "searchHistory failed", it) }
    }

    suspend fun reportProgress(aid: Long, cid: Long, progress: Long): Result<Unit> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "reportProgress: aid=$aid cid=$cid progress=${progress}s")
        return runCatching<Unit> {
            val response = api.reportProgress(aid = aid, cid = cid, progress = progress, csrf = BilibiliApi.biliJct)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "reportProgress response: code=${response.code} msg=${response.message}")
            if (response.code != 0) {
                Log.w(BilibiliApp.TAG, "reportProgress failed: code=${response.code} msg=${response.message}")
                throw IllegalStateException("history/report code=${response.code} msg=${response.message}")
            }
            Log.d(BilibiliApp.TAG, "reportProgress ok: aid=$aid cid=$cid progress=${progress}s")
        }.onFailure { Log.e(BilibiliApp.TAG, "reportProgress exception: aid=$aid cid=$cid", it) }
    }

    suspend fun deleteHistory(aid: Long): Result<Unit> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "deleteHistory: aid=$aid")
        return runCatching {
            if (BilibiliApi.loginCookies.isBlank() || BilibiliApi.biliJct.isBlank()) {
                throw Exception("请先登录")
            }
            if (aid <= 0L) throw Exception("无法删除这条记录")
            val response = api.deleteHistory(
                kid = "archive_$aid",
                csrf = BilibiliApi.biliJct,
            )
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "deleteHistory response: code=${response.code}")
            if (response.code == 0) Unit else throw Exception(response.message)
        }.onFailure { Log.e(BilibiliApp.TAG, "deleteHistory failed", it) }
    }

    /**
     * Re-sends progress reports that previously failed (offline playback).
     * Newest first; stops at the first failure so a dead network does not
     * burn rate limit on the remaining entries. Returns how many were sent.
     */
    suspend fun flushPendingReports(pending: PendingReportManager): Int {
        if (BilibiliApi.loginCookies.isBlank()) return 0
        val mid = BilibiliApi.parseSelfMid() ?: 0L
        val entries = PendingReportLogic.forAccount(pending.all(), mid)
        if (entries.isEmpty()) return 0
        val sent = PendingReportLogic.flush(entries) { entry ->
            reportProgress(entry.aid, entry.cid, entry.progress).also { result ->
                if (result.isSuccess) pending.remove(entry.aid, entry.cid, entry.mid)
            }
        }
        Log.d(BilibiliApp.TAG, "flushPendingReports: sent=$sent/${entries.size}")
        return sent
    }

    suspend fun getUserVideos(mid: Long, page: Int = 1): Result<Pair<List<UserVideoItem>, UserSpacePage?>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getUserVideos: mid=$mid page=$page")
        return runCatching<Pair<List<UserVideoItem>, UserSpacePage?>> {
            val response = api.getUserVideos(mid = mid, page = page)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getUserVideos response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                val data = response.data
                Pair(data?.list?.vlist ?: emptyList(), data?.page)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getUserVideos failed", it) }
    }

    suspend fun searchChannelVideos(mid: Long, keyword: String, page: Int = 1): Result<Pair<List<UserVideoItem>, UserSpacePage?>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "searchChannelVideos: mid=$mid keyword=$keyword page=$page")
        return runCatching<Pair<List<UserVideoItem>, UserSpacePage?>> {
            val response = api.getUserVideos(mid = mid, keyword = keyword, page = page)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "searchChannelVideos response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                val data = response.data
                Pair(data?.list?.vlist ?: emptyList(), data?.page)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "searchChannelVideos failed", it) }
    }

    suspend fun getSpaceSeasons(mid: Long, pageNum: Int = 1): Result<SpaceSeasonsData> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSpaceSeasons: mid=$mid pageNum=$pageNum")
        return runCatching {
            val response = api.getSpaceSeasons(mid = mid, pageNum = pageNum)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSpaceSeasons response: code=${response.code} msg=${response.message}")
            if (response.code == 0) response.data ?: throw Exception("No data")
            else throw Exception(response.message)
        }.onFailure { Log.e(BilibiliApp.TAG, "getSpaceSeasons failed", it) }
    }

    suspend fun getSeasonArchives(mid: Long, seasonId: Long, pageNum: Int = 1, sortReverse: Boolean? = null): Result<SeasonArchiveData> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSeasonArchives: mid=$mid seasonId=$seasonId pageNum=$pageNum sortReverse=$sortReverse")
        return runCatching {
            val response = api.getSeasonArchives(mid = mid, seasonId = seasonId, pageNum = pageNum, sortReverse = sortReverse)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSeasonArchives response: code=${response.code} msg=${response.message}")
            if (response.code == 0) response.data ?: throw Exception("No data")
            else throw Exception(response.message)
        }.onFailure { Log.e(BilibiliApp.TAG, "getSeasonArchives failed", it) }
    }

    // 合集视频列表：使用 seasons_archives_list API（已验证无需 WBI 签名，Safari UA 可通过）
    suspend fun getSeasonArchivesViaView(bvid: String): Result<Pair<List<SeasonArchiveItem>, SeasonMeta?>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSeasonArchivesViaView: bvid=$bvid")
        return runCatching {
            val response = api.getVideoInfo(bvid)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSeasonArchivesViaView response: code=${response.code} msg=${response.message}")
            if (response.code != 0) throw Exception(response.message)
            val ugc = response.data?.ugcSeason ?: throw Exception("该视频不属于合集")
            val episodes = ugc.sections?.flatMap { it.episodes ?: emptyList() } ?: emptyList()
            val items = episodes.map { ep ->
                SeasonArchiveItem(
                    bvid = ep.bvid,
                    title = ep.arc?.title ?: ep.title,
                    pic = ep.arc?.pic ?: "",
                    duration = ep.arc?.duration ?: ep.page?.duration ?: 0,
                    pubdate = ep.arc?.pubdate ?: 0,
                    stat = ep.arc?.stat
                )
            }
            val meta = SeasonMeta(
                seasonId = ugc.id,
                name = ugc.title,
                cover = ugc.cover,
                total = items.size,
                description = ugc.intro
            )
            items to meta
        }.onFailure { Log.e(BilibiliApp.TAG, "getSeasonArchivesViaView failed", it) }
    }

    suspend fun getFavFolders(upMid: Long, rid: Long? = null): Result<List<FavFolder>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getFavFolders: upMid=$upMid rid=$rid")
        return runCatching {
            val all = mutableListOf<FavFolder>()
            var page = 1
            while (true) {
                val response = api.getFavFolders(
                    upMid = upMid,
                    page = page,
                    pageSize = 100,
                    type = if (rid != null) 2 else null,
                    rid = rid
                )
                if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getFavFolders page=$page code=${response.code}")
                if (response.code != 0) throw Exception(response.message)
                val items = response.data?.list ?: emptyList()
                val total = response.data?.count ?: 0
                all += items
                page++
                if (items.isEmpty() || all.size >= total || page > 50) break
            }
            all
        }.onFailure { Log.e(BilibiliApp.TAG, "getFavFolders failed", it) }
    }

    suspend fun addFavFolder(title: String, intro: String = "", privacy: Int = 0): Result<Long> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "addFavFolder: title=$title privacy=$privacy")
        return runCatching {
            if (BilibiliApi.biliJct.isEmpty()) throw Exception("未登录")
            val response = api.addFavFolder(
                title = title,
                intro = intro,
                privacy = privacy,
                csrf = BilibiliApi.biliJct
            )
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "addFavFolder response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                val id = response.data?.folderId()
                if (id == null || id == 0L) throw Exception("创建收藏夹失败")
                id
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "addFavFolder failed", it) }
    }

    suspend fun editFavFolder(mediaId: Long, title: String, intro: String, privacy: Int): Result<Unit> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "editFavFolder: id=$mediaId title=$title privacy=$privacy")
        return runCatching {
            if (BilibiliApi.biliJct.isEmpty()) throw Exception("未登录")
            val response = api.editFavFolder(
                mediaId = mediaId,
                title = title,
                intro = intro,
                privacy = privacy,
                csrf = BilibiliApi.biliJct
            )
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "editFavFolder response: code=${response.code} msg=${response.message}")
            if (response.code != 0) throw Exception(response.message)
        }.onFailure { Log.e(BilibiliApp.TAG, "editFavFolder failed", it) }
    }

    suspend fun deleteFavFolders(mediaIds: List<Long>): Result<Unit> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "deleteFavFolders: ids=$mediaIds")
        return runCatching {
            if (mediaIds.isEmpty()) throw Exception("请选择要删除的收藏夹")
            if (BilibiliApi.biliJct.isEmpty()) throw Exception("未登录")
            val response = api.deleteFavFolders(
                mediaIds = mediaIds.joinToString(","),
                csrf = BilibiliApi.biliJct
            )
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "deleteFavFolders response: code=${response.code} msg=${response.message}")
            if (response.code != 0) throw Exception(response.message)
        }.onFailure { Log.e(BilibiliApp.TAG, "deleteFavFolders failed", it) }
    }

    suspend fun getFavFolderInfo(mediaId: Long): Result<FavFolderInfo> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getFavFolderInfo: id=$mediaId")
        return runCatching {
            val response = api.getFavFolderInfo(mediaId = mediaId)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getFavFolderInfo response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                response.data ?: throw Exception("收藏夹信息为空")
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getFavFolderInfo failed", it) }
    }

    suspend fun getFavResources(mediaId: Long, page: Int = 1, keyword: String? = null): Result<Pair<List<FavResourceItem>, Boolean>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getFavResources: mediaId=$mediaId page=$page keyword=$keyword")
        return runCatching<Pair<List<FavResourceItem>, Boolean>> {
            val response = api.getFavResources(mediaId = mediaId, pn = page, keyword = keyword)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getFavResources response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                val data = response.data
                Pair(data?.medias ?: emptyList(), data?.hasMore ?: false)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getFavResources failed", it) }
    }

    suspend fun checkFavoured(aid: Long): Result<Boolean> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "checkFavoured: aid=$aid")
        return runCatching {
            val response = api.getFavoured(aid = aid)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "checkFavoured response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                response.data?.favoured ?: false
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "checkFavoured failed", it) }
    }

    suspend fun checkVideoLiked(aid: Long): Result<Boolean> {
        return runCatching {
            val response = api.hasLikedVideo(aid = aid)
            if (response.code == 0) (response.data ?: 0) != 0 else throw Exception(response.message)
        }.onFailure { Log.e(BilibiliApp.TAG, "checkVideoLiked failed", it) }
    }

    suspend fun likeVideo(aid: Long, liked: Boolean): Result<Unit> {
        return runCatching {
            val response = api.likeVideo(
                aid = aid,
                // Bilibili uses 1 for like and 2 for cancel; 0 returns -400.
                like = if (liked) 1 else 2,
                csrf = BilibiliApi.biliJct,
            )
            if (response.code != 0) throw Exception(response.message)
        }.onFailure { Log.e(BilibiliApp.TAG, "likeVideo failed", it) }
    }

    suspend fun checkVideoCoins(aid: Long): Result<Int> {
        return runCatching {
            val response = api.getVideoCoins(aid = aid)
            if (response.code == 0) response.data?.multiply ?: 0 else throw Exception(response.message)
        }.onFailure { Log.e(BilibiliApp.TAG, "checkVideoCoins failed", it) }
    }

    suspend fun addVideoCoin(aid: Long, multiply: Int = 1): Result<Unit> {
        return runCatching {
            val response = api.addVideoCoin(
                aid = aid,
                multiply = multiply,
                selectLike = 0,
                csrf = BilibiliApi.biliJct,
            )
            if (response.code != 0) throw Exception(response.message)
        }.onFailure { Log.e(BilibiliApp.TAG, "addVideoCoin failed", it) }
    }

    suspend fun dealFavResource(
        rid: Long,
        addMediaIds: String,
        delMediaIds: String,
        type: Int = 2,
    ): Result<Unit> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "dealFavResource: rid=$rid type=$type add=$addMediaIds del=$delMediaIds")
        return runCatching {
            val response = api.dealFavResource(
                rid = rid,
                type = type,
                addMediaIds = addMediaIds,
                delMediaIds = delMediaIds,
                csrf = BilibiliApi.biliJct
            )
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "dealFavResource response: code=${response.code} msg=${response.message}")
            if (response.code != 0) {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "dealFavResource failed", it) }
    }

    suspend fun getPlayUrl(bvid: String, cid: Long, qn: Int = 80): Result<String> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getPlayUrl: bvid=$bvid cid=$cid qn=$qn fnval=1 platform=android")
        return runCatching {
            val response = api.getPlayUrl(bvid = bvid, cid = cid, qn = qn)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getPlayUrl response: code=${response.code} msg=${response.message} ttl=${response.ttl}")
            if (response.code == 0) {
                val playUrl = response.data
                if (BuildConfig.DEBUG) {
                    val acceptDesc = playUrl?.accept_description?.joinToString(", ") ?: "N/A"
                    val acceptQual = playUrl?.accept_quality?.joinToString(", ") ?: "N/A"
                    Log.d(BilibiliApp.TAG, "getPlayUrl success: quality=${playUrl?.quality}(${playUrl?.format}) " +
                        "duration=${playUrl?.timelength}ms accept=[$acceptQual]($acceptDesc) " +
                        "durl=${playUrl?.durl?.size} segments")
                    playUrl?.durl?.forEachIndexed { i, d ->
                        Log.d(BilibiliApp.TAG, "getPlayUrl durl[$i]: size=${d.size} len=${d.length} url=${d.url.take(100)}...")
                    }
                }
                val url = playUrl?.durl?.firstOrNull()?.url
                    ?: throw Exception("No playable URL")
                url
            } else {
                Log.w(BilibiliApp.TAG, "getPlayUrl failed: code=${response.code} msg=${response.message}")
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getPlayUrl exception", it) }
    }

    suspend fun getPlayUrlDash(bvid: String, cid: Long): Result<PlayUrlInfo> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getPlayUrlDash: bvid=$bvid cid=$cid fnval=16")
        return runCatching {
            val response = api.getPlayUrlDash(bvid = bvid, cid = cid)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getPlayUrlDash response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                val playUrl = response.data ?: throw Exception("No play URL data")
                if (BuildConfig.DEBUG) {
                    val acceptDesc = playUrl.accept_description?.joinToString(", ") ?: "N/A"
                    val acceptQual = playUrl.accept_quality?.joinToString(", ") ?: "N/A"
                    val videoCount = playUrl.dash?.video?.size ?: 0
                    val audioCount = playUrl.dash?.audio?.size ?: 0
                    Log.d(BilibiliApp.TAG, "getPlayUrlDash success: accept=[$acceptQual]($acceptDesc) video=$videoCount audio=$audioCount")
                    playUrl.dash?.video?.forEach { v ->
                        Log.d(BilibiliApp.TAG, "  video: id=${v.id} ${v.width}x${v.height} codec=${v.codecs} bw=${v.bandwidth}")
                    }
                    playUrl.dash?.audio?.forEach { a ->
                        Log.d(BilibiliApp.TAG, "  audio: id=${a.id} codec=${a.codecs} bw=${a.bandwidth}")
                    }
                }
                playUrl
            } else {
                Log.w(BilibiliApp.TAG, "getPlayUrlDash failed: code=${response.code} msg=${response.message}")
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getPlayUrlDash exception", it) }
    }

    suspend fun getComments(
        aid: Long,
        page: Int = 0,
        mode: Int = 3,
        type: Int = 1,
    ): Result<CommentList> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getComments: oid=$aid type=$type page=$page mode=$mode")
        return runCatching {
            val response = api.getComments(type = type, oid = aid, mode = mode, next = page)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getComments response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                response.data ?: CommentList(null)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getComments failed", it) }
    }

    /** Dynamic comments use the legacy reply endpoint with type=17. */
    suspend fun getDynamicComments(
        oid: Long,
        type: Int = 17,
        page: Int = 1,
    ): Result<CommentList> {
        Log.d(BilibiliApp.TAG, "getDynamicComments: oid=$oid type=$type page=$page")
        return runCatching {
            val response = api.getReplyList(type = type, oid = oid, pn = page, pageSize = 20, sort = 1)
            Log.d(BilibiliApp.TAG, "getDynamicComments response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                response.data ?: CommentList(null)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getDynamicComments failed", it) }
    }

    suspend fun getReplies(aid: Long, root: Long, page: Int = 1): Result<CommentList> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getReplies: aid=$aid root=$root page=$page")
        return runCatching {
            val response = api.getReplies(oid = aid, root = root, pn = page)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getReplies response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                response.data ?: CommentList(null)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getReplies failed", it) }
    }

    suspend fun likeComment(aid: Long, rpid: Long, like: Boolean, type: Int = 1): Result<Unit> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "likeComment: aid=$aid rpid=$rpid like=$like")
        return runCatching {
            if (BilibiliApi.biliJct.isEmpty()) {
                throw Exception("请先登录")
            }
            val response = api.likeComment(
                type = type,
                oid = aid,
                rpid = rpid,
                action = if (like) 1 else 0,
                csrf = BilibiliApi.biliJct
            )
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "likeComment response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                Unit
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "likeComment failed", it) }
    }

    suspend fun addComment(
        aid: Long,
        message: String,
        root: Long? = null,
        parent: Long? = null,
        type: Int = 1,
    ): Result<CommentAddData> {
        Log.d(BilibiliApp.TAG, "addComment: oid=$aid type=$type root=$root parent=$parent")
        return runCatching {
            if (BilibiliApi.biliJct.isEmpty()) {
                throw Exception("请先登录")
            }
            val response = api.addComment(
                type = type,
                oid = aid,
                message = message,
                root = root,
                parent = parent,
                csrf = BilibiliApi.biliJct
            )
            Log.d(
                BilibiliApp.TAG,
                "addComment response: code=${response.code} msg=${response.message} " +
                    "rpid=${response.data?.rpid} toast=${response.data?.successToast}",
            )
            if (response.code == 0) {
                response.data ?: CommentAddData()
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "addComment failed", it) }
    }

    suspend fun deleteComment(aid: Long, rpid: Long, type: Int = 1): Result<Unit> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "deleteComment: aid=$aid rpid=$rpid")
        return runCatching {
            if (BilibiliApi.biliJct.isEmpty()) {
                throw Exception("请先登录")
            }
            val response = api.deleteComment(
                type = type,
                oid = aid,
                rpid = rpid,
                csrf = BilibiliApi.biliJct
            )
            if (response.code == 0) Unit else throw Exception(response.message)
        }.onFailure { Log.e(BilibiliApp.TAG, "deleteComment failed", it) }
    }

    suspend fun suggestSearch(term: String): Result<List<String>> {
        if (term.isBlank()) return Result.success(emptyList())
        return runCatching {
            val response = api.suggestSearch(term.trim())
            if (response.code != 0) emptyList()
            else response.result?.tag.orEmpty()
                .mapNotNull {
                    it.value.ifBlank { it.term }
                        .replace("​", "")
                        .trim()
                        .takeIf(String::isNotEmpty)
                }
                .distinctBy { it.lowercase() }
                .take(8)
        }.onFailure { Log.e(BilibiliApp.TAG, "suggestSearch failed", it) }
    }

    suspend fun sendDanmaku(
        aid: Long,
        cid: Long,
        bvid: String,
        message: String,
        progressMs: Long,
        mode: Int = 1,
        color: Long = 0xFFFFFFL,
    ): Result<Long> {
        if (BuildConfig.DEBUG) {
            Log.d(BilibiliApp.TAG, "sendDanmaku: aid=$aid cid=$cid length=${message.length}")
        }
        return runCatching {
            if (BilibiliApi.loginCookies.isBlank() || BilibiliApi.biliJct.isBlank()) {
                throw Exception("请先登录")
            }
            if (aid <= 0L || cid <= 0L || bvid.isBlank()) {
                throw Exception("当前视频暂不支持发送弹幕")
            }
            val response = api.postDanmaku(
                oid = cid,
                msg = message,
                bvid = bvid,
                progress = progressMs.coerceAtLeast(0L),
                mode = mode,
                color = color,
                csrf = BilibiliApi.biliJct,
            )
            if (BuildConfig.DEBUG) {
                Log.d(BilibiliApp.TAG, "sendDanmaku response: code=${response.code}")
            }
            if (response.code == 0) {
                response.data?.dmid ?: 0L
            } else {
                throw Exception(response.message.ifBlank { "发送失败" })
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "sendDanmaku failed", it) }
    }

    suspend fun recallDanmaku(cid: Long, dmid: Long): Result<Unit> {
        if (BuildConfig.DEBUG) {
            Log.d(BilibiliApp.TAG, "recallDanmaku: cid=$cid dmid=$dmid")
        }
        return runCatching {
            if (BilibiliApi.loginCookies.isBlank() || BilibiliApi.biliJct.isBlank()) {
                throw Exception("请先登录")
            }
            if (cid <= 0L || dmid <= 0L) throw Exception("没有可撤回的弹幕")
            val response = api.recallDanmaku(
                dmid = dmid,
                cid = cid,
                type = 1,
                csrf = BilibiliApi.biliJct,
            )
            if (BuildConfig.DEBUG) {
                Log.d(BilibiliApp.TAG, "recallDanmaku response: code=${response.code} message=${response.message}")
            }
            if (response.code == 0) Unit else throw Exception(response.message.ifBlank { "撤回失败" })
        }.onFailure { Log.e(BilibiliApp.TAG, "recallDanmaku failed", it) }
    }

    suspend fun hateComment(aid: Long, rpid: Long, dislike: Boolean): Result<Unit> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "hateComment: aid=$aid rpid=$rpid dislike=$dislike")
        return runCatching {
            if (BilibiliApi.biliJct.isEmpty()) {
                throw Exception("请先登录")
            }
            val response = api.hateComment(
                oid = aid,
                rpid = rpid,
                action = if (dislike) 1 else 0,
                csrf = BilibiliApi.biliJct
            )
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "hateComment response: code=${response.code} msg=${response.message}")
            if (response.code == 0) {
                Unit
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "hateComment failed", it) }
    }

    suspend fun generateQRCode(): Result<QRLoginData> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "generateQRCode")
        return runCatching {
            val response = passportApi.generateQRCode()
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "generateQRCode response: code=${response.code}")
            if (response.code == 0) {
                response.data ?: throw Exception("Empty QR code data")
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "generateQRCode failed", it) }
    }

    suspend fun pollQRCode(key: String): Result<Pair<Int, QRPollData?>> {
        return runCatching {
            val response = passportApi.pollQRCode(key)
            val statusCode = response.data?.code ?: -1
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "pollQRCode response: outer=${response.code} inner=$statusCode msg=${response.data?.message}")
            Pair(statusCode, response.data)
        }.onFailure { Log.e(BilibiliApp.TAG, "pollQRCode failed") }
    }

    /**
     * Turns the QR-login success URL into session cookies.
     *
     * Since 2026-08 Bilibili returns a `passport.biligame.com/.../crossDomain?ticket=...`
     * link instead of a URL with SESSDATA/bili_jct/DedeUserID in its query.
     * The actual cookies arrive in Set-Cookie when that ticket is opened with a
     * browser User-Agent. Legacy URLs with cookie params are still supported.
     *
     * The ticket is one-shot, so this must run immediately after poll reports 0.
     */
    suspend fun resolveQrLogin(url: String): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val legacy = parseLoginCookiesFromUrl(url)
            if (hasCompleteSession(legacy)) {
                if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "resolveQrLogin: legacy url cookies")
                return@runCatching legacy
            }

            val jar = mutableMapOf<String, String>()
            var current = url
            var hops = 0
            val client = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            while (current.isNotBlank() && hops < 6) {
                val requestBuilder = Request.Builder()
                    .url(current)
                    .header("User-Agent", LOGIN_BROWSER_UA)
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    )
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Referer", "https://www.bilibili.com/")
                if (jar.isNotEmpty()) {
                    requestBuilder.header(
                        "Cookie",
                        jar.entries.joinToString("; ") { "${it.key}=${it.value}" },
                    )
                }
                val response = client.newCall(requestBuilder.build()).execute()
                response.use {
                    extractSetCookies(it, jar)
                    if (hasCompleteSession(jar)) {
                        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "resolveQrLogin: crossDomain cookies")
                        return@runCatching jar.filterKeys { name -> name in LOGIN_COOKIE_NAMES }
                    }
                    val location = it.header("Location")
                    current = if (location.isNullOrBlank()) {
                        ""
                    } else {
                        current.toHttpUrlOrNull()?.resolve(location)?.toString() ?: location
                    }
                }
                hops++
            }
            jar.filterKeys { name -> name in LOGIN_COOKIE_NAMES }
        }
    }

    private fun parseLoginCookiesFromUrl(url: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return result
        query.split('&').forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val name = pair.substring(0, eq)
                if (name in LOGIN_COOKIE_NAMES) {
                    // Keep the raw (still percent-encoded) value: this is what a
                    // browser would put in the Cookie header.
                    result[name] = pair.substring(eq + 1)
                }
            }
        }
        return result
    }

    private fun extractSetCookies(response: Response, jar: MutableMap<String, String>) {
        response.headers("Set-Cookie").forEach { header ->
            val pair = header.substringBefore(';')
            val eq = pair.indexOf('=')
            if (eq > 0) {
                jar[pair.substring(0, eq).trim()] = pair.substring(eq + 1).trim()
            }
        }
    }

    private fun hasCompleteSession(cookies: Map<String, String>): Boolean =
        !cookies["SESSDATA"].isNullOrEmpty() &&
            !cookies["bili_jct"].isNullOrEmpty() &&
            !cookies["DedeUserID"].isNullOrEmpty()

    suspend fun modifyRelation(fid: Long, act: Int): Result<Unit> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "modifyRelation: fid=$fid act=$act")
        return runCatching {
            val response = api.modifyRelation(fid = fid, act = act, csrf = BilibiliApi.biliJct)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "modifyRelation response: code=${response.code} msg=${response.message}")
            if (response.code != 0) throw Exception(response.message)
        }.onFailure { Log.e(BilibiliApp.TAG, "modifyRelation failed", it) }
    }

    suspend fun getFollowings(vmid: Long, page: Int = 1): Result<Pair<List<FollowingItem>, Int>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getFollowings: vmid=$vmid page=$page")
        return runCatching<Pair<List<FollowingItem>, Int>> {
            val response = api.getFollowings(vmid = vmid, page = page)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getFollowings response: code=${response.code}")
            if (response.code == 0) {
                val data = response.data
                Pair(data?.list ?: emptyList(), data?.total ?: 0)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getFollowings failed", it) }
    }

    suspend fun searchFollowings(vmid: Long, name: String, page: Int = 1): Result<Pair<List<FollowingItem>, Int>> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "searchFollowings: vmid=$vmid name=$name page=$page")
        return runCatching<Pair<List<FollowingItem>, Int>> {
            val response = api.searchFollowings(vmid = vmid, name = name, page = page)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "searchFollowings response: code=${response.code}")
            if (response.code == 0) {
                val data = response.data
                Pair(data?.list ?: emptyList(), data?.total ?: 0)
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "searchFollowings failed", it) }
    }

    suspend fun getRelationStat(vmid: Long): Result<RelationStat> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getRelationStat: vmid=$vmid")
        return runCatching {
            val response = api.getRelationStat(vmid = vmid)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getRelationStat response: code=${response.code}")
            if (response.code == 0) {
                response.data ?: throw Exception("Empty stat")
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getRelationStat failed", it) }
    }

    suspend fun getSpaceAccInfo(mid: Long): Result<SpaceAccInfo> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSpaceAccInfo: mid=$mid")
        return runCatching {
            val response = api.getSpaceAccInfo(mid = mid)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSpaceAccInfo response: code=${response.code}")
            if (response.code == 0) {
                response.data ?: throw Exception("Empty space info")
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getSpaceAccInfo failed", it) }
    }

    suspend fun getNavInfo(): Result<NavInfo> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getNavInfo")
        return runCatching {
            val response = api.getNavInfo()
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getNavInfo response: code=${response.code} face=${response.data?.face}")
            if (response.code == 0) {
                response.data ?: throw Exception("Empty nav info")
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getNavInfo failed", it) }
    }

    suspend fun getUserCard(mid: Long): Result<UserCardData> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getUserCard: mid=$mid")
        return runCatching {
            val response = api.getUserCard(mid = mid)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getUserCard response: code=${response.code} face=${response.data?.card?.face}")
            if (response.code == 0) {
                response.data ?: throw Exception("Empty user card")
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getUserCard failed", it) }
    }

    suspend fun checkRelation(fid: Long): Result<Boolean> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "checkRelation: fid=$fid")
        return try {
            val response = api.getRelation(fid)
            if (response.code != 0) {
                Result.failure(Exception("getRelation failed: code=${response.code} msg=${response.message}"))
            } else {
                val attr = response.data?.attribute ?: 0
                Result.success(attr == 2 || attr == 6)
            }
        } catch (e: Exception) {
            Log.e(BilibiliApp.TAG, "checkRelation failed", e)
            Result.failure(e)
        }
    }

    suspend fun isFollowing(selfMid: Long, targetMid: Long): Result<Boolean> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "isFollowing: self=$selfMid target=$targetMid")
        var page = 1
        val maxPages = 10
        val pageSize = 100
        while (page <= maxPages) {
            try {
                val response = api.getFollowings(vmid = selfMid, pageSize = 100, page = page)
                if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "isFollowing page=$page: code=${response.code}")
                if (response.code != 0) {
                    Log.w(BilibiliApp.TAG, "isFollowing page $page failed: code=${response.code} msg=${response.message}")
                    page++
                    continue
                }
                val list = response.data?.list ?: emptyList()
                if (list.any { it.mid == targetMid }) return Result.success(true)
                if (list.size < pageSize) break
                page++
            } catch (e: Exception) {
                Log.w(BilibiliApp.TAG, "isFollowing page $page exception", e)
                page++
            }
        }
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "isFollowing: not found after $page pages")
        return Result.success(false)
    }

    suspend fun getCaptcha(): Result<CaptchaData> {
        return runCatching {
            val response = passportApi.getCaptcha()
            Log.d(BilibiliApp.TAG, "getCaptcha response: code=${response.code} type=${response.data?.type}")
            if (response.code == 0) response.data ?: throw Exception("Empty captcha")
            else throw Exception(response.message)
        }
    }

    suspend fun sendSmsCode(tel: String, token: String, challenge: String, validate: String, seccode: String): Result<SmsSendData> {
        return runCatching {
            val response = passportApi.sendSmsCode(tel = tel, token = token, challenge = challenge, validate = validate, seccode = seccode)
            Log.d(BilibiliApp.TAG, "sendSmsCode response: code=${response.code} message=${response.message}")
            if (response.code == 0) response.data ?: throw Exception("Failed to send SMS")
            else throw Exception(response.message)
        }
    }

    suspend fun smsLoginRaw(tel: String, code: Int, captchaKey: String): Result<Map<String, String>> {
        return runCatching {
            val formBody = okhttp3.FormBody.Builder()
                .add("cid", "86")
                .add("tel", tel)
                .add("code", code.toString())
                .add("source", "main_web")
                .add("captcha_key", captchaKey)
                .add("go_url", "https://www.bilibili.com")
                .add("keep", "1")
                .build()
            val request = okhttp3.Request.Builder()
                .url("https://passport.bilibili.com/x/passport-login/web/login/sms")
                .post(formBody)
                .build()
            withContext(Dispatchers.IO) {
                val response = BilibiliApi.httpClient.newCall(request).execute()
                val bodyStr = response.body?.string().orEmpty()
                val cookies = mutableMapOf<String, String>()
                response.headers("Set-Cookie").forEach { setCookie ->
                    val part = setCookie.substringBefore(";")
                    val eq = part.indexOf("=")
                    if (eq > 0) cookies[part.substring(0, eq).trim()] = part.substring(eq + 1).trim()
                }
                response.close()
                Log.d(BilibiliApp.TAG, "smsLoginRaw code=${response.code}")
                val json = com.google.gson.Gson().fromJson(bodyStr, java.util.Map::class.java)
                val rc = (json?.get("code") as? Number)?.toInt() ?: -1
                if (rc != 0) {
                    throw Exception(json?.get("message")?.toString() ?: "登录失败($rc)")
                }
                val hasSession = cookies.containsKey("SESSDATA") &&
                        cookies.containsKey("bili_jct") &&
                        cookies.containsKey("DedeUserID")
                if (!hasSession) {
                    val dataMsg = (json?.get("data") as? java.util.Map<*, *>)?.get("message")?.toString().orEmpty()
                    throw Exception(dataMsg.ifBlank { "本次登录环境存在风险，无法获取登录凭证" })
                }
                cookies
            }
        }
    }

    suspend fun getWebKey(): Result<WebKeyData> {
        return runCatching {
            val response = passportApi.getWebKey()
            if (response.code == 0) response.data ?: throw Exception("Empty key")
            else throw Exception(response.message)
        }
    }

    suspend fun passwordLoginRaw(username: String, password: String, token: String, challenge: String, validate: String, seccode: String): Result<Map<String, String>> {
        return runCatching {
            val formBody = okhttp3.FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("keep", "0")
                .add("token", token)
                .add("challenge", challenge)
                .add("validate", validate)
                .add("seccode", seccode)
                .add("go_url", "https://www.bilibili.com")
                .add("source", "main_web")
                .build()
            val request = okhttp3.Request.Builder()
                .url("https://passport.bilibili.com/x/passport-login/web/login")
                .post(formBody)
                .build()
            val cookies = withContext(Dispatchers.IO) {
                val response = BilibiliApi.httpClient.newCall(request).execute()
                val bodyStr = response.body?.string().orEmpty()
                val c = mutableMapOf<String, String>()
                response.headers("Set-Cookie").forEach { setCookie ->
                    val part = setCookie.substringBefore(";")
                    val eq = part.indexOf("=")
                    if (eq > 0) c[part.substring(0, eq).trim()] = part.substring(eq + 1).trim()
                }
                response.close()
                Log.d(BilibiliApp.TAG, "passwordLoginRaw code=${response.code}")
                val json = com.google.gson.Gson().fromJson(bodyStr, java.util.Map::class.java)
                val rc = (json?.get("code") as? Number)?.toInt() ?: -1
                if (rc != 0) {
                    throw Exception(json?.get("message")?.toString() ?: "登录失败($rc)")
                }
                val hasSession = c.containsKey("SESSDATA") &&
                        c.containsKey("bili_jct") &&
                        c.containsKey("DedeUserID")
                if (!hasSession) {
                    val dataMsg = (json?.get("data") as? java.util.Map<*, *>)?.get("message")?.toString().orEmpty()
                    throw Exception(dataMsg.ifBlank { "本次登录环境存在风险，无法获取登录凭证" })
                }
                c
            }
            cookies
        }
    }

    suspend fun getPlaybackMeta(aid: Long, cid: Long): Result<PlaybackMeta> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getPlaybackMeta: aid=$aid cid=$cid")
        return runCatching {
            val response = api.getPlayerInfo(aid = aid, cid = cid)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getPlaybackMeta response: code=${response.code}")
            if (response.code == 0) {
                val data = response.data
                val subtitles = data?.subtitle?.subtitles ?: emptyList()
                PlaybackMeta(
                    subtitleTracks = subtitles.map { track ->
                        SubtitleTrack(
                            id = track.id,
                            lan = track.lan,
                            lanDoc = track.lan_doc,
                            subtitleUrl = track.subtitleUrl.let { url ->
                                if (url.startsWith("//")) "https:$url" else url
                            },
                            type = track.type,
                            aiStatus = track.aiStatus
                        )
                    }.filter { it.subtitleUrl.isNotBlank() },
                    lastPlayTimeMs = data?.lastPlayTime?.coerceAtLeast(0L) ?: 0L,
                    lastPlayCid = data?.lastPlayCid ?: 0L,
                )
            } else {
                throw Exception(response.message)
            }
        }.onFailure { Log.e(BilibiliApp.TAG, "getPlaybackMeta failed", it) }
    }

    suspend fun getSubtitleTracks(aid: Long, cid: Long): Result<List<SubtitleTrack>> =
        getPlaybackMeta(aid, cid).map { it.subtitleTracks }

    suspend fun getSubtitleContent(url: String): Result<SubtitleBody> {
        if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSubtitleContent: url=${url.take(80)}")
        return runCatching {
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "https://www.bilibili.com/")
                .build()
            val responseBody = withContext(Dispatchers.IO) {
                BilibiliApi.httpClient.newCall(request).execute().use { response ->
                    response.body?.string() ?: throw Exception("Empty response")
                }
            }
            val body = com.google.gson.Gson().fromJson(responseBody, SubtitleBody::class.java)
            if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "getSubtitleContent: ${body.body.size} cues")
            body
        }.onFailure { Log.e(BilibiliApp.TAG, "getSubtitleContent failed", it) }
    }

    private companion object {
        const val LOGIN_BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        val LOGIN_COOKIE_NAMES = setOf("SESSDATA", "bili_jct", "DedeUserID")
    }
}
