package com.akaaflix.launcher.wallpaper.ui

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import com.akaaflix.launcher.core.R as CoreR
import com.akaaflix.launcher.core.ui.GlassDrawable
import com.akaaflix.launcher.core.ui.dpi
import com.akaaflix.launcher.core.ui.glass
import com.akaaflix.launcher.remote.Keys
import com.akaaflix.launcher.wallpaper.model.Wallpaper
import com.akaaflix.launcher.wallpaper.view.WallpaperHostView

/** Full-screen wallpaper preview (plays video/animation). OK applies, Back closes. */
object FullscreenPreviewDialog {
    fun show(context: Context, wp: Wallpaper, resolver: (String?) -> Wallpaper, allowMotion: Boolean, onApply: () -> Unit) {
        val dialog = Dialog(context, CoreR.style.Theme_Akaa_FullscreenDialog)
        val root = FrameLayout(context)
        val host = WallpaperHostView(context).apply {
            this.resolver = resolver
            allowVideo = allowMotion; allowAnimation = allowMotion; crossfade = false
        }
        root.addView(host, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val hint = AppCompatTextView(context).apply {
            setText(CoreR.string.wp_fullscreen_hint)
            setTextAppearance(CoreR.style.TextAppearance_Akaa_Label)
            setPadding(context.dpi(18f), context.dpi(8f), context.dpi(18f), context.dpi(8f))
            glass(GlassDrawable.Variant.PANEL, 18f)
        }
        root.addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = context.dpi(24f) })
        root.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(root)
        dialog.setOnKeyListener { _, code, ev ->
            if (ev.action == KeyEvent.ACTION_UP && Keys.isConfirm(code)) { dialog.dismiss(); onApply(); true } else false
        }
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.show()
        host.post { host.setWallpaper(wp.id, force = true); host.setActive(true) }
        hint.postDelayed({ hint.animate().alpha(0f).setDuration(600).start() }, 3500)
    }
}
