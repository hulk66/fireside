package org.himstedt.fireside

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.himstedt.fireside.data.ImmichConfig
import org.himstedt.fireside.ui.AppRoot
import org.himstedt.fireside.ui.theme.FiresideTheme

/**
 * The app's single activity. It is landscape-only and keeps the screen awake,
 * which is what a photo frame on the living-room TV wants.
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as SlideshowApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        IntentConfig.read(intent)?.let { container.settings.saveConfig(it) }

        setContent {
            FiresideTheme {
                AppRoot(
                    container = container,
                    onFinish = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        IntentConfig.read(intent)?.let { container.settings.saveConfig(it) }
    }
}

/**
 * Lets a launcher (or `adb shell am start`) hand over the connection details, so
 * a fresh install can be provisioned without typing on the remote:
 *
 * ```
 * adb shell am start -n org.himstedt.fireside/.MainActivity \
 *   -e immich_url http://192.168.1.20:2283 -e immich_api_key YOUR_KEY
 * ```
 */
internal object IntentConfig {

    const val EXTRA_URL = "immich_url"
    const val EXTRA_URL_ALT = "immich_base_url"
    const val EXTRA_API_KEY = "immich_api_key"
    const val EXTRA_DEMO = "demo"

    fun read(intent: Intent?): ImmichConfig? {
        if (intent == null) return null
        if (intent.getBooleanExtra(EXTRA_DEMO, false)) return ImmichConfig.Demo

        val url = intent.getStringExtra(EXTRA_URL) ?: intent.getStringExtra(EXTRA_URL_ALT)
        val key = intent.getStringExtra(EXTRA_API_KEY)
        if (url.isNullOrBlank() || key.isNullOrBlank()) return null
        return ImmichConfig(baseUrl = url, apiKey = key).sanitized()
    }
}
