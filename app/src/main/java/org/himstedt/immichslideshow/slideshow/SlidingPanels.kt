package org.himstedt.immichslideshow.slideshow

/**
 * One panel of the wall: the photo it is showing and the one sliding in.
 *
 * A change is a *chase*: the arriving photo pushes the leaving one out of the
 * panel, so the panel is never empty and no black shows. The reference lets its
 * panels go black between photos; here the two photos are always adjacent, which
 * is what makes the wall read as panels sliding rather than as pictures blinking.
 */
data class PanelTurn(val slot: Int, val from: Int, val to: Int) {
    val turns: Boolean get() = from != to
}

/** The wall at one step of one cycle. */
data class PanelStep(
    val cycle: Long,
    /** Panels in this cycle's wall: 4, 5 or 6. */
    val panels: Int,
    /** Step within the cycle, 0 until [panels]; equal to [panels] is the exchange. */
    val index: Int,
    /** True on the step that replaces the whole wall. */
    val exchange: Boolean,
    /** How far the slide itself has got, 0..1. Zero through the dwell. */
    val slide: Float,
    /** +1 when the photos travel right, -1 when they travel left. */
    val direction: Int,
    /** What each panel holds before this step's turn. */
    val before: List<Int>,
    /** What each panel holds after it. Equal to [before] where nothing turns. */
    val photos: List<Int>,
    /** For the exchange: the arriving wall's panel count and photos. */
    val arrivingPanels: Int,
    val arrivingPhotos: List<Int>,
) {
    /** The panel turning on this step, or -1 when the whole wall turns. */
    val turningSlot: Int
        get() = if (exchange) -1 else before.indices.firstOrNull { before[it] != photos[it] } ?: -1
}

/**
 * Sliding Panels: a wall of four to six photos whose panels slide their photo out
 * and the next one in.
 *
 * A cycle is one wall. Each panel changes **once**, one at a time, [STEP_MS] apart
 * — so a wall of four takes ten seconds to have every panel changed once — and
 * then the whole wall is exchanged for the next one, all panels at once, with a
 * new cut and new photos. Both rhythms the reference uses, in one cycle.
 *
 * Photos are consumed in album order and never repeated inside a cycle: the wall
 * opens with the first `panels` photos, each panel's turn brings in the photo
 * `panels` places further on, and the exchange brings in the next cycle's opening
 * photos. So every photo is shown exactly once per pass through the album.
 *
 * Everything is derived from the clock rather than accumulated, so the renderer
 * stays stateless and any moment can be drawn on its own.
 */
object SlidingPanels {

    /** How long one photo stays up before its panel slides it out. */
    const val STEP_MS = 2_500L

    /** Share of a step the slide itself takes; the rest is the photo at rest. */
    private const val SLIDE = 0.45f

    private const val MIN_PANELS = 4
    private const val MAX_PANELS = 6
    private const val PATTERN = 3L

    /** One turn of the whole pattern: a 4, a 5 and a 6 panel cycle. */
    private val GROUP_MS = (MIN_PANELS + 1 + MIN_PANELS + 2 + MIN_PANELS + 3) * STEP_MS

    /** Panels in [cycle]'s wall: 4, 5 and 6, in rotation. */
    fun panelsFor(cycle: Long): Int =
        MIN_PANELS + Math.floorMod(cycle, PATTERN).toInt()

    /** Steps in [cycle]: one per panel, plus the exchange. */
    fun stepsIn(cycle: Long): Int = panelsFor(cycle) + 1

    fun cycleMs(cycle: Long): Long = stepsIn(cycle) * STEP_MS

    /**
     * Photos consumed by every cycle before [cycle].
     *
     * A cycle consumes two panels' worth per panel: the opening photos, then the
     * ones the single turns bring in — and the exchange brings in the next cycle's
     * opening photos, which are already counted there.
     */
    fun photosBefore(cycle: Long): Long {
        val c = cycle.coerceAtLeast(0L)
        val q = c / PATTERN
        val r = c % PATTERN
        // sum of (MIN_PANELS + j % PATTERN) for j < c
        val panels = MIN_PANELS * c + PATTERN * q * (q - 1) / 2 + q * r
        return 2L * panels
    }

    /** Which way the photos travel on a step: randomised, but the same every run. */
    fun directionFor(cycle: Long, index: Int): Int =
        if (Rng.unit(cycle * 31L + index, 7) < 0.5f) 1 else -1

    /** The wall at [elapsedMs]. */
    fun stepAt(elapsedMs: Long, count: Int): PanelStep {
        if (count <= 0) return emptyStep()
        val t = elapsedMs.coerceAtLeast(0L)
        val group = t / GROUP_MS
        var rest = t % GROUP_MS
        var cycle = group * PATTERN
        while (rest >= cycleMs(cycle)) {
            rest -= cycleMs(cycle)
            cycle++
        }
        val panels = panelsFor(cycle)
        val index = (rest / STEP_MS).toInt().coerceIn(0, panels)
        val within = (rest - index * STEP_MS).toFloat() / STEP_MS
        // The photo rests first and slides at the end of the step, so each photo is
        // up for its full step rather than only for the tail of it.
        val slide = ((within - (1f - SLIDE)) / SLIDE).coerceIn(0f, 1f)
        val base = photosBefore(cycle)
        val direction = directionFor(cycle, index)
        val exchange = index >= panels

        if (exchange) {
            // Every panel has turned once; now the whole wall goes and the next one
            // arrives, all at once, with its own cut.
            val leaving = List(panels) { k -> wrap(base + panels + k, count) }
            val nextCycle = cycle + 1
            val arrivingPanels = panelsFor(nextCycle)
            val arriving = List(arrivingPanels) { k -> wrap(photosBefore(nextCycle) + k, count) }
            return PanelStep(
                cycle = cycle,
                panels = panels,
                index = index,
                exchange = true,
                slide = slide,
                direction = direction,
                before = leaving,
                photos = leaving,
                arrivingPanels = arrivingPanels,
                arrivingPhotos = arriving,
            )
        }

        val before = List(panels) { k -> wrap(base + k + if (k < index) panels else 0, count) }
        val after = List(panels) { k -> wrap(base + k + if (k <= index) panels else 0, count) }
        return PanelStep(
            cycle = cycle,
            panels = panels,
            index = index,
            exchange = false,
            slide = slide,
            direction = direction,
            before = before,
            photos = after,
            arrivingPanels = 0,
            arrivingPhotos = emptyList(),
        )
    }

    /**
     * Photos the player must keep decoded: this step and the next, which covers
     * both photos of a turning panel and both walls of an exchange.
     */
    fun prefetch(elapsedMs: Long, count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val indices = LinkedHashSet<Int>()
        val step = stepAt(elapsedMs, count)
        indices.addAll(step.before)
        indices.addAll(step.photos)
        if (step.exchange) indices.addAll(step.arrivingPhotos)
        // The step after this one: a slide is only smooth if the photo it brings in
        // is already decoded when it starts.
        val next = stepAt(elapsedMs + STEP_MS, count)
        indices.addAll(next.before)
        indices.addAll(next.photos)
        return indices.toList()
    }

    private fun wrap(index: Long, count: Int): Int = Math.floorMod(index, count.toLong()).toInt()

    private fun emptyStep() = PanelStep(
        cycle = 0L,
        panels = 0,
        index = 0,
        exchange = false,
        slide = 0f,
        direction = 1,
        before = emptyList(),
        photos = emptyList(),
        arrivingPanels = 0,
        arrivingPhotos = emptyList(),
    )
}
