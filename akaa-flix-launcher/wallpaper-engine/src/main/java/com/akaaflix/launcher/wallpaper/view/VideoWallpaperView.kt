package com.akaaflix.launcher.wallpaper.view

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView

/** Muted, looping, centre-cropped video wallpaper (MediaPlayer – no extra libraries). */
class VideoWallpaperView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var path: String? = null
    private var videoW = 0
    private var videoH = 0
    private var paused = false
    var onError: (() -> Unit)? = null
    var onFirstFrame: (() -> Unit)? = null

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun play(path: String) {
        this.path = path
        if (isAvailable) start()
    }

    private fun start() {
        release()
        val p = path ?: return
        val st = surfaceTexture ?: return
        try {
            val s = Surface(st).also { surface = it }
            player = MediaPlayer().apply {
                setDataSource(p)
                setSurface(s)
                isLooping = true
                setVolume(0f, 0f)
                setOnVideoSizeChangedListener { _, w, h -> videoW = w; videoH = h; applyCrop() }
                setOnPreparedListener { if (!paused) it.start() }
                setOnInfoListener { _, what, _ -> if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) onFirstFrame?.invoke(); false }
                setOnErrorListener { _, _, _ -> onError?.invoke(); true }
                prepareAsync()
            }
        } catch (_: Exception) { onError?.invoke() }
    }

    fun pause() { paused = true; try { player?.takeIf { it.isPlaying }?.pause() } catch (_: Exception) {} }
    fun resume() { paused = false; try { player?.start() } catch (_: Exception) {} }

    fun release() {
        try { player?.release() } catch (_: Exception) {}
        player = null
        surface?.release(); surface = null
    }

    private fun applyCrop() {
        if (videoW <= 0 || videoH <= 0 || width == 0 || height == 0) return
        val scale = maxOf(width / videoW.toFloat(), height / videoH.toFloat())
        val sx = videoW * scale / width
        val sy = videoH * scale / height
        setTransform(Matrix().apply { setScale(sx, sy, width / 2f, height / 2f) })
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); applyCrop() }
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = start()
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = applyCrop()
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { release(); return true }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
}
