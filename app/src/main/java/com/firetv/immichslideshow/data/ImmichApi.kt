package com.firetv.immichslideshow.data

import retrofit2.http.GET
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

    @GET("api/server/ping")
    suspend fun ping(): PingDto
}
