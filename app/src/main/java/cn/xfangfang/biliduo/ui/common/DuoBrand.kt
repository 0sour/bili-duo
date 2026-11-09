package cn.xfangfang.biliduo.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.xfangfang.biliduo.ui.theme.DuoTheme

private val DuoPink = Color(0xFFFB7299)
private val DuoInk = Color(0xFF1A1A22)

/**
 * The line-only version of the dual-screen mark.
 *
 * Kept as a transparent variant for places that already provide their own
 * surface (e.g. a future splash); the regular app branding uses [DuoLogo].
 */
@Composable
fun DuoMark(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    accent: Color = DuoPink,
) {
    val colors = DuoTheme.colors
    Canvas(modifier.size(size)) {
        drawDuoMark(
            inset = this.size.minDimension * 0.02f,
            accent = accent,
            screen = colors.text.copy(alpha = 0.92f),
            screenFill = colors.text.copy(alpha = 0.055f),
            bottomFill = accent.copy(alpha = 0.18f),
        )
    }
}

/**
 * The app-icon tile used in the top-left of the top screen and in the
 * bottom-screen rail. This is the in-app counterpart of the launcher icon:
 * same two-screen geometry, same pink play glyph, rounded-square field.
 *
 * Dark keeps the original black tile. Light uses the selected paper-ink
 * variant: paper tile, ink screen outline, deep-pink play glyph. The mark
 * geometry is identical in both themes.
 */
@Composable
fun DuoLogo(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val colors = DuoTheme.colors
    val dark = colors.isDark
    val accent = if (dark) DuoPink else colors.pinkInk
    val screen = if (dark) {
        Color.White.copy(alpha = 0.92f)
    } else {
        DuoInk.copy(alpha = 0.92f)
    }
    val screenFill = if (dark) {
        Color.White.copy(alpha = 0.055f)
    } else {
        DuoInk.copy(alpha = 0.045f)
    }
    val bottomFill = accent.copy(alpha = if (dark) 0.18f else 0.12f)

    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val radius = this.size.minDimension * 0.24f
        val tile = RoundRect(
            left = 0f,
            top = 0f,
            right = w,
            bottom = h,
            cornerRadius = CornerRadius(radius),
        )
        val tilePath = Path().apply { addRoundRect(tile) }

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = if (dark) {
                    listOf(Color(0xFF252532), Color(0xFF0B0B0E))
                } else {
                    listOf(Color(0xFFF8F8FA), Color(0xFFEEEFF3))
                },
                start = Offset(0f, 0f),
                end = Offset(w, h),
            ),
            size = Size(w, h),
            cornerRadius = CornerRadius(radius),
        )
        clipPath(tilePath) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = if (dark) 0.22f else 0.10f),
                        Color.Transparent,
                    ),
                    center = Offset(w * 0.72f, h * 0.86f),
                    radius = w * 0.82f,
                ),
                radius = w * 0.82f,
                center = Offset(w * 0.72f, h * 0.86f),
            )
        }
        drawRoundRect(
            color = if (dark) {
                Color.White.copy(alpha = 0.08f)
            } else {
                DuoInk.copy(alpha = 0.08f)
            },
            size = Size(w, h),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = 1.dp.toPx()),
        )

        drawDuoMark(
            inset = this.size.minDimension * 0.13f,
            accent = accent,
            screen = screen,
            screenFill = screenFill,
            bottomFill = bottomFill,
        )
    }
}

private fun DrawScope.drawDuoMark(
    inset: Float,
    accent: Color,
    screen: Color,
    screenFill: Color,
    bottomFill: Color,
) {
    val w = size.width - inset * 2f
    val h = size.height - inset * 2f
    val stroke = (w / 96f) * 4.5f
    val corner = (w / 96f) * 11f

    translate(inset, inset) {
        drawRoundRect(
            color = screenFill,
            topLeft = Offset(w * 0.06f, h * 0.06f),
            size = Size(w * 0.88f, h * 0.44f),
            cornerRadius = CornerRadius(corner),
        )
        drawRoundRect(
            color = screen,
            topLeft = Offset(w * 0.06f, h * 0.06f),
            size = Size(w * 0.88f, h * 0.44f),
            cornerRadius = CornerRadius(corner),
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = bottomFill,
            topLeft = Offset(w * 0.18f, h * 0.66f),
            size = Size(w * 0.64f, h * 0.24f),
            cornerRadius = CornerRadius(corner * 0.7f),
        )
        drawRoundRect(
            color = accent,
            topLeft = Offset(w * 0.18f, h * 0.66f),
            size = Size(w * 0.64f, h * 0.24f),
            cornerRadius = CornerRadius(corner * 0.7f),
            style = Stroke(width = stroke),
        )
        val play = Path().apply {
            // Shifted right of the geometric center: a right-pointing triangle
            // carries its visual weight on the base, so bbox-centering reads left.
            moveTo(w * 0.42f, h * 0.17f)
            lineTo(w * 0.42f, h * 0.39f)
            lineTo(w * 0.64f, h * 0.28f)
            close()
        }
        drawPath(play, accent)
    }
}
