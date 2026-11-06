package com.akaaflix.prime.presentation.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BorderStyle
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akaaflix.prime.BuildConfig
import com.akaaflix.prime.R
import com.akaaflix.prime.data.settings.AccentColor
import com.akaaflix.prime.data.settings.AppLanguage
import com.akaaflix.prime.data.settings.AppSettings
import com.akaaflix.prime.data.settings.AspectMode
import com.akaaflix.prime.data.settings.AudioOutputMode
import com.akaaflix.prime.data.settings.BufferProfile
import com.akaaflix.prime.data.settings.DecoderMode
import com.akaaflix.prime.data.settings.InterfaceMode
import com.akaaflix.prime.data.settings.LiveFormat
import com.akaaflix.prime.data.settings.PlayerChoice
import com.akaaflix.prime.data.settings.SubtitleEdge
import com.akaaflix.prime.data.settings.SubtitleOptions
import com.akaaflix.prime.data.settings.SubtitlePosition
import com.akaaflix.prime.data.settings.SubtitleSource
import com.akaaflix.prime.data.settings.ThemeMode
import com.akaaflix.prime.domain.model.Category
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.model.RuleRestriction
import com.akaaflix.prime.domain.model.RuleScope
import com.akaaflix.prime.navigation.AppNavigator
import com.akaaflix.prime.navigation.LocalNavigator
import com.akaaflix.prime.navigation.MyListRoute
import com.akaaflix.prime.player.aspectLabel
import com.akaaflix.prime.player.positionLabel
import com.akaaflix.prime.presentation.catalog.FilterRow
import com.akaaflix.prime.presentation.components.AkaaButton
import com.akaaflix.prime.presentation.components.AkaaCard
import com.akaaflix.prime.presentation.components.AkaaDialog
import com.akaaflix.prime.presentation.components.AkaaIconButton
import com.akaaflix.prime.presentation.components.AkaaLogo
import com.akaaflix.prime.presentation.components.ButtonVariant
import com.akaaflix.prime.presentation.components.ChoiceDialog
import com.akaaflix.prime.presentation.components.ConfirmDialog
import com.akaaflix.prime.presentation.components.LocalMessenger
import com.akaaflix.prime.presentation.components.LocalPinGate
import com.akaaflix.prime.presentation.components.Messenger
import com.akaaflix.prime.presentation.components.MenuRow
import com.akaaflix.prime.presentation.components.PinDialog
import com.akaaflix.prime.presentation.components.PinGate
import com.akaaflix.prime.presentation.components.TextInputDialog
import com.akaaflix.prime.presentation.components.akaaViewModel
import com.akaaflix.prime.presentation.library.typeLabel
import com.akaaflix.prime.presentation.theme.AkaaTheme
import com.akaaflix.prime.security.PinHasher
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun SettingsSection.icon(): ImageVector = when (this) {
    SettingsSection.GENERAL -> Icons.Default.Settings
    SettingsSection.PLAYER -> Icons.Default.PlayCircle
    SettingsSection.LIVE_FORMAT -> Icons.Default.Sensors
    SettingsSection.SUBTITLES -> Icons.Default.ClosedCaption
    SettingsSection.SUBTITLE_APPEARANCE -> Icons.Default.Tune
    SettingsSection.HIDE_CONTENT -> Icons.Default.VisibilityOff
    SettingsSection.PARENTAL -> Icons.Default.Shield
    SettingsSection.PLAYLISTS -> Icons.AutoMirrored.Filled.PlaylistPlay
    SettingsSection.FAVORITES_GROUPS -> Icons.Default.Layers
    SettingsSection.LANGUAGE -> Icons.Default.Language
    SettingsSection.APPEARANCE -> Icons.Default.ColorLens
    SettingsSection.NOTIFICATIONS -> Icons.Default.Notifications
    SettingsSection.EPG -> Icons.Default.CalendarViewDay
    SettingsSection.PLAYBACK -> Icons.Default.SlowMotionVideo
    SettingsSection.CACHE -> Icons.Default.Memory
    SettingsSection.DATA -> Icons.Default.Storage
    SettingsSection.DEVICES -> Icons.Default.Devices
    SettingsSection.BACKUP -> Icons.Default.Backup
    SettingsSection.ABOUT -> Icons.Default.Info
}

fun SettingsSection.labelRes(): Int = when (this) {
    SettingsSection.GENERAL -> R.string.settings_general
    SettingsSection.PLAYER -> R.string.settings_player
    SettingsSection.LIVE_FORMAT -> R.string.settings_live_format
    SettingsSection.SUBTITLES -> R.string.settings_subtitles
    SettingsSection.SUBTITLE_APPEARANCE -> R.string.settings_subtitle_appearance
    SettingsSection.HIDE_CONTENT -> R.string.settings_hide_content
    SettingsSection.PARENTAL -> R.string.settings_parental
    SettingsSection.PLAYLISTS -> R.string.settings_playlists
    SettingsSection.FAVORITES_GROUPS -> R.string.settings_favorites_groups
    SettingsSection.LANGUAGE -> R.string.settings_language
    SettingsSection.APPEARANCE -> R.string.settings_appearance
    SettingsSection.NOTIFICATIONS -> R.string.settings_notifications
    SettingsSection.EPG -> R.string.settings_epg
    SettingsSection.PLAYBACK -> R.string.settings_playback
    SettingsSection.CACHE -> R.string.settings_cache
    SettingsSection.DATA -> R.string.settings_data
    SettingsSection.DEVICES -> R.string.settings_devices
    SettingsSection.BACKUP -> R.string.settings_backup
    SettingsSection.ABOUT -> R.string.settings_about
}

/** Everything a section needs. */
private class SettingsCtx(
    val vm: SettingsViewModel,
    val s: AppSettings,
    val navigator: AppNavigator,
    val messenger: Messenger,
    val pinGate: PinGate,
    val choose: (ChoiceRequest) -> Unit,
    val startPinSetup: () -> Unit,
)

