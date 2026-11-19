package dev.jorge.flacbox.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File

data class Track(
    val id: Long,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val durationMs: Long,
    val trackNo: Int,
    val year: Int,
    val videoId: String? = null,
) {
    val displayTitle: String get() = title.ifBlank { fileName() }

    val displayArtist: String get() = artist.ifBlank { "Artista desconocido" }

    val displayAlbum: String get() = album.ifBlank { "Álbum desconocido" }

    val albumKey: String get() = albumArtist.ifBlank { displayArtist } + "\u0000" + displayAlbum

    private fun fileName(): String =
        path.substringAfterLast('/').substringBeforeLast('.').ifBlank { path }
}

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(path)
    .setUri(Uri.fromFile(File(path)))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(displayTitle)
            .setArtist(displayArtist)
            .setAlbumTitle(displayAlbum)
            .setAlbumArtist(albumArtist.ifBlank { null })
            .setTrackNumber(trackNo.takeIf { it > 0 })
            .setYear(year.takeIf { it > 0 })
            .setDurationMs(durationMs)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()
    )
    .build()

/** Reconstruye la pista a partir del MediaItem que viaja por la MediaSession. */
fun MediaItem.toTrack(): Track {
    val meta = mediaMetadata
    val filePath = mediaId.ifEmpty { localConfiguration?.uri?.path.orEmpty() }
    return Track(
        id = filePath.hashCode().toLong(),
        path = filePath,
        title = meta.title?.toString().orEmpty(),
        artist = meta.artist?.toString().orEmpty(),
        album = meta.albumTitle?.toString().orEmpty(),
        albumArtist = meta.albumArtist?.toString().orEmpty(),
        durationMs = meta.durationMs ?: 0L,
        trackNo = meta.trackNumber ?: 0,
        year = meta.year ?: 0,
    )
}
