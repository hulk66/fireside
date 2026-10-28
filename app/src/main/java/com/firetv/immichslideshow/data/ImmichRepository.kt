package com.firetv.immichslideshow.data

import com.firetv.immichslideshow.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin, blocking-free facade over Immich. Every call returns [Result] so the
 * UI can show a friendly message instead of crashing on a flaky LAN.
 */
class ImmichRepository(
    private val config: ImmichConfig,
    client: OkHttpClient? = null,
) {
    private val okHttp: OkHttpClient = client ?: defaultClient()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val api: ImmichApi = Retrofit.Builder()
        .baseUrl(config.normalizedBaseUrl())
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ImmichApi::class.java)

    suspend fun checkConnection(): Result<String> = call {
        val ping = api.ping()
        if (ping.res.equals("pong", ignoreCase = true)) "Connected" else ping.res
    }

    suspend fun albums(): Result<List<Album>> = call {
        api.getAlbums()
            .filter { !it.id.isBlank() }
            .map { it.toDomain() }
            .sortedWith(compareByDescending<Album> { it.startDate ?: "" }.thenBy { it.name.lowercase() })
    }

    /** Album metadata plus its ordered photo list. */
    suspend fun album(albumId: String): Result<Album> = call {
        api.getAlbum(albumId, withoutAssets = false).toDomain()
    }

    suspend fun photos(albumId: String): Result<List<Photo>> = call {
        api.getAlbum(albumId, withoutAssets = false)
            .assets
            .asSequence()
            .filter { it.type.equals("IMAGE", ignoreCase = true) }
            .filter { !it.isTrashed && !it.isArchived }
            .map { it.toDomain() }
            .toList()
    }

    fun thumbnailUrl(assetId: String, preview: Boolean = true): String {
        val size = if (preview) "preview" else "thumbnail"
        return "${config.normalizedBaseUrl()}api/assets/$assetId/thumbnail?size=$size"
    }

    fun originalUrl(assetId: String): String =
        "${config.normalizedBaseUrl()}api/assets/$assetId/original"

    private fun defaultClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("x-api-key", config.apiKey)
                        .header("Accept", "application/json")
                        .build(),
                )
            }
            .addInterceptor(logging)
            .build()
    }

    private suspend fun <T> call(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: HttpException) {
        Result.failure(ImmichException(mapHttpError(e), e))
    } catch (e: IOException) {
        Result.failure(
            ImmichException(
                "Can't reach ${config.displayHost()}. Check the URL and that the TV is on the same network.",
                e,
            ),
        )
    } catch (e: Exception) {
        Result.failure(ImmichException(e.message ?: "Unexpected error talking to Immich.", e))
    }

    private fun mapHttpError(e: HttpException): String = when (e.code()) {
        401, 403 -> "Immich rejected the API key. Create one under Account Settings → API Keys."
        404 -> "Not found on ${config.displayHost()}. Is that the right Immich URL?"
        429 -> "Immich is rate limiting requests. Try again in a moment."
        in 500..599 -> "Immich had a server error (${e.code()}). Check the server logs."
        else -> "Immich returned HTTP ${e.code()}."
    }
}

class ImmichException(message: String, cause: Throwable? = null) : Exception(message, cause)