@Composable
fun SettingsScreen() {
    val context = LocalContext.current.applicationContext
    val vm = akaaViewModel { c, h ->
        SettingsViewModel(
            h, context, c.settingsRepository, c.sessionManager, c.playlistRepository, c.catalogRepository, c.rulesRepository,
            c.historyRepository, c.epgRepository, c.searchHistoryRepository, c.notificationRepository, c.backupManager, c.externalPlayers,
        )
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val messenger = LocalMessenger.current
    val pinGate = LocalPinGate.current
    val isTv = AkaaTheme.isTv
    val d = AkaaTheme.dimens
    val c = AkaaTheme.colors
    var selectedName by rememberSaveable { mutableStateOf(vm.initialSection?.name ?: if (isTv) SettingsSection.GENERAL.name else null) }
    val selected = selectedName?.let { SettingsSection.valueOf(it) }
    var choice by remember { mutableStateOf<ChoiceRequest?>(null) }
    var pinStep by remember { mutableStateOf(0) }
    var firstPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<Int?>(null) }
    val firstFocus = remember { FocusRequester() }

    val s = settings ?: return
    val ctx = SettingsCtx(vm, s, navigator, messenger, pinGate, { choice = it }, { pinStep = 1; firstPin = ""; pinError = null })

    if (!isTv && selected != null) BackHandler { selectedName = null }

    val sectionList: @Composable (Modifier) -> Unit = { modifier ->
        LazyColumn(modifier, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, null, tint = c.accent, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall, color = c.text)
                        Text(stringResource(R.string.settings_subtitle), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            }
            items(SettingsSection.entries.toList(), key = { it.name }) { sec ->
                FilterRow(
                    stringResource(sec.labelRes()), sec == selected && isTv,
                    onClick = {
                        when (sec) {
                            SettingsSection.PLAYLISTS -> navigator.goToPlaylists(selectMode = false, clearStack = false)
                            SettingsSection.FAVORITES_GROUPS -> navigator.navigateTopLevel(MyListRoute(1))
                            else -> selectedName = sec.name
                        }
                    },
                    icon = sec.icon(),
                    focusRequester = if (sec == (selected ?: SettingsSection.GENERAL)) firstFocus else null,
                )
            }
        }
    }

    if (isTv) {
        Row(Modifier.fillMaxSize()) {
            sectionList(Modifier.width(d.sidebarWidth + 20.dp).fillMaxHeight().background(c.backgroundSecondary.copy(alpha = 0.6f)))
            Box(Modifier.weight(1f).fillMaxHeight()) { selected?.let { SectionPane(it, ctx) } }
        }
        LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    } else {
        if (selected == null) {
            sectionList(Modifier.fillMaxSize())
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AkaaIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), { selectedName = null }, size = 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(selected.labelRes()), style = MaterialTheme.typography.titleLarge, color = c.text)
                }
                SectionPane(selected, ctx)
            }
        }
    }

    choice?.let { req ->
        ChoiceDialog(
            title = req.title,
            options = req.options.indices.toList(),
            selected = req.selectedIndex,
            label = { req.options[it] },
            enabled = { req.enabled?.getOrNull(it) ?: true },
            onSelect = { req.onSelect(it) },
            onDismiss = { choice = null },
        )
    }

    // New PIN: enter twice.
    when (pinStep) {
        1 -> PinDialog(
            title = stringResource(R.string.pin_new_title), subtitle = stringResource(R.string.pin_new_subtitle),
            error = pinError?.let { stringResource(it) },
            onSubmit = { p -> if (PinHasher.isValidPin(p)) { firstPin = p; pinStep = 2; pinError = null } else pinError = R.string.pin_invalid },
            onDismiss = { pinStep = 0 },
        )
        2 -> PinDialog(
            title = stringResource(R.string.pin_confirm_title), subtitle = stringResource(R.string.pin_confirm_subtitle),
            error = pinError?.let { stringResource(it) },
            onSubmit = { p ->
                if (p == firstPin) {
                    vm.setPin(p) { ok ->
                        if (ok) { vm.update { it.copy(parentalEnabled = true) }; messenger.show(R.string.msg_pin_saved) }
                        pinStep = 0
                    }
                } else { pinError = R.string.pin_mismatch; pinStep = 1 }
            },
            onDismiss = { pinStep = 0 },
        )
    }
}

@Composable
private fun SectionPane(section: SettingsSection, ctx: SettingsCtx) {
    val isTv = AkaaTheme.isTv
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = if (isTv) 28.dp else 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isTv) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(section.icon(), null, tint = AkaaTheme.colors.accent, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(section.labelRes()), style = MaterialTheme.typography.headlineSmall, color = AkaaTheme.colors.text, modifier = Modifier.weight(1f))
                AkaaLogo(height = 40.dp)
            }
        }
        item {
            when (section) {
                SettingsSection.GENERAL -> GeneralSection(ctx)
                SettingsSection.PLAYER -> PlayerSection(ctx)
                SettingsSection.LIVE_FORMAT -> LiveFormatSection(ctx)
                SettingsSection.SUBTITLES -> SubtitlesSection(ctx)
                SettingsSection.SUBTITLE_APPEARANCE -> SubtitleAppearanceSection(ctx)
                SettingsSection.HIDE_CONTENT -> HideContentSection(ctx)
                SettingsSection.PARENTAL -> ParentalSection(ctx)
                SettingsSection.PLAYLISTS, SettingsSection.FAVORITES_GROUPS -> LinkSection(section, ctx)
                SettingsSection.LANGUAGE -> LanguageSection(ctx)
                SettingsSection.APPEARANCE -> AppearanceSection(ctx)
                SettingsSection.NOTIFICATIONS -> NotificationsSection(ctx)
                SettingsSection.EPG -> EpgSection(ctx)
                SettingsSection.PLAYBACK -> PlaybackSection(ctx)
                SettingsSection.CACHE -> CacheSection(ctx)
                SettingsSection.DATA -> DataSection(ctx)
                SettingsSection.DEVICES -> DevicesSection(ctx)
                SettingsSection.BACKUP -> BackupSection(ctx)
                SettingsSection.ABOUT -> AboutSection()
            }
        }
    }
}

// ---------------------------------------------------------------- Label helpers

@Composable private fun onOff(b: Boolean) = stringResource(if (b) R.string.on else R.string.off)

@Composable
private fun themeLabel(t: ThemeMode) = stringResource(when (t) { ThemeMode.DARK -> R.string.theme_dark; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.SYSTEM -> R.string.theme_auto })

@Composable
private fun languageLabel(l: AppLanguage) = when (l) { AppLanguage.ARABIC -> "العربية"; AppLanguage.ENGLISH -> "English" }

@Composable
private fun playerLabel(p: PlayerChoice) = stringResource(
    when (p) { PlayerChoice.BUILT_IN -> R.string.player_builtin; PlayerChoice.VLC -> R.string.player_vlc; PlayerChoice.MX_PLAYER -> R.string.player_mx; PlayerChoice.SYSTEM -> R.string.player_system },
)

