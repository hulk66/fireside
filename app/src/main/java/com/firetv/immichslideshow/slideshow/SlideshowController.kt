package com.firetv.immichslideshow.slideshow

import android.os.SystemClock
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.firetv.immichslideshow.data.Photo
import com.firetv.immichslideshow.data.PhotoBitmapSource
import com.firetv.immichslideshow.data.SlideshowPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives one slideshow: a frame clock, the current slide index, a bitmap
 * prefetch cache and the D-pad commands.
 *
 * The controller owns no Android views. It is created once per album and
 * consumed by a single `Canvas`, which keeps the hot path free of
 * recomposition — only the draw phase reads [frame].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SlideshowController(
    photos: List<Photo>,
    private val source: PhotoBitmapSource,
    initialPrefs: SlideshowPrefs,
    private val scope: CoroutineScope,
    private val decodeSize: IntSize = IntSize(1920, 1080),
    /**
     * Square, not 16:9: a grid cell is as often portrait as landscape, and a
     * 16:9 decode of a portrait photo is only 270 px wide — visibly soft once it
     * is stretched into a tall cell. `Scale.FIT` keeps each photo's own shape.
     *
     * 600 covers the largest cell a 1080p wall produces (about 500 px on the
     * long side, plus the scale-up when the mosaic has to fill the frame).
     */
    private val tileDecodeSize: IntSize = IntSize(600, 600),
    private val scrapbookTileSize: IntSize = IntSize(640, 640),
) {

    private val basePhotos: List<Photo> = photos

    /**
     * Page size for the Photo Grid, from the album's shapes. Computed once: the
     * wall must not change size from page to page.
     */
    private var gridSlots: Int = GridPlan.slotsFor(photos.mapNotNull { it.aspectRatio })
    private var ordered: List<Photo> = if (initialPrefs.shuffle) photos.shuffled() else photos

    /** Playback order for the current session (respects the shuffle setting). */
    val photos: List<Photo> get() = ordered

    var prefs by mutableStateOf(initialPrefs)
        private set

    var paused by mutableStateOf(false)
        private set

    private var timeline = SlideshowTimeline(initialPrefs.timing)
    private var elapsedMs = 0L
    private var lastFrameNanos = 0L

    private val _frame = mutableStateOf(SlideshowFrame.Empty)
    val frame: State<SlideshowFrame> = _frame

    // ------------------------------------------------------------ bitmap cache

    private val fullCache = object : LinkedHashMap<Int, ImageBitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>): Boolean =
            size > FULL_CACHE_SIZE
    }

    private val tileCache = object : LinkedHashMap<Int, ImageBitmap>(TILE_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>): Boolean =
            size > TILE_CACHE_SIZE
    }

    private val inFlight = HashSet<String>()
    private val failedAt = HashMap<String, Long>()

    private val bitmaps = object : SlideshowBitmaps {
        override fun full(index: Int): ImageBitmap? = fullCache[index]
        override fun tile(index: Int): ImageBitmap? = tileCache[index]
    }

    private val decoder = Dispatchers.IO.limitedParallelism(2)

    // ------------------------------------------------------------------ clock

    /** Runs the display loop. Cancel the coroutine to stop the slideshow. */
    suspend fun run() {
        lastFrameNanos = 0L
        while (true) {
            val nanos = withFrameNanos { it }
            val previous = lastFrameNanos
            lastFrameNanos = nanos
            if (previous != 0L) {
                val delta = ((nanos - previous) / 1_000_000L).coerceIn(0L, 120L)
                if (!paused) elapsedMs += delta
            }
            emit()
        }
    }

    private fun emit() {
        if (photos.isEmpty()) {
            _frame.value = SlideshowFrame.Empty.copy(effect = prefs.effect, prefs = prefs)
            return
        }
        val index = timeline.indexAt(elapsedMs, photos.size)
        val transition = timeline.transitionProgressAt(elapsedMs)
        val hold = timeline.holdProgressAt(elapsedMs)
        val progress = (
            timeline.timing.holdMs * hold + timeline.timing.transitionMs * transition
            ) / timeline.timing.cycleMs

        _frame.value = SlideshowFrame(
            effect = prefs.effect,
            currentIndex = index,
            transition = transition,
            hold = hold,
            progress = progress.coerceIn(0f, 1f),
            elapsedMs = elapsedMs,
            photos = photos,
            prefs = prefs,
            bitmaps = bitmaps,
            gridSlots = gridSlots,
        )
        prefetch(index, transition)
    }

    // --------------------------------------------------------------- prefetch

    private fun prefetch(index: Int, transition: Float) {
        if (prefs.effect.mosaic) {
            // Exactly the wall's own window plus the next page, using the same
            // page size the renderer lays out with. A different size here means
            // a different window of photos, and every cell draws empty.
            for (i in GridPlan.prefetchIndices(elapsedMs, ordered.size, gridSlots)) {
                request(i, tile = true)
            }
            return
        }
        if (prefs.effect == SlideshowEffect.SCRAPBOOK) {
            // The desk is the whole picture: twenty cards, none of them ever
            // drawn full-frame, so tiles alone keep the table stocked and leave
            // the full-frame cache (and its 8 MB bitmaps) alone.
            for (k in 0 until ScrapbookDesk.CARDS) request(index - k, tile = true)
            return
        }
        request(index - 1, tile = false)
        request(index, tile = false)
        request(index + 1, tile = false)
        if (transition > 0f) request(index + 2, tile = false)
    }

    private fun request(rawIndex: Int, tile: Boolean) {
        val count = photos.size
        if (count == 0) return
        val index = ((rawIndex % count) + count) % count
        val cache = if (tile) tileCache else fullCache
        if (cache.containsKey(index)) return

        val key = (if (tile) "t:" else "f:") + index
        if (inFlight.contains(key)) return
        val lastFailure = failedAt[key]
        if (lastFailure != null && SystemClock.uptimeMillis() - lastFailure < RETRY_AFTER_MS) return
        inFlight.add(key)

        val photo = photos[index]
        val target = when {
            !tile -> decodeSize
            // Scrapbook cards are portrait as often as landscape; a square
            // decode keeps both orientations sharp without the full-frame cost.
            prefs.effect == SlideshowEffect.SCRAPBOOK -> scrapbookTileSize
            else -> tileDecodeSize
        }
        scope.launch(decoder) {
            val bitmap = runCatching { source.load(photo, target.width, target.height) }.getOrNull()
            withContext(Dispatchers.Main) {
                inFlight.remove(key)
                if (bitmap != null) {
                    failedAt.remove(key)
                    cache[index] = bitmap
                } else {
                    failedAt[key] = SystemClock.uptimeMillis()
                }
            }
        }
    }

    // --------------------------------------------------------------- commands

    fun next() = step(1)

    fun previous() = step(-1)

    private fun step(direction: Int) {
        val count = photos.size
        if (count == 0) return
        val index = timeline.indexAt(elapsedMs, count)
        val target = ((index + direction) % count + count) % count
        elapsedMs = timeline.elapsedForSlide(target)
        emit()
    }

    fun seekTo(index: Int) {
        val count = photos.size
        if (count == 0) return
        val target = ((index % count) + count) % count
        elapsedMs = timeline.elapsedForSlide(target)
        emit()
    }

    fun restart() {
        elapsedMs = 0L
        emit()
    }

    fun togglePause() {
        paused = !paused
    }

    /** Applies new preferences without restarting the current slide. */
    fun applyPrefs(updated: SlideshowPrefs) {
        if (updated == prefs) return
        val current = currentPhoto()
        val transition = timeline.transitionProgressAt(elapsedMs)

        if (updated.shuffle != prefs.shuffle) {
            // Re-order around the photo that is on screen so the change is invisible.
            val reshuffled = if (updated.shuffle) basePhotos.shuffled() else basePhotos
            ordered = if (current == null) {
                reshuffled
            } else {
                listOf(current) + reshuffled.filterNot { it.id == current.id }
            }
            fullCache.clear()
            tileCache.clear()
        }

        prefs = updated
        timeline = SlideshowTimeline(updated.timing)
        val index = current?.let { photo -> ordered.indexOfFirst { it.id == photo.id } }
            ?.coerceAtLeast(0) ?: 0
        elapsedMs = timeline.elapsedForSlide(index) +
            (timeline.timing.transitionMs * transition).toLong()
        emit()
    }

    fun setEffect(effect: SlideshowEffect) = applyPrefs(prefs.copy(effect = effect))

    fun currentIndex(): Int = timeline.indexAt(elapsedMs, photos.size)

    fun currentPhoto(): Photo? = photos.getOrNull(currentIndex())

    /** Lightweight snapshot for the chrome overlay (safe to read while composing). */
    fun chromeInfo(): ChromeInfo = ChromeInfo(
        index = currentIndex(),
        count = photos.size,
        progress = (
            timeline.timing.holdMs * timeline.holdProgressAt(elapsedMs) +
                timeline.timing.transitionMs * timeline.transitionProgressAt(elapsedMs)
            ) / timeline.timing.cycleMs,
        effect = prefs.effect,
        secondsPerPhoto = prefs.secondsPerPhoto,
    )

    data class ChromeInfo(
        val index: Int,
        val count: Int,
        val progress: Float,
        val effect: SlideshowEffect,
        val secondsPerPhoto: Int,
    )

    private companion object {
        const val FULL_CACHE_SIZE = 6
        /**
         * A grid page wants 30 photos (this page plus the next), and the page
         * before it is still on screen while the tiles flip, so the cache has to
         * hold well over two pages or the LRU thrashes and the wall flickers.
         */
        const val TILE_CACHE_SIZE = 50
        const val RETRY_AFTER_MS = 15_000L
    }
}
