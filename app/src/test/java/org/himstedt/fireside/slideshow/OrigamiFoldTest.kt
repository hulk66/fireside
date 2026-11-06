package org.himstedt.fireside.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fold is geometry, so it is tested as geometry: where each corner of a cell
 * lands once the paper has turned, and whether the projection still looks like a
 * sheet of paper rather than a rubber band.
 */
class OrigamiFoldTest {

    private val cell = GridRect(400f, 200f, 1400f, 900f)
    private val camera = 4_000f
    private val cx = 960f
    private val cy = 540f

    private fun fold(edge: OrigamiFold.Edge, turned: Float) =
        OrigamiFold.homography(cell, edge, turned, camera, cx, cy)

    private fun assertNear(expected: Pair<Float, Float>, actual: Pair<Float, Float>, what: String) {
        assertEquals("$what x", expected.first, actual.first, 0.5f)
        assertEquals("$what y", expected.second, actual.second, 0.5f)
    }

    @Test
    fun `nothing turned is the identity`() {
        for (edge in OrigamiFold.Edge.entries) {
            val h = fold(edge, 0f)
            for (x in listOf(cell.left, cell.right)) {
                for (y in listOf(cell.top, cell.bottom)) {
                    assertNear(x to y, OrigamiFold.map(h, x, y), "$edge corner ($x, $y)")
                }
            }
        }
    }

    @Test
    fun `the hinge does not move`() {
        // A hinge that drifts would make the paper slide across the wall instead
        // of turning on it.
        for (turned in listOf(0.1f, 0.5f, 0.9f, 1f)) {
            val h = fold(OrigamiFold.Edge.BOTTOM, turned)
            assertNear(cell.left to cell.bottom, OrigamiFold.map(h, cell.left, cell.bottom), "bottom left")
            assertNear(cell.right to cell.bottom, OrigamiFold.map(h, cell.right, cell.bottom), "bottom right")

            val top = fold(OrigamiFold.Edge.TOP, turned)
            assertNear(cell.left to cell.top, OrigamiFold.map(top, cell.left, cell.top), "top left")

            val left = fold(OrigamiFold.Edge.LEFT, turned)
            assertNear(cell.left to cell.top, OrigamiFold.map(left, cell.left, cell.top), "left top")

            val right = fold(OrigamiFold.Edge.RIGHT, turned)
            assertNear(cell.right to cell.bottom, OrigamiFold.map(right, cell.right, cell.bottom), "right bottom")
        }
    }

    @Test
    fun `the free edge lifts towards the hinge`() {
        // The free edge is the one opposite the hinge: the top for a bottom
        // hinge. It must travel towards the hinge as the paper turns, and never
        // past it.
        var previous = cell.height
        for (turned in listOf(0.1f, 0.25f, 0.5f, 0.75f, 1f)) {
            val h = fold(OrigamiFold.Edge.BOTTOM, turned)
            val (_, y) = OrigamiFold.map(h, cell.left + 10f, cell.top)
            val distance = cell.bottom - y
            assertTrue("at $turned the free edge is $distance from the hinge", distance < previous)
            assertTrue("at $turned the free edge went past the hinge", distance > 0f)
            previous = distance
        }
    }

    @Test
    fun `the far edge is shorter than the hinge edge`() {
        // This is the perspective: without it the flap is a flat squash and the
        // effect reads as a zoom rather than as paper.
        for (edge in OrigamiFold.Edge.entries) {
            val h = fold(edge, 0.6f)
            val hingeWidth = hingeSpan(edge, h)
            val freeWidth = freeSpan(edge, h)
            assertTrue(
                "$edge: free edge $freeWidth should be shorter than hinge edge $hingeWidth",
                freeWidth < hingeWidth * 0.99f,
            )
        }
    }

