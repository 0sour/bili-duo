package cn.xfangfang.biliduo.data.model

import com.google.gson.annotations.SerializedName

/** Filter exposed as chips on the 动态 page. */
enum class DynamicFilter(val apiType: String, val label: String) {
    ALL("all", "全部"),
    VIDEO("video", "视频"),
    IMAGE("draw", "图文"),
}

data class DynamicAuthorUi(
    val mid: Long,
    val name: String,
    val face: String = "",
)

/**
 * Response of `x/polymer/web-dynamic/v1/feed/all`.
 *
 * Gson models stay close to the wire format; [toCardUi] flattens the nested
 * modules into the small shape the list actually renders.
 */
data class DynamicFeedData(
    @SerializedName("items") val items: List<DynamicFeedItem>? = null,
    @SerializedName("offset") val offset: String? = null,
    @SerializedName("has_more") val hasMore: Boolean = false,
)

data class DynamicFeedItem(
    @SerializedName("id_str") val idStr: String = "",
    @SerializedName("id") val id: Long = 0,
    @SerializedName("type") val type: String = "",
    @SerializedName("modules") val modules: DynamicModules? = null,
    @SerializedName("orig") val orig: DynamicFeedItem? = null,
)

data class DynamicModules(
    @SerializedName("module_author") val author: DynamicAuthor? = null,
    @SerializedName("module_dynamic") val dynamic: DynamicContent? = null,
    @SerializedName("module_stat") val stat: DynamicStat? = null,
)

data class DynamicAuthor(
    @SerializedName("mid") val mid: Long = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("face") val face: String = "",
    @SerializedName("pub_time") val pubTime: String = "",
    @SerializedName("pub_ts") val pubTs: Long = 0,
    @SerializedName("type") val type: String = "",
)

data class DynamicContent(
    @SerializedName("desc") val desc: DynamicDesc? = null,
    @SerializedName("major") val major: DynamicMajor? = null,
)

data class DynamicDesc(
    @SerializedName("text") val text: String = "",
)

data class DynamicMajor(
    @SerializedName("type") val type: String = "",
    @SerializedName("draw") val draw: DynamicDraw? = null,
    @SerializedName("archive") val archive: DynamicArchive? = null,
    @SerializedName("opus") val opus: DynamicOpus? = null,
    @SerializedName("article") val article: DynamicArticle? = null,
    @SerializedName("pgc") val pgc: DynamicPgc? = null,
)

data class DynamicDraw(
    @SerializedName("items") val items: List<DynamicDrawItem>? = null,
)

data class DynamicDrawItem(
    @SerializedName("src") val src: String = "",
    @SerializedName("width") val width: Int = 0,
    @SerializedName("height") val height: Int = 0,
)

data class DynamicArchive(
    @SerializedName("aid") val aid: Long = 0,
    @SerializedName("bvid") val bvid: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("cover") val cover: String = "",
    @SerializedName("desc") val desc: String = "",
    @SerializedName("duration_text") val durationText: String = "",
    @SerializedName("stat") val stat: DynamicArchiveStat? = null,
)

data class DynamicArchiveStat(
    @SerializedName("play") val play: String? = null,
    @SerializedName("danmaku") val danmaku: String? = null,
)

data class DynamicOpus(
    @SerializedName("title") val title: String? = null,
    @SerializedName("summary") val summary: DynamicDesc? = null,
    @SerializedName("pics") val pics: List<DynamicPic>? = null,
)

data class DynamicPic(
    @SerializedName("url") val url: String? = null,
    @SerializedName("src") val src: String? = null,
    @SerializedName("width") val width: Int = 0,
    @SerializedName("height") val height: Int = 0,
)

data class DynamicArticle(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("desc") val desc: String = "",
    @SerializedName("covers") val covers: List<String>? = null,
)

data class DynamicPgc(
    @SerializedName("season_id") val seasonId: Long = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("cover") val cover: String = "",
)

data class DynamicStat(
    @SerializedName("comment") val comment: DynamicCount? = null,
    @SerializedName("forward") val forward: DynamicCount? = null,
    @SerializedName("like") val like: DynamicCount? = null,
)

data class DynamicCount(
    @SerializedName("count") val count: Int = 0,
    /** Present on the like stat: whether the current account already liked it. */
    val status: Boolean = false,
    val forbidden: Boolean = false,
)

data class DynamicLikeRequest(
    @SerializedName("dyn_id_str") val dynIdStr: String,
    /** 1 = like, 2 = cancel. */
    val up: Int,
)

