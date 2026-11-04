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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xfangfang.biliduo.ui.common.DuoLogo
import cn.xfangfang.biliduo.ui.navigation.Screen
import cn.xfangfang.biliduo.ui.navigation.railItems

private val RailBg = Color(0xFF0C0C0D)
private val RailText = Color(0xFFA6A6AE)
private val RailPink = Color(0xFFFB7299)

@Composable
fun DuoRail(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
) {
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
        railItems.forEach { screen ->
            val selected = currentRoute == screen.route
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .duoFocusRing(RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) Color.White.copy(alpha = 0.07f) else Color.Transparent
                    )
                    .duoClickable { onNavigate(screen) }
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
