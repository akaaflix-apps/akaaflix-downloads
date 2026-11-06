package com.akaaflix.prime.data.repository

import androidx.room.withTransaction
import com.akaaflix.prime.common.AppError
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.common.CategoryClassifier
import com.akaaflix.prime.common.ContentText
import com.akaaflix.prime.database.AppDatabase
import com.akaaflix.prime.database.entity.CategoryEntity
import com.akaaflix.prime.database.entity.ChannelEntity
import com.akaaflix.prime.database.entity.MovieEntity
import com.akaaflix.prime.database.entity.SeriesEntity
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.repository.PlaylistRepository
import com.akaaflix.prime.network.XtreamDataSource
import com.akaaflix.prime.network.dto.CategoryDto
import com.akaaflix.prime.network.dto.LiveStreamDto
import com.akaaflix.prime.network.dto.SeriesDto
import com.akaaflix.prime.network.dto.VodStreamDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

enum class SyncStage { CONNECTING, LIVE, MOVIES, SERIES, DONE }

data class SyncProgress(val stage: SyncStage, val running: Boolean, val error: AppError? = null)

data class SyncSummary(
    val liveCount: Int,
    val movieCount: Int,
    val seriesCount: Int,
    val newMovies: Int,
    val newSeries: Int,
    val partialErrors: List<AppError>,
)

/**
 * Downloads the Xtream catalog (categories, live streams, VOD, series) and caches it in Room.
 * Each content type is replaced atomically, so browsing never sees a half-written table, and
 * user data (favorites, groups, hidden rules, history) lives in separate tables and is kept.
 */
