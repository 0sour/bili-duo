package cn.xfangfang.biliduo.data.model

import com.google.gson.annotations.SerializedName

enum class HomeTab(
    val label: String,
    val isPgc: Boolean = false,
    val isLive: Boolean = false,
) {
    LIVE("直播", isLive = true),
    RECOMMEND("推荐"),
    POPULAR("热门"),
    RANKING("排行"),
    ANIME("番剧", isPgc = true),
    MOVIE("影视", isPgc = true),
}

/** `x/web-interface/index/top/feed/rcmd` response. */
data class HomeFeedData(
    @SerializedName("item") val items: List<HomeVideoItem>? = null
)

/** `x/web-interface/popular` response. */
data class HomePopularData(
    val list: List<HomeVideoItem>? = null,
    @SerializedName("no_more") val noMore: Boolean? = null
)

/** `x/web-interface/ranking/v2` response. */
data class HomeRankingData(
    val list: List<HomeVideoItem>? = null
)

/**
 * Shared shape for the recommend feed and popular list. The recommend endpoint
 * uses `id`, popular uses `aid`; both expose bvid/cid/cover/title/owner/stat.
 */
data class HomeVideoItem(
    val bvid: String = "",
    val cid: Long = 0,
    val pic: String = "",
    val title: String = "",
    val duration: Long = 0,
    val pubdate: Long = 0,
    val goto: String = "",
    val owner: HomeVideoOwner? = null,
    val stat: HomeVideoStat? = null,
    @SerializedName("rcmd_reason") val reason: HomeRcmdReason? = null
)

data class HomeVideoOwner(
    val mid: Long = 0,
    val name: String = "",
    val face: String = ""
)

data class HomeVideoStat(
    val view: Long = 0,
    val like: Long = 0,
    val danmaku: Long = 0
)

data class HomeRcmdReason(
    val content: String = ""
)
