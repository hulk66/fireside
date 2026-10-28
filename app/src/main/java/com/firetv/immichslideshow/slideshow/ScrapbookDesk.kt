package com.firetv.immichslideshow.slideshow

/**
 * The scrapbook desk.
 *
 * Photos pile up on the table as the show advances: the newest card lands on top
 * of the pile, and every card keeps the place it was given when it arrived (the
 * spot is derived from the photo id, so a card never wanders while it is on
 * screen). Once the desk is full the oldest card slides off the bottom of the
 * pile.
 */
object ScrapbookDesk {

    /** Photos on the desk at once — a pile, not a slideshow. */
    const val CARDS = 20

    /** Captions stay legible on the few cards that are not buried. */
    const val CAPTIONED = 3

    /** Where one card sits, in fractions of the frame. */
    data class Placement(
        val cx: Float,
        val cy: Float,
        /** Card width as a fraction of the frame width. */
        val width: Float,
        val rotation: Float,
    )

    /** Photo indices on the desk, newest first. */
    fun visible(index: Int, count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val cards = minOf(count, CARDS)
        return List(cards) { step -> Math.floorMod(index - step, count) }
    }

    /** The card that drops off the desk when [index] arrives, if the desk is full. */
    fun leaving(index: Int, count: Int): Int? =
        if (count > CARDS) Math.floorMod(index - CARDS, count) else null

    /**
     * A stable, scattered place for one photo: wide enough to read, spread over
     * the whole desk, and never hanging off an edge.
     */
    fun placement(photoId: String, aspect: Float, frameAspect: Float): Placement {
        val seed = photoId.hashCode().toLong()
        val safeAspect = if (aspect.isFinite() && aspect > 0f) aspect.coerceIn(0.7f, 2.0f) else 1.4f
        val width = Rng.range(seed, 720, 0.155f, 0.265f)
        // Same proportions drawPolaroid uses: 5% mat each side, 13% caption strip.
        val cardAspect = 1.034f / safeAspect + 0.10f
        val heightFraction = (width * frameAspect * cardAspect).coerceAtMost(0.42f)

        val halfW = width / 2f
        val halfH = heightFraction / 2f
        val cx = Rng.range(seed, 700, halfW + 0.025f, 1f - halfW - 0.025f)
        // Leave the bottom strip clear of the playback chrome.
        val cy = Rng.range(seed, 710, halfH + 0.035f, 1f - halfH - 0.06f)
        val rotation = Rng.range(seed, 730, -9f, 9f)
        return Placement(cx, cy, width, rotation)
    }
}
