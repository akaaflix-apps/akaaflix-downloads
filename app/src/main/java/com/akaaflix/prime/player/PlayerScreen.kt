@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.akaaflix.prime.player

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.akaaflix.prime.R
import com.akaaflix.prime.common.TimeFormat
import com.akaaflix.prime.data.settings.AppSettings
import com.akaaflix.prime.data.settings.AspectMode
import com.akaaflix.prime.data.settings.SubtitleEdge
import com.akaaflix.prime.data.settings.SubtitleOptions
import com.akaaflix.prime.data.settings.SubtitlePosition
import com.akaaflix.prime.navigation.LocalNavigator
import com.akaaflix.prime.presentation.components.AkaaButton
import com.akaaflix.prime.presentation.components.AkaaCard
import com.akaaflix.prime.presentation.components.AkaaDialog
import com.akaaflix.prime.presentation.components.AkaaIconButton
import com.akaaflix.prime.presentation.components.ButtonVariant
import com.akaaflix.prime.presentation.components.ChoiceDialog
import com.akaaflix.prime.presentation.components.LocalMessenger
import com.akaaflix.prime.presentation.components.MenuRow
import com.akaaflix.prime.presentation.components.akaaViewModel
import com.akaaflix.prime.presentation.theme.AkaaTheme
import androidx.media3.common.text.Cue
import kotlinx.coroutines.delay

private enum class PlayerDialog { NONE, AUDIO, SUBTITLES, QUALITY, ASPECT, SPEED, SUBTITLE_STYLE, CHANNELS }

