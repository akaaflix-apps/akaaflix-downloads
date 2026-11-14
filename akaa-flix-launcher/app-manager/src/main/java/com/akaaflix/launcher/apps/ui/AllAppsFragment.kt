package com.akaaflix.launcher.apps.ui

import com.akaaflix.launcher.core.util.str

import android.app.Application
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.akaaflix.launcher.apps.AppLists
import com.akaaflix.launcher.apps.AppRepository
import com.akaaflix.launcher.apps.databinding.FragmentAllAppsBinding
import com.akaaflix.launcher.apps.model.AppEntry
import com.akaaflix.launcher.apps.model.AppFilter
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.nav.BackHandler
import com.akaaflix.launcher.core.nav.KeyInterceptor
import com.akaaflix.launcher.core.nav.MenuKeyHandler
import com.akaaflix.launcher.core.nav.NavArgs
import com.akaaflix.launcher.core.nav.PageHeader
import com.akaaflix.launcher.core.nav.launcherHost
import com.akaaflix.launcher.core.perf.DevicePerformance
import com.akaaflix.launcher.core.ui.FocusEffects
import com.akaaflix.launcher.core.ui.GlassDrawable
import com.akaaflix.launcher.core.ui.dialog.Dialogs
import com.akaaflix.launcher.core.ui.dpi
import com.akaaflix.launcher.core.ui.glass
import com.akaaflix.launcher.core.util.SafeIntents
import com.akaaflix.launcher.prefs.AppCustomization
import com.akaaflix.launcher.prefs.LauncherSettings
import com.akaaflix.launcher.prefs.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AllAppsState(
    val list: List<AppEntry> = emptyList(),
    val totalVisible: Int = 0,
    val custom: AppCustomization = AppCustomization(),
    val settings: LauncherSettings = LauncherSettings(),
    val loaded: Boolean = false,
)

class AllAppsViewModel(app: Application) : AndroidViewModel(app) {
    val repo = AppRepository.get(app)
    val prefs = SettingsRepository.get(app)
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(AppFilter.ALL)

    val state: StateFlow<AllAppsState> = combine(repo.apps, prefs.apps, prefs.settings, query, filter) { apps, custom, s, q, f ->
        AllAppsState(AppLists.visible(apps, custom, f, q, custom.sortMode), apps.count { it.key !in custom.hidden }, custom, s, true)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, AllAppsState())

    fun setSort(mode: String) = prefs.editApps { it.copy(sortMode = mode) }
}

/**
 * "All apps" page from the reference: search, filter chips (All / Entertainment / Tools) and its own
 * vertically scrolling grid (independent of the Home screen's row limit).
 */
class AllAppsFragment : Fragment(), BackHandler, MenuKeyHandler, KeyInterceptor {
    private var _b: FragmentAllAppsBinding? = null
    private val b get() = _b!!
    private val vm: AllAppsViewModel by viewModels()
    private lateinit var grid: AppGridController
    private var lastState: AllAppsState? = null
    private val move = MoveModeController(object : MoveModeController.Listener {
        override fun currentOrder() = vm.state.value.list.map { it.key }
        override fun columns() = grid.spec?.columns ?: 1
        override fun applyOrder(order: List<String>, movingKey: String?) = showOrder(order, movingKey)
        override fun commit(order: List<String>) {
            vm.prefs.editApps { it.copy(customOrder = order, sortMode = "custom") }
            android.widget.Toast.makeText(requireContext(), R.string.move_mode_done, android.widget.Toast.LENGTH_SHORT).show()
        }
        override fun onModeChanged(active: Boolean) { b.moveHint.isVisible = active; grid.allowEdgePaging = !active }
        override fun focusedKey() = (grid.focusedItem() as? GridItem.App)?.entry?.key
    })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentAllAppsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launcherHost?.setHeader(PageHeader(getString(R.string.title_all_apps), icon = R.drawable.ic_apps))
        val isTv = DevicePerformance.profile(requireContext()).isTv
        val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val searchBox = b.searchInclude.root
        searchBox.glass(GlassDrawable.Variant.FIELD, 20f, 3f)
        b.sortButton.glass(GlassDrawable.Variant.CIRCLE, 20f, 3f)
        FocusEffects.attach(b.sortButton, 1.1f)
        listOf(b.chipAll, b.chipEntertainment, b.chipTools, b.storeButton).forEach { it.styleAsChip() }
        b.moveHint.glass(GlassDrawable.Variant.PANEL, 16f)

        // Scrolling grid: children are clipped to the list, the padding leaves room for the focus zoom.
        val padH = requireContext().dpi(8f)
        b.grid.clipChildren = true
        b.grid.clipToPadding = false
        b.grid.setPadding(padH, requireContext().dpi(8f), padH, requireContext().dpi(18f))
        grid = AppGridController(this, b.grid, null, { w, _ ->
            val s = vm.state.value.settings
            val scale = iconScale(s)
            val inner = w - 2 * padH
            if (portrait) GridSizing.scrolling(inner, requireContext().dpi(84f * scale), 1.08f, s.gridColumns, 3, 6)
            else GridSizing.scrolling(inner, requireContext().dpi((if (isTv) 112f else 120f) * scale), 0.64f, s.gridColumns, 3, 10)
        }, object : AppGridAdapter.Callbacks {
            override fun onItemClick(item: GridItem, view: View) {
                if (move.active) return
                (item as? GridItem.App)?.let { AppActions.open(this@AllAppsFragment, it.entry, view) }
            }
            override fun onItemLongClick(item: GridItem, view: View): Boolean {
                val app = item as? GridItem.App ?: return false
                if (move.active) return true
                when (vm.state.value.settings.longPressAction) {
                    "move" -> startMove(app.entry.key)
                    "info" -> SafeIntents.startOrToast(requireContext(), SafeIntents.appDetails(app.entry.packageName))
                    else -> showMenu(app.entry, view)
                }
                return true
            }
            override fun onItemMenuKey(item: GridItem, view: View): Boolean {
                (item as? GridItem.App)?.let { showMenu(it.entry, view) }; return true
            }
        })
        b.pagerControls.visibility = View.GONE

