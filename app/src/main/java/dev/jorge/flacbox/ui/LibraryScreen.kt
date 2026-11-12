package dev.jorge.flacbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jorge.flacbox.PlayerViewModel
import dev.jorge.flacbox.UiState

@Composable
fun LibraryScreen(
    ui: UiState,
    viewModel: PlayerViewModel,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tracks = viewModel.filteredTracks()

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "FlacBox",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = when {
                            ui.scanning -> "Escaneando biblioteca…"
                            ui.tracks.isEmpty() -> "Sin música todavía"
                            else -> "${ui.tracks.size} canciones · ${ui.albums.size} álbumes"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { viewModel.refreshLibrary() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reescanear")
                }
                IconButton(
                    onClick = { viewModel.playAll(shuffle = true) },
                    enabled = ui.tracks.isNotEmpty(),
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = "Aleatorio")
                }
                IconButton(
                    onClick = { viewModel.playAll() },
                    enabled = tracks.isNotEmpty(),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Reproducir todo")
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = ui.search,
                onValueChange = viewModel::setSearch,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar canción, artista o álbum") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    if (ui.search.isNotEmpty()) {
                        TextButton(onClick = { viewModel.setSearch("") }) { Text("Limpiar") }
                    }
                },
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabChip("Canciones", tab == 0) { tab = 0 }
                TabChip("Álbumes", tab == 1) { tab = 1 }
                TabChip("Artistas", tab == 2) { tab = 2 }
            }
        }

        if (tracks.isEmpty()) {
            EmptyState(
                title = if (ui.scanning) "Leyendo la biblioteca" else "No hay canciones",
                subtitle = if (ui.search.isNotBlank()) {
                    "Nada coincide con «${ui.search}»"
                } else {
                    "Copia tus FLAC al teléfono y vuelve a escanear"
                },
            )
        } else when (tab) {
            0 -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                itemsIndexed(tracks, key = { _, track -> track.path }) { _, track ->
                    val isCurrent = ui.current?.path == track.path
                    TrackRow(
                        track = track,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && ui.isPlaying,
                        onClick = {
                            if (isCurrent) onOpenNowPlaying() else viewModel.playTrack(track)
                        },
                    )
                }
            }

            1 -> {
                val albums = if (ui.search.isBlank()) ui.albums else {
                    ui.albums.filter { album ->
                        tracks.any { it.albumKey == album.key }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(148.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(albums, key = { it.key }) { album ->
                        AlbumCard(
                            albumTitle = album.album,
                            artist = album.artist,
                            artworkTrack = album.tracks.firstOrNull(),
                            onClick = { viewModel.playAlbum(album) },
                        )
                    }
                }
            }

            else -> {
                val artists = ui.artists.filter { artist ->
                    artist.tracks.any { track -> tracks.any { it.path == track.path } }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(artists, key = { it.name }) { artist ->
                        ArtistRow(
                            name = artist.name,
                            subtitle = if (artist.tracks.size == 1) "1 canción" else "${artist.tracks.size} canciones",
                            artworkTrack = artist.tracks.firstOrNull(),
                            onClick = { viewModel.playArtist(artist) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}
