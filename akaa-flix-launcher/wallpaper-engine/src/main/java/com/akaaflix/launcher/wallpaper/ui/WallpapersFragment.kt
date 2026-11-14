package com.akaaflix.launcher.wallpaper.ui

import com.akaaflix.launcher.core.util.str

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.akaaflix.launcher.core.R as CoreR
import com.akaaflix.launcher.core.nav.BackHandler
import com.akaaflix.launcher.core.nav.PageHeader
import com.akaaflix.launcher.core.nav.launcherHost
import com.akaaflix.launcher.core.perf.DevicePerformance
import com.akaaflix.launcher.core.ui.FocusEffects
import com.akaaflix.launcher.core.ui.GlassDrawable
import com.akaaflix.launcher.core.ui.LauncherUi
import com.akaaflix.launcher.core.ui.dialog.Dialogs
import com.akaaflix.launcher.core.ui.dpi
import com.akaaflix.launcher.core.ui.glass
import com.akaaflix.launcher.core.ui.rows.SettingRow
import com.akaaflix.launcher.core.ui.tint
import com.akaaflix.launcher.prefs.LauncherSettings
import com.akaaflix.launcher.prefs.SettingsRepository
import com.akaaflix.launcher.remote.PagedGridRecyclerView
import com.akaaflix.launcher.remote.PagingMath
import com.akaaflix.launcher.wallpaper.ImportResult
import com.akaaflix.launcher.wallpaper.ThumbnailLoader
import com.akaaflix.launcher.wallpaper.WallpaperRepository
import com.akaaflix.launcher.wallpaper.databinding.FragmentWallpapersBinding
import com.akaaflix.launcher.wallpaper.model.Wallpaper
import com.akaaflix.launcher.wallpaper.model.WallpaperCategory
import com.akaaflix.launcher.wallpaper.model.WallpaperType
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Wallpaper gallery from the reference design. */
class WallpapersFragment : Fragment(), BackHandler {
    private var _b: FragmentWallpapersBinding? = null
    private val b get() = _b!!
    private val repo by lazy { WallpaperRepository.get(requireContext()) }
    private val prefs by lazy { SettingsRepository.get(requireContext()) }
    private lateinit var adapter: WallpaperGridAdapter
    private val layoutManager by lazy { GridLayoutManager(requireContext(), 4) }

    private var category: WallpaperCategory? = null
    private var favoritesOnly = false
    private var items: List<WpItem> = emptyList()
    private var page = 0
    private var cols = 4
    private var rows = 4
    private var paged = true
    private var selected: Wallpaper? = null
    private var pendingVideo = false
    private var previewing = false

