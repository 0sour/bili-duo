package cn.xfangfang.biliduo.ui.search

import cn.xfangfang.biliduo.data.model.SearchUserItem
import cn.xfangfang.biliduo.util.cleanHtmlText
import cn.xfangfang.biliduo.util.formatCount

internal fun SearchUserItem.cleanForDisplay(): SearchUserItem = copy(
    uname = cleanHtmlText(uname).trim(),
    sign = cleanHtmlText(sign).trim(),
)

internal fun SearchUserItem.badgeLabel(): String =
    officialVerify?.desc?.trim()?.takeIf { it.isNotEmpty() }
        ?: if (isUpUser != 0) "UP 主" else "用户"

internal fun SearchUserItem.metaLine(): String = buildString {
    if (fans > 0) append("${formatCount(fans)}粉丝")
    if (videos > 0) {
        if (isNotEmpty()) append(" · ")
        append("${formatCount(videos)}视频")
    }
}
