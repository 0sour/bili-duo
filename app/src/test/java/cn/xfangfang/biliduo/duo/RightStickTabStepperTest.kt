package cn.xfangfang.biliduo.duo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RightStickTabStepperTest {

    @Test
    fun firstPushUpEmitsPrevious() {
        val stepper = RightStickTabStepper()
        assertEquals(-1, stepper.onAxis(-0.9f, 0L))
    }

    @Test
    fun firstPushDownEmitsNext() {
        val stepper = RightStickTabStepper()
        assertEquals(1, stepper.onAxis(0.9f, 0L))
    }

    @Test
    fun heldStickRepeatsAfterInterval() {
        val stepper = RightStickTabStepper(repeatMs = 200L)
        assertEquals(1, stepper.onAxis(0.9f, 0L))
        assertNull(stepper.onAxis(0.9f, 50L))
        assertNull(stepper.onAxis(0.9f, 199L))
        assertEquals(1, stepper.onAxis(0.9f, 200L))
    }

    @Test
    fun reverseDirectionEmitsImmediately() {
        val stepper = RightStickTabStepper()
        assertEquals(1, stepper.onAxis(0.9f, 0L))
        assertEquals(-1, stepper.onAxis(-0.9f, 20L))
    }

    @Test
    fun jitterBetweenThresholdsDoesNotRetrigger() {
        val stepper = RightStickTabStepper()
        assertEquals(1, stepper.onAxis(0.7f, 0L))
        assertNull(stepper.onAxis(0.5f, 50L))
        assertNull(stepper.onAxis(0.7f, 100L))
    }

    @Test
    fun releaseThenPushEmitsAgain() {
        val stepper = RightStickTabStepper()
        assertEquals(1, stepper.onAxis(0.9f, 0L))
        assertNull(stepper.onAxis(0.1f, 30L))
        assertEquals(1, stepper.onAxis(0.9f, 60L))
    }

    @Test
    fun resetClearsDirection() {
        val stepper = RightStickTabStepper()
        assertEquals(1, stepper.onAxis(0.9f, 0L))
        stepper.reset()
        assertEquals(1, stepper.onAxis(0.9f, 10L))
    }
}
