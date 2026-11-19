package dev.jorge.flacbox.playback

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.SimpleBasePlayer.State
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.jorge.flacbox.audio.AudioEngine
import dev.jorge.flacbox.audio.PlaybackStatus
import dev.jorge.flacbox.audio.RepeatMode
import dev.jorge.flacbox.data.toTrack
import kotlin.math.abs

/**
 * Puente entre la MediaSession y el motor nativo. El motor es la fuente de
 * verdad de la reproducción (índice, posición, shuffle y repeat); aquí solo se
 * traduce a estado de media3 para la sesión, la notificación y la UI.
 */
@UnstableApi
class EnginePlayer(
    looper: Looper,
    context: Context,
) : SimpleBasePlayer(looper) {

    private val handler = Handler(looper)
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var items: List<MediaItem> = emptyList()
    private var playlistData: List<MediaItemData> = emptyList()

    private var currentIndex = 0
    private var playWhenReady = false
    private var repeatMode = Player.REPEAT_MODE_OFF
    private var shuffleModeEnabled = false
    private var volume = 1f
    private var seekTargetMs: Long? = null

    private var hasFocus = false
    private var pausedForFocus = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                abandonFocus()
                applyPause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausedForFocus = true
                applyPause()
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedForFocus) {
                    pausedForFocus = false
                    applyPlay()
                }
            }
        }
    }

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setOnAudioFocusChangeListener(focusListener, handler)
        .build()

    private val ticker = object : Runnable {
        override fun run() {
            invalidateState()
            handler.postDelayed(this, if (playWhenReady) 250L else 800L)
        }
    }

    init {
        handler.post(ticker)
    }

    // ------------------------------------------------------------- estado

    override fun getState(): State {
        val snap = AudioEngine.snapshot()

        if (snap.status != PlaybackStatus.LOADING &&
            snap.index >= 0 && snap.index < items.size
        ) {
            currentIndex = snap.index
        }

        val target = seekTargetMs
        val positionMs = when {
            items.isEmpty() -> 0L
            target != null && abs(snap.positionMs - target) > 1_500L -> target
            else -> {
                seekTargetMs = null
                snap.positionMs
            }
        }

        val playbackState = when {
            items.isEmpty() -> Player.STATE_IDLE
            snap.status == PlaybackStatus.LOADING -> Player.STATE_BUFFERING
            snap.status == PlaybackStatus.ENDED -> Player.STATE_ENDED
            snap.status == PlaybackStatus.IDLE -> Player.STATE_IDLE
            snap.status == PlaybackStatus.ERROR -> Player.STATE_IDLE
            else -> Player.STATE_READY
        }

        val builder = State.Builder()
            .setAvailableCommands(COMMANDS)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setRepeatMode(repeatMode)
            .setShuffleModeEnabled(shuffleModeEnabled)
            .setVolume(volume)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(30_000L)

        if (items.isNotEmpty()) {
            builder
                .setPlaylist(playlistData)
                .setCurrentMediaItemIndex(currentIndex.coerceIn(0, items.lastIndex))
                .setContentPositionMs(positionMs)
        }

        return builder.build()
    }

    // ------------------------------------------------------------ comandos

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        items = mediaItems
        playlistData = mediaItems.mapIndexed { index, item -> itemData(index, item) }

        val start = if (startIndex == C.INDEX_UNSET) {
            0
        } else {
            startIndex.coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0))
        }
        currentIndex = start
        val position = if (startPositionMs == C.TIME_UNSET) 0L else startPositionMs
        seekTargetMs = position

        if (mediaItems.isNotEmpty()) {
            AudioEngine.setQueue(mediaItems.map { it.mediaId }, start, position)
            AudioEngine.setRepeat(repeatMode.toEngine())
            AudioEngine.setShuffle(shuffleModeEnabled)
            AudioEngine.setVolume(volume)
            if (playWhenReady) AudioEngine.play() else AudioEngine.pause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) {
            applyPlay()
        } else {
            applyPause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        playWhenReady = false
        abandonFocus()
        AudioEngine.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        handler.removeCallbacks(ticker)
        playWhenReady = false
        abandonFocus()
        AudioEngine.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            -> {
                seekTargetMs = 0L
                AudioEngine.next()
            }

            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            -> {
                seekTargetMs = 0L
                AudioEngine.previous()
            }

            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            -> {
                if (mediaItemIndex != currentIndex && mediaItemIndex in items.indices) {
                    currentIndex = mediaItemIndex
                    seekTargetMs = 0L
                    AudioEngine.playIndex(mediaItemIndex)
                } else {
                    seekTargetMs = positionMs
                    AudioEngine.seekTo(positionMs)
                }
            }

            else -> {
                seekTargetMs = positionMs
                AudioEngine.seekTo(positionMs)
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        this.repeatMode = repeatMode
        AudioEngine.setRepeat(repeatMode.toEngine())
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        this.shuffleModeEnabled = shuffleModeEnabled
        AudioEngine.setShuffle(shuffleModeEnabled)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        this.volume = volume.coerceIn(0f, 1f)
        AudioEngine.setVolume(this.volume)
        return Futures.immediateVoidFuture()
    }

    override fun handleAddMediaItems(
        index: Int,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<*> {
        if (mediaItems.isEmpty()) return Futures.immediateVoidFuture()
        val insertAt = if (index == C.INDEX_UNSET) items.size else index.coerceIn(0, items.size)
        val updated = items.toMutableList().also { it.addAll(insertAt, mediaItems) }
        val position = AudioEngine.snapshot().positionMs
        items = updated
        playlistData = items.mapIndexed { i, item -> itemData(i, item) }
        AudioEngine.setQueue(items.map { it.mediaId }, currentIndex, position)
        if (playWhenReady) AudioEngine.play()
        return Futures.immediateVoidFuture()
    }

    override fun handleMoveMediaItems(
        fromIndex: Int,
        toIndex: Int,
        newIndex: Int,
    ): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleRemoveMediaItems(
        fromIndex: Int,
        toIndex: Int,
    ): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleSetPlaylistMetadata(playlistMetadata: MediaMetadata): ListenableFuture<*> =
        Futures.immediateVoidFuture()

    // ---------------------------------------------------------- internos

    private fun applyPlay() {
        if (!requestFocus()) {
            playWhenReady = false
            return
        }
        playWhenReady = true
        pausedForFocus = false
        AudioEngine.play()
    }

    private fun applyPause() {
        playWhenReady = false
        AudioEngine.pause()
    }

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        val result = audioManager.requestAudioFocus(focusRequest)
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        audioManager.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }

    private fun itemData(index: Int, item: MediaItem): MediaItemData =
        MediaItemData.Builder("$index\u0000${item.mediaId}")
            .setMediaItem(item)
            .setMediaMetadata(item.mediaMetadata)
            .setDurationUs(
                (item.mediaMetadata.durationMs ?: 0L) * 1_000L
            )
            .setIsSeekable(true)
            .build()

    private fun Int.toEngine(): Int = when (this) {
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }

    private companion object {
        val COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SET_SHUFFLE_MODE,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_SET_VOLUME,
                Player.COMMAND_RELEASE,
            )
            .addAllReadOnlyCommands()
            .build()
    }
}
