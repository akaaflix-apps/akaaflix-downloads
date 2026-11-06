package com.akaaflix.prime.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.akaaflix.prime.R
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.data.settings.PlayerChoice
import com.akaaflix.prime.data.settings.SettingsRepository
import com.akaaflix.prime.di.AppContainer
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.model.PlaybackEntry
import com.akaaflix.prime.player.StreamSource
import com.akaaflix.prime.player.StreamUrlBuilder
import com.akaaflix.prime.presentation.components.Messenger
import com.akaaflix.prime.presentation.components.PinGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

val LocalNavigator = staticCompositionLocalOf<AppNavigator> { error("AppNavigator not provided") }

/**
 * Central navigation + playback launcher. Every "open"/"play" action goes through the
 * parental [PinGate] so locked or age-restricted content is never revealed without the PIN,
 * and respects the user's default player (built-in ExoPlayer or an installed external player).
 */
@Stable
class AppNavigator(
    val nav: NavHostController,
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val pinGate: PinGate,
    private val messenger: Messenger,
) {
    private val settings: SettingsRepository get() = container.settingsRepository

    fun back() { if (!nav.popBackStack()) Unit }

    /** Switches between top-level sections, keeping a single copy of each on the back stack. */
    fun navigateTopLevel(route: Any) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun goHomeClearingStack() {
        nav.navigate(HomeRoute) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun goToPlaylists(selectMode: Boolean, clearStack: Boolean) {
        nav.navigate(PlaylistsRoute(selectMode)) {
            if (clearStack) popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun addPlaylist(firstRun: Boolean = false) = nav.navigate(EditPlaylistRoute(-1L, firstRun)) {
        if (firstRun) popUpTo(0) { inclusive = true }
    }
    fun editPlaylist(id: Long) = nav.navigate(EditPlaylistRoute(id))

    fun openMovie(streamId: Long, categoryId: String?) =
        pinGate.guard(ContentType.MOVIE, streamId.toString(), categoryId) { nav.navigate(MovieDetailsRoute(streamId)) }

    fun openSeries(seriesId: Long, categoryId: String?) =
        pinGate.guard(ContentType.SERIES, seriesId.toString(), categoryId) { nav.navigate(SeriesDetailsRoute(seriesId)) }

    /** Re-runs the catalog download for a playlist (used by empty / error states). */
    fun retrySync(playlistId: Long) = container.sessionManager.syncInBackground(playlistId)

    fun openGroup(groupId: Long) = nav.navigate(GroupRoute(groupId))
    fun openSearch() = nav.navigate(SearchRoute) { launchSingleTop = true }
    fun openNotifications() = nav.navigate(NotificationsRoute) { launchSingleTop = true }
    fun openAccount() = nav.navigate(AccountRoute) { launchSingleTop = true }
    fun openSettings(section: String? = null) = nav.navigate(SettingsRoute(section)) { launchSingleTop = true }

    // ------------------------------------------------------------------ Playback

    fun playChannel(streamId: Long, categoryId: String?) {
        pinGate.guard(ContentType.LIVE, streamId.toString(), categoryId) {
            launchPlayback(ContentType.LIVE, streamId.toString(), categoryId, -1L, -1L)
        }
    }

    /** Plays a movie. [ageRating] (from details) is checked against the parental age limit. */
    fun playMovie(streamId: Long, categoryId: String?, startMs: Long = -1L, ageRating: String? = null) {
        pinGate.guard(ContentType.MOVIE, streamId.toString(), categoryId, ageRating) {
            launchPlayback(ContentType.MOVIE, streamId.toString(), categoryId, -1L, startMs)
        }
    }

    fun playEpisode(seriesId: Long, episodeId: String, categoryId: String?, startMs: Long = -1L, ageRating: String? = null) {
        pinGate.guard(ContentType.SERIES, seriesId.toString(), categoryId, ageRating) {
            launchPlayback(ContentType.EPISODE, episodeId, categoryId, seriesId, startMs)
        }
    }

    private fun launchPlayback(type: ContentType, contentId: String, categoryId: String?, seriesId: Long, startMs: Long) {
        scope.launch(Dispatchers.Main.immediate) {
            val s = settings.current()
            if (s.defaultPlayer != PlayerChoice.BUILT_IN && container.externalPlayers.isAvailable(s.defaultPlayer)) {
                if (launchExternal(s.defaultPlayer, type, contentId, seriesId, startMs)) return@launch
                messenger.show(R.string.msg_external_player_failed)
            } else if (s.defaultPlayer != PlayerChoice.BUILT_IN) {
                messenger.show(R.string.msg_external_player_missing)
            }
            nav.navigate(PlayerRoute(type.name, contentId, categoryId, seriesId, startMs))
        }
    }

    private suspend fun launchExternal(choice: PlayerChoice, type: ContentType, contentId: String, seriesId: Long, startMs: Long): Boolean {
        val playlist = container.sessionManager.activePlaylist.value ?: return false
        val creds = container.playlistRepository.credentials(playlist.id) ?: return false
        val s = settings.current()
        val (source: StreamSource, title: String, image: String?, ext: String?) = when (type) {
            ContentType.LIVE -> {
                val ch = container.catalogRepository.getChannel(playlist.id, contentId.toLong()) ?: return false
                val fmt = StreamUrlBuilder.resolveLiveFormat(s.liveFormat, playlist.allowedOutputFormats, playlist.rtmpPort)
                Quad(StreamUrlBuilder.live(creds, ch.streamId, fmt, playlist.rtmpPort), ch.name, ch.logo, null)
            }
            ContentType.MOVIE -> {
                val m = container.catalogRepository.getMovie(playlist.id, contentId.toLong()) ?: return false
                Quad(StreamUrlBuilder.movie(creds, m.streamId, m.containerExtension), m.name, m.poster, m.containerExtension)
            }
            ContentType.EPISODE -> {
                val details = (container.catalogRepository.seriesDetails(playlist.id, seriesId) as? AppResult.Success)?.data ?: return false
                val ep = details.allEpisodesOrdered().firstOrNull { it.id == contentId } ?: return false
                Quad(StreamUrlBuilder.episode(creds, ep.id, ep.containerExtension), "${details.series.name} · S${ep.season}E${ep.episodeNumber}", ep.thumbnail ?: details.series.cover, ep.containerExtension)
            }
            ContentType.SERIES -> return false
        }
        val ok = container.externalPlayers.launch(choice, source, title, startMs.coerceAtLeast(0))
        if (ok) {
            container.historyRepository.save(
                PlaybackEntry(
                    playlistId = playlist.id, type = type, contentId = contentId, title = title, subtitle = null, imageUrl = image,
                    positionMs = startMs.coerceAtLeast(0), durationMs = 0, lastWatchedAt = System.currentTimeMillis(), playCount = 1,
                    seriesId = seriesId.takeIf { it > 0 }, season = null, episode = null, containerExtension = ext, categoryId = null,
                ),
                incrementPlayCount = true,
            )
        }
        return ok
    }

    private data class Quad(val source: StreamSource, val title: String, val image: String?, val ext: String?)
}
