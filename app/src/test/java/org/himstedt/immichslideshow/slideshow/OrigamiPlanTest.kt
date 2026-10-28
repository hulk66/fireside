package org.himstedt.immichslideshow.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wall's own arithmetic: which cells turn on a page, what they turn into, and
 * whether a photo is ever repeated or skipped on the way.
 */
class OrigamiPlanTest {

    private val count = 40
    private val slots = 6

    private fun wall(page: Long) = OrigamiPlan.wall(page, count, slots)

    @Test
    fun `two pages in three turn the whole wall and the third turns one cell`() {
        val kinds = (0L..8L).map { OrigamiPlan.isFull(it) }
        assertEquals(
            listOf(true, true, false, true, true, false, true, true, false),
            kinds,
        )
        for (page in 0L..8L) {
            val cells = wall(page).cells
            val turning = cells.count { it.turns }
            if (OrigamiPlan.isFull(page) && page > 0L) {
                assertEquals("page $page should turn every cell", slots, turning)
            } else if (!OrigamiPlan.isFull(page)) {
                assertEquals("page $page should turn exactly one cell", 1, turning)
            }
        }
    }

    @Test
    fun `a single page keeps the wall it inherits`() {
        // The point of a single turn is that nothing else moves: the untouched
        // cells must hold exactly the photo the previous page gave them.
        for (page in listOf(2L, 5L, 8L, 11L)) {
            val before = wall(page - 1).cells.map { it.to }
            val after = wall(page).cells
            after.forEachIndexed { slot, cell ->
                if (!cell.turns) {
                    assertEquals("page $page moved cell $slot", before[slot], cell.from)
                    assertEquals("page $page moved cell $slot", before[slot], cell.to)
                }
            }
        }
    }

    @Test
    fun `a single turn previews the cell the next whole turn brings`() {
        // The photo a single cell turns into is the one the next full page puts
        // in that same cell. That is what stops it repeating a photo already on
        // the wall, or skipping one on the way past.
        for (page in listOf(2L, 5L, 8L, 11L)) {
            val single = wall(page)
            val next = wall(page + 1)
            assertTrue("page $page should be a single turn", !single.full)
            single.cells.forEachIndexed { slot, cell ->
                if (cell.turns) {
                    assertEquals("page $page cell $slot", next.cells[slot].to, cell.to)
                }
            }
        }
    }

    @Test
    fun `the single cell moves around the wall`() {
        // Always replacing cell 0 would read as one corner of the wall flickering.
        val cells = (0L until 3L * slots).map { page ->
            val p = page * OrigamiPlan.PERIOD + OrigamiPlan.PERIOD - 1
            wall(p).cells.indexOfFirst { it.turns }
        }
        assertEquals("each cell should get its turn", slots, cells.take(slots.toInt()).toSet().size)
        assertTrue("no cell should be missed", cells.all { it >= 0 })
    }

    @Test
    fun `a photo never repeats in the cell it is already in`() {
        // A turn that leaves the same photo behind is not a turn, it is a flap
        // with nothing underneath.
        for (page in 1L..30L) {
            for ((slot, cell) in wall(page).cells.withIndex()) {
                if (cell.turns) {
                    assertTrue(
                        "page $page cell $slot turns ${{ cell.from }} into itself",
                        cell.from != cell.to,
                    )
                }
            }
        }
    }

    @Test
    fun `the wall advances through the album`() {
        // Every whole-wall turn should move the window on by a wall's worth, so
        // the album is covered in order rather than sampling the same photos.
        val firsts = (0L..6L).filter { OrigamiPlan.isFull(it) }.map { wall(it).cells.first().to }
        val expected = (0L..6L).filter { OrigamiPlan.isFull(it) }
            .mapIndexed { i, _ -> (i * slots) % count }
        assertEquals(expected, firsts)
    }

    @Test
    fun `the first page simply appears`() {
        // Nothing turns on the opening page: there is no wall behind it to fold.
        val first = wall(0L)
        assertTrue(first.full)
        assertTrue("the first wall should not turn", first.cells.none { it.turns })
    }

    @Test
    fun `indices stay inside a short album`() {
        // Fewer photos than cells is the awkward case: the window has to wrap
        // several times inside one wall.
        for (album in listOf(1, 2, 3, 7)) {
            for (page in 0L..12L) {
                for (cell in OrigamiPlan.wall(page, album, slots).cells) {
                    assertTrue("from ${cell.from} outside 0..<$album", cell.from in 0 until album)
                    assertTrue("to ${cell.to} outside 0..<$album", cell.to in 0 until album)
                }
            }
        }
    }

    @Test
    fun `prefetch covers both walls it needs`() {
        for (page in 0L..9L) {
            val wanted = OrigamiPlan.prefetch(page, count, slots)
            for (p in page..page + 1) {
                for (cell in wall(p).cells) {
                    assertTrue("page $page does not prefetch ${cell.from}", cell.from in wanted)
                    assertTrue("page $page does not prefetch ${cell.to}", cell.to in wanted)
                }
            }
            assertTrue("prefetch is unbounded", wanted.size <= slots * 4)
        }
        assertTrue(OrigamiPlan.prefetch(3L, 0, slots).isEmpty())
        assertTrue(OrigamiPlan.prefetch(3L, count, 0).isEmpty())
    }

    @Test
    fun `an empty wall has no cells`() {
        assertTrue(OrigamiPlan.wall(4L, 0, slots).cells.isEmpty())
        assertFalse(OrigamiPlan.wall(4L, 0, slots).full)
    }
}
