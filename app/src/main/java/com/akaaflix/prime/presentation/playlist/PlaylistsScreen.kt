package com.akaaflix.prime.presentation.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.akaaflix.prime.R
import com.akaaflix.prime.common.AppError
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.common.TimeFormat
import com.akaaflix.prime.data.SessionManager
import com.akaaflix.prime.data.repository.CatalogSyncer
import com.akaaflix.prime.data.repository.SyncProgress
import com.akaaflix.prime.domain.model.AccountStatus
import com.akaaflix.prime.domain.model.ConnectionStatus
import com.akaaflix.prime.domain.model.Playlist
import com.akaaflix.prime.domain.repository.PlaylistRepository
import com.akaaflix.prime.navigation.LocalNavigator
import com.akaaflix.prime.navigation.PlaylistsRoute
import com.akaaflix.prime.network.ServerUrl
import com.akaaflix.prime.presentation.components.AkaaButton
import com.akaaflix.prime.presentation.components.AkaaCard
import com.akaaflix.prime.presentation.components.AkaaIconButton
import com.akaaflix.prime.presentation.components.AkaaLogo
import com.akaaflix.prime.presentation.components.ButtonVariant
import com.akaaflix.prime.presentation.components.ConfirmDialog
import com.akaaflix.prime.presentation.components.LocalMessenger
import com.akaaflix.prime.presentation.components.akaaViewModel
import com.akaaflix.prime.presentation.theme.AkaaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistsViewModel(
    handle: SavedStateHandle,
    private val repo: PlaylistRepository,
    private val syncer: CatalogSyncer,
    private val session: SessionManager,
) : ViewModel() {
    val selectMode = handle.toRoute<PlaylistsRoute>().selectMode
    val playlists: StateFlow<List<Playlist>?> = repo.observePlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val progress: StateFlow<Map<Long, SyncProgress>> = syncer.progress
    private val _lastError = MutableStateFlow<AppError?>(null)
    val lastError: StateFlow<AppError?> = _lastError.asStateFlow()

    fun activate(p: Playlist, onDone: () -> Unit) = viewModelScope.launch {
        repo.setActive(p.id)
        session.lockAdult()
        session.refreshIfStale(p)
        onDone()
    }

    fun setDefault(p: Playlist) = viewModelScope.launch { repo.setDefault(p.id) }

    fun refresh(p: Playlist, onResult: (AppResult<*>) -> Unit) = viewModelScope.launch {
        val account = repo.refreshAccount(p.id)
        if (account is AppResult.Failure) { _lastError.value = account.error; onResult(account); return@launch }
        session.syncInBackground(p.id) { onResult(it) }
    }

    fun refreshAll(onResult: (AppResult<*>) -> Unit) = viewModelScope.launch {
        playlists.value.orEmpty().forEach { refresh(it, onResult) }
    }

    fun delete(p: Playlist) = viewModelScope.launch { repo.deletePlaylist(p.id) }
}

