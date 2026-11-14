package com.akaaflix.launcher.core.nav

import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment

enum class LauncherPage {
    HOME, ALL_APPS, FAVORITES, CUSTOMIZATION, WALLPAPERS, SETTINGS;

    /** Sidebar item that represents this page (Wallpapers lives under Customization). */
    val navItem: LauncherPage get() = if (this == WALLPAPERS) CUSTOMIZATION else this
    /** Page that Back returns to (Home for top-level pages). */
    val backTarget: LauncherPage get() = if (this == WALLPAPERS) CUSTOMIZATION else HOME
}

/** Header shown in the centre of the top bar. [showLogo] = the big Akaa Flix logo (home). */
data class PageHeader(
    val title: CharSequence? = null,
    val subtitle: CharSequence? = null,
    @DrawableRes val icon: Int = 0,
    val showLogo: Boolean = false,
)

/** Implemented by the launcher activity; feature fragments talk to the shell through this. */
interface LauncherHost {
    fun navigate(page: LauncherPage, args: Bundle? = null)
    fun currentPage(): LauncherPage
    fun setHeader(header: PageHeader)
    /** Shows the PIN dialog when a parental PIN exists; runs [onGranted] when verified or not needed. */
    fun requirePin(onGranted: () -> Unit)
    /** Temporarily shows another wallpaper behind the UI (null restores the applied one). */
    fun previewWallpaper(wallpaperId: String?)
    /** Fades the page UI so the wallpaper can be seen (wallpaper "Preview" button). */
    fun peekBehindUi()
    /** Shows the system runtime-permission dialog (registered by the activity). */
    fun requestPermission(permission: String, onResult: (granted: Boolean) -> Unit)
    fun recreateUi()
}

/** Fragments that want the Back key before the activity handles it. */
interface BackHandler { fun onBackPressed(): Boolean }

/** Fragments that need raw key events first (e.g. reorder mode). Return true to consume. */
interface KeyInterceptor { fun onInterceptKey(event: android.view.KeyEvent): Boolean }

/** Pages with unsaved changes. Return false to block navigation and call [proceed] later. */
interface LeaveGuard { fun canLeave(proceed: () -> Unit): Boolean }

/** Retained pages (Home) that receive navigation arguments without being recreated. */
interface ArgsReceiver { fun onNavigationArgs(args: android.os.Bundle) }

/** Fragments that react to the remote Menu key. */
interface MenuKeyHandler { fun onMenuKey(): Boolean }

val Fragment.launcherHost: LauncherHost? get() = activity as? LauncherHost

object NavArgs {
    const val FOCUS_SEARCH = "focus_search"
    const val SEARCH_QUERY = "search_query"
    const val MOVE_MODE = "move_mode"
    const val OPEN_SECTION = "open_section"
}
