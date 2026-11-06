package org.himstedt.fireside.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DropInTest {

    @Test
    fun `a falling card starts at zero and ends settled`() {
        assertEquals(0f, Easing.dropIn(0f), 0.001f)
        assertEquals(1f, Easing.dropIn(1f), 0.001f)
    }

    @Test
    fun `it falls, bounces once, and stays landed`() {
        // Falling means falling: the value only ever rises until impact.
        var previous = -1f
        var peakAt = 0
        var peak = 0f
        for (i in 0..100) {
            val value = Easing.dropIn(i / 100f)
            if (value > peak) { peak = value; peakAt = i }
            if (i <= 60) {
                assertTrue("rose then fell before impact at $i", value >= previous - 0.0001f)
            }
            previous = value
        }
        // One bounce back up to the mark, and it never dips below it.
        for (i in peakAt..100) {
            assertTrue("fell back past the mark at $i", Easing.dropIn(i / 100f) >= 1f - 0.0001f)
        }
    }

    @Test
    fun `it lands past the mark and bounces back`() {
        val peak = (0..100).maxOf { Easing.dropIn(it / 100f) }
        assertTrue("no bounce: peak $peak", peak > 1.02f)
        assertTrue("bounce is violent: peak $peak", peak < 1.15f)
    }

    @Test
    fun `the fall accelerates`() {
        // Gravity: the first tenth of the time covers far less than the last.
        val early = Easing.dropIn(0.1f) - Easing.dropIn(0f)
        val late = Easing.dropIn(0.65f) - Easing.dropIn(0.55f)
        assertTrue("fall is not accelerating: $early then $late", late > early * 2f)
    }

    @Test
    fun `a settled card is fully opaque and in place`() {
        // The renderer drives alpha separately, but settle must reach exactly 1
        // so a resting card is drawn at its placement and not a hair above it.
        assertEquals(1f, Easing.dropIn(1f), 0.0001f)
    }
}
