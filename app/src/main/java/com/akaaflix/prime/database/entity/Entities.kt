package com.akaaflix.prime.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A saved Xtream Codes playlist. Credentials are stored AES-GCM encrypted (Android Keystore). */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val serverUrl: String,
    val usernameEnc: String,
    val passwordEnc: String,
    val isActive: Boolean = false,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastRefreshAt: Long? = null,
    val lastCheckedAt: Long? = null,
    val connectionStatus: String = "UNKNOWN",
    val accountStatus: String = "UNKNOWN",
    val expDate: Long? = null,
    val serverCreatedAt: Long? = null,
    val isTrial: Boolean = false,
    val maxConnections: Int? = null,
    val activeConnections: Int? = null,
    val allowedFormats: String = "",
    val serverMessage: String? = null,
    val serverTimezone: String? = null,
    val rtmpPort: String? = null,
    val liveCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
)

/** Cached catalog category (LIVE / MOVIE / SERIES). */
@Entity(tableName = "categories", primaryKeys = ["playlistId", "type", "categoryId"])
data class CategoryEntity(
    val playlistId: Long,
    val type: String,
    val categoryId: String,
    val name: String,
    val isAdult: Boolean,
    val sortOrder: Int,
)

@Entity(
    tableName = "channels",
    primaryKeys = ["playlistId", "streamId"],
    indices = [Index(value = ["playlistId", "categoryId"]), Index(value = ["playlistId", "nameNorm"])],
)
data class ChannelEntity(
    val playlistId: Long,
    val streamId: Long,
    val num: Long,
    val name: String,
    val nameNorm: String,
    val logo: String?,
    val categoryId: String?,
    val epgChannelId: String?,
    val tvArchive: Boolean,
    val isAdult: Boolean,
    val added: Long,
    val sortOrder: Int,
)

@Entity(
    tableName = "movies",
    primaryKeys = ["playlistId", "streamId"],
    indices = [Index(value = ["playlistId", "categoryId"]), Index(value = ["playlistId", "added"]), Index(value = ["playlistId", "nameNorm"])],
)
data class MovieEntity(
    val playlistId: Long,
    val streamId: Long,
    val name: String,
    val nameNorm: String,
    val poster: String?,
    val rating: Double?,
    val year: Int?,
    val added: Long,
    val categoryId: String?,
    val containerExtension: String?,
    val isAdult: Boolean,
    val sortOrder: Int,
)

@Entity(
    tableName = "series",
    primaryKeys = ["playlistId", "seriesId"],
    indices = [Index(value = ["playlistId", "categoryId"]), Index(value = ["playlistId", "lastModified"]), Index(value = ["playlistId", "nameNorm"])],
)
data class SeriesEntity(
    val playlistId: Long,
    val seriesId: Long,
    val name: String,
    val nameNorm: String,
    val cover: String?,
    val backdrop: String?,
    val plot: String?,
    val genre: String?,
    val rating: Double?,
    val year: Int?,
    val lastModified: Long,
    val categoryId: String?,
    val isAdult: Boolean,
    val sortOrder: Int,
)

/** Unified favorites for channels, movies and series. Title/image are denormalised so favorites survive catalog refreshes. */
@Entity(
    tableName = "favorites",
    indices = [Index(value = ["playlistId", "contentType", "contentId"], unique = true)],
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val contentType: String,
    val contentId: String,
    val title: String,
    val imageUrl: String?,
    val extra: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "custom_groups", indices = [Index(value = ["playlistId", "name"], unique = true)])
data class CustomGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
)

/** A group can mix channels, movies and series. */
@Entity(
    tableName = "group_contents",
    foreignKeys = [ForeignKey(entity = CustomGroupEntity::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["groupId", "contentType", "contentId"], unique = true)],
)
data class GroupContentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val playlistId: Long,
    val contentType: String,
    val contentId: String,
    val title: String,
    val imageUrl: String?,
    val extra: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

/**
 * Hidden-content and parental-lock rules. `scope` = CATEGORY / ITEM / GROUP / KEYWORD,
 * `restriction` = HIDDEN (removed from browsing & search) or PIN_LOCKED (requires PIN).
 */
@Entity(
    tableName = "hidden_content",
    indices = [Index(value = ["playlistId", "scope", "restriction", "contentType", "targetId"], unique = true)],
)
data class HiddenContentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val scope: String,
    val restriction: String,
    val contentType: String,
    val targetId: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playback_history",
    primaryKeys = ["playlistId", "contentType", "contentId"],
    indices = [Index(value = ["playlistId", "lastWatchedAt"]), Index(value = ["playlistId", "seriesId"])],
)
data class PlaybackHistoryEntity(
    val playlistId: Long,
    val contentType: String,
    val contentId: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
    val playCount: Int,
    val seriesId: Long?,
    val seasonNum: Int?,
    val episodeNum: Int?,
    val containerExtension: String?,
    val categoryId: String?,
)

/** Per-playlist key/value settings (e.g. last watched channel, last category). Global settings live in DataStore. */
@Entity(tableName = "user_settings", primaryKeys = ["playlistId", "key"])
data class UserSettingsEntity(
    val playlistId: Long,
    val key: String,
    val value: String,
)

@Entity(tableName = "search_history", indices = [Index(value = ["query"], unique = true)])
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val searchedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "epg_cache", primaryKeys = ["playlistId", "streamId", "startTs"], indices = [Index(value = ["playlistId", "streamId", "endTs"])])
data class EpgCacheEntity(
    val playlistId: Long,
    val streamId: Long,
    val startTs: Long,
    val endTs: Long,
    val title: String,
    val description: String?,
    val fetchedAt: Long,
)

@Entity(tableName = "notifications", indices = [Index(value = ["createdAt"])])
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long?,
    val type: String,
    val title: String,
    val message: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
)

/** Projection: per-category item counts. */
data class CategoryCount(
    @ColumnInfo(name = "categoryId") val categoryId: String?,
    @ColumnInfo(name = "cnt") val count: Int,
)

/** Projection: group with item count. */
data class GroupWithCount(
    val id: Long,
    val playlistId: Long,
    val name: String,
    val createdAt: Long,
    val itemCount: Int,
)
