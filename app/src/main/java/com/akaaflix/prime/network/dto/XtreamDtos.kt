package com.akaaflix.prime.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Response of `player_api.php?username=..&password=..` (no action). */
@Serializable
data class AuthResponseDto(
    @SerialName("user_info") val userInfo: UserInfoDto? = null,
    @SerialName("server_info") val serverInfo: ServerInfoDto? = null,
)

@Serializable
data class UserInfoDto(
    @Serializable(with = FlexStringSerializer::class) val username: String? = null,
    // NOTE: the panel echoes the password back. It is intentionally never read or logged.
    @Serializable(with = FlexStringSerializer::class) val message: String? = null,
    @Serializable(with = FlexLongSerializer::class) val auth: Long? = null,
    @Serializable(with = FlexStringSerializer::class) val status: String? = null,
    @SerialName("exp_date") @Serializable(with = FlexLongSerializer::class) val expDate: Long? = null,
    @SerialName("is_trial") @Serializable(with = FlexLongSerializer::class) val isTrial: Long? = null,
    @SerialName("active_cons") @Serializable(with = FlexLongSerializer::class) val activeConnections: Long? = null,
    @SerialName("created_at") @Serializable(with = FlexLongSerializer::class) val createdAt: Long? = null,
    @SerialName("max_connections") @Serializable(with = FlexLongSerializer::class) val maxConnections: Long? = null,
    @SerialName("allowed_output_formats") @Serializable(with = FlexStringListSerializer::class)
    val allowedOutputFormats: List<String> = emptyList(),
)

@Serializable
data class ServerInfoDto(
    @Serializable(with = FlexStringSerializer::class) val url: String? = null,
    @Serializable(with = FlexStringSerializer::class) val port: String? = null,
    @SerialName("https_port") @Serializable(with = FlexStringSerializer::class) val httpsPort: String? = null,
    @SerialName("server_protocol") @Serializable(with = FlexStringSerializer::class) val serverProtocol: String? = null,
    @SerialName("rtmp_port") @Serializable(with = FlexStringSerializer::class) val rtmpPort: String? = null,
    @Serializable(with = FlexStringSerializer::class) val timezone: String? = null,
    @SerialName("timestamp_now") @Serializable(with = FlexLongSerializer::class) val timestampNow: Long? = null,
)

@Serializable
data class CategoryDto(
    @SerialName("category_id") @Serializable(with = FlexStringSerializer::class) val categoryId: String? = null,
    @SerialName("category_name") @Serializable(with = FlexStringSerializer::class) val categoryName: String? = null,
    @SerialName("parent_id") @Serializable(with = FlexStringSerializer::class) val parentId: String? = null,
)

@Serializable
data class LiveStreamDto(
    @Serializable(with = FlexLongSerializer::class) val num: Long? = null,
    @Serializable(with = FlexStringSerializer::class) val name: String? = null,
    @SerialName("stream_id") @Serializable(with = FlexLongSerializer::class) val streamId: Long? = null,
    @SerialName("stream_icon") @Serializable(with = FlexStringSerializer::class) val streamIcon: String? = null,
    @SerialName("epg_channel_id") @Serializable(with = FlexStringSerializer::class) val epgChannelId: String? = null,
    @Serializable(with = FlexLongSerializer::class) val added: Long? = null,
    @SerialName("is_adult") @Serializable(with = FlexLongSerializer::class) val isAdult: Long? = null,
    @SerialName("category_id") @Serializable(with = FlexStringSerializer::class) val categoryId: String? = null,
    @SerialName("tv_archive") @Serializable(with = FlexLongSerializer::class) val tvArchive: Long? = null,
    @SerialName("tv_archive_duration") @Serializable(with = FlexLongSerializer::class) val tvArchiveDuration: Long? = null,
)

