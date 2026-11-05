package cn.xfangfang.biliduo.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumePositionTest {

    @Test
    fun `local progress wins over cloud progress`() {
        assertEquals(42_000L, pickResumePosition(localMs = 42_000L, cloudMs = 300_000L, durationMs = 600_000L))
    }

    @Test
    fun `cloud progress is used when there is no local record`() {
        assertEquals(120_000L, pickResumePosition(localMs = 0L, cloudMs = 120_000L, durationMs = 600_000L))
    }

    @Test
    fun `cloud progress under five seconds is ignored`() {
        assertEquals(0L, normalizeCloudResume(4_999L, 600_000L))
        assertEquals(5_000L, normalizeCloudResume(5_000L, 600_000L))
    }

    @Test
    fun `cloud progress in the last five percent restarts the page`() {
        assertEquals(0L, normalizeCloudResume(570_000L, 600_000L))
        assertEquals(0L, normalizeCloudResume(599_000L, 600_000L))
        assertEquals(560_000L, normalizeCloudResume(560_000L, 600_000L))
    }

    @Test
    fun `unknown duration still allows cloud resume`() {
        assertEquals(90_000L, normalizeCloudResume(90_000L, 0L))
    }

    @Test
    fun `zero cloud progress keeps the start position`() {
        assertEquals(0L, pickResumePosition(0L, 0L, 600_000L))
    }
}
