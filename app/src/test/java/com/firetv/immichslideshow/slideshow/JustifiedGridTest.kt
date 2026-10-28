package com.firetv.immichslideshow.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JustifiedGridTest {

    private val width = 1920f
    private val height = 1080f
    private val gap = 6f

    private fun layout(vararg aspects: Float) =
        JustifiedGrid.layout(aspects.toList(), width, height, gap)

    @Test
    fun `one rect per photo, in order`() {
        val rects = layout(1.5f, 0.8f, 1f, 1.77f, 1.33f, 0.66f, 1.5f, 1f)
        assertEquals(8, rects.size)
    }

    @Test
    fun `the mosaic covers the viewport`() {
        val rects = layout(1.5f, 0.8f, 1f, 1.77f, 1.33f, 0.66f, 1.5f, 1f, 1.2f, 0.9f)
        val minTop = rects.minOf { it.top }
        val maxBottom = rects.maxOf { it.bottom }
        val minLeft = rects.minOf { it.left }
        val maxRight = rects.maxOf { it.right }
        assertEquals(gap, minTop, 0.5f)
        assertEquals(gap, minLeft, 0.5f)
        assertEquals(width - gap, maxRight, 1.5f)
        assertEquals(height - gap, maxBottom, 1.5f)
    }

    @Test
    fun `every row is filled edge to edge`() {
        val rects = layout(1.5f, 0.8f, 1f, 1.77f, 1.33f, 0.66f, 1.5f, 1f)
        rects.groupBy { it.top }.values.forEach { row ->
            val left = row.minOf { it.left }
            val right = row.maxOf { it.right }
            assertEquals(gap, left, 0.5f)
            assertEquals(width - gap, right, 1.5f)
        }
    }

    @Test
    fun `photos in a row share one height`() {
        val rects = layout(1.5f, 0.8f, 1f, 1.77f, 1.33f, 0.66f, 1.5f, 1f)
        rects.groupBy { it.top }.values.forEach { row ->
            val first = row.first().height
            row.forEach { assertEquals(first, it.height, 0.01f) }
        }
    }

    @Test
    fun `wider photos get wider cells`() {
        // Two photos, both placed in one row: the wider one must be wider.
        val rects = JustifiedGrid.layout(listOf(2.0f, 1.0f), width, height, gap)
        assertEquals(2, rects.size)
        assertTrue(rects[0].width > rects[1].width)
        assertTrue(rects[0].area > rects[1].area)
    }

    @Test
    fun `cells in a row never overlap`() {
        val rects = layout(1.5f, 0.8f, 1f, 1.77f, 1.33f, 0.66f, 1.5f, 1f, 1.2f, 0.9f)
        rects.groupBy { it.top }.values.forEach { row ->
            val sorted = row.sortedBy { it.left }
            sorted.zipWithNext { a, b ->
                assertTrue("cells overlap", b.left >= a.right)
                assertTrue("gap is not honoured", b.left - a.right >= gap - 0.01f)
            }
        }
    }

    @Test
    fun `a single photo still fills the frame`() {
        val rects = JustifiedGrid.layout(listOf(1.5f), width, height, gap)
        assertEquals(1, rects.size)
        assertEquals(width - gap, rects[0].right, 0.5f)
        assertEquals(height - gap, rects[0].bottom, 0.5f)
    }

    @Test
    fun `a portrait photo gets a narrow cell`() {
        val rects = JustifiedGrid.layout(listOf(0.6f, 1.6f), width, height, gap)
        assertTrue(rects[0].width < rects[1].width)
    }

    @Test
    fun `nonsense aspects fall back instead of exploding`() {
        val rects = JustifiedGrid.layout(listOf(0f, -3f, Float.NaN, Float.POSITIVE_INFINITY), width, height, gap)
        assertEquals(4, rects.size)
        rects.forEach {
            assertTrue(it.width.isFinite() && it.width > 0f)
            assertTrue(it.height.isFinite() && it.height > 0f)
        }
    }

    @Test
    fun `nothing to lay out gives no cells`() {
        assertTrue(JustifiedGrid.layout(emptyList(), width, height, gap).isEmpty())
        assertTrue(JustifiedGrid.layout(listOf(1f), 0f, height, gap).isEmpty())
        assertTrue(JustifiedGrid.layout(listOf(1f), width, 0f, gap).isEmpty())
    }

    @Test
    fun `a phone sized box works too`() {
        val rects = JustifiedGrid.layout(List(9) { 1.4f }, 384f, 216f, 2f)
        assertEquals(9, rects.size)
        assertEquals(216f - 2f, rects.maxOf { it.bottom }, 1f)
        assertEquals(384f - 2f, rects.maxOf { it.right }, 1f)
    }
}
