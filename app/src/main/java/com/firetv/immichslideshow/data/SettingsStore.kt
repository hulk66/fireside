package com.firetv.immichslideshow.data

import android.content.Context
import com.firetv.immichslideshow.slideshow.SlideshowEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** SharedPreferences-backed store for connection details and slideshow taste. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("immich_slideshow", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<ImmichConfig?> = _config.asStateFlow()

    private val _slideshow = MutableStateFlow(readSlideshowPrefs())
    val slideshow: StateFlow<SlideshowPrefs> = _slideshow.asStateFlow()

    init {
        AuthProvider.set(_config.value?.apiKey.orEmpty())
    }

    fun saveConfig(config: ImmichConfig?) {
        val sanitized = config?.sanitized()
        _config.value = sanitized
        prefs.edit().apply {
            if (sanitized == null) {
                remove(KEY_URL)
                remove(KEY_API_KEY)
                remove(KEY_DEMO)
            } else {
                putString(KEY_URL, sanitized.baseUrl)
                putString(KEY_API_KEY, sanitized.apiKey)
                putBoolean(KEY_DEMO, sanitized.demo)
            }
        }.apply()
        AuthProvider.set(sanitized?.apiKey.orEmpty())
    }

    fun updateSlideshow(transform: (SlideshowPrefs) -> SlideshowPrefs) {
        val updated = transform(_slideshow.value)
        _slideshow.value = updated
        prefs.edit().apply {
            putString(KEY_EFFECT, updated.effect.name)
            putInt(KEY_SECONDS, updated.secondsPerPhoto)
            putBoolean(KEY_SHUFFLE, updated.shuffle)
            putBoolean(KEY_CAPTION, updated.showCaption)
            putBoolean(KEY_VIGNETTE, updated.vignette)
            putFloat(KEY_PAN, updated.panIntensity)
        }.apply()
    }

    fun setEffect(effect: SlideshowEffect) = updateSlideshow { it.copy(effect = effect) }

    private fun readConfig(): ImmichConfig? {
        val demo = prefs.getBoolean(KEY_DEMO, false)
        if (demo) return ImmichConfig.Demo
        val url = prefs.getString(KEY_URL, null) ?: return null
        val key = prefs.getString(KEY_API_KEY, null) ?: return null
        if (url.isBlank()) return null
        return ImmichConfig(baseUrl = url, apiKey = key)
    }

    private fun readSlideshowPrefs(): SlideshowPrefs = SlideshowPrefs(
        effect = SlideshowEffect.fromName(prefs.getString(KEY_EFFECT, null)),
        secondsPerPhoto = prefs.getInt(KEY_SECONDS, 8).coerceIn(2, 120),
        shuffle = prefs.getBoolean(KEY_SHUFFLE, false),
        showCaption = prefs.getBoolean(KEY_CAPTION, true),
        vignette = prefs.getBoolean(KEY_VIGNETTE, true),
        panIntensity = prefs.getFloat(KEY_PAN, 1f).coerceIn(0f, 2f),
    )

    private companion object {
        const val KEY_URL = "immich_base_url"
        const val KEY_API_KEY = "immich_api_key"
        const val KEY_DEMO = "demo_mode"
        const val KEY_EFFECT = "slideshow_effect"
        const val KEY_SECONDS = "slideshow_seconds"
        const val KEY_SHUFFLE = "slideshow_shuffle"
        const val KEY_CAPTION = "slideshow_caption"
        const val KEY_VIGNETTE = "slideshow_vignette"
        const val KEY_PAN = "slideshow_pan"
    }
}
