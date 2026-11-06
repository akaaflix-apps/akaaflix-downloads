@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.akaaflix.prime.presentation.livetv

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.map
import com.akaaflix.prime.R
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.common.CategoryKind
import com.akaaflix.prime.common.TimeFormat
import com.akaaflix.prime.data.SessionManager
import com.akaaflix.prime.data.repository.PlaylistPrefs
import com.akaaflix.prime.data.repository.toRef
import com.akaaflix.prime.data.settings.SettingsRepository
import com.akaaflix.prime.domain.model.Category
import com.akaaflix.prime.domain.model.Channel
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.model.EpgProgram
import com.akaaflix.prime.domain.model.RuleRestriction
import com.akaaflix.prime.domain.model.RuleScope
import com.akaaflix.prime.domain.repository.BrowseQuery
import com.akaaflix.prime.domain.repository.CatalogRepository
import com.akaaflix.prime.domain.repository.ContentRulesRepository
import com.akaaflix.prime.domain.repository.EpgRepository
import com.akaaflix.prime.domain.repository.LibraryRepository
import com.akaaflix.prime.domain.repository.PlaylistRepository
import com.akaaflix.prime.navigation.LocalNavigator
import com.akaaflix.prime.player.PlayerFactory
import com.akaaflix.prime.player.StreamUrlBuilder
import com.akaaflix.prime.presentation.catalog.BrowseSelection
import com.akaaflix.prime.presentation.catalog.CategoryActionsDialog
import com.akaaflix.prime.presentation.catalog.FilterChipItem
import com.akaaflix.prime.presentation.catalog.FilterRow
import com.akaaflix.prime.presentation.catalog.label
import com.akaaflix.prime.presentation.catalog.title
import com.akaaflix.prime.presentation.components.AkaaButton
import com.akaaflix.prime.presentation.components.AkaaTextField
import com.akaaflix.prime.presentation.components.ButtonVariant
import com.akaaflix.prime.presentation.components.ChannelCard
import com.akaaflix.prime.presentation.components.LoadingState
import com.akaaflix.prime.presentation.components.LocalContentActions
import com.akaaflix.prime.presentation.components.LocalMessenger
import com.akaaflix.prime.presentation.components.LocalPinGate
import com.akaaflix.prime.presentation.components.MessageState
import com.akaaflix.prime.presentation.components.RemoteImage
import com.akaaflix.prime.presentation.components.akaaViewModel
import com.akaaflix.prime.presentation.theme.AkaaTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class LiveKey(val playlistId: Long, val includeAdult: Boolean)

data class ChannelUi(val channel: Channel, val isFavorite: Boolean, val locked: Boolean)

