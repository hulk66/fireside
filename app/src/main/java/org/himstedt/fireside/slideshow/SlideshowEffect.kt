package org.himstedt.fireside.slideshow

/**
 * The ten slideshow themes, modelled on the classic Apple TV photo themes.
 *
 * [transitionFraction] is the share of one slide cycle that is spent blending
 * into the next photo (the rest is the "hold" where the photo simply breathes).
 * [mosaic] themes ignore the single-photo timeline and paint a whole wall of
 * photos instead.
 */
enum class SlideshowEffect(
    val title: String,
    val tagline: String,
    val transitionFraction: Float,
    val mosaic: Boolean = false,
    /**
     * How long one page of a mosaic stays up. Only the walls use it; Sliding
     * Panels runs on panels, so its page is a couple of seconds rather than the
     * sixteen a dense wall of photos needs.
     */
    val pageMs: Long = GridPlan.PAGE_MS,
) {
    CLASSIC(
        title = "Classic",
        tagline = "A slow cross-dissolve with a gentle push-in",
        transitionFraction = 0.22f,
    ),
    KEN_BURNS(
        title = "Ken Burns",
        tagline = "A roving pan and zoom across every frame",
        transitionFraction = 0.20f,
    ),
    SLIDE(
        title = "Slide",
        tagline = "Frames glide past each other, edge to edge",
        transitionFraction = 0.22f,
    ),
    FLIP(
        title = "Flip",
        tagline = "Cards turn over on a shared vertical axis",
        transitionFraction = 0.24f,
    ),
    FOLD(
        title = "Fold",
        tagline = "The frame folds away in vertical panels",
        transitionFraction = 0.26f,
    ),
    SHIFTING_TILES(
        title = "Shifting Tiles",
        tagline = "A grid of tiles shuffles into the next photo",
        transitionFraction = 0.28f,
    ),
    SLIDING_PANELS(
        title = "Sliding Panels",
        tagline = "Panels slide their photo out and the next one in",
        transitionFraction = 0.2f,
        mosaic = true,
        pageMs = SlidingPanels.STEP_MS,
    ),
    REFLECTIONS(
        title = "Reflections",
        tagline = "A floating photo and its mirrored echo on dark water",
        transitionFraction = 0.22f,
    ),
    SCRAPBOOK(
        title = "Scrapbook",
        tagline = "Polaroids settle onto a paper desk",
        transitionFraction = 0.26f,
    ),
    VINTAGE_PRINTS(
        title = "Vintage Prints",
        tagline = "One print on a pile of others, under glass",
        // A second of dissolve on an eight second hold: long enough to read as a
        // wash rather than as a cut, short enough not to feel stalled.
        transitionFraction = 0.125f,
    ),
    ORIGAMI(
        title = "Origami",
        tagline = "A wall of photos turns over like sheets of paper",
        transitionFraction = 0.28f,
        mosaic = true,
    ),
    PHOTO_GRID(
        title = "Photo Grid",
        tagline = "A living mosaic of the whole album",
        transitionFraction = 0.01f,
        mosaic = true,
    ),
    ;

    companion object {
        /** Display order, matching the classic Apple TV theme picker. */
        val ordered: List<SlideshowEffect> = entries.toList()

        fun fromName(name: String?): SlideshowEffect =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: KEN_BURNS
    }
}
