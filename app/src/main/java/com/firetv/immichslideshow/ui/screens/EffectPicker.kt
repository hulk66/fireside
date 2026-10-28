package com.firetv.immichslideshow.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import com.firetv.immichslideshow.data.DemoLibrary
import com.firetv.immichslideshow.data.Photo
import com.firetv.immichslideshow.data.FitMode
import com.firetv.immichslideshow.data.SlideshowPrefs
import com.firetv.immichslideshow.slideshow.SlideshowBitmaps
import com.firetv.immichslideshow.slideshow.SlideshowEffect
import com.firetv.immichslideshow.slideshow.SlideshowFrame
import com.firetv.immichslideshow.slideshow.SlideshowTimeline
import com.firetv.immichslideshow.slideshow.SlideshowTiming
import com.firetv.immichslideshow.slideshow.drawSlideshow
import com.firetv.immichslideshow.ui.components.FocusableSurface
import com.firetv.immichslideshow.ui.components.KeyHint
import com.firetv.immichslideshow.ui.components.TvButton
import com.firetv.immichslideshow.ui.components.TvChip
import com.firetv.immichslideshow.ui.theme.Palette

/**
 * Full-screen theme chooser. Every tile renders the real slideshow renderer
 * against three locally generated photos, all driven by one shared clock, so
 * the previews are honest previews rather than static stills.
 */
@Composable
fun EffectPickerOverlay(
    current: SlideshowEffect,
    prefs: SlideshowPrefs,
    onPick: (SlideshowEffect) -> Unit,
    onPrefsChange: (SlideshowPrefs) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    val clock = rememberAnimationClock()
    val measurer = rememberTextMeasurer()
    val previewPhotos = remember { DemoLibrary.photos.take(3) }
    val previewBitmaps = remember { previewBitmaps() }
    val requesters = remember { List(SlideshowEffect.entries.size) { FocusRequester() } }

    LaunchedEffect(Unit) {
        runCatching { requesters[current.ordinal].requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xF7080A0F), Color(0xFA05070A)),
                ),
            ),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 54.dp, vertical = 32.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "THEMES",
                        color = Palette.Amber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.6.sp,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = "Photo themes",
                        color = Palette.Cream,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
                TvButton(label = "Close", onClick = onClose)
            }

            Spacer(Modifier.height(18.dp))

            TvLazyVerticalGrid(
                columns = TvGridCells.Fixed(5),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = rememberTvLazyGridState(),
                contentPadding = PaddingValues(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(SlideshowEffect.entries, key = { _, e -> e.name }) { slot, effect ->
                    EffectTile(
                        effect = effect,
                        slot = slot,
                        selected = effect == current,
                        clock = clock,
                        photos = previewPhotos,
                        bitmaps = previewBitmaps,
                        modifier = Modifier.focusRequester(requesters[effect.ordinal]),
                        onClick = { onPick(effect) },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "While playing:  1 – 0  jumps straight to a theme  ·  " +
                        "▼ themes  ·  OK pause  ·  ◀ ▶ photo",
                    color = Palette.MutedDim,
                    fontSize = 12.sp,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "SECONDS",
                    color = Palette.MutedDim,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(end = 4.dp),
                )
                SlideshowPrefs.Durations.forEach { seconds ->
                    TvChip(
                        label = "$seconds",
                        selected = prefs.secondsPerPhoto == seconds,
                        onClick = { onPrefsChange(prefs.copy(secondsPerPhoto = seconds)) },
                    )
                }
                Spacer(Modifier.width(16.dp))
                TvChip(
                    label = "Shuffle",
                    selected = prefs.shuffle,
                    onClick = { onPrefsChange(prefs.copy(shuffle = !prefs.shuffle)) },
                )
                TvChip(
                    label = "Captions",
                    selected = prefs.showCaption,
                    onClick = { onPrefsChange(prefs.copy(showCaption = !prefs.showCaption)) },
                )
                TvChip(
                    label = "Vignette",
                    selected = prefs.vignette,
                    onClick = { onPrefsChange(prefs.copy(vignette = !prefs.vignette)) },
                )
                TvChip(
                    label = "Fit",
                    selected = prefs.fitMode.isFit,
                    onClick = {
                        onPrefsChange(
                            prefs.copy(
                                fitMode = if (prefs.fitMode.isFit) FitMode.Fill else FitMode.Fit,
                            ),
                        )
                    },
                )
                Spacer(Modifier.weight(1f))
                KeyHint(keys = "OK", label = "Apply")
            }
        }
    }
}

