package com.akaaflix.prime.domain.repository

import androidx.paging.PagingData
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.common.CategoryKind
import com.akaaflix.prime.domain.model.AccountInfo
import com.akaaflix.prime.domain.model.Category
import com.akaaflix.prime.domain.model.Channel
import com.akaaflix.prime.domain.model.ContentRef
import com.akaaflix.prime.domain.model.ContentRule
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.model.CustomGroup
import com.akaaflix.prime.domain.model.EpgProgram
import com.akaaflix.prime.domain.model.FavoriteItem
import com.akaaflix.prime.domain.model.GroupItem
import com.akaaflix.prime.domain.model.Movie
import com.akaaflix.prime.domain.model.MovieDetails
import com.akaaflix.prime.domain.model.PlaybackEntry
import com.akaaflix.prime.domain.model.Playlist
import com.akaaflix.prime.domain.model.RuleRestriction
import com.akaaflix.prime.domain.model.RuleScope
import com.akaaflix.prime.domain.model.Series
import com.akaaflix.prime.domain.model.SeriesDetails
import com.akaaflix.prime.domain.model.SortOrder
import com.akaaflix.prime.domain.model.XtreamCredentials
import kotlinx.coroutines.flow.Flow

/** Input for creating or editing a playlist (exactly the four Xtream fields). */
data class PlaylistInput(val name: String, val username: String, val password: String, val url: String)

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>
    fun observeActive(): Flow<Playlist?>
    suspend fun getPlaylist(id: Long): Playlist?
    suspend fun count(): Int
    /** Authenticates against the server without saving anything. */
    suspend fun testConnection(input: PlaylistInput): AppResult<AccountInfo>
    /** Authenticates, then saves; returns the new playlist id. */
    suspend fun addPlaylist(input: PlaylistInput): AppResult<Long>
    /** Re-authenticates with the new values and updates the playlist. Blank password keeps the old one. */
    suspend fun updatePlaylist(id: Long, input: PlaylistInput): AppResult<Unit>
    suspend fun deletePlaylist(id: Long)
    suspend fun setActive(id: Long)
    suspend fun setDefault(id: Long)
    suspend fun clearActive()
    /** Refreshes account information from the server (status, expiry, connections). */
    suspend fun refreshAccount(id: Long): AppResult<AccountInfo>
    suspend fun credentials(id: Long): XtreamCredentials?
}

/** Query used by paged browse screens. */
data class BrowseQuery(
    val playlistId: Long,
    val type: ContentType,
    val categoryId: String? = null,
    val categoryIds: List<String>? = null,
    val search: String? = null,
    val sort: SortOrder = SortOrder.DEFAULT,
    val mostWatched: Boolean = false,
    val favoritesOnly: Boolean = false,
    val groupId: Long? = null,
    val includeAdult: Boolean = false,
    val limit: Int? = null,
)

interface CatalogRepository {
    fun observeCategories(playlistId: Long, type: ContentType, includeAdult: Boolean): Flow<List<Category>>
    /** Every cached category, including hidden ones (for the hidden-content manager). */
    suspend fun allCategories(playlistId: Long, type: ContentType): List<Category>
    fun channelsPaged(query: BrowseQuery): Flow<PagingData<Channel>>
    fun moviesPaged(query: BrowseQuery): Flow<PagingData<Movie>>
    fun seriesPaged(query: BrowseQuery): Flow<PagingData<Series>>
    fun channels(query: BrowseQuery): Flow<List<Channel>>
    fun movies(query: BrowseQuery): Flow<List<Movie>>
    fun series(query: BrowseQuery): Flow<List<Series>>
    suspend fun channelList(query: BrowseQuery): List<Channel>
    suspend fun getChannel(playlistId: Long, streamId: Long): Channel?
    suspend fun getMovie(playlistId: Long, streamId: Long): Movie?
    suspend fun getSeries(playlistId: Long, seriesId: Long): Series?
    suspend fun movieDetails(playlistId: Long, streamId: Long, forceRefresh: Boolean = false): AppResult<MovieDetails>
    suspend fun seriesDetails(playlistId: Long, seriesId: Long, forceRefresh: Boolean = false): AppResult<SeriesDetails>
    fun observeCounts(playlistId: Long): Flow<Triple<Int, Int, Int>>
    suspend fun categoryKinds(playlistId: Long, type: ContentType): Map<CategoryKind, List<String>>
    suspend fun countries(playlistId: Long, type: ContentType): Map<String, List<String>>
    fun clearMemoryCache()
}

interface LibraryRepository {
    fun observeFavorites(playlistId: Long, type: ContentType? = null): Flow<List<FavoriteItem>>
    fun observeFavoriteIds(playlistId: Long, type: ContentType): Flow<Set<String>>
    fun isFavorite(playlistId: Long, type: ContentType, contentId: String): Flow<Boolean>
    suspend fun toggleFavorite(ref: ContentRef): Boolean
    suspend fun addFavorite(ref: ContentRef)
    suspend fun removeFavorite(playlistId: Long, type: ContentType, contentId: String)

    fun observeGroups(playlistId: Long): Flow<List<CustomGroup>>
    fun observeGroupItems(groupId: Long): Flow<List<GroupItem>>
    fun observeGroupsContaining(playlistId: Long, type: ContentType, contentId: String): Flow<Set<Long>>
    suspend fun getGroup(groupId: Long): CustomGroup?
    suspend fun createGroup(playlistId: Long, name: String): AppResult<Long>
    suspend fun renameGroup(groupId: Long, name: String): AppResult<Unit>
    suspend fun deleteGroup(groupId: Long)
    suspend fun addToGroup(groupId: Long, ref: ContentRef)
    suspend fun removeFromGroup(groupId: Long, type: ContentType, contentId: String)
}

interface ContentRulesRepository {
    fun observeRules(playlistId: Long): Flow<List<ContentRule>>
    fun observeRules(playlistId: Long, restriction: RuleRestriction): Flow<List<ContentRule>>
    suspend fun addRule(playlistId: Long, scope: RuleScope, restriction: RuleRestriction, type: ContentType?, targetId: String, title: String)
    suspend fun removeRule(id: Long)
    suspend fun removeRule(playlistId: Long, scope: RuleScope, restriction: RuleRestriction, type: ContentType?, targetId: String)
    suspend fun isLocked(playlistId: Long, type: ContentType, contentId: String, categoryId: String?): Boolean
    suspend fun clear(playlistId: Long, restriction: RuleRestriction)
}

interface HistoryRepository {
    fun observeContinueWatching(playlistId: Long, limit: Int = 20): Flow<List<PlaybackEntry>>
    fun observeHistory(playlistId: Long, limit: Int = 100): Flow<List<PlaybackEntry>>
    suspend fun get(playlistId: Long, type: ContentType, contentId: String): PlaybackEntry?
    suspend fun lastEpisodeOf(playlistId: Long, seriesId: Long): PlaybackEntry?
    suspend fun save(entry: PlaybackEntry, incrementPlayCount: Boolean)
    suspend fun remove(playlistId: Long, type: ContentType, contentId: String)
    suspend fun clear(playlistId: Long)
}

interface EpgRepository {
    suspend fun shortEpg(playlistId: Long, streamId: Long, forceRefresh: Boolean = false): AppResult<List<EpgProgram>>
    suspend fun cachedNowPlaying(playlistId: Long, streamIds: List<Long>): Map<Long, EpgProgram>
    suspend fun clearCache()
}
