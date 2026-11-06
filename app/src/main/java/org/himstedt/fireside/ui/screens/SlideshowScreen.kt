package org.himstedt.fireside.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.himstedt.fireside.AppContainer
import org.himstedt.fireside.data.Album
import org.himstedt.fireside.data.FitMode
import org.himstedt.fireside.data.DemoLibrary
import org.himstedt.fireside.data.ImmichConfig
import org.himstedt.fireside.data.Photo
import org.himstedt.fireside.data.SlideshowPrefs
import org.himstedt.fireside.slideshow.SlideshowController
import org.himstedt.fireside.slideshow.GridPlan
import org.himstedt.fireside.slideshow.SlidingPanels
import org.himstedt.fireside.slideshow.SlideshowEffect
import org.himstedt.fireside.slideshow.drawSlideshow
import org.himstedt.fireside.ui.components.AppBackground
import org.himstedt.fireside.ui.components.ErrorNotice
import org.himstedt.fireside.ui.components.KeyHint
import org.himstedt.fireside.ui.components.Spinner
import org.himstedt.fireside.ui.components.TvButton
import org.himstedt.fireside.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Album playback: loads the album's photos, then hands them to a
 * [SlideshowController] and a single full-screen `Canvas`.
 */
@Composable
fun SlideshowScreen(
    container: AppContainer,
    config: ImmichConfig,
    album: Album,
    prefs: SlideshowPrefs,
    onPrefsChange: (SlideshowPrefs) -> Unit,
    onExit: () -> Unit,
) {
    var photos by remember(album.id, config) { mutableStateOf<List<Photo>?>(null) }
    var error by remember(album.id, config) { mutableStateOf<String?>(null) }
    var attempt by remember(album.id, config) { mutableStateOf(0) }

    LaunchedEffect(album.id, config, attempt) {
        photos = null
        error = null
        val result = if (config.demo) {
            Result.success(DemoLibrary.photos)
        } else {
            container.repository(config).photos(album.id)
        }
        result.fold(
            onSuccess = { photos = it },
            onFailure = { error = it.message ?: "Could not load this album." },
        )
    }

    val loaded = photos
    val failure = error
    when {
        failure != null -> AlbumFailure(
            album = album,
            message = failure,
            onRetry = { attempt++ },
            onExit = onExit,
        )

        loaded == null -> AlbumLoading(album = album, demo = config.demo)

        loaded.isEmpty() -> AlbumEmpty(album = album, onExit = onExit)

        else -> SlideshowPlayer(
            container = container,
            config = config,
            album = album,
            photos = loaded,
            prefs = prefs,
            onPrefsChange = onPrefsChange,
            onExit = onExit,
        )
    }
}

@Composable
private fun SlideshowPlayer(
    container: AppContainer,
    config: ImmichConfig,
    album: Album,
    photos: List<Photo>,
    prefs: SlideshowPrefs,
    onPrefsChange: (SlideshowPrefs) -> Unit,
    onExit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val controller = remember(photos) {
        SlideshowController(
            photos = photos,
            source = container.bitmapSource(config),
            initialPrefs = prefs,
            scope = scope,
        )
    }

    LaunchedEffect(controller) { controller.run() }
    LaunchedEffect(prefs) { controller.applyPrefs(prefs) }

    var menuOpen by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableStateOf(0) }
    var info by remember { mutableStateOf(controller.chromeInfo()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        while (true) {
            delay(400)
            info = controller.chromeInfo()
        }
    }

    LaunchedEffect(interaction, menuOpen) {
        if (menuOpen) {
            chromeVisible = false
        } else {
            chromeVisible = true
            delay(6_000)
            chromeVisible = false
        }
    }

    fun touch() {
        interaction++
    }

    fun openMenu() {
        menuOpen = true
        chromeVisible = false
    }

    fun closeMenu() {
        menuOpen = false
        interaction++
        scope.launch { runCatching { focusRequester.requestFocus() } }
    }

    fun onKey(event: KeyEvent): Boolean {
        if (menuOpen) return false
        if (event.type != KeyEventType.KeyUp) return false
        when (event.key) {
            Key.DirectionRight, Key.MediaNext, Key.MediaFastForward -> {
                controller.next()
                touch()
            }

            Key.DirectionLeft, Key.MediaPrevious, Key.MediaRewind -> {
                controller.previous()
                touch()
            }

            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar,
            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                controller.togglePause()
                touch()
            }

            Key.DirectionDown, Key.Menu -> openMenu()

            Key.DirectionUp -> {
                chromeVisible = !chromeVisible
                touch()
            }

            else -> {
                val effect = effectForKeyCode(event.key.nativeKeyCode) ?: return false
                onPrefsChange(prefs.copy(effect = effect))
                touch()
            }
        }
        return true
    }

    // While the theme picker is open its own BackHandler owns the key; leaving
    // this one enabled would let a single press fall through and stop playback.
    BackHandler(enabled = !menuOpen) { onExit() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onKeyEvent(::onKey)
            .focusable(),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawSlideshow(controller.frame.value, measurer)
        }

        if (controller.paused) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xB305070A))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "❚❚", color = Palette.Amber, fontSize = 13.sp)
                Text(text = "Paused", color = Palette.Cream, fontSize = 14.sp, letterSpacing = 1.sp)
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && !menuOpen,
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(420)),
        ) {
            SlideshowChrome(
                album = album,
                info = info,
                paused = controller.paused,
                fitMode = prefs.fitMode,
            )
        }

        if (menuOpen) {
            EffectPickerOverlay(
                current = prefs.effect,
                prefs = prefs,
                onPick = { effect ->
                    onPrefsChange(prefs.copy(effect = effect))
                    closeMenu()
                },
                onPrefsChange = onPrefsChange,
                onClose = ::closeMenu,
            )
        }
    }
}

