package cn.xfangfang.biliduo.data.model

import com.google.gson.annotations.SerializedName

/**
 * PGC endpoints wrap their payload in `result` instead of `data`, unlike the
 * UGC [ApiResponse]. Keep a separate wrapper so both can coexist.
 */
data class PgcApiResponse<T>(
    val code: Int,
    val message: String,
    val result: T?,
    val ttl: Int = 0,
)

/** `pgc/web/rank/list` result. */
data class PgcRankResult(
    val list: List<PgcSeasonItem>? = null,
)

data class PgcSeasonItem(
    @SerializedName("season_id") val seasonId: Long = 0,
    val title: String = "",
    val cover: String = "",
    val rating: String = "",
    val badge: String = "",
    @SerializedName("index_show") val indexShow: String = "",
)

/** `pgc/view/web/season` result. */
data class PgcSeasonDetail(
    @SerializedName("season_id") val seasonId: Long = 0,
    val title: String = "",
    val cover: String = "",
    val evaluate: String = "",
    val type: Int = 0,
    val episodes: List<PgcEpisode>? = null,
    val stat: PgcStat? = null,
    @SerializedName("up_info") val upInfo: PgcUpInfo? = null,
)

data class PgcEpisode(
    val aid: Long = 0,
    @SerializedName("ep_id") val epId: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    @SerializedName("long_title") val longTitle: String = "",
    val duration: Long = 0,
    /** Absent for free episodes; Gson leaves it null rather than "". */
    val badge: String? = null,
    /** Episode opening/ending markers; ranges are 0/0 when unset. */
    val skip: PgcSkip? = null,
)

data class PgcSkip(
    val op: PgcSkipRange? = null,
    val ed: PgcSkipRange? = null,
)

data class PgcSkipRange(
    val start: Long = 0,
    val end: Long = 0,
)

data class PgcStat(
    val views: Long = 0,
    val danmakus: Long = 0,
    val likes: Long = 0,
    val favorites: Long = 0,
    val reply: Long = 0,
    @SerializedName("follow_text") val followText: String = "",
)

data class PgcUpInfo(
    val mid: Long = 0,
    val uname: String = "",
    val avatar: String = "",
)

/** `pgc/view/web/season/user/status` result. */
data class PgcFollowStatusData(
    val status: Int = 0,
    val follow: Int = 0,
    @SerializedName("follow_status") val followStatus: Int = 0,
)

/** `x/space/bangumi/follow/list` result. */
data class PgcFollowListData(
    val list: List<PgcFollowItem>? = null,
    val total: Int = 0,
    val pn: Int = 0,
    val ps: Int = 0,
)

data class PgcFollowItem(
    @SerializedName("season_id") val seasonId: Long = 0,
    val title: String? = null,
    val cover: String? = null,
    /** e.g. "看到第 3 话"; "-" when nothing watched. */
    val progress: String? = null,
    @SerializedName("new_ep") val newEp: PgcNewEp? = null,
    @SerializedName("total_count") val totalCount: Int = 0,
    @SerializedName("follow_status") val followStatus: Int = 0,
    @SerializedName("season_type_name") val seasonTypeName: String? = null,
    val badge: String? = null,
    val rating: PgcFollowRating? = null,
)

data class PgcNewEp(
    @SerializedName("index_show") val indexShow: String? = null,
)

data class PgcFollowRating(
    val score: Float = 0f,
)

/**
 * `pgc/page/index/condition` and `pgc/page/index/result` use the older
 * `data` wrapper (not PGC's usual `result`), so they get their own response.
 */
data class PgcIndexResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null,
    val ttl: Int = 0,
)

data class PgcIndexConditionData(
    val filter: List<PgcIndexFilter>? = null,
    val order: List<PgcIndexOrder>? = null,
)

data class PgcIndexFilter(
    /** Query parameter name, e.g. `area`, `style_id`, `season_status`. */
    val field: String = "",
    val name: String = "",
    val values: List<PgcIndexFilterValue>? = null,
)

data class PgcIndexFilterValue(
    val keyword: String = "",
    val name: String = "",
)

data class PgcIndexOrder(
    /** Sort id passed back as `order`. */
    val field: String = "0",
    val name: String = "",
    val sort: String = "0",
)

data class PgcIndexResultData(
    @SerializedName("has_next") val hasNext: Int = 0,
    val list: List<PgcIndexItem>? = null,
    val num: Int = 0,
    val size: Int = 0,
    val total: Int = 0,
)

data class PgcIndexItem(
    @SerializedName("season_id") val seasonId: Long = 0,
    val title: String? = null,
    val cover: String? = null,
    val badge: String? = null,
    @SerializedName("index_show") val indexShow: String? = null,
    /** e.g. "1813.5万追番" / "1.2亿播放". */
    val order: String? = null,
    @SerializedName("is_finish") val isFinish: Int = 0,
    @SerializedName("season_type") val seasonType: Int = 0,
    @SerializedName("season_type_name") val seasonTypeName: String? = null,
    val rating: PgcIndexRating? = null,
)

data class PgcIndexRating(
    val score: Float = 0f,
)
