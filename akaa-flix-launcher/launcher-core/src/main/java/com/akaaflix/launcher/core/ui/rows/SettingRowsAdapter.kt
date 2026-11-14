package com.akaaflix.launcher.core.ui.rows

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.databinding.ItemSettingHeaderBinding
import com.akaaflix.launcher.core.databinding.ItemSettingRowBinding
import com.akaaflix.launcher.core.ui.GlassDrawable
import com.akaaflix.launcher.core.ui.LauncherUi
import com.akaaflix.launcher.core.ui.clearTint
import com.akaaflix.launcher.core.ui.dialog.Dialogs
import com.akaaflix.launcher.core.ui.dpi
import com.akaaflix.launcher.core.ui.glass
import com.akaaflix.launcher.core.ui.tint

class SettingRowsAdapter : ListAdapter<SettingRow, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int) = if (getItem(position) is SettingRow.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == 0) HeaderVH(ItemSettingHeaderBinding.inflate(inf, parent, false))
        else RowVH(ItemSettingRowBinding.inflate(inf, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = getItem(position)
        when (holder) {
            is HeaderVH -> holder.b.headerText.apply {
                text = (row as SettingRow.Header).title
                setTextColor(LauncherUi.palette.accentLight)
            }
            is RowVH -> holder.bind(row)
        }
    }

    class HeaderVH(val b: ItemSettingHeaderBinding) : RecyclerView.ViewHolder(b.root)

    class RowVH(val b: ItemSettingRowBinding) : RecyclerView.ViewHolder(b.root) {
        private val bg = b.rowRoot.glass(GlassDrawable.Variant.PANEL, 14f, 3f)

        init {
            b.rowMinus.glass(GlassDrawable.Variant.CHIP, 16f, 2f)
            b.rowPlus.glass(GlassDrawable.Variant.CHIP, 16f, 2f)
        }

        fun bind(row: SettingRow) {
            val ctx = b.root.context
            b.rowSwitch.visibility = View.GONE
            b.rowValue.visibility = View.GONE
            b.rowStepper.visibility = View.GONE
            b.rowSwatches.visibility = View.GONE
            b.rowChevron.visibility = View.GONE
            b.rowSubtitle.visibility = View.GONE
            b.rowRoot.isFocusable = true
            b.rowRoot.isClickable = true
            b.rowRoot.alpha = 1f
            b.rowRoot.setOnClickListener(null)
            b.rowRoot.setOnLongClickListener(null)
            b.rowRoot.setOnKeyListener(null)
            b.rowTitle.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            bg.forceSelected = false

            fun icon(res: Int, loader: ((android.widget.ImageView) -> Unit)? = null) {
                when {
                    loader != null -> { b.rowIcon.visibility = View.VISIBLE; b.rowIcon.clearTint(); b.rowIcon.setImageDrawable(null); loader(b.rowIcon) }
                    res != 0 -> { b.rowIcon.visibility = View.VISIBLE; b.rowIcon.setImageResource(res); b.rowIcon.tint(LauncherUi.palette.accentLight) }
                    else -> b.rowIcon.visibility = View.GONE
                }
            }
            fun subtitle(s: CharSequence?) {
                if (!s.isNullOrEmpty()) { b.rowSubtitle.visibility = View.VISIBLE; b.rowSubtitle.text = s }
            }

            when (row) {
                is SettingRow.Toggle -> {
                    b.rowTitle.text = row.title; subtitle(row.subtitle); icon(row.icon, row.iconLoader)
                    b.rowSwitch.visibility = View.VISIBLE
                    b.rowSwitch.onCheckedChange = null
                    b.rowSwitch.setChecked(row.checked, false)
                    b.rowRoot.alpha = if (row.enabled) 1f else 0.45f
                    b.rowRoot.setOnClickListener {
                        if (!row.enabled) return@setOnClickListener
                        val v = !b.rowSwitch.isChecked
                        b.rowSwitch.setChecked(v, true)
                        row.onChange(v)
                    }
                }
                is SettingRow.Choice -> {
                    b.rowTitle.text = row.title; subtitle(row.subtitle); icon(row.icon)
                    b.rowValue.visibility = View.VISIBLE
                    b.rowValue.text = row.options.getOrNull(row.selected) ?: ""
                    b.rowChevron.visibility = View.VISIBLE
                    b.rowChevron.setImageResource(R.drawable.ic_chevron_down)
                    b.rowRoot.alpha = if (row.enabled) 1f else 0.45f
                    b.rowRoot.setOnClickListener {
                        if (!row.enabled) return@setOnClickListener
                        Dialogs.choose(ctx, row.title, row.options, row.selected) { row.onSelect(it) }
                    }
                }
                is SettingRow.Action -> {
                    b.rowTitle.text = row.title; subtitle(row.subtitle); icon(row.icon, row.iconLoader)
                    if (row.value != null) { b.rowValue.visibility = View.VISIBLE; b.rowValue.text = row.value }
                    if (row.showChevron) { b.rowChevron.visibility = View.VISIBLE; b.rowChevron.setImageResource(R.drawable.ic_chevron_right) }
                    if (row.danger) b.rowTitle.setTextColor(ContextCompat.getColor(ctx, R.color.danger_red))
                    b.rowRoot.alpha = if (row.enabled) 1f else 0.45f
                    b.rowRoot.setOnClickListener { if (row.enabled) row.onClick() }
                    row.onLongClick?.let { lc ->
                        b.rowRoot.setOnLongClickListener { lc(); true }
                        b.rowRoot.setOnKeyListener { _, code, ev ->
                            if (code == android.view.KeyEvent.KEYCODE_MENU) { if (ev.action == android.view.KeyEvent.ACTION_UP) lc(); true } else false
                        }
                    }
                }
                is SettingRow.Stepper -> {
                    b.rowTitle.text = row.title; subtitle(row.subtitle); icon(row.icon)
                    b.rowRoot.isFocusable = false; b.rowRoot.isClickable = false
                    b.rowStepper.visibility = View.VISIBLE
                    b.rowStepValue.text = row.format(row.value)
                    b.rowMinus.alpha = if (row.value > row.min) 1f else 0.35f
                    b.rowPlus.alpha = if (row.value < row.max) 1f else 0.35f
                    b.rowMinus.setOnClickListener { if (row.value > row.min) row.onChange(row.value - 1) }
                    b.rowPlus.setOnClickListener { if (row.value < row.max) row.onChange(row.value + 1) }
                }
                is SettingRow.Colors -> {
                    b.rowTitle.text = row.title; icon(row.icon)
                    b.rowRoot.isFocusable = false; b.rowRoot.isClickable = false
                    b.rowSwatches.visibility = View.VISIBLE
                    b.rowSwatches.removeAllViews()
                    row.colors.forEachIndexed { i, c ->
                        val sw = SwatchView(ctx, if (c == 0) LauncherUi.palette.accent else c, auto = c == 0)
                        sw.isActivated = i == row.selected
                        sw.setOnClickListener { row.onSelect(i) }
                        b.rowSwatches.addView(sw, LinearLayout.LayoutParams(ctx.dpi(34f), ctx.dpi(34f)).apply { marginStart = ctx.dpi(4f) })
                    }
                }
                is SettingRow.Info -> {
                    b.rowTitle.text = row.title; icon(row.icon)
                    b.rowValue.visibility = if (row.value != null) View.VISIBLE else View.GONE
                    b.rowValue.text = row.value
                    b.rowRoot.isClickable = false
                }
                is SettingRow.Header -> Unit
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<SettingRow>() {
        override fun areItemsTheSame(oldItem: SettingRow, newItem: SettingRow) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SettingRow, newItem: SettingRow) = oldItem.contentKey() == newItem.contentKey()
    }
}

/** Round colour swatch with focus ring; [auto] draws a split swatch meaning "theme colour". */
class SwatchView(context: android.content.Context, private val color: Int, private val auto: Boolean) : View(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val ring = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE }
    init { isFocusable = true; isClickable = true; contentDescription = if (auto) context.getString(R.string.accent_theme) else String.format("#%06X", 0xFFFFFF and color) }
    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect); invalidate()
    }
    override fun setActivated(activated: Boolean) { super.setActivated(activated); invalidate() }
    override fun onDraw(canvas: android.graphics.Canvas) {
        val cx = width / 2f; val cy = height / 2f; val d = resources.displayMetrics.density
        val r = minOf(cx, cy) - 5 * d
        paint.color = color
        canvas.drawCircle(cx, cy, r, paint)
        if (auto) {
            paint.color = Color.argb(150, 0, 0, 0)
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, -45f, 180f, true, paint)
        }
        if (isActivated || isFocused) {
            ring.strokeWidth = (if (isFocused) 2.6f else 1.8f) * d
            ring.color = if (isFocused) LauncherUi.palette.focusBorder else Color.WHITE
            canvas.drawCircle(cx, cy, r + 3 * d, ring)
        }
    }
}
