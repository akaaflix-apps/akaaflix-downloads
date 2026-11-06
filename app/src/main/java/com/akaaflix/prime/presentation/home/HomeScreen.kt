package com.akaaflix.prime.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.akaaflix.prime.R
import com.akaaflix.prime.common.AppError
import com.akaaflix.prime.data.SessionManager
import com.akaaflix.prime.data.repository.CatalogSyncer
import com.akaaflix.prime.data.repository.SyncStage
import com.akaaflix.prime.data.repository.toRef
import com.akaaflix.prime.data.settings.SettingsRepository
import com.akaaflix.prime.domain.model.Channel
import com.akaaflix.prime.domain.model.ContentRef
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.model.Movie
import com.akaaflix.prime.domain.model.PlaybackEntry
import com.akaaflix.prime.domain.model.Playlist
import com.akaaflix.prime.domain.model.Series
import com.akaaflix.prime.domain.model.SortOrder
import com.akaaflix.prime.domain.repository.BrowseQuery
import com.akaaflix.prime.domain.repository.CatalogRepository
import com.akaaflix.prime.domain.repository.HistoryRepository
import com.akaaflix.prime.domain.repository.LibraryRepository
import com.akaaflix.prime.navigation.AppNavigator
import com.akaaflix.prime.navigation.LiveTvRoute
import com.akaaflix.prime.navigation.LocalNavigator
import com.akaaflix.prime.navigation.MoviesRoute
import com.akaaflix.prime.navigation.MyListRoute
import com.akaaflix.prime.navigation.SeriesRoute
import com.akaaflix.prime.presentation.components.AkaaCard
import com.akaaflix.prime.presentation.components.ChannelCard
import com.akaaflix.prime.presentation.components.ContinueWatchingCard
import com.akaaflix.prime.presentation.components.ErrorState
import com.akaaflix.prime.presentation.components.HeroBanner
import com.akaaflix.prime.presentation.components.HeroItem
import com.akaaflix.prime.presentation.components.LoadingState
import com.akaaflix.prime.presentation.components.MessageState
import com.akaaflix.prime.presentation.components.LocalContentActions
import com.akaaflix.prime.presentation.components.PosterCard
import com.akaaflix.prime.presentation.components.SectionHeader
import com.akaaflix.prime.presentation.components.SectionTile
import com.akaaflix.prime.presentation.components.akaaViewModel
import com.akaaflix.prime.presentation.theme.AkaaTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** (playlistId, includeAdult) — the inputs every home row depends on. */
private data class HomeKey(val playlistId: Long, val includeAdult: Boolean)

data class SyncUi(val running: Boolean, val stage: SyncStage?, val error: AppError?)

