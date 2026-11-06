package com.akaaflix.prime.presentation.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.map
import com.akaaflix.prime.R
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.common.CategoryKind
import com.akaaflix.prime.data.SessionManager
import com.akaaflix.prime.data.settings.SettingsRepository
import com.akaaflix.prime.domain.model.Category
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.model.CustomGroup
import com.akaaflix.prime.domain.model.RuleRestriction
import com.akaaflix.prime.domain.model.RuleScope
import com.akaaflix.prime.domain.model.SortOrder
import com.akaaflix.prime.domain.repository.BrowseQuery
import com.akaaflix.prime.domain.repository.CatalogRepository
import com.akaaflix.prime.domain.repository.ContentRulesRepository
import com.akaaflix.prime.domain.repository.LibraryRepository
import com.akaaflix.prime.navigation.LocalNavigator
import com.akaaflix.prime.presentation.components.AkaaIconButton
import com.akaaflix.prime.presentation.components.AkaaTextField
import com.akaaflix.prime.presentation.components.ChoiceDialog
import com.akaaflix.prime.presentation.components.LoadingState
import com.akaaflix.prime.presentation.components.LocalContentActions
import com.akaaflix.prime.presentation.components.LocalMessenger
import com.akaaflix.prime.presentation.components.LocalPinGate
import com.akaaflix.prime.presentation.components.MessageState
import com.akaaflix.prime.presentation.components.PosterCard
import com.akaaflix.prime.presentation.components.TextInputDialog
import com.akaaflix.prime.presentation.components.akaaViewModel
import com.akaaflix.prime.presentation.home.PosterItem
import com.akaaflix.prime.presentation.home.toPoster
import com.akaaflix.prime.presentation.theme.AkaaTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class CatalogKey(val playlistId: Long, val includeAdult: Boolean)

