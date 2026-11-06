package com.akaaflix.prime.presentation.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import com.akaaflix.prime.R
import com.akaaflix.prime.common.AgeRating
import com.akaaflix.prime.common.AppResult
import com.akaaflix.prime.di.AppContainer
import com.akaaflix.prime.domain.model.ContentRef
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.domain.model.RuleRestriction
import com.akaaflix.prime.domain.model.RuleScope
import com.akaaflix.prime.presentation.theme.AkaaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
val LocalMessenger = staticCompositionLocalOf<Messenger> { error("Messenger not provided") }
val LocalPinGate = staticCompositionLocalOf<PinGate> { error("PinGate not provided") }
val LocalContentActions = staticCompositionLocalOf<ContentActions> { error("ContentActions not provided") }

/** Creates a ViewModel with access to the [AppContainer] and navigation arguments. */
@Composable
inline fun <reified VM : ViewModel> akaaViewModel(key: String? = null, crossinline create: (AppContainer, SavedStateHandle) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container, createSavedStateHandle()) } })
}

/** Snackbar messages with localized strings. */
@Stable
class Messenger(private val context: Context, private val scope: CoroutineScope) {
    val snackbar = SnackbarHostState()
    fun show(@StringRes res: Int, vararg args: Any) = show(context.getString(res, *args))
    fun show(text: String) {
        scope.launch(Dispatchers.Main.immediate) {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }
}

private data class PinRequest(@StringRes val reason: Int, val action: () -> Unit)

/**
 * Parental-control gate. Content is only revealed after the PIN is verified; nothing is
 * shown or played while the PIN dialog is open.
 */
@Stable
class PinGate(private val container: AppContainer, private val scope: CoroutineScope) {
    private var request by mutableStateOf<PinRequest?>(null)
    var errorRes by mutableStateOf<Int?>(null)
        private set

    /** Runs [action] immediately when no PIN applies, otherwise after successful PIN entry. */
    fun requirePin(@StringRes reason: Int = R.string.pin_required_message, always: Boolean = false, action: () -> Unit) {
        scope.launch(Dispatchers.Main.immediate) {
            val s = container.settingsRepository.current()
            val needs = s.hasPin && (always || s.parentalEnabled) && (always || !container.sessionManager.isPinGraceActive())
            if (!needs) action() else request = PinRequest(reason, action)
        }
    }

    /** Guards opening/playing content that is PIN-locked or above the age limit. */
    fun guard(type: ContentType, contentId: String, categoryId: String?, ageRating: String? = null, action: () -> Unit) {
        scope.launch(Dispatchers.Main.immediate) {
            val s = container.settingsRepository.current()
            val playlistId = container.sessionManager.activePlaylist.value?.id
            if (!s.parentalEnabled || !s.hasPin || playlistId == null || container.sessionManager.isPinGraceActive()) {
                action(); return@launch
            }
            val locked = container.rulesRepository.isLocked(playlistId, type, contentId, categoryId) ||
                AgeRating.isRestricted(ageRating, s.maxAgeRating)
            if (locked) request = PinRequest(R.string.pin_content_locked, action) else action()
        }
    }

    fun submit(pin: String) {
        scope.launch(Dispatchers.Main.immediate) {
            if (container.sessionManager.verifyPin(pin)) {
                val r = request
                request = null
                errorRes = null
                r?.action?.invoke()
            } else {
                errorRes = R.string.pin_wrong
            }
        }
    }

    fun dismiss() { request = null; errorRes = null }

