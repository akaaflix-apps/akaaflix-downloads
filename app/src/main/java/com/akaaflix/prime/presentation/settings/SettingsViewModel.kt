package com.akaaflix.prime.presentation.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.data.SessionManager
import com.akaaflix.prime.data.backup.BackupManager
import com.akaaflix.prime.data.backup.RestoreReport
import com.akaaflix.prime.data.repository.NotificationRepository
import com.akaaflix.prime.data.repository.SearchHistoryRepository
import com.akaaflix.prime.data.settings.AppSettings
import com.akaaflix.prime.data.settings.SettingsRepository
import com.akaaflix.prime.database.AppDatabase
import com.akaaflix.prime.domain.model.Category
import com.akaaflix.prime.domain.model.ContentRule
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.model.Playlist
import com.akaaflix.prime.domain.model.RuleRestriction
import com.akaaflix.prime.domain.model.RuleScope
import com.akaaflix.prime.domain.repository.CatalogRepository
import com.akaaflix.prime.domain.repository.ContentRulesRepository
import com.akaaflix.prime.domain.repository.EpgRepository
import com.akaaflix.prime.domain.repository.HistoryRepository
import com.akaaflix.prime.domain.repository.PlaylistRepository
import com.akaaflix.prime.navigation.SettingsRoute
import com.akaaflix.prime.player.ExternalPlayerInfo
import com.akaaflix.prime.player.ExternalPlayers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The settings dashboard sections (in display order). */
enum class SettingsSection {
    GENERAL, PLAYER, LIVE_FORMAT, SUBTITLES, SUBTITLE_APPEARANCE, HIDE_CONTENT, PARENTAL, PLAYLISTS,
    FAVORITES_GROUPS, LANGUAGE, APPEARANCE, NOTIFICATIONS, EPG, PLAYBACK, CACHE, DATA, DEVICES, BACKUP, ABOUT,
}

data class StorageInfo(val imageCacheBytes: Long = 0, val databaseBytes: Long = 0)

