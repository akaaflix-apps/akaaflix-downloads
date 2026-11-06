package com.akaaflix.prime.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations (Navigation Compose 2.8). */
@Serializable data object SplashRoute
@Serializable data class PlaylistsRoute(val selectMode: Boolean = false)
@Serializable data class EditPlaylistRoute(val playlistId: Long = -1L, val firstRun: Boolean = false)
@Serializable data object HomeRoute
@Serializable data class LiveTvRoute(val categoryId: String? = null)
@Serializable data class MoviesRoute(val categoryId: String? = null)
@Serializable data class SeriesRoute(val categoryId: String? = null)
@Serializable data class MyListRoute(val tab: Int = 0)
@Serializable data class GroupRoute(val groupId: Long)
@Serializable data class MovieDetailsRoute(val streamId: Long)
@Serializable data class SeriesDetailsRoute(val seriesId: Long)

/**
 * Built-in player. [type] = LIVE / MOVIE / EPISODE. For episodes [contentId] is the episode id
 * and [seriesId] the parent series; for live [categoryId] scopes channel up/down.
 */
@Serializable
data class PlayerRoute(
    val type: String,
    val contentId: String,
    val categoryId: String? = null,
    val seriesId: Long = -1L,
    val startMs: Long = -1L,
)

@Serializable data object SearchRoute
@Serializable data object AccountRoute
@Serializable data class SettingsRoute(val section: String? = null)
@Serializable data object NotificationsRoute
