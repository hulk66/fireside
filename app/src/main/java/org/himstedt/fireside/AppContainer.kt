package org.himstedt.fireside

import android.app.Application
import android.content.Context
import coil.ImageLoader
import org.himstedt.fireside.data.AuthProvider
import org.himstedt.fireside.data.DemoBitmapSource
import org.himstedt.fireside.data.ImmichBitmapSource
import org.himstedt.fireside.data.ImmichConfig
import org.himstedt.fireside.data.ImmichRepository
import org.himstedt.fireside.data.PhotoBitmapSource
import org.himstedt.fireside.data.SettingsStore
import org.himstedt.fireside.data.authenticatedClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class SlideshowApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Small hand-rolled dependency container. One process-wide OkHttp client and
 * Coil loader keep the connection pool warm while the Immich URL/API key can
 * still change at runtime.
 *
 * The client itself carries no credential: the key is attached per consumer.
 * Repositories authenticate from their own [ImmichConfig]; Coil is built before
 * any config exists, so it reads [AuthProvider], which is published whenever a
 * config is saved.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settings = SettingsStore(appContext)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .okHttpClient { authenticatedClient(httpClient) { AuthProvider.apiKey } }
            .crossfade(160)
            .respectCacheHeaders(false)
            .build()
    }

    private var cachedRepositoryKey: String? = null
    private var cachedRepository: ImmichRepository? = null

    @Synchronized
    fun repository(config: ImmichConfig): ImmichRepository {
        val key = config.normalizedBaseUrl() + "|" + config.apiKey
        val existing = cachedRepository
        if (existing != null && cachedRepositoryKey == key) return existing
        return ImmichRepository(config, httpClient).also {
            cachedRepository = it
            cachedRepositoryKey = key
        }
    }

    fun bitmapSource(config: ImmichConfig): PhotoBitmapSource =
        if (config.demo) {
            DemoBitmapSource()
        } else {
            ImmichBitmapSource(appContext, repository(config), imageLoader)
        }
}