    @Composable
    fun Host() {
        val r = request ?: return
        PinDialog(
            title = stringResource(R.string.pin_enter_title),
            subtitle = stringResource(r.reason),
            error = errorRes?.let { stringResource(it) },
            onSubmit = ::submit,
            onDismiss = ::dismiss,
        )
    }
}

data class ContentMenuRequest(
    val ref: ContentRef,
    val categoryId: String?,
    val categoryName: String?,
    val onOpen: (() -> Unit)?,
)

/** Shared long-press / context actions for any channel, movie or series. */
@Stable
class ContentActions(
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val messenger: Messenger,
    private val pinGate: PinGate,
) {
    var menu by mutableStateOf<ContentMenuRequest?>(null)
        private set
    var groupPickerFor by mutableStateOf<ContentRef?>(null)
        private set

    fun showMenu(ref: ContentRef, categoryId: String? = null, categoryName: String? = null, onOpen: (() -> Unit)? = null) {
        menu = ContentMenuRequest(ref, categoryId, categoryName, onOpen)
    }

    fun dismissMenu() { menu = null }

    fun toggleFavorite(ref: ContentRef) {
        scope.launch {
            val added = container.libraryRepository.toggleFavorite(ref)
            messenger.show(if (added) R.string.msg_added_favorite else R.string.msg_removed_favorite)
        }
    }

    fun openGroupPicker(ref: ContentRef) { groupPickerFor = ref }
    fun dismissGroupPicker() { groupPickerFor = null }

    fun hideItem(ref: ContentRef) {
        scope.launch {
            container.rulesRepository.addRule(ref.playlistId, RuleScope.ITEM, RuleRestriction.HIDDEN, ref.type, ref.contentId, ref.title)
            messenger.show(R.string.msg_hidden_item, ref.title)
        }
    }

    fun hideCategory(playlistId: Long, type: ContentType, categoryId: String, name: String) {
        scope.launch {
            container.rulesRepository.addRule(playlistId, RuleScope.CATEGORY, RuleRestriction.HIDDEN, type, categoryId, name)
            messenger.show(R.string.msg_hidden_category, name)
        }
    }

    fun setLocked(ref: ContentRef, locked: Boolean) {
        pinGate.requirePin(R.string.pin_change_lock, always = true) {
            scope.launch {
                if (locked) {
                    container.rulesRepository.addRule(ref.playlistId, RuleScope.ITEM, RuleRestriction.PIN_LOCKED, ref.type, ref.contentId, ref.title)
                    messenger.show(R.string.msg_locked, ref.title)
                } else {
                    container.rulesRepository.removeRule(ref.playlistId, RuleScope.ITEM, RuleRestriction.PIN_LOCKED, ref.type, ref.contentId)
                    messenger.show(R.string.msg_unlocked, ref.title)
                }
            }
        }
    }

    @Composable
    fun Host() {
        menu?.let { ContentMenuDialog(it) }
        groupPickerFor?.let { GroupPickerDialog(it) }
    }

    @Composable
    private fun ContentMenuDialog(req: ContentMenuRequest) {
        val ref = req.ref
        val isFavorite by remember(ref) { container.libraryRepository.isFavorite(ref.playlistId, ref.type, ref.contentId) }.collectAsState(false)
        val settings by container.settingsRepository.settings.collectAsState(null)
        var locked by remember { mutableStateOf(false) }
        LaunchedEffect(ref) { locked = container.rulesRepository.isLocked(ref.playlistId, ref.type, ref.contentId, null) }
        val first = remember { FocusRequester() }
        AkaaDialog(onDismiss = ::dismissMenu, title = ref.title) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                var firstAssigned = false
                @Composable
                fun option(icon: ImageVector, label: String, action: () -> Unit) {
                    val fr = if (!firstAssigned) { firstAssigned = true; first } else null
                    MenuRow(icon, label, fr) { dismissMenu(); action() }
                }
                req.onOpen?.let { open ->
                    option(Icons.Default.PlayArrow, stringResource(if (ref.type == ContentType.LIVE) R.string.action_play else R.string.action_open), open)
                }
                option(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    stringResource(if (isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite),
                ) { toggleFavorite(ref) }
                option(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.action_add_to_group)) { openGroupPicker(ref) }
                option(Icons.Default.VisibilityOff, stringResource(R.string.action_hide_item)) { hideItem(ref) }
                if (req.categoryId != null) {
                    option(Icons.Default.FolderOff, stringResource(R.string.action_hide_category, req.categoryName ?: req.categoryId)) {
                        hideCategory(ref.playlistId, ref.type, req.categoryId, req.categoryName ?: req.categoryId)
                    }
                }
                if (settings?.parentalEnabled == true && settings?.hasPin == true) {
                    option(if (locked) Icons.Default.LockOpen else Icons.Default.Lock, stringResource(if (locked) R.string.action_unlock else R.string.action_lock)) {
                        setLocked(ref, !locked)
                    }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }

    @Composable
    private fun GroupPickerDialog(ref: ContentRef) {
        val groups by remember(ref.playlistId) { container.libraryRepository.observeGroups(ref.playlistId) }.collectAsState(emptyList())
        val containing by remember(ref) {
            container.libraryRepository.observeGroupsContaining(ref.playlistId, ref.type, ref.contentId)
        }.collectAsState(emptySet())
        var creating by remember { mutableStateOf(false) }
        var createError by remember { mutableStateOf<Int?>(null) }
        val first = remember { FocusRequester() }
        AkaaDialog(onDismiss = ::dismissGroupPicker, title = stringResource(R.string.add_to_group_title), icon = Icons.AutoMirrored.Filled.PlaylistAdd) {
            Text(ref.title, style = MaterialTheme.typography.bodyMedium, color = AkaaTheme.colors.textSecondary)
            Spacer(Modifier.height(10.dp))
            MenuRow(Icons.Default.Add, stringResource(R.string.action_create_group), first) { creating = true }
            Spacer(Modifier.height(6.dp))
            if (groups.isEmpty()) {
                Text(stringResource(R.string.groups_empty_hint), style = MaterialTheme.typography.bodySmall, color = AkaaTheme.colors.textSecondary, modifier = Modifier.padding(8.dp))
            }
            LazyColumn(Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(groups, key = { it.id }) { g ->
                    val inGroup = g.id in containing
                    MenuRow(
                        if (inGroup) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        "${g.name}  (${g.itemCount})",
                        null,
                    ) {
                        scope.launch {
                            if (inGroup) {
                                container.libraryRepository.removeFromGroup(g.id, ref.type, ref.contentId)
                                messenger.show(R.string.msg_removed_from_group, g.name)
                            } else {
                                container.libraryRepository.addToGroup(g.id, ref)
                                messenger.show(R.string.msg_added_to_group, g.name)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AkaaButton(stringResource(R.string.action_done), ::dismissGroupPicker, variant = ButtonVariant.ACCENT)
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        if (creating) {
            TextInputDialog(
                title = stringResource(R.string.action_create_group),
                label = stringResource(R.string.group_name),
                initial = "",
                confirmLabel = stringResource(R.string.action_create),
                error = createError?.let { stringResource(it) },
                onConfirm = { name ->
                    scope.launch {
                        when (val r = container.libraryRepository.createGroup(ref.playlistId, name)) {
                            is AppResult.Success -> {
                                container.libraryRepository.addToGroup(r.data, ref)
                                messenger.show(R.string.msg_added_to_group, name)
                                creating = false
                                createError = null
                            }
                            is AppResult.Failure -> createError = R.string.error_group_name
                        }
                    }
                },
                onDismiss = { creating = false; createError = null },
            )
        }
    }
}

@Composable
fun MenuRow(icon: ImageVector, label: String, focusRequester: FocusRequester?, onClick: () -> Unit) {
    AkaaCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        focusRequester = focusRequester,
        containerColor = AkaaTheme.colors.surfaceHigh,
        focusScale = 1.02f,
    ) { focused ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (focused) AkaaTheme.colors.text else AkaaTheme.colors.accent)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = AkaaTheme.colors.text)
        }
    }
}
