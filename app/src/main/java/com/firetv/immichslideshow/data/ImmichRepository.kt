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

class ImmichException(
    message: String,
    cause: Throwable? = null,
    /** True when nothing answered — worth retrying against another address. */
    val networkFailure: Boolean = false,
) : Exception(message, cause)
