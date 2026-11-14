package com.akaaflix.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import com.akaaflix.launcher.wallpaper.model.Wallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/** Small wallpaper thumbnails (sampled decode) with a byte-bounded memory cache. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThumbnailLoader private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val dispatcher = Dispatchers.IO.limitedParallelism(2)
    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun configure(bytes: Int) { if (bytes != cache.maxSize()) cache.resize(bytes) }

    fun peek(wp: Wallpaper, w: Int, h: Int): Bitmap? = cache.get(key(wp, w, h))

    suspend fun load(wp: Wallpaper, w: Int, h: Int): Bitmap? = withContext(dispatcher) {
        val k = key(wp, w, h)
        cache.get(k) ?: WallpaperDecoder.decode(appContext, wp, w, h, false)?.also { cache.put(k, it) }
    }

    fun clear() = cache.evictAll()

    private fun key(wp: Wallpaper, w: Int, h: Int) = "${wp.id}|${wp.file?.lastModified() ?: 0}|${w}x$h"

    companion object {
        @Volatile private var instance: ThumbnailLoader? = null
        fun get(context: Context): ThumbnailLoader =
            instance ?: synchronized(this) { instance ?: ThumbnailLoader(context).also { instance = it } }
    }
}