    private fun hingeSpan(edge: OrigamiFold.Edge, h: FloatArray): Float =
        when (edge) {
            OrigamiFold.Edge.TOP, OrigamiFold.Edge.BOTTOM -> {
                val y = if (edge == OrigamiFold.Edge.BOTTOM) cell.bottom else cell.top
                val a = OrigamiFold.map(h, cell.left, y)
                val b = OrigamiFold.map(h, cell.right, y)
                kotlin.math.abs(b.first - a.first)
            }
            else -> {
                val x = if (edge == OrigamiFold.Edge.LEFT) cell.left else cell.right
                val a = OrigamiFold.map(h, x, cell.top)
                val b = OrigamiFold.map(h, x, cell.bottom)
                kotlin.math.abs(b.second - a.second)
            }
        }

    private fun freeSpan(edge: OrigamiFold.Edge, h: FloatArray): Float =
        when (edge) {
            OrigamiFold.Edge.TOP, OrigamiFold.Edge.BOTTOM -> {
                val y = if (edge == OrigamiFold.Edge.BOTTOM) cell.top else cell.bottom
                val a = OrigamiFold.map(h, cell.left, y)
                val b = OrigamiFold.map(h, cell.right, y)
                kotlin.math.abs(b.first - a.first)
            }
            else -> {
                val x = if (edge == OrigamiFold.Edge.LEFT) cell.right else cell.left
                val a = OrigamiFold.map(h, x, cell.top)
                val b = OrigamiFold.map(h, x, cell.bottom)
                kotlin.math.abs(b.second - a.second)
            }
        }

    @Test
    fun `a turned sheet stays inside its cell`() {
        // The flap is clipped to its cell, so a corner that escapes would simply
        // be cut off mid-paper.
        for (edge in OrigamiFold.Edge.entries) {
            for (turned in listOf(0.15f, 0.5f, 0.85f)) {
                val h = fold(edge, turned)
                for (x in listOf(cell.left, cell.right)) {
                    for (y in listOf(cell.top, cell.bottom)) {
                        val (px, py) = OrigamiFold.map(h, x, y)
                        assertTrue("$edge at $turned puts ($px, $py) outside the cell", px >= cell.left - 1f)
                        assertTrue("$edge at $turned puts ($px, $py) outside the cell", px <= cell.right + 1f)
                        assertTrue("$edge at $turned puts ($px, $py) outside the cell", py >= cell.top - 1f)
                        assertTrue("$edge at $turned puts ($px, $py) outside the cell", py <= cell.bottom + 1f)
                    }
                }
            }
        }
    }

    @Test
    fun `the paper goes to shadow as it turns`() {
        assertEquals(0f, OrigamiFold.shadeStrength(0f), 1e-4f)
        var previous = 0f
        for (turned in listOf(0.1f, 0.3f, 0.6f, 1f)) {
            val shade = OrigamiFold.shadeStrength(turned)
            assertTrue("shade went backwards at $turned", shade > previous)
            assertTrue("shade out of range at $turned: $shade", shade in 0f..0.65f)
            previous = shade
        }
    }

    @Test
    fun `every edge comes up over a run of pages`() {
        val edges = (1L..8L).map { OrigamiFold.edgeFor(it, seed = 0L) }.toSet()
        assertEquals("all four hinges should be used", 4, edges.size)
        // Deterministic: the same page always turns the same way.
        assertEquals(OrigamiFold.edgeFor(5L, 7L), OrigamiFold.edgeFor(5L, 7L))
        // And the album's seed does change the order.
        assertNotEquals(
            (1L..4L).map { OrigamiFold.edgeFor(it, 0L) },
            (1L..4L).map { OrigamiFold.edgeFor(it, 1L) },
        )
    }

    @Test
    fun `a sheet fold comes round every fourth page and never on the first`() {
        assertTrue("the first page has nothing behind it", !OrigamiFold.sheetFor(0L, 0L))
        val sheets = (1L..16L).count { OrigamiFold.sheetFor(it, 0L) }
        assertEquals("one page in four should turn as a sheet", 4, sheets)
        assertTrue(OrigamiFold.sheetFor(4L, 0L))
    }
}
