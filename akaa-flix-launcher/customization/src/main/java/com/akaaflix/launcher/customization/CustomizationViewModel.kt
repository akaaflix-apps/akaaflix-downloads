package com.akaaflix.launcher.customization

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.akaaflix.launcher.prefs.LauncherSettings
import com.akaaflix.launcher.prefs.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the unsaved customization draft; nothing is persisted until [save]. */
class CustomizationViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = SettingsRepository.get(app)
    private val _draft = MutableStateFlow(prefs.settings.value)
    val draft: StateFlow<LauncherSettings> = _draft.asStateFlow()

    val isDirty: Boolean
        get() { val cur = prefs.settings.value; return cur.withCustomizationFrom(_draft.value) != cur }

    fun update(transform: (LauncherSettings) -> LauncherSettings) { _draft.value = transform(_draft.value) }

    fun restoreDefaults() { _draft.value = _draft.value.withCustomizationDefaults() }

    fun discard() { _draft.value = prefs.settings.value }

    /** Persists the draft. Returns true when the activity must be recreated (font changes). */
    suspend fun save(): Boolean {
        val before = prefs.settings.value
        val d = _draft.value
        prefs.update { it.withCustomizationFrom(d) }
        _draft.value = prefs.settings.value.withCustomizationFrom(d)
        return before.fontFamily != d.fontFamily || before.fontScale != d.fontScale
    }
}
