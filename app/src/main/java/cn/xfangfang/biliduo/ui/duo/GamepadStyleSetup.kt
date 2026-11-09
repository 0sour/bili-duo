package cn.xfangfang.biliduo.ui.duo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xfangfang.biliduo.data.local.GamepadStyle
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.ui.common.DuoPalette
import kotlinx.coroutines.delay

/**
 * First-run handheld-style chooser. It is intentionally skippable: skipping
 * saves Xbox, which is what the app used before this setting existed.
 */
@Composable
fun GamepadStyleSetupOverlay(
    onChoose: (GamepadStyle) -> Unit,
    onSkip: () -> Unit,
    session: DuoSessionViewModel? = null,
) {
    val focuses = remember { List(3) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(-1) }

    DisposableEffect(session) {
        session?.setBackInterceptor { true }
        session?.setHintOverride("方向键选择 · B 确认 · A 返回")
        onDispose {
            session?.setBackInterceptor(null)
            session?.setHintOverride(null)
        }
    }
    LaunchedEffect(Unit) {
        // The Home feed may request focus after its first cards arrive; keep
        // the setup layer's focus until it is dismissed.
        while (true) {
            if (focusedIndex < 0) runCatching { focuses[0].requestFocus() }
            delay(120L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoPalette.Scrim)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .background(DuoPalette.SurfaceHigh, RoundedCornerShape(16.dp))
                .border(1.dp, DuoPalette.Outline, RoundedCornerShape(16.dp))
                .padding(18.dp),
        ) {
            Text(
                text = "选择手柄风格",
                color = DuoPalette.Text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "实体按钮功能不变：B 确认 · A 返回 · Y 刷新。\n" +
                    "请按 RP Duo 当前的手柄风格选择；之后可在 设置 → 播放 修改。",
                color = DuoPalette.Secondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(14.dp))

            GamepadOptionRow(
                style = GamepadStyle.XBOX,
                focusRequester = focuses[0],
                onFocused = { focusedIndex = if (it) 0 else -1 },
                onClick = { onChoose(GamepadStyle.XBOX) },
            )
            Spacer(Modifier.height(8.dp))
            GamepadOptionRow(
                style = GamepadStyle.RETRO,
                focusRequester = focuses[1],
                onFocused = { focusedIndex = if (it) 1 else -1 },
                onClick = { onChoose(GamepadStyle.RETRO) },
            )
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .focusRequester(focuses[2])
                    .onFocusChanged {
                        if (it.isFocused) focusedIndex = 2 else if (focusedIndex == 2) focusedIndex = -1
                    }
                    .duoFocusRing(RoundedCornerShape(8.dp))
                    .duoClickable(onClick = onSkip)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "稍后设置（默认 Xbox 映射）",
                    color = DuoPalette.Secondary,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "方向键选择 · B 确认 · A 返回",
                color = DuoPalette.Tertiary,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun GamepadOptionRow(
    style: GamepadStyle,
    focusRequester: FocusRequester,
    onFocused: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocused(it.isFocused) }
            .duoFocusRing(RoundedCornerShape(10.dp))
            .background(DuoPalette.Surface, RoundedCornerShape(10.dp))
            .border(1.dp, DuoPalette.Outline, RoundedCornerShape(10.dp))
            .duoClickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = style.label,
                color = DuoPalette.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = style.faceLayout,
                color = if (style == GamepadStyle.RETRO) DuoPalette.Pink else DuoPalette.Secondary,
                fontSize = 10.sp,
            )
        }
        Text(
            text = if (style == GamepadStyle.XBOX) "默认" else "实体丝印",
            color = DuoPalette.Tertiary,
            fontSize = 9.sp,
        )
    }
}
