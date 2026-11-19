package dev.jorge.flacbox

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.jorge.flacbox.data.MusicScanner
import dev.jorge.flacbox.data.Track
import dev.jorge.flacbox.data.toMediaItem
import dev.jorge.flacbox.data.toTrack
import dev.jorge.flacbox.playback.PlaybackService
import dev.jorge.flacbox.video.VideoCache
import dev.jorge.flacbox.video.VideoManager
import dev.jorge.flacbox.video.VideoPlaybackState
import dev.jorge.flacbox.video.VideoRegistry
import dev.jorge.flacbox.video.YouTubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PlayerMode { AUDIO, VIDEO }

data class UiState(
    val tracks: List<Track> = emptyList(),
    val albums: List<AlbumGroup> = emptyList(),
    val artists: List<ArtistGroup> = emptyList(),
    val queue: List<Track> = emptyList(),
    val current: Track? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
    val scanning: Boolean = false,
    val connected: Boolean = false,
    val search: String = "",
    val error: String? = null,
    val video: VideoPlaybackState? = null,
    val playerMode: PlayerMode = PlayerMode.VIDEO,
    val message: String? = null,
)

data class AlbumGroup(
    val key: String,
    val album: String,
    val artist: String,
    val tracks: List<Track>,
)

data class ArtistGroup(
    val name: String,
    val tracks: List<Track>,
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = MusicScanner(app)
    val videoManager = VideoManager(app)
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var controller: MediaController? = null
    private var tickerJob: Job? = null
    private var queueCount = -1

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            syncQueue()
            refresh()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()

        override fun onPlaybackStateChanged(playbackState: Int) = refresh()

        override fun onRepeatModeChanged(repeatMode: Int) {
            _ui.update { it.copy(repeatMode = repeatMode) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _ui.update { it.copy(shuffle = shuffleModeEnabled) }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _ui.update { it.copy(error = error.message) }
        }
    }

    init {
        connect()
        viewModelScope.launch {
            videoManager.state.collect { state ->
                _ui.update { it.copy(video = state) }
            }
        }
    }

    private fun connect() {
        val token = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java),
        )
        val future = MediaController.Builder(getApplication(), token).buildAsync()
        future.addListener(
            {
                val built = runCatching { future.get() }.getOrNull()
                if (built == null) {
                    _ui.update { it.copy(error = "No se pudo conectar con el servicio de audio") }
                    return@addListener
                }
                controller = built
                built.addListener(listener)
                _ui.update {
                    it.copy(
                        connected = true,
                        repeatMode = built.repeatMode,
                        shuffle = built.shuffleModeEnabled,
                    )
                }
                syncQueue()
                refresh()
                startTicker()
            },
            ContextCompat.getMainExecutor(getApplication()),
        )
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(300)
            }
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val current = c.currentMediaItem?.toTrack()
        val itemCount = c.mediaItemCount
        if (itemCount != queueCount) syncQueue()

        val video = videoManager.state.value
        val duration = when {
            video != null && video.durationMs > 0 -> video.durationMs
            c.duration != C.TIME_UNSET && c.duration > 0 -> c.duration
            current != null -> current.durationMs
            else -> 0L
        }

        _ui.update {
            it.copy(
                current = current ?: it.current,
                positionMs = if (video != null) {
                    video.positionMs
                } else if (current == null) {
                    0L
                } else {
                    c.currentPosition.coerceAtLeast(0L)
                },
                durationMs = duration,
                isPlaying = c.isPlaying || videoManager.isVideoPlaying,
                repeatMode = c.repeatMode,
                shuffle = c.shuffleModeEnabled,
            )
        }
    }

    private fun syncQueue() {
        val c = controller ?: return
        val count = c.mediaItemCount
        queueCount = count
        val list = (0 until count).map { c.getMediaItemAt(it).toTrack() }
        _ui.update { it.copy(queue = list) }
    }

    fun refreshLibrary() {
        if (_ui.value.scanning) return
        _ui.update { it.copy(scanning = true) }
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { scanner.scan() }
            val albums = groupAlbums(list)
            val artists = groupArtists(list)
            _ui.update {
                it.copy(
                    tracks = list,
                    albums = albums,
                    artists = artists,
                    scanning = false,
                )
            }
        }
    }

    private fun groupAlbums(tracks: List<Track>): List<AlbumGroup> {
        val groups = LinkedHashMap<String, MutableList<Track>>()
        for (track in tracks) {
            groups.getOrPut(track.albumKey) { mutableListOf() }.add(track)
        }
        return groups.map { (key, list) ->
            list.sortBy { it.trackNo }
            AlbumGroup(
                key = key,
                album = list.first().displayAlbum,
                artist = list.first().albumArtist.ifBlank { list.first().displayArtist },
                tracks = list,
            )
        }.sortedBy { it.album.lowercase() }
    }

    private fun groupArtists(tracks: List<Track>): List<ArtistGroup> =
        tracks.groupBy { it.albumArtist.ifBlank { it.displayArtist } }
            .map { (name, list) ->
                ArtistGroup(name, list.sortedBy { it.displayTitle.lowercase() })
            }
            .sortedBy { it.name.lowercase() }

    fun setSearch(value: String) = _ui.update { it.copy(search = value) }

    fun filteredTracks(): List<Track> {
        val query = _ui.value.search.trim()
        if (query.isEmpty()) return _ui.value.tracks
        return _ui.value.tracks.filter { track ->
            track.displayTitle.contains(query, ignoreCase = true) ||
                track.displayArtist.contains(query, ignoreCase = true) ||
                track.displayAlbum.contains(query, ignoreCase = true)
        }
    }

    // ---------------------------------------------------------------- play

    fun playTrack(track: Track) {
        if (_ui.value.playerMode == PlayerMode.AUDIO) {
            playAudio(track)
        } else {
            playVideoFor(track)
        }
    }

    fun setPlayerMode(mode: PlayerMode) {
        if (_ui.value.playerMode == mode) return
        _ui.update { it.copy(playerMode = mode) }
        val track = _ui.value.current ?: return
        when (mode) {
            PlayerMode.AUDIO -> {
                videoManager.stop()
                playAudio(track)
            }
            PlayerMode.VIDEO -> playVideoFor(track)
        }
    }

    private fun playVideoFor(track: Track) {
        val app = getApplication<Application>()
        val videoId = track.videoId
        if (videoId != null && VideoCache.isCached(app, videoId)) {
            startVideo(track, videoId)
            return
        }
        viewModelScope.launch {
            val found = runCatching { YouTubeClient.searchForTrack(track) }.getOrNull()
            if (found != null) {
                VideoRegistry.set(app, track.path, found.videoId)
                _ui.update { ui ->
                    ui.copy(
                        tracks = ui.tracks.map {
                            if (it.path == track.path) it.copy(videoId = found.videoId) else it
                        },
                    )
                }
                val ready = VideoCache.isCached(app, found.videoId) ||
                    withContext(Dispatchers.IO) {
                        found.streamUrl?.let { VideoCache.download(app, found.videoId, it) } ?: false
                    }
                if (ready && VideoCache.isCached(app, found.videoId)) {
                    startVideo(track.copy(videoId = found.videoId), found.videoId)
                    return@launch
                }
            }
            playAudio(track)
        }
    }

    private fun startVideo(track: Track, videoId: String) {
        controller?.pause()
        videoManager.play(videoId, VideoCache.videoFile(getApplication<Application>(), videoId))
        _ui.update { it.copy(current = track, isPlaying = true) }
    }

    private fun playAudio(track: Track) {
        val list = filteredTracks().ifEmpty { _ui.value.tracks }
        val index = list.indexOfFirst { it.path == track.path }
        playQueue(list, if (index >= 0) index else 0)
    }

    fun stopVideo() = videoManager.stop()

    fun addToQueue(track: Track) {
        val c = controller ?: return
        val index = if (c.mediaItemCount == 0) {
            0
        } else {
            (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
        }
        c.addMediaItem(index, track.toMediaItem())
        _ui.update { it.copy(message = "Se reproducirá a continuación: ${track.displayTitle}") }
    }

    fun clearVideoCache() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            videoManager.stop()
            withContext(Dispatchers.IO) {
                VideoCache.clear(app)
                VideoRegistry.clear(app)
            }
            _ui.update { ui ->
                ui.copy(
                    tracks = ui.tracks.map {
                        if (it.videoId != null) it.copy(videoId = null) else it
                    },
                    message = "Caché de videos eliminada",
                )
            }
        }
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    fun playAlbum(album: AlbumGroup) = playQueue(album.tracks, 0)

    fun playArtist(artist: ArtistGroup) = playQueue(artist.tracks, 0)

    fun playAll(shuffle: Boolean = false) {
        val list = filteredTracks()
        if (list.isEmpty()) return
        playQueue(list, 0, shuffle)
    }

    fun playQueue(tracks: List<Track>, index: Int, shuffle: Boolean? = null) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        if (shuffle != null) c.shuffleModeEnabled = shuffle
        c.setMediaItems(tracks.map { it.toMediaItem() }, index.coerceIn(0, tracks.lastIndex), 0L)
        c.prepare()
        c.play()
        syncQueue()
    }

    fun playFromQueue(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekToDefaultPosition(index)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        if (videoManager.isActive) {
            videoManager.togglePlay()
            return
        }
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = controller?.seekToNextMediaItem()

    fun previous() = controller?.seekToPreviousMediaItem()

    fun seekTo(positionMs: Long) {
        if (videoManager.isActive) {
            videoManager.seekTo(positionMs)
            return
        }
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun dismissError() = _ui.update { it.copy(error = null) }

    override fun onCleared() {
        tickerJob?.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        videoManager.release()
        super.onCleared()
    }
}
