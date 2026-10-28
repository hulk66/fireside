package org.himstedt.immichslideshow.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {

    private val timing = SlideshowTiming(holdMs = 8_000, transitionMs = 2_000)
    private val timeline = SlideshowTimeline(timing)

    @Test
    fun `cycle is hold plus transition`() {
        assertEquals(10_000L, timeline.cycleMs)
    }

    @Test
    fun `hold phase reports zero transition progress`() {
        assertEquals(0f, timeline.transitionProgressAt(0), 0.0001f)
        assertEquals(0f, timeline.transitionProgressAt(7_999), 0.0001f)
        assertEquals(0f, timeline.transitionProgressAt(8_000), 0.0001f)
    }

    @Test
    fun `transition progress ramps across the blend`() {
        assertEquals(0.25f, timeline.transitionProgressAt(8_500), 0.0001f)
        assertEquals(0.5f, timeline.transitionProgressAt(9_000), 0.0001f)
        assertEquals(0.9995f, timeline.transitionProgressAt(9_999), 0.0001f)
    }

    @Test
    fun `transition progress never leaves the unit range`() {
        for (ms in -30_000L..30_000L step 137) {
            val p = timeline.transitionProgressAt(ms)
            assertTrue("progress out of range at $ms: $p", p in 0f..1f)
        }
    }

    @Test
    fun `hold progress saturates once the blend begins`() {
        assertEquals(0f, timeline.holdProgressAt(0), 0.0001f)
        assertEquals(0.5f, timeline.holdProgressAt(4_000), 0.0001f)
        assertEquals(1f, timeline.holdProgressAt(8_000), 0.0001f)
        assertEquals(1f, timeline.holdProgressAt(9_500), 0.0001f)
    }

    @Test
    fun `index advances once per cycle`() {
        assertEquals(0, timeline.indexAt(0, 5))
        assertEquals(0, timeline.indexAt(9_999, 5))
        assertEquals(1, timeline.indexAt(10_000, 5))
        assertEquals(3, timeline.indexAt(35_000, 5))
    }

    @Test
    fun `index wraps around the album`() {
        assertEquals(0, timeline.indexAt(50_000, 5))
        assertEquals(1, timeline.indexAt(60_000, 5))
    }

    @Test
    fun `negative time is handled without crashing`() {
        assertEquals(4, timeline.indexAt(-1, 5))
        assertEquals(4, timeline.indexAt(-10_000, 5))
        assertEquals(8_000L, timeline.cycleOffset(-2_000))
    }

    @Test
    fun `empty album is safe`() {
        assertEquals(0, timeline.indexAt(123_456, 0))
        assertEquals(0, timeline.indexAt(123_456, -4))
    }

    @Test
    fun `elapsedForSlide is the inverse of indexAt`() {
        for (i in 0 until 7) {
            assertEquals(i, timeline.indexAt(timeline.elapsedForSlide(i), 7))
        }
    }

    @Test
    fun `isTransitioning matches the blend window`() {
        assertTrue(!timeline.isTransitioning(1_000))
        assertTrue(timeline.isTransitioning(9_000))
    }

    @Test
    fun `floor helpers match kotlin semantics`() {
        assertEquals(-1L, SlideshowTimeline.floorDiv(-10L, 10L))
        assertEquals(-2L, SlideshowTimeline.floorDiv(-10L, 6L))
        assertEquals(-1L, SlideshowTimeline.floorDiv(-1L, 10L))
        assertEquals(2L, SlideshowTimeline.floorDiv(10L, 5L))
        assertEquals(0L, SlideshowTimeline.floorMod(-20L, 10L))
        assertEquals(9L, SlideshowTimeline.floorMod(-1L, 10L))
    }
}

class TimingTest {

    @Test
    fun `hold follows the requested seconds`() {
        val t = SlideshowTiming.forEffect(SlideshowEffect.CLASSIC, secondsPerPhoto = 8f)
        assertEquals(8_000L, t.holdMs)
        assertEquals(1_760L, t.transitionMs)
    }

    @Test
    fun `transition stays inside its bounds for every theme`() {
        for (effect in SlideshowEffect.entries) {
            for (seconds in listOf(1, 5, 8, 12, 20, 120)) {
                val t = SlideshowTiming.forEffect(effect, seconds.toFloat())
                assertTrue(
                    "${effect.name}@${seconds}s transition ${t.transitionMs} too short",
                    t.transitionMs >= SlideshowTiming.MIN_TRANSITION_MS,
                )
                assertTrue(
                    "${effect.name}@${seconds}s transition ${t.transitionMs} too long",
                    t.transitionMs <= SlideshowTiming.MAX_TRANSITION_MS,
                )
            }
        }
    }

    @Test
    fun `hold never drops below the floor`() {
        val t = SlideshowTiming.forEffect(SlideshowEffect.FOLD, secondsPerPhoto = 0.2f)
        assertEquals(1_500L, t.holdMs)
    }

