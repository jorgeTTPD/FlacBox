package dev.jorge.flacbox.audio

/**
 * Enlaza con libflacplayer.so.
 *
 * Los métodos son `external` dentro de un `object`, así que en la JVM son métodos
 * de instancia del singleton: cada función nativa recibe (JNIEnv, jobject).
 */
internal object NativeAudio {

    init {
        System.loadLibrary("flacplayer")
    }

    external fun create(callback: EngineCallback): Long
    external fun release(handle: Long)

    external fun setQueue(handle: Long, paths: Array<String>, startIndex: Int, startPositionMs: Long)
    external fun play(handle: Long)
    external fun pause(handle: Long)
    external fun stop(handle: Long)
    external fun next(handle: Long)
    external fun playIndex(handle: Long, index: Int)
    external fun previous(handle: Long)
    external fun seekTo(handle: Long, ms: Long)
    external fun setRepeat(handle: Long, mode: Int)
    external fun setShuffle(handle: Long, enabled: Boolean)
    external fun setVolume(handle: Long, volume: Float)

    external fun snapshot(handle: Long): LongArray?

    external fun readMeta(path: String): String?
    external fun probe(path: String): String?
    external fun extractArt(path: String, outPath: String): Boolean

    external fun version(): String
}
