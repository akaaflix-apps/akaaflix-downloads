package com.akaaflix.prime.data

import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.data.repository.CatalogSyncer
import com.akaaflix.prime.data.repository.SyncSummary
import com.akaaflix.prime.data.settings.SettingsRepository
import com.akaaflix.prime.domain.model.Playlist
import com.akaaflix.prime.domain.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds the active playlist and the parental-control unlock state for this app session.
 * Unlock state is in memory only: restarting the app always re-locks restricted content.
 */
class SessionManager(
    private val playlists: PlaylistRepository,
    private val syncer: CatalogSyncer,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val activePlaylist: StateFlow<Playlist?> = playlists.observeActive().stateIn(scope, SharingStarted.Eagerly, null)

    private val _adultUnlocked = MutableStateFlow(false)
    /** True after the PIN was entered to reveal adult content for this session. */
    val adultUnlocked: StateFlow<Boolean> = _adultUnlocked.asStateFlow()

    @Volatile private var pinUnlockedUntil = 0L

    fun unlockAdult() { _adultUnlocked.value = true; markPinVerified() }
    fun lockAdult() { _adultUnlocked.value = false; pinUnlockedUntil = 0 }

    /** Locked items stay unlocked for a short grace period after a successful PIN entry. */
    fun markPinVerified() { pinUnlockedUntil = now() + PIN_GRACE_MS }
    fun isPinGraceActive(): Boolean = now() < pinUnlockedUntil

    suspend fun verifyPin(pin: String): Boolean {
        val ok = settings.verifyPin(pin)
        if (ok) markPinVerified()
        return ok
    }

    /** Syncs the catalog in the application scope (survives screen changes). */
    fun syncInBackground(playlistId: Long, onDone: (AppResult<SyncSummary>) -> Unit = {}) {
        synchronized(this) {
            if (syncer.isRunning(playlistId)) return
            syncer.markQueued(playlistId)
        }
        scope.launch { onDone(syncer.sync(playlistId)) }
    }

    /** Refreshes the catalog if it was never loaded or is older than the auto-refresh interval. */
    suspend fun refreshIfStale(playlist: Playlist) {
        val hours = settings.current().autoRefreshHours
        val last = playlist.lastRefreshAt
        val stale = last == null || (hours > 0 && now() - last > hours * 3_600_000L)
        if (stale) syncInBackground(playlist.id)
    }

    companion object {
        const val PIN_GRACE_MS = 10 * 60 * 1000L
    }
}