class HomeViewModel(
    session: SessionManager,
    settings: SettingsRepository,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val history: HistoryRepository,
    syncer: CatalogSyncer,
) : ViewModel() {
    val playlist: StateFlow<Playlist?> = session.activePlaylist

    private val key: Flow<HomeKey> = combine(session.activePlaylist.filterNotNull(), settings.settings, session.adultUnlocked) { p, s, unlocked ->
        HomeKey(p.id, !s.shouldHideAdult || unlocked)
    }.distinctUntilChanged()

    private fun <T> row(initial: T, block: (HomeKey) -> Flow<T>): StateFlow<T> =
        key.flatMapLatest(block).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val sync: StateFlow<SyncUi> = combine(session.activePlaylist, syncer.progress) { p, map ->
        val prog = p?.let { map[it.id] }
        SyncUi(prog?.running == true, prog?.stage, prog?.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUi(false, null, null))

    val counts: StateFlow<Triple<Int, Int, Int>?> = row(null) { k -> catalog.observeCounts(k.playlistId).map { it } }

    private val favoriteMovies = key.flatMapLatest { library.observeFavoriteIds(it.playlistId, ContentType.MOVIE) }
    private val favoriteSeries = key.flatMapLatest { library.observeFavoriteIds(it.playlistId, ContentType.SERIES) }

    val latestMovies: StateFlow<List<Movie>> = row(emptyList()) { k -> catalog.movies(BrowseQuery(k.playlistId, ContentType.MOVIE, sort = SortOrder.LATEST, includeAdult = k.includeAdult, limit = 24)) }
    val latestSeries: StateFlow<List<Series>> = row(emptyList()) { k -> catalog.series(BrowseQuery(k.playlistId, ContentType.SERIES, sort = SortOrder.LATEST, includeAdult = k.includeAdult, limit = 24)) }
    val mostWatched: StateFlow<List<Movie>> = row(emptyList()) { k -> catalog.movies(BrowseQuery(k.playlistId, ContentType.MOVIE, mostWatched = true, includeAdult = k.includeAdult, limit = 20)) }
    val topRatedMovies: StateFlow<List<Movie>> = row(emptyList()) { k -> catalog.movies(BrowseQuery(k.playlistId, ContentType.MOVIE, sort = SortOrder.RATING, includeAdult = k.includeAdult, limit = 60)) }
    val popularSeries: StateFlow<List<Series>> = row(emptyList()) { k -> catalog.series(BrowseQuery(k.playlistId, ContentType.SERIES, sort = SortOrder.RATING, includeAdult = k.includeAdult, limit = 20)) }
    val continueWatching: StateFlow<List<PlaybackEntry>> = row(emptyList()) { k -> history.observeContinueWatching(k.playlistId) }
    private val recentHistory: StateFlow<List<PlaybackEntry>> = row(emptyList()) { k -> history.observeHistory(k.playlistId, 200) }

    /** Recently watched channels first; otherwise the provider's first channels. */
    val channels: StateFlow<List<Channel>> = row(emptyList()) { k ->
        history.observeHistory(k.playlistId, 60).flatMapLatest { entries ->
            val ids = entries.filter { it.type == ContentType.LIVE }.map { it.contentId }
            catalog.channels(BrowseQuery(k.playlistId, ContentType.LIVE, includeAdult = k.includeAdult, limit = 40)).map { list ->
                val byId = list.associateBy { it.streamId.toString() }
                val recent = ids.mapNotNull { id -> byId[id] }
                (recent + list).distinctBy { it.streamId }.take(20)
            }
        }
    }

    /**
     * Recommendations: highest-rated movies from the categories the user watches most,
     * excluding titles already watched. Falls back to top rated when there is no history.
     */
    val recommended: StateFlow<List<Movie>> = combine(topRatedMovies, recentHistory) { top, hist ->
        val watched = hist.filter { it.type == ContentType.MOVIE }
        val watchedIds = watched.map { it.contentId }.toSet()
        val favCats = watched.mapNotNull { it.categoryId }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key }.toSet()
        val pool = top.filter { it.streamId.toString() !in watchedIds }
        val preferred = pool.filter { it.categoryId in favCats }
        (preferred + pool).distinctBy { it.streamId }.take(20)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hero: StateFlow<List<HeroItem>> = combine(latestMovies, latestSeries, favoriteMovies, favoriteSeries) { movies, series, favM, favS ->
        val m = movies.filter { it.poster != null }.take(4).map {
            HeroItem(it.playlistId, ContentType.MOVIE, it.streamId, it.name, null, it.poster, it.year, it.rating, null, it.quality, it.streamId.toString() in favM, it.categoryId)
        }
        val s = series.filter { it.backdrop != null || it.cover != null }.take(3).map {
            HeroItem(it.playlistId, ContentType.SERIES, it.seriesId, it.name, it.plot, it.backdrop ?: it.cover, it.year, it.rating, it.genre, it.quality, it.seriesId.toString() in favS, it.categoryId)
        }
        (s.zip(m) { a, b -> listOf(a, b) }.flatten() + s.drop(m.size) + m.drop(s.size)).take(6)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteMovieIds: StateFlow<Set<String>> = favoriteMovies.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val favoriteSeriesIds: StateFlow<Set<String>> = favoriteSeries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
}

@Composable
fun HomeScreen() {
    val vm = akaaViewModel { c, _ ->
        HomeViewModel(c.sessionManager, c.settingsRepository, c.catalogRepository, c.libraryRepository, c.historyRepository, c.catalogSyncer)
    }
    val navigator = LocalNavigator.current
    val actions = LocalContentActions.current
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val hero by vm.hero.collectAsStateWithLifecycle()
    val continueWatching by vm.continueWatching.collectAsStateWithLifecycle()
    val latestMovies by vm.latestMovies.collectAsStateWithLifecycle()
    val latestSeries by vm.latestSeries.collectAsStateWithLifecycle()
    val mostWatched by vm.mostWatched.collectAsStateWithLifecycle()
    val recommended by vm.recommended.collectAsStateWithLifecycle()
    val popularSeries by vm.popularSeries.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()
    val favM by vm.favoriteMovieIds.collectAsStateWithLifecycle()
    val favS by vm.favoriteSeriesIds.collectAsStateWithLifecycle()
    val d = AkaaTheme.dimens
    val c = AkaaTheme.colors
    val isTv = AkaaTheme.isTv

    val totalItems = counts?.let { it.first + it.second + it.third }
    if (playlist == null) {
        LoadingState()
        return
    }
    if (totalItems == 0 || totalItems == null) {
        val p = playlist!!
        when {
            totalItems == null || sync.running -> LoadingState(message = syncMessage(sync.stage))
            sync.error != null -> ErrorState(sync.error!!, onRetry = { navigator.retrySync(p.id) })
            p.lastRefreshAt == null -> {
                // Never downloaded (e.g. the app was closed during the first sync).
                LoadingState(message = syncMessage(sync.stage))
                LaunchedSyncIfNeeded(navigator, p)
            }
            else -> MessageState(
                icon = Icons.Default.LiveTv,
                title = stringResource(R.string.home_empty_title),
                message = stringResource(R.string.home_empty_message),
                actionLabel = stringResource(R.string.action_refresh),
                onAction = { navigator.retrySync(p.id) },
            )
        }
        return
    }

    val rowPadding = PaddingValues(horizontal = d.gutter)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp, top = if (isTv) 8.dp else 4.dp)) {
        if (sync.running) item {
            Row(Modifier.padding(horizontal = d.gutter, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = c.accent)
                Spacer(Modifier.width(8.dp))
                Text(syncMessage(sync.stage), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
        item {
            HeroBanner(
                items = hero,
                label = stringResource(R.string.home_hero_label),
                onWatch = { h -> if (h.type == ContentType.MOVIE) navigator.playMovie(h.id, h.categoryId) else navigator.openSeries(h.id, h.categoryId) },
                onOpen = { h -> if (h.type == ContentType.MOVIE) navigator.openMovie(h.id, h.categoryId) else navigator.openSeries(h.id, h.categoryId) },
                onToggleFavorite = { h -> actions.toggleFavorite(ContentRef(h.playlistId, h.type, h.id.toString(), h.title, h.imageUrl, h.categoryId)) },
                onAddToGroup = { h -> actions.openGroupPicker(ContentRef(h.playlistId, h.type, h.id.toString(), h.title, h.imageUrl, h.categoryId)) },
                modifier = Modifier.padding(horizontal = if (isTv) d.gutter / 2 else 12.dp),
            )
        }
        item {
            Spacer(Modifier.height(d.sectionSpacing))
            SectionTiles(navigator, counts)
        }
        if (!isTv) item { QuickActions(navigator) }

        if (continueWatching.isNotEmpty()) item {
            RowSection(stringResource(R.string.home_continue_watching)) {
                LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(d.itemSpacing)) {
                    items(continueWatching, key = { "${it.type}-${it.contentId}" }) { e ->
                        ContinueWatchingCard(
                            title = e.title,
                            subtitle = e.subtitle,
                            imageUrl = e.imageUrl,
                            progress = e.progress,
                            onClick = { resume(navigator, e) },
                        )
                    }
                }
            }
        }
        val latestMix = (latestMovies.take(12).map { it.toPoster(favM) } + latestSeries.take(12).map { it.toPoster(favS) })
            .sortedByDescending { it.added }
        if (latestMix.isNotEmpty()) item {
            RowSection(stringResource(R.string.home_latest), stringResource(R.string.action_see_all), { navigator.navigateTopLevel(MoviesRoute()) }) {
                PosterRow(latestMix, navigator)
            }
        }
        if (mostWatched.isNotEmpty()) item {
            RowSection(stringResource(R.string.home_most_watched)) { PosterRow(mostWatched.map { it.toPoster(favM) }, navigator) }
        }
        if (recommended.isNotEmpty()) item {
            RowSection(stringResource(R.string.home_recommended), stringResource(R.string.action_see_all), { navigator.navigateTopLevel(MoviesRoute()) }) {
                PosterRow(recommended.map { it.toPoster(favM) }, navigator)
            }
        }
        if (popularSeries.isNotEmpty()) item {
            RowSection(stringResource(R.string.home_popular_series), stringResource(R.string.action_see_all), { navigator.navigateTopLevel(SeriesRoute()) }) {
                PosterRow(popularSeries.map { it.toPoster(favS) }, navigator)
            }
        }
        if (channels.isNotEmpty()) item {
            RowSection(stringResource(R.string.home_live_channels), stringResource(R.string.action_see_all), { navigator.navigateTopLevel(LiveTvRoute()) }) {
                LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(d.itemSpacing)) {
                    items(channels, key = { it.streamId }) { ch ->
                        ChannelCard(
                            name = ch.name, logo = ch.logo, number = ch.number,
                            onClick = { navigator.playChannel(ch.streamId, ch.categoryId) },
                            onLongClick = { actions.showMenu(ch.toRef(), ch.categoryId, null) { navigator.playChannel(ch.streamId, ch.categoryId) } },
                            modifier = Modifier.width(d.channelCardWidth),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchedSyncIfNeeded(navigator: AppNavigator, playlist: Playlist) {
    androidx.compose.runtime.LaunchedEffect(playlist.id) { navigator.retrySync(playlist.id) }
}

@Composable
private fun syncMessage(stage: SyncStage?): String = stringResource(
    when (stage) {
        SyncStage.LIVE -> R.string.sync_live
        SyncStage.MOVIES -> R.string.sync_movies
        SyncStage.SERIES -> R.string.sync_series
        else -> R.string.sync_connecting
    },
)

private fun resume(navigator: AppNavigator, e: PlaybackEntry) {
    when (e.type) {
        ContentType.MOVIE -> navigator.playMovie(e.contentId.toLong(), e.categoryId, e.positionMs)
        ContentType.EPISODE -> e.seriesId?.let { navigator.playEpisode(it, e.contentId, e.categoryId, e.positionMs) }
        ContentType.LIVE -> navigator.playChannel(e.contentId.toLong(), e.categoryId)
        ContentType.SERIES -> navigator.openSeries(e.contentId.toLong(), e.categoryId)
    }
}

/** Poster row item that can be a movie or a series. */
data class PosterItem(
    val ref: ContentRef,
    val id: Long,
    val title: String,
    val image: String?,
    val quality: String?,
    val rating: Double?,
    val year: Int?,
    val isFavorite: Boolean,
    val categoryId: String?,
    val added: Long,
    val locked: Boolean = false,
)

fun Movie.toPoster(fav: Set<String>) = PosterItem(toRef(), streamId, name, poster, quality, rating, year, streamId.toString() in fav, categoryId, added)
fun Series.toPoster(fav: Set<String>) = PosterItem(toRef(), seriesId, name, cover, quality, rating, year, seriesId.toString() in fav, categoryId, lastModified)

@Composable
fun PosterRow(items: List<PosterItem>, navigator: AppNavigator) {
    val d = AkaaTheme.dimens
    val actions = LocalContentActions.current
    LazyRow(contentPadding = PaddingValues(horizontal = d.gutter), horizontalArrangement = Arrangement.spacedBy(d.itemSpacing)) {
        items(items, key = { "${it.ref.type}-${it.id}" }) { p ->
            val open = {
                if (p.ref.type == ContentType.MOVIE) navigator.openMovie(p.id, p.categoryId) else navigator.openSeries(p.id, p.categoryId)
            }
            PosterCard(
                title = p.title,
                imageUrl = p.image,
                quality = p.quality,
                rating = p.rating,
                subtitle = p.year?.toString(),
                isFavorite = p.isFavorite,
                onClick = open,
                onLongClick = { actions.showMenu(p.ref, p.categoryId, null, open) },
            )
        }
    }
}

@Composable
private fun RowSection(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val d = AkaaTheme.dimens
    Column(Modifier.fillMaxWidth().padding(top = d.sectionSpacing)) {
        SectionHeader(title, Modifier.padding(horizontal = d.gutter), actionLabel, onAction)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun SectionTiles(navigator: AppNavigator, counts: Triple<Int, Int, Int>?) {
    val d = AkaaTheme.dimens
    val c = AkaaTheme.colors
    val isTv = AkaaTheme.isTv
    val height = if (isTv) 104.dp else 112.dp
    Row(Modifier.fillMaxWidth().padding(horizontal = d.gutter), horizontalArrangement = Arrangement.spacedBy(d.itemSpacing)) {
        SectionTile(
            stringResource(R.string.nav_live), stringResource(R.string.nav_live_en), Icons.Default.LiveTv,
            listOf(Color(0xFF8E0E24), c.red.copy(alpha = 0.85f), Color(0xFF3A0610)),
            { navigator.navigateTopLevel(LiveTvRoute()) }, Modifier.weight(1f).height(height), counts?.first,
        )
        SectionTile(
            stringResource(R.string.nav_movies), stringResource(R.string.nav_movies_en), Icons.Default.Movie,
            listOf(Color(0xFF0A2A6B), c.accent.copy(alpha = 0.85f), Color(0xFF061A3F)),
            { navigator.navigateTopLevel(MoviesRoute()) }, Modifier.weight(1f).height(height), counts?.second,
        )
        SectionTile(
            stringResource(R.string.nav_series), stringResource(R.string.nav_series_en), Icons.Default.Tv,
            listOf(Color(0xFF3B0A6B), c.purple.copy(alpha = 0.85f), Color(0xFF21063F)),
            { navigator.navigateTopLevel(SeriesRoute()) }, Modifier.weight(1f).height(height), counts?.third,
        )
    }
}

@Composable
private fun QuickActions(navigator: AppNavigator) {
    val d = AkaaTheme.dimens
    Row(Modifier.fillMaxWidth().padding(horizontal = d.gutter, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickAction(Icons.Default.Favorite, stringResource(R.string.nav_favorites), Modifier.weight(1f)) { navigator.navigateTopLevel(MyListRoute(0)) }
        QuickAction(Icons.Default.Layers, stringResource(R.string.nav_groups), Modifier.weight(1f)) { navigator.navigateTopLevel(MyListRoute(1)) }
        QuickAction(Icons.Default.History, stringResource(R.string.nav_history), Modifier.weight(1f)) { navigator.navigateTopLevel(MyListRoute(2)) }
        QuickAction(Icons.AutoMirrored.Filled.PlaylistPlay, stringResource(R.string.nav_playlists), Modifier.weight(1f)) { navigator.goToPlaylists(false, false) }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = AkaaTheme.colors
    AkaaCard(onClick = onClick, modifier = modifier.height(70.dp), containerColor = c.surface) { _ ->
        Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = c.gold, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.text, maxLines = 1)
        }
    }
}
