package com.firetv.immichslideshow.data

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where slideshow frames come from — Immich, or locally generated demo art. */
interface PhotoBitmapSource {
    /**
     * Loads a photo sized for the screen. Implementations must be safe to call
     * from a coroutine on any dispatcher and should return null on failure so
     * the slideshow can keep running.
     */
    suspend fun load(photo: Photo, width: Int, height: Int): ImageBitmap?

    /** Thumbnail URL for grid cards, or null when there is no remote source. */
    fun previewUrl(photo: Photo): String?
}

class ImmichBitmapSource(
    private val context: Context,
    private val repository: ImmichRepository,
    private val imageLoader: ImageLoader,
) : PhotoBitmapSource {

    override fun previewUrl(photo: Photo): String =
        repository.thumbnailUrl(photo.id, preview = false)

    override suspend fun load(photo: Photo, width: Int, height: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(repository.thumbnailUrl(photo.id, preview = true))
                .size(width, height)
                .scale(Scale.FIT)
                .allowHardware(false)
                .bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()

            when (val result = imageLoader.execute(request)) {
                is SuccessResult -> runCatching {
                    result.drawable.toBitmap().asImageBitmap()
                }.getOrNull()

                else -> null
            }
        }
}

class DemoBitmapSource : PhotoBitmapSource {

    override fun previewUrl(photo: Photo): String? = null

    override suspend fun load(photo: Photo, width: Int, height: Int): ImageBitmap? =
        withContext(Dispatchers.Default) {
            runCatching { DemoLibrary.bitmap(photo.id, width, height) }.getOrNull()
        }
}
