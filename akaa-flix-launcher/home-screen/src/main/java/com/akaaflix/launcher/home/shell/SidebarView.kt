package com.akaaflix.launcher.home.shell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.InsetDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.nav.LauncherPage
import com.akaaflix.launcher.core.ui.FocusEffects
import com.akaaflix.launcher.core.ui.GlassDrawable
import com.akaaflix.launcher.core.ui.LauncherUi
import com.akaaflix.launcher.core.ui.SidebarDrawable
import com.akaaflix.launcher.core.ui.tint

/**
 * Navigation rail of the reference: curved glass panel with Home, All apps, Favorites,
 * Customization, Wallpapers and Settings. Becomes a bottom bar in portrait ([horizontal]).
 */
class SidebarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val d = resources.displayMetrics.density
    private val items = LinkedHashMap<LauncherPage, NavItemView>()
    var onPageSelected: ((LauncherPage) -> Unit)? = null
    var selectedPage: LauncherPage = LauncherPage.HOME
        private set
    var horizontal = false
        private set

    fun setup(horizontal: Boolean) {
        this.horizontal = horizontal
        orientation = if (horizontal) HORIZONTAL else VERTICAL
        gravity = Gravity.CENTER
        clipChildren = false; clipToPadding = false
        background = SidebarDrawable(d, horizontal)
        if (horizontal) setPadding((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
        else setPadding(0, (22 * d).toInt(), (8 * d).toInt(), (22 * d).toInt())
        removeAllViews(); items.clear()
        listOf(
            LauncherPage.HOME to (R.drawable.ic_home to R.string.nav_home),
            LauncherPage.ALL_APPS to (R.drawable.ic_apps to R.string.nav_all_apps),
            LauncherPage.FAVORITES to (R.drawable.ic_star to R.string.nav_favorites),
            LauncherPage.CUSTOMIZATION to (R.drawable.ic_sliders to R.string.nav_customization),
            LauncherPage.WALLPAPERS to (R.drawable.ic_layers to R.string.nav_wallpapers),
            LauncherPage.SETTINGS to (R.drawable.ic_settings to R.string.nav_settings),
        ).forEach { (page, res) ->
            val item = NavItemView(context, res.first, context.getString(res.second), horizontal)
            item.setOnClickListener { onPageSelected?.invoke(page) }
            val lp = if (horizontal) LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            else LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = (2 * d).toInt(); bottomMargin = (2 * d).toInt() }
            addView(item, lp)
            items[page] = item
        }
        select(selectedPage)
    }

    fun select(page: LauncherPage) {
        selectedPage = page
        items.forEach { (p, v) -> v.isActivated = p == page; v.refreshTint() }
    }

    fun itemFor(page: LauncherPage): View? = items[page]
    fun selectedItem(): View? = items[selectedPage]

    fun setLabelsVisible(visible: Boolean) = items.values.forEach { it.setLabelVisible(visible) }

    fun refreshPalette() { items.values.forEach { it.refreshTint() }; background?.invalidateSelf() }
}

@SuppressLint("ViewConstructor")
class NavItemView(context: Context, icon: Int, label: String, private val horizontal: Boolean) : LinearLayout(context) {
    private val d = resources.displayMetrics.density
    private val iconView = ImageView(context).apply { setImageResource(icon); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    private val labelView = AppCompatTextView(context).apply {
        text = label
        setTextAppearance(R.style.TextAppearance_Akaa_Label)
        textSize = if (horizontal) 10f else 12f
        maxLines = 1
        gravity = Gravity.CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 8, if (horizontal) 10 else 12, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
    }
    private val glass = GlassDrawable(22f * d, GlassDrawable.Variant.NAV_ITEM, 3f * d, d)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        isFocusable = true; isClickable = true
        contentDescription = label
        // Vertical rail: extend the pill off-screen on the left so its edge looks attached to the screen side.
        background = if (horizontal) glass else InsetDrawable(glass, (-26 * d).toInt(), 0, 0, 0)
        val s = ((if (horizontal) 24 else 28) * d).toInt()
        addView(iconView, LayoutParams(s, s))
        addView(labelView, LayoutParams(LayoutParams.MATCH_PARENT, (16 * d).toInt()).apply { topMargin = (3 * d).toInt() })
    }

    fun setLabelVisible(v: Boolean) { labelView.visibility = if (v) VISIBLE else GONE }

    fun refreshTint() {
        iconView.tint(if (isActivated) LauncherUi.palette.focusBorder else 0xFFFFFFFF.toInt())
        labelView.setTypeface(labelView.typeface, if (isActivated) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        glass.invalidateSelf()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        FocusEffects.apply(this, gainFocus, 1.06f)
    }
}
