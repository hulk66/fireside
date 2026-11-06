package org.himstedt.fireside.data

import org.himstedt.fireside.slideshow.SlideshowEffect
import org.himstedt.fireside.slideshow.SlideshowTiming

/** How a photo is laid into the 16:9 frame. */
enum class FitMode {
    /** Crop and scale so the picture covers the screen — no bars, some edges lost. */
    Fill,

    /** Original aspect ratio: the whole photo is visible, with bars around it. */
    Fit,
    ;

    val isFit: Boolean get() = this == Fit

    companion object {
        fun fromName(name: String?): FitMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Fill
    }
}

/** User-tunable slideshow behaviour, persisted between launches. */
data class SlideshowPrefs(
    val effect: SlideshowEffect = SlideshowEffect.KEN_BURNS,
    /** How long each photo stays on screen, in seconds. */
    val secondsPerPhoto: Int = 8,
    val shuffle: Boolean = false,
    val showCaption: Boolean = true,
    val vignette: Boolean = true,
    /** 0 = still, 1 = default drift, 2 = pronounced Ken Burns. */
    val panIntensity: Float = 1f,
    val fitMode: FitMode = FitMode.Fill,
) {
    val timing: SlideshowTiming
        get() = SlideshowTiming.forEffect(effect, secondsPerPhoto.toFloat())

    companion object {
        val Durations = listOf(5, 8, 12, 20)
    }
}
