package org.himstedt.fireside.slideshow

import kotlin.math.abs
import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wall's own arithmetic: how many panels, which photo each holds, who turns
 * when, and whether a pass through the album shows every photo exactly once.
 */
class SlidingPanelsTest {

    private val count = 40

    /** A mix of shapes, as an album would have. */
    private val aspects: List<Float> = List(count) { i ->
        when (i % 5) {
            0 -> 1.5f
            1 -> 0.66f
            2 -> 1.78f
            3 -> 1.0f
            else -> 2.4f
        }
    }

    private fun stepAt(ms: Long) = SlidingPanels.stepAt(ms, aspects)

    /** The first moment of every step of [cycle]. */
    private fun stepsOf(cycle: Long): List<PanelStep> {
        val start = cycleStart(cycle)
        val panels = SlidingPanels.panelsFor(cycle)
        return (0..panels).map { stepAt(start + it * SlidingPanels.STEP_MS) }
    }

    private fun cycleStart(cycle: Long): Long {
        var t = 0L
        for (c in 0 until cycle) t += SlidingPanels.cycleMs(c)
        return t
    }

    @Test
    fun `the wall holds four to six panels`() {
        val counts = (0L..5L).map { SlidingPanels.panelsFor(it) }
        assertEquals(listOf(4, 5, 6, 4, 5, 6), counts)
        assertTrue("never fewer than four panels", (0L..30L).all { SlidingPanels.panelsFor(it) >= 4 })
        assertTrue("never more than six panels", (0L..30L).all { SlidingPanels.panelsFor(it) <= 6 })
    }

    @Test
    fun `each panel takes its turn exactly once per cycle`() {
        for (cycle in 0L..4L) {
            val steps = stepsOf(cycle)
            val panels = SlidingPanels.panelsFor(cycle)
            assertEquals("a cycle is one step per panel plus the exchange", panels + 1, steps.size)
            val turned = steps.filter { !it.exchange }.map { it.turningSlot }
            assertEquals("every panel should turn once", (0 until panels).toList(), turned)
            // And each single step turns exactly one panel.
            steps.filter { !it.exchange }.forEach {
                assertEquals("a single step should turn one panel", 1, it.before.indices.count { k -> it.before[k] != it.photos[k] })
            }
            // The last step is the exchange, and it is the only one.
            assertTrue("the last step is the exchange", steps.last().exchange)
            assertEquals(1, steps.count { it.exchange })
        }
    }

    @Test
    fun `the exchange brings in the next cycle's opening photos`() {
        for (cycle in 0L..4L) {
            val exchange = stepsOf(cycle).last()
            val nextOpening = stepsOf(cycle + 1).first()
            assertEquals(
                "the exchange should arrive at the next wall",
                nextOpening.before,
                exchange.arrivingPhotos,
            )
            assertEquals(SlidingPanels.panelsFor(cycle + 1), exchange.arrivingPanels)
        }
    }

    @Test
    fun `no photo is shown twice in a cycle`() {
        // This is the point of the rhythm: every panel changes once, and the wall
        // is then replaced - so a pass through a cycle never repeats a photo.
        for (cycle in 0L..3L) {
            // The photos this cycle puts on the wall: the opening wall, then the
            // photo each panel's turn brings in. The exchange's arrivals belong to
            // the next cycle and are checked there.
            val shown = ArrayList<Int>()
            val steps = stepsOf(cycle)
            shown.addAll(steps.first().before)
            steps.filter { !it.exchange }.forEach { step ->
                val slot = step.turningSlot
                shown.add(step.photos[slot])
            }
            val distinct = shown.distinct()
            assertEquals("a cycle repeated a photo: $shown", distinct.size, shown.size)
            assertEquals(
                "a cycle should show two walls' worth",
                2 * SlidingPanels.panelsFor(cycle),
                distinct.size,
            )
        }
    }

    @Test
    fun `a four panel wall is fully changed in ten seconds`() {
        // The rhythm asked for: two to three seconds a photo, so a wall of four has
        // every panel changed once inside eight to twelve seconds.
        assertEquals(4, SlidingPanels.panelsFor(0L))
        // The wall is fully changed once the fourth panel has finished its slide,
        // which is four steps of the panel clock.
        val spanMs = 4 * SlidingPanels.STEP_MS
        assertTrue("four panels take ${spanMs}ms to all turn", spanMs in 8_000L..12_000L)
        assertTrue("a step is two to three seconds", SlidingPanels.STEP_MS in 2_000L..3_000L)
    }

