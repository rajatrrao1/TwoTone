package com.hmj.twotone.music

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size

/**
 * Album covers, turned into 1-bit pictures that suit E-ink: grey levels become
 * patterns of black and white dots (Atkinson dithering, as on early Macs).
 */
object AlbumArt {
    private val cache = LruCache<String, Bitmap>(24)

    /**
     * The cover of [t] as a [dots]×[dots] two-colour bitmap, or null when the
     * track has no cover. Call off the main thread.
     */
    fun dithered(context: Context, t: Track, dots: Int, ink: Int, paper: Int): Bitmap? {
        val key = "${t.albumId}/$dots/$ink/$paper"
        cache.get(key)?.let { return it }
        val src = original(context, t, 300) ?: return null
        val out = dither(src, dots, ink, paper)
        cache.put(key, out)
        return out
    }

    private fun original(context: Context, t: Track, px: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            try { return context.contentResolver.loadThumbnail(t.uri, Size(px, px), null) } catch (e: Exception) { }
        } else {
            try {
                val art = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), t.albumId)
                context.contentResolver.openInputStream(art)?.use { return BitmapFactory.decodeStream(it) }
            } catch (e: Exception) { }
        }
        // Covers embedded in the file itself
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, t.uri)
            r.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } catch (e: Exception) {
            null
        } finally {
            try { r.release() } catch (e: Exception) { }
        }
    }

    /** Any picture → an n×n two-colour dithered bitmap (also used for book covers). */
    fun dither(src: Bitmap, n: Int, ink: Int, paper: Int): Bitmap {
        // Centre-crop to a square, shrink to n×n.
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val small = Bitmap.createScaledBitmap(square, n, n, true)
        val px = IntArray(n * n)
        small.getPixels(px, 0, n, 0, 0, n, n)

        // Grey levels, with a little extra contrast so covers don't turn to mush.
        val g = FloatArray(n * n) { i ->
            val c = px[i]
            val l = 0.299f * ((c shr 16) and 255) + 0.587f * ((c shr 8) and 255) + 0.114f * (c and 255)
            ((l - 128f) * 1.25f + 128f).coerceIn(0f, 255f)
        }
        val out = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val i = y * n + x
            val old = g[i]
            val new = if (old < 128f) 0f else 255f
            out[i] = if (new == 0f) ink else paper
            val err = (old - new) / 8f
            fun spread(dx: Int, dy: Int) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until n && yy < n) g[yy * n + xx] += err
            }
            spread(1, 0); spread(2, 0); spread(-1, 1); spread(0, 1); spread(1, 1); spread(0, 2)
        }
        return Bitmap.createBitmap(out, n, n, Bitmap.Config.ARGB_8888)
    }
}