class CatalogSyncer(
    private val db: AppDatabase,
    private val playlists: PlaylistRepository,
    private val xtream: XtreamDataSource,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _progress = MutableStateFlow<Map<Long, SyncProgress>>(emptyMap())
    val progress: StateFlow<Map<Long, SyncProgress>> = _progress.asStateFlow()
    private val locks = ConcurrentHashMap<Long, Mutex>()

    private fun report(playlistId: Long, stage: SyncStage, running: Boolean, error: AppError? = null) =
        _progress.update { it + (playlistId to SyncProgress(stage, running, error)) }

    suspend fun sync(playlistId: Long): AppResult<SyncSummary> {
        return locks.getOrPut(playlistId) { Mutex() }.withLock { doSync(playlistId) }
    }

    private suspend fun doSync(playlistId: Long): AppResult<SyncSummary> {
        report(playlistId, SyncStage.CONNECTING, true)
        val creds = playlists.credentials(playlistId)
        if (creds == null) {
            report(playlistId, SyncStage.DONE, false, AppError.Storage)
            return AppResult.Failure(AppError.Storage)
        }
        val dao = db.catalogDao()
        val errors = mutableListOf<AppError>()

        // ---- Live TV ----
        report(playlistId, SyncStage.LIVE, true)
        var liveCount = -1
        val liveCats = xtream.liveCategories(creds)
        val live = xtream.liveStreams(creds)
        if (live is AppResult.Success) {
            val cats = (liveCats as? AppResult.Success)?.data.orEmpty().toCategoryEntities(playlistId, ContentType.LIVE)
            val rows = live.data.mapIndexedNotNull { i, dto -> dto.toEntity(playlistId, i) }
            db.withTransaction {
                dao.deleteCategories(playlistId, ContentType.LIVE.name)
                dao.insertCategories(cats)
                dao.deleteChannels(playlistId)
                rows.chunked(CHUNK).forEach { dao.insertChannels(it) }
            }
            liveCount = rows.size
        } else {
            errors += (live as AppResult.Failure).error
        }

        // ---- Movies ----
        report(playlistId, SyncStage.MOVIES, true)
        var movieCount = -1
        var newMovies = 0
        val vodCats = xtream.vodCategories(creds)
        val vod = xtream.vodStreams(creds)
        if (vod is AppResult.Success) {
            val oldIds = dao.movieIds(playlistId).toHashSet()
            val cats = (vodCats as? AppResult.Success)?.data.orEmpty().toCategoryEntities(playlistId, ContentType.MOVIE)
            val rows = vod.data.mapIndexedNotNull { i, dto -> dto.toEntity(playlistId, i) }
            if (oldIds.isNotEmpty()) newMovies = rows.count { it.streamId !in oldIds }
            db.withTransaction {
                dao.deleteCategories(playlistId, ContentType.MOVIE.name)
                dao.insertCategories(cats)
                dao.deleteMovies(playlistId)
                rows.chunked(CHUNK).forEach { dao.insertMovies(it) }
            }
            movieCount = rows.size
        } else {
            errors += (vod as AppResult.Failure).error
        }

        // ---- Series ----
        report(playlistId, SyncStage.SERIES, true)
        var seriesCount = -1
        var newSeries = 0
        val seriesCats = xtream.seriesCategories(creds)
        val series = xtream.series(creds)
        if (series is AppResult.Success) {
            val oldIds = dao.seriesIds(playlistId).toHashSet()
            val cats = (seriesCats as? AppResult.Success)?.data.orEmpty().toCategoryEntities(playlistId, ContentType.SERIES)
            val rows = series.data.mapIndexedNotNull { i, dto -> dto.toEntity(playlistId, i) }
            if (oldIds.isNotEmpty()) newSeries = rows.count { it.seriesId !in oldIds }
            db.withTransaction {
                dao.deleteCategories(playlistId, ContentType.SERIES.name)
                dao.insertCategories(cats)
                dao.deleteSeries(playlistId)
                rows.chunked(CHUNK).forEach { dao.insertSeries(it) }
            }
            seriesCount = rows.size
        } else {
            errors += (series as AppResult.Failure).error
        }

        if (liveCount < 0 && movieCount < 0 && seriesCount < 0) {
            val err = errors.firstOrNull() ?: AppError.Unknown
            report(playlistId, SyncStage.DONE, false, err)
            return AppResult.Failure(err)
        }
        val existing = playlists.getPlaylist(playlistId)
        val finalLive = if (liveCount >= 0) liveCount else existing?.liveCount ?: 0
        val finalMovies = if (movieCount >= 0) movieCount else existing?.movieCount ?: 0
        val finalSeries = if (seriesCount >= 0) seriesCount else existing?.seriesCount ?: 0
        db.playlistDao().updateCounts(playlistId, finalLive, finalMovies, finalSeries, now())
        report(playlistId, SyncStage.DONE, false, errors.firstOrNull())
        return AppResult.Success(SyncSummary(finalLive, finalMovies, finalSeries, newMovies, newSeries, errors))
    }

    fun isRunning(playlistId: Long): Boolean = _progress.value[playlistId]?.running == true

    /** Marks a sync as queued immediately, so concurrent callers do not start a duplicate download. */
    fun markQueued(playlistId: Long) = report(playlistId, SyncStage.CONNECTING, true)

    private fun List<CategoryDto>.toCategoryEntities(playlistId: Long, type: ContentType): List<CategoryEntity> =
        mapIndexedNotNull { i, dto ->
            val id = dto.categoryId ?: return@mapIndexedNotNull null
            val name = dto.categoryName ?: id
            CategoryEntity(playlistId, type.name, id, name, CategoryClassifier.isAdult(name), i)
        }.distinctBy { it.categoryId }

    private fun LiveStreamDto.toEntity(playlistId: Long, index: Int): ChannelEntity? {
        val id = streamId ?: return null
        val n = name?.trim().orEmpty().ifEmpty { "#$id" }
        return ChannelEntity(
            playlistId = playlistId,
            streamId = id,
            num = num ?: (index + 1).toLong(),
            name = n,
            nameNorm = ContentText.normalize(n),
            logo = streamIcon?.takeIf { it.startsWith("http", ignoreCase = true) },
            categoryId = categoryId,
            epgChannelId = epgChannelId,
            tvArchive = (tvArchive ?: 0L) > 0L,
            isAdult = isAdult == 1L,
            added = added ?: 0L,
            sortOrder = index,
        )
    }

    private fun VodStreamDto.toEntity(playlistId: Long, index: Int): MovieEntity? {
        val id = streamId ?: return null
        val n = (name ?: title)?.trim().orEmpty().ifEmpty { "#$id" }
        return MovieEntity(
            playlistId = playlistId,
            streamId = id,
            name = n,
            nameNorm = ContentText.normalize(n),
            poster = streamIcon?.takeIf { it.startsWith("http", ignoreCase = true) },
            rating = (rating ?: rating5?.times(2))?.takeIf { it > 0 && it <= 10 },
            year = year?.toIntOrNull()?.takeIf { it in 1900..2100 } ?: ContentText.yearOf(releaseDate, n),
            added = added ?: 0L,
            categoryId = categoryId,
            containerExtension = containerExtension,
            isAdult = isAdult == 1L,
            sortOrder = index,
        )
    }

    private fun SeriesDto.toEntity(playlistId: Long, index: Int): SeriesEntity? {
        val id = seriesId ?: return null
        val n = name?.trim().orEmpty().ifEmpty { "#$id" }
        return SeriesEntity(
            playlistId = playlistId,
            seriesId = id,
            name = n,
            nameNorm = ContentText.normalize(n),
            cover = cover?.takeIf { it.startsWith("http", ignoreCase = true) },
            backdrop = backdropPath.firstOrNull { it.startsWith("http", ignoreCase = true) },
            plot = plot,
            genre = genre,
            rating = (rating ?: rating5?.times(2))?.takeIf { it > 0 && it <= 10 },
            year = ContentText.yearOf(releaseDate, releaseDate2, n),
            lastModified = lastModified ?: 0L,
            categoryId = categoryId,
            isAdult = isAdult == 1L,
            sortOrder = index,
        )
    }

    private companion object {
        const val CHUNK = 900
    }
}
