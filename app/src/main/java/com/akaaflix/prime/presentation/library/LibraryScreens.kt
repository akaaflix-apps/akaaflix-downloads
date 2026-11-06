package com.akaaflix.prime.presentation.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.akaaflix.prime.R
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.common.TimeFormat
import com.akaaflix.prime.data.SessionManager
import com.akaaflix.prime.domain.model.ContentRef
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.model.CustomGroup
import com.akaaflix.prime.domain.model.FavoriteItem
import com.akaaflix.prime.domain.model.GroupItem
import com.akaaflix.prime.domain.model.PlaybackEntry
import com.akaaflix.prime.domain.model.RuleRestriction
import com.akaaflix.prime.domain.model.RuleScope
import com.akaaflix.prime.domain.repository.ContentRulesRepository
import com.akaaflix.prime.domain.repository.HistoryRepository
import com.akaaflix.prime.domain.repository.LibraryRepository
import com.akaaflix.prime.navigation.AppNavigator
import com.akaaflix.prime.navigation.GroupRoute
import com.akaaflix.prime.navigation.LocalNavigator
import com.akaaflix.prime.navigation.MyListRoute
import com.akaaflix.prime.presentation.catalog.FilterChipItem
import com.akaaflix.prime.presentation.components.AkaaButton
import com.akaaflix.prime.presentation.components.AkaaCard
import com.akaaflix.prime.presentation.components.AkaaIconButton
import com.akaaflix.prime.presentation.components.ButtonVariant
import com.akaaflix.prime.presentation.components.ConfirmDialog
import com.akaaflix.prime.presentation.components.LocalContentActions
import com.akaaflix.prime.presentation.components.LocalMessenger
import com.akaaflix.prime.presentation.components.MessageState
import com.akaaflix.prime.presentation.components.PosterCard
import com.akaaflix.prime.presentation.components.RemoteImage
import com.akaaflix.prime.presentation.components.TextInputDialog
import com.akaaflix.prime.presentation.components.akaaViewModel
import com.akaaflix.prime.presentation.theme.AkaaTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MyListViewModel(
    handle: SavedStateHandle,
    private val session: SessionManager,
    private val library: LibraryRepository,
    private val historyRepo: HistoryRepository,
) : ViewModel() {
    val initialTab = handle.toRoute<MyListRoute>().tab
    private val pid = session.activePlaylist.filterNotNull().map { it.id }

    val favorites: StateFlow<List<FavoriteItem>> = pid.flatMapLatest { library.observeFavorites(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val groups: StateFlow<List<CustomGroup>> = pid.flatMapLatest { library.observeGroups(it) }.map { l -> l.filter { !it.isHidden } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history: StateFlow<List<PlaybackEntry>> = pid.flatMapLatest { historyRepo.observeHistory(it, 200) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createGroup(name: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val p = session.activePlaylist.value?.id ?: return@launch
        onResult(library.createGroup(p, name) is AppResult.Success)
    }

    fun removeHistory(e: PlaybackEntry) = viewModelScope.launch { historyRepo.remove(e.playlistId, e.type, e.contentId) }
    fun clearHistory() = viewModelScope.launch { session.activePlaylist.value?.id?.let { historyRepo.clear(it) } }
}

/** "My List": favorites (channels, movies, series), custom groups and watch history. */
@Composable
fun MyListScreen() {
    val vm = akaaViewModel { c, h -> MyListViewModel(h, c.sessionManager, c.libraryRepository, c.historyRepository) }
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val actions = LocalContentActions.current
    val messenger = LocalMessenger.current
    val d = AkaaTheme.dimens
    val c = AkaaTheme.colors
    var tab by remember { mutableStateOf(vm.initialTab.coerceIn(0, 2)) }
    var typeFilter by remember { mutableStateOf<ContentType?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = d.gutter, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_my_list), style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.weight(1f))
            FilterChipItem(stringResource(R.string.nav_favorites), tab == 0, { tab = 0 }, count = favorites.size)
            FilterChipItem(stringResource(R.string.nav_groups), tab == 1, { tab = 1 }, count = groups.size)
            FilterChipItem(stringResource(R.string.nav_history), tab == 2, { tab = 2 })
        }
        when (tab) {
            0 -> {
                LazyRow(contentPadding = PaddingValues(horizontal = d.gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChipItem(stringResource(R.string.filter_all), typeFilter == null, { typeFilter = null }) }
                    item { FilterChipItem(stringResource(R.string.nav_live), typeFilter == ContentType.LIVE, { typeFilter = ContentType.LIVE }) }
                    item { FilterChipItem(stringResource(R.string.nav_movies), typeFilter == ContentType.MOVIE, { typeFilter = ContentType.MOVIE }) }
                    item { FilterChipItem(stringResource(R.string.nav_series), typeFilter == ContentType.SERIES, { typeFilter = ContentType.SERIES }) }
                }
                val items = favorites.filter { typeFilter == null || it.ref.type == typeFilter }
                if (items.isEmpty()) {
                    MessageState(Icons.Default.FavoriteBorder, stringResource(R.string.favorites_empty_title), stringResource(R.string.favorites_empty_message))
                } else {
                    RefGrid(items.map { it.ref }, navigator, onLongClick = { ref -> actions.showMenu(ref, ref.extra, null) { open(navigator, ref) } })
                }
            }
            1 -> {
                Row(Modifier.padding(horizontal = d.gutter, vertical = 4.dp)) {
                    AkaaButton(stringResource(R.string.action_create_group), { creating = true }, icon = Icons.Default.Add, variant = ButtonVariant.PURPLE, compact = true)
                }
                if (groups.isEmpty()) {
                    MessageState(Icons.Default.Layers, stringResource(R.string.groups_empty_title), stringResource(R.string.groups_empty_hint))
                } else {
                    LazyColumn(contentPadding = PaddingValues(d.gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(groups, key = { it.id }) { g ->
                            AkaaCard(onClick = { navigator.openGroup(g.id) }, modifier = Modifier.fillMaxWidth(), containerColor = c.surface, focusScale = 1.02f) { _ ->
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Layers, null, tint = c.purple, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(14.dp))
                                    Text(g.name, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
                                    Text(stringResource(R.string.items_count, g.itemCount), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                if (history.isEmpty()) {
                    MessageState(Icons.Default.History, stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_message))
                } else {
                    Row(Modifier.padding(horizontal = d.gutter, vertical = 4.dp)) {
                        AkaaButton(stringResource(R.string.action_clear_history), { confirmClear = true }, icon = Icons.Default.DeleteSweep, compact = true)
                    }
                    LazyColumn(contentPadding = PaddingValues(d.gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(history, key = { "${it.type}-${it.contentId}" }) { e ->
                            HistoryRow(e, onClick = { resume(navigator, e) }, onRemove = { vm.removeHistory(e) })
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        TextInputDialog(
            title = stringResource(R.string.action_create_group), label = stringResource(R.string.group_name), initial = "",
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name -> vm.createGroup(name) { ok -> messenger.show(if (ok) R.string.msg_group_created else R.string.error_group_name) }; creating = false },
            onDismiss = { creating = false },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.action_clear_history), message = stringResource(R.string.history_clear_confirm),
            confirmLabel = stringResource(R.string.action_clear), destructive = true, onConfirm = vm::clearHistory, onDismiss = { confirmClear = false },
        )
    }
}

class GroupViewModel(
    handle: SavedStateHandle,
    private val session: SessionManager,
    private val library: LibraryRepository,
    private val rules: ContentRulesRepository,
) : ViewModel() {
    val groupId = handle.toRoute<GroupRoute>().groupId
    val groups: StateFlow<List<CustomGroup>> = session.activePlaylist.filterNotNull().flatMapLatest { library.observeGroups(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val items: StateFlow<List<GroupItem>> = library.observeGroupItems(groupId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rename(name: String, onResult: (Boolean) -> Unit) = viewModelScope.launch { onResult(library.renameGroup(groupId, name) is AppResult.Success) }
    fun delete() = viewModelScope.launch { library.deleteGroup(groupId) }
    fun remove(item: GroupItem) = viewModelScope.launch { library.removeFromGroup(groupId, item.ref.type, item.ref.contentId) }
    fun hide(name: String) = viewModelScope.launch {
        val pid = session.activePlaylist.value?.id ?: return@launch
        rules.addRule(pid, RuleScope.GROUP, RuleRestriction.HIDDEN, null, groupId.toString(), name)
    }
}

/** Custom group contents (can mix channels, movies and series). */
@Composable
fun GroupScreen() {
    val vm = akaaViewModel { c, h -> GroupViewModel(h, c.sessionManager, c.libraryRepository, c.rulesRepository) }
    val groups by vm.groups.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val messenger = LocalMessenger.current
    val group = groups.firstOrNull { it.id == vm.groupId }
    val d = AkaaTheme.dimens
    val c = AkaaTheme.colors
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<GroupItem?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = d.gutter, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AkaaIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), navigator::back)
            Text(group?.name ?: "", style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            AkaaButton(stringResource(R.string.action_rename), { renaming = true }, icon = Icons.Default.Edit, compact = true)
            AkaaButton(stringResource(R.string.action_hide), {
                group?.let { vm.hide(it.name); messenger.show(R.string.msg_hidden_item, it.name); navigator.back() }
            }, icon = Icons.Default.VisibilityOff, compact = true)
            AkaaButton(stringResource(R.string.action_delete), { deleting = true }, icon = Icons.Default.Delete, variant = ButtonVariant.DANGER, compact = true)
        }
        if (items.isEmpty()) {
            MessageState(Icons.Default.Layers, stringResource(R.string.group_empty_title), stringResource(R.string.group_empty_message))
        } else {
            RefGrid(items.map { it.ref }, navigator, onLongClick = { ref -> removing = items.firstOrNull { it.ref == ref } })
        }
    }
    if (renaming && group != null) {
        TextInputDialog(
            title = stringResource(R.string.action_rename), label = stringResource(R.string.group_name), initial = group.name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { vm.rename(it) { ok -> if (!ok) messenger.show(R.string.error_group_name) }; renaming = false },
            onDismiss = { renaming = false },
        )
    }
    if (deleting && group != null) {
        ConfirmDialog(
            title = stringResource(R.string.group_delete_title), message = stringResource(R.string.group_delete_message, group.name),
            confirmLabel = stringResource(R.string.action_delete), destructive = true,
            onConfirm = { vm.delete(); navigator.back() }, onDismiss = { deleting = false },
        )
    }
    removing?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.group_remove_item_title), message = stringResource(R.string.group_remove_item_message, item.ref.title),
            confirmLabel = stringResource(R.string.action_remove), destructive = true,
            onConfirm = { vm.remove(item) }, onDismiss = { removing = null },
        )
    }
}

@Composable
private fun RefGrid(refs: List<ContentRef>, navigator: AppNavigator, onLongClick: (ContentRef) -> Unit) {
    val d = AkaaTheme.dimens
    val isTv = AkaaTheme.isTv
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (isTv) d.posterWidth + 8.dp else 104.dp),
        contentPadding = PaddingValues(d.gutter),
        horizontalArrangement = Arrangement.spacedBy(d.itemSpacing),
        verticalArrangement = Arrangement.spacedBy(d.itemSpacing),
    ) {
        items(refs, key = { "${it.type}-${it.contentId}" }) { ref ->
            PosterCard(
                title = ref.title,
                imageUrl = ref.imageUrl,
                width = null,
                subtitle = stringResource(typeLabel(ref.type)),
                onClick = { open(navigator, ref) },
                onLongClick = { onLongClick(ref) },
            )
        }
    }
}

@Composable
private fun HistoryRow(e: PlaybackEntry, onClick: () -> Unit, onRemove: () -> Unit) {
    val c = AkaaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        AkaaCard(onClick = onClick, modifier = Modifier.weight(1f), containerColor = c.surface, focusScale = 1.02f) { _ ->
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RemoteImage(e.imageUrl, e.title, Modifier.width(56.dp).aspectRatio(if (e.type == ContentType.LIVE) 16f / 9f else 2f / 3f).clip(RoundedCornerShape(8.dp)), placeholderText = e.title.take(10))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(stringResource(typeLabel(e.type)), e.subtitle, TimeFormat.dateTime(e.lastWatchedAt, TimeFormat.zone("system"))).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1,
                    )
                    if (e.durationMs > 0) {
                        LinearProgressIndicator(progress = { e.progress }, color = c.red, trackColor = c.border, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(2.dp)))
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        AkaaIconButton(Icons.Default.Delete, stringResource(R.string.action_remove), onRemove, tint = c.red)
    }
}

fun typeLabel(t: ContentType): Int = when (t) {
    ContentType.LIVE -> R.string.type_channel
    ContentType.MOVIE -> R.string.type_movie
    ContentType.SERIES -> R.string.type_series
    ContentType.EPISODE -> R.string.type_episode
}

fun open(navigator: AppNavigator, ref: ContentRef) {
    when (ref.type) {
        ContentType.LIVE -> navigator.playChannel(ref.contentId.toLong(), ref.extra)
        ContentType.MOVIE -> navigator.openMovie(ref.contentId.toLong(), ref.extra)
        ContentType.SERIES -> navigator.openSeries(ref.contentId.toLong(), ref.extra)
        ContentType.EPISODE -> Unit
    }
}

private fun resume(navigator: AppNavigator, e: PlaybackEntry) {
    when (e.type) {
        ContentType.LIVE -> navigator.playChannel(e.contentId.toLong(), e.categoryId)
        ContentType.MOVIE -> navigator.playMovie(e.contentId.toLong(), e.categoryId, e.positionMs)
        ContentType.EPISODE -> e.seriesId?.let { navigator.playEpisode(it, e.contentId, e.categoryId, e.positionMs) }
        ContentType.SERIES -> navigator.openSeries(e.contentId.toLong(), e.categoryId)
    }
}
