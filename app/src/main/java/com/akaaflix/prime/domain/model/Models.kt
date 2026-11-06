package com.akaaflix.prime.domain.model

/** Kinds of content handled by the unified favorites / groups / hidden-content systems. */
enum class ContentType { LIVE, MOVIE, SERIES, EPISODE;

    companion object {
        fun of(value: String?): ContentType? = entries.firstOrNull { it.name == value }
    }
}

/** Subscription status as reported by `user_info.status`. */
enum class AccountStatus { ACTIVE, EXPIRED, BANNED, DISABLED, UNKNOWN;

    companion object {
        fun parse(raw: String?): AccountStatus = when (raw?.trim()?.lowercase()) {
            "active" -> ACTIVE
            "expired" -> EXPIRED
            "banned" -> BANNED
            "disabled" -> DISABLED
            else -> UNKNOWN
        }
    }
}

enum class ConnectionStatus { CONNECTED, DISCONNECTED, UNKNOWN }

/** Credentials needed to talk to an Xtream Codes server. Never logged or persisted in plain text. */
data class XtreamCredentials(val baseUrl: String, val username: String, val password: String) {
    override fun toString(): String = "XtreamCredentials(baseUrl=***, username=***, password=***)"
}

/** Account information returned by the authenticated server (source of truth). */
data class AccountInfo(
    val status: AccountStatus,
    val rawStatus: String?,
    val expiresAtEpochSec: Long?,
    val createdAtEpochSec: Long?,
    val isTrial: Boolean,
    val activeConnections: Int?,
    val maxConnections: Int?,
    val allowedOutputFormats: List<String>,
    val message: String?,
    val serverTimezone: String?,
    val serverProtocol: String?,
    val rtmpPort: String?,
    val httpsPort: String?,
    /** Base URL that actually answered (may differ in scheme from user input). */
    val resolvedBaseUrl: String,
)

data class Playlist(
    val id: Long,
    val name: String,
    val serverUrl: String,
    val username: String,
    val isActive: Boolean,
    val isDefault: Boolean,
    val createdAt: Long,
    val lastRefreshAt: Long?,
    val lastCheckedAt: Long?,
    val connectionStatus: ConnectionStatus,
    val accountStatus: AccountStatus,
    val expiresAtEpochSec: Long?,
    val createdAtEpochSec: Long?,
    val isTrial: Boolean,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val allowedOutputFormats: List<String>,
    val serverMessage: String?,
    val serverTimezone: String?,
    val rtmpPort: String?,
    val liveCount: Int,
    val movieCount: Int,
    val seriesCount: Int,
)

data class Category(
    val id: String,
    val name: String,
    val type: ContentType,
    val count: Int = 0,
    val isAdult: Boolean = false,
    val isLocked: Boolean = false,
)

data class Channel(
    val playlistId: Long,
    val streamId: Long,
    val number: Long,
    val name: String,
    val logo: String?,
    val categoryId: String?,
    val epgChannelId: String?,
    val hasArchive: Boolean,
    val isAdult: Boolean,
    val isLocked: Boolean = false,
)

data class Movie(
    val playlistId: Long,
    val streamId: Long,
    val name: String,
    val poster: String?,
    val rating: Double?,
    val year: Int?,
    val added: Long,
    val categoryId: String?,
    val containerExtension: String?,
    val quality: String?,
    val isAdult: Boolean,
    val isLocked: Boolean = false,
)

data class Series(
    val playlistId: Long,
    val seriesId: Long,
    val name: String,
    val cover: String?,
    val backdrop: String?,
    val plot: String?,
    val genre: String?,
    val rating: Double?,
    val year: Int?,
    val lastModified: Long,
    val categoryId: String?,
    val quality: String?,
    val isAdult: Boolean,
    val isLocked: Boolean = false,
)

data class MovieDetails(
    val movie: Movie,
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val releaseDate: String?,
    val durationSec: Long?,
    val backdrop: String?,
    val ageRating: String?,
    val country: String?,
    val trailer: String?,
)

data class Season(val number: Int, val name: String, val cover: String?, val episodeCount: Int, val overview: String?)

data class Episode(
    val id: String,
    val seriesId: Long,
    val season: Int,
    val episodeNumber: Int,
    val title: String,
    val plot: String?,
    val thumbnail: String?,
    val durationSec: Long?,
    val containerExtension: String?,
    val rating: Double?,
    val releaseDate: String?,
)

data class SeriesDetails(
    val series: Series,
    val cast: String?,
    val director: String?,
    val releaseDate: String?,
    val ageRating: String?,
    val seasons: List<Season>,
    val episodesBySeason: Map<Int, List<Episode>>,
) {
    fun allEpisodesOrdered(): List<Episode> = episodesBySeason.toSortedMap().values.flatMap { list -> list.sortedBy { it.episodeNumber } }
}

data class EpgProgram(
    val streamId: Long,
    val title: String,
    val description: String?,
    val startEpochSec: Long,
    val endEpochSec: Long,
) {
    fun isNow(nowSec: Long) = nowSec in startEpochSec until endEpochSec
    fun progress(nowSec: Long): Float =
        if (endEpochSec <= startEpochSec) 0f else ((nowSec - startEpochSec).toFloat() / (endEpochSec - startEpochSec)).coerceIn(0f, 1f)
}

/** Reference to any piece of content inside a playlist (used by favorites, groups, history, rules). */
data class ContentRef(
    val playlistId: Long,
    val type: ContentType,
    val contentId: String,
    val title: String,
    val imageUrl: String?,
    /** Optional extra metadata (e.g. series id for episodes, category id). */
    val extra: String? = null,
)

data class FavoriteItem(val id: Long, val ref: ContentRef, val addedAt: Long)

data class CustomGroup(val id: Long, val playlistId: Long, val name: String, val itemCount: Int, val createdAt: Long, val isHidden: Boolean = false)

data class GroupItem(val id: Long, val groupId: Long, val ref: ContentRef, val addedAt: Long)

/** Scope of a hidden / locked rule. */
enum class RuleScope { CATEGORY, ITEM, GROUP, KEYWORD }

/** What a rule does: remove from browsing, or require the parental PIN. */
enum class RuleRestriction { HIDDEN, PIN_LOCKED }

data class ContentRule(
    val id: Long,
    val playlistId: Long,
    val scope: RuleScope,
    val restriction: RuleRestriction,
    /** LIVE / MOVIE / SERIES for category & item rules; null for group / keyword rules. */
    val contentType: ContentType?,
    val targetId: String,
    val title: String,
    val createdAt: Long,
)

data class PlaybackEntry(
    val playlistId: Long,
    val type: ContentType,
    val contentId: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
    val playCount: Int,
    val seriesId: Long?,
    val season: Int?,
    val episode: Int?,
    val containerExtension: String?,
    val categoryId: String?,
) {
    val progress: Float get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

data class AppNotification(
    val id: Long,
    val playlistId: Long?,
    val type: String,
    val title: String,
    val message: String,
    val createdAt: Long,
    val isRead: Boolean,
)

/** Sorting options for movie / series / channel grids. */
enum class SortOrder { DEFAULT, LATEST, NAME_ASC, NAME_DESC, RATING, YEAR }

/** Special (non-category) browse filters. */
enum class BrowseFilter { ALL, LATEST, MOST_WATCHED, FAVORITES, CATEGORY, GROUP, COUNTRY, KIND }
