package com.akaaflix.launcher.remote

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import java.lang.ref.WeakReference

/**
 * Root of the launcher window. Makes D-pad movement between the three regions predictable:
 *  - entering the navigation rail always lands on the selected page item;
 *  - entering the content (from the rail or the top bar) restores the last focused item;
 *  - inside a region the normal geometric focus search applies.
 * This removes the "focus jumps to a random item" problem and keeps focus restoration correct
 * when returning from another app or another page.
 */
class NavigationRootLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    var navRail: ViewGroup? = null
    var content: ViewGroup? = null
    var navPreferredEntry: (() -> View?)? = null
    /** True when the navigation is a bottom bar (portrait): UP leaves it instead of RIGHT. */
    var navHorizontal = false

    private var contentMemory: WeakReference<View>? = null

    private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
        val c = content
        if (newFocus != null && c != null && c.isAncestorOf(newFocus)) contentMemory = WeakReference(newFocus)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalFocusChangeListener(focusListener)
        super.onDetachedFromWindow()
    }

    fun clearContentMemory() { contentMemory = null }

    fun rememberContentFocus(view: View) { contentMemory = WeakReference(view) }

    fun rememberedContentFocus(): View? {
        val v = contentMemory?.get() ?: return null
        val c = content ?: return null
        return v.takeIf { it.isAttachedToWindow && it.isShown && it.isFocusable && c.isAncestorOf(it) }
    }

    override fun focusSearch(focused: View?, direction: Int): View? {
        val next = super.focusSearch(focused, direction)
        val rail = navRail
        val c = content
        // Leaving the rail towards the content always returns to the content (never the top bar).
        if (focused != null && rail != null && c != null && rail.isAncestorOf(focused) &&
            direction == (if (navHorizontal) View.FOCUS_UP else View.FOCUS_RIGHT)) {
            rememberedContentFocus()?.let { return it }
            if (next != null && c.isAncestorOf(next)) return next
            firstFocusable(c)?.let { return it }
        }
        if (focused == null || next == null) return next
        if (rail != null && rail.isAncestorOf(next) && !rail.isAncestorOf(focused)) {
            return navPreferredEntry?.invoke()?.takeIf { it.isShown } ?: next
        }
        if (c != null && c.isAncestorOf(next) && !c.isAncestorOf(focused)) {
            return rememberedContentFocus() ?: next
        }
        return next
    }
}

private fun firstFocusable(group: ViewGroup): View? {
    val list = ArrayList<View>()
    group.addFocusables(list, View.FOCUS_FORWARD)
    return list.firstOrNull { it.isShown }
}

fun ViewGroup.isAncestorOf(view: View): Boolean {
    var p = view.parent
    while (p != null) {
        if (p === this) return true
        p = p.parent
    }
    return false
}
