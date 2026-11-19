package dev.jorge.flacbox.video

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object VideoCache {

    private const val DIR = "video-cache"

    fun videoFile(context: Context, videoId: String): File =
        File(context.filesDir, DIR).resolve("${sanitize(videoId)}.mp4")

    fun isCached(context: Context, videoId: String): Boolean {
        val file = videoFile(context, videoId)
        return file.exists() && file.length() > 0L
    }

    fun download(
        context: Context,
        videoId: String,
        url: String,
        onProgress: ((Long, Long) -> Unit)? = null,
    ): Boolean {
        val file = videoFile(context, videoId)
        if (file.exists() && file.length() > 0L) return true
        val dir = file.parentFile ?: return false
        if (!dir.exists() && !dir.mkdirs()) return false

        val part = File(dir, "${file.name}.part")
        val connection = URL(url).openConnection() as? HttpURLConnection ?: return false
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return false
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        onProgress?.invoke(done, total)
                    }
                }
            }
            if (part.length() <= 0L) {
                part.delete()
                false
            } else {
                part.renameTo(file) || (part.copyTo(file, overwrite = true).let { part.delete(); true })
            }
        } catch (e: Exception) {
            part.delete()
            false
        } finally {
            connection.disconnect()
        }
    }

    fun clear(context: Context) {
        File(context.filesDir, DIR).deleteRecursively()
    }

    private fun sanitize(videoId: String): String =
        videoId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
}
