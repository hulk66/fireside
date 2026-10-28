package org.himstedt.immichslideshow.slideshow

/** How long one photo is held and how long the blend to the next one takes. */
data class SlideshowTiming(
    val holdMs: Long,
    val transitionMs: Long,
) {
    init {
        require(holdMs > 0) { "holdMs must be positive" }
        require(transitionMs > 0) { "transitionMs must be positive" }
    }

    val cycleMs: Long get() = holdMs + transitionMs

    companion object {
        const val MIN_TRANSITION_MS = 550L
        const val MAX_TRANSITION_MS = 2_800L

        fun forEffect(
            effect: SlideshowEffect,
            secondsPerPhoto: Float,
            paceMultiplier: Float = 1f,
        ): SlideshowTiming {
            val pace = if (paceMultiplier <= 0f) 1f else paceMultiplier
            val hold = (secondsPerPhoto * 1_000f / pace).toLong().coerceAtLeast(1_500L)
            val transition = (hold * effect.transitionFraction).toLong()
                .coerceIn(MIN_TRANSITION_MS, MAX_TRANSITION_MS)
            return SlideshowTiming(hold, transition)
        }
    }
}

/**
 * Maps a monotonically increasing elapsed time to "which photo is on screen and
 * how far into the blend are we". Pure, so it is unit tested independently of
 * the renderer.
 */
class SlideshowTimeline(val timing: SlideshowTiming) {

    val cycleMs: Long get() = timing.cycleMs

    /** Elapsed time wrapped into the current cycle, always in [0, cycleMs). */
    fun cycleOffset(elapsedMs: Long): Long {
        val mod = elapsedMs % cycleMs
        return if (mod < 0) mod + cycleMs else mod
    }

    /** Index of the photo being shown at [elapsedMs], wrapped into [0, count). */
    fun indexAt(elapsedMs: Long, count: Int): Int {
        if (count <= 0) return 0
        val raw = floorDiv(elapsedMs, cycleMs)
        return floorMod(raw, count.toLong()).toInt()
    }

    /** 0 while holding, then ramps 0..1 across the transition. */
    fun transitionProgressAt(elapsedMs: Long): Float {
        val offset = cycleOffset(elapsedMs)
        if (offset <= timing.holdMs) return 0f
        val p = (offset - timing.holdMs).toFloat() / timing.transitionMs
        return p.coerceIn(0f, 1f)
    }

    /** 0..1 progress through the hold phase (1 once the blend has begun). */
    fun holdProgressAt(elapsedMs: Long): Float {
        val offset = cycleOffset(elapsedMs)
        return (offset.toFloat() / timing.holdMs).coerceIn(0f, 1f)
    }

    /** Elapsed time at which photo [index] has just appeared. */
    fun elapsedForSlide(index: Int): Long = index.toLong() * cycleMs

    /** True while the blend between [index] and the next photo is running. */
    fun isTransitioning(elapsedMs: Long): Boolean = transitionProgressAt(elapsedMs) > 0f

    companion object {
        fun floorDiv(a: Long, b: Long): Long {
            var q = a / b
            if ((a % b != 0L) && ((a < 0) != (b < 0))) q--
            return q
        }

        fun floorMod(a: Long, b: Long): Long {
            val m = a % b
            return if (m < 0) m + b else m
        }
    }
}