data class DynamicCardUi(
    val id: String,
    val authorMid: Long = 0,
    val authorName: String,
    val authorFace: String,
    val pubTime: String,
    val kindLabel: String,
    val text: String,
    val images: List<String>,
    val video: DynamicVideoUi?,
    val forward: DynamicForwardUi?,
    val commentCount: Int,
    val forwardCount: Int,
    val likeCount: Int,
    val liked: Boolean = false,
) {
    val playbackBvid: String?
        get() = video?.bvid?.takeIf { it.isNotBlank() }
            ?: forward?.video?.bvid?.takeIf { it.isNotBlank() }
}

data class DynamicVideoUi(
    val bvid: String,
    val title: String,
    val cover: String,
    val duration: String,
    val play: String,
    val description: String,
)

data class DynamicForwardUi(
    val authorName: String,
    val authorFace: String,
    val text: String,
    val image: String?,
    val video: DynamicVideoUi?,
)

fun DynamicFeedItem.toCardUi(): DynamicCardUi? {
    val module = modules ?: return null
    val author = module.author
    val content = module.dynamic
    val major = content?.major
    val text = content?.desc?.text?.trim().orEmpty()
        .ifBlank { major?.opus?.summary?.text?.trim().orEmpty() }
        .ifBlank { major?.article?.desc?.trim().orEmpty() }
    val images = major.images()
    val video = major?.archive?.toVideoUi()
    val forward = orig?.toForwardUi()
    val stat = module.stat

    return DynamicCardUi(
        id = idStr.ifBlank { id.toString() },
        authorMid = author?.mid ?: 0L,
        authorName = author?.name.orEmpty().ifBlank { "未知用户" },
        authorFace = author?.face.orEmpty(),
        pubTime = author?.pubTime.orEmpty(),
        kindLabel = kindLabel(type, major?.type.orEmpty(), video != null, images.isNotEmpty()),
        text = text,
        images = images,
        video = video,
        forward = forward,
        commentCount = stat?.comment?.count ?: 0,
        forwardCount = stat?.forward?.count ?: 0,
        likeCount = stat?.like?.count ?: 0,
        liked = stat?.like?.status ?: false,
    )
}

private fun DynamicFeedItem.toForwardUi(): DynamicForwardUi? {
    val module = modules
    val content = module?.dynamic
    val major = content?.major
    val images = major.images()
    val video = major?.archive?.toVideoUi()
    val text = content?.desc?.text?.trim().orEmpty()
        .ifBlank { major?.opus?.summary?.text?.trim().orEmpty() }
        .ifBlank { major?.article?.desc?.trim().orEmpty() }
    return DynamicForwardUi(
        authorName = module?.author?.name.orEmpty().ifBlank { "原动态" },
        authorFace = module?.author?.face.orEmpty(),
        text = text.ifBlank { "原动态已删除" },
        image = images.firstOrNull(),
        video = video,
    )
}

private fun DynamicMajor?.images(): List<String> {
    if (this == null) return emptyList()
    val draw = draw?.items.orEmpty().map { it.src }
    val opus = opus?.pics.orEmpty().mapNotNull { it.url ?: it.src }
    val covers = article?.covers.orEmpty()
    val pgcCover = listOfNotNull(pgc?.cover)
    return (draw + opus + covers + pgcCover)
        .filter { it.isNotBlank() }
        .distinct()
}

private fun DynamicArchive.toVideoUi(): DynamicVideoUi? {
    if (bvid.isBlank()) return null
    return DynamicVideoUi(
        bvid = bvid,
        title = title,
        cover = cover,
        duration = durationText,
        play = stat?.play.orEmpty(),
        description = desc,
    )
}

private fun kindLabel(
    type: String,
    majorType: String,
    hasVideo: Boolean,
    hasImages: Boolean,
): String = when {
    type == "DYNAMIC_TYPE_FORWARD" -> "转发"
    hasVideo || majorType == "MAJOR_TYPE_ARCHIVE" -> "视频"
    majorType == "MAJOR_TYPE_ARTICLE" || type == "DYNAMIC_TYPE_ARTICLE" -> "专栏"
    majorType == "MAJOR_TYPE_PGC" || type == "DYNAMIC_TYPE_PGC" -> "番剧"
    type == "DYNAMIC_TYPE_LIVE_RCMD" -> "直播"
    hasImages || majorType == "MAJOR_TYPE_DRAW" -> "图文"
    else -> "动态"
}
