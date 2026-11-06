package com.akaaflix.prime.presentation.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.akaaflix.prime.R
import com.akaaflix.prime.common.AppError
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.common.TimeFormat
import com.akaaflix.prime.data.SessionManager
import com.akaaflix.prime.domain.model.AccountInfo
import com.akaaflix.prime.domain.repository.PlaylistInput
import com.akaaflix.prime.domain.repository.PlaylistRepository
import com.akaaflix.prime.domain.usecase.PlaylistValidator
import com.akaaflix.prime.navigation.EditPlaylistRoute
import com.akaaflix.prime.navigation.LocalNavigator
import com.akaaflix.prime.presentation.components.AkaaButton
import com.akaaflix.prime.presentation.components.AkaaIconButton
import com.akaaflix.prime.presentation.components.AkaaLogo
import com.akaaflix.prime.presentation.components.AkaaTextField
import com.akaaflix.prime.presentation.components.ButtonVariant
import com.akaaflix.prime.presentation.components.akaaViewModel
import com.akaaflix.prime.presentation.theme.AkaaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditPlaylistState(
    val isEdit: Boolean = false,
    val name: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val invalid: Set<PlaylistValidator.Field> = emptySet(),
    val busy: Boolean = false,
    val error: AppError? = null,
    val testResult: AccountInfo? = null,
    val savedPlaylistId: Long? = null,
    val done: Boolean = false,
)

class EditPlaylistViewModel(
    handle: SavedStateHandle,
    private val repo: PlaylistRepository,
    private val session: SessionManager,
) : ViewModel() {
    private val route = handle.toRoute<EditPlaylistRoute>()
    val firstRun = route.firstRun
    private val _state = MutableStateFlow(EditPlaylistState(isEdit = route.playlistId > 0))
    val state: StateFlow<EditPlaylistState> = _state.asStateFlow()

    init {
        if (route.playlistId > 0) viewModelScope.launch {
            repo.getPlaylist(route.playlistId)?.let { p ->
                // The stored password is never shown; leaving it empty keeps the current one.
                _state.update { it.copy(name = p.name, username = p.username, url = p.serverUrl) }
            }
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v.take(PlaylistValidator.MAX_NAME), invalid = it.invalid - PlaylistValidator.Field.NAME, error = null) }
    fun onUsername(v: String) = _state.update { it.copy(username = v.trim(), invalid = it.invalid - PlaylistValidator.Field.USERNAME, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, invalid = it.invalid - PlaylistValidator.Field.PASSWORD, error = null) }
    fun onUrl(v: String) = _state.update { it.copy(url = v.trim(), invalid = it.invalid - PlaylistValidator.Field.URL, error = null) }

    private fun input() = _state.value.let { PlaylistInput(it.name, it.username, it.password, it.url) }

    private fun validate(): Boolean {
        val invalid = PlaylistValidator.validate(input(), passwordOptional = _state.value.isEdit).toSet()
        _state.update { it.copy(invalid = invalid) }
        return invalid.isEmpty()
    }

    fun test() {
        if (!validate() || _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, testResult = null) }
            val input = input()
            val result = if (_state.value.isEdit && input.password.isEmpty()) {
                repo.refreshAccount(route.playlistId)
            } else repo.testConnection(input)
            _state.update {
                when (result) {
                    is AppResult.Success -> it.copy(busy = false, testResult = result.data)
                    is AppResult.Failure -> it.copy(busy = false, error = result.error)
                }
            }
        }
    }

    fun save() {
        if (!validate() || _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            if (_state.value.isEdit) {
                when (val r = repo.updatePlaylist(route.playlistId, input())) {
                    is AppResult.Success -> {
                        session.syncInBackground(route.playlistId)
                        _state.update { it.copy(busy = false, done = true) }
                    }
                    is AppResult.Failure -> _state.update { it.copy(busy = false, error = r.error) }
                }
            } else {
                when (val r = repo.addPlaylist(input())) {
                    is AppResult.Success -> {
                        session.syncInBackground(r.data)
                        _state.update { it.copy(busy = false, savedPlaylistId = r.data, done = true) }
                    }
                    is AppResult.Failure -> _state.update { it.copy(busy = false, error = r.error) }
                }
            }
        }
    }
}

