package cn.xfangfang.biliduo.ui.duo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xfangfang.biliduo.ui.common.DuoLogo
import cn.xfangfang.biliduo.ui.navigation.Screen
import cn.xfangfang.biliduo.ui.navigation.railItems
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RailBg = Color(0xFF0C0C0D)
private val RailText = Color(0xFFA6A6AE)
private val RailPink = Color(0xFFFB7299)

/**
 * Remembers that the rail, not the destination page, owns focus after a
 * navigation. The destination may request focus on its first card while data
 * loads; [locked] makes the rail reclaim it until the user presses a key or
 * touches the page.
 */
@Stable
class DuoRailFocusState {
    var pendingRoute by mutableStateOf<String?>(null)
        private set
    var locked by mutableStateOf(false)
        private set
    var focusedRoute by mutableStateOf<String?>(null)
        private set

    val railHasFocus: Boolean get() = focusedRoute != null

    fun onItemFocus(route: String, focused: Boolean) {
        if (focused) {
            focusedRoute = route
        } else if (focusedRoute == route) {
            focusedRoute = null
        }
    }

    fun beginNavigation(route: String) {
        pendingRoute = route
        locked = true
    }

    fun release() {
        locked = false
        pendingRoute = null
    }

    fun reset() {
        release()
        focusedRoute = null
    }

    internal fun consume(route: String) {
        if (pendingRoute == route) pendingRoute = null
    }
}

@Composable
fun rememberDuoRailFocusState(): DuoRailFocusState = remember { DuoRailFocusState() }

@Composable
fun DuoRail(
    currentRoute: String?,
    focusState: DuoRailFocusState,
    onNavigate: (Screen) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val itemFocuses = remember(railItems.size) { List(railItems.size) { FocusRequester() } }

    DisposableEffect(focusState) {
        onDispose { focusState.reset() }
    }

    LaunchedEffect(currentRoute, focusState.pendingRoute, focusState.locked) {
        val pending = focusState.pendingRoute ?: return@LaunchedEffect
        if (!focusState.locked || pending != currentRoute) return@LaunchedEffect
        val index = railItems.indexOfFirst { it.route == currentRoute }
        if (index < 0) return@LaunchedEffect
        // Let NavHost compose the destination first, then take focus back from
        // any initial-content request.
        delay(60)
        if (focusState.locked && focusState.pendingRoute == pending) {
            runCatching { itemFocuses[index].requestFocus() }
            focusState.consume(pending)
        }
    }

    Column(
        modifier = Modifier
            .width(64.dp)
            .fillMaxHeight()
            .background(RailBg)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DuoLogo(
            modifier = Modifier.padding(bottom = 10.dp),
            size = 40.dp,
        )
        railItems.forEachIndexed { index, screen ->
            val selected = currentRoute == screen.route
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .focusRequester(itemFocuses[index])
                    .onFocusChanged { focus ->
                        focusState.onItemFocus(screen.route, focus.isFocused)
                        if (!focus.isFocused && selected && focusState.locked) {
                            scope.launch {
                                delay(40)
                                if (focusState.locked && currentRoute == screen.route) {
                                    runCatching { itemFocuses[index].requestFocus() }
                                }
                            }
                        }
                    }
                    .duoFocusRing(RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) Color.White.copy(alpha = 0.07f) else Color.Transparent
                    )
                    .duoClickable {
                        focusState.beginNavigation(screen.route)
                        onNavigate(screen)
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = screen.icon,
                    contentDescription = screen.label,
                    tint = if (selected) RailPink else RailText,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = screen.label,
                    color = if (selected) RailPink else RailText,
                    fontSize = 9.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}
