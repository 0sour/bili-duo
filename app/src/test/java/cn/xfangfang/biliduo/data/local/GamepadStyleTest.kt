package cn.xfangfang.biliduo.data.local

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GamepadStyleTest {

    @Test
    fun `xbox style keeps canonical face keycodes`() {
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_A,
            GamepadStyle.XBOX.normalizeFaceKey(KeyEvent.KEYCODE_BUTTON_A),
        )
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_B,
            GamepadStyle.XBOX.normalizeFaceKey(KeyEvent.KEYCODE_BUTTON_B),
        )
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_X,
            GamepadStyle.XBOX.normalizeFaceKey(KeyEvent.KEYCODE_BUTTON_X),
        )
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_Y,
            GamepadStyle.XBOX.normalizeFaceKey(KeyEvent.KEYCODE_BUTTON_Y),
        )
    }

    @Test
    fun `retro swaps ab and xy so physical positions keep their actions`() {
        // Physical bottom B emits BUTTON_B in Retro; canonical confirm is A.
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_A,
            GamepadStyle.RETRO.normalizeFaceKey(KeyEvent.KEYCODE_BUTTON_B),
        )
        // Physical right A emits BUTTON_A in Retro; canonical back is B.
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_B,
            GamepadStyle.RETRO.normalizeFaceKey(KeyEvent.KEYCODE_BUTTON_A),
        )
        // Physical left Y emits BUTTON_Y in Retro; canonical refresh is X.
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_X,
            GamepadStyle.RETRO.normalizeFaceKey(KeyEvent.KEYCODE_BUTTON_Y),
        )
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_Y,
            GamepadStyle.RETRO.normalizeFaceKey(KeyEvent.KEYCODE_BUTTON_X),
        )
    }

    @Test
    fun `non-face keys are not remapped`() {
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_START,
            GamepadStyle.RETRO.normalizeFaceKey(KeyEvent.KEYCODE_BUTTON_START),
        )
        assertEquals(
            KeyEvent.KEYCODE_DPAD_CENTER,
            GamepadStyle.RETRO.normalizeFaceKey(KeyEvent.KEYCODE_DPAD_CENTER),
        )
        assertEquals(
            KeyEvent.KEYCODE_X,
            GamepadStyle.RETRO.normalizeFaceKey(KeyEvent.KEYCODE_X),
        )
    }

    @Test
    fun `storage values round trip`() {
        assertEquals(GamepadStyle.XBOX, GamepadStyle.fromStorage("xbox"))
        assertEquals(GamepadStyle.RETRO, GamepadStyle.fromStorage("retro"))
        assertNull(GamepadStyle.fromStorage(null))
        assertNull(GamepadStyle.fromStorage("switch"))
    }
}
