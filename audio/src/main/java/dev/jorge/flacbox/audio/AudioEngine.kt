package dev.jorge.flacbox.audio

import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

/**
 * Fachada del motor nativo. Es un singleton de proceso: el servicio de
 * reproducción y la UI comparten la misma instancia.
 */
object AudioEngine {

    private const val TAG = "FlacBox.Engine"

    private var handle: Long = 0L

    private val _events = MutableSharedFlow<EngineEvent>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    private val callback = object : EngineCallback {
        override fun onEvent(type: Int, index: Int, value: Long, extra: Long, text: String?) {
            _events.tryEmit(EngineEvent(type, index, value, extra, text))
        }
    }

    @Synchronized
    private fun engine(): Long {
        if (handle == 0L) {
            handle = NativeAudio.create(callback)
            Log.i(TAG, "motor nativo v${runCatching { NativeAudio.version() }.getOrDefault("?")} creado")
        }
        return handle
    }

    fun setQueue(paths: List<String>, startIndex: Int, startPositionMs: Long = 0L) {
        if (paths.isEmpty()) return
        NativeAudio.setQueue(engine(), paths.toTypedArray(), startIndex, startPositionMs)
    }

    fun play() = NativeAudio.play(engine())
    fun pause() = NativeAudio.pause(engine())
    fun stop() = NativeAudio.stop(engine())
    fun next() = NativeAudio.next(engine())
    fun playIndex(index: Int) = NativeAudio.playIndex(engine(), index)
    fun previous() = NativeAudio.previous(engine())
    fun seekTo(positionMs: Long) = NativeAudio.seekTo(engine(), positionMs.coerceAtLeast(0L))
    fun setRepeat(mode: Int) = NativeAudio.setRepeat(engine(), mode)
    fun setShuffle(enabled: Boolean) = NativeAudio.setShuffle(engine(), enabled)
    fun setVolume(volume: Float) = NativeAudio.setVolume(engine(), volume)

    fun snapshot(): Snapshot {
        val raw = NativeAudio.snapshot(engine()) ?: return Snapshot.EMPTY
        if (raw.size < 11) return Snapshot.EMPTY
        return Snapshot(
            positionMs = raw[0],
            durationMs = raw[1],
            index = raw[2].toInt(),
            status = raw[3].toInt(),
            sourceRate = raw[4].toInt(),
            streamRate = raw[5].toInt(),
            bits = raw[6].toInt(),
            channels = raw[7].toInt(),
            underruns = raw[8],
            level = Float.fromBits(raw[9].toInt()),
            playing = raw[10] != 0L,
        )
    }

    fun readMeta(path: String): TrackMeta? = runCatching {
        val json = NativeAudio.readMeta(path) ?: return null
        val obj = JSONObject(json)
        if (!obj.optBoolean("ok", false)) return null
        TrackMeta(
            title = obj.stringOrNull("title"),
            artist = obj.stringOrNull("artist"),
            album = obj.stringOrNull("album"),
            albumArtist = obj.stringOrNull("albumArtist"),
            genre = obj.stringOrNull("genre"),
            year = obj.intOrNull("year"),
            track = obj.intOrNull("track"),
            disc = obj.intOrNull("disc"),
            durationMs = obj.optLong("durationMs"),
            sampleRate = obj.optInt("sampleRate"),
            channels = obj.optInt("channels"),
            bitDepth = obj.optInt("bitDepth"),
            hasArt = obj.optBoolean("hasArt"),
            pictureMime = obj.stringOrNull("pictureMime"),
        )
    }.getOrNull()

    /** Extrae la carátula incrustada. Devuelve true si escribió el fichero. */
    fun extractArt(path: String, outPath: String): Boolean =
        runCatching { NativeAudio.extractArt(path, outPath) }.getOrDefault(false)

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.intOrNull(key: String): Int? =
        if (isNull(key)) null else optInt(key)
}
