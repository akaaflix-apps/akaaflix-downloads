package com.akaaflix.launcher.core.ui.rows

import android.widget.ImageView
import androidx.annotation.DrawableRes

/** Declarative rows rendered by [SettingRowsAdapter] for settings, sheets and menus. */
sealed class SettingRow {
    abstract val id: String
    /** Equality key for DiffUtil (lambdas are ignored). */
    abstract fun contentKey(): String

    data class Header(override val id: String, val title: CharSequence) : SettingRow() {
        override fun contentKey() = "h|$title"
    }

    data class Toggle(
        override val id: String, val title: CharSequence, val subtitle: CharSequence? = null,
        @DrawableRes val icon: Int = 0, val checked: Boolean, val enabled: Boolean = true,
        val iconLoader: ((ImageView) -> Unit)? = null, val onChange: (Boolean) -> Unit,
    ) : SettingRow() {
        override fun contentKey() = "t|$title|$subtitle|$icon|$checked|$enabled"
    }

    data class Choice(
        override val id: String, val title: CharSequence, val subtitle: CharSequence? = null,
        @DrawableRes val icon: Int = 0, val options: List<CharSequence>, val selected: Int,
        val enabled: Boolean = true, val onSelect: (Int) -> Unit,
    ) : SettingRow() {
        override fun contentKey() = "c|$title|$subtitle|$icon|${options.joinToString()}|$selected|$enabled"
    }

    data class Action(
        override val id: String, val title: CharSequence, val subtitle: CharSequence? = null,
        @DrawableRes val icon: Int = 0, val value: CharSequence? = null, val danger: Boolean = false,
        val showChevron: Boolean = true, val enabled: Boolean = true,
        val iconLoader: ((ImageView) -> Unit)? = null, val onLongClick: (() -> Unit)? = null, val onClick: () -> Unit,
    ) : SettingRow() {
        override fun contentKey() = "a|$title|$subtitle|$icon|$value|$danger|$enabled"
    }

    data class Stepper(
        override val id: String, val title: CharSequence, val subtitle: CharSequence? = null,
        @DrawableRes val icon: Int = 0, val value: Int, val min: Int, val max: Int,
        val format: (Int) -> String = { it.toString() }, val onChange: (Int) -> Unit,
    ) : SettingRow() {
        override fun contentKey() = "s|$title|$subtitle|$icon|$value|${format(value)}"
    }

    data class Colors(
        override val id: String, val title: CharSequence, @DrawableRes val icon: Int = 0,
        val colors: IntArray, val selected: Int, val onSelect: (Int) -> Unit,
    ) : SettingRow() {
        override fun contentKey() = "k|$title|${colors.joinToString()}|$selected"
        override fun equals(other: Any?) = other is Colors && other.contentKey() == contentKey()
        override fun hashCode() = contentKey().hashCode()
    }

    data class Info(override val id: String, val title: CharSequence, val value: CharSequence?, @DrawableRes val icon: Int = 0) : SettingRow() {
        override fun contentKey() = "i|$title|$value|$icon"
    }
}
