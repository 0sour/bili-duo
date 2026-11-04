package cn.xfangfang.biliduo.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Home : Screen("home", "首页", Icons.Default.Home)
    data object Dynamic : Screen("dynamic", "动态", Icons.Default.DynamicFeed)
    data object Search : Screen("search", "搜索", Icons.Default.Search)
    data object Profile : Screen("profile", "个人", Icons.Default.Person)
}

object Routes {
    const val DETAIL = "detail/{bvid}"
    const val PLAYER = "player/{bvid}"
    const val PLAYER_CID = "player/{bvid}/{cid}"
    const val DOWNLOAD_PLAY = "download/{bvid}/{cid}"
    const val PGC_PLAYER = "pgc/{seasonId}"
    const val LIVE_PLAYER = "live/{roomId}"
    const val LOGIN = "login"
    const val HISTORY = "history"
    const val CHANNEL = "channel/{mid}"
    const val FAVORITES = "favorites"
    const val FOLLOWING = "following"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val NOTE = "note/{cvid}"

    fun detail(bvid: String): String {
        if (bvid.isBlank()) throw IllegalArgumentException("BVID cannot be empty")
        return "detail/$bvid"
    }
    fun note(cvid: String): String {
        return "note/$cvid"
    }
    fun player(bvid: String, cid: Long? = null): String {
        if (bvid.isBlank()) throw IllegalArgumentException("BVID cannot be empty")
        return if (cid != null) "player/$bvid/$cid" else "player/$bvid"
    }
    fun downloadPlay(bvid: String, cid: Long): String {
        if (bvid.isBlank()) throw IllegalArgumentException("BVID cannot be empty")
        return "download/$bvid/$cid"
    }
    fun channel(mid: Long) = "channel/$mid"
    fun pgc(seasonId: Long) = "pgc/$seasonId"
    fun live(roomId: Long) = "live/$roomId"
}

val bottomNavItems = listOf(Screen.Search, Screen.Profile)
val railItems = listOf(Screen.Home, Screen.Dynamic, Screen.Search, Screen.Profile)