@Composable
private fun liveFormatLabel(f: LiveFormat) = stringResource(
    when (f) {
        LiveFormat.AUTO -> R.string.format_auto; LiveFormat.HLS -> R.string.format_hls; LiveFormat.MPEG_TS -> R.string.format_ts
        LiveFormat.DASH -> R.string.format_dash; LiveFormat.RTMP -> R.string.format_rtmp; LiveFormat.PROGRESSIVE_MP4 -> R.string.format_mp4
    },
)

@Composable
private fun bufferLabel(b: BufferProfile) = stringResource(when (b) { BufferProfile.LOW -> R.string.buffer_low; BufferProfile.NORMAL -> R.string.buffer_normal; BufferProfile.HIGH -> R.string.buffer_high })

@Composable
private fun edgeLabel(e: SubtitleEdge) = stringResource(
    when (e) { SubtitleEdge.NONE -> R.string.edge_none; SubtitleEdge.OUTLINE -> R.string.edge_outline; SubtitleEdge.DROP_SHADOW -> R.string.edge_shadow; SubtitleEdge.RAISED -> R.string.edge_raised; SubtitleEdge.DEPRESSED -> R.string.edge_depressed },
)

@Composable
private fun sourceLabel(s: SubtitleSource) = stringResource(
    when (s) { SubtitleSource.EMBEDDED_AND_EXTERNAL -> R.string.sub_source_both; SubtitleSource.EMBEDDED_ONLY -> R.string.sub_source_embedded; SubtitleSource.EXTERNAL_ONLY -> R.string.sub_source_external },
)

private fun langName(tag: String): String = if (tag.isBlank()) "—" else Locale.forLanguageTag(tag).let { it.getDisplayLanguage(it).replaceFirstChar { c -> c.titlecase(it) } }

@Composable
private fun refreshLabel(h: Int) = if (h <= 0) stringResource(R.string.off) else stringResource(R.string.every_hours, h)

private val timeZones = listOf(
    "system", "UTC", "Africa/Cairo", "Asia/Riyadh", "Asia/Dubai", "Asia/Kuwait", "Asia/Qatar", "Asia/Baghdad", "Asia/Amman", "Asia/Beirut", "Asia/Damascus",
    "Africa/Casablanca", "Africa/Algiers", "Africa/Tunis", "Africa/Tripoli", "Africa/Khartoum", "Europe/London", "Europe/Paris", "Europe/Berlin",
    "Europe/Istanbul", "Europe/Moscow", "America/New_York", "America/Chicago", "America/Los_Angeles", "Asia/Karachi", "Asia/Kolkata",
)

// ---------------------------------------------------------------- Sections

@Composable
private fun GeneralSection(ctx: SettingsCtx) {
    val s = ctx.s
    val restoreTitle = stringResource(R.string.settings_restore_defaults)
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmCache by remember { mutableStateOf(false) }
    val langTitle = stringResource(R.string.settings_language)
    val themeTitle = stringResource(R.string.settings_theme)
    val tzTitle = stringResource(R.string.settings_time_zone)
    val refreshTitle = stringResource(R.string.settings_auto_refresh)
    val sys = stringResource(R.string.system_default)
    val themes = ThemeMode.entries.map { themeLabel(it) }
    val refreshOptions = listOf(0, 6, 12, 24, 48)
    val refreshLabels = refreshOptions.map { refreshLabel(it) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroup(null) {
            ValueRow(langTitle, languageLabel(s.language), {
                ctx.choose(ChoiceRequest(langTitle, AppLanguage.entries.map { if (it == AppLanguage.ARABIC) "العربية" else "English" }, s.language.ordinal) { i -> ctx.vm.update { it.copy(language = AppLanguage.entries[i]) } })
            }, icon = Icons.Default.Language)
            ValueRow(themeTitle, themeLabel(s.theme), {
                ctx.choose(ChoiceRequest(themeTitle, themes, s.theme.ordinal) { i -> ctx.vm.update { it.copy(theme = ThemeMode.entries[i]) } })
            }, icon = Icons.Default.Brightness6)
            ValueRow(tzTitle, if (s.timeZone == "system") sys else s.timeZone, {
                ctx.choose(ChoiceRequest(tzTitle, timeZones.map { if (it == "system") sys else it }, timeZones.indexOf(s.timeZone).coerceAtLeast(0)) { i -> ctx.vm.update { it.copy(timeZone = timeZones[i]) } })
            }, icon = Icons.Default.Schedule)
            SwitchRow(stringResource(R.string.settings_notifications), s.notificationsEnabled, { v -> ctx.vm.update { it.copy(notificationsEnabled = v) } }, icon = Icons.Default.Notifications)
            ValueRow(refreshTitle, refreshLabel(s.autoRefreshHours), {
                ctx.choose(ChoiceRequest(refreshTitle, refreshLabels, refreshOptions.indexOf(s.autoRefreshHours).coerceAtLeast(0)) { i -> ctx.vm.update { it.copy(autoRefreshHours = refreshOptions[i]) } })
            }, subtitle = stringResource(R.string.settings_auto_refresh_hint), icon = Icons.Default.Refresh)
        }
        SettingsGroup(null) {
            ActionRow(stringResource(R.string.settings_clear_cache), { confirmCache = true }, icon = Icons.Default.DeleteSweep)
            ActionRow(restoreTitle, { ctx.pinGate.requirePin(R.string.pin_required_message) { confirmRestore = true } }, icon = Icons.Default.Restore, destructive = true)
        }
    }
    if (confirmRestore) ConfirmDialog(restoreTitle, stringResource(R.string.settings_restore_confirm), stringResource(R.string.action_restore), { ctx.vm.restoreDefaults { ctx.messenger.show(R.string.msg_defaults_restored) } }, { confirmRestore = false }, destructive = true)
    if (confirmCache) ConfirmDialog(stringResource(R.string.settings_clear_cache), stringResource(R.string.settings_clear_cache_confirm), stringResource(R.string.action_clear), {
        ctx.vm.clearImageCache { }; ctx.vm.clearEpgCache { }; ctx.vm.clearDetailsCache { ctx.messenger.show(R.string.msg_cache_cleared) }
    }, { confirmCache = false })
}

