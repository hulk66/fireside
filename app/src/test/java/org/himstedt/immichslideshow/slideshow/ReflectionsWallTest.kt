package org.himstedt.immichslideshow.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.roundToInt
import org.junit.Test

class ReflectionsWallTest {

    private val box = GridRect(115f, 54f, 1805f, 810f)
    private val gap = 6f

    @Test
    fun `roughly seventy percent of steps are a collage`() {
        val modes = (0 until 100).map { ReflectionsWall.modeFor(it) }
        val collage = modes.count { it == ReflectionsWall.Mode.Collage }
        assertEquals("collage share was $collage %", 70, collage)
    }

    @Test
    fun `single steps are spread out, not in one run`() {
        val modes = (0 until 10).map { ReflectionsWall.modeFor(it) }
        val longestRun = modes.fold(0 to 0) { (best, run), mode ->
            val next = if (mode == ReflectionsWall.Mode.Single) run + 1 else 0
            maxOf(best, next) to next
        }.first
        assertTrue("single steps clump: $modes", longestRun <= 1)
    }

    @Test
    fun `a single step shows exactly one photo`() {
        val single = (0 until 10).first { ReflectionsWall.modeFor(it) == ReflectionsWall.Mode.Single }
        assertEquals(1, ReflectionsWall.visible(single, 340).size)
        assertEquals(1, ReflectionsWall.cellsFor(single, 340))
        // And a collage step shows three.
        val collage = (0 until 10).first { ReflectionsWall.modeFor(it) == ReflectionsWall.Mode.Collage }
        assertEquals(3, ReflectionsWall.visible(collage, 340).size)
    }

    @Test
    fun `mode changes are detected at the right step`() {
        for (index in 1 until 30) {
            val expected = ReflectionsWall.modeFor(index) != ReflectionsWall.modeFor(index - 1)
            assertEquals("step $index", expected, ReflectionsWall.modeChangesAt(index))
        }
    }

    @Test
    fun `each cell holds a different photo`() {
        val shown = ReflectionsWall.visible(10, 340)
        assertEquals(3, shown.size)
        assertEquals(shown.distinct().size, shown.size)
    }

    @Test
    fun `exactly one cell changes per step inside a mode`() {
        for (index in 1 until 30) {
            if (ReflectionsWall.modeChangesAt(index)) continue
            val before = ReflectionsWall.visible(index - 1, 340)
            val after = ReflectionsWall.visible(index, 340)
            assertEquals("step $index changed the cell count", before.size, after.size)
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
    fun `a short album never asks for more cells than it has photos`() {
        val collageStep = (0 until 10).first { ReflectionsWall.modeFor(it) == ReflectionsWall.Mode.Collage }
        val singleStep = (0 until 10).first { ReflectionsWall.modeFor(it) == ReflectionsWall.Mode.Single }
        assertEquals(1, ReflectionsWall.visible(0, 1).size)
        assertEquals(2, ReflectionsWall.visible(collageStep, 2).size)
        assertEquals(1, ReflectionsWall.visible(singleStep, 2).size)
        assertEquals(3, ReflectionsWall.visible(collageStep, 3).size)
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
    fun `the wall fills the box horizontally and never spills sideways`() {
        // A wall that is too narrow is scaled up to fill the width, which pushes
        // the top of the tallest column out of the box; the stage crops that. It
        // must never spill sideways, though - that would cut photos in half.
        val mixes = listOf(
            listOf(0.45f, 0.45f, 2.6f),
            listOf(2.6f, 2.6f, 2.6f),
            listOf(0.45f, 0.45f, 0.45f),
            listOf(1.333f, 1.4f, 1.35f),
        )
        for (aspects in mixes) {
            val rects = ReflectionsWall.layout(aspects, box, gap)
            rects.forEach {
                assertTrue("$aspects pokes out left: ${it.left}", it.left >= box.left - 1f)
                assertTrue("$aspects pokes out right: ${it.right}", it.right <= box.right + 1f)
                assertTrue("$aspects floats above the baseline: ${it.bottom}", it.bottom <= box.bottom + 1f)
            }
        }
        // Real albums - 4:3 and 3:4 photos - fill most of the frame. They stop
        // short of the edges on purpose: filling the last few percent would push
        // the tallest column out of the top of the stage, which looks like a bug.
        for (aspects in listOf(
            listOf(1.333f, 1.4f, 1.35f),
            listOf(0.75f, 1.333f, 1.5f),
            listOf(0.8f, 0.75f, 1.78f),
        )) {
            val rects = ReflectionsWall.layout(aspects, box, gap)
            val used = (rects.maxOf { it.right } - rects.minOf { it.left }) / box.width
            assertTrue("$aspects only fills ${(used * 100).toInt()}%", used > 0.85f)
        }
    }

    @Test
    fun `the wall never grows past the top of the stage`() {
        // The wall is bottom aligned, so a wall taller than the box would be cut
        // off at the top - which is what "photos at the very top, no margin"
        // looked like. Every arrangement must fit the box height.
        val mixes = listOf(
            listOf(0.75f, 1.333f, 1.5f),
            listOf(1.333f, 1.4f, 1.35f),
            listOf(1.5f, 1.6f, 1.7f),
            listOf(0.8f, 0.75f, 1.78f),
            listOf(2.6f, 2.4f, 2.2f),
            listOf(0.66f, 0.7f, 0.8f),
        )
        for (aspects in mixes) {
            val rects = ReflectionsWall.layout(aspects, box, gap)
            rects.forEach {
                assertTrue(
                    "$aspects reaches ${(it.top).toInt()} above the box top ${box.top.toInt()}",
                    it.top >= box.top - 1f,
                )
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
