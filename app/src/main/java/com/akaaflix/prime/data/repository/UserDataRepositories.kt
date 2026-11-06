package com.akaaflix.prime.data.repository

import com.akaaflix.prime.common.AppError
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.common.Base64Text
import com.akaaflix.prime.database.AppDatabase
import com.akaaflix.prime.database.entity.CustomGroupEntity
import com.akaaflix.prime.database.entity.EpgCacheEntity
import com.akaaflix.prime.database.entity.FavoriteEntity
import com.akaaflix.prime.database.entity.GroupContentEntity
import com.akaaflix.prime.database.entity.HiddenContentEntity
import com.akaaflix.prime.database.entity.NotificationEntity
import com.akaaflix.prime.database.entity.SearchHistoryEntity
import com.akaaflix.prime.database.entity.UserSettingsEntity
import com.akaaflix.prime.domain.model.AppNotification
import com.akaaflix.prime.domain.model.ContentRef
import com.akaaflix.prime.domain.model.ContentRule
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.model.CustomGroup
import com.akaaflix.prime.domain.model.EpgProgram
import com.akaaflix.prime.domain.model.FavoriteItem
import com.akaaflix.prime.domain.model.GroupItem
import com.akaaflix.prime.domain.model.PlaybackEntry
import com.akaaflix.prime.domain.model.RuleRestriction
import com.akaaflix.prime.domain.model.RuleScope
import com.akaaflix.prime.domain.repository.ContentRulesRepository
import com.akaaflix.prime.domain.repository.EpgRepository
import com.akaaflix.prime.domain.repository.HistoryRepository
import com.akaaflix.prime.domain.repository.LibraryRepository
import com.akaaflix.prime.domain.repository.PlaylistRepository
import com.akaaflix.prime.network.XtreamDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class LibraryRepositoryImpl(private val db: AppDatabase) : LibraryRepository {
    private val dao = db.libraryDao()

    override fun observeFavorites(playlistId: Long, type: ContentType?): Flow<List<FavoriteItem>> =
        (if (type == null) dao.observeFavorites(playlistId) else dao.observeFavoritesOfType(playlistId, type.name))
            .map { list -> list.map { it.toDomain() } }

    override fun observeFavoriteIds(playlistId: Long, type: ContentType): Flow<Set<String>> =
        dao.observeFavoriteIds(playlistId, type.name).map { it.toSet() }

    override fun isFavorite(playlistId: Long, type: ContentType, contentId: String): Flow<Boolean> =
        dao.observeIsFavorite(playlistId, type.name, contentId)

    override suspend fun toggleFavorite(ref: ContentRef): Boolean {
        val exists = dao.isFavorite(ref.playlistId, ref.type.name, ref.contentId)
        if (exists) removeFavorite(ref.playlistId, ref.type, ref.contentId) else addFavorite(ref)
        return !exists
    }

    override suspend fun addFavorite(ref: ContentRef) {
        dao.insertFavorite(FavoriteEntity(playlistId = ref.playlistId, contentType = ref.type.name, contentId = ref.contentId, title = ref.title, imageUrl = ref.imageUrl, extra = ref.extra))
    }

    override suspend fun removeFavorite(playlistId: Long, type: ContentType, contentId: String) =
        dao.deleteFavorite(playlistId, type.name, contentId)

    override fun observeGroups(playlistId: Long): Flow<List<CustomGroup>> = combine(
        dao.observeGroups(playlistId),
        db.hiddenContentDao().observe(playlistId, RuleRestriction.HIDDEN.name),
    ) { groups, rules ->
        val hidden = rules.filter { it.scope == RuleScope.GROUP.name }.map { it.targetId }.toSet()
        groups.map { CustomGroup(it.id, it.playlistId, it.name, it.itemCount, it.createdAt, isHidden = it.id.toString() in hidden) }
    }

    override fun observeGroupItems(groupId: Long): Flow<List<GroupItem>> =
        dao.observeGroupItems(groupId).map { list -> list.map { it.toDomain() } }

    override fun observeGroupsContaining(playlistId: Long, type: ContentType, contentId: String): Flow<Set<Long>> =
        dao.observeGroupsContaining(playlistId, type.name, contentId).map { it.toSet() }

    override suspend fun getGroup(groupId: Long): CustomGroup? =
        dao.getGroup(groupId)?.let { CustomGroup(it.id, it.playlistId, it.name, 0, it.createdAt) }

    override suspend fun createGroup(playlistId: Long, name: String): AppResult<Long> {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > 40) return AppResult.Failure(AppError.Unknown)
        if (dao.findGroupByName(playlistId, clean) != null) return AppResult.Failure(AppError.Duplicate)
        return AppResult.Success(dao.insertGroup(CustomGroupEntity(playlistId = playlistId, name = clean)))
    }

    override suspend fun renameGroup(groupId: Long, name: String): AppResult<Unit> {
        val clean = name.trim()
        val group = dao.getGroup(groupId) ?: return AppResult.Failure(AppError.NotFound)
        if (clean.isEmpty() || clean.length > 40) return AppResult.Failure(AppError.Unknown)
        val other = dao.findGroupByName(group.playlistId, clean)
        if (other != null && other.id != groupId) return AppResult.Failure(AppError.Duplicate)
        dao.renameGroup(groupId, clean)
        return AppResult.Success(Unit)
    }

    override suspend fun deleteGroup(groupId: Long) {
        val group = dao.getGroup(groupId) ?: return
        dao.deleteGroup(groupId)
        db.hiddenContentDao().delete(group.playlistId, RuleScope.GROUP.name, RuleRestriction.HIDDEN.name, ANY_TYPE, groupId.toString())
    }

    override suspend fun addToGroup(groupId: Long, ref: ContentRef) {
        dao.insertGroupItem(GroupContentEntity(groupId = groupId, playlistId = ref.playlistId, contentType = ref.type.name, contentId = ref.contentId, title = ref.title, imageUrl = ref.imageUrl, extra = ref.extra))
    }

    override suspend fun removeFromGroup(groupId: Long, type: ContentType, contentId: String) =
        dao.deleteGroupItem(groupId, type.name, contentId)
}