        b.searchInclude.searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { vm.query.value = s?.toString().orEmpty() }
        })
        b.searchInclude.searchEdit.setOnEditorActionListener { v, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH || id == EditorInfo.IME_ACTION_DONE) {
                (requireContext().getSystemService(InputMethodManager::class.java))?.hideSoftInputFromWindow(v.windowToken, 0)
                grid.focusFirst(); true
            } else false
        }
        b.sortButton.setOnClickListener { chooseSort() }
        b.chipAll.setOnClickListener { vm.filter.value = AppFilter.ALL }
        b.chipEntertainment.setOnClickListener { vm.filter.value = AppFilter.ENTERTAINMENT }
        b.chipTools.setOnClickListener { vm.filter.value = AppFilter.TOOLS }
        b.storeButton.setOnClickListener {
            val q = vm.query.value
            SafeIntents.startOrToast(requireContext(), Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(q))))
        }

        arguments?.let { args ->
            args.getString(NavArgs.SEARCH_QUERY)?.let { q -> b.searchInclude.searchEdit.setText(q); b.searchInclude.searchEdit.setSelection(q.length) }
            if (args.getBoolean(NavArgs.FOCUS_SEARCH)) b.searchInclude.searchEdit.post { b.searchInclude.searchEdit.requestFocus() }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { render(it) }
            }
        }
    }

    private fun iconScale(s: LauncherSettings) = if (s.iconSize == "large") 1.15f else 1f

    private fun render(s: AllAppsState) {
        val prev = lastState
        lastState = s
        b.chipAll.text = str(R.string.apps_chip_all, s.totalVisible)
        b.chipAll.isActivated = vm.filter.value == AppFilter.ALL
        b.chipEntertainment.isActivated = vm.filter.value == AppFilter.ENTERTAINMENT
        b.chipTools.isActivated = vm.filter.value == AppFilter.TOOLS
        grid.setConfig(CardConfig(iconShape = "original", showLabels = s.settings.showAppLabels,
            useBanners = s.settings.useTvBanners, cardStyle = s.settings.cardStyle, iconScale = iconScale(s.settings)))
        if (prev != null && (prev.settings.gridColumns != s.settings.gridColumns || prev.settings.iconSize != s.settings.iconSize)) grid.relayout()
        if (move.active) return
        grid.setItems(s.list.map { GridItem.App(it, it.key in s.custom.locked) })
        val empty = s.loaded && s.list.isEmpty()
        b.emptyState.isVisible = empty
        val q = vm.query.value
        b.storeButton.isVisible = empty && q.isNotBlank() && vm.repo.hasPlayStore()
        if (b.storeButton.isVisible) b.storeButton.text = str(R.string.apps_search_store, q)
    }

    private fun showOrder(order: List<String>, movingKey: String?) {
        val byKey = vm.state.value.list.associateBy { it.key }
        val locked = vm.state.value.custom.locked
        val items = order.mapNotNull { byKey[it] }.map { GridItem.App(it, it.key in locked, it.key == movingKey) }
        grid.setItems(items, focusId = movingKey ?: grid.focusedItem()?.id)
    }

    private fun startMove(key: String?) {
        if (vm.filter.value != AppFilter.ALL || vm.query.value.isNotEmpty() || vm.state.value.custom.sortMode != "custom") {
            vm.filter.value = AppFilter.ALL
            b.searchInclude.searchEdit.setText("")
            vm.prefs.editApps { it.copy(sortMode = "custom", customOrder = vm.state.value.list.map { e -> e.key }) }
        }
        b.root.postDelayed({ move.begin(key) }, 150)
    }

    private fun showMenu(entry: AppEntry, view: View) = AppActions.showMenu(this, entry, view) { startMove(entry.key) }

    private fun chooseSort() {
        val modes = AppCustomization.SORT_MODES
        val labels = listOf(R.string.sort_custom, R.string.sort_az, R.string.sort_za, R.string.sort_installed, R.string.sort_most_used, R.string.sort_recent).map { getString(it) }
        Dialogs.choose(requireContext(), getString(R.string.apps_sort), labels, modes.indexOf(vm.state.value.custom.sortMode).coerceAtLeast(0)) {
            vm.setSort(modes[it])
        }
    }

    override fun onInterceptKey(event: KeyEvent): Boolean = move.onKey(event)

    override fun onMenuKey(): Boolean {
        val item = grid.focusedItem() as? GridItem.App ?: return false
        showMenu(item.entry, b.grid.focusedChild ?: b.grid); return true
    }

    override fun onBackPressed(): Boolean {
        if (move.active) { move.end(); return true }
        if (b.searchInclude.searchEdit.text.isNotEmpty()) { b.searchInclude.searchEdit.setText(""); return true }
        return false
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
