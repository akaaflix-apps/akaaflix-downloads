package com.akaaflix.prime.presentation.account

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.akaaflix.prime.BuildConfig
import com.akaaflix.prime.R
import com.akaaflix.prime.common.AppError
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.common.TimeFormat
import com.akaaflix.prime.data.SessionManager
import com.akaaflix.prime.data.settings.SettingsRepository
import com.akaaflix.prime.domain.model.AccountStatus
import com.akaaflix.prime.domain.model.ConnectionStatus
import com.akaaflix.prime.domain.model.Playlist
import com.akaaflix.prime.domain.repository.PlaylistRepository
import com.akaaflix.prime.navigation.LocalNavigator
import com.akaaflix.prime.network.ServerUrl
import com.akaaflix.prime.presentation.components.AkaaButton
import com.akaaflix.prime.presentation.components.AkaaDialog
import com.akaaflix.prime.presentation.components.AkaaLogo
import com.akaaflix.prime.presentation.components.ButtonVariant
import com.akaaflix.prime.presentation.components.ConfirmDialog
import com.akaaflix.prime.presentation.components.LoadingState
import com.akaaflix.prime.presentation.components.MenuRow
import com.akaaflix.prime.presentation.components.akaaViewModel
import com.akaaflix.prime.presentation.theme.AkaaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(
    private val session: SessionManager,
    private val playlists: PlaylistRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val playlist: StateFlow<Playlist?> = session.activePlaylist
    val timeZone: StateFlow<String> = settings.settings.map { it.timeZone }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    init { refresh() }

    /** Re-reads subscription info from the Xtream server (the only source of truth). */
    fun refresh() = viewModelScope.launch {
        val p = session.activePlaylist.value ?: return@launch
        _refreshing.value = true
        val r = playlists.refreshAccount(p.id)
        _error.value = (r as? AppResult.Failure)?.error
        _refreshing.value = false
    }

    fun logout(onDone: (hasOthers: Boolean) -> Unit) = viewModelScope.launch {
        playlists.clearActive()
        session.lockAdult()
        onDone(playlists.count() > 0)
    }
}

/** Account & subscription dashboard built from Xtream `user_info` / `server_info`. */
@Composable
fun AccountScreen() {
    val vm = akaaViewModel { c, _ -> AccountViewModel(c.sessionManager, c.playlistRepository, c.settingsRepository) }
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val tz by vm.timeZone.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val isTv = AkaaTheme.isTv
    val d = AkaaTheme.dimens
    val c = AkaaTheme.colors
    var showRenew by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val p = playlist ?: return LoadingState()
    val zone = TimeFormat.zone(tz)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = d.gutter, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Header
        Row(
            Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(c.surface, c.purple.copy(alpha = 0.35f))), RoundedCornerShape(18.dp))
                .border(1.dp, c.border, RoundedCornerShape(18.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(44.dp).background(c.purple, RoundedCornerShape(12.dp)).padding(8.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.account_title), style = MaterialTheme.typography.headlineSmall, color = c.text)
                Text(stringResource(R.string.account_subtitle), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            if (refreshing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = c.accent)
            else AkaaButton(stringResource(R.string.action_refresh), vm::refresh, icon = Icons.Default.Refresh, compact = true)
            if (isTv) { Spacer(Modifier.width(16.dp)); AkaaLogo(height = 50.dp) }
        }
        error?.let {
            Text(stringResource(it.messageRes), color = c.red, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.background(c.red.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(10.dp).fillMaxWidth())
        }

        val cards = @Composable { mod: Modifier ->
            StatusCard(p, zone, mod)
        }
        if (isTv) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                UserCard(p, Modifier.weight(1f))
                cards(Modifier.weight(2f))
            }
        } else {
            UserCard(p, Modifier.fillMaxWidth())
            cards(Modifier.fillMaxWidth())
        }

        // Actions (no upgrade / activation code / change account by design).
        val renewFocus = remember { FocusRequester() }
        FlowButtons {
            AkaaButton(stringResource(R.string.action_renew), { showRenew = true }, icon = Icons.Default.Autorenew, variant = ButtonVariant.PURPLE, focusRequester = renewFocus)
            AkaaButton(stringResource(R.string.action_add_new_playlist), { navigator.addPlaylist() }, icon = Icons.AutoMirrored.Filled.PlaylistAdd)
            AkaaButton(stringResource(R.string.action_logout), { confirmLogout = true }, icon = Icons.AutoMirrored.Filled.Logout, variant = ButtonVariant.DANGER)
        }
        LaunchedEffect(Unit) { runCatching { renewFocus.requestFocus() } }

        val details = @Composable { mod: Modifier -> DetailsCard(p, mod) }
        val server = @Composable { mod: Modifier -> ServerCard(p, mod) }
        val devices = @Composable { mod: Modifier -> DevicesCard(p, mod) }
        if (isTv) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { details(Modifier.weight(1f)); devices(Modifier.weight(1f)); server(Modifier.weight(1f)) }
        } else {
            details(Modifier.fillMaxWidth()); devices(Modifier.fillMaxWidth()); server(Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(20.dp))
    }

    if (showRenew) RenewDialog(p) { showRenew = false }
    if (confirmLogout) {
        ConfirmDialog(
            title = stringResource(R.string.action_logout), message = stringResource(R.string.logout_message, p.name),
            confirmLabel = stringResource(R.string.action_logout), destructive = true,
            onConfirm = { vm.logout { others -> if (others) navigator.goToPlaylists(selectMode = true, clearStack = true) else navigator.addPlaylist(firstRun = true) } },
            onDismiss = { confirmLogout = false },
        )
    }
}