    @Test
    fun `a turning panel always changes to a different photo`() {
        // A turn that leaves the same photo behind would be a slide with nothing to
        // reveal - and with the chase there is no gap to hide it.
        for (ms in 0L..120_000L step 250L) {
            val step = stepAt(ms)
            step.before.indices.forEach { k ->
                if (step.before[k] == step.photos[k]) return@forEach
                assertNotEquals("slot $k turns to itself", step.before[k], step.photos[k])
            }
        }
    }

    @Test
    fun `the slide rests first and moves at the end of the step`() {
        // Each photo gets its full step: the slide occupies the tail of it, so a
        // panel is not permanently in motion.
        for (cycle in 0L..1L) {
            val start = cycleStart(cycle)
            val panels = SlidingPanels.panelsFor(cycle)
            for (index in 0 until panels) {
                val base = start + index * SlidingPanels.STEP_MS
                assertEquals("slide should be at rest at the start of step $index", 0f, stepAt(base).slide, 1e-4f)
                assertTrue(
                    "slide should be under way late in the step",
                    stepAt(base + SlidingPanels.STEP_MS * 3 / 4).slide > 0f,
                )
                assertEquals(
                    "slide should be finished at the end of step $index",
                    1f,
                    stepAt(base + SlidingPanels.STEP_MS - 1).slide,
                    0.02f,
                )
            }
        }
    }

    @Test
    fun `the slide never goes backwards`() {
        for (cycle in 0L..2L) {
            val start = cycleStart(cycle)
            val panels = SlidingPanels.panelsFor(cycle)
            for (index in 0 until panels) {
                var previous = -1f
                for (offset in 0L until SlidingPanels.STEP_MS step 25L) {
                    val slide = stepAt(start + index * SlidingPanels.STEP_MS + offset).slide
                    assertTrue("slide reversed at step $index", slide >= previous - 1e-4f)
                    previous = slide
                }
            }
        }
    }

    @Test
    fun `the wall is cut once per cycle and never re-cut`() {
        // The bug this guards: cutting the layout from the *current* photos meant
        // every single-panel slide re-arranged the whole wall. The layout is cut
        // from the cycle's opening photos, so it cannot move.
        for (cycle in 0L..4L) {
            val steps = stepsOf(cycle)
            val opening = steps.first().opening
            assertEquals("the opening wall should be as wide as the wall", SlidingPanels.panelsFor(cycle), opening.size)
            steps.forEach { step ->
                assertEquals("the layout changed inside cycle $cycle", opening, step.opening)
            }
        }
    }

    @Test
    fun `a turning panel takes the closest shape still available`() {
        // A panel cut for a landscape should not receive a portrait, or the photo
        // is cropped hard to fill a cell cut for something else. Checked against an
        // independent greedy walk: each turn, in slot order, takes the nearest shape
        // of those not already taken.
        for (cycle in 0L..3L) {
            val panels = SlidingPanels.panelsFor(cycle)
            val turns = SlidingPanels.turnsFor(cycle, panels, aspects)
            assertEquals(panels, turns.size)
            val opening = turns.map { it.first }
            val pool = turns.map { it.second }
            // Every pool photo is used exactly once, and none of them is an opening
            // photo.
            assertEquals("a pool photo was used twice", pool.size, pool.toSet().size)
            assertTrue("a pool photo is also on the opening wall", opening.intersect(pool.toSet()).isEmpty())

            val taken = HashSet<Int>()
            for ((slot, turn) in turns.withIndex()) {
                val target = aspects[turn.first]
                val remaining = pool.indices.filter { it !in taken }
                val nearest = remaining.minOf { abs(ln(aspects[pool[it]]) - ln(target)) }
                val chosen = abs(ln(aspects[turn.second]) - ln(target))
                assertEquals("panel $slot of cycle $cycle took a poor shape", nearest, chosen, 0.001f)
                taken.add(pool.indexOf(turn.second))
            }
        }
    }

    @Test
    fun `a similar shape is preferred to album order`() {
        // Deliberately ordered so that plain album order would hand a panel the
        // wrong shape: the pool's first photo is a tall portrait while the panels
        // are wide landscapes.
        val shapes = listOf(1.78f, 1.78f, 1.78f, 0.55f, 1.75f, 1.70f)
        val turns = SlidingPanels.turnsFor(0L, 3, shapes)
        // The first two panels take the wide photos from the pool rather than the
        // portrait that comes first in album order...
        assertTrue("panel 0 took the portrait", shapes[turns[0].second] > 1.5f)
        assertTrue("panel 1 took the portrait", shapes[turns[1].second] > 1.5f)
        // ...and the last panel takes what is left, because the pool holds exactly
        // one photo per panel: there is nothing left to choose from.
        assertEquals(0.55f, shapes[turns[2].second], 0.01f)
    }

