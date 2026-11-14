package com.akaaflix.launcher.apps.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.akaaflix.launcher.apps.AppLists
import com.akaaflix.launcher.apps.AppRepository
import com.akaaflix.launcher.apps.databinding.FragmentFavoritesBinding
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.nav.BackHandler
import com.akaaflix.launcher.core.nav.KeyInterceptor
import com.akaaflix.launcher.core.nav.MenuKeyHandler
import com.akaaflix.launcher.core.nav.NavArgs
import com.akaaflix.launcher.core.nav.PageHeader
import com.akaaflix.launcher.core.nav.launcherHost
import com.akaaflix.launcher.core.ui.GlassDrawable
import com.akaaflix.launcher.core.ui.dpi
import com.akaaflix.launcher.core.ui.glass
import com.akaaflix.launcher.prefs.SettingsRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Favorites page: the apps the user marked as favourite (reorderable) and the launcher's recently
 * used apps. Favorites are independent of the Home screen apps.
 */
class FavoritesFragment : Fragment(), BackHandler, MenuKeyHandler, KeyInterceptor {
    private var _b: FragmentFavoritesBinding? = null
    private val b get() = _b!!
    private lateinit var grid: AppGridController
    private lateinit var recent: AppGridController
    private val prefs by lazy { SettingsRepository.get(requireContext()) }
    private val repo by lazy { AppRepository.get(requireContext()) }

    private val move = MoveModeController(object : MoveModeController.Listener {
        override fun currentOrder() = AppLists.favorites(repo.apps.value, prefs.apps.value).map { it.key }
        override fun columns() = grid.spec?.columns ?: 1
        override fun applyOrder(order: List<String>, movingKey: String?) {
            val byKey = repo.apps.value.associateBy { it.key }
            grid.setItems(order.mapNotNull { byKey[it] }.map { GridItem.App(it, false, it.key == movingKey) }, focusId = movingKey ?: grid.focusedItem()?.id)
        }
        override fun commit(order: List<String>) {
            prefs.editApps { c -> c.copy(favorites = order + c.favorites.filter { it !in order }) }
            Toast.makeText(requireContext(), R.string.move_mode_done, Toast.LENGTH_SHORT).show()
        }
        override fun onModeChanged(active: Boolean) { b.moveHint.isVisible = active; grid.allowEdgePaging = !active }
        override fun focusedKey() = (grid.focusedItem() as? GridItem.App)?.entry?.key
    })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentFavoritesBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launcherHost?.setHeader(PageHeader(getString(R.string.title_favorites), getString(R.string.subtitle_favorites), R.drawable.ic_star))
        val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        b.editButton.styleAsChip(); b.reorderButton.styleAsChip()
        b.moveHint.glass(GlassDrawable.Variant.PANEL, 16f)
        val pager = PagerViews(b.pagerControls, b.root.findViewById(com.akaaflix.launcher.apps.R.id.prevPage),
            b.root.findViewById(com.akaaflix.launcher.apps.R.id.nextPage), b.root.findViewById(com.akaaflix.launcher.apps.R.id.pageIndicator))
        val cb = object : AppGridAdapter.Callbacks {
            override fun onItemClick(item: GridItem, view: View) {
                if (move.active) return
                when (item) {
                    is GridItem.App -> AppActions.open(this@FavoritesFragment, item.entry, view)
                    GridItem.Add -> AppActions.editFavorites(this@FavoritesFragment)
                }
            }
            override fun onItemLongClick(item: GridItem, view: View): Boolean {
                val app = item as? GridItem.App ?: return false
                if (!move.active) AppActions.showMenu(this@FavoritesFragment, app.entry, view) { move.begin(app.entry.key) }
                return true
            }
        }
        grid = AppGridController(this, b.grid, if (portrait) null else pager, { w, h ->
            if (portrait) GridSizing.scrolling(w, requireContext().dpi(84f), 1.08f, 0, 3, 6)
            else GridSizing.paged(w, h, requireContext().dpi(124f), 0.70f, 0, 3, 9)
        }, cb)
        if (portrait) b.pagerControls.visibility = View.GONE
        recent = AppGridController(this, b.recentGrid, null, { w, h ->
            val cols = (w / requireContext().dpi(if (portrait) 84f else 124f)).coerceIn(3, 9)
            val cw = w / cols
            GridSpec(cols, 1, cw, minOf(h, (cw * 0.72f).toInt()), true)
        }, object : AppGridAdapter.Callbacks {
            override fun onItemClick(item: GridItem, view: View) { (item as? GridItem.App)?.let { AppActions.open(this@FavoritesFragment, it.entry, view) } }
            override fun onItemLongClick(item: GridItem, view: View): Boolean {
                (item as? GridItem.App)?.let { AppActions.showMenu(this@FavoritesFragment, it.entry, view) }; return true
            }
        })
        recent.allowEdgePaging = false

        b.editButton.setOnClickListener { AppActions.editFavorites(this) }
        b.reorderButton.setOnClickListener { if (!move.active) move.begin(null); grid.focusFirst() }
        if (arguments?.getBoolean(NavArgs.MOVE_MODE) == true) b.root.post { move.begin(null); grid.focusFirst() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(repo.apps, prefs.apps, prefs.settings) { a, c, s -> Triple(a, c, s) }.collect { (apps, custom, s) ->
                    val cfg = CardConfig(iconShape = "original", showLabels = s.showAppLabels, useBanners = s.useTvBanners, cardStyle = s.cardStyle,
                        iconScale = if (s.iconSize == "large") 1.15f else 1f)
                    grid.setConfig(cfg); recent.setConfig(cfg)
                    if (!move.active) {
                        val favs = AppLists.favorites(apps, custom)
                        grid.setItems(favs.map { GridItem.App(it, it.key in custom.locked) } + GridItem.Add)
                        b.emptyText.isVisible = favs.isEmpty() && apps.isNotEmpty()
                    }
                    val r = AppLists.recent(apps, custom, 12)
                    recent.setItems(r.map { GridItem.App(it, it.key in custom.locked) })
                    b.recentEmpty.isVisible = r.isEmpty()
                }
            }
        }
    }

    override fun onInterceptKey(event: KeyEvent) = move.onKey(event)

    override fun onMenuKey(): Boolean {
        val item = (grid.focusedItem() ?: recent.focusedItem()) as? GridItem.App ?: return false
        AppActions.showMenu(this, item.entry, null) { move.begin(item.entry.key) }; return true
    }

    override fun onBackPressed(): Boolean {
        if (move.active) { move.end(); return true }
        return false
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
