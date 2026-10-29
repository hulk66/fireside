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
    fun `the front print may bleed off the frame but stays the subject`() {
        // The reference lets a print run off the bottom of the screen, which is
        // what makes it read as a print larger than the window. So the print is no
        // longer required to fit - but it may never be mostly off-frame.
        var overflowing = 0
        for (aspect in listOf(0.3f, 0.5f, 0.66f, 1f, 1.5f, 1.78f, 2.4f, 3.6f)) {
            for (seed in seeds()) {
                val print = VintagePrints.front(0, aspect, w, h, seed)
                // Spanning most of the frame in one direction is what keeps it the
                // subject; a share of the *area* would be unfair to narrow prints,
                // which are slim however tall they are.
                val span = maxOf(print.width / w, print.height / h)
                assertTrue("$aspect at seed $seed spans only ${(span * 100).toInt()}%", span >= 0.75f)
                assertTrue(
                    "$aspect at seed $seed is a sliver",
                    VintagePrints.coverage(print, w, h) > 0.10f,
                )
                // Near the middle of the screen, not anywhere on it: a print
                // pinned to a corner reads as a layout rather than as a print put
                // down by hand.
                assertTrue(
                    "$aspect at seed $seed sits too far off centre: ${print.centerX}",
                    print.centerX in w * 0.35f..w * 0.65f,
                )
                assertTrue(
                    "$aspect at seed $seed sits too far off centre: ${print.centerY}",
                    print.centerY in h * 0.35f..h * 0.65f,
                )
                val past = print.centerX - print.halfWidth < -0.5f ||
                    print.centerX + print.halfWidth > w + 0.5f ||
                    print.centerY - print.halfHeight < -0.5f ||
                    print.centerY + print.halfHeight > h + 0.5f
                if (past) overflowing++
            }
        }
        assertTrue("no print ever runs off the edge", overflowing > 0)
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
    fun `the camera never reverses while a photo is up`() {
        // This is the pumping bug, as a test. The drift is a one-way ramp across
        // the hold: every channel has to move the same direction from the first
        // frame to the last, or the picture appears to breathe in and out.
        var previous = VintagePrints.drift(0f, w, h)
        val first = previous
        for (i in 1..200) {
            val now = VintagePrints.drift(i / 200f, w, h)
            assertTrue("turn reversed at ${i / 200f}", now.angleDeg > previous.angleDeg)
            assertTrue("zoom reversed at ${i / 200f}", now.scale > previous.scale - 1e-5f)
            assertTrue("pan reversed at ${i / 200f}", now.dx > previous.dx)
            assertTrue("pan reversed at ${i / 200f}", now.dy > previous.dy)
            previous = now
        }
        // And it really does travel, rather than sitting still.
        assertTrue("the turn barely moved", previous.angleDeg - first.angleDeg > 1.0f)
        assertTrue("the zoom barely moved", previous.scale - first.scale > 0.01f)
    }

    @Test
    fun `the drift is a creep, not a bob`() {
        // A ramp, not a shake: no frame-to-frame jump anywhere, at 1080p.
        var previous = VintagePrints.drift(0f, w, h)
        for (i in 1..200) {
            val now = VintagePrints.drift(i / 200f, w, h)
            assertTrue("turn jumped", abs(now.angleDeg - previous.angleDeg) < 0.05f)
            assertTrue("scale jumped", abs(now.scale - previous.scale) < 0.002f)
            assertTrue("pan jumped", abs(now.dx - previous.dx) < 0.001f)
            assertTrue("scale out of range: ${now.scale}", now.scale in 1f..1.10f)
            assertTrue("turn out of range: ${now.angleDeg}", abs(now.angleDeg) <= 1.3f)
            previous = now
        }
    }

    @Test
    fun `the drift keeps moving and never repeats inside a slide`() {
        val start = VintagePrints.drift(0f, w, h)
        val middle = VintagePrints.drift(0.5f, w, h)
        val later = VintagePrints.drift(1f, w, h)
        assertNotEquals(start, middle)
        assertNotEquals(middle, later)
    }

    @Test
    fun `a photo's camera runs one way and then stops`() {
        // One-way across the hold, and frozen at the far end through the fade. It
        // never comes back: the photo arriving is a different print with its own
        // camera, and the cross-fade bridges the two framings. Any return is a
        // visible swing, which is the pumping this had.
        assertEquals("the hold should start at one end", 0f, VintagePrints.phase(0f, 0f), 1e-4f)
        assertEquals("the hold should end at the other", 1f, VintagePrints.phase(1f, 0f), 1e-4f)
        var previous = VintagePrints.phase(0f, 0f)
        for (i in 1..50) {
            val now = VintagePrints.phase(i / 50f, 0f)
            assertTrue("the hold reversed at $i", now >= previous)
            previous = now
        }
        // And through the whole fade it does not move at all.
        for (i in 0..50) {
            assertEquals(
                "the camera moved during the fade at $i",
                1f,
                VintagePrints.phase(1f, i / 50f),
                1e-4f,
            )
        }
    }

    @Test
    fun `the arriving photo starts its own run at the beginning`() {
        // The incoming composition is drawn at phase 0 for the whole fade, so its
        // own hold can start from there without a jump.
        val arriving = VintagePrints.drift(0f, w, h, VintagePrints.directionFor(1))
        val firstFrameOfItsHold =
            VintagePrints.drift(VintagePrints.phase(0f, 0f), w, h, VintagePrints.directionFor(1))
        assertEquals(arriving, firstFrameOfItsHold)
    }

    @Test
    fun `the direction alternates from photo to photo`() {
        // Every photo turning the same way made it feel like a turntable.
        val directions = (0..5).map { VintagePrints.directionFor(it) }
        assertEquals(listOf(1f, -1f, 1f, -1f, 1f, -1f), directions)

        // One photo turns one way across its hold, the next the other way.
        val first = VintagePrints.drift(1f, w, h, VintagePrints.directionFor(0)).angleDeg -
            VintagePrints.drift(0f, w, h, VintagePrints.directionFor(0)).angleDeg
        val second = VintagePrints.drift(1f, w, h, VintagePrints.directionFor(1)).angleDeg -
            VintagePrints.drift(0f, w, h, VintagePrints.directionFor(1)).angleDeg
        assertTrue("the first photo should turn one way", abs(first) > 1f)
        assertTrue("the second photo should turn the other way", first * second < 0f)
    }

    @Test
    fun `each photo hands over to the next without a shift in framing`() {
        // Because the travel runs end to end and the direction alternates, the
        // framing a photo finishes on is exactly the framing the next one starts
        // on - so the cross-fade between them is a pure dissolve, with the print
        // not shifting as the photo changes.
        for (index in 0..7) {
            val next = index + 1
            val leaving = VintagePrints.drift(1f, w, h, VintagePrints.directionFor(index))
            val arriving = VintagePrints.drift(0f, w, h, VintagePrints.directionFor(next))
            assertEquals("turn shifts at photo $index", leaving.angleDeg, arriving.angleDeg, 1e-3f)
            assertEquals("scale shifts at photo $index", leaving.scale, arriving.scale, 1e-4f)
            assertEquals("pan shifts at photo $index", leaving.dx, arriving.dx, 1e-4f)
            assertEquals("pan shifts at photo $index", leaving.dy, arriving.dy, 1e-4f)
        }
    }

    @Test
    fun `nothing reverses during a whole cycle`() {
        // Walk a cycle the way the renderer does - hold then fade - and assert the
        // camera never turns round. This is the pumping, as a test: the old
        // implementation returned to its start during the fade, so the motion went
        // forward, back, then forward again.
        val steps = 60
        var previous = VintagePrints.phase(0f, 0f)
        for (i in 1..steps) {
            val hold = i.toFloat() / steps
            val now = VintagePrints.phase(hold, 0f)
            assertTrue("the camera reversed inside the hold at $i", now >= previous)
            previous = now
        }
        for (i in 1..steps) {
            val now = VintagePrints.phase(1f, i.toFloat() / steps)
            assertTrue("the camera moved during the fade at $i", now >= previous - 1e-5f)
            previous = now
        }
    }

    @Test
    fun `a turned composition still covers the frame`() {
        // This is why the zoom and the turn are one motion: turning a full-frame
        // composition uncovers its corners, and a bare corner of stage on this
        // theme reads as a broken render. Every corner of the frame has to stay
        // inside the turned, scaled composition.
        for (i in 0..200) {
            val d = VintagePrints.drift(i / 200f, w, h)
            assertTrue("at $i the frame is not covered (scale ${d.scale}, turn ${d.angleDeg})", d.covers(w, h))
            val rad = Math.toRadians(d.angleDeg.toDouble())
            val cs = Math.cos(rad).toFloat()
            val sn = Math.sin(rad).toFloat()
            val hw = (w / 2f) * d.scale
            val hh = (h / 2f) * d.scale
            val spanX = abs(hw * cs) + abs(hh * sn)
            val spanY = abs(hw * sn) + abs(hh * cs)
            assertTrue(
                "at $i the composition is off the frame sideways",
                spanX - abs(d.dx) * w >= w / 2f - 0.5f,
            )
            assertTrue(
                "at $i the composition is off the frame vertically",
                spanY - abs(d.dy) * h >= h / 2f - 0.5f,
            )
        }
    }

    @Test
    fun `a portrait screen is covered too`() {
        // The same drift has to work on a 16:10 TV panel or a portrait phone, so
        // the overscan is computed from the frame, not hard-coded for 16:9.
        for ((fw, fh) in listOf(1920f to 1080f, 1280f to 720f, 1080f to 1920f)) {
            for (i in 0..100) {
                val d = VintagePrints.drift(i / 100f, fw, fh)
                assertTrue("$fw x $fh not covered at $i", d.covers(fw, fh))
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