/** Playlist management: add, edit, delete, activate, set default, refresh, connection status. */
@Composable
fun PlaylistsScreen() {
    val vm = akaaViewModel { c, h -> PlaylistsViewModel(h, c.playlistRepository, c.catalogSyncer, c.sessionManager) }
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val messenger = LocalMessenger.current
    val c = AkaaTheme.colors
    val isTv = AkaaTheme.isTv
    var toDelete by remember { mutableStateOf<Playlist?>(null) }
    val firstFocus = remember { FocusRequester() }

    val onResult: (AppResult<*>) -> Unit = { r ->
        when (r) {
            is AppResult.Success -> messenger.show(R.string.msg_playlist_refreshed)
            is AppResult.Failure -> messenger.show(r.error.messageRes)
        }
    }

    LaunchedEffect(playlists) {
        if (playlists?.isEmpty() == true) navigator.addPlaylist(firstRun = true)
    }

    Column(Modifier.fillMaxSize().background(c.screenGradient).safeDrawingPadding().padding(horizontal = if (isTv) 48.dp else 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!vm.selectMode) AkaaIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), navigator::back)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (vm.selectMode) R.string.playlist_select_title else R.string.playlists_title),
                    style = if (isTv) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall, color = c.text,
                )
                Text(stringResource(R.string.playlists_subtitle), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AkaaLogo(height = if (isTv) 50.dp else 34.dp)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AkaaButton(stringResource(R.string.action_add_playlist), { navigator.addPlaylist() }, icon = Icons.Default.Add, variant = ButtonVariant.PURPLE, compact = !isTv)
            AkaaButton(stringResource(R.string.action_refresh_all), { vm.refreshAll(onResult) }, icon = Icons.Default.Refresh, compact = !isTv)
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(playlists.orEmpty(), key = { it.id }) { p ->
                PlaylistCard(
                    p = p,
                    progress = progress[p.id],
                    focusRequester = if (p.id == playlists?.firstOrNull()?.id) firstFocus else null,
                    onOpen = { vm.activate(p) { navigator.goHomeClearingStack() } },
                    onDefault = { vm.setDefault(p) },
                    onEdit = { navigator.editPlaylist(p.id) },
                    onRefresh = { vm.refresh(p, onResult) },
                    onDelete = { toDelete = p },
                )
            }
        }
        Text(
            stringResource(R.string.playlists_count, playlists?.size ?: 0),
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp),
        )
    }
    LaunchedEffect(playlists?.isNotEmpty()) { if (playlists?.isNotEmpty() == true) runCatching { firstFocus.requestFocus() } }

    toDelete?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.playlist_delete_title),
            message = stringResource(R.string.playlist_delete_message, p.name),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { vm.delete(p) },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun PlaylistCard(
    p: Playlist,
    progress: SyncProgress?,
    focusRequester: FocusRequester?,
    onOpen: () -> Unit,
    onDefault: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = AkaaTheme.colors
    val isTv = AkaaTheme.isTv
    val connected = p.connectionStatus == ConnectionStatus.CONNECTED && p.accountStatus == AccountStatus.ACTIVE
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(c.surface, c.surfaceHigh)), RoundedCornerShape(18.dp))
            .border(1.dp, if (p.isActive) c.purple else c.border, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AkaaCard(onClick = onOpen, focusRequester = focusRequester, modifier = Modifier.weight(1f), containerColor = Color.Transparent, focusScale = 1.02f) { _ ->
            Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(if (isTv) 96.dp else 64.dp).background(Brush.linearGradient(listOf(c.red, c.purple)), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = Color.White, modifier = Modifier.size(if (isTv) 44.dp else 30.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, style = MaterialTheme.typography.titleLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp))
                        StatusChip(connected, p)
                    }
                    Text("${ServerUrl.displayHost(p.serverUrl)} · ${p.username}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        val exp = p.expiresAtEpochSec?.let { TimeFormat.date(it, TimeFormat.zone("system")) } ?: stringResource(R.string.account_unlimited)
                        Text(stringResource(R.string.playlist_expires, exp), style = MaterialTheme.typography.bodySmall, color = c.gold)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.playlist_type_xtream), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    if (progress?.running == true) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = c.accent)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.sync_in_progress), style = MaterialTheme.typography.bodySmall, color = c.accent)
                        }
                    }
                }
                if (isTv) {
                    Counts(p)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!isTv) Counts(p)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AkaaIconButton(if (p.isDefault) Icons.Default.Star else Icons.Default.StarBorder, stringResource(R.string.action_set_default), onDefault, tint = c.gold, size = if (isTv) 44.dp else 36.dp)
                AkaaIconButton(Icons.Default.Edit, stringResource(R.string.action_edit), onEdit, size = if (isTv) 44.dp else 36.dp)
                AkaaIconButton(Icons.Default.Refresh, stringResource(R.string.action_refresh), onRefresh, size = if (isTv) 44.dp else 36.dp)
                AkaaIconButton(Icons.Default.Delete, stringResource(R.string.action_delete), onDelete, tint = c.red, size = if (isTv) 44.dp else 36.dp)
            }
        }
    }
}

@Composable
private fun StatusChip(connected: Boolean, p: Playlist) {
    val c = AkaaTheme.colors
    val color = if (connected) c.success else c.red
    val label = when {
        p.accountStatus == AccountStatus.EXPIRED -> stringResource(R.string.status_expired)
        p.accountStatus == AccountStatus.BANNED -> stringResource(R.string.status_banned)
        p.accountStatus == AccountStatus.DISABLED -> stringResource(R.string.status_disabled)
        connected -> stringResource(R.string.status_connected)
        p.connectionStatus == ConnectionStatus.UNKNOWN -> stringResource(R.string.status_unknown)
        else -> stringResource(R.string.status_disconnected)
    }
    Row(
        Modifier.border(1.5.dp, color, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun Counts(p: Playlist) {
    Row(horizontalArrangement = Arrangement.spacedBy(if (AkaaTheme.isTv) 18.dp else 10.dp), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        CountItem(Icons.Default.LiveTv, stringResource(R.string.nav_live), p.liveCount)
        CountItem(Icons.Default.Movie, stringResource(R.string.nav_movies), p.movieCount)
        CountItem(Icons.Default.Tv, stringResource(R.string.nav_series), p.seriesCount)
    }
}

@Composable
private fun CountItem(icon: ImageVector, label: String, value: Int) {
    val c = AkaaTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(if (AkaaTheme.isTv) 26.dp else 18.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 1)
        Text(String.format(java.util.Locale.US, "%,d", value), style = MaterialTheme.typography.titleSmall, color = c.text)
    }
}