class ContentRulesRepositoryImpl(db: AppDatabase) : ContentRulesRepository {
    private val dao = db.hiddenContentDao()

    override fun observeRules(playlistId: Long): Flow<List<ContentRule>> = dao.observe(playlistId).map { l -> l.map { it.toDomain() } }

    override fun observeRules(playlistId: Long, restriction: RuleRestriction): Flow<List<ContentRule>> =
        dao.observe(playlistId, restriction.name).map { l -> l.map { it.toDomain() } }

    override suspend fun addRule(playlistId: Long, scope: RuleScope, restriction: RuleRestriction, type: ContentType?, targetId: String, title: String) {
        val target = if (scope == RuleScope.KEYWORD) targetId.trim() else targetId
        if (target.isEmpty()) return
        dao.insert(HiddenContentEntity(playlistId = playlistId, scope = scope.name, restriction = restriction.name, contentType = type?.name ?: ANY_TYPE, targetId = target, title = title))
    }

    override suspend fun removeRule(id: Long) = dao.delete(id)

    override suspend fun removeRule(playlistId: Long, scope: RuleScope, restriction: RuleRestriction, type: ContentType?, targetId: String) =
        dao.delete(playlistId, scope.name, restriction.name, type?.name ?: ANY_TYPE, targetId)

    override suspend fun isLocked(playlistId: Long, type: ContentType, contentId: String, categoryId: String?): Boolean =
        dao.isLocked(playlistId, type.name, contentId, categoryId ?: "\u0000")

    override suspend fun clear(playlistId: Long, restriction: RuleRestriction) = dao.clear(playlistId, restriction.name)
}

class HistoryRepositoryImpl(db: AppDatabase) : HistoryRepository {
    private val dao = db.playbackHistoryDao()

    override fun observeContinueWatching(playlistId: Long, limit: Int): Flow<List<PlaybackEntry>> =
        dao.observeContinueWatching(playlistId, limit).map { l -> l.map { it.toDomain() } }

    override fun observeHistory(playlistId: Long, limit: Int): Flow<List<PlaybackEntry>> =
        dao.observeHistory(playlistId, limit).map { l -> l.map { it.toDomain() } }

    override suspend fun get(playlistId: Long, type: ContentType, contentId: String) = dao.get(playlistId, type.name, contentId)?.toDomain()

    override suspend fun lastEpisodeOf(playlistId: Long, seriesId: Long) = dao.lastEpisode(playlistId, seriesId)?.toDomain()

    override suspend fun save(entry: PlaybackEntry, incrementPlayCount: Boolean) {
        val previous = dao.get(entry.playlistId, entry.type.name, entry.contentId)
        val count = (previous?.playCount ?: 0) + if (incrementPlayCount) 1 else 0
        dao.upsert(entry.copy(playCount = maxOf(count, 1)).toEntity())
    }

    override suspend fun remove(playlistId: Long, type: ContentType, contentId: String) = dao.delete(playlistId, type.name, contentId)

    override suspend fun clear(playlistId: Long) = dao.clear(playlistId)
}

