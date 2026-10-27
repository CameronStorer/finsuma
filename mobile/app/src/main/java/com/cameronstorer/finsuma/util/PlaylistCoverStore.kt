package com.cameronstorer.finsuma.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Finsuma: a picture the user picked for a playlist, kept on this phone (the Subsonic API can't
 * set playlist pictures). Keyed by playlist id; Navidrome's cover ids look like "pl-<id>".
 */
object PlaylistCoverStore {
    private const val SIZE = 800

    private fun key(id: String) = id.removePrefix("pl-").replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun dir(context: Context) = File(context.applicationContext.filesDir, "playlist_covers")

    @JvmStatic
    fun file(context: Context, id: String?): File? {
        if (id.isNullOrBlank()) return null
        return File(dir(context), key(id) + ".jpg").takeIf { it.isFile }
    }

    /** Reads [uri], scales it to at most 800 px and saves it. Returns false if it isn't a readable image. */
    @JvmStatic
    fun save(context: Context, id: String, uri: Uri): Boolean = try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= SIZE && bounds.outHeight / (sample * 2) >= SIZE) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return false
        val scale = minOf(1f, SIZE.toFloat() / maxOf(bitmap.width, bitmap.height))
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        dir(context).mkdirs()
        File(dir(context), key(id) + ".jpg").outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    } catch (t: Throwable) {
        false
    }

    @JvmStatic
    fun clear(context: Context, id: String) {
        file(context, id)?.delete()
    }
}
