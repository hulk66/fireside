package org.himstedt.immichslideshow.slideshow

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The print layout is arithmetic, so it is tested as arithmetic: that the front
 * print always fits the frame, that its border is even and its photo uncropped,
 * that the scatter behind it actually covers the stage, and that the drift is a
 * creep rather than a bob.
 */
class VintagePrintsTest {

    private val w = 1920f
    private val h = 1080f
    private val aspects = listOf(1.5f, 0.66f, 1.78f, 1.0f, 0.5f, 2.4f, 1.33f)

    private fun seeds() = (0..5).map { it * 7919L + 13L }

    // ------------------------------------------------------------- front print

    @Test
    fun `the front print keeps the photo's shape inside an even border`() {
        for (aspect in listOf(0.3f, 0.5f, 0.66f, 1f, 1.5f, 1.78f, 2.4f, 3.6f)) {
            for (seed in seeds()) {
                val print = VintagePrints.front(0, aspect, w, h, seed)
                val inner = print.photoWidth / print.photoHeight
                assertEquals("border changed the photo's shape at $aspect", aspect, inner, 0.02f)
                // Even on all four sides, and the same share of the width.
                assertEquals(print.margin, print.photoLeft - (print.centerX - print.width / 2f), 0.5f)
                assertEquals(print.margin, print.photoTop - (print.centerY - print.height / 2f), 0.5f)
                assertEquals(print.margin, VintagePrints.MARGIN * print.width, 0.5f)
            }
        }
    }

    @Test
    fun `the whole front print always fits the frame`() {
        // The print is tilted, so the corners of the *rotated* box are what has to
        // fit - a print hanging off the edge would be a bug, not a pile.
        for (aspect in listOf(0.3f, 0.5f, 0.66f, 1f, 1.5f, 1.78f, 2.4f, 3.6f)) {
            for (seed in seeds()) {
                val print = VintagePrints.front(0, aspect, w, h, seed)
                assertTrue(
                    "left edge at ${print.centerX - print.halfWidth}",
                    print.centerX - print.halfWidth >= -0.5f,
                )
                assertTrue(
                    "right edge at ${print.centerX + print.halfWidth}",
                    print.centerX + print.halfWidth <= w + 0.5f,
                )
                assertTrue(
                    "top edge at ${print.centerY - print.halfHeight}",
                    print.centerY - print.halfHeight >= -0.5f,
                )
                assertTrue(
                    "bottom edge at ${print.centerY + print.halfHeight}",
                    print.centerY + print.halfHeight <= h + 0.5f,
                )
            }
        }
    }

    @Test
    fun `the front print is the subject, not a thumbnail`() {
        // Filling most of the frame is the whole look: a print adrift in the middle
        // of a large stage reads as a broken layout.
        for (aspect in listOf(0.5f, 0.66f, 1f, 1.5f, 1.78f, 2.4f)) {
            for (seed in seeds()) {
                val print = VintagePrints.front(0, aspect, w, h, seed)
                val share = maxOf(print.photoHeight / h, print.photoWidth / w)
                assertTrue("$aspect only fills ${(share * 100).toInt()}%", share > 0.58f)
                // ...and it must leave room to be placed somewhere else on the
                // next step, or every print lands on top of the last one.
                assertTrue(
                    "$aspect leaves no room to move",
                    w - print.halfWidth * 2f > w * 0.10f || h - print.halfHeight * 2f > h * 0.10f,
                )
            }
        }
    }

    @Test
    fun `the front print is tilted only slightly, and differently per photo`() {
        val angles = mutableSetOf<Int>()
        for (seed in seeds()) {
            val print = VintagePrints.front(0, 1.5f, w, h, seed)
            assertTrue("tilt ${print.angleDeg}", abs(print.angleDeg) <= VintagePrints.FRONT_TILT)
            angles.add((print.angleDeg * 10f).toInt())
        }
        assertTrue("every photo got the same tilt", angles.size > 2)
    }

    @Test
    fun `the front print is placed the same way twice`() {
        // Determinism matters twice over: a preview must not jitter on every
        // recomposition, and a photo must not jump when the show is restarted.
        for (seed in seeds()) {
            val a = VintagePrints.front(3, 1.4f, w, h, seed)
            val b = VintagePrints.front(3, 1.4f, w, h, seed)
            assertEquals(a, b)
        }
        assertNotEquals(
            VintagePrints.front(0, 1.4f, w, h, 1L),
            VintagePrints.front(0, 1.4f, w, h, 2L),
        )
    }

    // ----------------------------------------------------------------- scatter

