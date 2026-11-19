package dev.jorge.flacbox.video

import android.content.Context

object VideoRegistry {

    private const val NAME = "flacbox_video_registry"

    fun get(context: Context, path: String): String? =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(path, null)

    fun set(context: Context, path: String, videoId: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(path, videoId)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
