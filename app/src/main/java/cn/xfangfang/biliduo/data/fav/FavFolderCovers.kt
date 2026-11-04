package cn.xfangfang.biliduo.data.fav

import cn.xfangfang.biliduo.data.model.FavFolder
import cn.xfangfang.biliduo.util.fixPic

internal fun extractFolderCovers(folders: List<FavFolder>): Map<Long, String> =
    folders.mapNotNull { folder ->
        val cover = folder.cover?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        folder.id to fixPic(cover)
    }.toMap()
