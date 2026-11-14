package com.akaaflix.launcher.home.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.akaaflix.launcher.apps.icons.IconCache
import com.akaaflix.launcher.apps.model.AppEntry
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.ui.GlassDrawable
import com.akaaflix.launcher.core.ui.LauncherPalette
import com.akaaflix.launcher.core.ui.SidebarDrawable
import com.akaaflix.launcher.core.ui.ThemeCatalog
import com.akaaflix.launcher.prefs.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Live miniature of the Home screen drawn from the *unsaved* customization draft: wallpaper,
 * theme colours, card style, icon shape, labels, rows, logo, clock, weather and action bar.
 * Rendered on a 960x540 virtual canvas so proportions match the real screen.
 */
class HomePreviewView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val icons = IconCache.get(context)
    private val pending = HashSet<String>()

    var settings: LauncherSettings = LauncherSettings()
        set(value) { field = value; palette = ThemeCatalog.palette(value.themeId, value.accentColor); typeface = loadTypeface(value.fontFamily); invalidate() }
    var wallpaper: Bitmap? = null
        set(value) { field = value; invalidate() }
    var apps: List<AppEntry> = emptyList()
        set(value) { field = value; invalidate() }
    var clock: String = "12:45"
    var amPm: String = "AM"

    private var palette: LauncherPalette = ThemeCatalog.palette(ThemeCatalog.DEFAULT_ID, 0)
    private var typeface: Typeface = Typeface.DEFAULT
    private val sidebar = SidebarDrawable(1f)
    private val card = GlassDrawable(14f, GlassDrawable.Variant.CARD, 5f, 1f)
    private val chip = GlassDrawable(20f, GlassDrawable.Variant.CHIP, 0f, 1f)
    private val circle = GlassDrawable(20f, GlassDrawable.Variant.CIRCLE, 0f, 1f)
    private val navSel = GlassDrawable(20f, GlassDrawable.Variant.NAV_ITEM, 2f, 1f)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val logo: Bitmap? by lazy {
        try { BitmapFactory.decodeResource(resources, R.drawable.akaa_logo, BitmapFactory.Options().apply { inSampleSize = 2 }) } catch (_: Exception) { null }
    }
    private val navIcons = intArrayOf(R.drawable.ic_home, R.drawable.ic_apps, R.drawable.ic_star, R.drawable.ic_sliders, R.drawable.ic_settings)
    private val barIcons = intArrayOf(R.drawable.ic_search, R.drawable.ic_apps, R.drawable.ic_info)
    private val src = Rect()
    private val dst = RectF()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    private fun loadTypeface(f: String): Typeface = try {
        when (f) {
            "tajawal" -> ResourcesCompat.getFont(context, R.font.tajawal)
            "cairo" -> ResourcesCompat.getFont(context, R.font.cairo)
            else -> null
        } ?: Typeface.DEFAULT
    } catch (_: Exception) { Typeface.DEFAULT }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var w = MeasureSpec.getSize(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        if (hMode != MeasureSpec.UNSPECIFIED) w = minOf(w, MeasureSpec.getSize(heightMeasureSpec) * 16 / 9)
        setMeasuredDimension(w, w * 9 / 16)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    override fun onDetachedFromWindow() { scope.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val s = settings
        val k = width / 960f
        canvas.save()
        canvas.scale(k, k)
        listOf(card, chip, circle, navSel).forEach { it.paletteOverride = palette; it.cardStyle = s.cardStyle }
        sidebar.paletteOverride = palette
        // Wallpaper (centre crop)
        wallpaper?.let { bmp ->
            val scale = maxOf(960f / bmp.width, 540f / bmp.height)
            val sw = (960f / scale).toInt(); val sh = (540f / scale).toInt()
            src.set((bmp.width - sw) / 2, (bmp.height - sh) / 2, (bmp.width + sw) / 2, (bmp.height + sh) / 2)
            dst.set(0f, 0f, 960f, 540f)
            canvas.drawBitmap(bmp, src, dst, bmpPaint)
        } ?: canvas.drawColor(0xFF0B0418.toInt())
        // Sidebar + items
        sidebar.setBounds(0, 58, 96, 462); sidebar.draw(canvas)
        val step = (462 - 58 - 44) / navIcons.size.toFloat()
        for (i in navIcons.indices) {
            val cy = 80 + step * i + step / 2f
            if (i == 0) { navSel.setBounds(-20, (cy - 26).toInt(), 88, (cy + 26).toInt()); navSel.state = intArrayOf(android.R.attr.state_activated); navSel.draw(canvas) }
            drawIcon(canvas, navIcons[i], 48f - 12f, cy - 18f, 24f, if (i == 0) palette.focusBorder else Color.WHITE)
        }
        // Logo, clock, date pill
        if (s.showHomeLogo) logo?.let { l ->
            val w = 282f; val h = w * l.height / l.width
            dst.set(480 - w / 2, 44f, 480 + w / 2, 44f + h); canvas.drawBitmap(l, null, dst, bmpPaint)
        }
        if (s.showClockDate) {
            text.typeface = typeface; text.textAlign = Paint.Align.RIGHT; text.textSize = 22f
            canvas.drawText("$clock $amPm", 818f, 36f, text)
            chip.state = intArrayOf(); chip.setBounds(664, 50, 948, 92); chip.draw(canvas)
            text.textSize = 11f; text.textAlign = Paint.Align.CENTER
            canvas.drawText("•  ••••  •••••  ••••  |  ••  ••••  ••••  •", 806f, 76f, text)
        }
        drawIcon(canvas, R.drawable.ic_wifi, 866f, 14f, 22f, Color.WHITE)
        circle.state = intArrayOf(); circle.setBounds(902, 8, 940, 46); circle.draw(canvas)
        drawIcon(canvas, R.drawable.ic_settings, 910f, 16f, 22f, Color.WHITE)
        // Brand pill
        chip.setBounds(18, 12, 150, 60); chip.draw(canvas)
        drawIcon(canvas, R.drawable.ic_avatar, 26f, 18f, 36f, 0xFFE9D5FF.toInt())
        text.textAlign = Paint.Align.LEFT; text.textSize = 13f; text.isFakeBoldText = true
        canvas.drawText(s.profileName.ifBlank { "Akaa Flix" }, 70f, 33f, text)
        text.isFakeBoldText = false; text.textSize = 11f
        canvas.drawText("Launcher", 70f, 50f, text)
        // Apps grid
        if (s.showFavoritesBar) {
            val cols = 6; val rows = s.homeRows.coerceIn(1, 3)
            val left = 112f; val width = 838f
            val cw = width / cols; val ch = minOf(cw * 0.74f, (540f - 134f - 92f) / rows)
            val iconPx = 72
            for (r in 0 until rows) for (c in 0 until cols) {
                val idx = r * cols + c
                val l = left + c * cw; val t = 134f + r * ch
                card.state = if (idx == 0) intArrayOf(android.R.attr.state_focused) else intArrayOf()
                card.glowTint = 0
                val isAdd = idx == minOf(apps.size, rows * cols - 1)
                val entry = if (!isAdd) apps.getOrNull(idx) else null
                if (!isAdd && entry == null) continue
                if (entry != null) card.glowTint = icons.dominantColor(entry)
                card.setBounds(l.toInt(), t.toInt(), (l + cw).toInt(), (t + ch).toInt())
                canvas.save()
                if (idx == 0) { val z = 1.06f; canvas.scale(z, z, l + cw / 2, t + ch / 2) }
                card.draw(canvas)
                val iconSize = ch * (if (s.showAppLabels) 0.42f else 0.55f) * (if (s.iconSize == "large") 1.15f else 1f)
                val cx = l + cw / 2
                val iy = t + ch / 2 - iconSize / 2 - (if (s.showAppLabels) 8f else 0f)
                if (isAdd) drawIcon(canvas, R.drawable.ic_plus, cx - iconSize / 2, iy, iconSize, Color.WHITE)
                else if (entry != null) {
                    val bmp = icons.peekIcon(entry, iconPx, "original")
                    if (bmp != null) { dst.set(cx - iconSize / 2, iy, cx + iconSize / 2, iy + iconSize); canvas.drawBitmap(bmp, null, dst, bmpPaint) }
                    else request(entry, iconPx, "original")
                }
                if (s.showAppLabels) {
                    text.textAlign = Paint.Align.CENTER; text.textSize = 12f; text.typeface = typeface
                    val label = if (isAdd) context.getString(R.string.home_add_app) else entry?.label.orEmpty()
                    canvas.drawText(ellipsize(label, cw - 20f), cx, iy + iconSize + 16f, text)
                }
                canvas.restore()
            }
        }
        // Weather and action bar
        if (s.weatherEnabled) {
            chip.state = intArrayOf(); chip.setBounds(16, 470, 176, 526); chip.draw(canvas)
            // Schematic only (no invented temperature in the miniature).
            drawIcon(canvas, R.drawable.ic_weather_partly_night, 28f, 478f, 40f, 0)
            text.textAlign = Paint.Align.LEFT; text.textSize = 19f; canvas.drawText("--°", 78f, 504f, text)
        }
        if (s.showBottomBar) {
            val n = barIcons.size; val gap = 62f; val start = 480f - (n - 1) * gap / 2
            for (i in 0 until n) {
                val cx = start + i * gap
                circle.state = intArrayOf(); circle.setBounds((cx - 21).toInt(), 466, (cx + 21).toInt(), 508); circle.draw(canvas)
                drawIcon(canvas, barIcons[i], cx - 11f, 476f, 22f, Color.WHITE)
            }
        }
        canvas.restore()
    }

    private fun ellipsize(s: String, maxW: Float): String {
        if (text.measureText(s) <= maxW) return s
        var e = s
        while (e.length > 1 && text.measureText("$e…") > maxW) e = e.dropLast(1)
        return "$e…"
    }

    private fun drawIcon(canvas: Canvas, res: Int, x: Float, y: Float, size: Float, tint: Int) {
        val d = ResourcesCompat.getDrawable(resources, res, null)?.mutate() ?: return
        if (tint != 0) d.setTint(tint)
        d.setBounds(x.toInt(), y.toInt(), (x + size).toInt(), (y + size).toInt())
        d.draw(canvas)
    }

    private fun request(e: AppEntry, size: Int, shape: String) {
        val key = "${e.key}|$size|$shape"
        if (!pending.add(key)) return
        scope.launch { icons.icon(e, size, shape); pending.remove(key); invalidate() }
    }
}
