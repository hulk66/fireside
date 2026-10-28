package com.firetv.immichslideshow.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.roundToInt
import org.junit.Test

class ReflectionsWallTest {

    private val box = GridRect(115f, 54f, 1805f, 810f)
    private val gap = 6f

    @Test
    fun `each cell holds a different photo`() {
        val shown = ReflectionsWall.visible(10, 340)
        assertEquals(3, shown.size)
        assertEquals(shown.distinct().size, shown.size)
    }

    @Test
    fun `exactly one cell changes per step`() {
        for (index in 1 until 12) {
            val before = ReflectionsWall.visible(index - 1, 340)
            val after = ReflectionsWall.visible(index, 340)
            val changed = before.indices.count { before[it] != after[it] }
            assertEquals("step $index changed $changed cells", 1, changed)
            assertEquals(ReflectionsWall.newestCell(index, 340), before.indices.first { before[it] != after[it] })
        }
    }

    @Test
    fun `the newest photo sits in the newest cell`() {
        for (index in 0 until 12) {
            val cell = ReflectionsWall.newestCell(index, 340)
            assertEquals(index, ReflectionsWall.visible(index, 340)[cell])
        }
    }

    @Test
    fun `a short album shows one cell per photo`() {
        assertEquals(1, ReflectionsWall.visible(0, 1).size)
        assertEquals(2, ReflectionsWall.visible(3, 2).size)
        assertEquals(3, ReflectionsWall.visible(3, 3).size)
        assertTrue(ReflectionsWall.visible(0, 0).isEmpty())
    }

    @Test
    fun `every cell keeps its photo's aspect ratio`() {
        val mixes = listOf(
            listOf(0.75f, 1.333f, 1.5f),
            listOf(1.333f, 1.4f, 1.35f),
            listOf(0.66f, 0.7f, 0.8f),
            listOf(2.4f, 1.0f, 0.5f),
        )
        for (aspects in mixes) {
            val rects = ReflectionsWall.layout(aspects, box, gap)
            assertEquals(aspects.size, rects.size)
            rects.forEachIndexed { i, rect ->
                assertEquals("photo $i of $aspects was stretched", aspects[i], rect.width / rect.height, 0.02f)
            }
        }
    }

    @Test
    fun `every column sits on the baseline`() {
        val rects = ReflectionsWall.layout(listOf(0.75f, 1.333f, 1.5f), box, gap)
        // The tall column and the lowest photo of the stack both meet the
        // baseline; the photo above it stops a gap short, by design.
        rects.forEach { assertTrue("cell below the baseline: ${it.bottom}", it.bottom <= box.bottom + 0.5f) }
        rects.groupBy { it.left.roundToInt() }.values.forEach { column ->
            assertEquals("a column floats above the baseline", box.bottom, column.maxOf { it.bottom }, 0.5f)
        }
    }

    @Test
    fun `the wall stays inside its box`() {
        val mixes = listOf(
            listOf(0.45f, 0.45f, 2.6f),
            listOf(2.6f, 2.6f, 2.6f),
            listOf(0.45f, 0.45f, 0.45f),
            listOf(1.333f, 1.4f, 1.35f),
        )
        for (aspects in mixes) {
            ReflectionsWall.layout(aspects, box, gap).forEach {
                assertTrue("$aspects pokes out left: ${it.left}", it.left >= box.left - 1f)
                assertTrue("$aspects pokes out right: ${it.right}", it.right <= box.right + 1f)
                assertTrue("$aspects pokes out top: ${it.top}", it.top >= box.top - 1f)
                assertTrue("$aspects pokes out bottom: ${it.bottom}", it.bottom <= box.bottom + 1f)
            }
        }
    }

    @Test
    fun `cells never overlap`() {
        val rects = ReflectionsWall.layout(listOf(0.75f, 1.333f, 1.5f), box, gap)
        for (i in rects.indices) {
            for (j in i + 1 until rects.size) {
                val a = rects[i]
                val b = rects[j]
                val overlapX = minOf(a.right, b.right) - maxOf(a.left, b.left)
                val overlapY = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
                assertTrue("cells $i and $j overlap", overlapX <= 0.5f || overlapY <= 0.5f)
            }
        }
    }

    @Test
    fun `the wall is centred in its box`() {
        val rects = ReflectionsWall.layout(listOf(0.75f, 1.333f, 1.5f), box, gap)
        val left = rects.minOf { it.left }
        val right = rects.maxOf { it.right }
        assertEquals(box.left + box.right, left + right, 2f)
    }

    @Test
    fun `the columns sit one gap apart`() {
        // The tall column and the stack should be neighbours, not spread across
        // the screen with a hole between them.
        val rects = ReflectionsWall.layout(listOf(0.75f, 1.333f, 1.5f), box, gap)
        val sorted = rects.sortedBy { it.left }
        val tall = sorted.first()
        val stack = sorted.drop(1)
        stack.forEach { assertEquals(tall.right + gap, it.left, 1f) }
    }

    @Test
    fun `a single photo fills the box at its own shape`() {
        val rects = ReflectionsWall.layout(listOf(1.5f), box, gap)
        assertEquals(1, rects.size)
        assertEquals(1.5f, rects[0].width / rects[0].height, 0.02f)
        assertEquals(box.left + box.right, rects[0].left + rects[0].right, 2f)
    }

    @Test
    fun `two photos share the height`() {
        val rects = ReflectionsWall.layout(listOf(1.333f, 0.75f), box, gap)
        assertEquals(2, rects.size)
        rects.forEach {
            assertEquals(box.bottom, it.bottom, 0.5f)
            assertEquals(box.height, it.height, 1f)
        }
        assertEquals(1.333f, rects[0].width / rects[0].height, 0.02f)
        assertEquals(0.75f, rects[1].width / rects[1].height, 0.02f)
    }

    @Test
    fun `broken input yields no cells`() {
        assertTrue(ReflectionsWall.layout(emptyList(), box, gap).isEmpty())
        assertTrue(ReflectionsWall.layout(listOf(1f), GridRect(0f, 0f, 0f, 0f), gap).isEmpty())
    }

    @Test
    fun `nonsense aspects still lay out sanely`() {
        val rects = ReflectionsWall.layout(listOf(0f, -1f, Float.NaN), box, gap)
        assertEquals(3, rects.size)
        rects.forEach {
            assertTrue(it.width.isFinite() && it.width > 0f)
            assertTrue(it.height.isFinite() && it.height > 0f)
        }
    }
}
