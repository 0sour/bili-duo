package cn.xfangfang.biliduo.data.model

import com.google.gson.annotations.SerializedName

data class WatchLaterData(
    val count: Int = 0,
    val list: List<WatchLaterItem>? = null,
)

/**
 * Gson does not run Kotlin default values, so anything that can be absent in
 * JSON stays nullable; numeric fields fall back to 0.
 */
data class WatchLaterItem(
    val aid: Long = 0,
    val bvid: String? = null,
    val title: String? = null,
    val pic: String? = null,
    val duration: Long = 0,
    /** Saved position in seconds; 0 when the video was never opened. */
    val progress: Long = 0,
    val cid: Long = 0,
    val videos: Int = 0,
    val owner: VideoOwner? = null,
    val stat: VideoStat? = null,
    @SerializedName("add_at") val addAt: Long = 0,
)