class SettingsViewModel(
    handle: SavedStateHandle,
    private val appContext: Context,
    private val settingsRepo: SettingsRepository,
    private val session: SessionManager,
    private val playlists: PlaylistRepository,
    private val catalog: CatalogRepository,
    private val rules: ContentRulesRepository,
    private val history: HistoryRepository,
    private val epg: EpgRepository,
    private val searchHistory: SearchHistoryRepository,
    private val notifications: NotificationRepository,
    private val backup: BackupManager,
    private val externalPlayers: ExternalPlayers,
) : ViewModel() {

    val initialSection: SettingsSection? = handle.toRoute<SettingsRoute>().section?.let { s -> SettingsSection.entries.firstOrNull { it.name == s } }

    val settings: StateFlow<AppSettings?> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val playlist: StateFlow<Playlist?> = session.activePlaylist
    val adultUnlocked: StateFlow<Boolean> = session.adultUnlocked

    val hiddenRules: StateFlow<List<ContentRule>> = session.activePlaylist
        .flatMapLatest { p -> if (p == null) emptyFlow() else rules.observeRules(p.id, RuleRestriction.HIDDEN) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val lockedRules: StateFlow<List<ContentRule>> = session.activePlaylist
        .flatMapLatest { p -> if (p == null) emptyFlow() else rules.observeRules(p.id, RuleRestriction.PIN_LOCKED) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _players = MutableStateFlow<List<ExternalPlayerInfo>>(emptyList())
    val players: StateFlow<List<ExternalPlayerInfo>> = _players.asStateFlow()
    private val _storage = MutableStateFlow(StorageInfo())
    val storage: StateFlow<StorageInfo> = _storage.asStateFlow()

    init {
        refreshPlayers()
        refreshStorage()
    }

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { settingsRepo.update(transform) }

    fun refreshPlayers() = viewModelScope.launch(Dispatchers.IO) { _players.value = externalPlayers.detect() }

    fun refreshStorage() = viewModelScope.launch(Dispatchers.IO) {
        val images = dirSize(File(appContext.cacheDir, "image_cache"))
        val dbFile = appContext.getDatabasePath(AppDatabase.NAME)
        val db = listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).sumOf { if (it.exists()) it.length() else 0L }
        _storage.value = StorageInfo(images, db)
    }

    private fun dirSize(dir: File): Long = if (!dir.exists()) 0 else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    @OptIn(ExperimentalCoilApi::class)
    fun clearImageCache(onDone: () -> Unit) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            appContext.imageLoader.memoryCache?.clear()
            appContext.imageLoader.diskCache?.clear()
        }
        refreshStorage()
        onDone()
    }

    fun clearEpgCache(onDone: () -> Unit) = viewModelScope.launch { epg.clearCache(); onDone() }

    fun clearDetailsCache(onDone: () -> Unit) { catalog.clearMemoryCache(); onDone() }

    fun refreshCatalogNow() { session.activePlaylist.value?.let { session.syncInBackground(it.id) } }

    fun clearWatchHistory(onDone: () -> Unit) = viewModelScope.launch { session.activePlaylist.value?.let { history.clear(it.id) }; onDone() }

    fun clearSearchHistory(onDone: () -> Unit) = viewModelScope.launch { searchHistory.clear(); onDone() }

    fun restoreDefaults(onDone: () -> Unit) = viewModelScope.launch { settingsRepo.restoreDefaults(includeParental = false); onDone() }

    /** Deletes every playlist and all local data (favorites, groups, history, caches, settings). */
    fun resetAll(onDone: () -> Unit) = viewModelScope.launch {
        val all = playlists.observePlaylists().first()
        all.forEach { playlists.deletePlaylist(it.id) }
        epg.clearCache()
        searchHistory.clear()
        notifications.clear()
        settingsRepo.restoreDefaults(includeParental = true)
        session.lockAdult()
        refreshStorage()
        onDone()
    }

    // ---- Parental controls ----

    fun setPin(pin: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = settingsRepo.setPin(pin)
        if (ok) session.markPinVerified()
        onDone(ok)
    }

    fun unlockAdult() = session.unlockAdult()
    fun lockAdult() = session.lockAdult()

    // ---- Hidden content ----

    suspend fun allCategories(type: ContentType): List<Category> {
        val p = session.activePlaylist.value ?: return emptyList()
        return catalog.allCategories(p.id, type)
    }

    fun setCategoryHidden(type: ContentType, cat: Category, hidden: Boolean) = viewModelScope.launch {
        val p = session.activePlaylist.value ?: return@launch
        if (hidden) rules.addRule(p.id, RuleScope.CATEGORY, RuleRestriction.HIDDEN, type, cat.id, cat.name)
        else rules.removeRule(p.id, RuleScope.CATEGORY, RuleRestriction.HIDDEN, type, cat.id)
    }

    fun addKeyword(keyword: String) = viewModelScope.launch {
        val p = session.activePlaylist.value ?: return@launch
        rules.addRule(p.id, RuleScope.KEYWORD, RuleRestriction.HIDDEN, null, keyword.trim(), keyword.trim())
    }

    fun removeRule(rule: ContentRule) = viewModelScope.launch { rules.removeRule(rule.id) }

    fun clearRules(restriction: RuleRestriction) = viewModelScope.launch {
        session.activePlaylist.value?.let { rules.clear(it.id, restriction) }
    }

    // ---- Backup ----

    fun exportBackup(uri: Uri, includeCredentials: Boolean, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val r = withContext(Dispatchers.IO) {
            appContext.contentResolver.openOutputStream(uri, "wt")?.use { backup.export(it, includeCredentials) }
        }
        onDone(r is AppResult.Success)
    }

    fun importBackup(uri: Uri, onDone: (RestoreReport?) -> Unit) = viewModelScope.launch {
        val r = withContext(Dispatchers.IO) { appContext.contentResolver.openInputStream(uri)?.use { backup.restore(it) } }
        onDone((r as? AppResult.Success)?.data)
    }
}
