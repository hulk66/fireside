package org.himstedt.fireside.data

import java.text.SimpleDateFormat
import java.util.Locale

/** Domain models the UI and slideshow work with. */

data class Album(
    val id: String,
    val name: String,
    val description: String,
    val assetCount: Int,
    val coverAssetId: String?,
    val startDate: String?,
    val endDate: String?,
) {
    val yearLabel: String?
        get() = startDate?.let { DateLabels.year(it) }
}

data class Photo(
    val id: String,
    val fileName: String,
    val takenAt: String?,
    val width: Int?,
    val height: Int?,
    val city: String?,
) {
    /** Caption shown in the lower-left corner, Apple TV style. */
    val caption: String
        get() {
            val date = DateLabels.pretty(takenAt)
            return when {
                date != null && city != null -> "$date  ·  $city"
                date != null -> date
                else -> fileName.substringBeforeLast('.')
            }
        }

    val aspectRatio: Float?
        get() {
            val w = width ?: return null
            val h = height ?: return null
            if (w <= 0 || h <= 0) return null
            return w.toFloat() / h.toFloat()
        }
}

/** Lightweight, display-only ISO-8601 helpers (no desugaring required). */
object DateLabels {
    private val isoPrefix = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val pretty = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    private val yearFmt = SimpleDateFormat("yyyy", Locale.getDefault())

    private fun parsePrefix(value: String?): java.util.Date? {
        if (value.isNullOrBlank() || value.length < 10) return null
        return runCatching { isoPrefix.parse(value.substring(0, 10)) }.getOrNull()
    }

    fun pretty(value: String?): String? =
        parsePrefix(value)?.let { runCatching { pretty.format(it) }.getOrNull() }

    fun year(value: String?): String? =
        parsePrefix(value)?.let { runCatching { yearFmt.format(it) }.getOrNull() }
}

fun AlbumDto.toDomain(): Album = Album(
    id = id,
    name = albumName.ifBlank { "Untitled album" },
    description = description,
    assetCount = assetCount.coerceAtLeast(assets.size),
    coverAssetId = albumThumbnailAssetId ?: assets.firstOrNull()?.id,
    startDate = startDate ?: assets.firstOrNull()?.fileCreatedAt,
    endDate = endDate,
)

fun AssetDto.toDomain(): Photo = Photo(
    id = id,
    fileName = originalFileName.ifBlank { id },
    takenAt = fileCreatedAt ?: exifInfo?.dateTimeOriginal ?: localDateTime,
    width = width.takeIf { it > 0 } ?: exifInfo?.imageWidth,
    height = height.takeIf { it > 0 } ?: exifInfo?.imageHeight,
    city = exifInfo?.city?.takeIf { it.isNotBlank() },
)