@Composable
private fun PlayerSection(ctx: SettingsCtx) {
    val s = ctx.s
    val players by ctx.vm.players.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { ctx.vm.refreshPlayers() }
    val title = stringResource(R.string.settings_default_player)
    val unavailable = stringResource(R.string.player_not_installed)
    val choices = PlayerChoice.entries.toList()
    val available = choices.map { ch -> ch == PlayerChoice.BUILT_IN || players.any { it.choice == ch && it.isInstalled } }
    val labels = choices.mapIndexed { i, ch -> playerLabel(ch) + if (!available[i]) " ($unavailable)" else "" }
    val decTitle = stringResource(R.string.settings_decoder)
    val decLabels = listOf(stringResource(R.string.decoder_hw), stringResource(R.string.decoder_sw))
    val audioTitle = stringResource(R.string.settings_audio_output)
    val audioLabels = listOf(stringResource(R.string.audio_auto), stringResource(R.string.audio_pcm))
    val aspectTitle = stringResource(R.string.settings_default_aspect)
    val aspects = AspectMode.entries.map { aspectLabel(it) }
    val bufferTitle = stringResource(R.string.settings_buffering)
    val buffers = BufferProfile.entries.map { bufferLabel(it) }
    val audioLangTitle = stringResource(R.string.settings_audio_language)
    val langs = listOf("") + SubtitleOptions.languages
    val langLabels = langs.map { if (it.isBlank()) stringResource(R.string.auto) else langName(it) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroup(stringResource(R.string.settings_players_group)) {
            ValueRow(title, playerLabel(s.defaultPlayer), {
                ctx.choose(ChoiceRequest(title, labels, choices.indexOf(s.defaultPlayer), available) { i -> ctx.vm.update { it.copy(defaultPlayer = choices[i]) } })
            }, subtitle = stringResource(R.string.settings_default_player_hint), icon = Icons.Default.PlayCircle)
            players.forEach { p ->
                ValueRow(playerLabel(p.choice), if (p.isInstalled) stringResource(R.string.player_installed) else unavailable, {}, enabled = p.isInstalled)
            }
        }
        SettingsGroup(stringResource(R.string.settings_decoding_group)) {
            ValueRow(decTitle, decLabels[s.decoder.ordinal], { ctx.choose(ChoiceRequest(decTitle, decLabels, s.decoder.ordinal) { i -> ctx.vm.update { it.copy(decoder = DecoderMode.entries[i]) } }) }, subtitle = stringResource(R.string.settings_decoder_hint))
            ValueRow(audioTitle, audioLabels[s.audioOutput.ordinal], { ctx.choose(ChoiceRequest(audioTitle, audioLabels, s.audioOutput.ordinal) { i -> ctx.vm.update { it.copy(audioOutput = AudioOutputMode.entries[i]) } }) }, subtitle = stringResource(R.string.settings_audio_output_hint))
            ValueRow(audioLangTitle, langLabels[langs.indexOf(s.preferredAudioLanguage).coerceAtLeast(0)], { ctx.choose(ChoiceRequest(audioLangTitle, langLabels, langs.indexOf(s.preferredAudioLanguage).coerceAtLeast(0)) { i -> ctx.vm.update { it.copy(preferredAudioLanguage = langs[i]) } }) })
            ValueRow(aspectTitle, aspectLabel(s.defaultAspect), { ctx.choose(ChoiceRequest(aspectTitle, aspects, s.defaultAspect.ordinal) { i -> ctx.vm.update { it.copy(defaultAspect = AspectMode.entries[i]) } }) })
            ValueRow(bufferTitle, bufferLabel(s.bufferProfile), { ctx.choose(ChoiceRequest(bufferTitle, buffers, s.bufferProfile.ordinal) { i -> ctx.vm.update { it.copy(bufferProfile = BufferProfile.entries[i]) } }) }, subtitle = stringResource(R.string.settings_buffering_hint))
            SwitchRow(stringResource(R.string.settings_data_saver), s.dataSaver, { v -> ctx.vm.update { it.copy(dataSaver = v) } }, subtitle = stringResource(R.string.settings_data_saver_hint))
        }
        SettingsGroup(stringResource(R.string.settings_playback)) {
            SwitchRow(stringResource(R.string.settings_auto_play), s.autoPlay, { v -> ctx.vm.update { it.copy(autoPlay = v) } })
            SwitchRow(stringResource(R.string.settings_auto_next), s.autoNextEpisode, { v -> ctx.vm.update { it.copy(autoNextEpisode = v) } })
            SwitchRow(stringResource(R.string.settings_resume), s.resumePlayback, { v -> ctx.vm.update { it.copy(resumePlayback = v) } })
        }
    }
}

@Composable
private fun LiveFormatSection(ctx: SettingsCtx) {
    val s = ctx.s
    val playlist by ctx.vm.playlist.collectAsStateWithLifecycle()
    val c = AkaaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.live_format_hint), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        playlist?.let { p ->
            Text(
                stringResource(R.string.live_format_server, p.allowedOutputFormats.joinToString(", ").ifBlank { stringResource(R.string.not_available) }, p.rtmpPort?.takeIf { it.isNotBlank() && it != "0" } ?: "—"),
                style = MaterialTheme.typography.bodySmall, color = c.gold,
            )
        }
        SettingsGroup(null) {
            LiveFormat.entries.forEach { f ->
                val supported = playlist?.let { p ->
                    when (f) {
                        LiveFormat.AUTO -> true
                        LiveFormat.RTMP -> !p.rtmpPort.isNullOrBlank() && p.rtmpPort != "0"
                        LiveFormat.HLS -> p.allowedOutputFormats.isEmpty() || "m3u8" in p.allowedOutputFormats
                        LiveFormat.MPEG_TS -> p.allowedOutputFormats.isEmpty() || "ts" in p.allowedOutputFormats
                        LiveFormat.DASH -> "mpd" in p.allowedOutputFormats
                        LiveFormat.PROGRESSIVE_MP4 -> "mp4" in p.allowedOutputFormats
                    }
                } ?: true
                ValueRow(
                    liveFormatLabel(f),
                    if (s.liveFormat == f) "✓" else "",
                    { ctx.vm.update { it.copy(liveFormat = f) } },
                    subtitle = if (!supported) stringResource(R.string.live_format_unsupported) else null,
                )
            }
        }
    }
}

