package cn.xfangfang.biliduo.ui.duo

import org.junit.Assert.assertEquals
import org.junit.Test

class TabCycleTest {

    @Test
    fun nextWrapsPastTheLastIndex() {
        assertEquals(1, cycleIndex(0, 1, 6))
        assertEquals(5, cycleIndex(4, 1, 6))
        assertEquals(0, cycleIndex(5, 1, 6))
    }

    @Test
    fun previousWrapsBeforeTheFirstIndex() {
        assertEquals(5, cycleIndex(0, -1, 6))
        assertEquals(4, cycleIndex(5, -1, 6))
        assertEquals(2, cycleIndex(3, -1, 6))
    }

    @Test
    fun unknownCurrentStartsFromTheFirstEntry() {
        assertEquals(0, cycleIndex(-1, 0, 4))
        assertEquals(0, cycleIndex(99, 0, 4))
    }

    @Test
    fun emptyListStaysAtZero() {
        assertEquals(0, cycleIndex(3, 1, 0))
    }
}
