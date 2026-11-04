package cn.xfangfang.biliduo.data.model

import com.google.gson.annotations.SerializedName

/** One parent area from `room/v1/Area/getList`; [subAreas] drives the second chip row. */
data class LiveAreaGroup(
    val id: Int = 0,
    val name: String = "",
    @SerializedName("list") val subAreas: List<LiveSubArea>? = null,
)

data class LiveSubArea(
    val id: String = "",
    val name: String = "",
    @SerializedName("parent_id") val parentId: String = "",
)

/** One room from `room/v1/area/getRoomList`. */
data class LiveRoomItem(
    @SerializedName("roomid") val roomId: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    val uname: String = "",
    val online: Long = 0,
    @SerializedName("user_cover") val userCover: String = "",
    @SerializedName("system_cover") val systemCover: String = "",
    val cover: String = "",
    val face: String = "",
    @SerializedName("area_name") val areaName: String = "",
    @SerializedName("parent_name") val parentName: String = "",
    @SerializedName("area_v2_id") val areaV2Id: Long = 0,
    @SerializedName("area_v2_parent_id") val areaV2ParentId: Long = 0,
) {
    val displayCover: String
        get() = userCover.ifBlank { cover.ifBlank { systemCover } }
}

/** One item from `room/v1/Room/get_status_info_by_uids`. */
data class LiveStatusInfo(
    @SerializedName("room_id") val roomId: Long = 0,
    val uid: Long = 0,
    val uname: String = "",
    val face: String = "",
    val title: String = "",
    val online: Long = 0,
    @SerializedName("live_status") val liveStatus: Int = 0,
    @SerializedName("area_v2_name") val areaName: String = "",
    @SerializedName("area_v2_parent_name") val parentName: String = "",
)

/** A followed UP who is currently live, shown as an avatar row on the live tab. */
data class LiveFollowedAnchor(
    val uid: Long,
    val uname: String,
    val face: String,
    val roomId: Long,
    val title: String = "",
    val areaName: String = "",
    val online: Long = 0,
)

data class LiveRoomInfoData(
    @SerializedName("room_id") val roomId: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    val description: String = "",
    @SerializedName("user_cover") val userCover: String = "",
    val keyframe: String = "",
    val background: String = "",
    @SerializedName("live_status") val liveStatus: Int = 0,
    val online: Long = 0,
    val attention: Long = 0,
    @SerializedName("area_id") val areaId: Long = 0,
    @SerializedName("area_name") val areaName: String = "",
    @SerializedName("parent_area_id") val parentAreaId: Long = 0,
    @SerializedName("parent_area_name") val parentAreaName: String = "",
    @SerializedName("live_time") val liveTime: String = "",
)

data class LiveAnchorData(
    val info: LiveAnchorInfo? = null,
)

data class LiveAnchorInfo(
    val uid: Long = 0,
    val uname: String = "",
    val face: String = "",
    @SerializedName("official_verify") val officialVerify: LiveOfficialVerify? = null,
)

data class LiveOfficialVerify(
    val type: Int = 0,
    val desc: String = "",
)

/** Room metadata merged from `Room/get_info` and `get_anchor_in_room`. */
data class LiveRoomDetail(
    val roomId: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    val description: String = "",
    val cover: String = "",
    val keyframe: String = "",
    val liveStatus: Int = 0,
    val online: Long = 0,
    val attention: Long = 0,
    val areaName: String = "",
    val parentAreaName: String = "",
    val liveTime: String = "",
    val anchorName: String = "",
    val anchorFace: String = "",
    val anchorOfficial: String = "",
) {
    val isLive: Boolean get() = liveStatus == 1

    /** Prefer the live keyframe; fall back to the room cover when offline. */
    val displayCover: String
        get() = if (isLive) keyframe.ifBlank { cover } else cover.ifBlank { keyframe }

    val displayArea: String
        get() = listOf(parentAreaName, areaName)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")
}

data class LivePlayInfoData(
    @SerializedName("room_id") val roomId: Long = 0,
    @SerializedName("live_status") val liveStatus: Int = 0,
    @SerializedName("playurl_info") val playurlInfo: LivePlayurlInfo? = null,
)

data class LivePlayurlInfo(
    val playurl: LivePlayurl? = null,
)

