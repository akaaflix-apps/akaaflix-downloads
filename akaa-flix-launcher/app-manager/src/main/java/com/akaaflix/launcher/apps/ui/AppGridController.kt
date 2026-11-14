package com.akaaflix.launcher.apps.ui

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.akaaflix.launcher.apps.icons.IconCache
import com.akaaflix.launcher.core.ui.FocusEffects
import com.akaaflix.launcher.core.ui.GlassDrawable
import com.akaaflix.launcher.core.ui.PageIndicatorView
import com.akaaflix.launcher.core.ui.glass
import com.akaaflix.launcher.remote.PagedGridRecyclerView
import com.akaaflix.launcher.remote.PagingMath
import kotlin.math.abs

data class GridSpec(val columns: Int, val rows: Int, val cellW: Int, val cellH: Int, val paged: Boolean) {
    val pageSize: Int get() = if (paged) columns * rows else Int.MAX_VALUE
}

object GridSizing {
    /** Paged grid filling the container; [aspect] = cell height / width (glow margin included). */
    fun paged(w: Int, h: Int, minCellW: Int, aspect: Float, fixedCols: Int = 0, minCols: Int = 3, maxCols: Int = 9, maxRows: Int = 99): GridSpec {
        val cols = if (fixedCols > 0) fixedCols else PagingMath.fit(w, minCellW, minCols, maxCols)
        val cellW = w / cols
        val cellH = (cellW * aspect).toInt().coerceAtLeast(1)
        val rows = (h / cellH).coerceIn(1, maxRows)
        return GridSpec(cols, rows, cellW, cellH, true)
    }

    fun scrolling(w: Int, minCellW: Int, aspect: Float, fixedCols: Int = 0, minCols: Int = 3, maxCols: Int = 6): GridSpec {
        val cols = if (fixedCols > 0) fixedCols.coerceAtMost(maxCols) else PagingMath.fit(w, minCellW, minCols, maxCols)
        val cellW = w / cols
        return GridSpec(cols, 0, cellW, (cellW * aspect).toInt(), false)
    }
}

class PagerViews(val container: View, val prev: ImageView, val next: ImageView, val indicator: PageIndicatorView)

/**
 * Shared grid behaviour for Home, All Apps and Favorites: sizing from the available space,
 * pages with D-pad edge paging + arrows + swipe, focus restoration and reorder support.
 */
