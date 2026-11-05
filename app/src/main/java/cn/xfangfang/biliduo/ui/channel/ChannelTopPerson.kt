package cn.xfangfang.biliduo.ui.channel

import cn.xfangfang.biliduo.data.model.DynamicCardUi
import cn.xfangfang.biliduo.data.model.SpaceAccInfo
import cn.xfangfang.biliduo.data.model.UserCardData
import cn.xfangfang.biliduo.data.model.UserVideoItem
import cn.xfangfang.biliduo.ui.top.TopGlyph
import cn.xfangfang.biliduo.ui.top.TopPersonMoment
import cn.xfangfang.biliduo.ui.top.TopPersonStat
import cn.xfangfang.biliduo.ui.top.TopPersonUi
import cn.xfangfang.biliduo.ui.top.TopPersonVideo
import cn.xfangfang.biliduo.util.formatCount

/**
 * Read-only top-screen payload for the UP space. Kept free of Compose/Android
 * types so the mapping can be unit tested without a device.
 */
internal fun buildChannelPersonUi(
    mid: Long,
    info: SpaceAccInfo?,
    card: UserCardData?,
    videos: List<UserVideoItem>,
    moments: List<DynamicCardUi>,
): TopPersonUi {
    val videoCount = card?.archive_count?.takeIf { it > 0 } ?: videos.size
    val fans = card?.follower ?: 0
    return TopPersonUi(
        eyebrow = "SPACE / UP 主空间",
        mid = mid,
        name = info?.name?.takeIf { it.isNotBlank() } ?: "UP 主",
        subtitle = "UID $mid",
        description = info?.sign?.takeIf { it.isNotBlank() }
            ?: "这个 UP 主还没有填写签名。",
        badge = "UP 主",
        avatarUrl = info?.face.orEmpty(),
        glyph = TopGlyph.PERSON,
        stats = listOf(
            TopPersonStat(formatCount(fans.toLong()), "粉丝"),
            TopPersonStat(formatCount(videoCount.toLong()), "视频"),
        ),
        recentVideos = videos.take(3).map { video ->
            TopPersonVideo(
                title = video.title,
                coverUrl = video.pic,
                meta = video.duration,
            )
        },
        recentMoments = moments.take(2).map { moment ->
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
