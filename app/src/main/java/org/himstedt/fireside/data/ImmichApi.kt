package org.himstedt.fireside.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** The small slice of the Immich REST API this app needs. */
interface ImmichApi {

    @GET("api/albums")
    suspend fun getAlbums(): List<AlbumDto>

    /**
     * @param withoutAssets `true` returns album metadata only. We pass `false`
     *   to get the ordered asset list in a single request.
     */
    @GET("api/albums/{id}")
    suspend fun getAlbum(
        @Path("id") id: String,
        @Query("withoutAssets") withoutAssets: Boolean = false,
    ): AlbumDto

    /**
     * Immich v2/v3 no longer embeds assets in the album payload, so the photo
     * list comes from the metadata search, paged with [MetadataSearchDto.page].
     */
    @POST("api/search/metadata")
    suspend fun searchMetadata(@Body body: MetadataSearchDto): MetadataSearchResponseDto

    @GET("api/server/ping")
    suspend fun ping(): PingDto
}
