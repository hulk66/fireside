package org.himstedt.fireside.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FitModeTest {

    @Test
    fun `fill is the default so an upgrade keeps the old look`() {
        assertEquals(FitMode.Fill, SlideshowPrefs().fitMode)
    }

    @Test
    fun `stored names round-trip in any case`() {
        assertEquals(FitMode.Fit, FitMode.fromName("Fit"))
        assertEquals(FitMode.Fit, FitMode.fromName("FIT"))
        assertEquals(FitMode.Fill, FitMode.fromName("fill"))
    }

    @Test
    fun `missing or unknown values fall back to fill`() {
        assertEquals(FitMode.Fill, FitMode.fromName(null))
        assertEquals(FitMode.Fill, FitMode.fromName(""))
        assertEquals(FitMode.Fill, FitMode.fromName("stretch"))
    }

    @Test
    fun `only fit letterboxes`() {
        assertTrue(FitMode.Fit.isFit)
        assertFalse(FitMode.Fill.isFit)
    }
}
