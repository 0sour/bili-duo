package cn.xfangfang.biliduo.data.api

import android.os.Build
import android.util.Log
import cn.xfangfang.biliduo.BilibiliApp
import cn.xfangfang.biliduo.BuildConfig
import cn.xfangfang.biliduo.data.model.*
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.TlsVersion
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

interface BilibiliApi {

    @GET("x/web-interface/search/type")
    suspend fun search(
        @Query("search_type") searchType: String = "video",
        @Query("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("order") order: String? = null
    ): ApiResponse<SearchResult>

    @GET("https://s.search.bilibili.com/main/suggest")
    suspend fun suggestSearch(
        @Query("term") term: String,
        @Query("main_ver") mainVer: String = "v1"
    ): SearchSuggestResponse

    @GET("x/web-interface/search/square")
    suspend fun getSearchSquare(
        @Query("limit") limit: Int = 20
    ): ApiResponse<SearchSquareData>

    @GET("x/web-interface/view")
    suspend fun getVideoInfo(@Query("bvid") bvid: String): ApiResponse<VideoInfo>

    @GET("x/web-interface/archive/related")
    suspend fun getRelatedVideos(@Query("bvid") bvid: String): ApiResponse<List<RelatedVideoItem>>

    @GET("x/v1/dm/list.so")
    suspend fun getDanmakuXml(@Query("oid") oid: Long): retrofit2.Response<okhttp3.ResponseBody>

    @GET("x/web-interface/index/top/feed/rcmd")
    suspend fun getRecommendFeed(
        @Query("ps") pageSize: Int = 20,
        @Query("fresh_type") freshType: Int = 3,
        @Query("feed_version") feedVersion: String = "V8",
        @Query("homepage_ver") homepageVer: Int = 1
    ): ApiResponse<HomeFeedData>

    @GET("x/web-interface/popular")
    suspend fun getPopular(
        @Query("pn") page: Int = 1,
        @Query("ps") pageSize: Int = 20
    ): ApiResponse<HomePopularData>

    @GET("x/web-interface/ranking/v2")
    suspend fun getRanking(
        @Query("rid") rid: Int = 0,
        @Query("type") type: String = "all"
    ): ApiResponse<HomeRankingData>

    @GET("pgc/web/rank/list")
    suspend fun getPgcRank(
        @Query("day") day: Int = 3,
        @Query("season_type") seasonType: Int
    ): PgcApiResponse<PgcRankResult>

    @GET("pgc/view/web/season")
    suspend fun getPgcSeason(
        @Query("season_id") seasonId: Long
    ): PgcApiResponse<PgcSeasonDetail>

    @GET("pgc/player/web/playurl")
    suspend fun getPgcPlayUrl(
        @Query("ep_id") epId: Long,
        @Query("cid") cid: Long,
        @Query("fnval") fnval: Int = 4048,
        @Query("fnver") fnver: Int = 0,
        @Query("fourk") fourk: Int = 1
    ): PgcApiResponse<PlayUrlInfo>

    // ---- Live ----
    // api.live.bilibili.com needs its own Referer/Cookie handling; see httpClient.

    @GET("https://api.live.bilibili.com/room/v1/area/getRoomList")
    suspend fun getLiveRooms(
        @Query("platform") platform: String = "web",
        @Query("parent_area_id") parentAreaId: Int = 0,
        @Query("area_id") areaId: Int = 0,
        @Query("sort_type") sortType: String = "online",
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 24
    ): ApiResponse<List<LiveRoomItem>>

    @GET("https://api.live.bilibili.com/room/v1/Area/getList")
    suspend fun getLiveAreas(
        @Query("parent_area_id") parentAreaId: Int = 0
    ): ApiResponse<List<LiveAreaGroup>>

    @GET("https://api.live.bilibili.com/room/v1/Room/get_status_info_by_uids")
    suspend fun getLiveStatusByUids(
        @Query("uids[]") uids: List<Long>
    ): ApiResponse<Map<String, LiveStatusInfo>>

    @GET("https://api.live.bilibili.com/room/v1/Room/get_info")
    suspend fun getLiveRoomInfo(
        @Query("room_id") roomId: Long
    ): ApiResponse<LiveRoomInfoData>

    @GET("https://api.live.bilibili.com/live_user/v1/UserInfo/get_anchor_in_room")
    suspend fun getLiveAnchor(
        @Query("roomid") roomId: Long
    ): ApiResponse<LiveAnchorData>

    @GET("https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo")
    suspend fun getLivePlayInfo(
        @Query("room_id") roomId: Long,
        @Query("protocol") protocol: String = "0,1",
        @Query("format") format: String = "0,1,2",
        @Query("codec") codec: String = "0,1",
        @Query("qn") qn: Int = 10000,
        @Query("platform") platform: String = "web",
        @Query("ptype") ptype: Int = 8
    ): ApiResponse<LivePlayInfoData>

    // ---- Dynamic feed ----

    @GET("x/polymer/web-dynamic/v1/feed/all")
    suspend fun getDynamicFeed(
        @Query("type") type: String = "all",
        @Query("page") page: Int = 1,
        @Query("offset") offset: String = "",
        @Query("features") features: String =
            "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote",
        @Query("web_location") webLocation: String = "333.999",
    ): ApiResponse<DynamicFeedData>

    @GET("x/polymer/web-dynamic/v1/feed/space")
    suspend fun getDynamicSpaceFeed(
        @Query("host_mid") hostMid: Long,
        @Query("offset") offset: String = "",
        @Query("features") features: String =
            "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote",
        @Query("web_location") webLocation: String = "333.999",
    ): ApiResponse<DynamicFeedData>

    @GET("https://api.live.bilibili.com/room/v1/Danmu/getConf")
    suspend fun getLiveDanmakuConf(
        @Query("room_id") roomId: Long,
        @Query("platform") platform: String = "pc",
        @Query("player") player: String = "web"
    ): ApiResponse<LiveDanmakuConfData>

    @FormUrlEncoded
    @POST("https://api.live.bilibili.com/msg/send")
    suspend fun sendLiveDanmaku(
        @Field("roomid") roomId: Long,
        @Field("msg") message: String,
        @Field("color") color: Long = 16777215,
        @Field("fontsize") fontSize: Int = 25,
        @Field("mode") mode: Int = 1,
        @Field("bubble") bubble: Int = 0,
        @Field("csrf") csrf: String,
        @Field("csrf_token") csrfToken: String = csrf,
        @Field("rnd") rnd: Long = System.currentTimeMillis() / 1000L
    ): ApiResponse<Any>

    @GET("x/v2/reply/main")
    suspend fun getComments(
        @Query("type") type: Int = 1,
        @Query("oid") oid: Long,
        @Query("mode") mode: Int = 3,
        @Query("ps") pageSize: Int = 20,
        @Query("next") next: Int = 0
    ): ApiResponse<CommentList>

    @GET("x/v2/reply/reply")
    suspend fun getReplies(
        @Query("type") type: Int = 1,
        @Query("oid") oid: Long,
        @Query("root") root: Long,
        @Query("ps") pageSize: Int = 20,
        @Query("pn") pn: Int = 1
    ): ApiResponse<CommentList>

    @FormUrlEncoded
    @POST("x/v2/dm/post")
    suspend fun postDanmaku(
        @Field("type") type: Int = 1,
        @Field("oid") oid: Long,
        @Field("msg") msg: String,
        @Field("bvid") bvid: String,
        @Field("progress") progress: Long,
        @Field("mode") mode: Int = 1,
        @Field("fontsize") fontsize: Int = 25,
        @Field("color") color: Long = 16777215,
        @Field("pool") pool: Int = 0,
        @Field("csrf") csrf: String
    ): ApiResponse<DanmakuPostData>

    @FormUrlEncoded
    @POST("x/v2/dm/recall")
    suspend fun recallDanmaku(
        @Field("dmid") dmid: Long,
        @Field("cid") cid: Long,
        @Field("type") type: Int = 1,
        @Field("csrf") csrf: String
    ): ApiResponse<Any>

    @FormUrlEncoded
    @POST("x/v2/reply/action")
    suspend fun likeComment(
        @Field("type") type: Int = 1,
        @Field("oid") oid: Long,
        @Field("rpid") rpid: Long,
        @Field("action") action: Int,
        @Field("csrf") csrf: String
    ): ApiResponse<Any>

    @FormUrlEncoded
    @POST("x/v2/reply/hate")
    suspend fun hateComment(
        @Field("type") type: Int = 1,
        @Field("oid") oid: Long,
        @Field("rpid") rpid: Long,
        @Field("action") action: Int,
        @Field("csrf") csrf: String
    ): ApiResponse<Any>

    @FormUrlEncoded
    @POST("x/v2/reply/add")
    suspend fun addComment(
        @Field("type") type: Int = 1,
        @Field("oid") oid: Long,
        @Field("message") message: String,
        @Field("plat") plat: Int = 1,
        @Field("root") root: Long?,
        @Field("parent") parent: Long?,
        @Field("csrf") csrf: String
    ): ApiResponse<CommentAddData>

    @FormUrlEncoded
    @POST("x/v2/reply/del")
    suspend fun deleteComment(
        @Field("type") type: Int = 1,
        @Field("oid") oid: Long,
        @Field("rpid") rpid: Long,
        @Field("csrf") csrf: String
    ): ApiResponse<Any>

    @FormUrlEncoded
    @POST("x/v2/history/report")
    suspend fun reportProgress(
        @Field("aid") aid: Long,
        @Field("cid") cid: Long,
        @Field("progress") progress: Long,
        @Field("csrf") csrf: String
    ): ApiResponse<Any>

    @FormUrlEncoded
    @POST("x/v2/history/delete")
    suspend fun deleteHistory(
        @Field("kid") kid: String,
        @Field("csrf") csrf: String
    ): ApiResponse<Any>

    @GET("x/v2/history")
    suspend fun getHistory(
        @Query("pn") page: Int = 1,
        @Query("ps") pageSize: Int = 20
    ): ApiResponse<List<HistoryItem>>

    @GET("x/web-interface/history/search")
    suspend fun searchHistory(
        @Query("keyword") keyword: String,
        @Query("pn") page: Int = 1,
        @Query("business") business: String = "all"
    ): ApiResponse<HistorySearchData>

    @GET("x/space/wbi/arc/search")
    suspend fun getUserVideos(
        @Query("mid") mid: Long,
        @Query("keyword") keyword: String? = null,
        @Query("ps") pageSize: Int = 30,
        @Query("pn") page: Int = 1
    ): ApiResponse<UserSpaceData>

    @GET("x/polymer/web-space/seasons_series_list")
    suspend fun getSpaceSeasons(
        @Query("mid") mid: Long,
        @Query("page_num") pageNum: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("web_location") webLocation: String = "333.1387"
    ): ApiResponse<SpaceSeasonsData>

    @GET("x/polymer/web-space/seasons_archives_list")
    suspend fun getSeasonArchives(
        @Query("mid") mid: Long,
        @Query("season_id") seasonId: Long,
        @Query("page_num") pageNum: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("sort_reverse") sortReverse: Boolean? = null,
        @Query("web_location") webLocation: String = "333.1387"
    ): ApiResponse<SeasonArchiveData>

    @GET("x/player/playurl")
    suspend fun getPlayUrl(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("qn") qn: Int = 80,
        @Query("fnval") fnval: Int = 1,
        @Query("fnver") fnver: Int = 0,
        @Query("fourk") fourk: Int = 1,
        @Query("platform") platform: String = "android"
    ): ApiResponse<PlayUrlInfo>

    @GET("x/player/playurl")
    suspend fun getPlayUrlDash(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("qn") qn: Int = 80,
        @Query("fnval") fnval: Int = 16,
        @Query("fnver") fnver: Int = 0,
        @Query("fourk") fourk: Int = 1,
        @Query("platform") platform: String = "android"
    ): ApiResponse<PlayUrlInfo>

    @GET("x/v3/fav/folder/created/list")
    suspend fun getFavFolders(
        @Query("up_mid") upMid: Long,
        @Query("pn") page: Int = 1,
        @Query("ps") pageSize: Int = 100,
        @Query("type") type: Int? = null,
        @Query("rid") rid: Long? = null
    ): ApiResponse<FavFolderList>

    @FormUrlEncoded
    @POST("x/v3/fav/folder/add")
    suspend fun addFavFolder(
        @Field("title") title: String,
        @Field("intro") intro: String = "",
        @Field("privacy") privacy: Int = 0,
        @Field("csrf") csrf: String
    ): ApiResponse<FavFolderAddData>

    @FormUrlEncoded
    @POST("x/v3/fav/folder/edit")
    suspend fun editFavFolder(
        @Field("media_id") mediaId: Long,
        @Field("title") title: String,
        @Field("intro") intro: String = "",
        @Field("privacy") privacy: Int = 0,
        @Field("csrf") csrf: String
    ): ApiResponse<Any>

    @FormUrlEncoded
    @POST("x/v3/fav/folder/del")
    suspend fun deleteFavFolders(
        @Field("media_ids") mediaIds: String,
        @Field("platform") platform: String = "web",
        @Field("csrf") csrf: String
    ): ApiResponse<Any>

    @GET("x/v3/fav/folder/info")
    suspend fun getFavFolderInfo(
        @Query("media_id") mediaId: Long
    ): ApiResponse<FavFolderInfo?>

    @GET("x/v3/fav/resource/list")
    suspend fun getFavResources(
        @Query("media_id") mediaId: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
        @Query("platform") platform: String = "web",
        @Query("keyword") keyword: String? = null
    ): ApiResponse<FavResourceList>

    @GET("x/v2/fav/video/favoured")
    suspend fun getFavoured(
        @Query("aid") aid: Long
    ): ApiResponse<FavouredData>

    @FormUrlEncoded
    @POST("x/v3/fav/resource/deal")
    suspend fun dealFavResource(
        @Field("rid") rid: Long,
        @Field("type") type: Int = 2,
        @Field("add_media_ids") addMediaIds: String,
        @Field("del_media_ids") delMediaIds: String,
        @Field("csrf") csrf: String
    ): ApiResponse<Any>

    @FormUrlEncoded
    @POST("x/relation/modify")
    suspend fun modifyRelation(
        @Field("fid") fid: Long,
        @Field("act") act: Int,
        @Field("re_src") reSrc: Int = 14,
        @Field("csrf") csrf: String
    ): ApiResponse<Any>

    @GET("x/relation/followings")
    suspend fun getFollowings(
        @Query("vmid") vmid: Long,
        @Query("ps") pageSize: Int = 50,
        @Query("pn") page: Int = 1
    ): ApiResponse<FollowingListData>

    @GET("x/relation/followings/search")
    suspend fun searchFollowings(
        @Query("vmid") vmid: Long,
        @Query("name") name: String,
        @Query("pn") page: Int = 1,
        @Query("ps") pageSize: Int = 20,
        @Query("order") order: String = "desc",
        @Query("order_type") orderType: String = "attention"
    ): ApiResponse<FollowingListData>

    @GET("x/relation/stat")
    suspend fun getRelationStat(@Query("vmid") vmid: Long): ApiResponse<RelationStat>

    @GET("x/relation")
    suspend fun getRelation(@Query("fid") fid: Long): ApiResponse<RelationData>

    @GET("x/article/view")
    suspend fun getArticleView(
        @Query("id") id: Long,
        @Query("gaia_source") gaiaSource: String = "main_web",
        @Query("web_location") webLocation: String = "333.976"
    ): ApiResponse<NoteArticle>

    @GET("x/space/acc/info")
    suspend fun getSpaceAccInfo(@Query("mid") mid: Long): ApiResponse<SpaceAccInfo>

    @GET("x/web-interface/nav")
    suspend fun getNavInfo(): ApiResponse<NavInfo>

    @FormUrlEncoded
    @POST("x/internal/gaia-gateway/ExClimbWuzhi")
    suspend fun activateBuvid(
        @Field("payload") payload: String
    ): ApiResponse<Any>

    @GET("x/web-interface/card")
    suspend fun getUserCard(@Query("mid") mid: Long): ApiResponse<UserCardData>

    @GET("x/player/wbi/v2")
    suspend fun getPlayerInfo(
        @Query("aid") aid: Long,
        @Query("cid") cid: Long
    ): ApiResponse<PlayerInfo>

    companion object {
        private const val BASE_URL = "https://api.bilibili.com/"
        lateinit var buvid3: String
        var loginCookies: String = ""
        var biliJct: String = ""

        private var wbiImgKey: String? = null
        private var wbiSubKey: String? = null
        private val wbiFetchedAt = AtomicLong(0L)
        private val wbiTtlMs = 24 * 60 * 60 * 1000L

        private val MIXIN_KEY_ENC_TAB = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
        )

        fun setLoginCookies(sessdata: String, biliJct: String, dedeUserId: String) {
            loginCookies = "SESSDATA=$sessdata; bili_jct=$biliJct; DedeUserID=$dedeUserId"
            this.biliJct = biliJct
        }

        fun parseSelfMid(): Long? {
            val patterns = listOf("DedeUserID=", "DedeUserID=")
            for (pattern in patterns) {
                try {
                    loginCookies.split(";").firstOrNull { it.trim().startsWith(pattern) }
                        ?.substringAfter(pattern)?.trim()?.toLongOrNull()?.let { return it }
                } catch (_: Exception) {}
            }
            return null
        }

        private var buvidActivated = false

        fun activateBuvidIfNeeded(scope: CoroutineScope) {
            if (buvidActivated || buvid3.isBlank()) return
            buvidActivated = true
            scope.launch(Dispatchers.IO) {
                try {
                    val randBytes = List(50) { ('0'..'9').random() }
                    val payload = org.json.JSONObject().apply {
                        put("3064", 1)
                        put("39c8", "333.1387.fp.risk")
                        put("3c43", org.json.JSONObject().apply {
                            put("adca", "Linux")
                            put("bfe9", randBytes.joinToString("").takeLast(50))
                        })
                    }
                    val response = create().activateBuvid(payload.toString())
                    if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "buvid activated: code=${response.code}")
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(BilibiliApp.TAG, "buvid activation failed", e)
                }
            }
        }