@Composable
private fun Card(modifier: Modifier, title: String, icon: ImageVector, content: @Composable () -> Unit) {
    val c = AkaaTheme.colors
    Column(
        modifier.background(c.surface, RoundedCornerShape(18.dp)).border(1.dp, c.border, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = c.accent)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
        }
        content()
    }
}

@Composable
private fun InfoRow(icon: ImageVector?, label: String, value: String, valueColor: Color = AkaaTheme.colors.text) {
    val c = AkaaTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UserCard(p: Playlist, modifier: Modifier) {
    val c = AkaaTheme.colors
    Column(
        modifier.background(c.surface, RoundedCornerShape(18.dp)).border(1.dp, c.purple.copy(alpha = 0.5f), RoundedCornerShape(18.dp)).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(84.dp).background(Brush.linearGradient(listOf(c.purple, c.accent)), CircleShape), contentAlignment = Alignment.Center) {
            Text(p.username.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = Color.White)
        }
        Text(p.username, style = MaterialTheme.typography.titleLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.account_playlist_label, p.name), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        if (p.isTrial) Text(stringResource(R.string.account_trial), style = MaterialTheme.typography.labelLarge, color = c.gold)
        Text(stringResource(R.string.account_active_playlist), style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.background(c.purple, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp))
    }
}

