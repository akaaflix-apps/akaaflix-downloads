package com.akaaflix.launcher.apps.ui

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.akaaflix.launcher.apps.icons.IconCache
import com.akaaflix.launcher.apps.model.AppEntry
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.remote.Keys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed class GridItem {
    abstract val id: String
    data class App(val entry: AppEntry, val locked: Boolean, val moving: Boolean = false) : GridItem() { override val id get() = entry.key }
    data object Add : GridItem() { override val id get() = AppCardView.ADD_KEY }
}

/** Visual options for cards (all from settings / customization draft). */
data class CardConfig(
    val cellW: Int = 0,
    val cellH: Int = 0,
    val iconShape: String = "original",
    val showLabels: Boolean = true,
    val useBanners: Boolean = true,
    val cardStyle: String = "glass",
    val iconScale: Float = 1f,
)

class AppGridAdapter(
    private val scope: CoroutineScope,
    private val icons: IconCache,
    private val callbacks: Callbacks,
) : ListAdapter<GridItem, AppGridAdapter.VH>(Diff) {

    interface Callbacks {
        fun onItemClick(item: GridItem, view: View)
        fun onItemLongClick(item: GridItem, view: View): Boolean
        fun onItemMenuKey(item: GridItem, view: View): Boolean = onItemLongClick(item, view)
        fun onItemFocused(item: GridItem, view: View) {}
    }

    var config = CardConfig()
        private set

    init { setHasStableIds(true) }

    fun updateConfig(c: CardConfig) {
        if (c != config) { config = c; notifyItemRangeChanged(0, itemCount) }
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = VH(AppCardView(parent.context))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val card = holder.card
        val cfg = config
        val lp = card.layoutParams as? RecyclerView.LayoutParams
        if (lp == null || lp.width != cfg.cellW || lp.height != cfg.cellH) card.layoutParams = RecyclerView.LayoutParams(cfg.cellW, cfg.cellH)
        card.showLabel = cfg.showLabels
        card.iconScale = cfg.iconScale
        holder.job?.cancel()
        when (item) {
            is GridItem.Add -> card.bindAdd(card.context.getString(R.string.home_add_app), cfg.cardStyle)
            is GridItem.App -> bindApp(holder, item, cfg)
        }
        card.glass.forceSelected = (item as? GridItem.App)?.moving == true
        card.setOnClickListener { callbacks.onItemClick(item, it) }
        card.setOnLongClickListener { callbacks.onItemLongClick(item, it) }
        card.setOnKeyListener { v, keyCode, event ->
            if (Keys.isMenu(keyCode)) {
                if (event.action == KeyEvent.ACTION_UP) callbacks.onItemMenuKey(item, v)
                true
            } else false
        }
        card.onFocusChangeListener = View.OnFocusChangeListener { v, has -> if (has) callbacks.onItemFocused(item, v) }
    }

    private fun bindApp(holder: VH, item: GridItem.App, cfg: CardConfig) {
        val card = holder.card
        val e = item.entry
        card.bindApp(e.key, e.label, item.locked, cfg.cardStyle)
        val iconPx = (((cfg.cellH * 0.5f * cfg.iconScale).toInt() + 7) / 8 * 8).coerceIn(32, 256)
        val wantBanner = cfg.useBanners && cfg.cellH > 0 && cfg.cellW.toFloat() / cfg.cellH >= 1.25f
        val bw = (cfg.cellW * 0.9f).toInt().coerceAtLeast(1); val bh = (cfg.cellH * 0.9f).toInt().coerceAtLeast(1)
        val banner = if (wantBanner) icons.peekBanner(e, bw, bh) else null
        if (banner != null) { card.setBanner(banner); return }
        icons.peekIcon(e, iconPx, cfg.iconShape)?.let { card.setIcon(it); card.glass.glowTint = icons.dominantColor(e) }
        holder.job = scope.launch {
            if (wantBanner && !icons.hasNoBanner(e, bw, bh)) {
                val b = icons.banner(e, bw, bh)
                if (b != null && card.boundKey == e.key) { card.setBanner(b); return@launch }
            }
            if (card.iconView.drawable == null) {
                val bmp = icons.icon(e, iconPx, cfg.iconShape)
                if (card.boundKey == e.key) { card.setIcon(bmp); card.glass.glowTint = icons.dominantColor(e) }
            }
        }
    }

    override fun onViewRecycled(holder: VH) { holder.job?.cancel(); holder.job = null }

    class VH(val card: AppCardView) : RecyclerView.ViewHolder(card) { var job: Job? = null }

    private object Diff : DiffUtil.ItemCallback<GridItem>() {
        override fun areItemsTheSame(oldItem: GridItem, newItem: GridItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: GridItem, newItem: GridItem) = oldItem == newItem
    }
}
