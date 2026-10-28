package com.firetv.immichslideshow.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.firetv.immichslideshow.AppContainer
import com.firetv.immichslideshow.data.Album
import com.firetv.immichslideshow.data.DemoLibrary
import com.firetv.immichslideshow.data.ImmichConfig
import com.firetv.immichslideshow.ui.components.AppBackground
import com.firetv.immichslideshow.ui.components.ErrorNotice
import com.firetv.immichslideshow.ui.components.FocusableSurface
import com.firetv.immichslideshow.ui.components.KeyHint
import com.firetv.immichslideshow.ui.components.Spinner
import com.firetv.immichslideshow.ui.components.TvButton
import com.firetv.immichslideshow.ui.theme.Palette

private sealed interface AlbumListState {
    data object Loading : AlbumListState
    data class Ready(val albums: List<Album>) : AlbumListState
    data class Failed(val message: String) : AlbumListState
}

/** Album browser: the launch screen once a server is configured. */
@Composable
fun AlbumListScreen(
    container: AppContainer,
    config: ImmichConfig,
    onOpenAlbum: (Album) -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
) {
    var state by remember(config) { mutableStateOf<AlbumListState>(AlbumListState.Loading) }
    var reloadToken by remember { mutableStateOf(0) }

    LaunchedEffect(config, reloadToken) {
        state = AlbumListState.Loading
        val result = if (config.demo) {
            Result.success(listOf(DemoLibrary.album))
        } else {
            container.repository(config).albums()
        }
        state = result.fold(
            onSuccess = { AlbumListState.Ready(it) },
            onFailure = { AlbumListState.Failed(it.message ?: "Could not load albums.") },
        )
    }

    AppBackground(
        modifier = Modifier.onKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp && event.key == Key.Menu) {
                onOpenSettings()
                true
            } else {
                false
            }
        },
    ) {
        Column(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 44.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (config.demo) "DEMO LIBRARY" else "IMMICH · " + config.displayHost().uppercase(),
                            color = Palette.Amber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 2.6.sp,
                            maxLines = 1,
                        )
                        if (state is AlbumListState.Ready) {
                            val count = (state as AlbumListState.Ready).albums.size
                            Text(
                                text = "  ·  $count ${if (count == 1) "album" else "albums"}",
                                color = Palette.MutedDim,
                                fontSize = 12.sp,
                                letterSpacing = 1.4.sp,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Choose an album",
                        color = Palette.Cream,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(label = "Refresh", onClick = { reloadToken++ })
                    TvButton(label = "Connection", onClick = onOpenSettings)
                }
            }

            Spacer(Modifier.height(30.dp))

            when (val current = state) {
                is AlbumListState.Loading -> LoadingAlbums(config)
                is AlbumListState.Failed -> Column(Modifier.padding(top = 20.dp)) {
                    ErrorNotice(
                        message = current.message,
                        onRetry = { reloadToken++ },
                        retryLabel = "Retry",
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Using ${config.normalizedBaseUrl()}",
                        color = Palette.MutedDim,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(18.dp))
                    TvButton(label = "Edit connection", onClick = onOpenSettings)
                }

                is AlbumListState.Ready -> {
                    if (current.albums.isEmpty()) {
                        Column(Modifier.padding(top = 40.dp)) {
                            Text(
                                text = "No albums found",
                                color = Palette.Cream,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Create an album in Immich, add some photos, then refresh.",
                                color = Palette.Muted,
                                fontSize = 17.sp,
                            )
                            Spacer(Modifier.height(20.dp))
                            TvButton(label = "Refresh", onClick = { reloadToken++ }, accent = true)
                        }
                    } else {
                        AlbumGrid(
                            albums = current.albums,
                            config = config,
                            container = container,
                            onOpenAlbum = onOpenAlbum,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                KeyHint(keys = "OK", label = "Play album")
                KeyHint(keys = "MENU", label = "Connection")
                KeyHint(keys = "BACK", label = "Exit")
            }
        }
    }
}

@Composable
private fun LoadingAlbums(config: ImmichConfig) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spinner(size = 42.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = if (config.demo) "Preparing the demo library…" else "Talking to ${config.displayHost()}…",
            color = Palette.Muted,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun AlbumGrid(
    albums: List<Album>,
    config: ImmichConfig,
    container: AppContainer,
    onOpenAlbum: (Album) -> Unit,
) {
    TvLazyVerticalGrid(
        columns = TvGridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        state = rememberTvLazyGridState(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
        horizontalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(
                album = album,
                config = config,
                container = container,
                onClick = { onOpenAlbum(album) },
            )
        }
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    config: ImmichConfig,
    container: AppContainer,
    onClick: () -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.Transparent,
        focusedColor = Color.Transparent,
        borderColor = Color.Transparent,
        focusedBorderColor = Palette.Amber,
        contentPadding = PaddingValues(0.dp),
    ) { focused ->
        Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 14.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.Panel),
            ) {
                AlbumCover(album = album, config = config, container = container)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0x4D000000), Color(0x8F000000)),
                            ),
                        ),
                )
                // One fact per place: the year sits on the cover, the photo count
                // under the name, so nothing on the card repeats.
                Text(
                    text = album.yearLabel ?: "${album.assetCount}",
                    color = Palette.Cream.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xB305070A))
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                )
                if (focused) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .width(56.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xCCF2A45C)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "▶", color = Color(0xFF21150A), fontSize = 20.sp)
                    }
                }
            }
            Spacer(Modifier.height(11.dp))
            Text(
                text = album.name,
                color = Palette.Cream,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append(album.assetCount)
                    append(if (album.assetCount == 1) " photo" else " photos")
                },
                color = Palette.MutedDim,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AlbumCover(album: Album, config: ImmichConfig, container: AppContainer) {
    if (config.demo) {
        val bitmap = remember(album.id) {
            // The album's own cover photo, so the card matches what plays.
            DemoLibrary.bitmap(DemoLibrary.indexOf(album.coverAssetId ?: ""), 640, 360)
        }
        Image(
            bitmap = bitmap,
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val context = LocalContext.current
    val assetId = album.coverAssetId
    if (assetId == null) {
        CoverFallback(album.name)
        return
    }
    val request = remember(assetId, config) {
        ImageRequest.Builder(context)
            .data(container.repository(config).thumbnailUrl(assetId, preview = true))
            .size(640, 360)
            .scale(Scale.FIT)
            .crossfade(180)
            .build()
    }
    AsyncImage(
        model = request,
        imageLoader = container.imageLoader,
        contentDescription = album.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun CoverFallback(name: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF1B2130), Color(0xFF0C1017)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            color = Palette.Amber.copy(alpha = 0.7f),
            fontSize = 44.sp,
            fontWeight = FontWeight.Light,
        )
    }
}
