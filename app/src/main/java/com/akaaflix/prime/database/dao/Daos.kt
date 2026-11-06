package com.akaaflix.prime.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.akaaflix.prime.database.entity.CategoryCount
import com.akaaflix.prime.database.entity.CategoryEntity
import com.akaaflix.prime.database.entity.ChannelEntity
import com.akaaflix.prime.database.entity.CustomGroupEntity
import com.akaaflix.prime.database.entity.EpgCacheEntity
import com.akaaflix.prime.database.entity.FavoriteEntity
import com.akaaflix.prime.database.entity.GroupContentEntity
import com.akaaflix.prime.database.entity.GroupWithCount
import com.akaaflix.prime.database.entity.HiddenContentEntity
import com.akaaflix.prime.database.entity.MovieEntity
import com.akaaflix.prime.database.entity.NotificationEntity
import com.akaaflix.prime.database.entity.PlaybackHistoryEntity
import com.akaaflix.prime.database.entity.PlaylistEntity
import com.akaaflix.prime.database.entity.SearchHistoryEntity
import com.akaaflix.prime.database.entity.SeriesEntity
import com.akaaflix.prime.database.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY isDefault DESC, createdAt ASC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists ORDER BY isDefault DESC, createdAt ASC")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): PlaylistEntity?

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entity: PlaylistEntity): Long

    @Update
    suspend fun update(entity: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE playlists SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE playlists SET isActive = 1 WHERE id = :id")
    suspend fun markActive(id: Long)

    @Transaction
    suspend fun setActive(id: Long) {
        clearActive()
        markActive(id)
    }

    @Query("UPDATE playlists SET isDefault = 0")
    suspend fun clearDefault()

    @Query("UPDATE playlists SET isDefault = 1 WHERE id = :id")
    suspend fun markDefault(id: Long)

    @Transaction
    suspend fun setDefault(id: Long) {
        clearDefault()
        markDefault(id)
    }

    @Query("UPDATE playlists SET liveCount = :live, movieCount = :movies, seriesCount = :series, lastRefreshAt = :at WHERE id = :id")
    suspend fun updateCounts(id: Long, live: Int, movies: Int, series: Int, at: Long)
}

