package com.firetv.immichslideshow.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrapbookDeskTest {

    @Test
    fun `the desk fills every slot from the first step`() {
        val count = 40
        val shown = (0 until ScrapbookDesk.SLOTS).map { ScrapbookDesk.photoIndex(0, it, count) }
        assertEquals(ScrapbookDesk.SLOTS, shown.distinct().size)
        shown.forEach { assertTrue(it in 0 until count) }
    }

    @Test
    fun `the desk shows the last five photos`() {
        val count = 40
        val index = 12
        val shown = (0 until ScrapbookDesk.SLOTS).map { ScrapbookDesk.photoIndex(index, it, count) }.toSet()
        assertEquals(setOf(8, 9, 10, 11, 12), shown)
    }

    @Test
    fun `the current photo sits on the next slot of the ring`() {
        val count = 40
        for (index in 0 until 15) {
            val slot = ScrapbookDesk.newestSlot(index)
            assertEquals(index, ScrapbookDesk.photoIndex(index, slot, count))
        }
    }

    @Test
    fun `exactly one slot changes per step`() {
        val count = 40
        for (index in 1 until 15) {
            val before = (0 until ScrapbookDesk.SLOTS).map { ScrapbookDesk.photoIndex(index - 1, it, count) }
            val after = (0 until ScrapbookDesk.SLOTS).map { ScrapbookDesk.photoIndex(index, it, count) }
            val changed = before.indices.count { before[it] != after[it] }
            assertEquals("step $index changed $changed slots", 1, changed)
            assertEquals(ScrapbookDesk.newestSlot(index), before.indices.first { before[it] != after[it] })
        }
    }

    @Test
    fun `slots wrap into the end of a short album`() {
        val shown = (0 until ScrapbookDesk.SLOTS).map { ScrapbookDesk.photoIndex(1, it, 3) }
        shown.forEach { assertTrue(it in 0..2) }
        assertEquals(1, shown[1])
    }

    @Test
    fun `an empty album yields index zero`() {
        assertEquals(0, ScrapbookDesk.photoIndex(5, 2, 0))
    }
}
