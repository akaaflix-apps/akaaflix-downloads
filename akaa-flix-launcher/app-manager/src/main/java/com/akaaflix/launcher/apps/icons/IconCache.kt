package com.akaaflix.launcher.apps.icons

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.util.DisplayMetrics
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.akaaflix.launcher.apps.model.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Icon & TV-banner cache: memory LRU (sized by device tier) + PNG disk cache keyed by package
 * version, so cold starts decode small PNGs instead of opening every APK's resources.
 * Also extracts a dominant colour per app for the neon under-glow of cards.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IconCache private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val launcherApps = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager = appContext.getSystemService(Context.USER_SERVICE) as UserManager
    private val myUserSerial = userManager.getSerialNumberForUser(Process.myUserHandle())
    private val dispatcher = Dispatchers.IO.limitedParallelism(3)
    private val diskDir = File(appContext.cacheDir, "icons").apply { mkdirs() }
    private val colors = ConcurrentHashMap<String, Int>()
    private val noBanner = ConcurrentHashMap.newKeySet<String>()

    private var memory = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun configure(maxBytes: Int) { if (maxBytes != memory.maxSize()) memory.resize(maxBytes) }

    private fun iconKey(e: AppEntry, size: Int, shape: String) = "i|${e.key}|${e.versionCode}|${e.updateTime}|$size|$shape"
    private fun bannerKey(e: AppEntry, w: Int, h: Int) = "b|${e.key}|${e.versionCode}|${e.updateTime}|${w}x$h"

    fun peekIcon(e: AppEntry, size: Int, shape: String): Bitmap? = memory.get(iconKey(e, size, shape))
    fun peekBanner(e: AppEntry, w: Int, h: Int): Bitmap? = memory.get(bannerKey(e, w, h))
    fun hasNoBanner(e: AppEntry, w: Int, h: Int) = bannerKey(e, w, h) in noBanner
    fun dominantColor(e: AppEntry): Int = colors[e.key] ?: 0

    suspend fun icon(e: AppEntry, size: Int, shape: String): Bitmap? = withContext(dispatcher) {
        val key = iconKey(e, size, shape)
        memory.get(key)?.let { return@withContext it }
        val file = File(diskDir, md5(key) + ".png")
        var bmp: Bitmap? = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        if (bmp == null) {
            val d = loadDrawable(e, size) ?: return@withContext null
            bmp = render(d, size, shape)
            save(bmp, file)
        }
        if (!colors.containsKey(e.key)) colors[e.key] = dominant(bmp)
        memory.put(key, bmp)
        bmp
    }

    /** Android TV banner (wide artwork) or null when the app has none. */
    suspend fun banner(e: AppEntry, w: Int, h: Int): Bitmap? = withContext(dispatcher) {
        val key = bannerKey(e, w, h)
        memory.get(key)?.let { return@withContext it }
        if (key in noBanner) return@withContext null
        val file = File(diskDir, md5(key) + ".png")
        val missing = File(diskDir, md5(key) + ".none")
        if (missing.exists()) { noBanner.add(key); return@withContext null }
        var bmp: Bitmap? = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        if (bmp == null) {
            val d = try {
                if (e.userSerial != myUserSerial) null else {
                    val ai = pm.getActivityInfo(e.component, 0)
                    ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
                }
            } catch (_: Exception) { null }
            if (d == null) { noBanner.add(key); try { missing.createNewFile() } catch (_: Exception) {}; return@withContext null }
            bmp = renderCover(d, w, h)
            save(bmp, file)
        }
        memory.put(key, bmp)
        bmp
    }

    fun clear() {
        memory.evictAll(); colors.clear(); noBanner.clear()
        diskDir.listFiles()?.forEach { it.delete() }
    }

    fun trim(level: Int) {
        @Suppress("DEPRECATION")
        when {
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> memory.evictAll()
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE -> memory.trimToSize(memory.maxSize() / 2)
        }
    }

    private fun loadDrawable(e: AppEntry, size: Int): Drawable? {
        val density = if (size > 120) DisplayMetrics.DENSITY_XXHIGH else DisplayMetrics.DENSITY_XHIGH
        return try {
            if (e.userSerial != myUserSerial) {
                val user = userManager.getUserForSerialNumber(e.userSerial)
                launcherApps.getActivityList(e.packageName, user).firstOrNull { it.componentName.className == e.className }?.getBadgedIcon(density)
            } else {
                val ai = pm.getActivityInfo(e.component, 0)
                val res = pm.getResourcesForApplication(ai.applicationInfo)
                val id = ai.iconResource
                (if (id != 0) try { ResourcesCompat.getDrawableForDensity(res, id, density, null) } catch (_: Exception) { null } else null)
                    ?: ai.loadIcon(pm)
            }
        } catch (_: Exception) {
            try { pm.getApplicationIcon(e.packageName) } catch (_: Exception) { null }
        }
    }

    private fun render(d: Drawable, size: Int, shape: String): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val adaptive = Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable
        if (shape == "original") {
            d.setBounds(0, 0, size, size); d.draw(c)
            return bmp
        }
        val path = shapePath(shape, size.toFloat())
        if (adaptive && Build.VERSION.SDK_INT >= 26) {
            val a = d as AdaptiveIconDrawable
            c.save(); c.clipPath(path)
            val o = size / 4
            a.background?.let { it.setBounds(-o, -o, size + o, size + o); it.draw(c) }
            a.foreground?.let { it.setBounds(-o, -o, size + o, size + o); it.draw(c) }
            c.restore()
        } else {
            val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (shape == "circle") Color.WHITE else 0xFF2A1A44.toInt()
            }
            c.drawPath(path, plate)
            val inset = (size * 0.16f).toInt()
            d.setBounds(inset, inset, size - inset, size - inset)
            c.save(); c.clipPath(path); d.draw(c); c.restore()
            if (shape == "square") {
                val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = size * 0.03f; color = 0x80C9A6FF.toInt() }
                c.drawPath(path, border)
            }
        }
        return bmp
    }

    private fun renderCover(d: Drawable, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val iw = d.intrinsicWidth.takeIf { it > 0 } ?: w
        val ih = d.intrinsicHeight.takeIf { it > 0 } ?: h
        val scale = maxOf(w / iw.toFloat(), h / ih.toFloat())
        val dw = (iw * scale).toInt(); val dh = (ih * scale).toInt()
        val l = (w - dw) / 2; val t = (h - dh) / 2
        if (d is BitmapDrawable) d.setFilterBitmap(true)
        d.setBounds(l, t, l + dw, t + dh)
        d.draw(c)
        return bmp
    }

    private fun save(bmp: Bitmap, file: File) {
        try { FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (_: Exception) {}
    }

    companion object {
        fun shapePath(shape: String, size: Float): Path = Path().apply {
            val r = RectF(0f, 0f, size, size)
            when (shape) {
                "circle" -> addOval(r, Path.Direction.CW)
                "square" -> addRoundRect(r, size * 0.10f, size * 0.10f, Path.Direction.CW)
                else -> addRoundRect(r, size * 0.24f, size * 0.24f, Path.Direction.CW)
            }
        }

        /** Most saturated, frequent hue of the icon (16x16 sample). 0 when the icon is greyscale. */
        fun dominant(bmp: Bitmap): Int {
            val small = Bitmap.createScaledBitmap(bmp, 16, 16, true)
            val buckets = FloatArray(24); val r = FloatArray(24); val g = FloatArray(24); val b = FloatArray(24)
            val hsl = FloatArray(3)
            for (y in 0 until 16) for (x in 0 until 16) {
                val p = small.getPixel(x, y)
                if (Color.alpha(p) < 128) continue
                ColorUtils.colorToHSL(p, hsl)
                val s = hsl[1]; val l = hsl[2]
                if (s < 0.35f || l < 0.18f || l > 0.85f) continue
                val idx = ((hsl[0] / 360f) * 24).toInt().coerceIn(0, 23)
                val w = s * (1f - kotlin.math.abs(l - 0.5f))
                buckets[idx] += w; r[idx] += Color.red(p) * w; g[idx] += Color.green(p) * w; b[idx] += Color.blue(p) * w
            }
            if (small !== bmp) small.recycle()
            var best = -1; var max = 0f
            for (i in 0 until 24) if (buckets[i] > max) { max = buckets[i]; best = i }
            if (best < 0 || max < 1.2f) return 0
            return Color.rgb((r[best] / max).toInt(), (g[best] / max).toInt(), (b[best] / max).toInt())
        }

        private fun md5(s: String): String {
            val d = MessageDigest.getInstance("MD5").digest(s.toByteArray())
            return d.joinToString("") { "%02x".format(it) }
        }

        @Volatile private var instance: IconCache? = null
        fun get(context: Context): IconCache =
            instance ?: synchronized(this) { instance ?: IconCache(context).also { instance = it } }
    }
}
