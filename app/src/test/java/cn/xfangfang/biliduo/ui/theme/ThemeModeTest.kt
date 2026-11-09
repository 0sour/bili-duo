package cn.xfangfang.biliduo.ui.theme

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    private fun at(hour: Int): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    @Test
    fun manualModeUsesExplicitChoice() {
        assertTrue(resolveDarkTheme(THEME_MANUAL, manualDark = true, systemDark = false, now = at(12)))
        assertFalse(resolveDarkTheme(THEME_MANUAL, manualDark = false, systemDark = true, now = at(23)))
    }

    @Test
    fun followSystemUsesSystemFlag() {
        assertTrue(resolveDarkTheme(THEME_FOLLOW_SYSTEM, false, systemDark = true, now = at(12)))
        assertFalse(resolveDarkTheme(THEME_FOLLOW_SYSTEM, true, systemDark = false, now = at(12)))
    }

    @Test
    fun autoSunIsLightAtNoonAndDarkAtNight() {
        assertFalse(resolveDarkTheme(THEME_AUTO_SUN, true, systemDark = true, now = at(12)))
        assertTrue(resolveDarkTheme(THEME_AUTO_SUN, false, systemDark = false, now = at(2)))
    }

    @Test
    fun labelsMatchThreeModes() {
        assertEquals(3, themeModeLabels.size)
        assertEquals("日出日落", themeModeLabels[THEME_AUTO_SUN])
        assertEquals("跟随系统", themeModeLabels[THEME_FOLLOW_SYSTEM])
        assertEquals("不自动切换", themeModeLabels[THEME_MANUAL])
    }

    @Test
    fun sunScheduleHasFormattedWindow() {
        val text = SunSchedule.describe(at(12))
        assertTrue(text.startsWith("约 "))
        assertTrue(text.contains("日出"))
        assertTrue(text.contains("日落"))
        assertTrue(Regex("""\d{2}:\d{2}""").containsMatchIn(text))
    }
}
