@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.akaaflix.prime.player

import android.content.ContentResolver
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.toRoute
import com.akaaflix.prime.R
import com.akaaflix.prime.common.AppError
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.data.SessionManager
import com.akaaflix.prime.data.repository.PlaylistPrefs
import com.akaaflix.prime.data.settings.AppSettings
import com.akaaflix.prime.data.settings.AspectMode
import com.akaaflix.prime.data.settings.LiveFormat
import com.akaaflix.prime.data.settings.SettingsRepository
import com.akaaflix.prime.data.settings.SubtitleSource
import com.akaaflix.prime.domain.model.Channel
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.model.Episode
import com.akaaflix.prime.domain.model.EpgProgram
import com.akaaflix.prime.domain.model.PlaybackEntry
import com.akaaflix.prime.domain.model.Playlist
import com.akaaflix.prime.domain.model.SeriesDetails
import com.akaaflix.prime.domain.model.XtreamCredentials
import com.akaaflix.prime.domain.repository.BrowseQuery
import com.akaaflix.prime.domain.repository.CatalogRepository
import com.akaaflix.prime.domain.repository.ContentRulesRepository
import com.akaaflix.prime.domain.repository.EpgRepository
import com.akaaflix.prime.domain.repository.HistoryRepository
import com.akaaflix.prime.domain.repository.PlaylistRepository
import com.akaaflix.prime.navigation.PlayerRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class TrackOption(val groupIndex: Int, val trackIndex: Int, val label: String, val selected: Boolean)

data class PlayerUiState(
    val title: String = "",
    val subtitle: String? = null,
    val isLive: Boolean = false,
    val isPlaying: Boolean = false,
    val buffering: Boolean = true,
    @StringRes val errorRes: Int? = null,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val speed: Float = 1f,
    val aspect: AspectMode = AspectMode.FIT,
    val liveFormat: LiveFormat? = null,
    val externalSubtitleName: String? = null,
    val upNextSeconds: Int? = null,
    val nowPlaying: EpgProgram? = null,
    val channelNumber: Long? = null,
    val tracksVersion: Int = 0,
    val formatFallbackNotice: Boolean = false,
)

/**
 * Drives the built-in ExoPlayer: resolves stream URLs, handles resume, next/previous episode,
 * channel up/down, retries & HLS<->TS fallback, expired-session detection, progress saving,
 * audio/subtitle/quality tracks and external subtitles with live delay adjustment.
 */