@Dao
interface CatalogDao {
    // ---- Categories ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId AND type = :type")
    suspend fun deleteCategories(playlistId: Long, type: String)

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND type = :type ORDER BY sortOrder")
    fun observeCategories(playlistId: Long, type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND type = :type ORDER BY sortOrder")
    suspend fun getCategories(playlistId: Long, type: String): List<CategoryEntity>

    @RawQuery(observedEntities = [ChannelEntity::class, MovieEntity::class, SeriesEntity::class, HiddenContentEntity::class])
    fun observeCategoryCounts(query: SupportSQLiteQuery): Flow<List<CategoryCount>>

    // ---- Channels ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(items: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannels(playlistId: Long)

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND streamId = :streamId")
    suspend fun getChannel(playlistId: Long, streamId: Long): ChannelEntity?

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    fun observeChannelCount(playlistId: Long): Flow<Int>

    @RawQuery(observedEntities = [ChannelEntity::class, HiddenContentEntity::class, CategoryEntity::class, FavoriteEntity::class, GroupContentEntity::class, PlaybackHistoryEntity::class])
    fun channelsPaging(query: SupportSQLiteQuery): PagingSource<Int, ChannelEntity>

    @RawQuery(observedEntities = [ChannelEntity::class, HiddenContentEntity::class, CategoryEntity::class, FavoriteEntity::class, GroupContentEntity::class, PlaybackHistoryEntity::class])
    fun channelsFlow(query: SupportSQLiteQuery): Flow<List<ChannelEntity>>

    @RawQuery
    suspend fun channelsList(query: SupportSQLiteQuery): List<ChannelEntity>

    // ---- Movies ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovies(items: List<MovieEntity>)

    @Query("DELETE FROM movies WHERE playlistId = :playlistId")
    suspend fun deleteMovies(playlistId: Long)

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND streamId = :streamId")
    suspend fun getMovie(playlistId: Long, streamId: Long): MovieEntity?

    @Query("SELECT COUNT(*) FROM movies WHERE playlistId = :playlistId")
    fun observeMovieCount(playlistId: Long): Flow<Int>

    @Query("SELECT streamId FROM movies WHERE playlistId = :playlistId")
    suspend fun movieIds(playlistId: Long): List<Long>

    @RawQuery(observedEntities = [MovieEntity::class, HiddenContentEntity::class, CategoryEntity::class, FavoriteEntity::class, GroupContentEntity::class, PlaybackHistoryEntity::class])
    fun moviesPaging(query: SupportSQLiteQuery): PagingSource<Int, MovieEntity>

    @RawQuery(observedEntities = [MovieEntity::class, HiddenContentEntity::class, CategoryEntity::class, FavoriteEntity::class, GroupContentEntity::class, PlaybackHistoryEntity::class])
    fun moviesFlow(query: SupportSQLiteQuery): Flow<List<MovieEntity>>

    // ---- Series ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(items: List<SeriesEntity>)

    @Query("DELETE FROM series WHERE playlistId = :playlistId")
    suspend fun deleteSeries(playlistId: Long)

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND seriesId = :seriesId")
    suspend fun getSeries(playlistId: Long, seriesId: Long): SeriesEntity?

    @Query("SELECT COUNT(*) FROM series WHERE playlistId = :playlistId")
    fun observeSeriesCount(playlistId: Long): Flow<Int>

    @Query("SELECT seriesId FROM series WHERE playlistId = :playlistId")
    suspend fun seriesIds(playlistId: Long): List<Long>

    @RawQuery(observedEntities = [SeriesEntity::class, HiddenContentEntity::class, CategoryEntity::class, FavoriteEntity::class, GroupContentEntity::class, PlaybackHistoryEntity::class])
    fun seriesPaging(query: SupportSQLiteQuery): PagingSource<Int, SeriesEntity>

    @RawQuery(observedEntities = [SeriesEntity::class, HiddenContentEntity::class, CategoryEntity::class, FavoriteEntity::class, GroupContentEntity::class, PlaybackHistoryEntity::class])
    fun seriesFlow(query: SupportSQLiteQuery): Flow<List<SeriesEntity>>

    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    suspend fun deleteAllCategories(playlistId: Long)
}

@Dao
interface LibraryDao {
    // ---- Favorites ----
    @Query("SELECT * FROM favorites WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    fun observeFavorites(playlistId: Long): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE playlistId = :playlistId AND contentType = :type ORDER BY addedAt DESC")
    fun observeFavoritesOfType(playlistId: Long, type: String): Flow<List<FavoriteEntity>>

