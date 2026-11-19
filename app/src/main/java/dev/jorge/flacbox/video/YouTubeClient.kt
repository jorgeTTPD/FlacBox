package dev.jorge.flacbox.video

import android.net.Uri
import com.yushosei.newpipe.extractor.MediaFormat
import com.yushosei.newpipe.extractor.NewPipe
import com.yushosei.newpipe.extractor.stream.StreamInfoItem
import com.yushosei.newpipe.extractor.stream.VideoStream
import com.yushosei.newpipe.util.DefaultDownloaderImpl
import com.yushosei.newpipe.util.ExtractorHelper
import dev.jorge.flacbox.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class VideoResult(
    val videoId: String,
    val title: String,
    val uploader: String,
    val durationMs: Long,
    val streamUrl: String?,
)

object YouTubeClient {

    private const val SERVICE_ID = 0
    private val initialized = AtomicBoolean(false)

    private suspend fun init() {
        if (!initialized.getAndSet(true)) {
            NewPipe.init(DefaultDownloaderImpl.initDefault())
        }
    }

    suspend fun searchForTrack(track: Track): VideoResult? = withContext(Dispatchers.IO) {
        init()
        val query = listOf(track.displayTitle, track.displayArtist)
            .filter { it.isNotBlank() && it != "Artista desconocido" }
            .joinToString(" ")
            .ifBlank { return@withContext null }

        val items = ExtractorHelper.searchFor(SERVICE_ID, query, listOf("videos"), "")
            .relatedItems
            .filterIsInstance<StreamInfoItem>()

        for (item in items.take(3)) {
            try {
                val info = ExtractorHelper.getStreamInfo(item.serviceId, item.url)
                val stream = info.videoStreams
                    .filter { it.isUrl() }
                    .maxWithOrNull(
                        compareBy<VideoStream> { if (it.format == MediaFormat.MPEG_4) 1 else 0 }
                            .thenBy { it.height }
                            .thenBy { it.fps }
                            .thenBy { it.bitrate }
                    )
                val url = stream?.content
                if (!url.isNullOrBlank()) {
                    val videoId = Uri.parse(item.url).getQueryParameter("v")?.takeIf { it.isNotBlank() }
                        ?: item.url
                    return@withContext VideoResult(
                        videoId = videoId,
                        title = item.name,
                        uploader = item.uploaderName.orEmpty(),
                        durationMs = item.duration * 1000,
                        streamUrl = url,
                    )
                }
            } catch (e: Exception) {
                continue
            }
        }
        null
    }
}
