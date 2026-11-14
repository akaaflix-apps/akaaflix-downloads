package com.akaaflix.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.akaaflix.launcher.prefs.LauncherSettings
import com.akaaflix.launcher.prefs.SettingsRepository
import com.akaaflix.launcher.prefs.UserWallpaper
import com.akaaflix.launcher.prefs.WallpaperUserData
import com.akaaflix.launcher.wallpaper.model.Wallpaper
import com.akaaflix.launcher.wallpaper.model.WallpaperType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

sealed class ImportResult {
    data class Success(val wallpaper: Wallpaper) : ImportResult()
    data object TooLarge : ImportResult()
    data object Failed : ImportResult()
}

/** Wallpaper list, selection, favourites, imports and rotation choice. */
class WallpaperRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    val prefs = SettingsRepository.get(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val wallpapers: StateFlow<List<Wallpaper>> = prefs.wallpapers.map { WallpaperCatalog.all(appContext, it) }
        .stateIn(scope, SharingStarted.Eagerly, WallpaperCatalog.bundled)

    fun resolve(id: String?): Wallpaper =
        wallpapers.value.firstOrNull { it.id == id } ?: WallpaperCatalog.all(appContext, prefs.wallpapers.value).firstOrNull { it.id == id }
            ?: WallpaperCatalog.bundled[0]

    fun apply(id: String) = prefs.edit { it.copy(wallpaperId = id, lastRotationAt = System.currentTimeMillis()) }

    fun toggleFavorite(id: String) = prefs.editWallpapers { d ->
        d.copy(favorites = if (id in d.favorites) d.favorites - id else d.favorites + id)
    }

    suspend fun delete(id: String) {
        val wp = wallpapers.value.firstOrNull { it.id == id } ?: return
        withContext(Dispatchers.IO) { wp.file?.delete() }
        prefs.updateWallpapers { d -> d.copy(userWallpapers = d.userWallpapers.filterNot { it.id == id }, favorites = d.favorites - id) }
        if (prefs.settings.value.wallpaperId == id) prefs.update { it.copy(wallpaperId = LauncherSettings.DEFAULT_WALLPAPER) }
    }

    /** Copies a user image into private storage, downscaled to at most 4K (8K sources supported). */
    suspend fun importImage(uri: Uri, displayName: String?): ImportResult = withContext(Dispatchers.IO) {
        try {
            val cr = appContext.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val sw = bounds.outWidth; val sh = bounds.outHeight
            if (sw <= 0 || sh <= 0) return@withContext ImportResult.Failed
            val landscape = sw >= sh
            val reqW = if (landscape) MAX_EDGE.coerceAtMost(sw) else (MAX_EDGE * sw / sh).coerceAtMost(sw)
            val reqH = if (landscape) (MAX_EDGE * sh / sw).coerceAtMost(sh) else MAX_EDGE.coerceAtMost(sh)
            var bmp = WallpaperDecoder.decodeStream({ cr.openInputStream(uri) }, reqW, reqH, false) ?: return@withContext ImportResult.Failed
            bmp = applyExif(uri, bmp)
            val id = "user:" + UUID.randomUUID().toString()
            val name = id.substringAfter(':') + ".jpg"
            val out = File(WallpaperCatalog.userWallpaperDir(appContext), name)
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            val uw = UserWallpaper(id, cleanName(displayName ?: queryName(uri)), name, "static", bmp.width, bmp.height,
                if (bmp.width >= bmp.height == sw >= sh) sw else sh, if (bmp.width >= bmp.height == sw >= sh) sh else sw, System.currentTimeMillis())
            bmp.recycle()
            prefs.updateWallpapers { it.copy(userWallpapers = it.userWallpapers + uw) }
            ImportResult.Success(resolve(id))
        } catch (_: OutOfMemoryError) { ImportResult.Failed } catch (_: Exception) { ImportResult.Failed }
    }

    /** Copies a video (≤300 MB) into private storage for use as a muted looping wallpaper. */
    suspend fun importVideo(uri: Uri, displayName: String?): ImportResult = withContext(Dispatchers.IO) {
        val cr = appContext.contentResolver
        val size = querySize(uri)
        if (size > MAX_VIDEO_BYTES) return@withContext ImportResult.TooLarge
        val id = "user:" + UUID.randomUUID().toString()
        val name = id.substringAfter(':') + ".mp4"
        val out = File(WallpaperCatalog.userWallpaperDir(appContext), name)
        try {
            var copied = 0L
            cr.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        copied += n
                        if (copied > MAX_VIDEO_BYTES) throw IllegalStateException("too large")
                        o.write(buf, 0, n)
                    }
                }
            } ?: return@withContext ImportResult.Failed
            val r = MediaMetadataRetriever()
            val (w, h) = try {
                r.setDataSource(out.path)
                if (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") throw IllegalStateException("no video")
                val vw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val vh = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rot == 90 || rot == 270) Pair(vh, vw) else Pair(vw, vh)
            } finally { try { r.release() } catch (_: Exception) {} }
            val uw = UserWallpaper(id, cleanName(displayName ?: queryName(uri)), name, "video", w, h, w, h, System.currentTimeMillis())
            prefs.updateWallpapers { it.copy(userWallpapers = it.userWallpapers + uw) }
            ImportResult.Success(resolve(id))
        } catch (e: Exception) {
            out.delete()
            if (e is IllegalStateException && e.message == "too large") ImportResult.TooLarge else ImportResult.Failed
        }
    }

    /** Next wallpaper for automatic rotation (cycles through favourites, else all static ones). */
    fun nextForRotation(settings: LauncherSettings, data: WallpaperUserData, allowMotion: Boolean): String? {
        val all = wallpapers.value
        val pool = (if (settings.rotationSource == "favorites") all.filter { it.id in data.favorites } else emptyList())
            .ifEmpty { all.filter { it.type == WallpaperType.STATIC } }
            .filter { allowMotion || it.type == WallpaperType.STATIC }
        if (pool.size < 2) return pool.firstOrNull()?.id?.takeIf { it != settings.wallpaperId }
        val i = pool.indexOfFirst { it.id == settings.wallpaperId }
        return pool[(i + 1).mod(pool.size)].id
    }

    private fun applyExif(uri: Uri, bmp: Bitmap): Bitmap {
        if (Build.VERSION.SDK_INT < 24) return bmp
        return try {
            val deg = appContext.contentResolver.openInputStream(uri)?.use { s ->
                when (android.media.ExifInterface(s).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f
                }
            } ?: 0f
            if (deg == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true).also { if (it !== bmp) bmp.recycle() }
        } catch (_: Exception) { bmp }
    }

    private fun queryName(uri: Uri): String = try {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "Wallpaper"
    } catch (_: Exception) { "Wallpaper" }

    private fun querySize(uri: Uri): Long = try {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        } ?: 0L
    } catch (_: Exception) { 0L }

    private fun cleanName(n: String) = n.substringBeforeLast('.').replace('_', ' ').take(40).ifBlank { "Wallpaper" }

    companion object {
        const val MAX_EDGE = 3840
        const val MAX_VIDEO_BYTES = 300L * 1024 * 1024

        @Volatile private var instance: WallpaperRepository? = null
        fun get(context: Context): WallpaperRepository =
            instance ?: synchronized(this) { instance ?: WallpaperRepository(context).also { instance = it } }
    }
}