class CatalogViewModel(
    val type: ContentType,
    handle: SavedStateHandle,
    private val session: SessionManager,
    settings: SettingsRepository,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val rules: ContentRulesRepository,
) : ViewModel() {

    private val initialCategory: String? = handle.get<String>("categoryId")

    private val key: Flow<CatalogKey> = combine(session.activePlaylist.filterNotNull(), settings.settings, session.adultUnlocked) { p, s, u ->
        CatalogKey(p.id, !s.shouldHideAdult || u)
    }.distinctUntilChanged()

    private val _selection = MutableStateFlow<BrowseSelection>(BrowseSelection.All)
    val selection: StateFlow<BrowseSelection> = _selection.asStateFlow()
    private val _sort = MutableStateFlow(SortOrder.DEFAULT)
    val sort: StateFlow<SortOrder> = _sort.asStateFlow()
    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    val categories: StateFlow<List<Category>?> = key.flatMapLatest { catalog.observeCategories(it.playlistId, type, it.includeAdult) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val groups: StateFlow<List<CustomGroup>> = key.flatMapLatest { library.observeGroups(it.playlistId) }
        .map { list -> list.filter { !it.isHidden } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val countries: StateFlow<Map<String, List<String>>> = key.mapLatest { catalog.countries(it.playlistId, type) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val kinds: StateFlow<Map<CategoryKind, List<String>>> = key.mapLatest { k ->
        catalog.categoryKinds(k.playlistId, type).filterKeys { it != CategoryKind.OTHER && (k.includeAdult || it != CategoryKind.ADULT) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val favoriteIds = key.flatMapLatest { library.observeFavoriteIds(it.playlistId, type) }
    private val lockedIds = key.flatMapLatest { rules.observeRules(it.playlistId, RuleRestriction.PIN_LOCKED) }.map { list ->
        list.filter { it.contentType == type }.map { "${it.scope}:${it.targetId}" }.toSet()
    }

    private val paging: Flow<PagingData<PosterItem>> = combine(key, _selection, _sort, _search.debounce(300).distinctUntilChanged()) { k, sel, sort, q ->
        query(k, sel, sort, q)
    }.flatMapLatest { q ->
        when (type) {
            ContentType.MOVIE -> catalog.moviesPaged(q).map { pd -> pd.map { it.toPoster(emptySet()) } }
            ContentType.SERIES -> catalog.seriesPaged(q).map { pd -> pd.map { it.toPoster(emptySet()) } }
            else -> emptyFlow()
        }
    }.cachedIn(viewModelScope)

    /** Paged posters decorated with favorite / lock state. */
    val items: Flow<PagingData<PosterItem>> = combine(paging, favoriteIds, lockedIds) { data, favs, locked ->
        data.map { p ->
            p.copy(
                isFavorite = p.id.toString() in favs,
                locked = "ITEM:${p.id}" in locked || (p.categoryId != null && "CATEGORY:${p.categoryId}" in locked),
            )
        }
    }

    init {
        if (initialCategory != null) {
            viewModelScope.launch {
                val cats = categories.filterNotNull().first()
                cats.firstOrNull { it.id == initialCategory }?.let { _selection.value = BrowseSelection.Category(it.id, it.name) }
            }
        }
    }

    private fun query(k: CatalogKey, sel: BrowseSelection, sort: SortOrder, q: String) = BrowseQuery(
        playlistId = k.playlistId,
        type = type,
        includeAdult = k.includeAdult,
        search = q.takeIf { it.isNotBlank() },
        sort = if (sel == BrowseSelection.Latest) SortOrder.LATEST else sort,
        categoryId = (sel as? BrowseSelection.Category)?.id,
        categoryIds = (sel as? BrowseSelection.Country)?.categoryIds ?: (sel as? BrowseSelection.Kind)?.categoryIds,
        mostWatched = sel == BrowseSelection.MostWatched,
        favoritesOnly = sel == BrowseSelection.Favorites,
        groupId = (sel as? BrowseSelection.Group)?.id,
    )

    fun select(s: BrowseSelection) { _selection.value = s }
    fun setSort(s: SortOrder) { _sort.value = s }
    fun setSearch(q: String) { _search.value = q }

    fun createGroup(name: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val pid = session.activePlaylist.value?.id ?: return@launch
        onResult(library.createGroup(pid, name) is AppResult.Success)
    }

    fun hideCategory(cat: Category) = viewModelScope.launch {
        val pid = session.activePlaylist.value?.id ?: return@launch
        rules.addRule(pid, RuleScope.CATEGORY, RuleRestriction.HIDDEN, type, cat.id, cat.name)
        if ((_selection.value as? BrowseSelection.Category)?.id == cat.id) _selection.value = BrowseSelection.All
    }

    fun setCategoryLocked(cat: Category, locked: Boolean) = viewModelScope.launch {
        val pid = session.activePlaylist.value?.id ?: return@launch
        if (locked) rules.addRule(pid, RuleScope.CATEGORY, RuleRestriction.PIN_LOCKED, type, cat.id, cat.name)
        else rules.removeRule(pid, RuleScope.CATEGORY, RuleRestriction.PIN_LOCKED, type, cat.id)
    }
}

/** Movies / Series browsing: categories (genres), countries, latest, most watched, favorites, groups, sorting and search. */
@Composable
fun CatalogScreen(type: ContentType) {
    val vm = akaaViewModel(key = "catalog-$type") { c, h ->
        CatalogViewModel(type, h, c.sessionManager, c.settingsRepository, c.catalogRepository, c.libraryRepository, c.rulesRepository)
    }
    val navigator = LocalNavigator.current
    val actions = LocalContentActions.current
    val messenger = LocalMessenger.current
    val pinGate = LocalPinGate.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val countries by vm.countries.collectAsStateWithLifecycle()
    val kinds by vm.kinds.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val items = vm.items.collectAsLazyPagingItems()
    val isTv = AkaaTheme.isTv
    val d = AkaaTheme.dimens
    val c = AkaaTheme.colors

    var showSort by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var creatingGroup by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf<Category?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    val total = categories?.sumOf { it.count }
    val selectCategory: (Category) -> Unit = { cat ->
        if (cat.isLocked) pinGate.requirePin(R.string.pin_content_locked) { vm.select(BrowseSelection.Category(cat.id, cat.name)) }
        else vm.select(BrowseSelection.Category(cat.id, cat.name))
    }

    val openItem: (PosterItem) -> Unit = { p ->
        if (type == ContentType.MOVIE) navigator.openMovie(p.id, p.categoryId) else navigator.openSeries(p.id, p.categoryId)
    }

    val grid: @Composable (Modifier) -> Unit = { modifier ->
        if (categories == null) {
            LoadingState(modifier)
        } else if (items.itemCount == 0 && items.loadState.refresh !is androidx.paging.LoadState.Loading) {
            MessageState(
                icon = Icons.Default.ViewModule,
                title = stringResource(R.string.empty_results_title),
                message = stringResource(if (search.isNotBlank()) R.string.empty_search_message else R.string.empty_section_message),
                modifier = modifier,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTv) d.posterWidth + 8.dp else 104.dp),
                modifier = modifier,
                contentPadding = PaddingValues(start = if (isTv) 20.dp else d.gutter, end = d.gutter, top = 12.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(d.itemSpacing),
                verticalArrangement = Arrangement.spacedBy(d.itemSpacing + 4.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "${selection.title()}  ·  ${stringResource(R.string.items_count, items.itemCount)}",
                        style = MaterialTheme.typography.titleMedium, color = c.textSecondary,
                    )
                }
                items(count = items.itemCount, key = { i -> items.peek(i)?.let { "${it.ref.type}-${it.id}" } ?: "i$i" }) { i ->
                    val p = items[i]
                    if (p != null) {
                        PosterCard(
                            title = p.title,
                            imageUrl = p.image,
                            width = null,
                            quality = p.quality,
                            rating = p.rating,
                            subtitle = p.year?.toString(),
                            isFavorite = p.isFavorite,
                            locked = p.locked,
                            onClick = { openItem(p) },
                            onLongClick = {
                                val catName = categories?.firstOrNull { it.id == p.categoryId }?.name
                                actions.showMenu(p.ref, p.categoryId, catName) { openItem(p) }
                            },
                        )
                    }
                }
            }
        }
    }

    if (isTv) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.width(d.sidebarWidth).fillMaxHeight().background(c.backgroundSecondary.copy(alpha = 0.6f)),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    AkaaTextField(
                        value = search, onValueChange = vm::setSearch,
                        label = stringResource(R.string.search_in_section),
                        leadingIcon = Icons.Default.Search, imeAction = ImeAction.Search,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    FilterRow(stringResource(R.string.sort_by, sort.label()), false, { showSort = true }, icon = Icons.AutoMirrored.Filled.Sort)
                }
                item { SidebarTitle(stringResource(R.string.my_groups)) }
                item { FilterRow(stringResource(R.string.nav_favorites), selection == BrowseSelection.Favorites, { vm.select(BrowseSelection.Favorites) }, icon = Icons.Default.Favorite) }
                items(groups, key = { "g${it.id}" }) { g ->
                    FilterRow(g.name, (selection as? BrowseSelection.Group)?.id == g.id, { vm.select(BrowseSelection.Group(g.id, g.name)) }, icon = Icons.Default.Layers, count = g.itemCount)
                }
                item { FilterRow(stringResource(R.string.action_create_group), false, { creatingGroup = true }, icon = Icons.Default.Add) }
                item { HorizontalDivider(color = c.border, modifier = Modifier.padding(vertical = 6.dp)) }
                item {
                    FilterRow(
                        stringResource(if (type == ContentType.MOVIE) R.string.filter_all_movies else R.string.filter_all_series),
                        selection == BrowseSelection.All, { vm.select(BrowseSelection.All) }, icon = Icons.Default.ViewModule, count = total, focusRequester = firstFocus,
                    )
                }
                item { FilterRow(stringResource(R.string.filter_most_watched), selection == BrowseSelection.MostWatched, { vm.select(BrowseSelection.MostWatched) }, icon = Icons.Default.LocalFireDepartment) }
                item { FilterRow(stringResource(R.string.filter_latest), selection == BrowseSelection.Latest, { vm.select(BrowseSelection.Latest) }, icon = Icons.Default.AutoAwesome) }
                if (kinds.isNotEmpty()) {
                    item { SidebarTitle(stringResource(R.string.quick_filters)) }
                    items(kinds.entries.toList(), key = { "k${it.key}" }) { (kind, ids) ->
                        FilterRow(kind.label(), (selection as? BrowseSelection.Kind)?.kind == kind, { vm.select(BrowseSelection.Kind(kind, ids)) }, icon = Icons.Default.Category)
                    }
                }
                if (countries.isNotEmpty()) {
                    item { SidebarTitle(stringResource(R.string.filter_countries)) }
                    items(countries.entries.toList(), key = { "c${it.key}" }) { (code, ids) ->
                        FilterRow(code, (selection as? BrowseSelection.Country)?.code == code, { vm.select(BrowseSelection.Country(code, ids)) }, icon = Icons.Default.Public)
                    }
                }
                item { SidebarTitle(stringResource(R.string.filter_genres)) }
                items(categories.orEmpty(), key = { "cat${it.id}" }) { cat ->
                    FilterRow(
                        cat.name, (selection as? BrowseSelection.Category)?.id == cat.id, { selectCategory(cat) },
                        icon = Icons.Default.Folder, count = cat.count, locked = cat.isLocked, onLongClick = { categoryMenu = cat },
                    )
                }
            }
            grid(Modifier.weight(1f).fillMaxHeight())
        }
        LaunchedEffect(categories != null) { if (categories != null) runCatching { firstFocus.requestFocus() } }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = d.gutter, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (type == ContentType.MOVIE) R.string.nav_movies else R.string.nav_series),
                    style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.weight(1f),
                )
                AkaaIconButton(if (searchOpen) Icons.Default.Close else Icons.Default.Search, stringResource(R.string.nav_search), {
                    if (searchOpen) vm.setSearch("")
                    searchOpen = !searchOpen
                }, size = 40.dp)
                Spacer(Modifier.width(6.dp))
                AkaaIconButton(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort_title), { showSort = true }, size = 40.dp)
                Spacer(Modifier.width(6.dp))
                AkaaIconButton(Icons.Default.Category, stringResource(R.string.filter_genres), { showCategories = true }, size = 40.dp)
            }
            if (searchOpen) {
                AkaaTextField(
                    value = search, onValueChange = vm::setSearch, label = stringResource(R.string.search_in_section),
                    leadingIcon = Icons.Default.Search, imeAction = ImeAction.Search,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = d.gutter),
                )
            }
            LazyRow(contentPadding = PaddingValues(horizontal = d.gutter, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChipItem(stringResource(R.string.filter_all), selection == BrowseSelection.All, { vm.select(BrowseSelection.All) }, count = total) }
                item { FilterChipItem(stringResource(R.string.filter_latest), selection == BrowseSelection.Latest, { vm.select(BrowseSelection.Latest) }) }
                item { FilterChipItem(stringResource(R.string.filter_most_watched), selection == BrowseSelection.MostWatched, { vm.select(BrowseSelection.MostWatched) }) }
                item { FilterChipItem(stringResource(R.string.nav_favorites), selection == BrowseSelection.Favorites, { vm.select(BrowseSelection.Favorites) }) }
                items(groups, key = { "g${it.id}" }) { g ->
                    FilterChipItem(g.name, (selection as? BrowseSelection.Group)?.id == g.id, { vm.select(BrowseSelection.Group(g.id, g.name)) }, count = g.itemCount)
                }
                items(kinds.entries.toList(), key = { "k${it.key}" }) { (kind, ids) ->
                    FilterChipItem(kind.label(), (selection as? BrowseSelection.Kind)?.kind == kind, { vm.select(BrowseSelection.Kind(kind, ids)) })
                }
                items(categories.orEmpty().take(40), key = { "cat${it.id}" }) { cat ->
                    FilterChipItem(cat.name, (selection as? BrowseSelection.Category)?.id == cat.id, { selectCategory(cat) }, onLongClick = { categoryMenu = cat }, count = cat.count)
                }
            }
            grid(Modifier.weight(1f).fillMaxWidth())
        }
    }

    if (showSort) {
        ChoiceDialog(
            title = stringResource(R.string.sort_title),
            options = SortOrder.entries.toList(),
            selected = sort,
            label = { it.label() },
            onSelect = vm::setSort,
            onDismiss = { showSort = false },
        )
    }
    if (showCategories) {
        val all = buildList<Pair<String, BrowseSelection>> {
            countries.forEach { (code, ids) -> add("🌐 $code" to BrowseSelection.Country(code, ids)) }
            categories.orEmpty().forEach { add(it.name to BrowseSelection.Category(it.id, it.name)) }
        }
        ChoiceDialog(
            title = stringResource(R.string.filter_genres),
            options = all,
            selected = all.firstOrNull { it.second == selection },
            label = { it.first },
            onSelect = { pair ->
                val cat = (pair.second as? BrowseSelection.Category)?.let { s -> categories?.firstOrNull { it.id == s.id } }
                if (cat != null) selectCategory(cat) else vm.select(pair.second)
            },
            onDismiss = { showCategories = false },
        )
    }
    if (creatingGroup) {
        TextInputDialog(
            title = stringResource(R.string.action_create_group),
            label = stringResource(R.string.group_name),
            initial = "",
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name ->
                vm.createGroup(name) { ok -> messenger.show(if (ok) R.string.msg_group_created else R.string.error_group_name) }
                creatingGroup = false
            },
            onDismiss = { creatingGroup = false },
        )
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
private fun SidebarTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = AkaaTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 4.dp),
    )
}