@Composable
private fun SubtitlesSection(ctx: SettingsCtx) {
    val s = ctx.s
    val langTitle = stringResource(R.string.settings_subtitle_language)
    val langLabels = SubtitleOptions.languages.map { langName(it) }
    val encTitle = stringResource(R.string.settings_subtitle_encoding)
    val srcTitle = stringResource(R.string.settings_subtitle_source)
    val sources = SubtitleSource.entries.map { sourceLabel(it) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroup(null) {
            SwitchRow(stringResource(R.string.settings_subtitles_enable), s.subtitlesEnabled, { v -> ctx.vm.update { it.copy(subtitlesEnabled = v) } }, icon = Icons.Default.Subtitles)
            ValueRow(langTitle, langName(s.subtitleLanguage), {
                ctx.choose(ChoiceRequest(langTitle, langLabels, SubtitleOptions.languages.indexOf(s.subtitleLanguage).coerceAtLeast(0)) { i -> ctx.vm.update { it.copy(subtitleLanguage = SubtitleOptions.languages[i]) } })
            }, icon = Icons.Default.Language)
            ValueRow(srcTitle, sourceLabel(s.subtitleSource), {
                ctx.choose(ChoiceRequest(srcTitle, sources, s.subtitleSource.ordinal) { i -> ctx.vm.update { it.copy(subtitleSource = SubtitleSource.entries[i]) } })
            })
            ValueRow(encTitle, s.subtitleEncoding, {
                ctx.choose(ChoiceRequest(encTitle, SubtitleOptions.encodings, SubtitleOptions.encodings.indexOf(s.subtitleEncoding).coerceAtLeast(0)) { i -> ctx.vm.update { it.copy(subtitleEncoding = SubtitleOptions.encodings[i]) } })
            }, subtitle = stringResource(R.string.settings_subtitle_encoding_hint), icon = Icons.Default.TextFields)
        }
        Text(stringResource(R.string.settings_subtitles_note), style = MaterialTheme.typography.bodySmall, color = AkaaTheme.colors.textSecondary)
    }
}