/** Add / edit an Xtream Codes playlist: exactly Name, Username, Password and URL. */
@Composable
fun EditPlaylistScreen() {
    val vm = akaaViewModel { c, h -> EditPlaylistViewModel(h, c.playlistRepository, c.sessionManager) }
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val c = AkaaTheme.colors
    val isTv = AkaaTheme.isTv
    var showPassword by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }

    LaunchedEffect(state.done) {
        if (state.done) {
            if (state.isEdit) navigator.back() else navigator.goHomeClearingStack()
        }
    }
    LaunchedEffect(Unit) { runCatching { nameFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(c.screenGradient).safeDrawingPadding().imePadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (isTv) 64.dp else 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!vm.firstRun) AkaaIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), navigator::back)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(if (state.isEdit) R.string.playlist_edit_title else R.string.playlist_add_title),
                    style = if (isTv) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall,
                    color = c.text, modifier = Modifier.weight(1f),
                )
                AkaaLogo(height = if (isTv) 52.dp else 36.dp)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.widthIn(max = 1100.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(
                    Modifier.weight(1f)
                        .background(c.surface.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                        .border(1.dp, c.border, RoundedCornerShape(20.dp))
                        .padding(if (isTv) 28.dp else 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (vm.firstRun) {
                        Text(stringResource(R.string.playlist_welcome), style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text(stringResource(R.string.playlist_welcome_hint), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    }
                    AkaaTextField(
                        value = state.name, onValueChange = vm::onName,
                        label = stringResource(R.string.field_name), placeholder = stringResource(R.string.field_name_hint),
                        leadingIcon = Icons.AutoMirrored.Filled.Label,
                        error = if (PlaylistValidator.Field.NAME in state.invalid) stringResource(R.string.error_field_name) else null,
                        modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                    )
                    AkaaTextField(
                        value = state.username, onValueChange = vm::onUsername,
                        label = stringResource(R.string.field_username), placeholder = stringResource(R.string.field_username_hint),
                        leadingIcon = Icons.Default.Person,
                        error = if (PlaylistValidator.Field.USERNAME in state.invalid) stringResource(R.string.error_field_username) else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AkaaTextField(
                        value = state.password, onValueChange = vm::onPassword,
                        label = stringResource(R.string.field_password),
                        placeholder = stringResource(if (state.isEdit) R.string.field_password_keep_hint else R.string.field_password_hint),
                        leadingIcon = Icons.Default.Lock,
                        isPassword = true, passwordVisible = showPassword,
                        keyboardType = KeyboardType.Password,
                        trailing = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, stringResource(R.string.action_toggle_password))
                            }
                        },
                        error = if (PlaylistValidator.Field.PASSWORD in state.invalid) stringResource(R.string.error_field_password) else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AkaaTextField(
                        value = state.url, onValueChange = vm::onUrl,
                        label = stringResource(R.string.field_url), placeholder = stringResource(R.string.field_url_hint),
                        leadingIcon = Icons.Default.Link,
                        keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, onImeAction = vm::save,
                        error = if (PlaylistValidator.Field.URL in state.invalid) stringResource(R.string.error_field_url) else null,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    state.error?.let { err ->
                        StatusBanner(ok = false, text = stringResource(err.messageRes))
                    }
                    state.testResult?.let { info ->
                        val exp = info.expiresAtEpochSec?.let { TimeFormat.date(it, TimeFormat.zone("system")) } ?: stringResource(R.string.account_unlimited)
                        StatusBanner(ok = true, text = stringResource(R.string.playlist_test_ok, info.rawStatus ?: "Active", exp, info.maxConnections ?: 1))
                    }
                    if (state.busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = c.accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.playlist_connecting), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        AkaaButton(
                            stringResource(if (state.isEdit) R.string.action_save else R.string.action_add_playlist), vm::save,
                            icon = if (state.isEdit) Icons.Default.Save else Icons.Default.AddCircle,
                            variant = ButtonVariant.PURPLE, enabled = !state.busy, modifier = Modifier.weight(1f),
                        )
                        AkaaButton(stringResource(R.string.action_test_connection), vm::test, icon = Icons.Default.NetworkCheck, enabled = !state.busy)
                        if (!vm.firstRun) AkaaButton(stringResource(R.string.action_cancel), navigator::back, icon = Icons.Default.Close)
                    }
                }
                if (isTv) XtreamInfoPanel(Modifier.width(320.dp))
            }
            if (!isTv) {
                Spacer(Modifier.height(16.dp))
                XtreamInfoPanel(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StatusBanner(ok: Boolean, text: String) {
    val c = AkaaTheme.colors
    val color = if (ok) c.success else c.red
    Row(
        Modifier.fillMaxWidth().background(color.copy(alpha = 0.14f), RoundedCornerShape(12.dp)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Error, null, tint = color)
        Spacer(Modifier.width(10.dp))
        Text(text, color = c.text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun XtreamInfoPanel(modifier: Modifier) {
    val c = AkaaTheme.colors
    Column(
        modifier.background(c.surface.copy(alpha = 0.7f), RoundedCornerShape(20.dp)).border(1.dp, c.purple.copy(alpha = 0.5f), RoundedCornerShape(20.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.border(2.dp, c.success, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = c.success)
            Spacer(Modifier.width(8.dp))
            Text("Xtream Codes", style = MaterialTheme.typography.titleMedium, color = c.text)
        }
        Text(stringResource(R.string.playlist_xtream_only), style = MaterialTheme.typography.titleSmall, color = c.text)
        Text(stringResource(R.string.playlist_secure_note), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Text(stringResource(R.string.playlist_authorized_note), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}
