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
    /** Photos on the Photo Grid wall; sized to the album's shapes by the player. */
    val gridSlots: Int = GridPlan.SLOTS,
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

/** One page of the mosaic: which photos are on the wall and how they fade in. */
data class GridPage(
    val generation: Long,
    /** Photo index for each slot of the justified layout, left to right. */
    val indices: List<Int>,
    val tiles: List<GridTile>,
)

/**
 * Drives the Photo Grid mosaic in pages.
 *
 * A page is a fixed window of photos laid out by [JustifiedGrid]; the tiles flip
 * over once per page at staggered moments, so the wall is alive without any of
 * the per-tile churn that made it flicker. Keeping the window stable for a whole
 * page also keeps the layout stable, which is what a justified grid needs.
 */
object GridPlan {
    /** Fallback page size when the album's shapes are unknown. */
    const val SLOTS = 15

    /**
     * Photos per page, from the album's shapes.
     *
     * Three rows of photos fill a 16:9 frame when the page holds
     * `3 · 3 · (16/9) / aspect` of them, i.e. `16 / aspect` — a 4:3 album wants
     * twelve, a 3:4 album twenty, a mix of both fifteen. Sizing the page to the
     * album is what keeps the mosaic from having to be scaled (and cropped) hard:
     * a mismatch here is what cuts whole cells in half.
     */
    fun slotsFor(aspects: List<Float>): Int {
        if (aspects.isEmpty()) return SLOTS
        val usable = aspects.filter { it.isFinite() && it > 0.05f }
        if (usable.isEmpty()) return SLOTS
        val average = (usable.sumOf { it.toDouble() } / usable.size).toFloat().coerceIn(0.7f, 2.0f)
        return (16f / average).toInt().coerceIn(9, 20)
    }

    /** How long one page of photos stays up. */
    const val PAGE_MS = 16_000L

    /** Share of a page over which the tiles flip, first to last. */
    private const val STAGGER = 0.30f

    /** How long one tile takes to cross-fade, as a share of a page. */
    private const val FADE = 0.075f

    /** Photo indices for one page, wrapping around the album. */
    fun indicesFor(generation: Long, count: Int, slots: Int = SLOTS): List<Int> {
        if (count <= 0 || slots <= 0) return emptyList()
        val start = Math.floorMod(generation * slots, count.toLong())
        return List(slots) { slot -> ((start + slot) % count).toInt() }
    }

    /** The wall at [elapsedMs]: which page, which photos, and how far each fade is. */
    fun pageAt(elapsedMs: Long, count: Int, slots: Int = SLOTS): GridPage {
        if (count <= 0 || slots <= 0) return GridPage(0L, emptyList(), emptyList())
        val generation = Math.floorDiv(elapsedMs, PAGE_MS)
        val local = ((elapsedMs - generation * PAGE_MS).toFloat() / PAGE_MS).coerceIn(0f, 1f)
        val indices = indicesFor(generation, count, slots)
        val previous = indicesFor(generation - 1, count, slots)

        val first = generation <= 0L
        val tiles = indices.mapIndexed { slot, index ->
            val start = STAGGER * slot / slots
            val raw = ((local - start) / FADE).coerceIn(0f, 1f)
            // The first page has nothing to fade from.
            val mix = if (first) 1f else Easing.smoothstep(raw)
            val from = if (first) index else previous.getOrElse(slot) { index }
            GridTile(slot, from, index, mix)
        }
        return GridPage(generation, indices, tiles)
    }

    /**
     * Photos the player should keep decoded: this page and the next one, which is
     * all a page ever needs — plus the previous page still on screen while the
     * tiles flip over.
     */
    fun prefetchIndices(elapsedMs: Long, count: Int, slots: Int = SLOTS): List<Int> {
        if (count <= 0 || slots <= 0) return emptyList()
        val page = pageAt(elapsedMs, count, slots)
        val indices = LinkedHashSet<Int>(slots * 2)
        indices.addAll(page.indices)
        indices.addAll(indicesFor(page.generation + 1, count, slots))
        return indices.toList()
    }
}