@Composable
private fun EffectTile(
    effect: SlideshowEffect,
    slot: Int,
    selected: Boolean,
    clock: State<Long>,
    photos: List<Photo>,
    bitmaps: SlideshowBitmaps,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val timing = remember(effect) {
        SlideshowTiming.forEffect(effect, secondsPerPhoto = 4f, paceMultiplier = 1.6f)
    }
    val shape = RoundedCornerShape(14.dp)

    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
        shape = shape,
        containerColor = Color(0xFF0E1219),
        focusedColor = Color(0xFF0E1219),
        borderColor = if (selected) Palette.Amber.copy(alpha = 0.7f) else Palette.Hairline,
        focusedBorderColor = Palette.Amber,
        contentPadding = PaddingValues(0.dp),
    ) { focused ->
        Box(Modifier.fillMaxSize().clip(shape)) {
            Canvas(Modifier.fillMaxSize()) {
                val now = clock.value
                drawSlideshow(
                    previewFrame(effect, now, timing, photos, bitmaps, indexOverride = slot),
                    measurer,
                )
            }

            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .fillMaxHeight(0.66f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0x9905070A), Color(0xE605070A)),
                        ),
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 11.dp, vertical = 9.dp),
            ) {
                Column {
                    Text(
                        text = effect.title,
                        color = Palette.Cream,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Text(
                        text = effect.tagline,
                        color = Palette.Cream.copy(alpha = 0.62f),
                        fontSize = 10.sp,
                        lineHeight = 12.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (selected) {
                Text(
                    text = "PLAYING",
                    color = Color(0xFF21150A),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Palette.Amber)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }

        }
    }
}

private fun previewFrame(
    effect: SlideshowEffect,
    elapsedMs: Long,
    timing: SlideshowTiming,
    photos: List<Photo>,
    bitmaps: SlideshowBitmaps,
    indexOverride: Int? = null,
): SlideshowFrame {
    val timeline = SlideshowTimeline(timing)
    val index = (indexOverride ?: timeline.indexAt(elapsedMs, photos.size)).mod(photos.size)
    val transition = timeline.transitionProgressAt(elapsedMs)
    val hold = timeline.holdProgressAt(elapsedMs)
    val progress = ((timing.holdMs * hold + timing.transitionMs * transition) / timing.cycleMs)
        .coerceIn(0f, 1f)
    return SlideshowFrame(
        effect = effect,
        currentIndex = index,
        transition = transition,
        hold = hold,
        progress = progress,
        elapsedMs = elapsedMs,
        photos = photos,
        prefs = SlideshowPrefs(
            effect = effect,
            secondsPerPhoto = 4,
            vignette = true,
            showCaption = false,
        ),
        bitmaps = bitmaps,
    )
}

private fun previewBitmaps(): SlideshowBitmaps {
    // One distinct artwork per tile so the wall of previews never looks repetitive.
    val images: List<ImageBitmap> = (0 until 10).map { DemoLibrary.bitmap(it, 384, 216) }
    return object : SlideshowBitmaps {
        override fun full(index: Int): ImageBitmap = images[index.mod(images.size)]
        override fun tile(index: Int): ImageBitmap = images[index.mod(images.size)]
    }
}

/** A shared millisecond clock that advances once per display frame. */
@Composable
private fun rememberAnimationClock(): State<Long> {
    val state = remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        var previous = 0L
        while (true) {
            val nanos = withFrameNanos { it }
            if (previous != 0L) {
                state.value += ((nanos - previous) / 1_000_000L).coerceIn(0L, 100L)
            }
            previous = nanos
        }
    }
    return state
}
