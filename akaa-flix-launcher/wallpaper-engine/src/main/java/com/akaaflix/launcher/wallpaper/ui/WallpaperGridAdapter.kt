package com.akaaflix.launcher.wallpaper.ui

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.akaaflix.launcher.core.R as CoreR
import com.akaaflix.launcher.remote.Keys
import com.akaaflix.launcher.wallpaper.ThumbnailLoader
import com.akaaflix.launcher.wallpaper.model.Wallpaper
import com.akaaflix.launcher.wallpaper.model.WallpaperType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed class WpItem {
    abstract val id: String
    data object Add : WpItem() { override val id get() = WallpaperThumbView.ADD_ID }
    data class Item(val wp: Wallpaper, val favorite: Boolean, val applied: Boolean) : WpItem() { override val id get() = wp.id }
}

class WallpaperGridAdapter(
    private val scope: CoroutineScope,
    private val thumbs: ThumbnailLoader,
    private val onClick: (WpItem, View) -> Unit,
    private val onLongClick: (WpItem, View) -> Unit,
    private val onFocus: (WpItem) -> Unit,
) : ListAdapter<WpItem, WallpaperGridAdapter.VH>(Diff) {
    var cellW = 0
    var cellH = 0

    init { setHasStableIds(true) }
    override fun getItemId(position: Int) = getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(WallpaperThumbView(parent.context))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val v = holder.view
        val lp = v.layoutParams as? RecyclerView.LayoutParams
        if (lp == null || lp.width != cellW || lp.height != cellH) v.layoutParams = RecyclerView.LayoutParams(cellW, cellH)
        holder.job?.cancel()
        when (item) {
            WpItem.Add -> v.bindAdd(v.context.getString(CoreR.string.wp_add_from_device))
            is WpItem.Item -> {
                val wp = item.wp
                v.bind(wp.id, wp.name(v.context), wp.badge(), item.favorite, item.applied, wp.type != WallpaperType.STATIC)
                val tw = (cellW * 0.95f).toInt().coerceAtLeast(64); val th = (cellH * 0.95f).toInt().coerceAtLeast(36)
                val cached = thumbs.peek(wp, tw, th)
                if (cached != null) v.setImage(cached) else holder.job = scope.launch {
                    val b = thumbs.load(wp, tw, th)
                    if (v.boundId == wp.id) v.setImage(b)
                }
            }
        }
        v.setOnClickListener { onClick(item, it) }
        v.setOnLongClickListener { onLongClick(item, it); true }
        v.setOnKeyListener { view, code, ev ->
            if (Keys.isMenu(code)) { if (ev.action == KeyEvent.ACTION_UP) onLongClick(item, view); true } else false
        }
        v.onFocusChangeListener = View.OnFocusChangeListener { _, has -> if (has) onFocus(item) }
    }

    override fun onViewRecycled(holder: VH) { holder.job?.cancel() }

    class VH(val view: WallpaperThumbView) : RecyclerView.ViewHolder(view) { var job: Job? = null }

    private object Diff : DiffUtil.ItemCallback<WpItem>() {
        override fun areItemsTheSame(a: WpItem, b: WpItem) = a.id == b.id
        override fun areContentsTheSame(a: WpItem, b: WpItem) = a == b
    }
}