    @Test
    fun `pace multiplier shortens the hold`() {
        val normal = SlideshowTiming.forEffect(SlideshowEffect.SLIDE, 8f, paceMultiplier = 1f)
        val fast = SlideshowTiming.forEffect(SlideshowEffect.SLIDE, 8f, paceMultiplier = 2f)
        assertEquals(normal.holdMs / 2, fast.holdMs)
    }

    @Test
    fun `invalid pace falls back to normal speed`() {
        val t = SlideshowTiming.forEffect(SlideshowEffect.SLIDE, 8f, paceMultiplier = 0f)
        assertEquals(8_000L, t.holdMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hold must be positive`() {
        SlideshowTiming(holdMs = 0, transitionMs = 500)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `transition must be positive`() {
        SlideshowTiming(holdMs = 1_000, transitionMs = 0)
    }

    @Test
    fun `prefs timing mirrors the effect`() {
        val prefs = org.himstedt.immichslideshow.data.SlideshowPrefs(
            effect = SlideshowEffect.SCRAPBOOK,
            secondsPerPhoto = 12,
        )
        assertEquals(12_000L, prefs.timing.holdMs)
        assertTrue(prefs.timing.transitionMs > 0)
    }
}

class EasingTest {

    @Test
    fun `curves are anchored at both ends`() {
        val curves = listOf(
            Easing::linear,
            Easing::smoothstep,
            Easing::easeInOutSine,
            Easing::easeInOutCubic,
            Easing::easeOutCubic,
            Easing::easeInCubic,
            Easing::easeOutQuint,
            { t: Float -> Easing.backOut(t) },
        )
        for (curve in curves) {
            assertEquals(0f, curve(0f), 0.001f)
            assertEquals(1f, curve(1f), 0.001f)
        }
    }

    @Test
    fun `curves clamp out-of-range input`() {
        assertEquals(0f, Easing.easeInOutCubic(-4f), 0.0001f)
        assertEquals(1f, Easing.easeInOutCubic(9f), 0.0001f)
    }

    @Test
    fun `easing curves are monotonic`() {
        val curves = listOf(
            Easing::smoothstep,
            Easing::easeInOutSine,
            Easing::easeInOutCubic,
            Easing::easeOutCubic,
            Easing::easeInCubic,
            Easing::easeOutQuint,
        )
        for (curve in curves) {
            var previous = -1f
            for (step in 0..100) {
                val value = curve(step / 100f)
                assertTrue("curve went backwards at $step", value >= previous - 0.0001f)
                previous = value
            }
        }
    }

    @Test
    fun `backOut overshoots then settles`() {
        var max = 0f
        for (step in 0..100) {
            max = maxOf(max, Easing.backOut(step / 100f))
        }
        assertTrue("backOut never overshot: $max", max > 1.01f)
    }

    @Test
    fun `lerp interpolates`() {
        assertEquals(2f, Easing.lerp(0f, 4f, 0.5f), 0.0001f)
        assertEquals(7f, Easing.lerp(7f, 7f, 0.3f), 0.0001f)
    }
}

class MotionTest {

    @Test
    fun `ken burns motion is deterministic per seed`() {
        val a = kenBurnsViewport(seed = 42L, t = 0.37f)
        val b = kenBurnsViewport(seed = 42L, t = 0.37f)
        assertEquals(a, b)
    }

    @Test
    fun `different seeds produce different moves`() {
        val a = kenBurnsViewport(seed = 1L, t = 0.5f)
        val b = kenBurnsViewport(seed = 2L, t = 0.5f)
        assertNotEquals(a, b)
    }

    @Test
    fun `ken burns always pushes in`() {
        for (seed in 0L until 200L) {
            for (t in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                val v = kenBurnsViewport(seed, t)
                assertTrue("zoom ${v.zoom} below 1 at seed $seed", v.zoom >= 1f)
            }
        }
    }

    @Test
    fun `viewport crop window stays inside the image`() {
        for (zoom in listOf(1f, 1.3f, 2f, 4f)) {
            for (fx in listOf(-3f, 0f, 0.5f, 1f, 7f)) {
                val v = Viewport(zoom, fx, fx).clamped()
                val half = 0.5f / v.zoom
                assertTrue(v.focusX >= half - 0.0001f)
                assertTrue(v.focusX <= 1f - half + 0.0001f)
                assertTrue(v.focusY >= half - 0.0001f)
                assertTrue(v.focusY <= 1f - half + 0.0001f)
            }
        }
    }

    @Test
    fun `zero intensity keeps the camera nearly still`() {
        val start = kenBurnsViewport(seed = 9L, t = 0f, intensity = 0f)
        val end = kenBurnsViewport(seed = 9L, t = 1f, intensity = 0f)
        assertEquals(1f, start.zoom, 0.0001f)
        assertEquals(1f, end.zoom, 0.0001f)
    }

    @Test
    fun `push in drifts gently`() {
        val v = pushInViewport(seed = 5L, t = 1f)
        assertTrue(v.zoom in 1f..1.2f)
        assertTrue(v.focusX in 0.4f..0.6f)
        assertTrue(v.focusY in 0.4f..0.6f)
    }

    @Test
    fun `stagger hands each item its own slice`() {
        assertEquals(0f, stagger(p = 0f, index = 0, count = 5), 0.0001f)
        assertEquals(0f, stagger(p = 0f, index = 4, count = 5), 0.0001f)
        assertEquals(1f, stagger(p = 1f, index = 4, count = 5), 0.0001f)
        // later items start later
        val first = stagger(0.45f, index = 0, count = 5)
        val last = stagger(0.45f, index = 4, count = 5)
        assertTrue("expected $first > $last", first > last)
    }

    @Test
    fun `stagger is safe for a single item`() {
        assertEquals(0.4f, stagger(0.4f, index = 0, count = 1), 0.0001f)
        assertEquals(1f, stagger(3f, index = 0, count = 1), 0.0001f)
    }

    @Test
    fun `tile exit directions point away from the middle`() {
        val left = tileExitDirection(seed = 3L, index = 0, cols = 5)
        val right = tileExitDirection(seed = 3L, index = 4, cols = 5)
        assertTrue(left.x < 0f)
        assertTrue(right.x > 0f)
    }

    @Test
    fun `photo rotation stays inside the requested range`() {
        for (index in 0 until 50) {
            val r = photoRotation(seed = 11L, index = index, maxDegrees = 6f)
            assertTrue(r in -6f..6f)
        }
    }

    @Test
    fun `photo offset stays inside the requested range`() {
        for (index in 0 until 50) {
            val o = photoOffset(seed = 11L, index = index, amount = 0.06f)
            assertTrue(o.x in -0.06f..0.06f)
            assertTrue(o.y in -0.06f..0.06f)
        }
    }
}

class RngTest {

    @Test
    fun `unit values are stable and in range`() {
        for (seed in listOf(-9L, -1L, 0L, 1L, 7L, Long.MAX_VALUE)) {
            for (salt in 0 until 20) {
                val u = Rng.unit(seed, salt)
                assertTrue("out of range: $u", u >= 0f && u < 1f)
                assertEquals(u, Rng.unit(seed, salt))
            }
        }
    }

    @Test
    fun `different salts diverge`() {
        val values = (0 until 16).map { Rng.unit(1234L, it) }.toSet()
        assertTrue("RNG produced only ${values.size} distinct values", values.size > 12)
    }

    @Test
    fun `range respects its bounds`() {
        for (salt in 0 until 50) {
            val v = Rng.range(88L, salt, -0.25f, 0.25f)
            assertTrue(v in -0.25f..0.25f)
        }
    }

    @Test
    fun `int respects its bound`() {
        for (salt in 0 until 50) {
            val v = Rng.int(88L, salt, 9)
            assertTrue("$v outside 0..8", v in 0..8)
        }
        assertEquals(0, Rng.int(88L, 1, 0))
        assertEquals(0, Rng.int(88L, 1, 1))
    }

    @Test
    fun `sign and bool are balanced enough`() {
        val signs = (0 until 200).map { Rng.sign(3L, it) }
        assertTrue(signs.all { it == -1f || it == 1f })
        assertTrue(signs.count { it < 0f } in 50..150)

        val bools = (0 until 200).map { Rng.bool(3L, it) }
        assertTrue(bools.count { it } in 50..150)
    }

    @Test
    fun `splitmix64 is a bijection-ish avalanche`() {
        val a = Rng.splitMix64(0L)
        val b = Rng.splitMix64(1L)
        assertNotEquals(a, b)
        assertNotEquals(0L, a)
    }
}

class SlideshowEffectTest {

    @Test
    fun `ten themes ship in the classic order`() {
        assertEquals(10, SlideshowEffect.entries.size)
        assertEquals(SlideshowEffect.CLASSIC, SlideshowEffect.entries.first())
        assertEquals(SlideshowEffect.PHOTO_GRID, SlideshowEffect.entries.last())
    }

    @Test
    fun `only photo grid is a mosaic`() {
        val mosaics = SlideshowEffect.entries.filter { it.mosaic }
        assertEquals(listOf(SlideshowEffect.PHOTO_GRID), mosaics)
    }

    @Test
    fun `transition fractions are sane`() {
        for (effect in SlideshowEffect.entries) {
            assertTrue("${effect.name} fraction too small", effect.transitionFraction > 0f)
            assertTrue("${effect.name} fraction too large", effect.transitionFraction < 0.5f)
            assertTrue(effect.title.isNotBlank())
            assertTrue(effect.tagline.isNotBlank())
        }
    }

    @Test
    fun `fromName is case insensitive and falls back to ken burns`() {
        assertEquals(SlideshowEffect.SHIFTING_TILES, SlideshowEffect.fromName("shifting_tiles"))
        assertEquals(SlideshowEffect.CLASSIC, SlideshowEffect.fromName("Classic"))
        assertEquals(SlideshowEffect.KEN_BURNS, SlideshowEffect.fromName("nope"))
        assertEquals(SlideshowEffect.KEN_BURNS, SlideshowEffect.fromName(null))
    }

    @Test
    fun `every number key maps to a theme`() {
        assertEquals(10, SlideshowEffect.ordered.size)
    }
}
