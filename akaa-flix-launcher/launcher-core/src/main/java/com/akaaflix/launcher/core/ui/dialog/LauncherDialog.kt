package com.akaaflix.launcher.core.ui.dialog

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.databinding.DialogBaseBinding
import com.akaaflix.launcher.core.ui.FocusEffects
import com.akaaflix.launcher.core.ui.GlassDrawable
import com.akaaflix.launcher.core.ui.LauncherUi
import com.akaaflix.launcher.core.ui.dpi
import com.akaaflix.launcher.core.ui.glass
import com.akaaflix.launcher.core.ui.rows.SettingRow
import com.akaaflix.launcher.core.ui.rows.SettingRowsAdapter
import com.akaaflix.launcher.core.ui.tint

/** Glass-styled dialog matching the launcher design. Fully usable with a D-pad. */
class LauncherDialog(val context: Context) {
    val dialog = AppCompatDialog(context, R.style.Theme_Akaa_Dialog)
    val binding: DialogBaseBinding = DialogBaseBinding.inflate(LayoutInflater.from(context))
    private var firstButton: View? = null
    private var preferredFocus: View? = null
    private var widthDp = 520

    init {
        binding.dialogRoot.glass(GlassDrawable.Variant.PANEL, 20f)
        binding.dialogRoot.background.alpha = 255
        dialog.setContentView(binding.root)
        dialog.setCanceledOnTouchOutside(true)
    }

    fun title(text: CharSequence?) = apply { binding.dialogTitle.text = text; binding.dialogHeader.visibility = if (text == null) View.GONE else View.VISIBLE }
    fun title(res: Int) = title(context.getString(res))

    fun icon(@DrawableRes res: Int, tintAccent: Boolean = true) = apply {
        binding.dialogIcon.visibility = View.VISIBLE
        binding.dialogIcon.setImageResource(res)
        if (tintAccent) binding.dialogIcon.tint(LauncherUi.palette.accentLight)
    }

    fun iconView(): ImageView = binding.dialogIcon.also { it.visibility = View.VISIBLE }

    fun message(text: CharSequence?) = apply {
        binding.dialogMessage.text = text
        binding.dialogMessage.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
    fun message(res: Int) = message(context.getString(res))

    fun content(view: View) = apply { binding.dialogContent.removeAllViews(); binding.dialogContent.addView(view) }

    fun width(dp: Int) = apply { widthDp = dp }

    fun focusOn(view: View) = apply { preferredFocus = view }

    fun button(text: CharSequence, primary: Boolean = false, dismiss: Boolean = true, action: (() -> Unit)? = null) = apply {
        val b = makeButton(context, text, primary)
        b.setOnClickListener {
            if (dismiss) dialog.dismiss()
            action?.invoke()
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, context.dpi(40f))
        lp.marginStart = context.dpi(8f)
        binding.dialogButtons.addView(b, lp)
        if (firstButton == null || primary) firstButton = b
    }
    fun button(res: Int, primary: Boolean = false, dismiss: Boolean = true, action: (() -> Unit)? = null) =
        button(context.getString(res), primary, dismiss, action)

    fun onDismiss(block: () -> Unit) = apply { dialog.setOnDismissListener { block() } }

    fun show(): AppCompatDialog {
        if (binding.dialogButtons.childCount == 0) binding.dialogButtons.visibility = View.GONE
        dialog.show()
        dialog.window?.let { w ->
            val dm = context.resources.displayMetrics
            val max = (dm.widthPixels * 0.92f).toInt()
            w.setLayout(minOf(context.dpi(widthDp.toFloat()), max), WindowManager.LayoutParams.WRAP_CONTENT)
            w.setGravity(Gravity.CENTER)
        }
        (preferredFocus ?: firstButton)?.requestFocus()
        return dialog
    }

    companion object {
        fun makeButton(context: Context, text: CharSequence, primary: Boolean): TextView = androidx.appcompat.widget.AppCompatTextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            minWidth = context.dpi(110f)
            setPadding(context.dpi(22f), 0, context.dpi(22f), 0)
            setTextAppearance(R.style.TextAppearance_Akaa_LabelBold)
            isFocusable = true
            isClickable = true
            glass(if (primary) GlassDrawable.Variant.PRIMARY else GlassDrawable.Variant.CHIP, 20f, 3f)
            FocusEffects.attach(this, 1.05f)
        }
    }
}

object Dialogs {
    fun info(context: Context, title: CharSequence, message: CharSequence): AppCompatDialog =
        LauncherDialog(context).title(title).message(message).button(R.string.ok, primary = true).show()

