package com.akaaflix.prime.presentation.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.akaaflix.prime.R
import com.akaaflix.prime.common.TimeFormat
import com.akaaflix.prime.data.repository.NotificationRepository
import com.akaaflix.prime.domain.model.AppNotification
import com.akaaflix.prime.presentation.components.AkaaButton
import com.akaaflix.prime.presentation.components.AkaaCard
import com.akaaflix.prime.presentation.components.MessageState
import com.akaaflix.prime.presentation.components.akaaViewModel
import com.akaaflix.prime.presentation.theme.AkaaTheme
import com.akaaflix.prime.work.PlaylistRefreshWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationsViewModel(private val repo: NotificationRepository) : ViewModel() {
    val items: StateFlow<List<AppNotification>> = repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun markAllRead() = viewModelScope.launch { repo.markAllRead() }
    fun markRead(id: Long) = viewModelScope.launch { repo.markRead(id) }
    fun clear() = viewModelScope.launch { repo.clear() }
}

/** Local notification center: subscription expiry warnings and new content found by background refresh. */
@Composable
fun NotificationsScreen() {
    val vm = akaaViewModel { c, _ -> NotificationsViewModel(c.notificationRepository) }
    val items by vm.items.collectAsStateWithLifecycle()
    val d = AkaaTheme.dimens
    val c = AkaaTheme.colors
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = d.gutter, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.nav_notifications), style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.weight(1f))
            if (items.isNotEmpty()) {
                AkaaButton(stringResource(R.string.action_mark_all_read), vm::markAllRead, icon = Icons.Default.DoneAll, compact = true)
                AkaaButton(stringResource(R.string.action_clear), vm::clear, icon = Icons.Default.DeleteSweep, compact = true)
            }
        }
        if (items.isEmpty()) {
            MessageState(Icons.Default.NotificationsNone, stringResource(R.string.notifications_empty_title), stringResource(R.string.notifications_empty_message))
        } else {
            LazyColumn(contentPadding = PaddingValues(d.gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { n ->
                    AkaaCard(onClick = { vm.markRead(n.id) }, modifier = Modifier.fillMaxWidth(), containerColor = if (n.isRead) c.surface else c.surfaceHigh, focusScale = 1.02f) { _ ->
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (n.type == PlaylistRefreshWorker.TYPE_EXPIRY) Icons.Default.Event else Icons.Default.NewReleases, null,
                                tint = if (n.type == PlaylistRefreshWorker.TYPE_EXPIRY) c.gold else c.accent, modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(n.title, style = MaterialTheme.typography.titleSmall, color = c.text)
                                Text(n.message, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                                Text(TimeFormat.dateTime(n.createdAt, TimeFormat.zone("system")), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                            }
                            if (!n.isRead) Box(Modifier.size(10.dp).background(c.red, CircleShape))
                        }
                    }
                }
            }
        }
    }
}
