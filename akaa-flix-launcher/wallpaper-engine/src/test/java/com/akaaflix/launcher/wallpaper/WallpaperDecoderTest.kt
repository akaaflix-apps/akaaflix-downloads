package com.akaaflix.launcher.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.akaaflix.launcher.wallpaper.model.Wallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WallpaperDecoderTest {
    @Test fun sampleSizes() {
        assertEquals(4, WallpaperDecoder.sampleSize(7680, 4320, 1920, 1080))
        assertEquals(2, WallpaperDecoder.sampleSize(3840, 2160, 1920, 1080))
        assertEquals(1, WallpaperDecoder.sampleSize(1920, 1080, 1920, 1080))
        assertEquals(1, WallpaperDecoder.sampleSize(1671, 941, 1920, 1080))
        assertEquals(2, WallpaperDecoder.sampleSize(7680, 4320, 3840, 2160))
    }

    @Test fun coverKeepsAspectRatio() {
        val (w, h) = WallpaperDecoder.coverSize(1000, 1000, 1920, 1080)
        assertEquals(1920, w); assertEquals(1920, h)
        val (w2, h2) = WallpaperDecoder.coverSize(4000, 3000, 1280, 720)
        assertEquals(1280, w2); assertEquals(960, h2)
    }

    @Test fun badges() {
        assertEquals("8K", Wallpaper.badgeFor(7680, 4320))
        assertEquals("4K", Wallpaper.badgeFor(3840, 2160))
        assertEquals("FHD", Wallpaper.badgeFor(1920, 1080))
        assertEquals("HD", Wallpaper.badgeFor(1280, 720))
        assertEquals("4K", Wallpaper.badgeFor(2160, 3840)) // portrait
    }

    /** An 8K image is decoded with sub-sampling to screen size – never at full resolution. */
    @Test fun decodes8kMemoryEfficiently() {
        val src = Bitmap.createBitmap(7680, 4320, Bitmap.Config.RGB_565)
        Canvas(src).drawColor(Color.MAGENTA)
        val bytes = ByteArrayOutputStream().also { src.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        src.recycle()
        val out = WallpaperDecoder.decodeStream({ ByteArrayInputStream(bytes) }, 1920, 1080, rgb565 = false)!!
        assertTrue("decoded ${out.width}x${out.height}", out.width in 1920..2400 && out.height in 1080..1350)
        assertEquals(1920.0 / 1080.0, out.width.toDouble() / out.height, 0.02)
    }

    @Test fun bundledCatalogHasDefaultFirst() {
        assertEquals(WallpaperCatalog.DEFAULT_ID, WallpaperCatalog.bundled.first().id)
        assertEquals(WallpaperCatalog.bundled.size, WallpaperCatalog.bundled.map { it.id }.toSet().size)
    }
}