    fun confirm(context: Context, title: CharSequence, message: CharSequence?, positive: CharSequence,
                negative: CharSequence = context.getString(R.string.cancel), onConfirm: () -> Unit): AppCompatDialog =
        LauncherDialog(context).title(title).message(message)
            .button(negative)
            .button(positive, primary = true, action = onConfirm)
            .show()

    /** Single choice list. Focus starts on the current value. */
    fun choose(context: Context, title: CharSequence, options: List<CharSequence>, selected: Int, onSelect: (Int) -> Unit): AppCompatDialog {
        val d = LauncherDialog(context).title(title)
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            setPadding(context.dpi(4f), context.dpi(4f), context.dpi(4f), context.dpi(4f))
        }
        var focusTarget: View? = null
        options.forEachIndexed { i, label ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true; isClickable = true
                setPadding(context.dpi(16f), 0, context.dpi(16f), 0)
                glass(GlassDrawable.Variant.PANEL, 12f, 3f)
                isActivated = i == selected
            }
            val tv = androidx.appcompat.widget.AppCompatTextView(context).apply {
                text = label
                setTextAppearance(R.style.TextAppearance_Akaa)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
            row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (i == selected) row.addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_check); tint(LauncherUi.palette.accentLight)
            }, LinearLayout.LayoutParams(context.dpi(20f), context.dpi(20f)))
            row.setOnClickListener { d.dialog.dismiss(); onSelect(i) }
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dpi(46f)).apply { topMargin = context.dpi(4f) })
            if (i == selected || focusTarget == null) focusTarget = row
        }
        val scroll = ScrollView(context).apply { isFillViewport = true; clipChildren = false; addView(list) }
        val maxH = (context.resources.displayMetrics.heightPixels * 0.62f).toInt()
        scroll.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        d.content(BoundedFrame(context, maxH).apply { addView(scroll) })
        focusTarget?.let { d.focusOn(it) }
        return d.width(440).show()
    }

    fun input(context: Context, title: CharSequence, hint: CharSequence, initial: String = "",
              inputType: Int = InputType.TYPE_CLASS_TEXT, onDone: (String) -> Unit): AppCompatDialog {
        val d = LauncherDialog(context).title(title)
        val edit = androidx.appcompat.widget.AppCompatEditText(context).apply {
            setText(initial)
            setSelection(initial.length)
            this.hint = hint
            this.inputType = inputType
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
            setPadding(context.dpi(16f), context.dpi(10f), context.dpi(16f), context.dpi(10f))
            glass(GlassDrawable.Variant.FIELD, 12f, 2f)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        edit.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { d.dialog.dismiss(); onDone(edit.text.toString().trim()); true } else false
        }
        d.content(edit).focusOn(edit)
            .button(R.string.cancel)
            .button(R.string.ok, primary = true) { onDone(edit.text.toString().trim()) }
        return d.show()
    }

    /** A dialog showing a list of setting rows (used for context menus and option sheets). */
    fun rows(context: Context, title: CharSequence, rows: List<SettingRow>, @DrawableRes icon: Int = 0,
             widthDp: Int = 520, setup: ((LauncherDialog, SettingRowsAdapter) -> Unit)? = null): LauncherDialog {
        val d = LauncherDialog(context).title(title)
        if (icon != 0) d.icon(icon)
        val adapter = SettingRowsAdapter()
        val rv = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
            clipChildren = false; clipToPadding = false
            setPadding(context.dpi(4f), context.dpi(2f), context.dpi(4f), context.dpi(2f))
            itemAnimator = null
        }
        adapter.submitList(rows)
        val maxH = (context.resources.displayMetrics.heightPixels * 0.70f).toInt()
        d.content(BoundedFrame(context, maxH).apply { addView(rv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) })
        d.width(widthDp)
        setup?.invoke(d, adapter)
        d.show()
        rv.post { rv.getChildAt(0)?.let { first -> (first.findFocusableChild() ?: first).requestFocus() } }
        return d
    }

    private fun View.findFocusableChild(): View? {
        if (isFocusable) return this
        if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findFocusableChild()?.let { return it }
        return null
    }
}

/** FrameLayout that never grows beyond [maxHeightPx] (keeps long lists inside the screen). */
class BoundedFrame(context: Context, private val maxHeightPx: Int) : android.widget.FrameLayout(context) {
    init { clipChildren = false; clipToPadding = false }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST))
    }
}
