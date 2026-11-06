package com.akaaflix.prime.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.akaaflix.prime.common.AppError
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.common.CategoryClassifier
import com.akaaflix.prime.common.CategoryKind
import com.akaaflix.prime.common.ContentText
import com.akaaflix.prime.common.TimeFormat
import com.akaaflix.prime.database.AppDatabase
import com.akaaflix.prime.domain.model.Category
import com.akaaflix.prime.domain.model.Channel
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.model.Episode
import com.akaaflix.prime.domain.model.Movie
import com.akaaflix.prime.domain.model.MovieDetails
import com.akaaflix.prime.domain.model.RuleRestriction
import com.akaaflix.prime.domain.model.RuleScope
import com.akaaflix.prime.domain.model.Season
import com.akaaflix.prime.domain.model.Series
import com.akaaflix.prime.domain.model.SeriesDetails
import com.akaaflix.prime.domain.repository.BrowseQuery
import com.akaaflix.prime.domain.repository.CatalogRepository
import com.akaaflix.prime.domain.repository.PlaylistRepository
import com.akaaflix.prime.network.XtreamDataSource
import com.akaaflix.prime.network.dto.flexDouble
import com.akaaflix.prime.network.dto.flexLong
import com.akaaflix.prime.network.dto.flexString
import com.akaaflix.prime.network.dto.flexStringList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class CatalogRepositoryImpl(
    private val db: AppDatabase,
    private val playlists: PlaylistRepository,
    private val xtream: XtreamDataSource,
) : CatalogRepository {

    private val dao = db.catalogDao()
    private val rulesDao = db.hiddenContentDao()

    private val movieCache = LruMap<String, MovieDetails>(64)
    private val seriesCache = LruMap<String, SeriesDetails>(32)

    private val pagingConfig = PagingConfig(pageSize = 60, prefetchDistance = 30, initialLoadSize = 120, enablePlaceholders = false)

    /** Hidden keyword rules drive the SQL, so a change rebuilds the query. */
    private fun hiddenKeywords(playlistId: Long): Flow<List<String>> =
        rulesDao.observe(playlistId, RuleRestriction.HIDDEN.name)
            .map { rules -> rules.filter { it.scope == RuleScope.KEYWORD.name }.map { it.targetId }.sorted() }
            .distinctUntilChanged()

    override fun observeCategories(playlistId: Long, type: ContentType, includeAdult: Boolean): Flow<List<Category>> =
        hiddenKeywords(playlistId).flatMapLatest { keywords ->
            combine(
                dao.observeCategories(playlistId, type.name),
                dao.observeCategoryCounts(CatalogQueryBuilder.categoryCounts(playlistId, type, includeAdult, keywords)),
                rulesDao.observe(playlistId),
            ) { cats, counts, rules ->
                val countMap = counts.associate { (it.categoryId ?: "") to it.count }
                val hidden = rules.filter { it.scope == RuleScope.CATEGORY.name && it.restriction == RuleRestriction.HIDDEN.name && it.contentType == type.name }
                    .map { it.targetId }.toSet()
                val locked = rules.filter { it.scope == RuleScope.CATEGORY.name && it.restriction == RuleRestriction.PIN_LOCKED.name && it.contentType == type.name }
                    .map { it.targetId }.toSet()
                cats.filter { it.categoryId !in hidden && (includeAdult || !it.isAdult) }
                    .map { Category(it.categoryId, it.name, type, countMap[it.categoryId] ?: 0, it.isAdult, it.categoryId in locked) }
            }
        }

    override suspend fun allCategories(playlistId: Long, type: ContentType): List<Category> =
        dao.getCategories(playlistId, type.name).map { Category(it.categoryId, it.name, type, 0, it.isAdult) }

    private fun <E : Any, D : Any> paged(q: BrowseQuery, source: (androidx.sqlite.db.SimpleSQLiteQuery) -> androidx.paging.PagingSource<Int, E>, map: (E) -> D): Flow<PagingData<D>> =
        hiddenKeywords(q.playlistId).flatMapLatest { keywords ->
            Pager(pagingConfig) { source(CatalogQueryBuilder.select(q, keywords)) }.flow.map { data -> data.map(map) }
        }

    override fun channelsPaged(query: BrowseQuery) = paged(query.copy(type = ContentType.LIVE), dao::channelsPaging) { it.toDomain() }
    override fun moviesPaged(query: BrowseQuery) = paged(query.copy(type = ContentType.MOVIE), dao::moviesPaging) { it.toDomain() }
    override fun seriesPaged(query: BrowseQuery) = paged(query.copy(type = ContentType.SERIES), dao::seriesPaging) { it.toDomain() }

    override fun channels(query: BrowseQuery): Flow<List<Channel>> = hiddenKeywords(query.playlistId).flatMapLatest { k ->
        dao.channelsFlow(CatalogQueryBuilder.select(query.copy(type = ContentType.LIVE), k)).map { l -> l.map { it.toDomain() } }
    }

    override fun movies(query: BrowseQuery): Flow<List<Movie>> = hiddenKeywords(query.playlistId).flatMapLatest { k ->
        dao.moviesFlow(CatalogQueryBuilder.select(query.copy(type = ContentType.MOVIE), k)).map { l -> l.map { it.toDomain() } }
    }

    override fun series(query: BrowseQuery): Flow<List<Series>> = hiddenKeywords(query.playlistId).flatMapLatest { k ->
        dao.seriesFlow(CatalogQueryBuilder.select(query.copy(type = ContentType.SERIES), k)).map { l -> l.map { it.toDomain() } }
    }

    override suspend fun channelList(query: BrowseQuery): List<Channel> {
        val keywords = rulesDao.get(query.playlistId, RuleRestriction.HIDDEN.name).filter { it.scope == RuleScope.KEYWORD.name }.map { it.targetId }
        return dao.channelsList(CatalogQueryBuilder.select(query.copy(type = ContentType.LIVE), keywords)).map { it.toDomain() }
    }

    override suspend fun getChannel(playlistId: Long, streamId: Long) = dao.getChannel(playlistId, streamId)?.toDomain()
    override suspend fun getMovie(playlistId: Long, streamId: Long) = dao.getMovie(playlistId, streamId)?.toDomain()
    override suspend fun getSeries(playlistId: Long, seriesId: Long) = dao.getSeries(playlistId, seriesId)?.toDomain()

    override fun observeCounts(playlistId: Long): Flow<Triple<Int, Int, Int>> = combine(
        dao.observeChannelCount(playlistId), dao.observeMovieCount(playlistId), dao.observeSeriesCount(playlistId),
    ) { a, b, c -> Triple(a, b, c) }

    override suspend fun categoryKinds(playlistId: Long, type: ContentType): Map<CategoryKind, List<String>> =
        dao.getCategories(playlistId, type.name).groupBy({ CategoryClassifier.classify(it.name) }, { it.categoryId })

    override suspend fun countries(playlistId: Long, type: ContentType): Map<String, List<String>> =
        dao.getCategories(playlistId, type.name)
            .mapNotNull { c -> CategoryClassifier.countryCode(c.name)?.let { it to c.categoryId } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.isNotEmpty() }
            .toSortedMap()

    override fun clearMemoryCache() {
        movieCache.clear()
        seriesCache.clear()
    }

    // ------------------------------------------------------------------ Details

    override suspend fun movieDetails(playlistId: Long, streamId: Long, forceRefresh: Boolean): AppResult<MovieDetails> {
        val key = "$playlistId:$streamId"
        if (!forceRefresh) movieCache[key]?.let { return AppResult.Success(it) }
        val movie = getMovie(playlistId, streamId) ?: return AppResult.Failure(AppError.NotFound)
        val creds = playlists.credentials(playlistId) ?: return AppResult.Failure(AppError.NoActivePlaylist)
        return when (val r = xtream.vodInfo(creds, streamId)) {
            is AppResult.Failure -> {
                // Still allow playback with the catalog data when the details call fails.
                if (r.error is AppError.NotFound || r.error is AppError.InvalidResponse) AppResult.Success(basicDetails(movie)) else r
            }
            is AppResult.Success -> {
                val info = r.data.info as? JsonObject
                val data = r.data.movieData as? JsonObject
                val details = MovieDetails(
                    movie = movie.copy(
                        containerExtension = data?.get("container_extension").flexString() ?: movie.containerExtension,
                        poster = movie.poster ?: info?.get("movie_image").flexString(),
                        rating = movie.rating ?: info?.get("rating").flexDouble()?.takeIf { it in 0.1..10.0 },
                        year = movie.year ?: ContentText.yearOf(info?.get("releasedate").flexString(), info?.get("release_date").flexString()),
                    ),
                    plot = info.str("plot", "description", "overview"),
                    genre = info.str("genre"),
                    cast = info.str("cast", "actors"),
                    director = info.str("director"),
                    releaseDate = info.str("releasedate", "release_date", "releaseDate"),
                    durationSec = info?.get("duration_secs").flexLong()?.takeIf { it > 0 }
                        ?: TimeFormat.parseDurationToSeconds(info.str("duration", "episode_run_time")),
                    backdrop = info?.get("backdrop_path").flexStringList().firstOrNull { it.startsWith("http") }
                        ?: info.str("cover_big", "movie_image"),
                    ageRating = info.str("age", "mpaa_rating", "mpaa", "certification", "rating_mpaa"),
                    country = info.str("country"),
                    trailer = info.str("youtube_trailer", "trailer"),
                )
                movieCache[key] = details
                AppResult.Success(details)
            }
        }
    }

    private fun basicDetails(movie: Movie) = MovieDetails(movie, null, null, null, null, null, null, null, null, null, null)

    override suspend fun seriesDetails(playlistId: Long, seriesId: Long, forceRefresh: Boolean): AppResult<SeriesDetails> {
        val key = "$playlistId:$seriesId"
        if (!forceRefresh) seriesCache[key]?.let { return AppResult.Success(it) }
        val series = getSeries(playlistId, seriesId) ?: return AppResult.Failure(AppError.NotFound)
        val creds = playlists.credentials(playlistId) ?: return AppResult.Failure(AppError.NoActivePlaylist)
        return when (val r = xtream.seriesInfo(creds, seriesId)) {
            is AppResult.Failure -> r
            is AppResult.Success -> {
                val info = r.data.info as? JsonObject
                val episodes = parseEpisodes(seriesId, r.data.episodes)
                val seasons = parseSeasons(r.data.seasons, episodes)
                val details = SeriesDetails(
                    series = series.copy(
                        plot = series.plot ?: info.str("plot", "description"),
                        genre = series.genre ?: info.str("genre"),
                        backdrop = series.backdrop ?: info?.get("backdrop_path").flexStringList().firstOrNull { it.startsWith("http") },
                        cover = series.cover ?: info.str("cover"),
                    ),
                    cast = info.str("cast", "actors"),
                    director = info.str("director"),
                    releaseDate = info.str("releaseDate", "release_date", "releasedate"),
                    ageRating = info.str("age", "mpaa_rating", "certification"),
                    seasons = seasons,
                    episodesBySeason = episodes,
                )
                seriesCache[key] = details
                AppResult.Success(details)
            }
        }
    }

    internal fun parseEpisodes(seriesId: Long, element: JsonElement?): Map<Int, List<Episode>> {
        val raw = mutableListOf<Pair<Int?, JsonObject>>()
        when (element) {
            is JsonObject -> element.forEach { (seasonKey, value) ->
                val season = seasonKey.toIntOrNull()
                when (value) {
                    is JsonArray -> value.forEach { (it as? JsonObject)?.let { ep -> raw += season to ep } }
                    is JsonObject -> value.values.forEach { (it as? JsonObject)?.let { ep -> raw += season to ep } }
                    else -> Unit
                }
            }
            is JsonArray -> element.forEach { item ->
                when (item) {
                    is JsonArray -> item.forEach { (it as? JsonObject)?.let { ep -> raw += null to ep } }
                    is JsonObject -> raw += null to item
                    else -> Unit
                }
            }
            else -> Unit
        }
        return raw.mapNotNull { (seasonKey, ep) ->
            val id = ep["id"].flexString() ?: return@mapNotNull null
            val info = ep["info"] as? JsonObject
            val season = ep["season"].flexLong()?.toInt() ?: seasonKey ?: 1
            val number = ep["episode_num"].flexLong()?.toInt() ?: 0
            Episode(
                id = id,
                seriesId = seriesId,
                season = season,
                episodeNumber = number,
                title = ep["title"].flexString() ?: info.str("name") ?: "E$number",
                plot = info.str("plot", "overview"),
                thumbnail = info.str("movie_image", "cover_big"),
                durationSec = info?.get("duration_secs").flexLong()?.takeIf { it > 0 } ?: TimeFormat.parseDurationToSeconds(info.str("duration")),
                containerExtension = ep["container_extension"].flexString(),
                rating = info?.get("rating").flexDouble()?.takeIf { it in 0.1..10.0 },
                releaseDate = info.str("releasedate", "air_date"),
            )
        }.distinctBy { it.id }
            .groupBy { it.season }
            .mapValues { (_, list) -> list.sortedBy { it.episodeNumber } }
            .toSortedMap()
    }

    private fun parseSeasons(element: JsonElement?, episodes: Map<Int, List<Episode>>): List<Season> {
        val declared = (element as? JsonArray)?.mapNotNull { it as? JsonObject }?.mapNotNull { s ->
            val number = s["season_number"].flexLong()?.toInt() ?: return@mapNotNull null
            Season(
                number = number,
                name = s["name"].flexString() ?: "S$number",
                cover = s["cover_big"].flexString() ?: s["cover"].flexString(),
                episodeCount = episodes[number]?.size ?: s["episode_count"].flexLong()?.toInt() ?: 0,
                overview = s["overview"].flexString(),
            )
        }.orEmpty()
        // Only keep seasons that actually have playable episodes; add seasons that only appear in episodes.
        val byNumber = declared.associateBy { it.number }
        return episodes.keys.sorted().map { n ->
            byNumber[n]?.copy(episodeCount = episodes[n]?.size ?: 0) ?: Season(n, "S$n", null, episodes[n]?.size ?: 0, null)
        }
    }

    private fun JsonObject?.str(vararg keys: String): String? {
        if (this == null) return null
        for (k in keys) this[k].flexString()?.let { return it }
        return null
    }
}

/** Tiny thread-safe LRU map for details caches. */
class LruMap<K, V>(private val max: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > max
    }

    @Synchronized operator fun get(key: K): V? = map[key]
    @Synchronized operator fun set(key: K, value: V) { map[key] = value }
    @Synchronized fun clear() = map.clear()
}