@Composable
private fun SlideshowChrome(
    album: Album,
    info: SlideshowController.ChromeInfo,
    paused: Boolean,
    fitMode: FitMode,
) {
    // The scrapbook desk and the reflections stage are white; a black scrim and
    // white type disappear on them, so the chrome flips to ink on paper.
    val light = info.effect == SlideshowEffect.REFLECTIONS || info.effect == SlideshowEffect.SCRAPBOOK
    val titleColor = if (light) Color(0xFF1B1B1B) else Palette.Cream
    val subColor = if (light) Color(0xCC7A4A1E) else Palette.AmberSoft.copy(alpha = 0.85f)
    val progress by animateFloatAsState(
        targetValue = info.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 420),
        label = "slideProgress",
    )

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(
                    if (light) {
                        Brush.verticalGradient(
                            listOf(Color(0xE6FFFFFF), Color(0xB3FFFFFF), Color.Transparent),
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(Color(0xD9000000), Color(0x73000000), Color.Transparent),
                        )
                    },
                ),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 52.dp, vertical = 32.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    color = titleColor,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        append(info.effect.title)
                        append("  ·  ")
                        if (info.effect == SlideshowEffect.SLIDING_PANELS) {
                            // This one changes a panel at a time, so its unit is
                            // the panel rather than a page of photos.
                            append(SlidingPanels.STEP_MS / 1000)
                            append("s per panel")
                        } else if (info.effect.mosaic) {
                            // A wall turns a page at a time; quoting the per-photo
                            // setting here would name a duration nothing obeys.
                            append(info.effect.pageMs / 1000)
                            append("s per page")
                        } else {
                            append(info.secondsPerPhoto)
                            append("s per photo")
                        }
                        if (fitMode.isFit) append("  ·  Fit")
                        if (paused) append("  ·  Paused")
                    },
                    color = subColor,
                    fontSize = 14.sp,
                    letterSpacing = 0.6.sp,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                // A wall has no "current photo", so a photo counter there would be
                // counting something that is not on screen.
                if (!info.effect.mosaic) {
                    Text(
                        text = "${(info.index + 1).coerceAtMost(info.count)} / ${info.count}",
                        color = titleColor,
                        fontSize = 17.sp,
                        letterSpacing = 1.6.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = "▼ themes",
                    color = if (light) Color(0x992A2A2A) else Palette.MutedDim,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(min = 132.dp)
                .background(
                    if (light) {
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xB3FFFFFF), Color(0xE6FFFFFF)),
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0x8C000000), Color(0xD9000000)),
                        )
                    },
                )
                .padding(horizontal = 52.dp, vertical = 30.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                Box(
                    Modifier
                        .widthIn(max = 420.dp)
                        .height(3.dp)
                        .background(if (light) Color(0x2E000000) else Color(0x33FFFFFF)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0.001f, 1f))
                            .height(3.dp)
                            .background(Palette.Amber),
                    )
                }
                Spacer(Modifier.weight(1f))
                KeyHint(keys = "◀ ▶", label = "Photo", light = light)
                KeyHint(keys = "OK", label = if (paused) "Resume" else "Pause", light = light)
                KeyHint(keys = "▼", label = "Themes", light = light)
                KeyHint(keys = "BACK", label = "Albums", light = light)
            }
        }
    }
}

@Composable
private fun AlbumLoading(album: Album, demo: Boolean) {
    AppBackground {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spinner(size = 46.dp)
            Spacer(Modifier.height(22.dp))
            Text(
                text = album.name,
                color = Palette.Cream,
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (demo) "Warming up the demo library…" else "Loading photos from your server…",
                color = Palette.Muted,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun AlbumFailure(
    album: Album,
    message: String,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    AppBackground {
        Column(
            Modifier.fillMaxSize().padding(72.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = album.name,
                color = Palette.Cream,
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(Modifier.height(20.dp))
            ErrorNotice(
                message = message,
                onRetry = onRetry,
                retryLabel = "Retry",
                modifier = Modifier.widthIn(max = 760.dp),
            )
            Spacer(Modifier.height(20.dp))
            TvButton(label = "Back to albums", onClick = onExit)
        }
    }
}

@Composable
private fun AlbumEmpty(album: Album, onExit: () -> Unit) {
    AppBackground {
        Column(
            Modifier.fillMaxSize().padding(72.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = album.name,
                color = Palette.Cream,
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "This album has no photos the TV can show yet. " +
                    "Add some images in Immich and try again.",
                color = Palette.Muted,
                fontSize = 17.sp,
                modifier = Modifier.widthIn(max = 620.dp),
            )
            Spacer(Modifier.height(24.dp))
            TvButton(label = "Back to albums", onClick = onExit, accent = true)
        }
    }
}

/** Number keys 1–9 and 0 map onto the theme list, Apple TV style. */
private fun effectForKeyCode(code: Int): SlideshowEffect? {
    val index = when (code) {
        AndroidKeyEvent.KEYCODE_1 -> 0
        AndroidKeyEvent.KEYCODE_2 -> 1
        AndroidKeyEvent.KEYCODE_3 -> 2
        AndroidKeyEvent.KEYCODE_4 -> 3
        AndroidKeyEvent.KEYCODE_5 -> 4
        AndroidKeyEvent.KEYCODE_6 -> 5
        AndroidKeyEvent.KEYCODE_7 -> 6
        AndroidKeyEvent.KEYCODE_8 -> 7
        AndroidKeyEvent.KEYCODE_9 -> 8
        AndroidKeyEvent.KEYCODE_0 -> 9
        else -> return null
    }
    return SlideshowEffect.entries.getOrNull(index)
}
