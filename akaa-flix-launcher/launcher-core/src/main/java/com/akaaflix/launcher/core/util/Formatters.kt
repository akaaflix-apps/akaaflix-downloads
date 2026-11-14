package com.akaaflix.launcher.core.util

import java.util.Locale

/** Number helpers. Digits are produced in Latin form and converted on request (design uses 123). */
object Numerals {
    private const val EASTERN = "٠١٢٣٤٥٦٧٨٩"
    /** Set from settings ("numerals"); used by [str]. */
    @Volatile var preferEastern: Boolean = false

    fun localize(text: String, eastern: Boolean): String {
        if (!eastern) return text
        val sb = StringBuilder(text.length)
        for (c in text) sb.append(if (c in '0'..'9') EASTERN[c - '0'] else c)
        return sb.toString()
    }
}

object Bytes {
    /** Storage style (powers of 1000): "568 GB", "1 TB", "31.5 GB". */
    fun storage(bytes: Long): String = format(bytes.toDouble(), 1000.0)

    /** Memory style (powers of 1024): "6.2 GB", "512 MB". */
    fun memory(bytes: Long): String = format(bytes.toDouble(), 1024.0)

    private fun format(value: Double, unit: Double): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
        var v = value
        var i = 0
        while (v >= unit && i < units.size - 1) { v /= unit; i++ }
        val pattern = when {
            i == 0 -> "%.0f"
            v >= 100 || (v % 1.0) < 0.05 -> "%.0f"
            else -> "%.1f"
        }
        return String.format(Locale.US, pattern, v) + " " + units[i]
    }
}

/**
 * Formats a localized string with Western digits (as in the approved design) unless the user
 * selected Eastern Arabic numerals. Avoids Android's locale digit substitution in "ar".
 */
fun android.content.Context.str(@androidx.annotation.StringRes id: Int, vararg args: Any?): String =
    Numerals.localize(String.format(java.util.Locale.US, getString(id), *args), Numerals.preferEastern)

fun androidx.fragment.app.Fragment.str(@androidx.annotation.StringRes id: Int, vararg args: Any?): String = requireContext().str(id, *args)
