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
    val badge: String = "",
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