    @Query("SELECT contentId FROM favorites WHERE playlistId = :playlistId AND contentType = :type")
    fun observeFavoriteIds(playlistId: Long, type: String): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE playlistId = :playlistId AND contentType = :type AND contentId = :contentId)")
    fun observeIsFavorite(playlistId: Long, type: String, contentId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE playlistId = :playlistId AND contentType = :type AND contentId = :contentId)")
    suspend fun isFavorite(playlistId: Long, type: String, contentId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorite(entity: FavoriteEntity): Long

    @Query("DELETE FROM favorites WHERE playlistId = :playlistId AND contentType = :type AND contentId = :contentId")
    suspend fun deleteFavorite(playlistId: Long, type: String, contentId: String)

    @Query("SELECT * FROM favorites")
    suspend fun allFavorites(): List<FavoriteEntity>

    // ---- Groups ----
    @Query(
        "SELECT g.id AS id, g.playlistId AS playlistId, g.name AS name, g.createdAt AS createdAt, " +
            "(SELECT COUNT(*) FROM group_contents c WHERE c.groupId = g.id) AS itemCount " +
            "FROM custom_groups g WHERE g.playlistId = :playlistId ORDER BY g.sortOrder, g.createdAt",
    )
    fun observeGroups(playlistId: Long): Flow<List<GroupWithCount>>

    @Query("SELECT * FROM custom_groups WHERE id = :groupId")
    suspend fun getGroup(groupId: Long): CustomGroupEntity?

    @Query("SELECT * FROM custom_groups WHERE playlistId = :playlistId AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findGroupByName(playlistId: Long, name: String): CustomGroupEntity?

    @Query("SELECT * FROM custom_groups")
    suspend fun allGroups(): List<CustomGroupEntity>

    @Insert
    suspend fun insertGroup(entity: CustomGroupEntity): Long

    @Query("UPDATE custom_groups SET name = :name WHERE id = :groupId")
    suspend fun renameGroup(groupId: Long, name: String)

    @Query("DELETE FROM custom_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Long)

    @Query("SELECT * FROM group_contents WHERE groupId = :groupId ORDER BY addedAt DESC")
    fun observeGroupItems(groupId: Long): Flow<List<GroupContentEntity>>

    @Query("SELECT * FROM group_contents")
    suspend fun allGroupItems(): List<GroupContentEntity>

    @Query("SELECT groupId FROM group_contents WHERE playlistId = :playlistId AND contentType = :type AND contentId = :contentId")
    fun observeGroupsContaining(playlistId: Long, type: String, contentId: String): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroupItem(entity: GroupContentEntity): Long

    @Query("DELETE FROM group_contents WHERE groupId = :groupId AND contentType = :type AND contentId = :contentId")
    suspend fun deleteGroupItem(groupId: Long, type: String, contentId: String)

    @Query("DELETE FROM favorites WHERE playlistId = :playlistId")
    suspend fun deleteFavoritesForPlaylist(playlistId: Long)

    @Query("DELETE FROM custom_groups WHERE playlistId = :playlistId")
    suspend fun deleteGroupsForPlaylist(playlistId: Long)
}

@Dao
interface HiddenContentDao {
    @Query("SELECT * FROM hidden_content WHERE playlistId = :playlistId ORDER BY createdAt DESC")
    fun observe(playlistId: Long): Flow<List<HiddenContentEntity>>

    @Query("SELECT * FROM hidden_content WHERE playlistId = :playlistId AND restriction = :restriction ORDER BY createdAt DESC")
    fun observe(playlistId: Long, restriction: String): Flow<List<HiddenContentEntity>>

    @Query("SELECT * FROM hidden_content WHERE playlistId = :playlistId AND restriction = :restriction")
    suspend fun get(playlistId: Long, restriction: String): List<HiddenContentEntity>

    @Query("SELECT * FROM hidden_content")
    suspend fun all(): List<HiddenContentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: HiddenContentEntity): Long

    @Query("DELETE FROM hidden_content WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM hidden_content WHERE playlistId = :playlistId AND scope = :scope AND restriction = :restriction AND contentType = :type AND targetId = :targetId")
    suspend fun delete(playlistId: Long, scope: String, restriction: String, type: String, targetId: String)

    @Query("DELETE FROM hidden_content WHERE playlistId = :playlistId AND restriction = :restriction")
    suspend fun clear(playlistId: Long, restriction: String)

    @Query(
        "SELECT EXISTS(SELECT 1 FROM hidden_content WHERE playlistId = :playlistId AND restriction = 'PIN_LOCKED' AND " +
            "((scope = 'ITEM' AND contentType = :type AND targetId = :contentId) OR " +
            "(scope = 'CATEGORY' AND contentType = :type AND targetId = :categoryId)))",
    )
    suspend fun isLocked(playlistId: Long, type: String, contentId: String, categoryId: String): Boolean

    @Query("DELETE FROM hidden_content WHERE playlistId = :playlistId")
    suspend fun deleteForPlaylist(playlistId: Long)
}

@Dao
interface PlaybackHistoryDao {
    @Query(
        "SELECT * FROM playback_history WHERE playlistId = :playlistId AND contentType IN ('MOVIE','EPISODE') " +
            "AND durationMs > 0 AND positionMs > 30000 AND positionMs < durationMs * 0.95 ORDER BY lastWatchedAt DESC LIMIT :limit",
    )
    fun observeContinueWatching(playlistId: Long, limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE playlistId = :playlistId ORDER BY lastWatchedAt DESC LIMIT :limit")
    fun observeHistory(playlistId: Long, limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE playlistId = :playlistId AND contentType = :type AND contentId = :contentId")
    suspend fun get(playlistId: Long, type: String, contentId: String): PlaybackHistoryEntity?

    @Query("SELECT * FROM playback_history WHERE playlistId = :playlistId AND contentType = 'EPISODE' AND seriesId = :seriesId ORDER BY lastWatchedAt DESC LIMIT 1")
    suspend fun lastEpisode(playlistId: Long, seriesId: Long): PlaybackHistoryEntity?

    @Upsert
    suspend fun upsert(entity: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history WHERE playlistId = :playlistId AND contentType = :type AND contentId = :contentId")
    suspend fun delete(playlistId: Long, type: String, contentId: String)

    @Query("DELETE FROM playback_history WHERE playlistId = :playlistId")
    suspend fun clear(playlistId: Long)

    @Query("DELETE FROM playback_history")
    suspend fun clearAll()
}

@Dao
interface UserSettingsDao {
    @Query("SELECT value FROM user_settings WHERE playlistId = :playlistId AND `key` = :key")
    suspend fun get(playlistId: Long, key: String): String?

    @Upsert
    suspend fun put(entity: UserSettingsEntity)

    @Query("DELETE FROM user_settings WHERE playlistId = :playlistId")
    suspend fun clear(playlistId: Long)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun observe(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Upsert
    suspend fun upsert(entity: SearchHistoryEntity)

    @Query("SELECT * FROM search_history WHERE query = :query LIMIT 1")
    suspend fun find(query: String): SearchHistoryEntity?

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}

@Dao
interface EpgDao {
    @Query("SELECT * FROM epg_cache WHERE playlistId = :playlistId AND streamId = :streamId AND endTs > :nowSec ORDER BY startTs")
    suspend fun upcoming(playlistId: Long, streamId: Long, nowSec: Long): List<EpgCacheEntity>

    @Query("SELECT MAX(fetchedAt) FROM epg_cache WHERE playlistId = :playlistId AND streamId = :streamId")
    suspend fun lastFetched(playlistId: Long, streamId: Long): Long?

    @Query("SELECT * FROM epg_cache WHERE playlistId = :playlistId AND streamId IN (:streamIds) AND startTs <= :nowSec AND endTs > :nowSec")
    suspend fun nowPlaying(playlistId: Long, streamIds: List<Long>, nowSec: Long): List<EpgCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<EpgCacheEntity>)

    @Query("DELETE FROM epg_cache WHERE playlistId = :playlistId AND streamId = :streamId")
    suspend fun deleteFor(playlistId: Long, streamId: Long)

    @Query("DELETE FROM epg_cache WHERE endTs < :beforeSec")
    suspend fun purgeOlderThan(beforeSec: Long)

    @Query("DELETE FROM epg_cache")
    suspend fun clear()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC LIMIT 200")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert
    suspend fun insert(entity: NotificationEntity): Long

    @Query("SELECT COUNT(*) FROM notifications WHERE type = :type AND playlistId = :playlistId AND createdAt > :since")
    suspend fun countSince(type: String, playlistId: Long, since: Long): Int

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllRead()

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM notifications")
    suspend fun clear()
}
