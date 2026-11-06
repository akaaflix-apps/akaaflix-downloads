package com.akaaflix.prime.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import com.akaaflix.prime.R
import com.akaaflix.prime.data.settings.AppSettings
import com.akaaflix.prime.navigation.LocalNavigator
import com.akaaflix.prime.navigation.MyListRoute
import com.akaaflix.prime.navigation.isFullScreen
import com.akaaflix.prime.navigation.route
import com.akaaflix.prime.navigation.topSection
import com.akaaflix.prime.navigation.visibleSections
import com.akaaflix.prime.presentation.components.AkaaIconButton
import com.akaaflix.prime.presentation.components.AkaaLogo
import com.akaaflix.prime.presentation.components.LocalAppContainer
import com.akaaflix.prime.presentation.theme.AkaaTheme
import com.akaaflix.prime.tv.icon
import com.akaaflix.prime.tv.labelRes
import kotlinx.coroutines.launch

/**
 * Phone / tablet shell: top bar (menu, logo, search, notifications, account), bottom
 * navigation for the main sections and a navigation drawer for everything else.
 */
@Composable
fun MobileShell(destination: NavDestination?, settings: AppSettings, content: @Composable (Modifier) -> Unit) {
    val c = AkaaTheme.colors
    val navigator = LocalNavigator.current
    val container = LocalAppContainer.current
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val showChrome = !destination.isFullScreen()
    val unread by container.notificationRepository.observeUnread().collectAsStateWithLifecycle(0)
    val active by container.sessionManager.activePlaylist.collectAsStateWithLifecycle()

    fun go(action: () -> Unit) {
        scope.launch { drawer.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = showChrome,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = c.backgroundSecondary) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                    AkaaLogo(height = 48.dp, modifier = Modifier.padding(8.dp))
                    active?.let {
                        Text(it.name, style = MaterialTheme.typography.titleSmall, color = c.textSecondary, modifier = Modifier.padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = c.border)
                    Spacer(Modifier.height(8.dp))
                    val itemColors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = c.accent.copy(alpha = 0.25f), unselectedContainerColor = Color.Transparent,
                        selectedTextColor = c.text, unselectedTextColor = c.text, selectedIconColor = c.accent, unselectedIconColor = c.textSecondary,
                    )
                    visibleSections(settings).forEach { section ->
                        NavigationDrawerItem(
                            label = { Text(stringResource(section.labelRes())) },
                            icon = { Icon(section.icon(), null) },
                            selected = destination.topSection() == section,
                            onClick = { go { navigator.navigateTopLevel(section.route()) } },
                            colors = itemColors,
                        )
                    }
                    HorizontalDivider(color = c.border, modifier = Modifier.padding(vertical = 8.dp))
                    DrawerItem(Icons.Default.Layers, stringResource(R.string.nav_groups)) { go { navigator.navigateTopLevel(MyListRoute(tab = 1)) } }
                    DrawerItem(Icons.Default.History, stringResource(R.string.nav_history)) { go { navigator.navigateTopLevel(MyListRoute(tab = 2)) } }
                    DrawerItem(Icons.AutoMirrored.Filled.PlaylistPlay, stringResource(R.string.nav_playlists)) { go { navigator.goToPlaylists(selectMode = false, clearStack = false) } }
                    DrawerItem(Icons.Default.Person, stringResource(R.string.nav_account)) { go { navigator.openAccount() } }
                    DrawerItem(Icons.Default.Notifications, stringResource(R.string.nav_notifications)) { go { navigator.openNotifications() } }
                    DrawerItem(Icons.Default.Settings, stringResource(R.string.nav_settings)) { go { navigator.openSettings() } }
                    DrawerItem(Icons.Default.Info, stringResource(R.string.settings_about)) { go { navigator.openSettings("ABOUT") } }
                }
            }
        },
    ) {
        Scaffold(
            containerColor = c.background,
            contentWindowInsets = if (showChrome) androidx.compose.material3.ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0),
            topBar = {
                if (showChrome) {
                    Row(
                        Modifier.fillMaxWidth().background(c.background).statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AkaaIconButton(Icons.Default.Menu, stringResource(R.string.nav_menu), { scope.launch { drawer.open() } }, size = 40.dp)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { AkaaLogo(height = 34.dp) }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AkaaIconButton(Icons.Default.Search, stringResource(R.string.nav_search), navigator::openSearch, size = 40.dp)
                            AkaaIconButton(Icons.Default.Notifications, stringResource(R.string.nav_notifications), navigator::openNotifications, size = 40.dp, badge = unread > 0)
                            AkaaIconButton(Icons.Default.Person, stringResource(R.string.nav_account), navigator::openAccount, size = 40.dp)
                        }
                    }
                }
            },
            bottomBar = {
                if (showChrome) {
                    NavigationBar(containerColor = c.backgroundSecondary) {
                        val current = destination.topSection()
                        visibleSections(settings).forEach { section ->
                            NavigationBarItem(
                                selected = current == section,
                                onClick = { navigator.navigateTopLevel(section.route()) },
                                icon = { Icon(section.icon(), null) },
                                label = { Text(stringResource(section.labelRes()), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White, selectedTextColor = c.text, indicatorColor = c.accent,
                                    unselectedIconColor = c.textSecondary, unselectedTextColor = c.textSecondary,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { content(Modifier.fillMaxSize()) }
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, null) },
        selected = false,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Color.Transparent,
            unselectedTextColor = AkaaTheme.colors.text,
            unselectedIconColor = AkaaTheme.colors.textSecondary,
        ),
    )
}
