package org.himstedt.immichslideshow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.himstedt.immichslideshow.AppContainer
import org.himstedt.immichslideshow.data.Album
import org.himstedt.immichslideshow.ui.screens.AlbumListScreen
import org.himstedt.immichslideshow.ui.screens.SetupScreen
import org.himstedt.immichslideshow.ui.screens.SlideshowScreen

/** Where the app currently is. Slideshow routes carry their album in memory. */
private sealed interface Route {
    data object Setup : Route
    data object Albums : Route
    data object Settings : Route
    data class Play(val album: Album) : Route
}

/**
 * Tiny state-machine navigator. A TV app has only four places to be, so this
 * stays a `when` instead of pulling in a navigation library.
 */
@Composable
fun AppRoot(container: AppContainer, onFinish: () -> Unit) {
    val config by container.settings.config.collectAsStateWithLifecycle()
    val prefs by container.settings.slideshow.collectAsStateWithLifecycle()

    var route by remember {
        mutableStateOf<Route>(if (container.settings.config.value != null) Route.Albums else Route.Setup)
    }

    LaunchedEffect(config) {
        if (config == null && route !is Route.Settings) route = Route.Setup
    }

    BackHandler(enabled = route is Route.Settings || route is Route.Play) {
        route = Route.Albums
    }

    AnimatedContent(
        targetState = route,
        transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
        label = "route",
    ) { current ->
        when (current) {
            Route.Setup -> SetupScreen(
                container = container,
                initial = config,
                canGoBack = false,
                onBack = onFinish,
                onConnected = { route = Route.Albums },
                onDemo = { route = Route.Albums },
            )

            Route.Albums -> {
                val active = config
                if (active == null) {
                    LaunchedEffect(Unit) { route = Route.Setup }
                } else {
                    AlbumListScreen(
                        container = container,
                        config = active,
                        onOpenAlbum = { album -> route = Route.Play(album) },
                        onOpenSettings = { route = Route.Settings },
                        onExit = onFinish,
                    )
                }
            }

            Route.Settings -> SetupScreen(
                container = container,
                initial = config,
                canGoBack = true,
                onBack = { route = Route.Albums },
                onConnected = { route = Route.Albums },
                onDemo = { route = Route.Albums },
            )

            is Route.Play -> {
                val active = config
                if (active == null) {
                    LaunchedEffect(Unit) { route = Route.Setup }
                } else {
                    SlideshowScreen(
                        container = container,
                        config = active,
                        album = current.album,
                        prefs = prefs,
                        onPrefsChange = { updated ->
                            container.settings.updateSlideshow { updated }
                        },
                        onExit = { route = Route.Albums },
                    )
                }
            }
        }
    }
}
