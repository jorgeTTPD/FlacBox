package dev.jorge.flacbox.video

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class VideoPlaybackState(
    val videoId: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

class VideoManager(private val context: Context) {

    private val scope = MainScope()
    private val _state = MutableStateFlow<VideoPlaybackState?>(null)
    val state: StateFlow<VideoPlaybackState?> = _state.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    val player: ExoPlayer? get() = exoPlayer
    private var ticker: Job? = null

    val isActive: Boolean get() = _state.value != null
    val isVideoPlaying: Boolean get() = _state.value?.isPlaying == true

    fun play(videoId: String, file: File) {
        ticker?.cancel()
        val p = ensurePlayer()
        p.setMediaItem(MediaItem.fromUri(file.toURI().toString()))
        p.prepare()
        p.play()
        _state.value = VideoPlaybackState(videoId, true, 0L, 0L)
        ticker = scope.launch {
            while (isActive) {
                sync(p)
                delay(500L)
            }
        }
    }

    fun togglePlay() {
        val p = exoPlayer ?: return
        if (p.isPlaying) p.pause() else p.play()
        _state.value = _state.value?.copy(isPlaying = p.isPlaying)
    }

    fun seekTo(positionMs: Long) {
        val p = exoPlayer ?: return
        p.seekTo(positionMs.coerceAtLeast(0L))
        sync(p)
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        _state.value = null
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
    }

    fun release() {
        ticker?.cancel()
        ticker = null
        _state.value = null
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun ensurePlayer(): ExoPlayer {
        exoPlayer?.let { return it }
        val p = ExoPlayer.Builder(context).build()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value?.copy(isPlaying = isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    _state.value = null
                } else {
                    sync(p)
                }
            }
        })
        exoPlayer = p
        return p
    }

    private fun sync(p: ExoPlayer) {
        val duration = p.duration
        _state.value = _state.value?.copy(
            positionMs = p.currentPosition.coerceAtLeast(0L),
            durationMs = if (duration != C.TIME_UNSET && duration > 0) duration else 0L,
        )
    }
}
