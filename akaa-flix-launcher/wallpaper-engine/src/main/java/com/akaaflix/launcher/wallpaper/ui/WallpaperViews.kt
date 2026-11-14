package com.akaaflix.launcher.wallpaper.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatTextView
import com.akaaflix.launcher.core.R as CoreR
import com.akaaflix.launcher.core.ui.FocusEffects
import com.akaaflix.launcher.core.ui.GlassDrawable
import com.akaaflix.launcher.core.ui.LauncherUi
import com.akaaflix.launcher.core.ui.glass
import com.akaaflix.launcher.core.ui.tint

/** FrameLayout with a fixed aspect ratio (16:9 by default) – keeps previews undistorted. */
class AspectFrameLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    var ratio = 9f / 16f
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((w * ratio).toInt(), MeasureSpec.EXACTLY))
    }
}

fun View.roundClip(radiusPx: Float) {
    clipToOutline = true
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
    }
}

/** Wallpaper thumbnail card: image, resolution badge, favourite heart / applied check, video mark. */
@SuppressLint("ViewConstructor")
class WallpaperThumbView(context: Context) : FrameLayout(context) {
    private val d = resources.displayMetrics.density
    private val inset = (5 * d).toInt()
    val glassBg = GlassDrawable(14f * d, GlassDrawable.Variant.CARD, 5f * d, d)
    val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; roundClip(11f * d) }
    private val badge = AppCompatTextView(context).apply {
        textSize = 10f
        setTextColor(0xFFFFFFFF.toInt())
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding((6 * d).toInt(), (1 * d).toInt(), (6 * d).toInt(), (1 * d).toInt())
        glass(GlassDrawable.Variant.CHIP, 8f)
    }
    private val corner = ImageView(context)
    private val play = ImageView(context).apply { setImageResource(CoreR.drawable.ic_play_box) }
    private val addLabel = AppCompatTextView(context).apply {
        setTextAppearance(CoreR.style.TextAppearance_Akaa_LabelBold)
        gravity = Gravity.CENTER
        setCompoundDrawablesWithIntrinsicBounds(0, CoreR.drawable.ic_plus, 0, 0)
        compoundDrawablePadding = (4 * d).toInt()
        visibility = GONE
    }
    var boundId: String? = null

    init {
        background = glassBg
        isFocusable = true; isClickable = true; isLongClickable = true
        clipChildren = false
        val m = inset + d.toInt()
        addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply { setMargins(m, m, m, m) })
        addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply { setMargins(m + (5 * d).toInt(), m + (5 * d).toInt(), 0, 0) })
        val cs = (22 * d).toInt()
        addView(corner, LayoutParams(cs, cs, Gravity.TOP or Gravity.END).apply { setMargins(0, m + (5 * d).toInt(), m + (5 * d).toInt(), 0) })
        addView(play, LayoutParams(cs, cs, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, m + (5 * d).toInt(), m + (5 * d).toInt()) })
        addView(addLabel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    fun bind(id: String, label: String, badgeText: String, favorite: Boolean, applied: Boolean, moving: Boolean) {
        boundId = id
        contentDescription = label
        addLabel.visibility = GONE
        image.visibility = VISIBLE
        badge.visibility = VISIBLE
        badge.text = badgeText
        corner.visibility = VISIBLE
        when {
            applied -> { corner.setImageResource(CoreR.drawable.ic_check); corner.tint(0xFFFFFFFF.toInt()); corner.glass(GlassDrawable.Variant.PRIMARY, 11f) }
            favorite -> { corner.setImageResource(CoreR.drawable.ic_heart_filled); corner.imageTintList = null; corner.background = null }
            else -> { corner.setImageResource(CoreR.drawable.ic_heart); corner.tint(0xFFFFFFFF.toInt()); corner.background = null }
        }
        corner.setPadding((3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt())
        play.visibility = if (moving) VISIBLE else GONE
        play.tint(0xFFFFFFFF.toInt())
        image.setImageDrawable(null)
    }

    fun bindAdd(label: String) {
        boundId = ADD_ID
        contentDescription = label
        image.visibility = GONE; badge.visibility = GONE; corner.visibility = GONE; play.visibility = GONE
        addLabel.visibility = VISIBLE
        addLabel.text = label
        addLabel.compoundDrawables[1]?.setTint(LauncherUi.palette.accentLight)
    }

    fun setImage(b: Bitmap?) { image.setImageBitmap(b) }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        FocusEffects.apply(this, gainFocus)
    }

    companion object { const val ADD_ID = "__add_wallpaper__" }
}
