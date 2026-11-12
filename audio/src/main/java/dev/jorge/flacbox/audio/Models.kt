package dev.jorge.flacbox.audio

/** Callback que el motor nativo invoca desde su hilo de control. */
interface EngineCallback {
    fun onEvent(type: Int, index: Int, value: Long, extra: Long, text: String?)
}

object PlaybackStatus {
    const val IDLE = 0
    const val LOADING = 1
    const val PLAYING = 2
    const val PAUSED = 3
    const val ENDED = 4
    const val ERROR = 5
}

object EngineEventType {
    const val STATUS = 1
    const val TRACK_STARTED = 2
    const val TRACK_ENDED = 3
    const val ERROR = 4
    const val QUEUE_ENDED = 5
    const val STREAM_INFO = 6
}

object RepeatMode {
    const val OFF = 0
    const val ALL = 1
    const val ONE = 2
}

data class EngineEvent(
    val type: Int,
    val index: Int,
    val value: Long,
    val extra: Long,
    val text: String?,
)

data class Snapshot(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val index: Int = -1,
    val status: Int = PlaybackStatus.IDLE,
    val sourceRate: Int = 0,
    val streamRate: Int = 0,
    val bits: Int = 0,
    val channels: Int = 0,
    val underruns: Long = 0L,
    val level: Float = 0f,
    val playing: Boolean = false,
) {
    val isPlaying: Boolean get() = playing && status == PlaybackStatus.PLAYING

    companion object {
        val EMPTY = Snapshot()
    }
}

data class TrackMeta(
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val genre: String?,
    val year: Int?,
    val track: Int?,
    val disc: Int?,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int,
    val hasArt: Boolean,
    val pictureMime: String?,
)