    private val openDocument = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (res.resultCode == Activity.RESULT_OK && uri != null) import(uri, null)
    }
    /** System photo picker (Android 11+ / Play-services backport): access to the chosen file only. */
    private val pickVisualMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) import(uri, null)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentWallpapersBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launcherHost?.setHeader(PageHeader(getString(CoreR.string.title_wallpapers), getString(CoreR.string.subtitle_wallpapers), CoreR.drawable.ic_image))
        paged = resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT
        b.detailPanel.glass(GlassDrawable.Variant.PANEL, 18f)
        b.rotationCard.glass(GlassDrawable.Variant.PANEL, 18f)
        b.rotationToggle.glass(GlassDrawable.Variant.PANEL, 14f, 3f).cardStyle = "minimal"
        b.setButton.glass(GlassDrawable.Variant.PRIMARY, 22f, 3f)
        FocusEffects.attach(b.setButton, 1.04f)
        listOf(b.previewButton, b.fullscreenButton, b.favoriteButton).forEach {
            it.glass(GlassDrawable.Variant.PANEL, 14f, 3f); FocusEffects.attach(it, 1.06f); it.sizeDrawables(22f)
        }
        b.intervalChip.glass(GlassDrawable.Variant.CHIP, 20f, 3f)
        b.intervalChip.sizeDrawables(16f)
        b.setButton.sizeDrawables(20f)
        b.detailResolution.sizeDrawables(18f)
        b.previewBadge.glass(GlassDrawable.Variant.PANEL, 6f)
        b.previewImage.roundClip(requireContext().dpi(12f).toFloat())
        b.titleIcon.tint(LauncherUi.palette.accentLight)
        listOf(b.prevPage, b.nextPage).forEach { it.glass(GlassDrawable.Variant.CIRCLE, 20f, 3f); FocusEffects.attach(it, 1.1f) }

        adapter = WallpaperGridAdapter(viewLifecycleOwner.lifecycleScope, ThumbnailLoader.get(requireContext()),
            onClick = { item, _ -> onItemClick(item) },
            onLongClick = { item, _ -> onItemOptions(item) },
            onFocus = { item -> (item as? WpItem.Item)?.let { select(it.wp) } })
        b.grid.layoutManager = layoutManager
        b.grid.adapter = adapter
        b.grid.edgeListener = PagedGridRecyclerView.EdgeListener { edge, col, row ->
            if (!paged) return@EdgeListener false
            val count = PagingMath.pageCount(items.size, cols * rows)
            when (edge) {
                PagedGridRecyclerView.Edge.BOTTOM -> if (page < count - 1) { goTo(page + 1, col, 0); true } else false
                PagedGridRecyclerView.Edge.TOP -> if (page > 0) { goTo(page - 1, col, rows - 1); true } else false
                PagedGridRecyclerView.Edge.END -> false
            }
        }
        b.gridContainer.addOnLayoutChangeListener { v, l, t, r, bt, ol, ot, or, ob -> if (r - l != or - ol || bt - t != ob - ot) v.post { measureGrid() } }
        b.prevPage.setOnClickListener { if (page > 0) goTo(page - 1, 0, 0) }
        b.nextPage.setOnClickListener { goTo(page + 1, 0, 0) }

        b.setButton.setOnClickListener { selected?.let { apply(it) } }
        b.previewButton.setOnClickListener { selected?.let { previewing = true; launcherHost?.previewWallpaper(it.id); launcherHost?.peekBehindUi() } }
        b.fullscreenButton.setOnClickListener {
            val wp = selected ?: return@setOnClickListener
            FullscreenPreviewDialog.show(requireContext(), wp, { repo.resolve(it) }, motionAllowed()) { apply(wp) }
        }
        b.favoriteButton.setOnClickListener { selected?.let { repo.toggleFavorite(it.id) } }
        b.rotationToggle.setOnClickListener {
            val on = !prefs.settings.value.rotationEnabled
            b.rotationSwitch.setChecked(on, true)
            prefs.edit { it.copy(rotationEnabled = on, lastRotationAt = System.currentTimeMillis()) }
        }
        b.intervalChip.setOnClickListener { chooseInterval() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(repo.wallpapers, prefs.wallpapers, prefs.settings) { list, data, s -> Triple(list, data, s) }.collect { (list, data, s) ->
                    buildChips(list, data.favorites)
                    val filtered = list.filter { wp ->
                        when {
                            favoritesOnly -> wp.id in data.favorites
                            category != null -> wp.category == category
                            else -> true
                        }
                    }
                    items = listOf<WpItem>(WpItem.Add) + filtered.map { WpItem.Item(it, it.id in data.favorites, it.id == s.wallpaperId) }
                    if (selected == null) selected = list.firstOrNull { it.id == s.wallpaperId } ?: list.firstOrNull()
                    selected = list.firstOrNull { it.id == selected?.id } ?: selected
                    render()
                    renderDetail(s, data.favorites)
                    b.rotationSwitch.setChecked(s.rotationEnabled, false)
                    b.intervalChip.text = intervalLabel(s.rotationIntervalMin)
                }
            }
        }
        b.grid.post { measureGrid() }
    }

    private fun TextView.sizeDrawables(dp: Float) {
        val s = requireContext().dpi(dp)
        val ds: Array<Drawable?> = compoundDrawablesRelative
        ds.forEach { it?.mutate()?.setBounds(0, 0, s, s); it?.setTint(LauncherUi.palette.accentLight) }
        setCompoundDrawablesRelative(ds[0], ds[1], ds[2], ds[3])
    }

    private fun motionAllowed(): Boolean {
        val q = DevicePerformance.quality(prefs.settings.value.performanceMode, DevicePerformance.profile(requireContext()))
        return q.videoWallpapers && prefs.settings.value.animatedWallpaper
    }

    private fun measureGrid() {
        val c = _b?.gridContainer ?: return
        val w = c.width; val h = c.height
        if (w <= 0) return
        val minCell = requireContext().dpi(if (paged) 134f else 150f)
        cols = (w / minCell).coerceIn(2, 6)
        val cw = w / cols
        val ch = (cw * 0.62f).toInt()
        rows = if (paged) (h / ch).coerceAtLeast(1) else 99
        adapter.cellW = cw; adapter.cellH = ch
        layoutManager.spanCount = cols
        b.grid.columns = cols
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
        render()
    }

    private fun render() {
        val size = if (paged) cols * rows else Int.MAX_VALUE
        page = PagingMath.clampPage(page, items.size, size)
        val range = if (paged) PagingMath.pageRange(items.size, page, size) else items.indices
        adapter.submitList(if (range.isEmpty()) emptyList() else items.subList(range.first, range.last + 1).toList())
        val count = if (paged) PagingMath.pageCount(items.size, size) else 1
        b.pagerControls.visibility = if (count > 1) View.VISIBLE else if (paged) View.INVISIBLE else View.GONE
        b.pageIndicator.count = count; b.pageIndicator.current = page
    }

    private fun goTo(p: Int, col: Int, row: Int) {
        val size = cols * rows
        page = PagingMath.clampPage(p, items.size, size)
        render()
        val target = PagingMath.targetPosition(PagingMath.pageRange(items.size, page, size).count(), cols, col, row)
        b.grid.post { b.grid.focusPosition(target) }
    }

    private fun buildChips(list: List<Wallpaper>, favorites: Set<String>) {
        val row = b.chipsRow
        val defs = mutableListOf<Triple<String, String, Int>>()
        defs += Triple("all", str(CoreR.string.wp_cat_all, list.size), CoreR.drawable.ic_apps)
        defs += Triple(WallpaperCategory.CINEMATIC.name, getString(CoreR.string.wp_cat_cinematic), CoreR.drawable.ic_video)
        defs += Triple(WallpaperCategory.SPACE.name, getString(CoreR.string.wp_cat_space), CoreR.drawable.ic_globe)
        defs += Triple(WallpaperCategory.NATURE.name, getString(CoreR.string.wp_cat_nature), CoreR.drawable.ic_sun_outline)
        defs += Triple(WallpaperCategory.LANDMARKS.name, getString(CoreR.string.wp_cat_landmarks), CoreR.drawable.ic_image)
        defs += Triple(WallpaperCategory.ABSTRACT.name, getString(CoreR.string.wp_cat_abstract), CoreR.drawable.ic_sparkles)
        defs += Triple(WallpaperCategory.ANIMATED.name, getString(CoreR.string.wp_cat_animated), CoreR.drawable.ic_play_box)
        defs += Triple("fav", getString(CoreR.string.wp_cat_favorites), CoreR.drawable.ic_star)
        if (list.any { it.category == WallpaperCategory.MINE }) defs += Triple(WallpaperCategory.MINE.name, getString(CoreR.string.wp_cat_mine), CoreR.drawable.ic_upload)
        val selectedKey = when { favoritesOnly -> "fav"; category != null -> category!!.name; else -> "all" }
        if (row.childCount != defs.size) {
            row.removeAllViews()
            defs.forEach { (key, _, icon) ->
                val chip = AppCompatTextView(requireContext(), null, 0).apply {
                    tag = key
                    setTextAppearance(CoreR.style.TextAppearance_Akaa_LabelBold)
                    gravity = android.view.Gravity.CENTER
                    isFocusable = true; isClickable = true; isSingleLine = true
                    setPadding(requireContext().dpi(16f), 0, requireContext().dpi(16f), 0)
                    compoundDrawablePadding = requireContext().dpi(8f)
                    setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
                    glass(GlassDrawable.Variant.CHIP, 20f, 3f)
                    FocusEffects.attach(this, 1.05f)
                    sizeDrawables(18f)
                    setOnClickListener { onChip(key) }
                }
                row.addView(chip, android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, requireContext().dpi(40f)).apply { marginEnd = requireContext().dpi(8f) })
            }
        }
        for (i in 0 until row.childCount) {
            val chip = row.getChildAt(i) as TextView
            chip.text = defs[i].second
            chip.isActivated = chip.tag == selectedKey
        }
    }

    private fun onChip(key: String) {
        favoritesOnly = key == "fav"
        category = if (key == "all" || key == "fav") null else WallpaperCategory.valueOf(key)
        page = 0
        val s = prefs.settings.value
        val data = prefs.wallpapers.value
        val list = repo.wallpapers.value
        val filtered = list.filter { wp -> when { favoritesOnly -> wp.id in data.favorites; category != null -> wp.category == category; else -> true } }
        items = listOf<WpItem>(WpItem.Add) + filtered.map { WpItem.Item(it, it.id in data.favorites, it.id == s.wallpaperId) }
        buildChips(list, data.favorites)
        render()
    }

    private fun select(wp: Wallpaper) {
        if (selected?.id == wp.id) return
        selected = wp
        renderDetail(prefs.settings.value, prefs.wallpapers.value.favorites)
    }

    private fun renderDetail(s: LauncherSettings, favorites: Set<String>) {
        val wp = selected ?: return
        b.detailTitle.text = wp.name(requireContext())
        b.previewBadge.text = wp.badge()
        b.detailResolution.text = str(CoreR.string.wp_resolution_fmt, wp.sourceWidth, wp.sourceHeight) + "  " + wp.badge()
        val (typeText, typeIcon) = when (wp.type) {
            WallpaperType.STATIC -> CoreR.string.wp_type_static to CoreR.drawable.ic_image
            WallpaperType.VIDEO -> CoreR.string.wp_type_video to CoreR.drawable.ic_video
            WallpaperType.ANIMATED -> CoreR.string.wp_type_animated to CoreR.drawable.ic_sparkles
        }
        b.detailType.setText(typeText)
        b.detailType.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, typeIcon, 0)
        b.detailType.sizeDrawables(18f)
        val fav = wp.id in favorites
        b.favoriteButton.setText(if (fav) CoreR.string.wp_remove_favorite else CoreR.string.wp_add_favorite)
        b.favoriteButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, if (fav) CoreR.drawable.ic_heart_filled else CoreR.drawable.ic_heart, 0, 0)
        b.favoriteButton.sizeDrawables(22f)
        if (fav) b.favoriteButton.compoundDrawablesRelative[1]?.setTintList(null)
        b.setButton.alpha = if (wp.id == s.wallpaperId) 0.75f else 1f
        val tl = ThumbnailLoader.get(requireContext())
        val w = requireContext().dpi(250f); val h = w * 9 / 16
        tl.peek(wp, w, h)?.let { b.previewImage.setImageBitmap(it) } ?: viewLifecycleOwner.lifecycleScope.launch {
            val bmp = tl.load(wp, w, h)
            if (selected?.id == wp.id) b.previewImage.setImageBitmap(bmp)
        }
    }

    private fun apply(wp: Wallpaper) {
        repo.apply(wp.id)
        previewing = false
        launcherHost?.previewWallpaper(null)
        Toast.makeText(requireContext(), CoreR.string.wp_applied, Toast.LENGTH_SHORT).show()
        if (wp.type != WallpaperType.STATIC && !motionAllowed()) Toast.makeText(requireContext(), CoreR.string.wp_video_lightweight, Toast.LENGTH_LONG).show()
    }

    private fun onItemClick(item: WpItem) {
        when (item) {
            WpItem.Add -> chooseImportType()
            is WpItem.Item -> {
                selected = null; select(item.wp)
                if (!b.grid.isInTouchMode) b.setButton.requestFocus()
            }
        }
    }

    private fun onItemOptions(item: WpItem) {
        val wp = (item as? WpItem.Item)?.wp ?: return
        val ctx = requireContext()
        var dialog: com.akaaflix.launcher.core.ui.dialog.LauncherDialog? = null
        val rows = mutableListOf<SettingRow>(
            SettingRow.Action("apply", getString(CoreR.string.wp_set), icon = CoreR.drawable.ic_check, showChevron = false) { dialog?.dialog?.dismiss(); apply(wp) },
            SettingRow.Action("fav", getString(if (item.favorite) CoreR.string.wp_remove_favorite else CoreR.string.wp_add_favorite),
                icon = CoreR.drawable.ic_heart, showChevron = false) { dialog?.dialog?.dismiss(); repo.toggleFavorite(wp.id) },
        )
        if (wp.isUser) rows += SettingRow.Action("del", getString(CoreR.string.delete), icon = CoreR.drawable.ic_trash, danger = true, showChevron = false) {
            dialog?.dialog?.dismiss()
            Dialogs.confirm(ctx, wp.name(ctx), getString(CoreR.string.wp_delete_confirm), getString(CoreR.string.delete)) {
                viewLifecycleOwner.lifecycleScope.launch { repo.delete(wp.id) }
            }
        }
        dialog = Dialogs.rows(ctx, wp.name(ctx), rows, icon = CoreR.drawable.ic_image, widthDp = 420)
    }

    private fun chooseImportType() {
        Dialogs.choose(requireContext(), getString(CoreR.string.wp_pick_title), listOf(getString(CoreR.string.wp_pick_image), getString(CoreR.string.wp_pick_video)), 0) { i ->
            pendingVideo = i == 1
            pick(pendingVideo)
        }
    }

    /**
     * Photo picker → system document picker → GET_CONTENT (any installed file manager). No storage
     * permission is ever requested; each picker grants access to the one file the user chooses.
     */
    private fun pick(video: Boolean) {
        val ctx = requireContext()
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(ctx)) {
            val type = if (video) ActivityResultContracts.PickVisualMedia.VideoOnly else ActivityResultContracts.PickVisualMedia.ImageOnly
            try { pickVisualMedia.launch(PickVisualMediaRequest(type)); return } catch (_: ActivityNotFoundException) {}
        }
        val mime = if (video) "video/*" else "image/*"
        val intents = listOf(
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime),
            Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime),
        )
        for (i in intents) {
            try { openDocument.launch(i); return } catch (_: ActivityNotFoundException) {} catch (_: SecurityException) {}
        }
        Dialogs.info(ctx, getString(CoreR.string.wp_pick_title), getString(CoreR.string.wp_no_picker))
    }

    private fun import(uri: Uri, name: String?) {
        val ctx = requireContext().applicationContext
        Toast.makeText(ctx, CoreR.string.wp_importing, Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val type = ctx.contentResolver.getType(uri) ?: ""
            val r = if (pendingVideo || type.startsWith("video")) repo.importVideo(uri, name) else repo.importImage(uri, name)
            when (r) {
                is ImportResult.Success -> { Toast.makeText(ctx, CoreR.string.wp_import_done, Toast.LENGTH_SHORT).show(); selected = r.wallpaper; _b?.let { renderDetail(prefs.settings.value, prefs.wallpapers.value.favorites) } }
                ImportResult.TooLarge -> Toast.makeText(ctx, CoreR.string.wp_video_too_large, Toast.LENGTH_LONG).show()
                ImportResult.Failed -> Toast.makeText(ctx, CoreR.string.wp_import_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun intervalLabel(min: Int): String = when {
        min < 60 -> str(CoreR.string.wp_every_min, min)
        min == 60 -> getString(CoreR.string.wp_every_hour)
        min >= 1440 -> getString(CoreR.string.wp_every_day)
        else -> str(CoreR.string.wp_every_hours, min / 60)
    }

    private fun chooseInterval() {
        val values = LauncherSettings.ROTATION_INTERVALS
        val labels = values.map { intervalLabel(it) }
        val cur = values.indexOf(prefs.settings.value.rotationIntervalMin).coerceAtLeast(0)
        Dialogs.choose(requireContext(), getString(CoreR.string.row_rotation_interval), labels, cur) { i ->
            prefs.edit { it.copy(rotationIntervalMin = values[i], lastRotationAt = System.currentTimeMillis()) }
        }
    }

    override fun onBackPressed(): Boolean = false

    override fun onDestroyView() {
        if (previewing) launcherHost?.previewWallpaper(null)
        super.onDestroyView(); _b = null
    }
}
