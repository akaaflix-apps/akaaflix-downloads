package com.akaaflix.prime.presentation.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.akaaflix.prime.common.AppError
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.data.SessionManager
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.model.MovieDetails
import com.akaaflix.prime.domain.model.PlaybackEntry
import com.akaaflix.prime.domain.model.SeriesDetails
import com.akaaflix.prime.domain.repository.CatalogRepository
import com.akaaflix.prime.domain.repository.HistoryRepository
import com.akaaflix.prime.domain.repository.LibraryRepository
import com.akaaflix.prime.navigation.MovieDetailsRoute
import com.akaaflix.prime.navigation.SeriesDetailsRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailsState<out T> {
    data object Loading : DetailsState<Nothing>
    data class Error(val error: AppError) : DetailsState<Nothing>
    data class Content<T>(val data: T) : DetailsState<T>
}

class MovieDetailsViewModel(
    handle: SavedStateHandle,
    private val session: SessionManager,
    private val catalog: CatalogRepository,
    library: LibraryRepository,
    history: HistoryRepository,
) : ViewModel() {
    val streamId = handle.toRoute<MovieDetailsRoute>().streamId
    private val _state = MutableStateFlow<DetailsState<MovieDetails>>(DetailsState.Loading)
    val state: StateFlow<DetailsState<MovieDetails>> = _state.asStateFlow()

    private val playlistId: Flow<Long> = session.activePlaylist.filterNotNull().map { it.id }

    val isFavorite: StateFlow<Boolean> = playlistId.flatMapLatest { library.isFavorite(it, ContentType.MOVIE, streamId.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val resume: StateFlow<PlaybackEntry?> = playlistId.flatMapLatest { pid ->
        history.observeHistory(pid, 300).map { list -> list.firstOrNull { it.type == ContentType.MOVIE && it.contentId == streamId.toString() } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { load(false) }

    fun load(force: Boolean) = viewModelScope.launch {
        _state.value = DetailsState.Loading
        val pid = playlistId.first()
        _state.value = when (val r = catalog.movieDetails(pid, streamId, force)) {
            is AppResult.Success -> DetailsState.Content(r.data)
            is AppResult.Failure -> DetailsState.Error(r.error)
        }
    }
}

class SeriesDetailsViewModel(
    handle: SavedStateHandle,
    private val session: SessionManager,
    private val catalog: CatalogRepository,
    library: LibraryRepository,
    private val history: HistoryRepository,
) : ViewModel() {
    val seriesId = handle.toRoute<SeriesDetailsRoute>().seriesId
    private val _state = MutableStateFlow<DetailsState<SeriesDetails>>(DetailsState.Loading)
    val state: StateFlow<DetailsState<SeriesDetails>> = _state.asStateFlow()
    private val _season = MutableStateFlow<Int?>(null)
    val season: StateFlow<Int?> = _season.asStateFlow()

    private val playlistId: Flow<Long> = session.activePlaylist.filterNotNull().map { it.id }

    val isFavorite: StateFlow<Boolean> = playlistId.flatMapLatest { library.isFavorite(it, ContentType.SERIES, seriesId.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Watch progress per episode id. */
    val episodeProgress: StateFlow<Map<String, PlaybackEntry>> = playlistId.flatMapLatest { pid ->
        history.observeHistory(pid, 1000).map { list -> list.filter { it.type == ContentType.EPISODE && it.seriesId == seriesId }.associateBy { it.contentId } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Last watched episode of this series (for "Resume"). */
    val lastEpisode: StateFlow<PlaybackEntry?> = episodeProgress.map { m -> m.values.maxByOrNull { it.lastWatchedAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { load(false) }

    fun load(force: Boolean) = viewModelScope.launch {
        _state.value = DetailsState.Loading
        val pid = playlistId.first()
        when (val r = catalog.seriesDetails(pid, seriesId, force)) {
            is AppResult.Success -> {
                _state.value = DetailsState.Content(r.data)
                if (_season.value == null) {
                    val last = history.lastEpisodeOf(pid, seriesId)?.season
                    _season.value = last?.takeIf { it in r.data.episodesBySeason } ?: r.data.episodesBySeason.keys.minOrNull()
                }
            }
            is AppResult.Failure -> _state.value = DetailsState.Error(r.error)
        }
    }

    fun selectSeason(n: Int) { _season.value = n }
}
