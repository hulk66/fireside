package com.firetv.immichslideshow.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridPlanTest {

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
    fun `an empty album yields nothing`() {
        assertTrue(GridPlan.indicesFor(0, 0).isEmpty())
        assertTrue(GridPlan.pageAt(0, 0).tiles.isEmpty())
        assertTrue(GridPlan.prefetchIndices(0, 0).isEmpty())
    }
}
