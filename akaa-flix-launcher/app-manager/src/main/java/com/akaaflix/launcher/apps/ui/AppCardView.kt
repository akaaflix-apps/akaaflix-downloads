package com.akaaflix.launcher.apps.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.Rect
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatTextView
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.ui.FocusEffects
import com.akaaflix.launcher.core.ui.GlassDrawable
import com.akaaflix.launcher.core.ui.LauncherUi
import com.akaaflix.launcher.core.ui.tint

/**
 * The rounded neon app card of the reference. Lightweight custom ViewGroup (no nested layouts).
 * Shows the app's Android TV banner when available (YouTube/Netflix style cards), otherwise the
 * icon and name. Focus = zoom + bright border + glow (within its own bounds).
 */
@SuppressLint("ViewConstructor")
class AppCardView(context: Context) : ViewGroup(context) {
    private val d = resources.displayMetrics.density
    val glowPad = 8f * d
    val glass = GlassDrawable(14f * d, GlassDrawable.Variant.CARD, glowPad, d)

    val iconView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    val labelView = AppCompatTextView(context).apply {
        setTextAppearance(R.style.TextAppearance_Akaa_Label)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.MARQUEE
        marqueeRepeatLimit = -1
        gravity = Gravity.CENTER
        setShadowLayer(3f * d, 0f, d, 0x99000000.toInt())
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    val bannerView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = GONE
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 12.5f * d)
            }
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val lockBadge = ImageView(context).apply {
        setImageResource(R.drawable.ic_lock)
        visibility = GONE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    var showLabel = true
        set(value) { if (field != value) { field = value; requestLayout() } }
    var iconScale = 1f
        set(value) { if (field != value) { field = value; requestLayout() } }
    var boundKey: String? = null
    private var hasBanner = false

    init {
        background = glass
        isFocusable = true
        isClickable = true
        isLongClickable = true
        clipChildren = false
        addView(bannerView); addView(iconView); addView(labelView); addView(lockBadge)
    }

    fun bindApp(key: String, label: String, locked: Boolean, cardStyle: String) {
        boundKey = key
        contentDescription = label
        labelView.text = label
        lockBadge.visibility = if (locked) VISIBLE else GONE
        lockBadge.tint(LauncherUi.palette.accentLight)
        glass.cardStyle = cardStyle
        glass.glowTint = 0
        setBanner(null)
        iconView.setImageDrawable(null)
        iconView.imageTintList = null
    }

    fun bindAdd(label: String, cardStyle: String) {
        boundKey = ADD_KEY
        contentDescription = label
        labelView.text = label
        lockBadge.visibility = GONE
        glass.cardStyle = cardStyle
        glass.glowTint = 0
        setBanner(null)
        iconView.setImageResource(R.drawable.ic_plus)
        iconView.tint(0xFFFFFFFF.toInt())
    }

    fun setIcon(bmp: Bitmap?) { iconView.setImageBitmap(bmp) }

    fun setBanner(bmp: Bitmap?) {
        hasBanner = bmp != null
        bannerView.setImageBitmap(bmp)
        bannerView.visibility = if (hasBanner) VISIBLE else GONE
        iconView.visibility = if (hasBanner) GONE else VISIBLE
        labelView.visibility = if (hasBanner || !showLabel) GONE else VISIBLE
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val cw = (w - 2 * glowPad).toInt().coerceAtLeast(0)
        val ch = (h - 2 * glowPad).toInt().coerceAtLeast(0)
        val icon = iconSize(cw, ch)
        iconView.measure(MeasureSpec.makeMeasureSpec(icon, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(icon, MeasureSpec.EXACTLY))
        labelView.measure(MeasureSpec.makeMeasureSpec((cw - 8 * d).toInt().coerceAtLeast(0), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        bannerView.measure(MeasureSpec.makeMeasureSpec((cw - 2 * d).toInt().coerceAtLeast(0), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((ch - 2 * d).toInt().coerceAtLeast(0), MeasureSpec.EXACTLY))
        val b = (16 * d).toInt()
        lockBadge.measure(MeasureSpec.makeMeasureSpec(b, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(b, MeasureSpec.EXACTLY))
    }

    private fun iconSize(cw: Int, ch: Int): Int {
        val labelled = showLabel && !hasBanner
        val base = minOf(ch * (if (labelled) 0.50f else 0.62f), cw * 0.55f)
        return (base * iconScale).toInt().coerceAtLeast((16 * d).toInt())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val left = glowPad.toInt(); val top = glowPad.toInt()
        val cw = width - 2 * left; val ch = height - 2 * top
        bannerView.layout(left + d.toInt(), top + d.toInt(), left + d.toInt() + bannerView.measuredWidth, top + d.toInt() + bannerView.measuredHeight)
        val icon = iconView.measuredWidth
        val cx = left + cw / 2
        if (labelView.visibility == VISIBLE) {
            val gap = (3 * d).toInt()
            val lh = labelView.measuredHeight
            val total = icon + gap + lh
            val y0 = top + (ch - total) / 2
            iconView.layout(cx - icon / 2, y0, cx + icon / 2, y0 + icon)
            val lw = labelView.measuredWidth
            labelView.layout(cx - lw / 2, y0 + icon + gap, cx + lw / 2, y0 + icon + gap + lh)
        } else {
            val y0 = top + (ch - icon) / 2
            iconView.layout(cx - icon / 2, y0, cx + icon / 2, y0 + icon)
        }
        val bs = lockBadge.measuredWidth
        val m = (6 * d).toInt()
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val bx = if (rtl) left + m else left + cw - m - bs
        lockBadge.layout(bx, top + m, bx + bs, top + m + bs)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        FocusEffects.apply(this, gainFocus)
        labelView.isSelected = gainFocus
    }

    override fun shouldDelayChildPressedState() = false

    companion object { const val ADD_KEY = "__add__" }
}
