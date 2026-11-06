package org.himstedt.fireside.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridLayersTest {

    private val current = List(GridPlan.SLOTS) { i -> GridRect(i * 100f, 0f, i * 100f + 90f, 50f) }
    private val previous = List(GridPlan.SLOTS) { i -> GridRect(i * 100f, 100f, i * 100f + 80f, 160f) }

    private fun page(local: Float, generation: Long = 1L): GridPage {
        val pageMs = GridPlan.PAGE_MS
        return GridPlan.pageAt(generation * pageMs + (local * pageMs).toLong(), 40)
    }

    @Test
    fun `a settled wall is one layer per tile, in the current layout`() {
        val draws = GridLayers.plan(page(0.9f), current, previous)
        assertEquals(GridPlan.SLOTS, draws.size)
        draws.forEach { draw ->
            assertTrue(draw.incoming)
            assertEquals(1f, draw.alpha, 0.001f)
            assertTrue("incoming tiles use the new layout", current.contains(draw.cell))
        }
    }

    @Test
    fun `the old layout is never applied to photos that are leaving`() {
        // Right at the page boundary nothing has flipped yet: every visible tile
        // must still be in the cell the previous layout gave it.
        val draws = GridLayers.plan(page(0.001f), current, previous)
        val visible = draws.filter { it.alpha > 0.5f }
        assertTrue(visible.isNotEmpty())
        visible.forEach { draw ->
            assertTrue("outgoing tiles keep the old layout", previous.contains(draw.cell))
            assertTrue(!current.contains(draw.cell))
        }
    }

    @Test
    fun `mid transition both layouts are on screen`() {
        val draws = GridLayers.plan(page(0.12f), current, previous)
        assertTrue("some tiles are leaving", draws.any { !it.incoming })
        assertTrue("some tiles have arrived", draws.any { it.incoming })
    }

    @Test
    fun `fading tiles cross over, never both opaque`() {
        val draws = GridLayers.plan(page(0.12f), current, previous)
        val bySlot = draws.groupBy { draw ->
            if (draw.incoming) current.indexOf(draw.cell) else previous.indexOf(draw.cell)
        }
        bySlot.values.forEach { layers ->
            if (layers.size == 2) {
                assertEquals(1f, layers[0].alpha + layers[1].alpha, 0.02f)
            }
        }
    }

    @Test
    fun `incoming layers are painted last so they land on top`() {
        val draws = GridLayers.plan(page(0.12f), current, previous)
        val lastOutgoing = draws.indexOfLast { !it.incoming }
        val firstIncoming = draws.indexOfFirst { it.incoming }
        if (lastOutgoing >= 0 && firstIncoming >= 0) {
            assertTrue(firstIncoming > lastOutgoing)
        }
    }

    @Test
    fun `the first page has nothing to fade out`() {
        val draws = GridLayers.plan(page(0.5f, generation = 0L), current, current)
        assertEquals(GridPlan.SLOTS, draws.size)
        draws.forEach { assertTrue(it.incoming) }
    }

    @Test
    fun `an empty page paints nothing`() {
        assertTrue(GridLayers.plan(GridPage(0L, emptyList(), emptyList()), current, previous).isEmpty())
    }

    @Test
    fun `a missing layout cell is skipped instead of crashing`() {
        val draws = GridLayers.plan(page(0.5f), current.take(2), previous.take(1))
        assertTrue(draws.isNotEmpty())
        draws.forEach { draw -> assertTrue(draw.cell in current.take(2) || draw.cell in previous.take(1)) }
    }
}
