package dev.jorge.flacbox.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.jorge.flacbox.audio.AudioEngine
import java.io.File

/**
 * Carátulas incrustadas: el motor nativo (lofty) las extrae a la caché y aquí
 * se decodifican con el tamaño justo para no llenar la memoria.
 */
object Artwork {

    private val cache = LruCache<String, ImageBitmap>(64)

    private fun dir(context: Context) = File(context.cacheDir, "artwork")

    fun fileFor(context: Context, track: Track): File? {
        if (track.path.isBlank()) return null
        val dir = dir(context)
        if (!dir.exists() && !dir.mkdirs()) return null

        val out = File(dir, "${track.path.hashCode().toUInt().toString(16)}.img")
        if (out.exists() && out.length() > 0L) return out

        val ok = runCatching { AudioEngine.extractArt(track.path, out.absolutePath) }
            .getOrDefault(false)
        return if (ok && out.length() > 0L) out else null
    }

    fun imageFor(context: Context, track: Track, targetPx: Int = 512): ImageBitmap? {
        val key = "${track.path}@$targetPx"
        cache.get(key)?.let { return it }

        val file = fileFor(context, track) ?: return null
        val bitmap = decodeSampled(file, targetPx) ?: return null
        val image = bitmap.asImageBitmap()
        cache.put(key, image)
        return image
    }

    private fun decodeSampled(file: File, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx &&
            bounds.outHeight / (sample * 2) >= targetPx
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
    }

    fun clear(context: Context) {
        cache.evictAll()
        dir(context).deleteRecursively()
    }
}
