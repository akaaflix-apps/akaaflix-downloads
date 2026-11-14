package com.akaaflix.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.akaaflix.launcher.wallpaper.model.Wallpaper
import com.akaaflix.launcher.wallpaper.model.WallpaperType
import java.io.FileInputStream
import java.io.InputStream

/**
 * Memory-efficient decoding: reads bounds first, decodes with a power-of-two sample size so an
 * 8K image never allocates more than ~2x the screen, then scales to "cover" the target exactly.
 */
object WallpaperDecoder {
    /** Largest power of two keeping the decoded image at least [reqW] x [reqH]. */
    fun sampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        if (srcW <= 0 || srcH <= 0 || reqW <= 0 || reqH <= 0) return 1
        var s = 1
        while (srcW / (s * 2) >= reqW && srcH / (s * 2) >= reqH) s *= 2
        return s
    }

    /** Size that covers [reqW]x[reqH] keeping the source aspect ratio (no distortion). */
    fun coverSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Pair<Int, Int> {
        val scale = maxOf(reqW / srcW.toFloat(), reqH / srcH.toFloat())
        return Pair((srcW * scale).toInt().coerceAtLeast(1), (srcH * scale).toInt().coerceAtLeast(1))
    }

    fun open(context: Context, wp: Wallpaper): InputStream? = try {
        when {
            wp.file != null -> FileInputStream(wp.file)
            wp.rawRes != 0 -> context.resources.openRawResource(wp.rawRes)
            else -> null
        }
    } catch (_: Exception) { null }

    fun decode(context: Context, wp: Wallpaper, reqW: Int, reqH: Int, rgb565: Boolean): Bitmap? {
        if (wp.type == WallpaperType.VIDEO) return videoFrame(wp, reqW, reqH)
        return decodeStream({ open(context, wp) }, reqW, reqH, rgb565)
    }

    fun decodeStream(open: () -> InputStream?, reqW: Int, reqH: Int, rgb565: Boolean): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
                inPreferredConfig = if (rgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            }
            val decoded = open()?.use { BitmapFactory.decodeStream(it, null, opts) }
            decoded?.let { fit(it, reqW, reqH) }
        }
    } catch (_: OutOfMemoryError) { null } catch (_: Exception) { null }

    /** Scales down when the decoded image is much larger than needed (keeps aspect ratio). */
    fun fit(bmp: Bitmap, reqW: Int, reqH: Int): Bitmap {
        val (cw, ch) = coverSize(bmp.width, bmp.height, reqW, reqH)
        if (cw.toLong() * ch * 1.3 >= bmp.width.toLong() * bmp.height) return bmp
        val scaled = Bitmap.createScaledBitmap(bmp, cw, ch, true)
        if (scaled !== bmp) bmp.recycle()
        return scaled
    }

    fun videoFrame(wp: Wallpaper, reqW: Int, reqH: Int): Bitmap? {
        val f = wp.file ?: return null
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.path)
            r.getFrameAtTime(1_000_000)?.let { fit(it, reqW, reqH) }
        } catch (_: Exception) { null } finally { try { r.release() } catch (_: Exception) {} }
    }
}