data class LivePlayurl(
    @SerializedName("g_qn_desc") val qualityDescriptions: List<LiveQualityDesc>? = null,
    val stream: List<LiveStreamInfo>? = null,
)

data class LiveQualityDesc(
    val qn: Int = 0,
    val desc: String = "",
)

data class LiveStreamInfo(
    @SerializedName("protocol_name") val protocolName: String = "",
    val format: List<LiveFormatInfo>? = null,
)

data class LiveFormatInfo(
    @SerializedName("format_name") val formatName: String = "",
    val codec: List<LiveCodecInfo>? = null,
)

data class LiveCodecInfo(
    @SerializedName("codec_name") val codecName: String = "",
    @SerializedName("current_qn") val currentQn: Int = 0,
    @SerializedName("accept_qn") val acceptQn: List<Int>? = null,
    @SerializedName("base_url") val baseUrl: String = "",
    @SerializedName("url_info") val urlInfo: List<LiveUrlInfo>? = null,
)

data class LiveUrlInfo(
    val host: String = "",
    val extra: String = "",
)

/**
 * The single playable URL selected from a live playurl response.
 *
 * Bilibili returns multiple protocol/format/codec combinations. HLS is preferred
 * because it reconnects cleanly after a network blip; FLV is the fallback.
 */
data class LivePlayResult(
    val url: String = "",
    val isHls: Boolean = true,
    val qualities: List<QualityOption> = emptyList(),
    val currentQuality: QualityOption? = null,
    val liveStatus: Int = 0,
)

/**
 * Pick the first usable HLS stream (ts, then fmp4, AVC before HEVC), falling
 * back to the FLV stream. Bilibili's ts playlist has a more stable live window
 * than fmp4, so it is preferred to avoid BehindLiveWindow churn on quality
 * changes. Pure so it can be unit tested without networking.
 */
fun LivePlayurl?.selectLivePlayResult(liveStatus: Int = 1): LivePlayResult? {
    val source = this ?: return null
    val descriptions = source.qualityDescriptions.orEmpty()
        .associate { it.qn to it.desc }

    fun streamFor(protocol: String, formatOrder: List<String>): Pair<LiveCodecInfo, String>? {
        for (formatName in formatOrder) {
            for (stream in source.stream.orEmpty()) {
                if (!stream.protocolName.equals(protocol, ignoreCase = true)) continue
                for (format in stream.format.orEmpty()) {
                    if (!format.formatName.equals(formatName, ignoreCase = true)) continue
                    val codec = format.codec.orEmpty()
                        .sortedBy { if (it.codecName.equals("avc", ignoreCase = true)) 0 else 1 }
                        .firstOrNull { it.baseUrl.isNotBlank() && it.urlInfo.orEmpty().isNotEmpty() }
                    if (codec != null) return codec to formatName
                }
            }
        }
        return null
    }

    val selected = streamFor("http_hls", listOf("ts", "fmp4"))
        ?: streamFor("http_stream", listOf("flv"))
    if (selected == null) return null
    val (codec, _) = selected
    val urlInfo = codec.urlInfo.orEmpty().firstOrNull() ?: return null
    val url = urlInfo.host + codec.baseUrl + urlInfo.extra
    if (url.isBlank()) return null

    val qualities = codec.acceptQn.orEmpty()
        .filter { it != 30000 }
        .distinct()
        .sortedDescending()
        .map { qn ->
            QualityOption(
                quality = qn,
                description = descriptions[qn]?.takeIf { it.isNotBlank() }
                    ?: qualityName(qn),
            )
        }
    val current = QualityOption(
        quality = codec.currentQn,
        description = descriptions[codec.currentQn]?.takeIf { it.isNotBlank() }
            ?: qualityName(codec.currentQn),
    )
    return LivePlayResult(
        url = url,
        isHls = url.contains(".m3u8", ignoreCase = true),
        qualities = if (qualities.any { it.quality == current.quality }) {
            qualities
        } else {
            (qualities + current).sortedByDescending { it.quality }
        },
        currentQuality = current,
        liveStatus = liveStatus,
    )
}

private fun qualityName(qn: Int): String = when (qn) {
    30000 -> "杜比"
    20000 -> "4K"
    15000 -> "2K"
    10000 -> "原画"
    400 -> "蓝光"
    250 -> "高清"
    150 -> "流畅"
    else -> "清晰度 $qn"
}
