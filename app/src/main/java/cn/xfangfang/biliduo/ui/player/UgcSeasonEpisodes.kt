package cn.xfangfang.biliduo.ui.player

import cn.xfangfang.biliduo.data.model.UgcSeason

/** One episode row of a UGC season, already flattened for the console list. */
data class SeasonEpisodeUi(
    val bvid: String,
    val cid: Long,
    val title: String,
    val durationSeconds: Long,
    val sectionTitle: String,
    /** Cover and play count make the 合集 tab render like the 推荐 tab cards. */
    val cover: String = "",
    val viewCount: Long = 0,
)

/**
 * Flatten the `ugc_season` payload from `x/web-interface/view`.
 *
 * Episodes without a bvid (deleted/private) are skipped. Some entries only
 * carry the playing page under `page.cid`, so prefer the episode cid and fall
 * back to the page cid.
 */
fun UgcSeason?.episodeItems(): List<SeasonEpisodeUi> {
    if (this == null) return emptyList()
    return sections.orEmpty().flatMap { section ->
        section.episodes.orEmpty().mapNotNull { episode ->
            val bvid = episode.bvid.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val cid = episode.cid.takeIf { it > 0L }
                ?: episode.page?.cid?.takeIf { it > 0L }
                ?: 0L
            val title = episode.title.ifBlank {
                episode.arc?.title.orEmpty().ifBlank { bvid }
            }
            SeasonEpisodeUi(
                bvid = bvid,
                cid = cid,
                title = title,
                durationSeconds = episode.arc?.duration
                    ?: episode.page?.duration
                    ?: 0L,
                sectionTitle = section.title,
                cover = episode.arc?.pic.orEmpty(),
                viewCount = episode.arc?.stat?.view ?: 0L,
            )
        }
    }
}