class PlayerViewModel(
    handle: SavedStateHandle,
    private val session: SessionManager,
    private val settingsRepo: SettingsRepository,
    private val playlists: PlaylistRepository,
    private val catalog: CatalogRepository,
    private val history: HistoryRepository,
    private val epg: EpgRepository,
    private val rules: ContentRulesRepository,
    private val prefs: PlaylistPrefs,
    private val factory: PlayerFactory,
) : ViewModel() {

    private val route = handle.toRoute<PlayerRoute>()
    val type: ContentType = ContentType.of(route.type) ?: ContentType.MOVIE

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()
    private val _state = MutableStateFlow(PlayerUiState(isLive = type == ContentType.LIVE))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    private val _cues = MutableStateFlow<List<Cue>>(emptyList())
    /** Subtitle cues to render (embedded with delay, or parsed external file). */
    val cues: StateFlow<List<Cue>> = _cues.asStateFlow()
    val settings: StateFlow<AppSettings?> = MutableStateFlow<AppSettings?>(null).also { flow ->
        viewModelScope.launch { settingsRepo.settings.collect { flow.value = it; applyLiveSettings(it) } }
    }

    private var playlist: Playlist? = null
    private var creds: XtreamCredentials? = null
    private var channels: List<Channel> = emptyList()
    private var channelIndex = -1
    private var series: SeriesDetails? = null
    private var episodes: List<Episode> = emptyList()
    private var episodeIndex = -1
    private var movieMeta: Triple<String, String?, String?>? = null // title, image, ext

    private var currentLiveFormat: LiveFormat? = null
    private val triedFormats = mutableSetOf<LiveFormat>()
    private var retryCount = 0
    private var progressJob: Job? = null
    private var upNextJob: Job? = null
    private var pendingStartMs: Long = route.startMs
    private var externalCues: List<SubtitleCue> = emptyList()
    private val embeddedQueue = ArrayDeque<Pair<Long, List<Cue>>>()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) saveProgress()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE && it.errorRes == null) }
            if (playbackState == Player.STATE_READY) {
                retryCount = 0
                _state.update { it.copy(errorRes = null) }
            }
            if (playbackState == Player.STATE_ENDED) onEnded()
        }

        override fun onPlayerError(error: PlaybackException) = handleError(error)

        override fun onTracksChanged(tracks: Tracks) {
            _state.update { it.copy(tracksVersion = it.tracksVersion + 1) }
        }

        override fun onCues(cueGroup: CueGroup) {
            if (externalCues.isNotEmpty()) return
            embeddedQueue.addLast(SystemClock.elapsedRealtime() to cueGroup.cues)
            if (embeddedQueue.size > 64) embeddedQueue.removeFirst()
        }
    }

    init {
        viewModelScope.launch {
            val s = settingsRepo.current()
            val p = session.activePlaylist.value ?: playlists.observeActive().first()
            if (p == null) { fail(R.string.player_error_no_playlist); return@launch }
            playlist = p
            creds = playlists.credentials(p.id) ?: run { fail(R.string.player_error_no_playlist); return@launch }
            val exo = factory.create(s)
            exo.addListener(listener)
            _player.value = exo
            _state.update { it.copy(aspect = s.defaultAspect) }
            prepareQueue()
            playCurrent()
            startTickers()
        }
    }

    // ------------------------------------------------------------------ Queue

    private suspend fun prepareQueue() {
        val p = playlist ?: return
        when (type) {
            ContentType.LIVE -> {
                val s = settingsRepo.current()
                channels = catalog.channelList(BrowseQuery(p.id, ContentType.LIVE, categoryId = route.categoryId, includeAdult = !s.shouldHideAdult || session.adultUnlocked.value))
                channelIndex = channels.indexOfFirst { it.streamId.toString() == route.contentId }
                if (channelIndex < 0) {
                    catalog.getChannel(p.id, route.contentId.toLong())?.let { channels = listOf(it); channelIndex = 0 }
                }
            }
            ContentType.MOVIE -> {
                val m = catalog.getMovie(p.id, route.contentId.toLong())
                movieMeta = Triple(m?.name ?: "", m?.poster, m?.containerExtension)
                // Prefer the container extension from details when available.
                (catalog.movieDetails(p.id, route.contentId.toLong()) as? AppResult.Success)?.data?.let { d ->
                    movieMeta = Triple(d.movie.name, d.movie.poster, d.movie.containerExtension)
                }
            }
            ContentType.EPISODE -> {
                (catalog.seriesDetails(p.id, route.seriesId) as? AppResult.Success)?.data?.let { d ->
                    series = d
                    episodes = d.allEpisodesOrdered()
                    episodeIndex = episodes.indexOfFirst { it.id == route.contentId }
                }
            }
            ContentType.SERIES -> Unit
        }
    }

    private fun currentChannel(): Channel? = channels.getOrNull(channelIndex)
    private fun currentEpisode(): Episode? = episodes.getOrNull(episodeIndex)

    private fun playCurrent() = viewModelScope.launch {
        val exo = _player.value ?: return@launch
        val p = playlist ?: return@launch
        val c = creds ?: return@launch
        val s = settingsRepo.current()
        upNextJob?.cancel()
        _state.update { it.copy(errorRes = null, buffering = true, upNextSeconds = null, nowPlaying = null) }
        externalCues = emptyList(); _cues.value = emptyList(); embeddedQueue.clear()
        _state.update { it.copy(externalSubtitleName = null) }

        val source: StreamSource
        when (type) {
            ContentType.LIVE -> {
                val ch = currentChannel() ?: return@launch fail(R.string.player_error_unavailable)
                val fmt = currentLiveFormat ?: StreamUrlBuilder.resolveLiveFormat(s.liveFormat, p.allowedOutputFormats, p.rtmpPort)
                currentLiveFormat = fmt
                triedFormats += fmt
                source = StreamUrlBuilder.live(c, ch.streamId, fmt, p.rtmpPort)
                _state.update {
                    it.copy(
                        title = ch.name, subtitle = null, isLive = true, liveFormat = source.format, channelNumber = ch.number,
                        hasNext = channels.size > 1, hasPrevious = channels.size > 1,
                    )
                }
                prefs.put(p.id, PlaylistPrefs.LAST_CHANNEL, ch.streamId.toString())
                history.save(entry(ContentType.LIVE, ch.streamId.toString(), ch.name, null, ch.logo, 0, 0, categoryId = ch.categoryId), incrementPlayCount = true)
                loadNowPlaying(ch)
            }
            ContentType.MOVIE -> {
                val (title, _, ext) = movieMeta ?: Triple("", null, null)
                source = StreamUrlBuilder.movie(c, route.contentId.toLong(), ext)
                _state.update { it.copy(title = title, subtitle = null, isLive = false, hasNext = false, hasPrevious = false) }
            }
            ContentType.EPISODE -> {
                val ep = currentEpisode() ?: return@launch fail(R.string.player_error_unavailable)
                source = StreamUrlBuilder.episode(c, ep.id, ep.containerExtension)
                _state.update {
                    it.copy(
                        title = series?.series?.name ?: ep.title,
                        subtitle = "S${ep.season} · E${ep.episodeNumber}  ${ep.title}",
                        isLive = false,
                        hasNext = episodeIndex in 0 until episodes.lastIndex,
                        hasPrevious = episodeIndex > 0,
                    )
                }
            }
            ContentType.SERIES -> return@launch
        }

        val item = MediaItem.Builder()
            .setUri(source.url)
            .apply { source.mimeType?.let { setMimeType(it) } }
            .apply {
                if (type == ContentType.LIVE) setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setMaxPlaybackSpeed(1.02f).build())
            }
            .build()
        exo.setMediaItem(item)
        exo.prepare()

        // Resume position for VOD.
        if (type != ContentType.LIVE) {
            val start = resolveStartPosition(s)
            if (start > 0) exo.seekTo(start)
        }
        exo.setPlaybackSpeed(if (type == ContentType.LIVE) 1f else _state.value.speed)
        exo.playWhenReady = s.autoPlay || type == ContentType.LIVE || pendingStartMs >= 0
        pendingStartMs = -1L
    }

    private suspend fun resolveStartPosition(s: AppSettings): Long {
        if (pendingStartMs >= 0) return pendingStartMs
        if (!s.resumePlayback) return 0
        val p = playlist ?: return 0
        val id = if (type == ContentType.MOVIE) route.contentId else currentEpisode()?.id ?: return 0
        val e = history.get(p.id, type, id) ?: return 0
        return if (e.positionMs > 30_000 && (e.durationMs <= 0 || e.positionMs < e.durationMs * 0.95)) e.positionMs else 0
    }

    private fun loadNowPlaying(ch: Channel) = viewModelScope.launch {
        if (!settingsRepo.current().epgEnabled) return@launch
        val r = epg.shortEpg(ch.playlistId, ch.streamId)
        if (r is AppResult.Success) {
            val now = System.currentTimeMillis() / 1000 - settingsRepo.current().epgTimeShiftHours * 3600L
            _state.update { it.copy(nowPlaying = r.data.firstOrNull { p -> p.isNow(now) }) }
        }
    }

    // ------------------------------------------------------------------ Navigation inside the player

    fun next() {
        saveProgress()
        when (type) {
            ContentType.LIVE -> if (channels.size > 1) { channelIndex = (channelIndex + 1) % channels.size; switchChannel() }
            ContentType.EPISODE -> if (episodeIndex in 0 until episodes.lastIndex) { episodeIndex++; pendingStartMs = -1; playCurrent() }
            else -> Unit
        }
    }

    fun previous() {
        saveProgress()
        when (type) {
            ContentType.LIVE -> if (channels.size > 1) { channelIndex = (channelIndex - 1 + channels.size) % channels.size; switchChannel() }
            ContentType.EPISODE -> if (episodeIndex > 0) { episodeIndex--; pendingStartMs = -1; playCurrent() }
            else -> Unit
        }
    }

    fun playChannelAt(index: Int) {
        if (index !in channels.indices) return
        channelIndex = index
        switchChannel()
    }

    /** Channel zapping skips PIN-locked channels unless the PIN grace period is active. */
    private fun switchChannel() = viewModelScope.launch {
        val p = playlist ?: return@launch
        val s = settingsRepo.current()
        if (s.parentalEnabled && s.hasPin && !session.isPinGraceActive()) {
            var guard = 0
            while (guard++ < channels.size) {
                val ch = currentChannel() ?: break
                if (!rules.isLocked(p.id, ContentType.LIVE, ch.streamId.toString(), ch.categoryId)) break
                channelIndex = (channelIndex + 1) % channels.size
            }
        }
        currentLiveFormat = null
        triedFormats.clear()
        retryCount = 0
        playCurrent()
    }

    fun channelList(): List<Channel> = channels
    fun currentChannelIndex(): Int = channelIndex

    private fun onEnded() {
        saveProgress(completed = true)
        if (type != ContentType.EPISODE || !(episodeIndex in 0 until episodes.lastIndex)) return
        viewModelScope.launch {
            if (!settingsRepo.current().autoNextEpisode) return@launch
            upNextJob = launch {
                for (sec in 8 downTo 1) {
                    _state.update { it.copy(upNextSeconds = sec) }
                    delay(1_000)
                }
                _state.update { it.copy(upNextSeconds = null) }
                next()
            }
        }
    }

    fun cancelUpNext() { upNextJob?.cancel(); _state.update { it.copy(upNextSeconds = null) } }

    // ------------------------------------------------------------------ Errors

    private fun fail(@StringRes res: Int) {
        _state.update { it.copy(errorRes = res, buffering = false) }
    }

    private fun handleError(error: PlaybackException) {
        val exo = _player.value ?: return
        saveProgress()
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            exo.seekToDefaultPosition()
            exo.prepare()
            return
        }
        val httpCode = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        val isNetwork = error.errorCode in setOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_TIMEOUT,
        )
        val isFormat = error.errorCode in setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        )
        viewModelScope.launch {
            // Live: try the alternative container (HLS <-> MPEG-TS) before giving up.
            if (type == ContentType.LIVE && (isFormat || httpCode == 404 || httpCode == 400 || error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && httpCode !in setOf(401, 403))) {
                val fb = currentLiveFormat?.let { StreamUrlBuilder.fallbackLiveFormat(it, playlist?.allowedOutputFormats.orEmpty()) }
                if (fb != null && fb !in triedFormats) {
                    currentLiveFormat = fb
                    _state.update { it.copy(formatFallbackNotice = true) }
                    playCurrent()
                    return@launch
                }
            }
            if (isNetwork && retryCount < MAX_RETRIES) {
                retryCount++
                _state.update { it.copy(buffering = true) }
                delay(1_500L * retryCount)
                exo.prepare()
                exo.playWhenReady = true
                return@launch
            }
            if (httpCode == 401 || httpCode == 403 || httpCode == 509 || httpCode == 458) {
                // Distinguish expired/invalid accounts from a connection-limit refusal.
                val pid = playlist?.id
                val acct = pid?.let { playlists.refreshAccount(it) }
                val err = (acct as? AppResult.Failure)?.error
                fail(
                    when (err) {
                        AppError.AccountExpired -> R.string.error_account_expired
                        AppError.InvalidCredentials -> R.string.error_invalid_credentials
                        AppError.AccountBanned, AppError.AccountDisabled -> R.string.error_account_disabled
                        else -> R.string.player_error_auth
                    },
                )
                return@launch
            }
            fail(
                when {
                    isNetwork -> R.string.player_error_network
                    isFormat -> R.string.player_error_format
                    httpCode != null -> R.string.player_error_unavailable
                    else -> R.string.player_error_generic
                },
            )
        }
    }

    fun retry() {
        retryCount = 0
        if (type == ContentType.LIVE) { currentLiveFormat = null; triedFormats.clear() }
        pendingStartMs = _player.value?.currentPosition?.takeIf { type != ContentType.LIVE && it > 0 } ?: -1L
        playCurrent()
    }

    fun dismissFormatNotice() = _state.update { it.copy(formatFallbackNotice = false) }

    // ------------------------------------------------------------------ Controls

    fun togglePlay() {
        val exo = _player.value ?: return
        if (exo.playbackState == Player.STATE_ENDED) { exo.seekTo(0); exo.play(); return }
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun seekBy(deltaMs: Long) {
        val exo = _player.value ?: return
        if (type == ContentType.LIVE && !exo.isCurrentMediaItemSeekable) return
        val dur = exo.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        exo.seekTo((exo.currentPosition + deltaMs).coerceIn(0, dur))
    }

    fun seekTo(ms: Long) { _player.value?.seekTo(ms.coerceAtLeast(0)) }

    fun setSpeed(speed: Float) {
        _player.value?.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun setAspect(mode: AspectMode) = _state.update { it.copy(aspect = mode) }

    fun setMuted(muted: Boolean) { _player.value?.volume = if (muted) 0f else 1f }
    fun isMuted(): Boolean = (_player.value?.volume ?: 1f) == 0f

    fun pauseForBackground() { _player.value?.pause(); saveProgress() }

    // ------------------------------------------------------------------ Tracks

    fun tracks(trackType: Int): List<TrackOption> {
        val exo = _player.value ?: return emptyList()
        return exo.currentTracks.groups.withIndex().filter { it.value.type == trackType }.flatMap { (gi, g) ->
            (0 until g.length).filter { g.isTrackSupported(it) }.map { ti ->
                TrackOption(gi, ti, labelFor(g.getTrackFormat(ti), trackType), g.isTrackSelected(ti))
            }
        }
    }

    fun isTextDisabled(): Boolean = _player.value?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) == true

    fun selectTrack(option: TrackOption, trackType: Int) {
        val exo = _player.value ?: return
        val group = exo.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        if (trackType == C.TRACK_TYPE_TEXT) { externalCues = emptyList(); _state.update { it.copy(externalSubtitleName = null) } }
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(trackType, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .build()
    }

    fun autoVideoQuality() {
        val exo = _player.value ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
    }

    fun disableSubtitles() {
        val exo = _player.value ?: return
        externalCues = emptyList()
        _cues.value = emptyList()
        embeddedQueue.clear()
        _state.update { it.copy(externalSubtitleName = null) }
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
    }

    /** Loads an external .srt / .vtt file chosen by the user (Storage Access Framework). */
    fun loadExternalSubtitle(resolver: ContentResolver, uri: Uri, displayName: String?, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val encoding = settingsRepo.current().subtitleEncoding
        val parsed = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching emptyList()
                if (bytes.size > 5_000_000) return@runCatching emptyList()
                SubtitleParser.parse(SubtitleParser.decode(bytes, encoding))
            }.getOrDefault(emptyList())
        }
        if (parsed.isEmpty()) { onResult(false); return@launch }
        externalCues = parsed
        embeddedQueue.clear()
        _player.value?.let { exo ->
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        }
        _state.update { it.copy(externalSubtitleName = displayName ?: "subtitle") }
        onResult(true)
    }

    fun adjustSubtitleDelay(deltaMs: Long) = viewModelScope.launch {
        settingsRepo.update { it.copy(subtitleDelayMs = (it.subtitleDelayMs + deltaMs).coerceIn(-30_000, 30_000)) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { settingsRepo.update(transform) }

    private fun applyLiveSettings(s: AppSettings) {
        val exo = _player.value ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage(s.subtitleLanguage.ifBlank { null })
            .apply { if (!s.subtitlesEnabled || s.subtitleSource == SubtitleSource.EXTERNAL_ONLY) setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true) }
            .build()
        if (!s.subtitlesEnabled) { _cues.value = emptyList(); embeddedQueue.clear() }
    }

    private fun labelFor(f: Format, trackType: Int): String {
        val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()).ifBlank { it } }
        return when (trackType) {
            C.TRACK_TYPE_VIDEO -> listOfNotNull(
                if (f.height > 0) "${f.height}p" else null,
                if (f.bitrate > 0) "${f.bitrate / 1000} kbps" else null,
                f.label,
            ).joinToString(" · ").ifBlank { "Video" }
            C.TRACK_TYPE_AUDIO -> listOfNotNull(
                lang ?: f.label,
                when (f.channelCount) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> null },
                f.sampleMimeType?.substringAfter('/')?.uppercase(Locale.ROOT),
            ).joinToString(" · ").ifBlank { "Audio" }
            else -> listOfNotNull(lang, f.label?.takeIf { it != lang }).joinToString(" · ").ifBlank { "Subtitle" }
        }
    }

    // ------------------------------------------------------------------ Tickers & progress

    private fun startTickers() {
        // UI position + subtitle rendering.
        viewModelScope.launch {
            while (isActive) {
                val exo = _player.value
                if (exo != null) {
                    val dur = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
                    _state.update { it.copy(positionMs = exo.currentPosition, durationMs = dur, bufferedMs = exo.bufferedPosition) }
                    renderSubtitles(exo)
                }
                delay(100)
            }
        }
        // Periodic progress save.
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000)
                if (_player.value?.isPlaying == true) saveProgress()
            }
        }
    }

    private fun renderSubtitles(exo: ExoPlayer) {
        val s = settings.value ?: return
        if (!s.subtitlesEnabled) { if (_cues.value.isNotEmpty()) _cues.value = emptyList(); return }
        val delayMs = s.subtitleDelayMs
        if (externalCues.isNotEmpty()) {
            val active = SubtitleParser.activeAt(externalCues, exo.currentPosition, delayMs)
            val newCues = active.map { Cue.Builder().setText(it.text).build() }
            if (newCues.map { it.text } != _cues.value.map { it.text }) _cues.value = newCues
            return
        }
        // Embedded cues arrive at presentation time; positive delay postpones them.
        val now = SystemClock.elapsedRealtime()
        val effectiveDelay = delayMs.coerceAtLeast(0)
        var latest: List<Cue>? = null
        while (embeddedQueue.isNotEmpty() && embeddedQueue.first().first + effectiveDelay <= now) {
            latest = embeddedQueue.removeFirst().second
        }
        if (latest != null) _cues.value = latest
    }

    private fun entry(
        t: ContentType, id: String, title: String, subtitle: String?, image: String?, pos: Long, dur: Long,
        seriesId: Long? = null, season: Int? = null, episode: Int? = null, ext: String? = null, categoryId: String? = null,
    ) = PlaybackEntry(
        playlistId = playlist?.id ?: 0, type = t, contentId = id, title = title, subtitle = subtitle, imageUrl = image,
        positionMs = pos, durationMs = dur, lastWatchedAt = System.currentTimeMillis(), playCount = 1,
        seriesId = seriesId, season = season, episode = episode, containerExtension = ext, categoryId = categoryId,
    )

    private var playCounted = mutableSetOf<String>()

    private fun saveProgress(completed: Boolean = false) {
        val exo = _player.value ?: return
        if (type == ContentType.LIVE || playlist == null) return
        val dur = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        val pos = if (completed) dur else exo.currentPosition
        if (pos <= 0) return
        val e = when (type) {
            ContentType.MOVIE -> {
                val (title, image, ext) = movieMeta ?: return
                entry(ContentType.MOVIE, route.contentId, title, null, image, pos, dur, ext = ext, categoryId = route.categoryId)
            }
            ContentType.EPISODE -> {
                val ep = currentEpisode() ?: return
                val sd = series ?: return
                entry(
                    ContentType.EPISODE, ep.id, sd.series.name, "S${ep.season} · E${ep.episodeNumber}", ep.thumbnail ?: sd.series.cover, pos, dur,
                    seriesId = sd.series.seriesId, season = ep.season, episode = ep.episodeNumber, ext = ep.containerExtension, categoryId = sd.series.categoryId,
                )
            }
            else -> return
        }
        val first = playCounted.add(e.contentId)
        viewModelScope.launch(Dispatchers.IO) { history.save(e, incrementPlayCount = first) }
    }

    override fun onCleared() {
        saveProgress()
        _player.value?.let {
            it.removeListener(listener)
            it.release()
        }
        _player.value = null
        super.onCleared()
    }

    private companion object { const val MAX_RETRIES = 3 }
}
