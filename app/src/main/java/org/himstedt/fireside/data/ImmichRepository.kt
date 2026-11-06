package org.himstedt.fireside.data

import org.himstedt.fireside.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

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
        // Without this, kotlinx omits fields that equal their default and the
        // server silently falls back to its own page size and sort order.
        encodeDefaults = true
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

    /**
     * `/api/server/ping` needs no credentials, so the ping alone only proves the
     * address. One album call proves the API key as well before we save it.
     */
    suspend fun checkConnection(): Result<String> = call {
        val ping = api.ping()
        if (!ping.res.equals("pong", ignoreCase = true)) return@call ping.res
        api.getAlbums()
        "Connected"
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

    /** Photo lists are a few hundred KB each; keep the last few for the session. */
    private val photoCache = java.util.concurrent.ConcurrentHashMap<String, List<Photo>>()

    /**
     * Album photos, oldest first. Immich v2/v3 keeps assets out of the album
     * payload, so they are paged in from the metadata search instead — a large
     * album is a couple of megabyte-sized responses, so the result is memoised
     * and re-entering an album does not go back to the network.
     */
    suspend fun photos(albumId: String): Result<List<Photo>> {
        photoCache[albumId]?.let { return Result.success(it) }
        return call { fetchPhotos(albumId) }
            .onSuccess { photos ->
                photoCache[albumId] = photos
                if (photoCache.size > PHOTO_CACHE_ALBUMS) {
                    photoCache.keys.firstOrNull()?.let { photoCache.remove(it) }
                }
            }
    }

    private suspend fun fetchPhotos(albumId: String): List<Photo> {
        val photos = ArrayList<Photo>()
        var page = 1
        while (photos.size < MAX_ALBUM_PHOTOS) {
            val response = api.searchMetadata(
                MetadataSearchDto(albumIds = listOf(albumId), size = SEARCH_PAGE, page = page),
            )
            val items = response.assets.items
            if (items.isEmpty()) break
            items.asSequence()
                .filter { it.type.equals("IMAGE", ignoreCase = true) }
                .filter { !it.isTrashed && !it.isArchived }
                .mapTo(photos) { it.toDomain() }
            // A short page means there is nothing after it.
            if (items.size < SEARCH_PAGE) break
            page++
        }
        return photos
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
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
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

    /**
     * Every endpoint here is a GET, so a single quiet retry is safe. Fire OS
     * drops the first connection after the TV has been idle, and one retry turns
     * that into a pause instead of an error card.
     */
    private suspend fun <T> call(block: suspend () -> T): Result<T> {
        var attempt = 0
        while (true) {
            try {
                return Result.success(block())
            } catch (e: HttpException) {
                return Result.failure(ImmichException(mapHttpError(e), e))
            } catch (e: IOException) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.w("ImmichRepo", "request failed (attempt ${attempt + 1}): ${config.normalizedBaseUrl()}", e)
                }
                if (attempt == 0) {
                    attempt++
                    delay(500)
                    continue
                }
                return Result.failure(ImmichException(networkMessage(e), e, networkFailure = true))
            } catch (e: Exception) {
                return Result.failure(ImmichException(e.message ?: "Unexpected error talking to Immich.", e))
            }
        }
    }

    /**
     * TLS trust problems, DNS failures and closed ports all surface as
     * IOException, but they need different fixes, so name the one we hit.
     */
    private fun networkMessage(e: IOException): String {
        val host = config.displayHost()
        return when {
            e.isTlsTrustFailure() ->
                "The TV doesn't trust the HTTPS certificate for $host. " +
                    "Use a certificate the TV knows, or the plain http:// address of the server."
            e.hasCause<SSLException>() ->
                "$host didn't answer a secure connection. Try the plain http:// address instead."
            e.hasCause<UnknownHostException>() ->
                "Can't reach $host. The TV couldn't look up that name — check the spelling, or enter the server's IP address."
            e.hasCause<SocketTimeoutException>() ->
                "Can't reach $host. The server didn't answer in time — is Immich running on that address?"
            e.hasCause<ConnectException>() ->
                "Can't reach $host. The connection was refused — is Immich listening on that port?"
            else ->
                "Can't reach $host. Check the URL and that the TV is on the same network."
        }
    }

    private inline fun <reified T : Throwable> IOException.hasCause(): Boolean {
        var cause: Throwable? = this
        var depth = 0
        while (cause != null && depth < 8) {
            if (cause is T) return true
            cause = cause.cause
            depth++
        }
        return false
    }

    private fun IOException.isTlsTrustFailure(): Boolean {
        var cause: Throwable? = this
        var depth = 0
        while (cause != null && depth < 8) {
            if (cause is CertPathValidatorException ||
                cause is CertificateException ||
                cause is SSLPeerUnverifiedException ||
                (cause is SSLHandshakeException && hasCause<CertificateException>())
            ) {
                return true
            }
            cause = cause.cause
            depth++
        }
        return false
    }

    private fun mapHttpError(e: HttpException): String = when (e.code()) {
        401, 403 -> "Immich rejected the API key. Create one under Account Settings → API Keys."
        404 -> "Not found on ${config.displayHost()}. Is that the right Immich URL?"
        429 -> "Immich is rate limiting requests. Try again in a moment."
        in 500..599 -> "Immich had a server error (${e.code()}). Check the server logs."
        else -> "Immich returned HTTP ${e.code()}."
    }
}

private const val SEARCH_PAGE = 1000

/** Enough for any album a TV slideshow would want to walk through. */
private const val MAX_ALBUM_PHOTOS = 5_000

/** How many albums' photo lists to keep memoised. */
private const val PHOTO_CACHE_ALBUMS = 8

class ImmichException(
    message: String,
    cause: Throwable? = null,
    /** True when nothing answered — worth retrying against another address. */
    val networkFailure: Boolean = false,
) : Exception(message, cause)