    @Test
    fun `the wall may slide from all four sides`() {
        // Sideways only was the bug: the theme could not slide up or down.
        val travels = (0L..40L).flatMap { cycle ->
            (0..6).map { index -> SlidingPanels.travelFor(cycle, index) }
        }.toSet()
        assertEquals("all four directions should come up", 4, travels.size)
        assertTrue("no rightward travel", travels.any { it.dx > 0f && it.dy == 0f })
        assertTrue("no leftward travel", travels.any { it.dx < 0f && it.dy == 0f })
        assertTrue("no downward travel", travels.any { it.dy > 0f && it.dx == 0f })
        assertTrue("no upward travel", travels.any { it.dy < 0f && it.dx == 0f })
        // Deterministic, so a preview cannot jitter and a photo cannot jump.
        assertEquals(SlidingPanels.travelFor(3L, 2), SlidingPanels.travelFor(3L, 2))
    }

    @Test
    fun `a travel is a unit vector on one axis`() {
        // The chase depends on it: the leaving photo and the arriving one have to
        // move along the same axis, by exactly the panel's span, or a gap opens.
        for (cycle in 0L..20L) {
            for (index in 0..6) {
                val travel = SlidingPanels.travelFor(cycle, index)
                assertEquals("travel is not a unit vector", 1f, abs(travel.dx) + abs(travel.dy), 1e-6f)
                assertTrue("travel is diagonal", travel.dx == 0f || travel.dy == 0f)
                // Measured against a cell: sideways uses its width, up and down its
                // height, so the span always matches the edge being travelled along.
                val span = travel.along(400f, 300f)
                val expected = if (travel.horizontal) 400f else 300f
                assertEquals("span does not match the axis", expected, abs(span), 1e-3f)
            }
        }
    }

    @Test
    fun `the exchange runs the way the cycle's last turn went`() {
        // Randomising the exchange separately let a wall finish sliding one way and
        // then set off the other, which reads as the wall bouncing.
        for (cycle in 0L..6L) {
            val panels = SlidingPanels.panelsFor(cycle)
            val steps = stepsOf(cycle)
            val exchange = steps.last()
            val lastTurn = steps[panels - 1]
            assertEquals("the exchange set off another way", lastTurn.travel, exchange.travel)
        }
    }

    @Test
    fun `indices stay inside a short album`() {
        // Fewer photos than panels is the awkward case: the wall has to wrap
        // several times inside one step.
        for (album in listOf(1, 2, 3, 5, 7)) {
            val small = List(album) { 1.5f }
            for (ms in 0L..60_000L step 700L) {
                val step = SlidingPanels.stepAt(ms, small)
                for (photo in step.before + step.photos + step.arrivingPhotos) {
                    assertTrue("photo $photo outside 0..<$album", photo in 0 until album)
                }
            }
        }
    }

    @Test
    fun `an empty album has no panels`() {
        val step = SlidingPanels.stepAt(5_000L, emptyList())
        assertTrue(step.before.isEmpty())
        assertFalse(step.exchange)
        assertTrue(SlidingPanels.prefetch(5_000L, emptyList()).isEmpty())
    }

    @Test
    fun `prefetch covers both photos of a turning panel`() {
        for (ms in 0L..120_000L step 300L) {
            val step = stepAt(ms)
            val wanted = SlidingPanels.prefetch(ms, aspects)
            for (photo in step.before + step.photos + step.arrivingPhotos) {
                assertTrue("at ${ms}ms the panel photo $photo is not prefetched", photo in wanted)
            }
            // The step after this one, so the arriving photo is decoded before the
            // slide starts.
            val next = SlidingPanels.stepAt(ms + SlidingPanels.STEP_MS, aspects)
            for (photo in next.before + next.photos) {
                assertTrue("at ${ms}ms the next step's photo $photo is not prefetched", photo in wanted)
            }
        }
    }

    @Test
    fun `the clock is continuous across steps and cycles`() {
        // The renderer derives everything from the clock, so a gap between one step
        // and the next would show as a jump.
        var previous = stepAt(0L)
        for (ms in 25L..180_000L step 25L) {
            val now = stepAt(ms)
            if (now.cycle == previous.cycle && now.index == previous.index) {
                assertTrue("slide jumped inside step", now.slide >= previous.slide - 1e-4f)
            } else {
                // A new step always starts at rest.
                assertEquals("step ${now.index} of cycle ${now.cycle} did not start at rest", 0f, now.slide, 0.05f)
            }
            previous = now
        }
    }
}