@Serializable
data class VodStreamDto(
    @Serializable(with = FlexLongSerializer::class) val num: Long? = null,
    @Serializable(with = FlexStringSerializer::class) val name: String? = null,
    @Serializable(with = FlexStringSerializer::class) val title: String? = null,
    @SerialName("stream_id") @Serializable(with = FlexLongSerializer::class) val streamId: Long? = null,
    @SerialName("stream_icon") @Serializable(with = FlexStringSerializer::class) val streamIcon: String? = null,
    @Serializable(with = FlexDoubleSerializer::class) val rating: Double? = null,
    @SerialName("rating_5based") @Serializable(with = FlexDoubleSerializer::class) val rating5: Double? = null,
    @Serializable(with = FlexLongSerializer::class) val added: Long? = null,
    @SerialName("is_adult") @Serializable(with = FlexLongSerializer::class) val isAdult: Long? = null,
    @SerialName("category_id") @Serializable(with = FlexStringSerializer::class) val categoryId: String? = null,
    @SerialName("container_extension") @Serializable(with = FlexStringSerializer::class) val containerExtension: String? = null,
    @Serializable(with = FlexStringSerializer::class) val year: String? = null,
    @SerialName("release_date") @Serializable(with = FlexStringSerializer::class) val releaseDate: String? = null,
    @Serializable(with = FlexStringSerializer::class) val genre: String? = null,
    @Serializable(with = FlexStringSerializer::class) val plot: String? = null,
)

@Serializable
data class SeriesDto(
    @Serializable(with = FlexLongSerializer::class) val num: Long? = null,
    @Serializable(with = FlexStringSerializer::class) val name: String? = null,
    @SerialName("series_id") @Serializable(with = FlexLongSerializer::class) val seriesId: Long? = null,
    @Serializable(with = FlexStringSerializer::class) val cover: String? = null,
    @Serializable(with = FlexStringSerializer::class) val plot: String? = null,
    @Serializable(with = FlexStringSerializer::class) val cast: String? = null,
    @Serializable(with = FlexStringSerializer::class) val director: String? = null,
    @Serializable(with = FlexStringSerializer::class) val genre: String? = null,
    @Serializable(with = FlexStringSerializer::class) val releaseDate: String? = null,
    @SerialName("release_date") @Serializable(with = FlexStringSerializer::class) val releaseDate2: String? = null,
    @SerialName("last_modified") @Serializable(with = FlexLongSerializer::class) val lastModified: Long? = null,
    @Serializable(with = FlexDoubleSerializer::class) val rating: Double? = null,
    @SerialName("rating_5based") @Serializable(with = FlexDoubleSerializer::class) val rating5: Double? = null,
    @SerialName("backdrop_path") @Serializable(with = FlexStringListSerializer::class) val backdropPath: List<String> = emptyList(),
    @SerialName("episode_run_time") @Serializable(with = FlexStringSerializer::class) val episodeRunTime: String? = null,
    @SerialName("category_id") @Serializable(with = FlexStringSerializer::class) val categoryId: String? = null,
    @SerialName("is_adult") @Serializable(with = FlexLongSerializer::class) val isAdult: Long? = null,
)

/** `get_vod_info` — `info` may be an object or an empty array on some panels. */
@Serializable
data class VodInfoResponseDto(
    val info: JsonElement? = null,
    @SerialName("movie_data") val movieData: JsonElement? = null,
)

/** `get_series_info` — `episodes` is usually a map of season -> list, sometimes a list. */
@Serializable
data class SeriesInfoResponseDto(
    val seasons: JsonElement? = null,
    val info: JsonElement? = null,
    val episodes: JsonElement? = null,
)

@Serializable
data class ShortEpgResponseDto(
    @SerialName("epg_listings") val listings: List<EpgListingDto> = emptyList(),
)

@Serializable
data class EpgListingDto(
    @Serializable(with = FlexStringSerializer::class) val title: String? = null,
    @Serializable(with = FlexStringSerializer::class) val description: String? = null,
    @Serializable(with = FlexStringSerializer::class) val lang: String? = null,
    @SerialName("start_timestamp") @Serializable(with = FlexLongSerializer::class) val startTimestamp: Long? = null,
    @SerialName("stop_timestamp") @Serializable(with = FlexLongSerializer::class) val stopTimestamp: Long? = null,
    @Serializable(with = FlexStringSerializer::class) val start: String? = null,
    @Serializable(with = FlexStringSerializer::class) val end: String? = null,
)
