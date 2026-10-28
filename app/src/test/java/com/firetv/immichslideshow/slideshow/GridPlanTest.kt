package com.firetv.immichslideshow.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridPlanTest {

    @Test
    fun `page size follows the album's shapes`() {
        // A 4:3 album wants three rows of four on a 16:9 screen.
        assertEquals(12, GridPlan.slotsFor(List(30) { 1.333f }))
        // A 3:4 album wants three rows of seven.
        assertEquals(21, GridPlan.slotsFor(List(30) { 0.75f }))
        // A mix of both lands in between.
        val mixed = List(15) { 0.75f } + List(15) { 1.333f }
        assertEquals(15, GridPlan.slotsFor(mixed))
    }

    @Test
    fun `page size stays in a sane range`() {
        assertTrue(GridPlan.slotsFor(emptyList()) in 9..21)
        assertTrue(GridPlan.slotsFor(List(10) { 0.2f }) in 9..21)
        assertTrue(GridPlan.slotsFor(List(10) { 8f }) in 9..21)
        assertTrue(GridPlan.slotsFor(List(10) { Float.NaN }) in 9..21)
    }

    @Test
    fun `a sized page uses exactly that many slots`() {
        assertEquals(12, GridPlan.indicesFor(0, 100, slots = 12).size)
        assertEquals(21, GridPlan.pageAt(0, 100, slots = 21).tiles.size)
        assertEquals(24, GridPlan.prefetchIndices(0, 100, slots = 12).size)
    }

    @Test
    fun `a page is a contiguous window of the album`() {
        val indices = GridPlan.indicesFor(0, 340)
        assertEquals(GridPlan.SLOTS, indices.size)
        assertEquals((0 until GridPlan.SLOTS).toList(), indices)

        val second = GridPlan.indicesFor(1, 340)
        assertEquals((GridPlan.SLOTS until GridPlan.SLOTS * 2).toList(), second)
    }

    @Test
    fun `pages wrap around a short album`() {
        val indices = GridPlan.indicesFor(1, 20)
        assertEquals(GridPlan.SLOTS, indices.size)
        indices.forEach { assertTrue(it in 0 until 20) }
        assertEquals(indices.distinct().size, indices.size)
    }

    @Test
    fun `the wall keeps the same photos for a whole page`() {
        val a = GridPlan.pageAt(0, 340)
        val b = GridPlan.pageAt(GridPlan.PAGE_MS - 1, 340)
        assertEquals(a.generation, b.generation)
        assertEquals(a.indices, b.indices)
    }

    @Test
    fun `the page turns over on the boundary`() {
        val a = GridPlan.pageAt(GridPlan.PAGE_MS - 1, 340)
        val b = GridPlan.pageAt(GridPlan.PAGE_MS, 340)
        assertEquals(a.generation + 1, b.generation)
        assertTrue(a.indices != b.indices)
    }

    @Test
    fun `tiles flip in staggered order and settle fully`() {
        val start = GridPlan.pageAt(GridPlan.PAGE_MS, 340)
        // Right at the page boundary nothing has flipped yet.
        assertEquals(0f, start.tiles.first().mix, 0.001f)
        // A third of the way in, every tile is on the new photo.
        val settled = GridPlan.pageAt(GridPlan.PAGE_MS + GridPlan.PAGE_MS / 2, 340)
        settled.tiles.forEach { assertEquals(1f, it.mix, 0.001f) }
    }

    @Test
    fun `each tile fades from the photo it showed on the previous page`() {
        val page = GridPlan.pageAt(GridPlan.PAGE_MS, 340)
        val previous = GridPlan.indicesFor(page.generation - 1, 340)
        page.tiles.forEachIndexed { slot, tile ->
            assertEquals(previous[slot], tile.fromIndex)
            assertEquals(page.indices[slot], tile.toIndex)
        }
    }

    @Test
    fun `the very first page has nothing to fade from`() {
        val page = GridPlan.pageAt(0, 340)
        page.tiles.forEach { tile ->
            assertEquals(tile.toIndex, tile.fromIndex)
            assertEquals(1f, tile.mix, 0.001f)
        }
    }

    @Test
    fun `prefetch covers two pages at most`() {
        val indices = GridPlan.prefetchIndices(GridPlan.PAGE_MS / 2, 340)
        assertEquals(GridPlan.SLOTS * 2, indices.size)
        assertEquals(indices.distinct().size, indices.size)
    }

    @Test
    fun `prefetch stays inside a tiny album`() {
        val indices = GridPlan.prefetchIndices(0, 5)
        assertTrue(indices.isNotEmpty())
        indices.forEach { assertTrue(it in 0 until 5) }
    }

    @Test
    fun `prefetch covers every photo the wall draws`() {
        // The player decodes exactly what prefetchIndices returns, and the wall
        // draws from pageAt. If the two disagree - a page size passed to one and
        // not the other - every cell comes up empty, so pin them together.
        for (slots in listOf(9, 12, 13, 15, 16, 20)) {
            for (elapsed in listOf(0L, GridPlan.PAGE_MS / 3, GridPlan.PAGE_MS, GridPlan.PAGE_MS * 5 + 400)) {
                val page = GridPlan.pageAt(elapsed, 340, slots)
                val drawn = page.tiles.flatMap { listOf(it.fromIndex, it.toIndex) }.toSet()
                val prefetched = GridPlan.prefetchIndices(elapsed, 340, slots).toSet()
                assertTrue(
                    "slots=$slots elapsed=$elapsed: wall draws ${drawn - prefetched} that is never prefetched",
                    prefetched.containsAll(drawn),
                )
            }
        }
    }

    @Test
    fun `an empty album yields nothing`() {
        assertTrue(GridPlan.indicesFor(0, 0).isEmpty())
        assertTrue(GridPlan.pageAt(0, 0).tiles.isEmpty())
        assertTrue(GridPlan.prefetchIndices(0, 0).isEmpty())
    }
}
