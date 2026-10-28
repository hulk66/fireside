package org.himstedt.immichslideshow.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire models for the Immich REST API. Unknown fields are ignored. */

@Serializable
data class AlbumDto(
    val id: String = "",
    @SerialName("albumName") val albumName: String = "",
    val description: String = "",
    val albumThumbnailAssetId: String? = null,
    val assetCount: Int = 0,
    val startDate: String? = null,
    val endDate: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val shared: Boolean = false,
    val assets: List<AssetDto> = emptyList(),
)

@Serializable
data class AssetDto(
    val id: String = "",
    val type: String = "IMAGE",
    val originalFileName: String = "",
    val originalMimeType: String? = null,
    val fileCreatedAt: String? = null,
    val localDateTime: String? = null,
    val thumbhash: String? = null,
    /** Pixel size of the original; drives the justified layout of the grid. */
    val width: Int = 0,
    val height: Int = 0,
    val isTrashed: Boolean = false,
    val isArchived: Boolean = false,
    val exifInfo: ExifDto? = null,
)

@Serializable
data class ExifDto(
    val make: String? = null,
    val model: String? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val orientation: String? = null,
    val dateTimeOriginal: String? = null,
    val city: String? = null,
    val country: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** Body of `POST /api/search/metadata`, the source of album photos in v2/v3. */
@Serializable
data class MetadataSearchDto(
    val albumIds: List<String> = emptyList(),
    val size: Int = 1000,
    val page: Int = 1,
    /** `asc` keeps a slideshow in chronological order, oldest frame first. */
    val order: String = "asc",
    val withExif: Boolean = true,
)

@Serializable
data class MetadataSearchResponseDto(
    val assets: SearchAssetsDto = SearchAssetsDto(),
)

@Serializable
data class SearchAssetsDto(
    /** Note: Immich reports the page size here, not the album total. */
    val total: Int = 0,
    val count: Int = 0,
    val items: List<AssetDto> = emptyList(),
)

@Serializable
data class PingDto(
    val res: String = "",
)

@Serializable
data class ErrorDto(
    val message: String = "",
    val error: String = "",
    @SerialName("statusCode") val statusCode: Int = 0,
)
