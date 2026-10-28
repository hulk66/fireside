package com.firetv.immichslideshow

import android.app.Application
import android.content.Context
import coil.ImageLoader
import com.firetv.immichslideshow.data.AuthProvider
import com.firetv.immichslideshow.data.DemoBitmapSource
import com.firetv.immichslideshow.data.ImmichBitmapSource
import com.firetv.immichslideshow.data.ImmichConfig
import com.firetv.immichslideshow.data.ImmichRepository
import com.firetv.immichslideshow.data.PhotoBitmapSource
import com.firetv.immichslideshow.data.SettingsStore
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
 * still change at runtime (the auth interceptor reads [AuthProvider]).
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settings = SettingsStore(appContext)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val key = AuthProvider.apiKey
                val request = if (key.isBlank()) {
                    chain.request()
                } else {
                    chain.request().newBuilder().header("x-api-key", key).build()
                }
                chain.proceed(request)
            }
            .build()
    }

    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .okHttpClient { httpClient }
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
