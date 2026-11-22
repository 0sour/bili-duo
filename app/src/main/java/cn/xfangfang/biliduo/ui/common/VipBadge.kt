package cn.xfangfang.biliduo.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xfangfang.biliduo.ui.theme.DuoTheme

/**
 * 大会员档位标记。与登录状态/当前会员状态无关，只要档位需要大会员就显示，
 * 避免自动档或非会员用户分不清哪些清晰度/音轨是付费档。
 */
@Composable
fun DuoVipBadge(compact: Boolean = false) {
    val colors = DuoTheme.colors
    Text(
        text = "大会员",
        color = colors.pinkInk,
        fontSize = if (compact) 8.sp else 9.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(colors.pink.copy(alpha = 0.14f))
            .padding(horizontal = if (compact) 3.dp else 4.dp, vertical = 1.dp),
    )
}
