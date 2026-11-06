package com.akaaflix.prime.presentation.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akaaflix.prime.R
import com.akaaflix.prime.common.ContentText
import com.akaaflix.prime.common.TimeFormat
import com.akaaflix.prime.data.repository.toRef
import com.akaaflix.prime.domain.model.Episode
import com.akaaflix.prime.domain.model.MovieDetails
import com.akaaflix.prime.domain.model.PlaybackEntry
import com.akaaflix.prime.domain.model.SeriesDetails
import com.akaaflix.prime.navigation.LocalNavigator
import com.akaaflix.prime.presentation.catalog.FilterChipItem
import com.akaaflix.prime.presentation.components.AkaaButton
import com.akaaflix.prime.presentation.components.AkaaCard
import com.akaaflix.prime.presentation.components.AkaaIconButton
import com.akaaflix.prime.presentation.components.ButtonVariant
import com.akaaflix.prime.presentation.components.ErrorState
import com.akaaflix.prime.presentation.components.LoadingState
import com.akaaflix.prime.presentation.components.LocalContentActions
import com.akaaflix.prime.presentation.components.OutlineBadge
import com.akaaflix.prime.presentation.components.QualityBadge
import com.akaaflix.prime.presentation.components.RemoteImage
import com.akaaflix.prime.presentation.components.akaaViewModel
import com.akaaflix.prime.presentation.theme.AkaaTheme
import java.util.Locale

