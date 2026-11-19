package dev.jorge.flacbox.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import dev.jorge.flacbox.audio.AudioEngine
import dev.jorge.flacbox.video.VideoRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val EXCLUDED_DIRS = listOf(
    "/ringtones/",
    "/alarms/",
    "/notifications/",
    "/ui/",
    "/android/",
)

class MusicScanner(private val context: Context) {

    suspend fun scan(): List<Track> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, Track>()

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DATA)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.ALBUM_ARTIST)
            }
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
        }.toTypedArray()

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val cursor = try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )
        } catch (e: SecurityException) {
            null
        } ?: return@withContext emptyList()

        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumArtistCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

            while (c.moveToNext()) {
                val path = c.getString(dataCol) ?: continue
                if (path.isBlank()) continue
                val lower = path.lowercase()
                if (EXCLUDED_DIRS.any { lower.contains(it) }) continue

                val duration = c.getLong(durationCol)
                if (duration < 2_000L) continue
                if (AudioEngine.readMeta(path)?.hasArt != true) continue

                val id = c.getLong(idCol)
                val rawTrack = c.getInt(trackCol)
                val track = Track(
                    id = id,
                    path = path,
                    title = c.getString(titleCol).orEmpty(),
                    artist = c.getString(artistCol).orEmpty().cleanArtist(),
                    album = c.getString(albumCol).orEmpty(),
                    albumArtist = if (albumArtistCol >= 0) {
                        c.getString(albumArtistCol).orEmpty().cleanArtist()
                    } else {
                        ""
                    },
                    durationMs = duration,
                    trackNo = rawTrack % 1000,
                    year = c.getInt(yearCol),
                    videoId = VideoRegistry.get(context, path),
                )
                found[path] = track
            }
        }

        found.values.toList()
    }

    fun contentUri(id: Long) = ContentUris.withAppendedId(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        id,
    )
}

private fun String.cleanArtist(): String =
    if (this == "<unknown>" || this == MediaStore.UNKNOWN_STRING) "" else this
