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
        // The wall covers the frame: it reaches or passes every edge, and the
        // surplus is cropped evenly rather than squeezed into the photos.
        // The wall covers the frame: at most a hairline of margin, and the
        // surplus is cropped evenly rather than squeezed into the photos.
        assertTrue("band at the left: $minLeft", minLeft <= width * 0.01f)
        assertTrue("band at the right: $maxRight", maxRight >= width * 0.99f)
        assertTrue("band at the top: $minTop", minTop <= height * 0.01f)
        assertTrue("band at the bottom: $maxBottom", maxBottom >= height * 0.99f)
    }

    @Test
    fun `every row spans at least the frame`() {
        val rects = layout(1.5f, 0.8f, 1f, 1.77f, 1.33f, 0.66f, 1.5f, 1f)
        rects.groupBy { it.top }.values.forEach { row ->
            val left = row.minOf { it.left }
            val right = row.maxOf { it.right }
            assertTrue("row starts right of the edge: $left", left <= gap + 0.5f)
            assertTrue("row stops short of the edge: $right", right >= width - gap - 0.5f)
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
    fun `a single photo keeps its shape and fills the width`() {
        val rects = JustifiedGrid.layout(listOf(1.5f), width, height, gap)
        assertEquals(1, rects.size)
        assertEquals(width - gap, rects[0].right, 0.5f)
        assertEquals(1.5f, rects[0].width / rects[0].height, 0.02f)
        // Taller than the screen, so it is centred and cropped evenly.
        assertTrue(rects[0].top < 0f)
        assertTrue(rects[0].bottom > height)
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
    fun `every cell keeps its photo's aspect ratio`() {
        val mixes = listOf(
            List(12) { 1.333f },
            List(12) { 0.75f },
            listOf(1.33f, 0.77f, 1.33f, 0.75f, 1.33f, 1.33f, 1.33f, 1.33f, 1.68f, 1.33f, 1.33f, 1.33f),
            listOf(1.5f, 0.66f, 2.0f, 1.0f, 0.8f, 1.78f, 0.75f, 1.33f),
            listOf(0.45f, 2.6f, 1.0f, 1.4f, 0.9f),
        )
        for (aspects in mixes) {
            val rects = JustifiedGrid.layout(aspects, width, height, gap)
            assertEquals(aspects.size, rects.size)
            rects.forEachIndexed { i, rect ->
                assertEquals(
                    "photo $i of $aspects was stretched",
                    aspects[i],
                    rect.width / rect.height,
                    0.02f,
                )
            }
        }
    }

    @Test
    fun `the wall still fills the frame`() {
        val mixes = listOf(
            List(12) { 1.333f },
            List(9) { 0.75f },
            listOf(1.33f, 0.77f, 1.33f, 0.75f, 1.33f, 1.33f, 1.33f, 1.33f, 1.68f, 1.33f, 1.33f, 1.33f),
        )
        for (aspects in mixes) {
            val rects = JustifiedGrid.layout(aspects, width, height, gap)
            val top = rects.minOf { it.top }
            val bottom = rects.maxOf { it.bottom }
            val used = (bottom - top) / height
            assertTrue("wall only covers ${(used * 100).toInt()}% for $aspects", used > 0.99f)
            assertTrue("wall starts below the top edge for $aspects", top <= height * 0.01f)
            assertTrue("wall stops above the bottom edge for $aspects", bottom >= height * 0.99f)
            // Whatever is cropped is cropped evenly, not all at one end.
            // Equal overrun at both ends: the crop is shared, not dumped at one end.
            assertTrue(
                "crop is one sided for $aspects",
                kotlin.math.abs(top + bottom - height) < height * 0.05f,
            )
        }
    }

    @Test
    fun `a phone sized box works too`() {
        val rects = JustifiedGrid.layout(List(9) { 1.4f }, 384f, 216f, 2f)
        assertEquals(9, rects.size)
        assertTrue(rects.maxOf { it.right } >= 384f * 0.99f)
        rects.forEach { assertEquals(1.4f, it.width / it.height, 0.02f) }
        assertTrue(rects.maxOf { it.bottom } - rects.minOf { it.top } >= 216f - 2f)
    }
}
