package com.akaaflix.launcher.wallpaper.view

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.akaaflix.launcher.wallpaper.WallpaperDecoder
import com.akaaflix.launcher.wallpaper.model.Wallpaper
import com.akaaflix.launcher.wallpaper.model.WallpaperType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Draws the launcher wallpaper behind every page. Static images are decoded at screen size off the
 * main thread and cross-faded; video wallpapers play muted on a TextureView; animated wallpapers
 * add a light procedural overlay. Playback pauses whenever the launcher is not visible.
 */
class WallpaperHostView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private val imageA = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val imageB = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
    private var front = imageA
    private var video: VideoWallpaperView? = null
    private var overlay: AnimatedOverlay? = null
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null

    /** Resolves an id into a wallpaper (set by the activity). */
    var resolver: ((String?) -> Wallpaper)? = null
    var allowVideo = true
    var allowAnimation = true
    var rgb565 = false
    var crossfade = true
    var maxEdgePx = 3840

    private var appliedId: String? = null
    private var previewId: String? = null
    private var shown: Wallpaper? = null
    private var active = true
    private var released = false

    val shownWallpaper: Wallpaper? get() = shown
    var currentBitmap: Bitmap? = null
        private set
    var onBitmapChanged: ((Bitmap?) -> Unit)? = null

    init {
        setBackgroundColor(0xFF07020F.toInt())
        addView(imageA, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(imageB, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setWallpaper(id: String, force: Boolean = false) {
        val changed = id != appliedId
        appliedId = id
        if (previewId == null && (changed || force)) show(id)
    }

    fun preview(id: String?) {
        previewId = id
        show(id ?: appliedId)
    }

    /** Re-applies after quality settings change (video allowed, rgb565...). */
    fun reload() { shown = null; show(previewId ?: appliedId) }

    fun setActive(active: Boolean) {
        this.active = active
        video?.let { if (active) it.resume() else it.pause() }
        overlay?.running = active
        if (active && released) { released = false; reload() }
    }

    /** Frees the decoded bitmap under memory pressure; reloaded when visible again. */
    fun releaseMemory() {
        if (active) return
        released = true
        imageA.setImageDrawable(null); imageB.setImageDrawable(null)
        currentBitmap = null
        removeVideo()
    }

    private fun targetSize(): Pair<Int, Int> {
        val dm = resources.displayMetrics
        var w = if (width > 0) width else dm.widthPixels
        var h = if (height > 0) height else dm.heightPixels
        val edge = maxOf(w, h)
        if (edge > maxEdgePx) { val s = maxEdgePx / edge.toFloat(); w = (w * s).toInt(); h = (h * s).toInt() }
        return Pair(w.coerceAtLeast(1), h.coerceAtLeast(1))
    }

    private fun show(id: String?) {
        val wp = resolver?.invoke(id) ?: return
        if (wp == shown) return
        shown = wp
        loadJob?.cancel()
        val (w, h) = targetSize()
        loadJob = scope.launch {
            val bmp = withContext(Dispatchers.IO) { WallpaperDecoder.decode(context, wp, w, h, rgb565) }
                ?: withContext(Dispatchers.IO) { resolver?.invoke(null)?.let { WallpaperDecoder.decode(context, it, w, h, rgb565) } }
            setBitmap(bmp)
            removeVideo(); removeOverlay()
            when (wp.type) {
                WallpaperType.VIDEO -> if (allowVideo) wp.file?.let { addVideo(it.path) }
                WallpaperType.ANIMATED -> if (allowAnimation) addOverlay(wp.animation)
                WallpaperType.STATIC -> Unit
            }
        }
    }

    private fun setBitmap(bmp: Bitmap?) {
        currentBitmap = bmp
        onBitmapChanged?.invoke(bmp)
        val back = if (front === imageA) imageB else imageA
        if (!crossfade || front.drawable == null || !isAttachedToWindow) {
            front.setImageBitmap(bmp); front.alpha = 1f
            back.setImageDrawable(null); back.alpha = 0f
            return
        }
        back.setImageBitmap(bmp)
        back.bringToFront()
        video?.bringToFront(); overlay?.bringToFront()
        back.alpha = 0f
        val old = front
        back.animate().alpha(1f).setDuration(450).withEndAction {
            old.setImageDrawable(null); old.alpha = 0f
        }.start()
        front = back
    }

    private fun addVideo(path: String) {
        val v = VideoWallpaperView(context)
        v.alpha = 0f
        v.onFirstFrame = { v.animate().alpha(1f).setDuration(300).start() }
        v.onError = { removeVideo() }
        addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        video = v
        v.play(path)
        if (!active) v.pause()
    }

    private fun removeVideo() { video?.let { it.release(); removeView(it) }; video = null }

    private fun addOverlay(kind: String?) {
        val o = when (kind) { "neon_pulse" -> NeonPulseOverlay(context); "starfield" -> StarfieldOverlay(context); else -> null } ?: return
        addView(o, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        overlay = o
        o.running = active
    }

    private fun removeOverlay() { overlay?.let { it.running = false; removeView(it) }; overlay = null }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!scope.isActiveScope()) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    override fun onDetachedFromWindow() {
        removeVideo(); removeOverlay()
        scope.cancel()
        shown = null
        super.onDetachedFromWindow()
    }

    private fun CoroutineScope.isActiveScope() = coroutineContext[Job]?.isActive == true

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        if (visibility != View.VISIBLE) setActive(false)
    }
}
