package org.himstedt.fireside.slideshow

/** Small, allocation-free easing curves used by the renderer. */
object Easing {

    fun linear(t: Float): Float = t.coerceIn(0f, 1f)

    fun smoothstep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    fun easeInOutSine(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return 0.5f - 0.5f * kotlin.math.cos(Math.PI.toFloat() * x)
    }

    fun easeInOutCubic(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f
    }

    fun easeOutCubic(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return 1f - (1f - x) * (1f - x) * (1f - x)
    }

    fun easeInCubic(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * x
    }

    fun easeOutQuint(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return 1f - (1f - x) * (1f - x) * (1f - x) * (1f - x) * (1f - x)
    }

    /** Overshoots slightly past the target then settles — used for scrapbook drops. */
    fun backOut(t: Float, overshoot: Float = 1.4f): Float {
        val x = t.coerceIn(0f, 1f)
        val c3 = overshoot + 1f
        return 1f + c3 * (x - 1f) * (x - 1f) * (x - 1f) + overshoot * (x - 1f) * (x - 1f)
    }

    /**
     * A card falling onto a pile: it accelerates like gravity, lands a little
     * past the mark, then bounces back up to rest. 0 is "still falling", 1 is
     * "settled"; the curve overshoots [bounce] at the moment of impact.
     */
    fun dropIn(t: Float, bounce: Float = 0.07f): Float {
        val x = t.coerceIn(0f, 1f)
        val land = 0.72f
        return if (x < land) {
            val p = x / land
            p * p * (1f + bounce)
        } else {
            val p = easeOutCubic((x - land) / (1f - land))
            1f + bounce * (1f - p)
        }
    }

    fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
}