class EpgRepositoryImpl(
    private val db: AppDatabase,
    private val playlists: PlaylistRepository,
    private val xtream: XtreamDataSource,
    private val cacheHours: suspend () -> Int,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : EpgRepository {
    private val dao = db.epgDao()

    override suspend fun shortEpg(playlistId: Long, streamId: Long, forceRefresh: Boolean): AppResult<List<EpgProgram>> {
        val nowSec = nowMillis() / 1000
        val ttlMs = cacheHours().coerceAtLeast(1) * 3_600_000L
        val last = dao.lastFetched(playlistId, streamId)
        val cached = dao.upcoming(playlistId, streamId, nowSec)
        if (!forceRefresh && last != null && nowMillis() - last < ttlMs && cached.isNotEmpty()) {
            return AppResult.Success(cached.map { it.toProgram() })
        }
        val creds = playlists.credentials(playlistId) ?: return AppResult.Failure(AppError.NoActivePlaylist)
        return when (val r = xtream.shortEpg(creds, streamId)) {
            is AppResult.Failure -> if (cached.isNotEmpty()) AppResult.Success(cached.map { it.toProgram() }) else r
            is AppResult.Success -> {
                val fetchedAt = nowMillis()
                val rows = r.data.listings.mapNotNull { l ->
                    val start = l.startTimestamp ?: return@mapNotNull null
                    val end = l.stopTimestamp ?: return@mapNotNull null
                    if (end <= start) return@mapNotNull null
                    EpgCacheEntity(
                        playlistId = playlistId,
                        streamId = streamId,
                        startTs = start,
                        endTs = end,
                        title = Base64Text.decodeTextOrSelf(l.title)?.trim().orEmpty().ifEmpty { "—" },
                        description = Base64Text.decodeTextOrSelf(l.description)?.trim()?.takeIf { it.isNotEmpty() },
                        fetchedAt = fetchedAt,
                    )
                }
                dao.deleteFor(playlistId, streamId)
                if (rows.isNotEmpty()) dao.insert(rows)
                dao.purgeOlderThan(nowSec - 86_400)
                AppResult.Success(rows.filter { it.endTs > nowSec }.sortedBy { it.startTs }.map { it.toProgram() })
            }
        }
    }

    override suspend fun cachedNowPlaying(playlistId: Long, streamIds: List<Long>): Map<Long, EpgProgram> {
        if (streamIds.isEmpty()) return emptyMap()
        val nowSec = nowMillis() / 1000
        return streamIds.chunked(500).flatMap { dao.nowPlaying(playlistId, it, nowSec) }.associate { it.streamId to it.toProgram() }
    }

    override suspend fun clearCache() = dao.clear()

    private fun EpgCacheEntity.toProgram() = EpgProgram(streamId, title, description, startTs, endTs)
}

/** Search history (recent queries). */
class SearchHistoryRepository(db: AppDatabase) {
    private val dao = db.searchHistoryDao()
    fun observe(): Flow<List<String>> = dao.observe(15).map { l -> l.map { it.query } }
    suspend fun record(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        val existing = dao.find(q)
        dao.upsert(SearchHistoryEntity(id = existing?.id ?: 0, query = q, searchedAt = System.currentTimeMillis()))
    }
    suspend fun remove(query: String) { dao.find(query)?.let { dao.delete(it.id) } }
    suspend fun clear() = dao.clear()
}

/** In-app notification center (subscription expiry, new content after refresh). */
class NotificationRepository(db: AppDatabase) {
    private val dao = db.notificationDao()
    fun observe(): Flow<List<AppNotification>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    fun observeUnread(): Flow<Int> = dao.observeUnreadCount()
    suspend fun add(playlistId: Long?, type: String, title: String, message: String): Long =
        dao.insert(NotificationEntity(playlistId = playlistId, type = type, title = title, message = message))
    suspend fun countSince(type: String, playlistId: Long, since: Long) = dao.countSince(type, playlistId, since)
    suspend fun markAllRead() = dao.markAllRead()
    suspend fun markRead(id: Long) = dao.markRead(id)
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun clear() = dao.clear()
}

/** Per-playlist key/value preferences (last channel, last category...). */
class PlaylistPrefs(db: AppDatabase) {
    private val dao = db.userSettingsDao()
    suspend fun get(playlistId: Long, key: String): String? = dao.get(playlistId, key)
    suspend fun put(playlistId: Long, key: String, value: String) = dao.put(UserSettingsEntity(playlistId, key, value))

    companion object {
        const val LAST_CHANNEL = "last_channel"
        const val LAST_LIVE_CATEGORY = "last_live_category"
    }
}
