package cn.xfangfang.biliduo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import cn.xfangfang.biliduo.data.local.AppSettings
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** Theme modes. 0/1/2 are persisted; keep the values stable. */
const val THEME_MANUAL = 0
const val THEME_FOLLOW_SYSTEM = 1
const val THEME_AUTO_SUN = 2

val themeModeLabels = mapOf(
    THEME_AUTO_SUN to "日出日落",
    THEME_FOLLOW_SYSTEM to "跟随系统",
    THEME_MANUAL to "不自动切换",
)

val themeModeOptions = listOf(THEME_AUTO_SUN, THEME_FOLLOW_SYSTEM, THEME_MANUAL)

@Immutable
data class DuoColors(
    val isDark: Boolean,
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val surface4: Color,
    val outline: Color,
    val outlineStrong: Color,
    val text: Color,
    val secondary: Color,
    val tertiary: Color,
    val disabled: Color,
    val pink: Color,
    val pinkInk: Color,
    val onPink: Color,
    val blue: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val focusRing: Color,
    val focusHalo: Color,
    val scrim: Color,
    val selectionFill: Color,
    val stageBg: Color,
)

val DarkDuoColors = DuoColors(
    isDark = true,
    bg = Color(0xFF0F0F0F),
    surface1 = Color(0xFF17171A),
    surface2 = Color(0xFF1F1F24),
    surface3 = Color(0xFF26262E),
    surface4 = Color(0xFF32323C),
    outline = Color(0xFF2E2E38),
    outlineStrong = Color(0xFF42424E),
    text = Color(0xFFF5F5F7),
    secondary = Color(0xFFA9A9B4),
    tertiary = Color(0xFF73737F),
    disabled = Color(0xFF4A4A55),
    pink = Color(0xFFFB7299),
    pinkInk = Color(0xFFFB7299),
    onPink = Color(0xFF1A0710),
    blue = Color(0xFF00A1D6),
    success = Color(0xFF4CC38A),
    warning = Color(0xFFF5A524),
    error = Color(0xFFF2555A),
    focusRing = Color(0xFFF5F5F7),
    focusHalo = Color(0xFF000000),
    scrim = Color.Black.copy(alpha = 0.66f),
    selectionFill = Color.White.copy(alpha = 0.07f),
    stageBg = Color(0xFF000000),
)

val LightDuoColors = DuoColors(
    isDark = false,
    // A dim "paper" light theme: no pure white on large surfaces. Cards sit at
    // ~95% luminance, the canvas at ~89%, which keeps hierarchy without the
    // glare of #FFFFFF in a handheld held close to the eyes.
    bg = Color(0xFFE2E3E8),
    surface1 = Color(0xFFF3F4F7),
    surface2 = Color(0xFFEAEBF0),
    surface3 = Color(0xFFD8DAE1),
    surface4 = Color(0xFFCBCDD7),
    outline = Color(0xFFCFD0D8),
    outlineStrong = Color(0xFFACAEBA),
    text = Color(0xFF111115),
    secondary = Color(0xFF383842),
    tertiary = Color(0xFF53535E),
    disabled = Color(0xFF878893),
    pink = Color(0xFFFB7299),
    pinkInk = Color(0xFFB02A5B),
    onPink = Color(0xFFFFFFFF),
    blue = Color(0xFF006E9C),
    success = Color(0xFF237A57),
    warning = Color(0xFF8A5A00),
    error = Color(0xFFC0353B),
    focusRing = Color(0xFF1B1B20),
    focusHalo = Color(0xFFFFFFFF),
    scrim = Color.Black.copy(alpha = 0.45f),
    selectionFill = Color.Black.copy(alpha = 0.09f),
    stageBg = Color(0xFF000000),
)

val LocalDuoColors = staticCompositionLocalOf { DarkDuoColors }

object DuoTheme {
    val colors: DuoColors
        @Composable @ReadOnlyComposable get() = LocalDuoColors.current
}

/**
 * Rough local sunrise/sunset model. Without asking for location permission we
 * use a mid-latitude day-length curve; it follows the seasons and switches
 * within a minute of the estimated boundary.
 */
object SunSchedule {
    fun isDaytime(now: Calendar = Calendar.getInstance()): Boolean {
        val day = now.get(Calendar.DAY_OF_YEAR)
        val daylight = 12.0 + 2.5 * sin(2.0 * PI * (day - 80) / 365.0)
        val hour = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0
        return hour in (12.0 - daylight / 2.0)..(12.0 + daylight / 2.0)
    }

    fun describe(now: Calendar = Calendar.getInstance()): String {
        val day = now.get(Calendar.DAY_OF_YEAR)
        val daylight = 12.0 + 2.5 * sin(2.0 * PI * (day - 80) / 365.0)
        val sunrise = 12.0 - daylight / 2.0
        val sunset = 12.0 + daylight / 2.0
        return "约 ${formatHour(sunrise)} 日出 · ${formatHour(sunset)} 日落"
    }

    private fun formatHour(value: Double): String {
        val total = (value * 60).toInt().coerceIn(0, 24 * 60)
        return "%02d:%02d".format(total / 60, total % 60)
    }
}

fun resolveDarkTheme(
    themeMode: Int,
    manualDark: Boolean,
    systemDark: Boolean,
    now: Calendar = Calendar.getInstance(),
): Boolean = when (themeMode) {
    THEME_FOLLOW_SYSTEM -> systemDark
    THEME_AUTO_SUN -> !SunSchedule.isDaytime(now)
    else -> manualDark
}

/** Resolves the live theme in both windows and re-evaluates sunset every minute. */
@Composable
fun rememberDuoDarkTheme(): Boolean {
    val systemDark = isSystemInDarkTheme()
    val mode = AppSettings.themeMode
    var minuteTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(mode) {
        if (mode != THEME_AUTO_SUN) return@LaunchedEffect
        while (true) {
            delay(60_000)
            minuteTick++
        }
    }
    val now = remember(minuteTick, mode) { Calendar.getInstance() }
    return resolveDarkTheme(
        themeMode = mode,
        manualDark = AppSettings.manualDark,
        systemDark = systemDark,
        now = now,
    )
}

@Composable
fun BilibiliPureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val duo = if (darkTheme) DarkDuoColors else LightDuoColors
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = duo.pink,
            onPrimary = duo.onPink,
            secondary = duo.pink,
            background = duo.bg,
            onBackground = duo.text,
            surface = duo.surface1,
            onSurface = duo.text,
            outline = duo.outline,
            error = duo.error,
        )
    } else {
        lightColorScheme(
            primary = duo.pinkInk,
            onPrimary = duo.onPink,
            secondary = duo.pinkInk,
            background = duo.bg,
            onBackground = duo.text,
            surface = duo.surface1,
            onSurface = duo.text,
            outline = duo.outline,
            error = duo.error,
        )
    }
    CompositionLocalProvider(LocalDuoColors provides duo) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