    @Test
    fun `the scatter covers the stage`() {
        // A bare patch behind the front print is the one thing this theme cannot
        // have: the pile is the background, so gaps read as a broken render.
        for (seed in seeds()) {
            val prints = (0 until VintagePrints.BACKDROP).map { slot ->
                VintagePrints.backdrop(slot, aspects[slot % aspects.size], w, h, seed, slot)
            }
            var covered = 0
            var total = 0
            val cols = 48
            val rows = 27
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val x = (c + 0.5f) / cols * w
                    val y = (r + 0.5f) / rows * h
                    total++
                    if (prints.any { contains(it, x, y) }) covered++
                }
            }
            val share = covered.toFloat() / total
            assertTrue("scatter leaves ${((1f - share) * 100).toInt()}% bare", share > 0.97f)
        }
    }

    @Test
    fun `the scatter covers the corners`() {
        // The drift turns the whole composition, so the corners of the frame are
        // as load-bearing as the middle: a gap here shows bare stage once the
        // composition rotates.
        for (seed in seeds()) {
            val prints = (0 until VintagePrints.BACKDROP).map { slot ->
                VintagePrints.backdrop(slot, aspects[slot % aspects.size], w, h, seed, slot)
            }
            // Probes outside the frame as well as inside it: the drift scales the
            // composition up, so what becomes the frame's corner is a point beyond
            // the frame in untransformed space.
            val probes = ArrayList<Pair<Float, Float>>()
            for (fx in listOf(-0.03f, 0.06f, 0.5f, 0.94f, 1.03f)) {
                for (fy in listOf(-0.03f, 0.06f, 0.5f, 0.94f, 1.03f)) {
                    probes.add(w * fx to h * fy)
                }
            }
            for ((x, y) in probes) {
                assertTrue(
                    "corner (${x.toInt()}, ${y.toInt()}) is bare at seed $seed",
                    prints.any { contains(it, x, y) },
                )
            }
        }
    }

    @Test
    fun `the scatter keeps its photos' shapes and may hang off the frame`() {
        for (seed in seeds()) {
            for (slot in 0 until VintagePrints.BACKDROP) {
                val aspect = aspects[slot % aspects.size]
                val print = VintagePrints.backdrop(slot, aspect, w, h, seed, slot)
                assertEquals(
                    "scatter print $slot was stretched",
                    aspect,
                    print.photoWidth / print.photoHeight,
                    0.05f,
                )
                assertTrue(
                    "scatter tilt ${print.angleDeg}",
                    abs(print.angleDeg) <= VintagePrints.BACK_TILT,
                )
                // Big enough to be part of a pile rather than a stamp.
                assertTrue("scatter print $slot is tiny", print.photoHeight > h * 0.35f)
            }
        }
    }

    @Test
    fun `the scatter is re-dealt from one photo to the next`() {
        // Every step re-deals the pile: same slot, different photo, different
        // arrangement.
        val first = (0 until VintagePrints.BACKDROP).map {
            VintagePrints.backdrop(it, 1.4f, w, h, seed = 100L, slot = it)
        }
        val second = (0 until VintagePrints.BACKDROP).map {
            VintagePrints.backdrop(it, 1.4f, w, h, seed = 200L, slot = it)
        }
        assertNotEquals(first, second)
    }

    // ------------------------------------------------------------------- blend

    @Test
    fun `the arriving print leads the one it replaces`() {
        // The reference does not dissolve evenly: the incoming print is already
        // opaque in places while the outgoing one is still a bright ghost.
        val start = VintagePrints.blend(0f)
        assertEquals(1f, start.outgoing, 0.001f)
        assertEquals(0f, start.incoming, 0.001f)
        assertEquals(0f, start.wash, 0.001f)

        val end = VintagePrints.blend(1f)
        assertEquals(0f, end.outgoing, 0.001f)
        assertEquals(1f, end.incoming, 0.001f)

        var previousIncoming = 0f
        var previousOutgoing = 1f
        for (i in 1..40) {
            val blend = VintagePrints.blend(i / 40f)
            assertTrue("incoming went backwards at $i", blend.incoming >= previousIncoming - 1e-4f)
            assertTrue("outgoing rose at $i", blend.outgoing <= previousOutgoing + 1e-4f)
            assertTrue("wash out of range at $i", blend.wash in 0f..0.8f)
            assertTrue("incoming out of range at $i", blend.incoming in 0f..1f)
            previousIncoming = blend.incoming
            previousOutgoing = blend.outgoing
        }

        // Leading means arriving early, not arriving first: at the moment the new
        // print is fully in, the old one is still on screen as a washed ghost. A
        // plain dissolve would not be finished with the old one until the end.
        val handover = (1..200).map { it / 200f }.first { VintagePrints.blend(it).incoming >= 0.999f }
        val at = VintagePrints.blend(handover)
        assertTrue("the new print only arrives at $handover", handover < 0.85f)
        // Still at full strength and visibly bleached: a photograph going white,
        // not a ghost fading into the dark stage.
        assertTrue("the outgoing print has faded too far: ${at.outgoing}", at.outgoing > 0.6f)
        assertTrue("the outgoing print is not washed: ${at.wash}", at.wash > 0.3f)
    }

    // ------------------------------------------------------------------- drift

    @Test
    fun `the drift is a creep, not a bob`() {
        // The reference drifts, so this theme drifts too - but a drift whose period
        // approaches a slide's length is a shake, and a shake reads as a rendering
        // fault. One second of drift must stay far below a pixel at 1080p.
        var previous = VintagePrints.drift(0L, w, h)
        for (ms in 1_000L..180_000L step 1_000L) {
            val now = VintagePrints.drift(ms, w, h)
            // The zoom follows the turn - the composition has to grow as it
            // turns, or the corners uncover - so the scale moves with it.
            assertTrue("scale jumped", abs(now.scale - previous.scale) < 0.008f)
            assertTrue("turn jumped", abs(now.angleDeg - previous.angleDeg) < 0.25f)
            assertTrue("drift jumped sideways", abs(now.dx - previous.dx) < 0.002f)
            assertTrue("drift jumped vertically", abs(now.dy - previous.dy) < 0.002f)
            assertTrue("scale out of range: ${now.scale}", now.scale in 1f..1.10f)
            assertTrue("turn out of range: ${now.angleDeg}", abs(now.angleDeg) <= 1.25f)
            assertTrue("offset out of range: ${now.dx}", abs(now.dx) < 0.01f)
            assertTrue("offset out of range: ${now.dy}", abs(now.dy) < 0.01f)
            previous = now
        }
    }

    @Test
    fun `the drift keeps moving and never repeats inside a slide`() {
        // A still composition would defeat the point; a repeating one inside a
        // single hold would look like a loop.
        val start = VintagePrints.drift(0L, w, h)
        val middle = VintagePrints.drift(4_000L, w, h)
        val later = VintagePrints.drift(8_000L, w, h)
        assertNotEquals(start, middle)
        assertNotEquals(middle, later)
    }

    @Test
    fun `the drift actually turns the composition`() {
        // The reference rotates the whole screen, not only pans and pushes. A
        // drift that never leaves zero would pass every other test here.
        val angles = (0L..40_000L step 1_000L).map { VintagePrints.drift(it, w, h).angleDeg }
        assertTrue("never turned one way", angles.any { it > 0.5f })
        assertTrue("never turned the other", angles.any { it < -0.5f })
        assertTrue("turn out of range", angles.all { abs(it) <= 1.25f })
    }

    @Test
    fun `a turned composition still covers the frame`() {
        // This is why the zoom and the turn are one motion: turning a full-frame
        // composition uncovers its corners, and a bare corner of stage on this
        // theme reads as a broken render. Every corner of the frame has to stay
        // inside the turned, scaled composition.
        for (ms in 0L..200_000L step 500L) {
            val d = VintagePrints.drift(ms, w, h)
            assertTrue("at ${ms}ms the frame is not covered (scale ${d.scale}, turn ${d.angleDeg})", d.covers(w, h))
            val rad = Math.toRadians(d.angleDeg.toDouble())
            val cs = Math.cos(rad).toFloat()
            val sn = Math.sin(rad).toFloat()
            // The composition's own half extents after scaling and turning.
            val hw = (w / 2f) * d.scale
            val hh = (h / 2f) * d.scale
            val spanX = abs(hw * cs) + abs(hh * sn)
            val spanY = abs(hw * sn) + abs(hh * cs)
            // Pan must not push the composition off the frame either.
            assertTrue(
                "at ${ms}ms the composition is off the frame sideways",
                spanX - abs(d.dx) * w >= w / 2f - 0.5f,
            )
            assertTrue(
                "at ${ms}ms the composition is off the frame vertically",
                spanY - abs(d.dy) * h >= h / 2f - 0.5f,
            )
        }
    }

    @Test
    fun `a portrait screen is covered too`() {
        // The same drift has to work on a 16:10 TV panel or a portrait phone, so
        // the overscan is computed from the frame, not hard-coded for 16:9.
        for ((fw, fh) in listOf(1920f to 1080f, 1280f to 720f, 1080f to 1920f)) {
            for (ms in 0L..120_000L step 2_000L) {
                val d = VintagePrints.drift(ms, fw, fh)
                assertTrue("$fw x $fh not covered at ${ms}ms", d.covers(fw, fh))
            }
        }
    }

    /** Is (x, y) inside this tilted print? */
    private fun contains(print: VintagePrints.Print, x: Float, y: Float): Boolean {
        val rad = Math.toRadians(print.angleDeg.toDouble())
        val dx = x - print.centerX
        val dy = y - print.centerY
        val localX = dx * cos(rad).toFloat() + dy * sin(rad).toFloat()
        val localY = -dx * sin(rad).toFloat() + dy * cos(rad).toFloat()
        return abs(localX) <= print.width / 2f && abs(localY) <= print.height / 2f
    }
}
