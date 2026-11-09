package cn.xfangfang.biliduo.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cn.xfangfang.biliduo.data.model.UserVideoItem
import cn.xfangfang.biliduo.ui.common.DuoPalette
import cn.xfangfang.biliduo.ui.duo.duoClickable
import cn.xfangfang.biliduo.ui.duo.duoFocusRing
import cn.xfangfang.biliduo.util.fixPic
import cn.xfangfang.biliduo.util.formatCount

@Composable
internal fun ChannelVideoCard(
    video: UserVideoItem,
    onClick: () -> Unit,
    onFocus: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    ChannelVideoRow(
        coverUrl = video.pic,
        title = video.title,
        playCount = video.playCount,
        durationText = video.duration,
        pubdate = video.pubdate,
        onClick = onClick,
        onFocus = onFocus,
        focusRequester = focusRequester,
    )
}

@Composable
internal fun ChannelVideoRow(
    coverUrl: String,
    title: String,
    playCount: Long,
    durationText: String,
    pubdate: Long,
    onClick: () -> Unit,
    onFocus: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .duoFocusRing(RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { if (it.isFocused) onFocus?.invoke() }
            .duoClickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 112.dp, height = 63.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(DuoPalette.SurfaceHigh),
        ) {
            AsyncImage(
                model = fixPic(coverUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(63.dp),
            )
            if (durationText.isNotBlank()) {
                Text(
                    text = durationText,
                    color = Color.White,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = DuoPalette.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    text = "${formatCount(playCount)} 播放",
                    color = DuoPalette.Tertiary,
                    fontSize = 9.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatChannelDate(pubdate),
                    color = DuoPalette.Tertiary,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

internal fun formatChannelDate(pubdate: Long): String {
    if (pubdate <= 0L) return ""
    val date = java.util.Date(pubdate * 1000L)
    val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return format.format(date)
}