class AppGridController(
    private val fragment: Fragment,
    val grid: PagedGridRecyclerView,
    private val pager: PagerViews?,
    private val sizing: (w: Int, h: Int) -> GridSpec,
    callbacks: AppGridAdapter.Callbacks,
) {
    val adapter = AppGridAdapter(fragment.viewLifecycleOwner.lifecycleScope, IconCache.get(fragment.requireContext()), callbacks)
    private val layoutManager = GridLayoutManager(fragment.requireContext(), 1)
    var spec: GridSpec? = null
        private set
    var page = 0
        private set
    private var items: List<GridItem> = emptyList()
    private var baseConfig = CardConfig()
    var onPageChanged: ((Int, Int) -> Unit)? = null
    var allowEdgePaging = true

    val pageCount: Int get() = spec?.let { if (it.paged) PagingMath.pageCount(items.size, it.pageSize) else 1 } ?: 1
    val allItems: List<GridItem> get() = items

    init {
        grid.layoutManager = layoutManager
        grid.adapter = adapter
        grid.setHasFixedSize(false)
        (grid.parent as? ViewGroup)?.addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) v.post { measure(v) }
        }
        (grid.parent as? View)?.let { p -> p.post { measure(p) } }
        grid.edgeListener = PagedGridRecyclerView.EdgeListener { edge, col, row ->
            val s = spec ?: return@EdgeListener false
            if (!s.paged || !allowEdgePaging) return@EdgeListener false
            when (edge) {
                PagedGridRecyclerView.Edge.BOTTOM -> if (page < pageCount - 1) { goToPage(page + 1, focusColumn = col, focusRow = 0); true } else false
                PagedGridRecyclerView.Edge.TOP -> if (page > 0) { goToPage(page - 1, focusColumn = col, focusRow = s.rows - 1); true } else false
                PagedGridRecyclerView.Edge.END -> if (page < pageCount - 1) { goToPage(page + 1, focusColumn = 0, focusRow = row); true } else false
            }
        }
        pager?.let { p ->
            p.prev.glass(GlassDrawable.Variant.CIRCLE, 20f, 3f)
            p.next.glass(GlassDrawable.Variant.CIRCLE, 20f, 3f)
            FocusEffects.attach(p.prev, 1.1f); FocusEffects.attach(p.next, 1.1f)
            p.prev.setOnClickListener { if (page > 0) goToPage(page - 1) }
            p.next.setOnClickListener { if (page < pageCount - 1) goToPage(page + 1) }
        }
        val detector = GestureDetectorCompat(fragment.requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val s = spec ?: return false
                if (!s.paged || abs(vx) < abs(vy) * 1.5f || abs(vx) < 800) return false
                if (vx < 0 && page < pageCount - 1) goToPage(page + 1, requestFocus = false)
                else if (vx > 0 && page > 0) goToPage(page - 1, requestFocus = false)
                return true
            }
        })
        grid.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean { detector.onTouchEvent(e); return false }
        })
    }

    private fun measure(container: View) {
        // Posted layout callbacks can run after the page was detached (activity recreated for a
        // language / font change): the old page must not measure any more.
        if (!fragment.isAdded) return
        val w = container.width - container.paddingLeft - container.paddingRight
        val h = container.height - container.paddingTop - container.paddingBottom
        if (w <= 0 || h <= 0) return
        val s = sizing(w, h)
        if (s == spec) return
        spec = s
        layoutManager.spanCount = s.columns
        grid.columns = s.columns
        adapter.updateConfig(baseConfig.copy(cellW = s.cellW, cellH = s.cellH))
        render(null)
    }

    fun setConfig(c: CardConfig) {
        baseConfig = c
        spec?.let { adapter.updateConfig(c.copy(cellW = it.cellW, cellH = it.cellH)) }
    }

    fun relayout() { spec = null; (grid.parent as? View)?.let { measure(it) } }

    fun setItems(list: List<GridItem>, focusId: String? = null) {
        items = list
        val s = spec
        if (focusId != null && s != null && s.paged) {
            val idx = list.indexOfFirst { it.id == focusId }
            if (idx >= 0) page = idx / s.pageSize
        }
        render(focusId)
    }

    fun goToPage(p: Int, focusColumn: Int = 0, focusRow: Int = 0, requestFocus: Boolean = true) {
        val s = spec ?: return
        page = PagingMath.clampPage(p, items.size, s.pageSize)
        val range = PagingMath.pageRange(items.size, page, s.pageSize)
        val target = PagingMath.targetPosition(range.count(), s.columns, focusColumn, focusRow)
        submit(range) { if (requestFocus) grid.focusPosition(target) }
    }

    private fun render(focusId: String?) {
        val s = spec ?: return
        if (s.paged) page = PagingMath.clampPage(page, items.size, s.pageSize)
        val range = if (s.paged) PagingMath.pageRange(items.size, page, s.pageSize) else items.indices
        submit(range) {
            if (focusId != null) {
                val pos = adapter.currentList.indexOfFirst { it.id == focusId }
                if (pos >= 0) grid.focusPosition(pos)
            }
        }
    }

    private fun submit(range: IntRange, after: () -> Unit) {
        val slice = if (range.isEmpty()) emptyList() else items.subList(range.first, range.last + 1).toList()
        adapter.submitList(slice) { after() }
        updatePager()
    }

    private fun updatePager() {
        val p = pager ?: return
        val count = pageCount
        p.container.visibility = if (count > 1) View.VISIBLE else View.INVISIBLE
        p.indicator.count = count
        p.indicator.current = page
        p.prev.alpha = if (page > 0) 1f else 0.4f
        p.next.alpha = if (page < count - 1) 1f else 0.4f
        onPageChanged?.invoke(page, count)
    }

    fun focusFirst(): Boolean {
        if (adapter.itemCount == 0) return false
        grid.focusPosition(0); return true
    }

    fun focusId(id: String): Boolean {
        val pos = adapter.currentList.indexOfFirst { it.id == id }
        if (pos < 0) return false
        grid.focusPosition(pos); return true
    }

    fun focusedItem(): GridItem? {
        val child = grid.focusedChild ?: return null
        val pos = grid.getChildAdapterPosition(child)
        return adapter.currentList.getOrNull(pos)
    }
}