@Composable
private fun StatusCard(p: Playlist, zone: java.time.ZoneId, modifier: Modifier) {
    val c = AkaaTheme.colors
    val status = p.accountStatus
    val statusColor = when (status) { AccountStatus.ACTIVE -> c.success; AccountStatus.UNKNOWN -> c.warning; else -> c.red }
    Card(modifier, stringResource(R.string.account_subscription_status), Icons.Default.EventAvailable) {
        Text(statusLabel(status), color = statusColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.border(2.dp, statusColor, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 4.dp))
        InfoRow(Icons.Default.CalendarMonth, stringResource(R.string.account_activation_date), p.createdAtEpochSec?.let { TimeFormat.date(it, zone) } ?: stringResource(R.string.not_available))
        InfoRow(Icons.Default.EventAvailable, stringResource(R.string.account_expiration_date), p.expiresAtEpochSec?.let { TimeFormat.date(it, zone) } ?: stringResource(R.string.account_unlimited), c.gold)
        val exp = p.expiresAtEpochSec
        val remaining = exp?.let { TimeFormat.remainingDays(it) }
        InfoRow(
            Icons.Default.AccessTime, stringResource(R.string.account_remaining),
            when {
                exp == null -> stringResource(R.string.account_unlimited)
                remaining == null -> stringResource(R.string.status_expired)
                else -> stringResource(R.string.account_days, remaining.toInt())
            },
            if (remaining != null && remaining <= 7) c.red else c.success,
        )
        val created = p.createdAtEpochSec
        if (exp != null && created != null && exp > created) {
            val now = System.currentTimeMillis() / 1000
            val fraction = ((exp - now).toFloat() / (exp - created)).coerceIn(0f, 1f)
            LinearProgressIndicator(progress = { fraction }, color = c.success, trackColor = c.border, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
        }
        p.lastCheckedAt?.let { Text(stringResource(R.string.account_last_checked, TimeFormat.dateTime(it, zone)), style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
    }
}

@Composable
private fun DetailsCard(p: Playlist, modifier: Modifier) {
    Card(modifier, stringResource(R.string.account_subscription_details), Icons.Default.Info) {
        InfoRow(null, stringResource(R.string.account_max_connections), p.maxConnections?.toString() ?: stringResource(R.string.not_available))
        InfoRow(null, stringResource(R.string.account_formats), p.allowedOutputFormats.joinToString(", ").ifBlank { stringResource(R.string.not_available) })
        InfoRow(null, stringResource(R.string.nav_live), String.format(java.util.Locale.US, "%,d", p.liveCount))
        InfoRow(null, stringResource(R.string.nav_movies), String.format(java.util.Locale.US, "%,d", p.movieCount))
        InfoRow(null, stringResource(R.string.nav_series), String.format(java.util.Locale.US, "%,d", p.seriesCount))
        p.serverMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AkaaTheme.colors.textSecondary) }
    }
}

@Composable
private fun DevicesCard(p: Playlist, modifier: Modifier) {
    val c = AkaaTheme.colors
    Card(modifier, stringResource(R.string.settings_devices), Icons.Default.Devices) {
        InfoRow(null, stringResource(R.string.devices_this_device), "${Build.MANUFACTURER} ${Build.MODEL}".trim())
        InfoRow(
            null, stringResource(R.string.devices_active_connections),
            if (p.activeConnections != null && p.maxConnections != null) "${p.activeConnections} / ${p.maxConnections}" else stringResource(R.string.not_available),
        )
        Text(stringResource(R.string.devices_note), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

@Composable
private fun ServerCard(p: Playlist, modifier: Modifier) {
    val c = AkaaTheme.colors
    val connected = p.connectionStatus == ConnectionStatus.CONNECTED
    Card(modifier, stringResource(R.string.account_server_info), Icons.Default.Dns) {
        InfoRow(Icons.Default.Language, stringResource(R.string.account_server), ServerUrl.displayHost(p.serverUrl))
        InfoRow(Icons.Default.Link, stringResource(R.string.account_connection_type), "Xtream Codes")
        InfoRow(Icons.Default.Wifi, stringResource(R.string.account_server_status), stringResource(if (connected) R.string.status_connected else R.string.status_disconnected), if (connected) c.success else c.red)
        p.serverTimezone?.let { InfoRow(Icons.Default.AccessTime, stringResource(R.string.account_server_timezone), it) }
    }
}

@Composable
fun statusLabel(s: AccountStatus): String = stringResource(
    when (s) {
        AccountStatus.ACTIVE -> R.string.status_active
        AccountStatus.EXPIRED -> R.string.status_expired
        AccountStatus.BANNED -> R.string.status_banned
        AccountStatus.DISABLED -> R.string.status_disabled
        AccountStatus.UNKNOWN -> R.string.status_unknown
    },
)

/** "Renew subscription": shows the configured support contacts. No payments are processed in-app. */
@Composable
private fun RenewDialog(p: Playlist, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val whatsapp = BuildConfig.SUPPORT_WHATSAPP.trim()
    val email = BuildConfig.SUPPORT_EMAIL.trim()
    val url = BuildConfig.SUPPORT_URL.trim()
    val first = remember { FocusRequester() }
    fun launch(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    AkaaDialog(onDismiss = onDismiss, title = stringResource(R.string.action_renew), icon = Icons.Default.Autorenew) {
        Text(stringResource(R.string.renew_message), style = MaterialTheme.typography.bodyMedium, color = AkaaTheme.colors.textSecondary)
        p.serverMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.renew_provider_message, it), style = MaterialTheme.typography.bodySmall, color = AkaaTheme.colors.text)
        }
        Spacer(Modifier.height(12.dp))
        var assigned = false
        fun fr(): FocusRequester? = if (!assigned) { assigned = true; first } else null
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (whatsapp.isNotEmpty()) MenuRow(Icons.Default.Phone, "WhatsApp: $whatsapp", fr()) {
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + whatsapp.filter { it.isDigit() })))
            }
            if (email.isNotEmpty()) MenuRow(Icons.Default.Email, email, fr()) {
                launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).putExtra(Intent.EXTRA_SUBJECT, "Akaa Flix Prime – ${p.username}"))
            }
            if (url.isNotEmpty()) MenuRow(Icons.Default.Language, url, fr()) { launch(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            if (whatsapp.isEmpty() && email.isEmpty() && url.isEmpty()) {
                Text(stringResource(R.string.renew_no_contact), style = MaterialTheme.typography.bodyMedium, color = AkaaTheme.colors.text)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AkaaButton(stringResource(R.string.action_close), onDismiss, variant = ButtonVariant.SECONDARY, focusRequester = if (whatsapp.isEmpty() && email.isEmpty() && url.isEmpty()) first else null)
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowButtons(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = 1200.dp)) { content() }
}
