package com.akaaflix.launcher.apps.ui

import android.graphics.drawable.Drawable
import android.widget.TextView
import com.akaaflix.launcher.core.ui.FocusEffects
import com.akaaflix.launcher.core.ui.GlassDrawable
import com.akaaflix.launcher.core.ui.dpi
import com.akaaflix.launcher.core.ui.glass

/** Styles a chip (glass pill, icon tint, zoom on focus). Icon size follows the text size. */
fun TextView.styleAsChip() {
    glass(GlassDrawable.Variant.CHIP, 20f, 3f)
    FocusEffects.attach(this, 1.05f)
    val size = dpi(20f)
    val ds: Array<Drawable?> = compoundDrawables
    ds.forEach { it?.setBounds(0, 0, size, size) }
    setCompoundDrawables(ds[0], ds[1], ds[2], ds[3])
}
