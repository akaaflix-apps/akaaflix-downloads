package com.akaaflix.launcher.home.shell

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.nav.PageHeader
import com.akaaflix.launcher.core.ui.FocusEffects
import com.akaaflix.launcher.core.ui.GlassDrawable
import com.akaaflix.launcher.core.ui.LauncherUi
import com.akaaflix.launcher.core.ui.glass
import com.akaaflix.launcher.core.ui.tint
import com.akaaflix.launcher.device.clock.DateTexts
import com.akaaflix.launcher.device.status.BluetoothState
import com.akaaflix.launcher.device.status.NetworkState
import com.akaaflix.launcher.home.databinding.ViewTopBarBinding

/** Top bar of every page: brand pill, page title / logo, clock, status icons and date pill. */
class TopBarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    val b: ViewTopBarBinding = ViewTopBarBinding.inflate(LayoutInflater.from(context), this)
    private var arabicFirst = true

    var onBrandClick: (() -> Unit)? = null
    var onWifiClick: (() -> Unit)? = null
    var onBluetoothClick: (() -> Unit)? = null
    var onSettingsClick: (() -> Unit)? = null

    init {
        clipChildren = false; clipToPadding = false
        layoutDirection = LAYOUT_DIRECTION_LTR
        b.brandPill.glass(GlassDrawable.Variant.CHIP, 24f, 2f)
        b.brandIcon.glass(GlassDrawable.Variant.CIRCLE, 20f)
        b.datePill.glass(GlassDrawable.Variant.CHIP, 22f)
        b.gearButton.glass(GlassDrawable.Variant.CIRCLE, 19f, 2f)
        listOf(b.wifiIcon, b.btIcon).forEach { it.glass(GlassDrawable.Variant.CIRCLE, 18f, 2f).cardStyle = "minimal" }
        listOf(b.brandPill, b.wifiIcon, b.btIcon, b.gearButton).forEach { FocusEffects.attach(it, 1.08f) }
        b.brandPill.setOnClickListener { onBrandClick?.invoke() }
        b.wifiIcon.setOnClickListener { onWifiClick?.invoke() }
        b.btIcon.setOnClickListener { onBluetoothClick?.invoke() }
        b.gearButton.setOnClickListener { onSettingsClick?.invoke() }
        refreshPalette()
    }

    fun refreshPalette() {
        val accent = LauncherUi.palette.accentLight
        b.titleIcon.tint(accent)
        b.dateIconA.tint(0xFFFFFFFF.toInt()); b.dateIconB.tint(0xFFFFFFFF.toInt())
        listOf(b.brandPill, b.brandIcon, b.datePill, b.gearButton, b.wifiIcon, b.btIcon).forEach { it.background?.invalidateSelf() }
    }

    fun setHeader(h: PageHeader) {
        b.homeLogo.visibility = if (h.showLogo) View.VISIBLE else View.GONE
        b.headerCenter.visibility = if (h.showLogo || h.title == null) View.GONE else View.VISIBLE
        b.titleText.text = h.title
        b.subtitleText.text = h.subtitle
        b.subtitleText.visibility = if (h.subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
        if (h.icon != 0) { b.titleIcon.visibility = View.VISIBLE; b.titleIcon.setImageResource(h.icon); b.titleIcon.tint(LauncherUi.palette.accentLight) }
        else b.titleIcon.visibility = View.GONE
    }

    fun setLogoVisible(visible: Boolean) { if (b.homeLogo.visibility != View.GONE || visible) b.homeLogo.visibility = if (visible) View.VISIBLE else View.GONE }

    /** Avatar on home, logo mark elsewhere (as in the reference). */
    fun setBrand(avatarRes: Int, isAvatarImage: Boolean, name: String) {
        b.brandIcon.setImageResource(avatarRes)
        val dd = resources.displayMetrics.density
        val pad = if (isAvatarImage) 0 else ((if (avatarRes == R.drawable.ic_avatar) 6 else 7) * dd).toInt()
        b.brandIcon.setPadding(pad, pad, pad, pad)
        b.brandIcon.clipToOutline = isAvatarImage
        b.brandIcon.outlineProvider = if (isAvatarImage) object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) = outline.setOval(0, 0, view.width, view.height)
        } else android.view.ViewOutlineProvider.BACKGROUND
        b.brandLine1.text = name.ifBlank { context.getString(R.string.brand_line1) }
    }

    fun setClockVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        b.clockTime.visibility = v; b.clockAmPm.visibility = v; b.datePill.visibility = v
    }

    fun setStatusVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        b.wifiIcon.visibility = v; b.btIcon.visibility = v
    }

    /** [arabicPrimary]: Arabic UI shows the Arabic line first and the Hijri block first (RTL order). */
    fun bindDate(t: DateTexts, arabicPrimary: Boolean, bilingual: Boolean, showHijri: Boolean) {
        b.clockTime.text = t.time
        b.clockAmPm.text = t.amPm
        b.clockAmPm.visibility = if (t.amPm.isEmpty() || b.clockTime.visibility != View.VISIBLE) View.GONE else View.VISIBLE
        val gPrimary = if (arabicPrimary) t.gregorianAr else t.gregorianEn
        val gSecondary = if (arabicPrimary) t.gregorianEn else t.gregorianAr
        val hPrimary = if (arabicPrimary) t.hijriAr else t.hijriEn
        val hSecondary = if (arabicPrimary) t.hijriEn else t.hijriAr
        // Layout is LTR: block A is on the left. Arabic: Gregorian left, Hijri right (as in the design).
        b.dateA1.text = gPrimary; b.dateA2.text = gSecondary
        b.dateB1.text = hPrimary; b.dateB2.text = hSecondary
        b.dateIconA.setImageResource(R.drawable.ic_calendar)
        b.dateIconB.setImageResource(R.drawable.ic_moon)
        b.dateA2.visibility = if (bilingual) View.VISIBLE else View.GONE
        b.dateB2.visibility = if (bilingual) View.VISIBLE else View.GONE
        b.dateBlockB.visibility = if (showHijri) View.VISIBLE else View.GONE
        b.dateDivider.visibility = if (showHijri) View.VISIBLE else View.GONE
        if (arabicFirst != arabicPrimary) arabicFirst = arabicPrimary
    }

    fun bindNetwork(n: NetworkState) {
        val icon = when {
            !n.connected -> R.drawable.ic_wifi_off
            n.type == NetworkState.Type.ETHERNET -> R.drawable.ic_ethernet
            n.type == NetworkState.Type.WIFI -> when (n.wifiLevel) { 3 -> R.drawable.ic_wifi; 2 -> R.drawable.ic_wifi_2; else -> R.drawable.ic_wifi_1 }
            else -> R.drawable.ic_wifi
        }
        b.wifiIcon.setImageResource(icon)
    }

    fun bindBluetooth(s: BluetoothState) {
        b.btIcon.visibility = if (!s.supported || b.wifiIcon.visibility != View.VISIBLE) View.GONE else View.VISIBLE
        b.btIcon.setImageResource(if (s.enabled) R.drawable.ic_bluetooth else R.drawable.ic_bluetooth_off)
    }
}
