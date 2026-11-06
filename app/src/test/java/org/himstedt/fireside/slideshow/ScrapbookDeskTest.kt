package org.himstedt.fireside.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrapbookDeskTest {

    private val frameAspect = 16f / 9f

    @Test
    fun `the desk is full from the very first step`() {
        // Older places wrap into the end of the album, so the table is never
        // half empty when a show starts.
        val shown = ScrapbookDesk.visible(0, 40)
        assertEquals(20, shown.size)
        assertEquals(0, shown.first())
        assertEquals(shown.distinct().size, shown.size)
        shown.forEach { assertTrue(it in 0 until 40) }
    }

    @Test
    fun `the desk fills to twenty cards`() {
        assertEquals(20, ScrapbookDesk.visible(19, 40).size)
        assertEquals(20, ScrapbookDesk.visible(39, 40).size)
        // Newest first, no repeats.
        val shown = ScrapbookDesk.visible(19, 40)
        assertEquals(shown.distinct().size, shown.size)
        assertEquals(19, shown.first())
    }

    @Test
    fun `a short album shows every photo once`() {
        val shown = ScrapbookDesk.visible(3, 5)
        assertEquals(5, shown.size)
        assertEquals(shown.distinct().size, shown.size)
        shown.forEach { assertTrue(it in 0 until 5) }
    }

    @Test
    fun `one card arrives and one leaves per step`() {
        val before = ScrapbookDesk.visible(19, 40).toSet()
        val after = ScrapbookDesk.visible(20, 40).toSet()
        assertEquals(1, (after - before).size)
        assertEquals(1, (before - after).size)
        // The card that leaves is the oldest one that was on the desk.
        assertEquals(0, ScrapbookDesk.leaving(20, 40))
        assertEquals(ScrapbookDesk.visible(19, 40).last(), ScrapbookDesk.leaving(20, 40))
    }

    @Test
    fun `nothing leaves while the album is smaller than the desk`() {
        assertNull(ScrapbookDesk.leaving(5, 20))
        assertNull(ScrapbookDesk.leaving(19, 7))
        assertNotNull(ScrapbookDesk.leaving(21, 40))
    }

    @Test
    fun `placements are stable for a photo`() {
        val a = ScrapbookDesk.placement("asset-123", 1.5f, frameAspect)
        val b = ScrapbookDesk.placement("asset-123", 1.5f, frameAspect)
        assertEquals(a, b)
    }

    @Test
    fun `different photos land in different places`() {
        val places = (0 until 20).map {
            ScrapbookDesk.placement("asset-$it", 1.5f, frameAspect)
        }
        assertTrue("placements are too uniform", places.map { it.cx }.distinct().size > 15)
        assertTrue(places.map { it.cy }.distinct().size > 15)
    }

    @Test
    fun `a card may hang over an edge but never half leave the screen`() {
        val aspects = listOf(0.7f, 1f, 1.5f, 2f)
        for (i in 0 until 200) {
            for (aspect in aspects) {
                val p = ScrapbookDesk.placement("asset-$i", aspect, frameAspect)
                val cardAspect = 1.034f / aspect + 0.10f
                val heightFraction = (p.width * frameAspect * cardAspect).coerceAtMost(0.62f)
                val visibleW = minOf(p.cx + p.width / 2f, 1f) - maxOf(p.cx - p.width / 2f, 0f)
                val visibleH = minOf(p.cy + heightFraction / 2f, 1f) - maxOf(p.cy - heightFraction / 2f, 0f)
                assertTrue("card $i/$aspect is mostly off the left", visibleW / p.width > 0.5f)
                assertTrue("card $i/$aspect is mostly off the top", visibleH / heightFraction > 0.5f)
                assertTrue("rotation is too steep", kotlin.math.abs(p.rotation) <= 12f)
            }
        }
    }

    @Test
    fun `cards are big and their sizes vary`() {
        val places = (0 until 60).map { ScrapbookDesk.placement("asset-$it", 1.5f, frameAspect) }
        places.forEach { assertTrue("card too small: ${it.width}", it.width >= 0.22f) }
        places.forEach { assertTrue("card too big: ${it.width}", it.width <= 0.40f) }
        assertTrue("sizes barely vary", places.map { it.width }.distinct().size > 40)
    }

    @Test
    fun `big cards do cover most of the desk`() {
        // Twenty cards of average size must add up to well over the screen area;
        // overlapping is expected, but the pile should bury the paper.
        val places = (0 until 20).map { ScrapbookDesk.placement("asset-$it", 1.4f, frameAspect) }
        val covered = places.sumOf { p ->
            val cardAspect = 1.034f / 1.4f + 0.10f
            val heightFraction = (p.width * frameAspect * cardAspect).coerceAtMost(0.62f)
            (p.width * heightFraction).toDouble()
        }
        assertTrue("the pile only covers $covered of the desk", covered > 1.6)
    }

    @Test
    fun `a broken aspect still gets a sane placement`() {
        listOf(0f, -1f, Float.NaN).forEach { aspect ->
            val p = ScrapbookDesk.placement("asset-x", aspect, frameAspect)
            assertTrue(p.width.isFinite() && p.width > 0f)
            assertTrue(p.cx.isFinite() && p.cx > 0f && p.cx < 1f)
            assertTrue(p.cy.isFinite() && p.cy > 0f && p.cy < 1f)
        }
    }

    @Test
    fun `an empty album has an empty desk`() {
        assertTrue(ScrapbookDesk.visible(0, 0).isEmpty())
        assertNull(ScrapbookDesk.leaving(0, 0))
    }
}
