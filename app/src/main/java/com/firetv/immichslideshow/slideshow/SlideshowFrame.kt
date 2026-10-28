package com.firetv.immichslideshow.slideshow

import androidx.compose.ui.graphics.ImageBitmap
import com.firetv.immichslideshow.data.Photo
import com.firetv.immichslideshow.data.SlideshowPrefs

/** Read-only access to the bitmaps the player currently has in memory. */
interface SlideshowBitmaps {
    fun full(index: Int): ImageBitmap?
    fun tile(index: Int): ImageBitmap?

    companion object {
        val Empty: SlideshowBitmaps = object : SlideshowBitmaps {
            override fun full(index: Int): ImageBitmap? = null
            override fun tile(index: Int): ImageBitmap? = null
        }
    }
}

/**
 * Everything a single rendered frame needs. Produced by the player once per
 * display frame and consumed by [SlideshowRenderer].
 */
data class SlideshowFrame(
    val effect: SlideshowEffect,
    val currentIndex: Int,
    val transition: Float,
    val hold: Float,
    /** 0..1 across the whole slide cycle (hold + transition). */
    val progress: Float,
    val elapsedMs: Long,
    val photos: List<Photo>,
    val prefs: SlideshowPrefs,
    val bitmaps: SlideshowBitmaps,
) {
    val count: Int get() = photos.size
    val hasPhotos: Boolean get() = photos.isNotEmpty()

    val currentPhoto: Photo? get() = photos.getOrNull(currentIndex)
    val nextIndex: Int
        get() = if (photos.isEmpty()) 0 else (currentIndex + 1) % photos.size
    val nextPhoto: Photo? get() = photos.getOrNull(nextIndex)

    /** Stable per-photo seed so a photo always performs the same motion. */
    val seed: Long get() = currentPhoto?.id?.hashCode()?.toLong() ?: 11L
    val nextSeed: Long get() = nextPhoto?.id?.hashCode()?.toLong() ?: 23L

    fun photoAt(offset: Int): Photo? {
        if (photos.isEmpty()) return null
        val n = photos.size
        return photos[(((currentIndex + offset) % n) + n) % n]
    }

    fun bitmapAt(offset: Int): ImageBitmap? =
        bitmaps.full((((currentIndex + offset) % photos.size) + photos.size) % photos.size)

    companion object {
        val Empty = SlideshowFrame(
            effect = SlideshowEffect.KEN_BURNS,
            currentIndex = 0,
            transition = 0f,
            hold = 0f,
            progress = 0f,
            elapsedMs = 0L,
            photos = emptyList(),
            prefs = SlideshowPrefs(),
            bitmaps = SlideshowBitmaps.Empty,
        )
    }
}

/** One cell of the Photo Grid mosaic at a given instant. */
data class GridTile(
    val slot: Int,
    val fromIndex: Int,
    val toIndex: Int,
    /** 0 = fully [fromIndex], 1 = fully [toIndex]. */
    val mix: Float,
)

object GridPlan {
    const val COLUMNS = 5
    const val ROWS = 3
    const val SLOTS = COLUMNS * ROWS

    /** How long a single tile waits before it swaps to the next photo. */
    const val TILE_CYCLE_MS = 12_000L

    private const val FADE_FRACTION = 0.22f

    /**
     * Tiles swap at staggered moments, so the mosaic is continuously alive
     * instead of flipping in one hard cut.
     */
    fun tiles(elapsedMs: Long, count: Int): List<GridTile> {
        if (count <= 0) return emptyList()
        val out = ArrayList<GridTile>(SLOTS)
        for (slot in 0 until SLOTS) {
            val offset = TILE_CYCLE_MS * slot / SLOTS
            val phase = (elapsedMs + offset).toDouble() / TILE_CYCLE_MS.toDouble()
            val generation = kotlin.math.floor(phase).toLong()
            val local = (phase - generation).toFloat()
            val fadeStart = 1f - FADE_FRACTION
            val rawMix = ((local - fadeStart) / FADE_FRACTION).coerceIn(0f, 1f)
            val from = SlideshowTimeline.floorMod(slot * 3L + generation, count.toLong()).toInt()
            val to = SlideshowTimeline.floorMod(slot * 3L + generation + 1L, count.toLong()).toInt()
            out.add(GridTile(slot, from, to, Easing.smoothstep(rawMix)))
        }
        return out
    }

    /** Photo indices the player should keep decoded for the grid. */
    fun prefetchIndices(elapsedMs: Long, count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val indices = LinkedHashSet<Int>()
        for (tile in tiles(elapsedMs, count)) {
            indices.add(tile.fromIndex)
            indices.add(tile.toIndex)
        }
        return indices.toList()
    }
}