/** Full-screen built-in player (Media3 ExoPlayer) for live TV, movies and episodes. */
@Composable
fun PlayerScreen() {
    val vm = akaaViewModel { c, h ->
        PlayerViewModel(
            h, c.sessionManager, c.settingsRepository, c.playlistRepository, c.catalogRepository, c.historyRepository,
            c.epgRepository, c.rulesRepository, c.playlistPrefs, c.playerFactory,
        )
    }
    val player by vm.player.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val cues by vm.cues.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val messenger = LocalMessenger.current
    val context = LocalContext.current
    val isTv = AkaaTheme.isTv
    val c = AkaaTheme.colors

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dialog by remember { mutableStateOf(PlayerDialog.NONE) }
    var gestureLabel by remember { mutableStateOf<Pair<ImageVector, String>?>(null) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var muted by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }

    fun poke() { lastInteraction = System.currentTimeMillis(); controlsVisible = true }

    PlayerWindowEffects(isTv = isTv)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) vm.pauseForBackground() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    // Auto-hide controls while playing.
    LaunchedEffect(controlsVisible, lastInteraction, state.isPlaying, dialog) {
        if (controlsVisible && state.isPlaying && dialog == PlayerDialog.NONE && state.errorRes == null) {
            delay(4_500)
            controlsVisible = false
        }
    }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) runCatching { playFocus.requestFocus() } else runCatching { rootFocus.requestFocus() }
    }
    LaunchedEffect(gestureLabel) { if (gestureLabel != null) { delay(900); gestureLabel = null } }
    LaunchedEffect(seekFeedback) { if (seekFeedback != null) { delay(800); seekFeedback = null } }
    LaunchedEffect(state.formatFallbackNotice) {
        if (state.formatFallbackNotice) { messenger.show(R.string.player_format_fallback); vm.dismissFormatNotice() }
    }

    BackHandler {
        when {
            dialog != PlayerDialog.NONE -> dialog = PlayerDialog.NONE
            controlsVisible && isTv && state.errorRes == null -> controlsVisible = false
            else -> navigator.back()
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            vm.loadExternalSubtitle(context.contentResolver, uri, displayName(context, uri)) { ok ->
                messenger.show(if (ok) R.string.player_subtitle_loaded else R.string.player_subtitle_failed)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { vm.togglePlay(); poke(); true }
                    Key.MediaFastForward -> { vm.seekBy(30_000); seekFeedback = "+30s"; true }
                    Key.MediaRewind -> { vm.seekBy(-30_000); seekFeedback = "-30s"; true }
                    Key.MediaNext, Key.ChannelUp -> { vm.next(); poke(); true }
                    Key.MediaPrevious, Key.ChannelDown -> { vm.previous(); poke(); true }
                    else -> {
                        if (controlsVisible || dialog != PlayerDialog.NONE) {
                            lastInteraction = System.currentTimeMillis()
                            false
                        } else when (e.key) {
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { poke(); true }
                            Key.DirectionLeft -> {
                                if (state.isLive) poke() else { vm.seekBy(-10_000); seekFeedback = "-10s" }
                                true
                            }
                            Key.DirectionRight -> {
                                if (state.isLive) poke() else { vm.seekBy(10_000); seekFeedback = "+10s" }
                                true
                            }
                            Key.DirectionUp -> { if (state.isLive) vm.next() else poke(); true }
                            Key.DirectionDown -> { if (state.isLive) vm.previous() else poke(); true }
                            Key.Menu -> { poke(); true }
                            else -> false
                        }
                    }
                }
            }
            .then(if (!isTv) Modifier.playerGestures(
                context = context,
                onTap = { if (controlsVisible) controlsVisible = false else poke() },
                onDoubleTapStart = { if (!state.isLive) { vm.seekBy(-10_000); seekFeedback = "-10s" } },
                onDoubleTapEnd = { if (!state.isLive) { vm.seekBy(10_000); seekFeedback = "+10s" } },
                onBrightness = { v -> gestureLabel = Icons.Default.BrightnessMedium to "${(v * 100).toInt()}%" },
                onVolume = { v -> gestureLabel = Icons.AutoMirrored.Filled.VolumeUp to "${(v * 100).toInt()}%" },
            ) else Modifier),
    ) {
        VideoSurface(player, state.aspect)
        SubtitleOverlay(cues, settings)

        if (state.buffering && state.errorRes == null) {
            CircularProgressIndicator(color = c.red, modifier = Modifier.align(Alignment.Center).size(56.dp), strokeWidth = 4.dp)
        }
        seekFeedback?.let { Bubble(it, Modifier.align(Alignment.Center)) }
        gestureLabel?.let { (icon, text) ->
            Row(Modifier.align(Alignment.TopCenter).padding(top = 40.dp).background(Color(0xAA000000), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }

        AnimatedVisibility(controlsVisible && state.errorRes == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Controls(
                state = state,
                isTv = isTv,
                muted = muted,
                playFocus = playFocus,
                onBack = navigator::back,
                onTogglePlay = { vm.togglePlay(); poke() },
                onSeekBy = { vm.seekBy(it); poke() },
                onSeekTo = { vm.seekTo(it); poke() },
                onNext = { vm.next(); poke() },
                onPrevious = { vm.previous(); poke() },
                onMute = { muted = !muted; vm.setMuted(muted); poke() },
                onDialog = { dialog = it; poke() },
            )
        }

        state.errorRes?.let { res ->
            Column(
                Modifier.align(Alignment.Center).padding(24.dp).background(Color(0xE6071426), RoundedCornerShape(18.dp)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(state.title, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(res), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                val retryFocus = remember { FocusRequester() }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AkaaButton(stringResource(R.string.action_retry), vm::retry, icon = Icons.Default.Refresh, variant = ButtonVariant.ACCENT, focusRequester = retryFocus)
                    if (state.isLive && state.hasNext) AkaaButton(stringResource(R.string.player_next_channel), vm::next, icon = Icons.Default.SkipNext)
                    AkaaButton(stringResource(R.string.action_back), navigator::back, icon = Icons.AutoMirrored.Filled.ArrowBack)
                }
                LaunchedEffect(res) { runCatching { retryFocus.requestFocus() } }
            }
        }

        state.upNextSeconds?.let { sec ->
            Column(
                Modifier.align(Alignment.BottomEnd).padding(32.dp).background(Color(0xE6071426), RoundedCornerShape(16.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.player_up_next, sec), color = Color.White, style = MaterialTheme.typography.titleMedium)
                val f = remember { FocusRequester() }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AkaaButton(stringResource(R.string.player_play_now), { vm.cancelUpNext(); vm.next() }, icon = Icons.Default.SkipNext, variant = ButtonVariant.ACCENT, focusRequester = f, compact = true)
                    AkaaButton(stringResource(R.string.action_cancel), vm::cancelUpNext, compact = true)
                }
                LaunchedEffect(Unit) { runCatching { f.requestFocus() } }
            }
        }
    }

    // ---- Dialogs ----
    val close = { dialog = PlayerDialog.NONE }
    when (dialog) {
        PlayerDialog.AUDIO -> TrackDialog(stringResource(R.string.player_audio), vm.tracks(C.TRACK_TYPE_AUDIO), state.tracksVersion, null, null, { vm.selectTrack(it, C.TRACK_TYPE_AUDIO) }, close)
        PlayerDialog.QUALITY -> TrackDialog(stringResource(R.string.player_quality), vm.tracks(C.TRACK_TYPE_VIDEO), state.tracksVersion, stringResource(R.string.player_quality_auto), vm::autoVideoQuality, { vm.selectTrack(it, C.TRACK_TYPE_VIDEO) }, close)
        PlayerDialog.SUBTITLES -> SubtitleTrackDialog(
            tracks = vm.tracks(C.TRACK_TYPE_TEXT),
            disabled = vm.isTextDisabled() && state.externalSubtitleName == null,
            externalName = state.externalSubtitleName,
            onSelect = { vm.selectTrack(it, C.TRACK_TYPE_TEXT); if (settings?.subtitlesEnabled == false) vm.updateSettings { s -> s.copy(subtitlesEnabled = true) } },
            onOff = vm::disableSubtitles,
            onLoadExternal = {
                try {
                    subtitlePicker.launch(arrayOf("application/x-subrip", "text/vtt", "text/plain", "application/octet-stream", "*/*"))
                } catch (e: ActivityNotFoundException) {
                    messenger.show(R.string.player_no_file_picker)
                }
            },
            onDismiss = close,
        )
        PlayerDialog.ASPECT -> ChoiceDialog(
            title = stringResource(R.string.player_aspect), options = AspectMode.entries.toList(), selected = state.aspect,
            label = { aspectLabel(it) }, onSelect = vm::setAspect, onDismiss = close,
        )
        PlayerDialog.SPEED -> ChoiceDialog(
            title = stringResource(R.string.player_speed), options = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f), selected = state.speed,
            label = { if (it == 1f) stringResource(R.string.player_speed_normal) else "${it}x" }, onSelect = vm::setSpeed, onDismiss = close,
        )
        PlayerDialog.SUBTITLE_STYLE -> settings?.let { s -> SubtitleStyleDialog(s, vm::updateSettings, vm::adjustSubtitleDelay, state.externalSubtitleName != null, close) }
        PlayerDialog.CHANNELS -> ChannelListDialog(vm, close)
        PlayerDialog.NONE -> Unit
    }
}