@Composable
private fun SubtitleAppearanceSection(ctx: SettingsCtx) {
    val s = ctx.s
    val c = AkaaTheme.colors
    val edgeTitle = stringResource(R.string.subtitle_edge)
    val edges = SubtitleEdge.entries.map { edgeLabel(it) }
    val posTitle = stringResource(R.string.subtitle_position)
    val positions = SubtitlePosition.entries.map { positionLabel(it) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Live preview
        Box(Modifier.fillMaxWidth().height(110.dp).background(Color(0xFF1B2A44), RoundedCornerShape(14.dp)), contentAlignment = Alignment.BottomCenter) {
            val alpha = s.subtitleBackgroundOpacity / 100f
            Text(
                stringResource(R.string.subtitle_preview_text),
                color = Color(s.subtitleTextColor),
                fontSize = MaterialTheme.typography.titleMedium.fontSize * (s.subtitleScale / 100f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp).background(Color(s.subtitleBackgroundColor).copy(alpha = alpha), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        SettingsGroup(null) {
            StepRow(stringResource(R.string.subtitle_size), "${s.subtitleScale}%", { ctx.vm.update { it.copy(subtitleScale = (it.subtitleScale - 10).coerceAtLeast(50)) } }, { ctx.vm.update { it.copy(subtitleScale = (it.subtitleScale + 10).coerceAtMost(250)) } }, icon = Icons.Default.TextFields)
            ColorRow(stringResource(R.string.subtitle_text_color), s.subtitleTextColor) { col -> ctx.vm.update { it.copy(subtitleTextColor = col) } }
            ColorRow(stringResource(R.string.subtitle_bg_color), s.subtitleBackgroundColor) { col -> ctx.vm.update { it.copy(subtitleBackgroundColor = col) } }
            StepRow(stringResource(R.string.subtitle_bg_opacity), "${s.subtitleBackgroundOpacity}%", { ctx.vm.update { it.copy(subtitleBackgroundOpacity = (it.subtitleBackgroundOpacity - 10).coerceAtLeast(0)) } }, { ctx.vm.update { it.copy(subtitleBackgroundOpacity = (it.subtitleBackgroundOpacity + 10).coerceAtMost(100)) } })
            ValueRow(edgeTitle, edgeLabel(s.subtitleEdge), { ctx.choose(ChoiceRequest(edgeTitle, edges, s.subtitleEdge.ordinal) { i -> ctx.vm.update { it.copy(subtitleEdge = SubtitleEdge.entries[i]) } }) }, icon = Icons.Default.BorderStyle)
            ValueRow(posTitle, positionLabel(s.subtitlePosition), { ctx.choose(ChoiceRequest(posTitle, positions, s.subtitlePosition.ordinal) { i -> ctx.vm.update { it.copy(subtitlePosition = SubtitlePosition.entries[i]) } }) })
            StepRow(
                stringResource(R.string.subtitle_delay), stringResource(R.string.subtitle_delay_value, s.subtitleDelayMs / 1000f),
                { ctx.vm.update { it.copy(subtitleDelayMs = it.subtitleDelayMs - 250) } }, { ctx.vm.update { it.copy(subtitleDelayMs = it.subtitleDelayMs + 250) } },
                subtitle = stringResource(R.string.subtitle_delay_hint),
            )
        }
        Text(stringResource(R.string.subtitle_sync_note), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

@Composable
private fun ColorRow(title: String, selected: Long, onSelect: (Long) -> Unit) {
    val c = AkaaTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f))
        SubtitleOptions.colors.forEach { col ->
            AkaaCard(onClick = { onSelect(col) }, modifier = Modifier.padding(horizontal = 3.dp).size(30.dp), shape = CircleShape, containerColor = Color(col), focusScale = 1.15f) { _ ->
                Box(Modifier.matchParentSize().border(if (selected == col) 3.dp else 1.dp, if (selected == col) c.accent else c.border, CircleShape))
            }
        }
    }
}

@Composable
private fun HideContentSection(ctx: SettingsCtx) {
    val s = ctx.s
    val rules by ctx.vm.hiddenRules.collectAsStateWithLifecycle()
    var manageType by remember { mutableStateOf<ContentType?>(null) }
    var addingKeyword by remember { mutableStateOf(false) }
    var confirmRestoreAll by remember { mutableStateOf(false) }
    val c = AkaaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.hide_content_hint), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        SettingsGroup(stringResource(R.string.hide_sections)) {
            SwitchRow(stringResource(R.string.hide_live_section), s.hideLiveSection, { v -> ctx.vm.update { it.copy(hideLiveSection = v) } })
            SwitchRow(stringResource(R.string.hide_movies_section), s.hideMoviesSection, { v -> ctx.vm.update { it.copy(hideMoviesSection = v) } })
            SwitchRow(stringResource(R.string.hide_series_section), s.hideSeriesSection, { v -> ctx.vm.update { it.copy(hideSeriesSection = v) } })
        }
        SettingsGroup(stringResource(R.string.hide_categories)) {
            ActionRow(stringResource(R.string.hide_live_categories), { manageType = ContentType.LIVE })
            ActionRow(stringResource(R.string.hide_movie_categories), { manageType = ContentType.MOVIE })
            ActionRow(stringResource(R.string.hide_series_categories), { manageType = ContentType.SERIES })
            ActionRow(stringResource(R.string.hide_keywords_add), { addingKeyword = true }, subtitle = stringResource(R.string.hide_keywords_hint), icon = Icons.Default.TextFields)
        }
        SettingsGroup(stringResource(R.string.hidden_items_title, rules.size)) {
            if (rules.isEmpty()) {
                Text(stringResource(R.string.hidden_items_empty), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(12.dp))
            }
            rules.forEach { r ->
                ValueRow(
                    r.title,
                    stringResource(R.string.action_restore),
                    { ctx.vm.removeRule(r) },
                    subtitle = ruleSubtitle(r.scope, r.contentType),
                    icon = Icons.Default.Visibility,
                )
            }
            if (rules.isNotEmpty()) ActionRow(stringResource(R.string.action_restore_all), { confirmRestoreAll = true }, icon = Icons.Default.Restore)
        }
    }
    manageType?.let { t -> CategoryVisibilityDialog(ctx, t, rules.filter { it.scope == RuleScope.CATEGORY && it.contentType == t }.map { it.targetId }.toSet()) { manageType = null } }
    if (addingKeyword) {
        TextInputDialog(
            title = stringResource(R.string.hide_keywords_add), label = stringResource(R.string.hide_keyword_label), initial = "",
            confirmLabel = stringResource(R.string.action_hide),
            onConfirm = { ctx.vm.addKeyword(it); addingKeyword = false; ctx.messenger.show(R.string.msg_hidden_item, it) },
            onDismiss = { addingKeyword = false },
        )
    }
    if (confirmRestoreAll) ConfirmDialog(stringResource(R.string.action_restore_all), stringResource(R.string.restore_all_confirm), stringResource(R.string.action_restore), { ctx.vm.clearRules(RuleRestriction.HIDDEN) }, { confirmRestoreAll = false })
}

@Composable
private fun ruleSubtitle(scope: RuleScope, type: ContentType?): String {
    val scopeLabel = stringResource(
        when (scope) { RuleScope.CATEGORY -> R.string.rule_category; RuleScope.ITEM -> R.string.rule_item; RuleScope.GROUP -> R.string.rule_group; RuleScope.KEYWORD -> R.string.rule_keyword },
    )
    return if (type != null && scope != RuleScope.GROUP && scope != RuleScope.KEYWORD) "$scopeLabel · ${stringResource(typeLabel(type))}" else scopeLabel
}

@Composable
private fun CategoryVisibilityDialog(ctx: SettingsCtx, type: ContentType, hidden: Set<String>, onDismiss: () -> Unit) {
    var cats by remember { mutableStateOf<List<Category>?>(null) }
    LaunchedEffect(type) { cats = ctx.vm.allCategories(type) }
    val first = remember { FocusRequester() }
    AkaaDialog(onDismiss = onDismiss, title = stringResource(R.string.hide_categories)) {
        Text(stringResource(R.string.hide_categories_dialog_hint), style = MaterialTheme.typography.bodySmall, color = AkaaTheme.colors.textSecondary)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val list = cats.orEmpty()
            if (cats != null && list.isEmpty()) item { Text(stringResource(R.string.empty_section_message), color = AkaaTheme.colors.textSecondary) }
            items(list, key = { it.id }) { cat ->
                val isHidden = cat.id in hidden
                MenuRow(
                    if (isHidden) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    cat.name, if (cat == list.first()) first else null,
                ) { ctx.vm.setCategoryHidden(type, cat, !isHidden) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { AkaaButton(stringResource(R.string.action_done), onDismiss, variant = ButtonVariant.ACCENT) }
    }
    LaunchedEffect(cats) { if (!cats.isNullOrEmpty()) runCatching { first.requestFocus() } }
}

@Composable
private fun ParentalSection(ctx: SettingsCtx) {
    val s = ctx.s
    val unlocked by ctx.vm.adultUnlocked.collectAsStateWithLifecycle()
    val locked by ctx.vm.lockedRules.collectAsStateWithLifecycle()
    val c = AkaaTheme.colors
    val ageTitle = stringResource(R.string.parental_age)
    val ages = listOf(0, 7, 12, 13, 16, 18)
    val ageLabels = ages.map { if (it == 0) stringResource(R.string.parental_age_none) else "+$it" }
    val guarded: (() -> Unit) -> Unit = { action -> if (s.hasPin) ctx.pinGate.requirePin(R.string.pin_required_message, always = true, action = action) else ctx.startPinSetup() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.parental_hint), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        SettingsGroup(null) {
            SwitchRow(
                stringResource(R.string.parental_enable), s.parentalEnabled,
                { v -> if (!s.hasPin) ctx.startPinSetup() else guarded { ctx.vm.update { it.copy(parentalEnabled = v) } } },
                icon = Icons.Default.Shield,
            )
            ActionRow(
                stringResource(if (s.hasPin) R.string.parental_change_pin else R.string.parental_set_pin),
                { if (s.hasPin) guarded { ctx.startPinSetup() } else ctx.startPinSetup() }, icon = Icons.Default.Password,
            )
            SwitchRow(stringResource(R.string.parental_hide_adult), s.hideAdultContent, { v -> guarded { ctx.vm.update { it.copy(hideAdultContent = v) } } }, subtitle = stringResource(R.string.parental_hide_adult_hint), enabled = s.parentalEnabled)
            ValueRow(ageTitle, ageLabels[ages.indexOf(s.maxAgeRating).coerceAtLeast(0)], {
                guarded { ctx.choose(ChoiceRequest(ageTitle, ageLabels, ages.indexOf(s.maxAgeRating).coerceAtLeast(0)) { i -> ctx.vm.update { it.copy(maxAgeRating = ages[i]) } }) }
            }, subtitle = stringResource(R.string.parental_age_hint), enabled = s.parentalEnabled)
            if (s.parentalEnabled && s.hideAdultContent) {
                if (unlocked) ActionRow(stringResource(R.string.parental_lock_now), { ctx.vm.lockAdult() }, icon = Icons.Default.Lock)
                else ActionRow(stringResource(R.string.parental_unlock_session), { guarded { ctx.vm.unlockAdult() } }, icon = Icons.Default.LockOpen)
            }
        }
        SettingsGroup(stringResource(R.string.parental_locked_items, locked.size)) {
            if (locked.isEmpty()) Text(stringResource(R.string.parental_locked_empty), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(12.dp))
            locked.forEach { r ->
                ValueRow(r.title, stringResource(R.string.action_unlock), { guarded { ctx.vm.removeRule(r) } }, subtitle = ruleSubtitle(r.scope, r.contentType), icon = Icons.Default.Lock)
            }
        }
    }
}

@Composable
private fun LinkSection(section: SettingsSection, ctx: SettingsCtx) {
    SettingsGroup(null) {
        if (section == SettingsSection.PLAYLISTS) {
            ActionRow(stringResource(R.string.playlists_title), { ctx.navigator.goToPlaylists(false, false) }, icon = Icons.AutoMirrored.Filled.PlaylistPlay)
            ActionRow(stringResource(R.string.action_add_playlist), { ctx.navigator.addPlaylist() })
        } else {
            ActionRow(stringResource(R.string.nav_favorites), { ctx.navigator.navigateTopLevel(MyListRoute(0)) })
            ActionRow(stringResource(R.string.nav_groups), { ctx.navigator.navigateTopLevel(MyListRoute(1)) }, icon = Icons.Default.Layers)
        }
    }
}

@Composable
private fun LanguageSection(ctx: SettingsCtx) {
    SettingsGroup(null) {
        AppLanguage.entries.forEach { l ->
            ValueRow(languageLabel(l), if (ctx.s.language == l) "✓" else "", { ctx.vm.update { it.copy(language = l) } }, icon = Icons.Default.Language)
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.language_hint), style = MaterialTheme.typography.bodySmall, color = AkaaTheme.colors.textSecondary)
}

@Composable
private fun AppearanceSection(ctx: SettingsCtx) {
    val s = ctx.s
    val c = AkaaTheme.colors
    val modeTitle = stringResource(R.string.settings_interface_mode)
    val modes = listOf(stringResource(R.string.auto), stringResource(R.string.interface_mobile), stringResource(R.string.interface_tv))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroup(stringResource(R.string.settings_theme)) {
            ThemeMode.entries.forEach { t -> ValueRow(themeLabel(t), if (s.theme == t) "✓" else "", { ctx.vm.update { it.copy(theme = t) } }) }
        }
        SettingsGroup(stringResource(R.string.settings_accent)) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentColor.entries.forEach { a ->
                    AkaaCard(onClick = { ctx.vm.update { it.copy(accent = a) } }, modifier = Modifier.size(40.dp), shape = CircleShape, containerColor = Color(a.argb), focusScale = 1.15f) { _ ->
                        if (s.accent == a) Box(Modifier.matchParentSize().border(3.dp, Color.White, CircleShape))
                    }
                }
            }
        }
        SettingsGroup(null) {
            ValueRow(modeTitle, modes[s.interfaceMode.ordinal], {
                ctx.choose(ChoiceRequest(modeTitle, modes, s.interfaceMode.ordinal) { i -> ctx.vm.update { it.copy(interfaceMode = InterfaceMode.entries[i]) } })
            }, subtitle = stringResource(R.string.settings_interface_mode_hint))
        }
        Text(stringResource(R.string.appearance_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

@Composable
private fun NotificationsSection(ctx: SettingsCtx) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) ctx.messenger.show(R.string.msg_notification_permission_denied)
    }
    SettingsGroup(null) {
        SwitchRow(stringResource(R.string.settings_notifications), ctx.s.notificationsEnabled, { v ->
            ctx.vm.update { it.copy(notificationsEnabled = v) }
            if (v && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }, subtitle = stringResource(R.string.settings_notifications_hint), icon = Icons.Default.Notifications)
    }
}

@Composable
private fun EpgSection(ctx: SettingsCtx) {
    val s = ctx.s
    val cacheTitle = stringResource(R.string.epg_cache_duration)
    val hours = listOf(1, 3, 6, 12, 24)
    val labels = hours.map { stringResource(R.string.hours_n, it) }
    SettingsGroup(null) {
        SwitchRow(stringResource(R.string.epg_enable), s.epgEnabled, { v -> ctx.vm.update { it.copy(epgEnabled = v) } }, subtitle = stringResource(R.string.epg_enable_hint), icon = Icons.Default.CalendarViewDay)
        StepRow(stringResource(R.string.epg_time_shift), (if (s.epgTimeShiftHours > 0) "+" else "") + "${s.epgTimeShiftHours}h",
            { ctx.vm.update { it.copy(epgTimeShiftHours = (it.epgTimeShiftHours - 1).coerceAtLeast(-12)) } },
            { ctx.vm.update { it.copy(epgTimeShiftHours = (it.epgTimeShiftHours + 1).coerceAtMost(12)) } },
            subtitle = stringResource(R.string.epg_time_shift_hint))
        ValueRow(cacheTitle, stringResource(R.string.hours_n, s.epgCacheHours), {
            ctx.choose(ChoiceRequest(cacheTitle, labels, hours.indexOf(s.epgCacheHours).coerceAtLeast(0)) { i -> ctx.vm.update { it.copy(epgCacheHours = hours[i]) } })
        })
        ActionRow(stringResource(R.string.epg_clear_cache), { ctx.vm.clearEpgCache { ctx.messenger.show(R.string.msg_cache_cleared) } }, icon = Icons.Default.DeleteSweep)
    }
}

@Composable
private fun PlaybackSection(ctx: SettingsCtx) {
    val s = ctx.s
    SettingsGroup(null) {
        SwitchRow(stringResource(R.string.settings_resume), s.resumePlayback, { v -> ctx.vm.update { it.copy(resumePlayback = v) } }, subtitle = stringResource(R.string.settings_resume_hint))
        SwitchRow(stringResource(R.string.settings_auto_next), s.autoNextEpisode, { v -> ctx.vm.update { it.copy(autoNextEpisode = v) } })
        SwitchRow(stringResource(R.string.settings_auto_play), s.autoPlay, { v -> ctx.vm.update { it.copy(autoPlay = v) } }, subtitle = stringResource(R.string.settings_auto_play_hint))
        SwitchRow(stringResource(R.string.settings_remember_channel), s.rememberLastChannel, { v -> ctx.vm.update { it.copy(rememberLastChannel = v) } })
        SwitchRow(stringResource(R.string.settings_live_preview), s.livePreview, { v -> ctx.vm.update { it.copy(livePreview = v) } }, subtitle = stringResource(R.string.settings_live_preview_hint))
    }
}

private fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", b / (1L shl 10).toDouble())
    else -> "$b B"
}

