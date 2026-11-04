package cn.xfangfang.biliduo.duo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuoSessionViewModelTest {

    @Test
    fun playerEntryStartsInPlayerModeAndExitReturnsToBrowse() {
        val session = DuoSessionViewModel()
        assertEquals(DuoMode.BROWSE, session.mode.value)

        session.onPlayerEntered()
        assertEquals(DuoMode.PLAYER, session.mode.value)
        assertEquals(true, session.playerActive.value)

        session.onPlayerExited()
        assertEquals(DuoMode.BROWSE, session.mode.value)
        assertEquals(false, session.playerActive.value)
    }

    @Test
    fun overlayReturnsToPlayerMode() {
        val session = DuoSessionViewModel()
        session.onPlayerEntered()
        session.openOverlay(DuoOverlay.QUALITY)
        assertEquals(DuoMode.OVERLAY, session.mode.value)
        assertEquals(DuoOverlay.QUALITY, session.overlay.value)

        session.closeOverlay()
        assertEquals(DuoMode.PLAYER, session.mode.value)
        assertNull(session.overlay.value)
    }

    @Test
    fun leavingPlayerClosesOverlay() {
        val session = DuoSessionViewModel()
        session.onPlayerEntered()
        session.openOverlay(DuoOverlay.QUALITY)
        session.onPlayerExited()

        assertNull(session.overlay.value)
        assertEquals(DuoMode.BROWSE, session.mode.value)
    }

    @Test
    fun overlayCannotOpenWithoutPlayer() {
        val session = DuoSessionViewModel()
        session.openOverlay(DuoOverlay.QUALITY)
        assertNull(session.overlay.value)
        assertEquals(DuoMode.BROWSE, session.mode.value)
    }

    @Test
    fun screenBackInterceptorRunsBeforeFocusBackInterceptor() {
        val session = DuoSessionViewModel()
        var screenCalls = 0
        var focusCalls = 0
        session.setBackInterceptor {
            screenCalls++
            true
        }
        session.setFocusBackInterceptor {
            focusCalls++
            true
        }

        session.requestBack()
        assertEquals(1, screenCalls)
        assertEquals(0, focusCalls)
    }

    @Test
    fun focusBackInterceptorRunsWhenScreenDoesNotConsumeB() {
        val session = DuoSessionViewModel()
        session.onPlayerEntered()
        var screenCalls = 0
        var focusCalls = 0
        session.setBackInterceptor {
            screenCalls++
            false
        }
        session.setFocusBackInterceptor {
            focusCalls++
            true
        }

        session.requestBack()
        assertEquals(1, screenCalls)
        assertEquals(1, focusCalls)
        assertEquals(DuoMode.PLAYER, session.mode.value)
    }
}