@Composable
private fun VideoSurface(player: androidx.media3.exoplayer.ExoPlayer?, aspect: AspectMode) {
    val ratio = when (aspect) {
        AspectMode.RATIO_16_9 -> 16f / 9f
        AspectMode.RATIO_4_3 -> 4f / 3f
        else -> null
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    keepScreenOn = true
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    subtitleView?.visibility = android.view.View.GONE // subtitles are rendered by SubtitleOverlay
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = when (aspect) {
                    AspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectMode.RATIO_16_9, AspectMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
            },
            onRelease = { it.player = null },
            modifier = if (ratio != null) Modifier.fillMaxSize().aspectRatio(ratio, matchHeightConstraintsFirst = true) else Modifier.fillMaxSize(),
        )
    }
}

/** Renders subtitle cues with the user's style (size, colours, opacity, edge, position). */
@Composable
private fun SubtitleOverlay(cues: List<Cue>, s: AppSettings?) {
    if (s == null || !s.subtitlesEnabled) return
    AndroidView(
        factory = { ctx -> SubtitleView(ctx).apply { setApplyEmbeddedStyles(false); setApplyEmbeddedFontSizes(false) } },
        update = { view ->
            val bgAlpha = (s.subtitleBackgroundOpacity.coerceIn(0, 100) * 255 / 100)
            val bg = (s.subtitleBackgroundColor.toInt() and 0x00FFFFFF) or (bgAlpha shl 24)
            view.setStyle(
                CaptionStyleCompat(
                    s.subtitleTextColor.toInt(), bg, android.graphics.Color.TRANSPARENT,
                    when (s.subtitleEdge) {
                        SubtitleEdge.NONE -> CaptionStyleCompat.EDGE_TYPE_NONE
                        SubtitleEdge.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                        SubtitleEdge.DROP_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                        SubtitleEdge.RAISED -> CaptionStyleCompat.EDGE_TYPE_RAISED
                        SubtitleEdge.DEPRESSED -> CaptionStyleCompat.EDGE_TYPE_DEPRESSED
                    },
                    android.graphics.Color.BLACK, null,
                ),
            )
            view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * s.subtitleScale / 100f)
            view.setBottomPaddingFraction(if (s.subtitlePosition == SubtitlePosition.BOTTOM) 0.08f else 0f)
            view.setCues(
                when (s.subtitlePosition) {
                    SubtitlePosition.BOTTOM -> cues
                    SubtitlePosition.MIDDLE -> cues.map { it.buildUpon().setLine(0.5f, Cue.LINE_TYPE_FRACTION).setLineAnchor(Cue.ANCHOR_TYPE_MIDDLE).build() }
                    SubtitlePosition.TOP -> cues.map { it.buildUpon().setLine(0.06f, Cue.LINE_TYPE_FRACTION).setLineAnchor(Cue.ANCHOR_TYPE_START).build() }
                },
            )
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun Controls(
    state: PlayerUiState,
    isTv: Boolean,
    muted: Boolean,
    playFocus: FocusRequester,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onMute: () -> Unit,
    onDialog: (PlayerDialog) -> Unit,
) {
    val c = AkaaTheme.colors
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0xCC000000), 0.25f to Color.Transparent, 0.65f to Color.Transparent, 1f to Color(0xE6000000)))) {
        // Top bar
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = if (isTv) 40.dp else 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            AkaaIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    state.channelNumber?.takeIf { it > 0 && state.isLive }?.let { Text("$it  ", color = c.textSecondary, style = MaterialTheme.typography.titleMedium) }
                    Text(state.title, color = Color.White, style = if (isTv) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                (state.subtitle ?: state.nowPlaying?.title)?.let { Text(it, color = c.textSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (state.isLive) {
                Text("LIVE", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.background(c.red, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
                state.liveFormat?.let { Text("  ${it.name.replace('_', ' ')}", color = c.textSecondary, style = MaterialTheme.typography.labelMedium) }
            }
        }
        // Center transport
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(if (isTv) 22.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.hasPrevious) AkaaIconButton(Icons.Default.SkipPrevious, stringResource(if (state.isLive) R.string.player_prev_channel else R.string.player_prev_episode), onPrevious, size = 52.dp)
            if (!state.isLive) AkaaIconButton(Icons.Default.Replay10, stringResource(R.string.player_rewind), { onSeekBy(-10_000) }, size = 52.dp)
            AkaaIconButton(
                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                stringResource(if (state.isPlaying) R.string.player_pause else R.string.action_play),
                onTogglePlay, size = if (isTv) 76.dp else 68.dp, focusRequester = playFocus, selected = true,
            )
            if (!state.isLive) AkaaIconButton(Icons.Default.Forward10, stringResource(R.string.player_forward), { onSeekBy(10_000) }, size = 52.dp)
            if (state.hasNext) AkaaIconButton(Icons.Default.SkipNext, stringResource(if (state.isLive) R.string.player_next_channel else R.string.player_next_episode), onNext, size = 52.dp)
        }
        // Bottom: seek bar + actions
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = if (isTv) 40.dp else 16.dp, vertical = 14.dp)) {
            if (!state.isLive && state.durationMs > 0) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(TimeFormat.duration(state.positionMs), color = Color.White, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(10.dp))
                        SeekBar(state.positionMs, state.bufferedMs, state.durationMs, onSeekTo, onSeekBy, Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        Text(TimeFormat.duration(state.durationMs), color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionChip(Icons.Default.Audiotrack, stringResource(R.string.player_audio)) { onDialog(PlayerDialog.AUDIO) }
                ActionChip(Icons.Default.ClosedCaption, stringResource(R.string.player_subtitles)) { onDialog(PlayerDialog.SUBTITLES) }
                ActionChip(Icons.Default.Subtitles, stringResource(R.string.player_subtitle_style)) { onDialog(PlayerDialog.SUBTITLE_STYLE) }
                ActionChip(Icons.Default.HighQuality, stringResource(R.string.player_quality)) { onDialog(PlayerDialog.QUALITY) }
                ActionChip(Icons.Default.AspectRatio, stringResource(R.string.player_aspect)) { onDialog(PlayerDialog.ASPECT) }
                if (!state.isLive) ActionChip(Icons.Default.Speed, stringResource(R.string.player_speed)) { onDialog(PlayerDialog.SPEED) }
                if (state.isLive) ActionChip(Icons.AutoMirrored.Filled.List, stringResource(R.string.player_channels)) { onDialog(PlayerDialog.CHANNELS) }
                ActionChip(if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, stringResource(if (muted) R.string.player_unmute else R.string.player_mute), onMute)
            }
        }
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    val isTv = AkaaTheme.isTv
    AkaaCard(onClick = onClick, shape = RoundedCornerShape(50), containerColor = Color(0x66000000), focusScale = 1.08f) { focused ->
        Row(Modifier.padding(horizontal = if (isTv) 14.dp else 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp))
            if (isTv || focused) {
                Spacer(Modifier.width(6.dp))
                Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/** Seek bar usable by touch (tap/drag) and D-pad (left/right = -/+10 s when focused). Always LTR. */
@Composable
private fun SeekBar(position: Long, buffered: Long, duration: Long, onSeekTo: (Long) -> Unit, onSeekBy: (Long) -> Unit, modifier: Modifier) {
    val c = AkaaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    BoxWithConstraints(
        modifier
            .height(28.dp)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { onSeekBy(-10_000); true }
                    Key.DirectionRight -> { onSeekBy(10_000); true }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction)
            .pointerInput(duration) {
                detectTapGestures { o -> onSeekTo((o.x / size.width * duration).toLong()) }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (dragFraction >= 0) onSeekTo((dragFraction * duration).toLong()); dragFraction = -1f },
                    onDragCancel = { dragFraction = -1f },
                ) { change, _ -> dragFraction = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
        contentAlignment = Alignment.Center,
    ) {
        val fraction = if (dragFraction >= 0) dragFraction else (position.toFloat() / duration).coerceIn(0f, 1f)
        val bufferedFraction = (buffered.toFloat() / duration).coerceIn(0f, 1f)
        Canvas(Modifier.fillMaxWidth().height(if (focused) 10.dp else 6.dp)) {
            val h = size.height
            drawRoundRect(Color(0x55FFFFFF), size = size, cornerRadius = CornerRadius(h, h))
            drawRoundRect(Color(0x88FFFFFF), size = Size(size.width * bufferedFraction, h), cornerRadius = CornerRadius(h, h))
            drawRoundRect(c.red, size = Size(size.width * fraction, h), cornerRadius = CornerRadius(h, h))
            drawCircle(Color.White, radius = h * (if (focused) 1.4f else 1.1f), center = Offset(size.width * fraction, h / 2))
        }
    }
}

@Composable
private fun Bubble(text: String, modifier: Modifier) {
    Text(text, color = Color.White, style = MaterialTheme.typography.headlineSmall, modifier = modifier.background(Color(0xAA000000), RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 10.dp))
}

@Composable
private fun TrackDialog(
    title: String,
    tracks: List<TrackOption>,
    @Suppress("UNUSED_PARAMETER") version: Int,
    autoLabel: String?,
    onAuto: (() -> Unit)?,
    onSelect: (TrackOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    AkaaDialog(onDismiss = onDismiss, title = title) {
        LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (autoLabel != null && onAuto != null) {
                item { MenuRow(Icons.Default.Tune, autoLabel, first) { onAuto(); onDismiss() } }
            }
            if (tracks.isEmpty()) item { Text(stringResource(R.string.player_no_tracks), color = AkaaTheme.colors.textSecondary, modifier = Modifier.padding(8.dp)) }
            itemsIndexed(tracks) { i, t ->
                MenuRow(
                    if (t.selected) Icons.Default.PlayArrow else Icons.Default.Audiotrack,
                    (if (t.selected) "✓ " else "") + t.label,
                    if (i == 0 && autoLabel == null) first else null,
                ) { onSelect(t); onDismiss() }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun SubtitleTrackDialog(
    tracks: List<TrackOption>,
    disabled: Boolean,
    externalName: String?,
    onSelect: (TrackOption) -> Unit,
    onOff: () -> Unit,
    onLoadExternal: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    AkaaDialog(onDismiss = onDismiss, title = stringResource(R.string.player_subtitles)) {
        LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { MenuRow(Icons.Default.ClosedCaption, (if (disabled) "✓ " else "") + stringResource(R.string.player_subtitles_off), first) { onOff(); onDismiss() } }
            itemsIndexed(tracks) { _, t ->
                MenuRow(Icons.Default.Subtitles, (if (t.selected && !disabled && externalName == null) "✓ " else "") + t.label + "  · " + stringResource(R.string.player_embedded), null) { onSelect(t); onDismiss() }
            }
            externalName?.let { item { MenuRow(Icons.Default.Subtitles, "✓ $it  · " + stringResource(R.string.player_external), null) { onDismiss() } } }
            item { MenuRow(Icons.Default.Subtitles, stringResource(R.string.player_load_external), null) { onDismiss(); onLoadExternal() } }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** Live subtitle adjustments during playback (persisted to settings). */
@Composable
private fun SubtitleStyleDialog(
    s: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
    adjustDelay: (Long) -> Unit,
    hasExternal: Boolean,
    onDismiss: () -> Unit,
) {
    val c = AkaaTheme.colors
    val first = remember { FocusRequester() }
    AkaaDialog(onDismiss = onDismiss, title = stringResource(R.string.player_subtitle_style)) {
        LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                StepperRow(
                    stringResource(R.string.subtitle_delay), stringResource(R.string.subtitle_delay_value, s.subtitleDelayMs / 1000f),
                    onMinus = { adjustDelay(-250) }, onPlus = { adjustDelay(250) }, focusRequester = first,
                )
                if (!hasExternal && s.subtitleDelayMs < 0) {
                    Text(stringResource(R.string.subtitle_delay_negative_note), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            item {
                StepperRow(
                    stringResource(R.string.subtitle_size), "${s.subtitleScale}%",
                    onMinus = { update { it.copy(subtitleScale = (it.subtitleScale - 10).coerceAtLeast(50)) } },
                    onPlus = { update { it.copy(subtitleScale = (it.subtitleScale + 10).coerceAtMost(250)) } },
                )
            }
            item {
                StepperRow(
                    stringResource(R.string.subtitle_bg_opacity), "${s.subtitleBackgroundOpacity}%",
                    onMinus = { update { it.copy(subtitleBackgroundOpacity = (it.subtitleBackgroundOpacity - 10).coerceAtLeast(0)) } },
                    onPlus = { update { it.copy(subtitleBackgroundOpacity = (it.subtitleBackgroundOpacity + 10).coerceAtMost(100)) } },
                )
            }
            item {
                Text(stringResource(R.string.subtitle_text_color), color = c.text, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubtitleOptions.colors.forEach { col ->
                        AkaaCard(onClick = { update { it.copy(subtitleTextColor = col) } }, modifier = Modifier.size(36.dp), shape = RoundedCornerShape(50), containerColor = Color(col)) { _ ->
                            if (s.subtitleTextColor == col) Box(Modifier.align(Alignment.Center).size(10.dp).background(if (col == 0xFFFFFFFF) Color.Black else Color.White, RoundedCornerShape(50)))
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.subtitle_position), color = c.text, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubtitlePosition.entries.forEach { pos ->
                        AkaaButton(
                            positionLabel(pos), { update { it.copy(subtitlePosition = pos) } }, compact = true,
                            variant = if (s.subtitlePosition == pos) ButtonVariant.ACCENT else ButtonVariant.SECONDARY,
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, focusRequester: FocusRequester? = null) {
    val c = AkaaTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        AkaaButton("−", onMinus, compact = true, focusRequester = focusRequester)
        Text(value, color = c.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 12.dp).width(72.dp), maxLines = 1)
        AkaaButton("+", onPlus, compact = true)
    }
}

@Composable
private fun ChannelListDialog(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val channels = remember { vm.channelList() }
    val current = vm.currentChannelIndex()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    val currentFocus = remember { FocusRequester() }
    AkaaDialog(onDismiss = onDismiss, title = stringResource(R.string.player_channels)) {
        LazyColumn(state = listState, modifier = Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(channels, key = { _, ch -> ch.streamId }) { i, ch ->
                MenuRow(
                    if (i == current) Icons.Default.PlayArrow else Icons.AutoMirrored.Filled.List,
                    "${if (ch.number > 0) "${ch.number}  " else ""}${ch.name}",
                    if (i == current) currentFocus else null,
                ) { vm.playChannelAt(i); onDismiss() }
            }
        }
    }
    LaunchedEffect(Unit) { delay(100); runCatching { currentFocus.requestFocus() } }
}

@Composable
fun aspectLabel(mode: AspectMode): String = stringResource(
    when (mode) {
        AspectMode.FIT -> R.string.aspect_fit
        AspectMode.FILL -> R.string.aspect_fill
        AspectMode.ZOOM -> R.string.aspect_zoom
        AspectMode.RATIO_16_9 -> R.string.aspect_16_9
        AspectMode.RATIO_4_3 -> R.string.aspect_4_3
    },
)

@Composable
fun positionLabel(pos: SubtitlePosition): String = stringResource(
    when (pos) {
        SubtitlePosition.BOTTOM -> R.string.position_bottom
        SubtitlePosition.MIDDLE -> R.string.position_middle
        SubtitlePosition.TOP -> R.string.position_top
    },
)

/** Landscape + immersive full screen on phones; keeps the screen awake. Restored on exit. */
@Composable
private fun PlayerWindowEffects(isTv: Boolean) {
    val view = LocalView.current
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(Unit) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, view)
        val oldOrientation = activity.requestedOrientation
        if (!isTv) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val attrs = window.attributes
            attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = attrs
            activity.requestedOrientation = if (isTv) oldOrientation else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

/** Touch gestures: tap, double-tap left/right to seek, vertical drag for brightness (left) / volume (right). */
private fun Modifier.playerGestures(
    context: Context,
    onTap: () -> Unit,
    onDoubleTapStart: () -> Unit,
    onDoubleTapEnd: () -> Unit,
    onBrightness: (Float) -> Unit,
    onVolume: (Float) -> Unit,
): Modifier = this
    .pointerInput(Unit) {
        detectTapGestures(
            onTap = { onTap() },
            onDoubleTap = { o -> if (o.x < size.width / 2) onDoubleTapStart() else onDoubleTapEnd() },
        )
    }
    .pointerInput(Unit) {
        var startX = 0f
        var accumulated = 0f
        detectVerticalDragGestures(
            onDragStart = { o -> startX = o.x; accumulated = 0f },
        ) { change, dragAmount ->
            accumulated += -dragAmount / size.height
            val ctx = context
            if (startX < size.width / 2) {
                val window = (ctx as? Activity)?.window ?: return@detectVerticalDragGestures
                val attrs = window.attributes
                val current = if (attrs.screenBrightness < 0) 0.5f else attrs.screenBrightness
                val next = (current + -dragAmount / size.height).coerceIn(0.02f, 1f)
                attrs.screenBrightness = next
                window.attributes = attrs
                onBrightness(next)
            } else {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (kotlin.math.abs(accumulated) * max >= 1f) {
                    val step = if (accumulated > 0) 1 else -1
                    val v = (am.getStreamVolume(AudioManager.STREAM_MUSIC) + step).coerceIn(0, max)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                    accumulated = 0f
                    onVolume(v.toFloat() / max)
                }
            }
            change.consume()
        }
    }

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()