@Composable
private fun CacheSection(ctx: SettingsCtx) {
    val storage by ctx.vm.storage.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { ctx.vm.refreshStorage() }
    SettingsGroup(null) {
        ValueRow(stringResource(R.string.cache_images), formatBytes(storage.imageCacheBytes), { ctx.vm.clearImageCache { ctx.messenger.show(R.string.msg_cache_cleared) } }, subtitle = stringResource(R.string.cache_images_hint), icon = Icons.Default.DeleteSweep)
        ActionRow(stringResource(R.string.epg_clear_cache), { ctx.vm.clearEpgCache { ctx.messenger.show(R.string.msg_cache_cleared) } })
        ActionRow(stringResource(R.string.cache_details), { ctx.vm.clearDetailsCache { ctx.messenger.show(R.string.msg_cache_cleared) } })
        ActionRow(stringResource(R.string.cache_refresh_catalog), { ctx.vm.refreshCatalogNow(); ctx.messenger.show(R.string.sync_in_progress) }, subtitle = stringResource(R.string.cache_refresh_catalog_hint), icon = Icons.Default.Refresh)
    }
}

@Composable
private fun DataSection(ctx: SettingsCtx) {
    val storage by ctx.vm.storage.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    var confirmHistory by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroup(null) {
            ValueRow(stringResource(R.string.data_database), formatBytes(storage.databaseBytes), {}, icon = Icons.Default.Storage)
            SwitchRow(stringResource(R.string.data_save_search), ctx.s.saveSearchHistory, { v -> ctx.vm.update { it.copy(saveSearchHistory = v) } })
            ActionRow(stringResource(R.string.data_clear_search), { ctx.vm.clearSearchHistory { ctx.messenger.show(R.string.msg_cleared) } })
            ActionRow(stringResource(R.string.action_clear_history), { confirmHistory = true })
        }
        SettingsGroup(null) {
            ActionRow(stringResource(R.string.data_reset_all), { ctx.pinGate.requirePin(R.string.pin_required_message) { confirmReset = true } }, subtitle = stringResource(R.string.data_reset_all_hint), icon = Icons.Default.DeleteForever, destructive = true)
        }
    }
    if (confirmHistory) ConfirmDialog(stringResource(R.string.action_clear_history), stringResource(R.string.history_clear_confirm), stringResource(R.string.action_clear), { ctx.vm.clearWatchHistory { ctx.messenger.show(R.string.msg_cleared) } }, { confirmHistory = false }, destructive = true)
    if (confirmReset) ConfirmDialog(stringResource(R.string.data_reset_all), stringResource(R.string.data_reset_confirm), stringResource(R.string.action_delete), {
        ctx.vm.resetAll { ctx.navigator.addPlaylist(firstRun = true) }
    }, { confirmReset = false }, destructive = true)
}