@Composable
fun MovieDetailsScreen() {
    val vm = akaaViewModel { c, h -> MovieDetailsViewModel(h, c.sessionManager, c.catalogRepository, c.libraryRepository, c.historyRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val isFavorite by vm.isFavorite.collectAsStateWithLifecycle()
    val resume by vm.resume.collectAsStateWithLifecycle()
    when (val s = state) {
        DetailsState.Loading -> LoadingState()
        is DetailsState.Error -> DetailsFrame(null) { ErrorState(s.error, onRetry = { vm.load(true) }) }
        is DetailsState.Content -> MovieContent(s.data, isFavorite, resume)
    }
}

@Composable
private fun MovieContent(d: MovieDetails, isFavorite: Boolean, resume: PlaybackEntry?) {
    val navigator = LocalNavigator.current
    val actions = LocalContentActions.current
    val m = d.movie
    val ref = m.toRef()
    val playFocus = remember { FocusRequester() }
    val canResume = resume != null && resume.durationMs > 0 && resume.positionMs > 30_000 && resume.progress < 0.95f
    DetailsFrame(d.backdrop ?: m.poster) {
        DetailsHeader(
            poster = m.poster, title = m.name,
            meta = listOfNotNull(
                m.year?.toString(), d.genre,
                d.durationSec?.let { TimeFormat.duration(it * 1000) },
            ),
            rating = m.rating, quality = m.quality, hdr = ContentText.isHdr(m.name), ageRating = d.ageRating,
        ) {
            if (!d.plot.isNullOrBlank()) InfoText(d.plot, maxLines = 6)
            d.cast?.let { LabeledText(stringResource(R.string.details_cast), it) }
            d.director?.let { LabeledText(stringResource(R.string.details_director), it) }
            d.country?.let { LabeledText(stringResource(R.string.details_country), it) }
            d.releaseDate?.let { LabeledText(stringResource(R.string.details_release), it) }
            if (canResume) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { resume!!.progress }, color = AkaaTheme.colors.red, trackColor = AkaaTheme.colors.border, modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)))
            }
            Spacer(Modifier.height(14.dp))
            FlowButtons {
                if (canResume) {
                    AkaaButton(
                        stringResource(R.string.action_resume_at, TimeFormat.duration(resume!!.positionMs)),
                        { navigator.playMovie(m.streamId, m.categoryId, resume.positionMs, d.ageRating) },
                        icon = Icons.Default.PlayArrow, variant = ButtonVariant.PRIMARY, focusRequester = playFocus,
                    )
                    AkaaButton(stringResource(R.string.action_from_start), { navigator.playMovie(m.streamId, m.categoryId, 0, d.ageRating) }, icon = Icons.Default.Replay)
                } else {
                    AkaaButton(stringResource(R.string.action_watch_now), { navigator.playMovie(m.streamId, m.categoryId, -1, d.ageRating) }, icon = Icons.Default.PlayArrow, variant = ButtonVariant.PRIMARY, focusRequester = playFocus)
                }
                AkaaButton(
                    stringResource(if (isFavorite) R.string.action_remove_favorite_short else R.string.action_add_favorite_short),
                    { actions.toggleFavorite(ref) }, icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                )
                AkaaButton(stringResource(R.string.action_add_to_group), { actions.openGroupPicker(ref) }, icon = Icons.AutoMirrored.Filled.PlaylistAdd, variant = ButtonVariant.PURPLE)
                AkaaIconButton(Icons.Default.MoreVert, stringResource(R.string.action_more), { actions.showMenu(ref, m.categoryId, null) })
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
}

@Composable
fun SeriesDetailsScreen() {
    val vm = akaaViewModel { c, h -> SeriesDetailsViewModel(h, c.sessionManager, c.catalogRepository, c.libraryRepository, c.historyRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val isFavorite by vm.isFavorite.collectAsStateWithLifecycle()
    val season by vm.season.collectAsStateWithLifecycle()
    val progress by vm.episodeProgress.collectAsStateWithLifecycle()
    val last by vm.lastEpisode.collectAsStateWithLifecycle()
    when (val s = state) {
        DetailsState.Loading -> LoadingState()
        is DetailsState.Error -> DetailsFrame(null) { ErrorState(s.error, onRetry = { vm.load(true) }) }
        is DetailsState.Content -> SeriesContent(s.data, isFavorite, season, progress, last, vm::selectSeason)
    }
}

@Composable
private fun SeriesContent(
    d: SeriesDetails,
    isFavorite: Boolean,
    season: Int?,
    progress: Map<String, PlaybackEntry>,
    last: PlaybackEntry?,
    onSeason: (Int) -> Unit,
) {
    val navigator = LocalNavigator.current
    val actions = LocalContentActions.current
    val s = d.series
    val ref = s.toRef()
    val c = AkaaTheme.colors
    val playFocus = remember { FocusRequester() }
    val ordered = d.allEpisodesOrdered()
    val first = ordered.firstOrNull()
    val resumeEpisode = last?.let { e -> ordered.firstOrNull { it.id == e.contentId } }
    val episodes = season?.let { d.episodesBySeason[it] }.orEmpty()

    DetailsFrame(s.backdrop ?: s.cover) {
        DetailsHeader(
            poster = s.cover, title = s.name,
            meta = listOfNotNull(s.year?.toString(), s.genre, stringResource(R.string.details_seasons_count, d.seasons.size), stringResource(R.string.details_episodes_count, ordered.size)),
            rating = s.rating, quality = s.quality, hdr = ContentText.isHdr(s.name), ageRating = d.ageRating,
        ) {
            if (!s.plot.isNullOrBlank()) InfoText(s.plot, maxLines = 5)
            d.cast?.let { LabeledText(stringResource(R.string.details_cast), it) }
            d.director?.let { LabeledText(stringResource(R.string.details_director), it) }
            Spacer(Modifier.height(14.dp))
            FlowButtons {
                if (resumeEpisode != null && last != null) {
                    AkaaButton(
                        stringResource(R.string.action_resume_episode, resumeEpisode.season, resumeEpisode.episodeNumber),
                        { navigator.playEpisode(s.seriesId, resumeEpisode.id, s.categoryId, last.positionMs, d.ageRating) },
                        icon = Icons.Default.PlayArrow, variant = ButtonVariant.PRIMARY, focusRequester = playFocus,
                    )
                } else if (first != null) {
                    AkaaButton(
                        stringResource(R.string.action_play_episode, first.season, first.episodeNumber),
                        { navigator.playEpisode(s.seriesId, first.id, s.categoryId, -1, d.ageRating) },
                        icon = Icons.Default.PlayArrow, variant = ButtonVariant.PRIMARY, focusRequester = playFocus,
                    )
                }
                AkaaButton(
                    stringResource(if (isFavorite) R.string.action_remove_favorite_short else R.string.action_add_favorite_short),
                    { actions.toggleFavorite(ref) }, icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                )
                AkaaButton(stringResource(R.string.action_add_to_group), { actions.openGroupPicker(ref) }, icon = Icons.AutoMirrored.Filled.PlaylistAdd, variant = ButtonVariant.PURPLE)
                AkaaIconButton(Icons.Default.MoreVert, stringResource(R.string.action_more), { actions.showMenu(ref, s.categoryId, null) })
            }
        }
        Spacer(Modifier.height(18.dp))
        if (d.seasons.isEmpty()) {
            Text(stringResource(R.string.details_no_episodes), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(stringResource(R.string.details_seasons), style = MaterialTheme.typography.titleLarge, color = c.text)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(d.seasons, key = { it.number }) { se ->
                    FilterChipItem(se.name.ifBlank { stringResource(R.string.details_season_n, se.number) }, se.number == season, { onSeason(se.number) }, count = se.episodeCount)
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                episodes.forEach { ep ->
                    EpisodeRow(ep, progress[ep.id]) { navigator.playEpisode(s.seriesId, ep.id, s.categoryId, progress[ep.id]?.positionMs ?: -1, d.ageRating) }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
}

@Composable
private fun EpisodeRow(ep: Episode, progress: PlaybackEntry?, onClick: () -> Unit) {
    val c = AkaaTheme.colors
    AkaaCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), containerColor = c.surface.copy(alpha = 0.9f), focusScale = 1.02f) { focused ->
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(if (AkaaTheme.isTv) 200.dp else 128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
                RemoteImage(ep.thumbnail, ep.title, Modifier.fillMaxSize(), placeholderText = "E${ep.episodeNumber}")
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).background(if (focused) c.accent else Color(0x99000000), RoundedCornerShape(50)).padding(6.dp).size(20.dp))
                if (progress != null && progress.durationMs > 0) {
                    LinearProgressIndicator(progress = { progress.progress }, color = c.red, trackColor = Color(0x66000000), modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.details_episode_label, ep.episodeNumber), style = MaterialTheme.typography.labelMedium, color = c.gold)
                Text(ep.title, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(ep.durationSec?.let { TimeFormat.duration(it * 1000) }, ep.releaseDate).joinToString(" • ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                ep.plot?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** Full-bleed backdrop with gradients and a scrollable content column. */
@Composable
private fun DetailsFrame(backdrop: String?, content: @Composable () -> Unit) {
    val c = AkaaTheme.colors
    val navigator = LocalNavigator.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val isTv = AkaaTheme.isTv
    Box(Modifier.fillMaxSize().background(c.background)) {
        if (backdrop != null) {
            RemoteImage(backdrop, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(
                    Brush.horizontalGradient(if (rtl) listOf(Color.Transparent, c.background.copy(alpha = 0.95f)) else listOf(c.background.copy(alpha = 0.95f), Color.Transparent)),
                ),
            )
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to c.background)))
        }
        LazyColumn(
            Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(horizontal = if (isTv) 56.dp else 16.dp, vertical = 12.dp),
        ) {
            item { AkaaIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), navigator::back) }
            item {
                Spacer(Modifier.height(if (isTv) 40.dp else 80.dp))
                Column { content() }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsHeader(
    poster: String?,
    title: String,
    meta: List<String>,
    rating: Double?,
    quality: String?,
    hdr: Boolean,
    ageRating: String?,
    body: @Composable () -> Unit,
) {
    val c = AkaaTheme.colors
    val isTv = AkaaTheme.isTv
    Row(verticalAlignment = Alignment.Top) {
        RemoteImage(poster, title, Modifier.width(if (isTv) 220.dp else 120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)), placeholderText = title)
        Spacer(Modifier.width(if (isTv) 28.dp else 14.dp))
        Column(Modifier.weight(1f).widthIn(max = 900.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = if (isTv) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall, color = c.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                quality?.let { QualityBadge(it) }
                if (hdr) OutlineBadge("HDR")
                ageRating?.let { OutlineBadge(it, color = c.gold) }
                rating?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = c.gold, modifier = Modifier.size(16.dp))
                        Text(String.format(Locale.US, " %.1f", it), style = MaterialTheme.typography.labelLarge, color = c.text)
                    }
                }
                meta.forEach { Text(it, style = MaterialTheme.typography.labelLarge, color = c.textSecondary) }
            }
            body()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowButtons(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

@Composable
private fun InfoText(text: String, maxLines: Int) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = AkaaTheme.colors.text.copy(alpha = 0.9f), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun LabeledText(label: String, value: String) {
    Row {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = AkaaTheme.colors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = AkaaTheme.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
