package com.akaaflix.launcher.wallpaper.ui

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.akaaflix.launcher.core.R as CoreR
import com.akaaflix.launcher.core.ui.FocusEffects
import com.akaaflix.launcher.core.ui.GlassDrawable
import com.akaaflix.launcher.core.ui.dialog.BoundedFrame
import com.akaaflix.launcher.core.ui.dialog.LauncherDialog
import com.akaaflix.launcher.core.ui.dpi
import android.widget.ImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Built-in media picker used only when the device has no system file picker (common on Android
 * TV boxes). Requires the user to grant photo/video read access first.
 */
object MediaPickerDialog {
    data class Media(val uri: Uri, val name: String)

    fun show(context: Context, scope: LifecycleCoroutineScope, video: Boolean, onPick: (Media) -> Unit) {
        scope.launch {
            val items = withContext(Dispatchers.IO) { query(context, video) }
            if (items.isEmpty()) {
                android.widget.Toast.makeText(context, CoreR.string.wp_no_media, android.widget.Toast.LENGTH_LONG).show(); return@launch
            }
            val d = LauncherDialog(context).title(CoreR.string.wp_pick_title).icon(CoreR.drawable.ic_image).width(640)
            val rv = RecyclerView(context).apply {
                layoutManager = GridLayoutManager(context, 4)
                clipChildren = false; clipToPadding = false
                setPadding(context.dpi(4f), context.dpi(4f), context.dpi(4f), context.dpi(4f))
            }
            val cell = context.dpi(140f)
            rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = items.size
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = object : RecyclerView.ViewHolder(ImageView(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cell * 9 / 16)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    isFocusable = true; isClickable = true
                    val p = context.dpi(5f); setPadding(p, p, p, p)
                    background = GlassDrawable(context.dpi(10f).toFloat(), GlassDrawable.Variant.CARD, context.dpi(4f).toFloat(), resources.displayMetrics.density)
                    FocusEffects.attach(this, 1.06f)
                }) {}
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val iv = holder.itemView as ImageView
                    val m = items[position]
                    iv.contentDescription = m.name
                    iv.setImageDrawable(null); iv.tag = m.uri
                    scope.launch {
                        val b = withContext(Dispatchers.IO) { thumb(context, m.uri, video) }
                        if (iv.tag == m.uri) iv.setImageBitmap(b)
                    }
                    iv.setOnClickListener { d.dialog.dismiss(); onPick(m) }
                }
            }
            d.content(BoundedFrame(context, (context.resources.displayMetrics.heightPixels * 0.66f).toInt()).apply {
                addView(rv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }).button(CoreR.string.cancel)
            d.show()
            rv.post { rv.getChildAt(0)?.requestFocus() }
        }
    }

    private fun query(context: Context, video: Boolean): List<Media> {
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val out = ArrayList<Media>()
        try {
            context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                null, null, MediaStore.MediaColumns.DATE_ADDED + " DESC")?.use { c ->
                while (c.moveToNext() && out.size < 240) out += Media(ContentUris.withAppendedId(collection, c.getLong(0)), c.getString(1) ?: "")
            }
        } catch (_: Exception) {}
        return out
    }

    @Suppress("DEPRECATION")
    private fun thumb(context: Context, uri: Uri, video: Boolean): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(uri, Size(320, 180), null)
        else {
            val id = ContentUris.parseId(uri)
            if (video) MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null)
            else MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null)
        }
    } catch (_: Exception) { null }

    @Suppress("unused") private fun View.unused() = Unit
}
