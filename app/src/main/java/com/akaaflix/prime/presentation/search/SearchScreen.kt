package com.akaaflix.prime.presentation.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.akaaflix.prime.R
import com.akaaflix.prime.common.ContentText
import com.akaaflix.prime.data.SessionManager
import com.akaaflix.prime.data.repository.SearchHistoryRepository
import com.akaaflix.prime.data.repository.toRef
import com.akaaflix.prime.data.settings.SettingsRepository
import com.akaaflix.prime.domain.model.Channel
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.model.CustomGroup
import com.akaaflix.prime.domain.model.FavoriteItem
import com.akaaflix.prime.domain.model.Movie
import com.akaaflix.prime.domain.model.Series
import com.akaaflix.prime.domain.repository.BrowseQuery
import com.akaaflix.prime.domain.repository.CatalogRepository
import com.akaaflix.prime.domain.repository.LibraryRepository
import com.akaaflix.prime.navigation.LocalNavigator
import com.akaaflix.prime.presentation.catalog.FilterChipItem
import com.akaaflix.prime.presentation.components.AkaaButton
import com.akaaflix.prime.presentation.components.AkaaTextField
import com.akaaflix.prime.presentation.components.ChannelCard
import com.akaaflix.prime.presentation.components.LocalContentActions
import com.akaaflix.prime.presentation.components.MessageState
import com.akaaflix.prime.presentation.components.SectionHeader
import com.akaaflix.prime.presentation.components.akaaViewModel
import com.akaaflix.prime.presentation.home.PosterRow
import com.akaaflix.prime.presentation.home.toPoster
import com.akaaflix.prime.presentation.library.open
import com.akaaflix.prime.presentation.theme.AkaaTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchResults(
    val query: String = "",
    val channels: List<Channel> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList(),
    val favorites: List<FavoriteItem> = emptyList(),
    val groups: List<CustomGroup> = emptyList(),
) {
    val isEmpty get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty() && favorites.isEmpty() && groups.isEmpty()
}

private data class SearchKey(val playlistId: Long, val includeAdult: Boolean, val query: String)

/** Global instant search across channels, movies, series, favorites and groups (hidden content excluded). */
class SearchViewModel(
    session: SessionManager,
    private val settings: SettingsRepository,
    catalog: CatalogRepository,
    library: LibraryRepository,
    private val historyRepo: SearchHistoryRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val history: StateFlow<List<String>> = historyRepo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val key: Flow<SearchKey> = combine(session.activePlaylist.filterNotNull(), settings.settings, session.adultUnlocked, _query.debounce(300)) { p, s, u, q ->
        SearchKey(p.id, !s.shouldHideAdult || u, q.trim())
    }.distinctUntilChanged()

    val results: StateFlow<SearchResults> = key.flatMapLatest { k ->
        if (k.query.length < 2) return@flatMapLatest flowOf(SearchResults(k.query))
        val q = BrowseQuery(k.playlistId, ContentType.LIVE, search = k.query, includeAdult = k.includeAdult, limit = 30)
        val norm = ContentText.normalize(k.query)
        combine(
            catalog.channels(q),
            catalog.movies(q.copy(type = ContentType.MOVIE)),
            catalog.series(q.copy(type = ContentType.SERIES)),
            library.observeFavorites(k.playlistId).map { l -> l.filter { ContentText.normalize(it.ref.title).contains(norm) } },
            library.observeGroups(k.playlistId).map { l -> l.filter { !it.isHidden && ContentText.normalize(it.name).contains(norm) } },
        ) { ch, mv, se, fav, gr -> SearchResults(k.query, ch, mv, se, fav, gr) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun setQuery(q: String) { _query.value = q }

    fun record() = viewModelScope.launch {
        if (settings.current().saveSearchHistory) historyRepo.record(_query.value)
    }

    fun clearHistory() = viewModelScope.launch { historyRepo.clear() }
    fun removeHistory(q: String) = viewModelScope.launch { historyRepo.remove(q) }
}

@Composable
fun SearchScreen() {
    val vm = akaaViewModel { c, _ -> SearchViewModel(c.sessionManager, c.settingsRepository, c.catalogRepository, c.libraryRepository, c.searchHistoryRepository) }
    val query by vm.query.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val actions = LocalContentActions.current
    val d = AkaaTheme.dimens
    val c = AkaaTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        AkaaTextField(
            value = query, onValueChange = vm::setQuery,
            label = stringResource(R.string.search_hint),
            leadingIcon = Icons.Default.Search,
            imeAction = ImeAction.Search, onImeAction = { vm.record() },
            trailing = if (query.isNotEmpty()) ({ IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Close, stringResource(R.string.action_clear)) } }) else null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = d.gutter, vertical = 10.dp).focusRequester(focus),
        )
        when {
            query.trim().length < 2 -> {
                Column(Modifier.padding(horizontal = d.gutter)) {
                    if (history.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, null, tint = c.textSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.search_recent), style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
                            AkaaButton(stringResource(R.string.action_clear_history), vm::clearHistory, icon = Icons.Default.DeleteSweep, compact = true)
                        }
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(history) { h -> FilterChipItem(h, false, { vm.setQuery(h) }, onLongClick = { vm.removeHistory(h) }) }
                        }
                    } else {
                        Text(stringResource(R.string.search_prompt), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    }
                }
            }
            results.isEmpty && results.query == query.trim() -> MessageState(Icons.Default.SearchOff, stringResource(R.string.empty_results_title), stringResource(R.string.empty_search_message))
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                if (results.channels.isNotEmpty()) item {
                    Section(stringResource(R.string.nav_live)) {
                        LazyRow(contentPadding = PaddingValues(horizontal = d.gutter), horizontalArrangement = Arrangement.spacedBy(d.itemSpacing)) {
                            items(results.channels, key = { it.streamId }) { ch ->
                                ChannelCard(
                                    ch.name, ch.logo, onClick = { vm.record(); navigator.playChannel(ch.streamId, ch.categoryId) },
                                    modifier = Modifier.width(d.channelCardWidth), number = ch.number,
                                    onLongClick = { actions.showMenu(ch.toRef(), ch.categoryId, null) { navigator.playChannel(ch.streamId, ch.categoryId) } },
                                )
                            }
                        }
                    }
                }
                if (results.movies.isNotEmpty()) item { Section(stringResource(R.string.nav_movies)) { PosterRow(results.movies.map { it.toPoster(emptySet()) }, navigator) } }
                if (results.series.isNotEmpty()) item { Section(stringResource(R.string.nav_series)) { PosterRow(results.series.map { it.toPoster(emptySet()) }, navigator) } }
                if (results.favorites.isNotEmpty()) item {
                    Section(stringResource(R.string.nav_favorites)) {
                        LazyRow(contentPadding = PaddingValues(horizontal = d.gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(results.favorites, key = { it.id }) { f -> FilterChipItem(f.ref.title, false, { vm.record(); open(navigator, f.ref) }) }
                        }
                    }
                }
                if (results.groups.isNotEmpty()) item {
                    Section(stringResource(R.string.nav_groups)) {
                        LazyRow(contentPadding = PaddingValues(horizontal = d.gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(results.groups, key = { it.id }) { g -> FilterChipItem(g.name, false, { navigator.openGroup(g.id) }, count = g.itemCount) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val d = AkaaTheme.dimens
    Column(Modifier.fillMaxWidth().padding(top = d.sectionSpacing)) {
        SectionHeader(title, Modifier.padding(horizontal = d.gutter))
        Spacer(Modifier.height(10.dp))
        content()
    }
}
