package com.firetv.immichslideshow.slideshow

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

    fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
}
