package com.akaaflix.launcher.core.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.widget.ImageViewCompat

fun Context.dp(value: Float): Float = value * resources.displayMetrics.density
fun Context.dpi(value: Float): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
fun View.dp(value: Float): Float = context.dp(value)
fun View.dpi(value: Float): Int = context.dpi(value)

/** Installs a [GlassDrawable] background. Returns it so callers can tune tint / style. */
fun View.glass(variant: GlassDrawable.Variant, radiusDp: Float = 14f, insetDp: Float = 0f): GlassDrawable {
    val d = GlassDrawable(dp(radiusDp), variant, dp(insetDp), resources.displayMetrics.density)
    background = d
    return d
}

fun ImageView.tint(color: Int) = ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(color))
fun ImageView.clearTint() = ImageViewCompat.setImageTintList(this, null)

/** Invalidates a whole view tree (used after palette changes). */
fun View.invalidateTree() {
    invalidate()
    background?.invalidateSelf()
    if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).invalidateTree()
}

/** Turns off child clipping up the hierarchy so focus zoom/glow are never cut. */
fun ViewGroup.disableClipping() {
    clipChildren = false
    clipToPadding = false
}