@Composable
private fun DevicesSection(ctx: SettingsCtx) {
    val playlist by ctx.vm.playlist.collectAsStateWithLifecycle()
    SettingsGroup(null) {
        ValueRow(stringResource(R.string.devices_this_device), "${Build.MANUFACTURER} ${Build.MODEL}", {}, icon = Icons.Default.Devices)
        ValueRow(stringResource(R.string.devices_android), "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", {})
        val p = playlist
        ValueRow(
            stringResource(R.string.devices_active_connections),
            if (p?.activeConnections != null && p.maxConnections != null) "${p.activeConnections} / ${p.maxConnections}" else stringResource(R.string.not_available),
            {},
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.devices_note), style = MaterialTheme.typography.bodySmall, color = AkaaTheme.colors.textSecondary)
}

@Composable
private fun BackupSection(ctx: SettingsCtx) {
    var includeCreds by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) ctx.vm.exportBackup(uri, includeCreds) { ok -> ctx.messenger.show(if (ok) R.string.msg_backup_saved else R.string.msg_backup_failed) }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ctx.vm.importBackup(uri) { report ->
            if (report == null) ctx.messenger.show(R.string.msg_restore_failed)
            else ctx.messenger.show(R.string.msg_restore_done, report.playlistsAdded + report.playlistsMatched, report.favorites, report.groups, report.rules)
        }
    }
    val c = AkaaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        SettingsGroup(null) {
            SwitchRow(stringResource(R.string.backup_include_credentials), includeCreds, { includeCreds = it }, subtitle = stringResource(R.string.backup_include_credentials_hint))
            ActionRow(stringResource(R.string.backup_export), {
                scope.launch {
                    val name = "akaaflix-backup-" + DateFormat.getDateInstance(DateFormat.SHORT, Locale.US).format(Date()).replace('/', '-') + ".json"
                    runCatching { exporter.launch(name) }.onFailure { ctx.messenger.show(R.string.player_no_file_picker) }
                }
            }, icon = Icons.Default.Upload)
            ActionRow(stringResource(R.string.backup_import), {
                runCatching { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }.onFailure { ctx.messenger.show(R.string.player_no_file_picker) }
            }, icon = Icons.Default.Download)
        }
    }
}

@Composable
private fun AboutSection() {
    val c = AkaaTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        AkaaLogo(height = 90.dp)
        Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), style = MaterialTheme.typography.titleMedium, color = c.text)
        Text(BuildConfig.APPLICATION_ID, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        HorizontalDivider(color = c.border)
        Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyMedium, color = c.text, textAlign = TextAlign.Center)
        Text(stringResource(R.string.about_xtream_only), style = MaterialTheme.typography.bodyMedium, color = c.gold, textAlign = TextAlign.Center)
        Text(stringResource(R.string.about_disclaimer), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center)
        Text(stringResource(R.string.about_libraries), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center)
    }
}