class LiveTvViewModel(
    private val session: SessionManager,
    private val settings: SettingsRepository,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val rules: ContentRulesRepository,
    private val epg: EpgRepository,
    private val playlists: PlaylistRepository,
    private val prefs: PlaylistPrefs,
    private val playerFactory: PlayerFactory,
) : ViewModel() {

    private val key: Flow<LiveKey> = combine(session.activePlaylist.filterNotNull(), settings.settings, session.adultUnlocked) { p, s, u ->
        LiveKey(p.id, !s.shouldHideAdult || u)
    }.distinctUntilChanged()

    private val _selection = MutableStateFlow<BrowseSelection>(BrowseSelection.All)
    val selection: StateFlow<BrowseSelection> = _selection.asStateFlow()
    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    val categories: StateFlow<List<Category>?> = key.flatMapLatest { catalog.observeCategories(it.playlistId, ContentType.LIVE, it.includeAdult) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val kinds: StateFlow<Map<CategoryKind, List<String>>> = key.mapLatest { k ->
        catalog.categoryKinds(k.playlistId, ContentType.LIVE).filterKeys { it != CategoryKind.OTHER && (k.includeAdult || it != CategoryKind.ADULT) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val countries: StateFlow<Map<String, List<String>>> = key.mapLatest { catalog.countries(it.playlistId, ContentType.LIVE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val groups = key.flatMapLatest { library.observeGroups(it.playlistId) }.map { l -> l.filter { !it.isHidden } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val favoriteIds = key.flatMapLatest { library.observeFavoriteIds(it.playlistId, ContentType.LIVE) }
    private val lockedKeys = key.flatMapLatest { rules.observeRules(it.playlistId, RuleRestriction.PIN_LOCKED) }
        .map { l -> l.filter { it.contentType == ContentType.LIVE }.map { "${it.scope}:${it.targetId}" }.toSet() }

    private val paging: Flow<PagingData<Channel>> = combine(key, _selection, _search.debounce(300).distinctUntilChanged()) { k, sel, q ->
        BrowseQuery(
            playlistId = k.playlistId, type = ContentType.LIVE, includeAdult = k.includeAdult,
            search = q.takeIf { it.isNotBlank() },
            categoryId = (sel as? BrowseSelection.Category)?.id,
            categoryIds = (sel as? BrowseSelection.Kind)?.categoryIds ?: (sel as? BrowseSelection.Country)?.categoryIds,
            favoritesOnly = sel == BrowseSelection.Favorites,
            groupId = (sel as? BrowseSelection.Group)?.id,
            mostWatched = sel == BrowseSelection.MostWatched,
        )
    }.flatMapLatest { catalog.channelsPaged(it) }.cachedIn(viewModelScope)

    val channels: Flow<PagingData<ChannelUi>> = combine(paging, favoriteIds, lockedKeys) { data, favs, locked ->
        data.map { ch ->
            ChannelUi(ch, ch.streamId.toString() in favs, "ITEM:${ch.streamId}" in locked || (ch.categoryId != null && "CATEGORY:${ch.categoryId}" in locked))
        }
    }

    private val _selected = MutableStateFlow<Channel?>(null)
    val selected: StateFlow<Channel?> = _selected.asStateFlow()
    private val _programs = MutableStateFlow<List<EpgProgram>>(emptyList())
    val programs: StateFlow<List<EpgProgram>> = _programs.asStateFlow()
    private val _epgLoading = MutableStateFlow(false)
    val epgLoading: StateFlow<Boolean> = _epgLoading.asStateFlow()

    private val _preview = MutableStateFlow<ExoPlayer?>(null)
    val preview: StateFlow<ExoPlayer?> = _preview.asStateFlow()
    private val _previewChannel = MutableStateFlow<Channel?>(null)
    val previewChannel: StateFlow<Channel?> = _previewChannel.asStateFlow()

    val isFavoriteSelected: StateFlow<Boolean> = combine(_selected, favoriteIds) { ch, favs -> ch != null && ch.streamId.toString() in favs }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var epgJob: Job? = null

    init {
        viewModelScope.launch {
            val playlist = session.activePlaylist.filterNotNull().first()
            val s = settings.current()
            if (s.rememberLastChannel) {
                val last = prefs.get(playlist.id, PlaylistPrefs.LAST_CHANNEL)?.toLongOrNull()
                val ch = last?.let { catalog.getChannel(playlist.id, it) }
                if (ch != null) {
                    select(ch)
                    if (s.livePreview && s.autoPlay && !rules.isLocked(playlist.id, ContentType.LIVE, ch.streamId.toString(), ch.categoryId)) startPreview(ch)
                }
            }
        }
    }

    fun setSelection(s: BrowseSelection) { _selection.value = s }
    fun setSearch(q: String) { _search.value = q }

    fun select(ch: Channel) {
        if (_selected.value?.streamId == ch.streamId) return
        _selected.value = ch
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            if (!settings.current().epgEnabled) { _programs.value = emptyList(); return@launch }
            delay(350) // debounce while the user scrolls through channels with the remote
            _epgLoading.value = true
            _programs.value = when (val r = epg.shortEpg(ch.playlistId, ch.streamId)) {
                is AppResult.Success -> r.data
                is AppResult.Failure -> emptyList()
            }
            _epgLoading.value = false
        }
    }

    fun startPreview(ch: Channel) = viewModelScope.launch {
        val playlist = session.activePlaylist.value ?: return@launch
        val creds = playlists.credentials(playlist.id) ?: return@launch
        val s = settings.current()
        val player = _preview.value ?: playerFactory.create(s).also { _preview.value = it }
        val fmt = StreamUrlBuilder.resolveLiveFormat(s.liveFormat, playlist.allowedOutputFormats, playlist.rtmpPort)
        val source = StreamUrlBuilder.live(creds, ch.streamId, fmt, playlist.rtmpPort)
        player.setMediaItem(MediaItem.Builder().setUri(source.url).apply { source.mimeType?.let { setMimeType(it) } }.build())
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.prepare()
        player.playWhenReady = true
        _previewChannel.value = ch
        prefs.put(playlist.id, PlaylistPrefs.LAST_CHANNEL, ch.streamId.toString())
    }

    fun stopPreview() {
        _preview.value?.let { it.stop(); it.release() }
        _preview.value = null
        _previewChannel.value = null
    }

    fun rememberChannel(ch: Channel) = viewModelScope.launch { prefs.put(ch.playlistId, PlaylistPrefs.LAST_CHANNEL, ch.streamId.toString()) }

    fun hideCategory(cat: Category) = viewModelScope.launch {
        val pid = session.activePlaylist.value?.id ?: return@launch
        rules.addRule(pid, RuleScope.CATEGORY, RuleRestriction.HIDDEN, ContentType.LIVE, cat.id, cat.name)
        if ((_selection.value as? BrowseSelection.Category)?.id == cat.id) _selection.value = BrowseSelection.All
    }

    fun setCategoryLocked(cat: Category, locked: Boolean) = viewModelScope.launch {
        val pid = session.activePlaylist.value?.id ?: return@launch
        if (locked) rules.addRule(pid, RuleScope.CATEGORY, RuleRestriction.PIN_LOCKED, ContentType.LIVE, cat.id, cat.name)
        else rules.removeRule(pid, RuleScope.CATEGORY, RuleRestriction.PIN_LOCKED, ContentType.LIVE, cat.id)
    }

    override fun onCleared() {
        stopPreview()
        super.onCleared()
    }
}

/** Live TV: categories, quick filters, channel grid, preview player (TV) and EPG now/next. */
@Composable
fun LiveTvScreen() {
    val vm = akaaViewModel { c, _ ->
        LiveTvViewModel(
            c.sessionManager, c.settingsRepository, c.catalogRepository, c.libraryRepository, c.rulesRepository,
            c.epgRepository, c.playlistRepository, c.playlistPrefs, c.playerFactory,
        )
    }
    val navigator = LocalNavigator.current
    val actions = LocalContentActions.current
    val pinGate = LocalPinGate.current
    val messenger = LocalMessenger.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val kinds by vm.kinds.collectAsStateWithLifecycle()
    val countries by vm.countries.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val programs by vm.programs.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val previewChannel by vm.previewChannel.collectAsStateWithLifecycle()
    val isFavSelected by vm.isFavoriteSelected.collectAsStateWithLifecycle()
    val settings by LocalAppContainerSettings()
    val channels = vm.channels.collectAsLazyPagingItems()
    val isTv = AkaaTheme.isTv
    val d = AkaaTheme.dimens
    val c = AkaaTheme.colors
    var categoryMenu by remember { mutableStateOf<Category?>(null) }
    val firstFocus = remember { FocusRequester() }

    DisposableEffect(Unit) { onDispose { vm.stopPreview() } }

    val openFullScreen: (Channel) -> Unit = { ch ->
        vm.stopPreview()
        vm.rememberChannel(ch)
        navigator.playChannel(ch.streamId, ch.categoryId)
    }
    val onChannelClick: (ChannelUi) -> Unit = { ui ->
        val ch = ui.channel
        vm.select(ch)
        if (isTv && settings?.livePreview != false && previewChannel?.streamId != ch.streamId) {
            pinGate.guard(ContentType.LIVE, ch.streamId.toString(), ch.categoryId) { vm.startPreview(ch) }
        } else {
            openFullScreen(ch)
        }
    }
    val selectCategory: (Category) -> Unit = { cat ->
        if (cat.isLocked) pinGate.requirePin(R.string.pin_content_locked) { vm.setSelection(BrowseSelection.Category(cat.id, cat.name)) }
        else vm.setSelection(BrowseSelection.Category(cat.id, cat.name))
    }
    val total = categories?.sumOf { it.count }

    val channelGrid: @Composable (Modifier) -> Unit = { modifier ->
        when {
            categories == null -> LoadingState(modifier)
            channels.itemCount == 0 && channels.loadState.refresh !is androidx.paging.LoadState.Loading -> MessageState(
                icon = Icons.Default.LiveTv,
                title = stringResource(R.string.empty_results_title),
                message = stringResource(if (search.isNotBlank()) R.string.empty_search_message else R.string.empty_section_message),
                modifier = modifier,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTv) 176.dp else 150.dp),
                modifier = modifier,
                contentPadding = PaddingValues(if (isTv) 12.dp else d.gutter),
                horizontalArrangement = Arrangement.spacedBy(d.itemSpacing),
                verticalArrangement = Arrangement.spacedBy(d.itemSpacing),
            ) {
                items(count = channels.itemCount, key = { i -> channels.peek(i)?.channel?.streamId ?: -i.toLong() }) { i ->
                    val ui = channels[i] ?: return@items
                    val ch = ui.channel
                    ChannelCard(
                        name = ch.name, logo = ch.logo, number = ch.number,
                        selected = previewChannel?.streamId == ch.streamId,
                        isFavorite = ui.isFavorite, locked = ui.locked,
                        onClick = { onChannelClick(ui) },
                        onFocus = { if (isTv) vm.select(ch) },
                        onLongClick = {
                            val catName = categories?.firstOrNull { it.id == ch.categoryId }?.name
                            actions.showMenu(ch.toRef(), ch.categoryId, catName) { openFullScreen(ch) }
                        },
                    )
                }
            }
        }
    }

    if (isTv) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.width(d.sidebarWidth).fillMaxHeight().background(c.backgroundSecondary.copy(alpha = 0.6f)),
                contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    AkaaTextField(
                        value = search, onValueChange = vm::setSearch, label = stringResource(R.string.search_channels),
                        leadingIcon = Icons.Default.Search, imeAction = ImeAction.Search, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                }
                item { FilterRow(stringResource(R.string.filter_all_channels), selection == BrowseSelection.All, { vm.setSelection(BrowseSelection.All) }, icon = Icons.Default.LiveTv, count = total, focusRequester = firstFocus) }
                item { FilterRow(stringResource(R.string.nav_favorites), selection == BrowseSelection.Favorites, { vm.setSelection(BrowseSelection.Favorites) }, icon = Icons.Default.Star) }
                item { FilterRow(stringResource(R.string.filter_recent_channels), selection == BrowseSelection.MostWatched, { vm.setSelection(BrowseSelection.MostWatched) }, icon = Icons.Default.Favorite) }
                items(groups, key = { "g${it.id}" }) { g ->
                    FilterRow(g.name, (selection as? BrowseSelection.Group)?.id == g.id, { vm.setSelection(BrowseSelection.Group(g.id, g.name)) }, icon = Icons.Default.Folder, count = g.itemCount)
                }
                if (kinds.isNotEmpty()) {
                    item { HorizontalDivider(color = c.border, modifier = Modifier.padding(vertical = 6.dp)) }
                    items(kinds.entries.toList(), key = { "k${it.key}" }) { (kind, ids) ->
                        FilterRow(kind.label(), (selection as? BrowseSelection.Kind)?.kind == kind, { vm.setSelection(BrowseSelection.Kind(kind, ids)) }, icon = Icons.Default.Category)
                    }
                }
                if (countries.isNotEmpty()) {
                    item { HorizontalDivider(color = c.border, modifier = Modifier.padding(vertical = 6.dp)) }
                    items(countries.entries.toList(), key = { "c${it.key}" }) { (code, ids) ->
                        FilterRow(code, (selection as? BrowseSelection.Country)?.code == code, { vm.setSelection(BrowseSelection.Country(code, ids)) }, icon = Icons.Default.Public)
                    }
                }
                item { HorizontalDivider(color = c.border, modifier = Modifier.padding(vertical = 6.dp)) }
                items(categories.orEmpty(), key = { "cat${it.id}" }) { cat ->
                    FilterRow(
                        cat.name, (selection as? BrowseSelection.Category)?.id == cat.id, { selectCategory(cat) },
                        icon = Icons.Default.Folder, count = cat.count, locked = cat.isLocked, onLongClick = { categoryMenu = cat },
                    )
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp, end = 16.dp, top = 8.dp)) {
                Row(Modifier.fillMaxWidth().height(250.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PreviewPane(preview, previewChannel, Modifier.weight(1.4f).fillMaxHeight())
                    EpgPanel(
                        channel = selected, programs = programs, isFavorite = isFavSelected, timeZone = settings?.timeZone ?: "system",
                        shiftHours = settings?.epgTimeShiftHours ?: 0,
                        onWatch = { selected?.let(openFullScreen) },
                        onFavorite = { selected?.let { actions.toggleFavorite(it.toRef()) } },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                Text(selection.title(), style = MaterialTheme.typography.titleMedium, color = c.textSecondary, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
                channelGrid(Modifier.weight(1f).fillMaxWidth())
            }
        }
        LaunchedEffect(categories != null) { if (categories != null) runCatching { firstFocus.requestFocus() } }
    } else {
        Column(Modifier.fillMaxSize()) {
            AkaaTextField(
                value = search, onValueChange = vm::setSearch, label = stringResource(R.string.search_channels),
                leadingIcon = Icons.Default.Search, imeAction = ImeAction.Search,
                modifier = Modifier.fillMaxWidth().padding(horizontal = d.gutter, vertical = 6.dp),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = d.gutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChipItem(stringResource(R.string.filter_all), selection == BrowseSelection.All, { vm.setSelection(BrowseSelection.All) }, count = total) }
                item { FilterChipItem(stringResource(R.string.nav_favorites), selection == BrowseSelection.Favorites, { vm.setSelection(BrowseSelection.Favorites) }) }
                item { FilterChipItem(stringResource(R.string.filter_recent_channels), selection == BrowseSelection.MostWatched, { vm.setSelection(BrowseSelection.MostWatched) }) }
                items(groups, key = { "g${it.id}" }) { g -> FilterChipItem(g.name, (selection as? BrowseSelection.Group)?.id == g.id, { vm.setSelection(BrowseSelection.Group(g.id, g.name)) }) }
                items(kinds.entries.toList(), key = { "k${it.key}" }) { (kind, ids) ->
                    FilterChipItem(kind.label(), (selection as? BrowseSelection.Kind)?.kind == kind, { vm.setSelection(BrowseSelection.Kind(kind, ids)) })
                }
                items(categories.orEmpty(), key = { "cat${it.id}" }) { cat ->
                    FilterChipItem(cat.name, (selection as? BrowseSelection.Category)?.id == cat.id, { selectCategory(cat) }, onLongClick = { categoryMenu = cat }, count = cat.count)
                }
            }
            channelGrid(Modifier.weight(1f).fillMaxWidth())
        }
    }

    categoryMenu?.let { cat ->
        CategoryActionsDialog(
            category = cat,
            onHide = { vm.hideCategory(cat); messenger.show(R.string.msg_hidden_category, cat.name) },
            onToggleLock = { pinGate.requirePin(R.string.pin_change_lock, always = true) { vm.setCategoryLocked(cat, !cat.isLocked) } },
            onDismiss = { categoryMenu = null },
        )
    }
}

@Composable
private fun LocalAppContainerSettings() =
    com.akaaflix.prime.presentation.components.LocalAppContainer.current.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)

@Composable
private fun PreviewPane(player: ExoPlayer?, channel: Channel?, modifier: Modifier) {
    val c = AkaaTheme.colors
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black).border(1.dp, c.border, RoundedCornerShape(16.dp))) {
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    }
                },
                update = { it.player = player },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                "LIVE", color = Color.White, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).background(c.red, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
            )
            channel?.let {
                Text(
                    it.name, color = Color.White, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LiveTv, null, tint = c.textSecondary, modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.live_preview_hint), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun EpgPanel(
    channel: Channel?,
    programs: List<EpgProgram>,
    isFavorite: Boolean,
    timeZone: String,
    shiftHours: Int,
    onWatch: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier,
) {
    val c = AkaaTheme.colors
    val zone = TimeFormat.zone(timeZone)
    val shift = shiftHours * 3600L
    val nowSec = System.currentTimeMillis() / 1000
    val current = programs.firstOrNull { it.isNow(nowSec - shift) }
    val next = programs.firstOrNull { it.startEpochSec >= (current?.endEpochSec ?: (nowSec - shift)) && it != current }
    Column(
        modifier.background(c.surface, RoundedCornerShape(16.dp)).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (channel == null) {
            Text(stringResource(R.string.live_select_channel), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteImage(channel.logo, channel.name, Modifier.size(width = 64.dp, height = 40.dp), contentScale = ContentScale.Fit, placeholderText = null)
            Spacer(Modifier.width(10.dp))
            Text(channel.name, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        if (current != null) {
            Text(stringResource(R.string.epg_now), style = MaterialTheme.typography.labelMedium, color = c.gold)
            Text(current.title, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${TimeFormat.time(current.startEpochSec + shift, zone)} - ${TimeFormat.time(current.endEpochSec + shift, zone)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            LinearProgressIndicator(progress = { current.progress(nowSec - shift) }, color = c.red, trackColor = c.border, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)))
        } else {
            Text(stringResource(R.string.epg_unavailable), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        if (next != null) {
            Text(stringResource(R.string.epg_next), style = MaterialTheme.typography.labelMedium, color = c.gold)
            Text("${TimeFormat.time(next.startEpochSec + shift, zone)}  ${next.title}", style = MaterialTheme.typography.bodySmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AkaaButton(stringResource(R.string.action_fullscreen), onWatch, icon = Icons.Default.Fullscreen, variant = ButtonVariant.ACCENT, compact = true)
            AkaaButton(
                stringResource(if (isFavorite) R.string.action_remove_favorite_short else R.string.action_add_favorite_short), onFavorite,
                icon = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder, compact = true,
            )
        }
    }
}