        private fun fetchWbiKeys(): Pair<String, String>? {
            return try {
                val client = OkHttpClient.Builder()
                    .protocols(listOf(Protocol.HTTP_1_1))
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val orig = chain.request()
                        val builder = orig.newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("Referer", "https://www.bilibili.com/")
                            .header("Cookie", "buvid3=$buvid3${if (loginCookies.isNotEmpty()) "; $loginCookies" else ""}")
                        chain.proceed(builder.build())
                    }
                    .build()
                val request = okhttp3.Request.Builder()
                    .url("${BASE_URL}x/web-interface/nav")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                if (body != null) {
                    val navResp = com.google.gson.Gson().fromJson(body, java.util.Map::class.java)
                    val data = navResp["data"] as? java.util.Map<*, *>
                    val wbiImg = data?.get("wbi_img") as? java.util.Map<*, *>
                    val imgUrl = wbiImg?.get("img_url") as? String
                    val subUrl = wbiImg?.get("sub_url") as? String
                    if (imgUrl != null && subUrl != null) {
                        val imgKey = imgUrl.substringAfterLast("/").removeSuffix(".png")
                        val subKey = subUrl.substringAfterLast("/").removeSuffix(".png")
                        Pair(imgKey, subKey)
                    } else {
                        if (BuildConfig.DEBUG) Log.w(BilibiliApp.TAG, "WBI keys not found in nav response: $body")
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(BilibiliApp.TAG, "fetchWbiKeys failed", e)
                null
            }
        }

        private fun getWbiKeys(): Pair<String, String>? {
            val now = System.currentTimeMillis()
            if (now - wbiFetchedAt.get() > wbiTtlMs || wbiImgKey == null || wbiSubKey == null) {
                val keys = fetchWbiKeys()
                if (keys != null) {
                    wbiImgKey = keys.first
                    wbiSubKey = keys.second
                    wbiFetchedAt.set(now)
                    if (BuildConfig.DEBUG) Log.d(BilibiliApp.TAG, "WBI keys refreshed")
                }
            }
            return if (wbiImgKey != null && wbiSubKey != null) {
                Pair(wbiImgKey!!, wbiSubKey!!)
            } else {
                null
            }
        }

        private fun encodeUriComponent(s: String): String {
            val sb = StringBuilder()
            for (c in s) {
                when (c) {
                    in 'A'..'Z', in 'a'..'z', in '0'..'9', '-', '_', '.', '~' -> sb.append(c)
                    ' ' -> sb.append("%20")
                    else -> {
                        for (b in c.toString().encodeToByteArray()) {
                            sb.append('%')
                            sb.append((b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
                        }
                    }
                }
            }
            return sb.toString()
        }

        private fun wbiSign(params: Map<String, String>): String? {
            val keys = getWbiKeys() ?: return null
            val rawKey = keys.first + keys.second
            val mixinKey = MIXIN_KEY_ENC_TAB.map { rawKey[it] }.joinToString("").take(32)

            val mutableParams = params.toMutableMap()
            val wts = (System.currentTimeMillis() / 1000L).toString()
            mutableParams["wts"] = wts

            val sortedKeys = mutableParams.keys.sorted()
            val queryParts = sortedKeys.joinToString("&") { key ->
                val encodedKey = encodeUriComponent(key)
                var encodedValue = encodeUriComponent(mutableParams[key] ?: "")
                encodedValue = encodedValue.replace("!", "")
                    .replace("'", "")
                    .replace("(", "")
                    .replace(")", "")
                    .replace("*", "")
                "$encodedKey=$encodedValue"
            }

            val signStr = queryParts + mixinKey
            val md5 = MessageDigest.getInstance("MD5")
            val wRid = md5.digest(signStr.encodeToByteArray()).joinToString("") {
                (it.toInt() and 0xFF).toString(16).padStart(2, '0')
            }
            return "$queryParts&w_rid=$wRid"
        }

        private class RateLimitInterceptor : Interceptor {
            @Volatile private var lastRequestAt = 0L
            override fun intercept(chain: Interceptor.Chain): Response {
                val url = chain.request().url.toString()
                val isApi = url.contains("api.bilibili.com") || url.contains("passport.bilibili.com")
                if (isApi) {
                    val elapsed = System.currentTimeMillis() - lastRequestAt
                    val wait = MIN_API_INTERVAL_MS - elapsed
                    if (wait > 0) {
                        try { Thread.sleep(wait) } catch (_: InterruptedException) {}
                    }
                }
                val response = chain.proceed(chain.request())
                if (isApi) lastRequestAt = System.currentTimeMillis()
                return response
            }
            companion object {
                private const val MIN_API_INTERVAL_MS = 300L
            }
        }

        private class RetryInterceptor : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                var response = chain.proceed(chain.request())
                var retryCount = 0
                while (retryCount < MAX_RETRIES && (response.code == 429 || response.code == 412)) {
                    response.close()
                    val retryAfter = response.header("Retry-After")?.toLongOrNull()
                    val backoff = retryAfter ?: ((1L shl retryCount) * if (response.code == 412) 2000L else 1000L)
                    if (BuildConfig.DEBUG) Log.w(BilibiliApp.TAG, "HTTP ${response.code} retry ${retryCount + 1}/$MAX_RETRIES, wait ${backoff}ms")
                    try { Thread.sleep(backoff) } catch (_: InterruptedException) {}
                    response = chain.proceed(chain.request())
                    retryCount++
                }
                return response
            }
            companion object {
                private const val MAX_RETRIES = 3
            }
        }

        val httpClient: OkHttpClient by lazy {
            val logging = HttpLoggingInterceptor().apply {
                // Never print Cookie / Set-Cookie: loginCookies carries SESSDATA.
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                        else HttpLoggingInterceptor.Level.NONE
                redactHeader("Cookie")
                redactHeader("Set-Cookie")
                redactHeader("Authorization")
            }

            OkHttpClient.Builder()
                .protocols(listOf(Protocol.HTTP_1_1))
                .connectionSpecs(listOf(
                    ConnectionSpec.Builder(ConnectionSpec.RESTRICTED_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2)
                        .supportsTlsExtensions(true)
                        .build()
                ))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val orig = chain.request()
                    val url = orig.url.toString()
                    val isLiveApi = url.contains("api.live.bilibili.com")
                    val isCdn = !url.contains("api.bilibili.com") &&
                        !url.contains("passport.bilibili.com") &&
                        !isLiveApi
                    val isLiveCdn = isCdn && url.contains("bilivideo.com") && url.contains("/live")
                    val isSpaceApi = !isCdn && ((url.contains("/x/space/") && !url.contains("acc/info")) || url.contains("/web-space/"))
                    val spaceOrigin = "https://space.bilibili.com"
                    val liveOrigin = "https://live.bilibili.com"
                    val apiOrigin = "https://www.bilibili.com"
                    val builder = orig.newBuilder()
                        .header(
                            "Referer",
                            when {
                                isLiveApi || isLiveCdn -> "$liveOrigin/"
                                isSpaceApi -> "$spaceOrigin/"
                                else -> "$apiOrigin/"
                            },
                        )
                    if (isCdn) {
                        builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    } else if (isSpaceApi) {
                        // Space APIs: Safari desktop UA (like PiliPlus), no sec-ch-ua headers
                        builder.header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.2 Safari/605.1.15")
                            .header("Origin", spaceOrigin)
                            .header("Cookie", buildString {
                                append("buvid3=$buvid3; buvid4=$buvid3")
                                if (loginCookies.isNotEmpty()) {
                                    append("; $loginCookies")
                                }
                            })
                            .header("Accept", "application/json, text/plain, */*")
                            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    } else if (isLiveApi) {
                        // Live endpoints reject anonymous requests with -352.
                        // Send the generated buvid plus the logged-in cookies,
                        // with a desktop web UA and the live origin.
                        builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("Origin", liveOrigin)
                            .header("Cookie", buildString {
                                append("buvid3=$buvid3; buvid4=$buvid3")
                                if (loginCookies.isNotEmpty()) {
                                    append("; $loginCookies")
                                }
                            })
                            .header("Accept", "application/json, text/plain, */*")
                            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    } else {
                        // 楼中楼翻页（pn>1）如果带上 App 生成的 buvid3/buvid4，
                        // 服务端会返回 code=0 但 data 为空页；阅读评论不需要登录，
                        // 因此分页请求跳过 Cookie。
                        val replyPage = orig.url.encodedPath.endsWith("/x/v2/reply/reply")
                        val pn = orig.url.queryParameter("pn")?.toIntOrNull() ?: 1
                        val skipCookie = replyPage && pn > 1

                        builder.header("User-Agent", "Mozilla/5.0 (Linux; Android ${Build.VERSION.SDK_INT}; ${Build.MANUFACTURER} ${Build.MODEL}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                            .header("Origin", apiOrigin)
                        if (!skipCookie) {
                            builder.header("Cookie", buildString {
                                append("buvid3=$buvid3; buvid4=$buvid3")
                                if (loginCookies.isNotEmpty()) {
                                    append("; $loginCookies")
                                }
                            })
                        }
                        builder.header("Accept", "application/json, text/plain, */*")
                            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    }
                    // WBI signing for endpoints that require w_rid/wts
                    // Note: seasons_archives_list does NOT need WBI (verified via PiliPlus)
                    if (!isCdn && (url.contains("/wbi/") || url.contains("search/type") || url.contains("seasons_series_list") || url.contains("article/view"))) {
                        val httpUrl = url.toHttpUrlOrNull()
                        if (httpUrl != null) {
                            val params = mutableMapOf<String, String>()
                            for (i in 0 until httpUrl.querySize) {
                                httpUrl.queryParameterName(i)?.let { name ->
                                    httpUrl.queryParameterValue(i)?.let { value ->
                                        params[name] = value
                                    }
                                }
                            }
                            val wbiResult = wbiSign(params)
                            if (wbiResult != null) {
                                val newUrl = httpUrl.newBuilder()
                                    .encodedQuery(wbiResult)
                                    .build()
                                builder.url(newUrl)
                            }
                        }
                    }
                    chain.proceed(builder.build())
                }
                .addInterceptor(RateLimitInterceptor())
                .addInterceptor(RetryInterceptor())
                .addInterceptor(logging)
                .build()
        }

        fun create(): BilibiliApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(httpClient)
                .addConverterFactory(
                    GsonConverterFactory.create(
                        com.google.gson.GsonBuilder()
                            .registerTypeAdapter(UserVideoItem::class.java, UserVideoItem.deserializer)
                            .create()
                    )
                )
                .build()
                .create(BilibiliApi::class.java)
        }
    }
}
