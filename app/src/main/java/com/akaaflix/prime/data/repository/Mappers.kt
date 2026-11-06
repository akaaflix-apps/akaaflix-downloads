package com.akaaflix.prime.data.repository

import com.akaaflix.prime.common.ContentText
import com.akaaflix.prime.database.entity.ChannelEntity
import com.akaaflix.prime.database.entity.FavoriteEntity
import com.akaaflix.prime.database.entity.GroupContentEntity
import com.akaaflix.prime.database.entity.HiddenContentEntity
import com.akaaflix.prime.database.entity.MovieEntity
import com.akaaflix.prime.database.entity.NotificationEntity
import com.akaaflix.prime.database.entity.PlaybackHistoryEntity
import com.akaaflix.prime.database.entity.SeriesEntity
import com.akaaflix.prime.domain.model.AppNotification
import com.akaaflix.prime.domain.model.Channel
import com.akaaflix.prime.domain.model.ContentRef
import com.akaaflix.prime.domain.model.ContentRule
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.model.FavoriteItem
import com.akaaflix.prime.domain.model.GroupItem
import com.akaaflix.prime.domain.model.Movie
import com.akaaflix.prime.domain.model.PlaybackEntry
import com.akaaflix.prime.domain.model.RuleRestriction
import com.akaaflix.prime.domain.model.RuleScope
import com.akaaflix.prime.domain.model.Series

internal const val ANY_TYPE = "ANY"

internal fun ChannelEntity.toDomain() = Channel(
    playlistId = playlistId,
    streamId = streamId,
    number = num,
    name = name,
    logo = logo,
    categoryId = categoryId,
    epgChannelId = epgChannelId,
    hasArchive = tvArchive,
    isAdult = isAdult,
)

internal fun MovieEntity.toDomain() = Movie(
    playlistId = playlistId,
    streamId = streamId,
    name = name,
    poster = poster,
    rating = rating,
    year = year,
    added = added,
    categoryId = categoryId,
    containerExtension = containerExtension,
    quality = ContentText.qualityOf(name),
    isAdult = isAdult,
)

internal fun SeriesEntity.toDomain() = Series(
    playlistId = playlistId,
    seriesId = seriesId,
    name = name,
    cover = cover,
    backdrop = backdrop,
    plot = plot,
    genre = genre,
    rating = rating,
    year = year,
    lastModified = lastModified,
    categoryId = categoryId,
    quality = ContentText.qualityOf(name),
    isAdult = isAdult,
)

internal fun FavoriteEntity.toDomain() = FavoriteItem(
    id = id,
    ref = ContentRef(playlistId, ContentType.of(contentType) ?: ContentType.MOVIE, contentId, title, imageUrl, extra),
    addedAt = addedAt,
)

internal fun GroupContentEntity.toDomain() = GroupItem(
    id = id,
    groupId = groupId,
    ref = ContentRef(playlistId, ContentType.of(contentType) ?: ContentType.MOVIE, contentId, title, imageUrl, extra),
    addedAt = addedAt,
)

internal fun HiddenContentEntity.toDomain() = ContentRule(
    id = id,
    playlistId = playlistId,
    scope = runCatching { RuleScope.valueOf(scope) }.getOrDefault(RuleScope.ITEM),
    restriction = runCatching { RuleRestriction.valueOf(restriction) }.getOrDefault(RuleRestriction.HIDDEN),
    contentType = ContentType.of(contentType),
    targetId = targetId,
    title = title,
    createdAt = createdAt,
)

internal fun PlaybackHistoryEntity.toDomain() = PlaybackEntry(
    playlistId = playlistId,
    type = ContentType.of(contentType) ?: ContentType.MOVIE,
    contentId = contentId,
    title = title,
    subtitle = subtitle,
    imageUrl = imageUrl,
    positionMs = positionMs,
    durationMs = durationMs,
    lastWatchedAt = lastWatchedAt,
    playCount = playCount,
    seriesId = seriesId,
    season = seasonNum,
    episode = episodeNum,
    containerExtension = containerExtension,
    categoryId = categoryId,
)

internal fun PlaybackEntry.toEntity() = PlaybackHistoryEntity(
    playlistId = playlistId,
    contentType = type.name,
    contentId = contentId,
    title = title,
    subtitle = subtitle,
    imageUrl = imageUrl,
    positionMs = positionMs,
    durationMs = durationMs,
    lastWatchedAt = lastWatchedAt,
    playCount = playCount,
    seriesId = seriesId,
    seasonNum = season,
    episodeNum = episode,
    containerExtension = containerExtension,
    categoryId = categoryId,
)

internal fun NotificationEntity.toDomain() = AppNotification(id, playlistId, type, title, message, createdAt, isRead)

fun Channel.toRef() = ContentRef(playlistId, ContentType.LIVE, streamId.toString(), name, logo, categoryId)
fun Movie.toRef() = ContentRef(playlistId, ContentType.MOVIE, streamId.toString(), name, poster, categoryId)
fun Series.toRef() = ContentRef(playlistId, ContentType.SERIES, seriesId.toString(), name, cover, categoryId)
