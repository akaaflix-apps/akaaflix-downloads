package com.akaaflix.prime.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.akaaflix.prime.R
import com.akaaflix.prime.data.settings.AppSettings
import com.akaaflix.prime.navigation.AccountRoute
import com.akaaflix.prime.navigation.HomeRoute
import com.akaaflix.prime.navigation.LocalNavigator
import com.akaaflix.prime.navigation.NotificationsRoute
import com.akaaflix.prime.navigation.SearchRoute
import com.akaaflix.prime.navigation.SettingsRoute
import com.akaaflix.prime.navigation.TopSection
import com.akaaflix.prime.navigation.isFullScreen
import com.akaaflix.prime.navigation.route
import com.akaaflix.prime.navigation.topSection
import com.akaaflix.prime.navigation.visibleSections
import com.akaaflix.prime.presentation.components.AkaaCard
import com.akaaflix.prime.presentation.components.AkaaIconButton
import com.akaaflix.prime.presentation.components.AkaaLogo
import com.akaaflix.prime.presentation.components.LocalAppContainer
import com.akaaflix.prime.presentation.components.LocalMessenger
import com.akaaflix.prime.presentation.theme.AkaaTheme

fun TopSection.icon(): ImageVector = when (this) {
    TopSection.HOME -> Icons.Default.Home
    TopSection.LIVE -> Icons.Default.LiveTv
    TopSection.MOVIES -> Icons.Default.Movie
    TopSection.SERIES -> Icons.Default.Tv
    TopSection.MY_LIST -> Icons.Default.Bookmark
}

fun TopSection.labelRes(): Int = when (this) {
    TopSection.HOME -> R.string.nav_home
    TopSection.LIVE -> R.string.nav_live
    TopSection.MOVIES -> R.string.nav_movies
    TopSection.SERIES -> R.string.nav_series
    TopSection.MY_LIST -> R.string.nav_my_list
}

/**
 * Android TV (10-foot) shell: landscape layout with a D-pad navigable top bar
 * (logo, sections, search, notifications, settings, account) above the content.
 */
@Composable
fun TvShell(destination: NavDestination?, settings: AppSettings, content: @Composable (Modifier) -> Unit) {
    val c = AkaaTheme.colors
    val showChrome = !destination.isFullScreen()
    Column(Modifier.fillMaxSize().background(c.screenGradient)) {
        if (showChrome) TvTopBar(destination, settings)
        Box(Modifier.weight(1f).fillMaxWidth()) { content(Modifier.fillMaxSize()) }
    }
    // "Press back again to exit" on the TV home screen.
    if (destination?.hasRoute(HomeRoute::class) == true) {
        val messenger = LocalMessenger.current
        val activity = LocalContext.current as? android.app.Activity
        var lastBack by remember { mutableLongStateOf(0L) }
        BackHandler {
            val now = System.currentTimeMillis()
            if (now - lastBack < 2_000) activity?.finish() else {
                lastBack = now
                messenger.show(R.string.msg_press_back_exit)
            }
        }
    }
}

@Composable
private fun TvTopBar(destination: NavDestination?, settings: AppSettings) {
    val c = AkaaTheme.colors
    val navigator = LocalNavigator.current
    val container = LocalAppContainer.current
    val unread by container.notificationRepository.observeUnread().collectAsStateWithLifecycle(0)
    val current = destination.topSection()
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.background.copy(alpha = 0.92f))
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AkaaLogo(height = 40.dp)
        Spacer(Modifier.width(16.dp))
        // Text-only tabs (Prime-style); scrolls horizontally if a translation is too long for the screen.
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            visibleSections(settings).forEach { section ->
                val selected = section == current
                AkaaCard(
                    onClick = { navigator.navigateTopLevel(section.route()) },
                    shape = RoundedCornerShape(50),
                    containerColor = if (selected) c.accent else Color.Transparent,
                    focusScale = 1.08f,
                ) { focused ->
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (selected) {
                            Icon(section.icon(), null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            stringResource(section.labelRes()),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected || focused) Color.White else c.text,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AkaaIconButton(Icons.Default.Search, stringResource(R.string.nav_search), navigator::openSearch, size = 44.dp, selected = destination?.hasRoute(SearchRoute::class) == true)
            AkaaIconButton(
                Icons.Default.Notifications, stringResource(R.string.nav_notifications), navigator::openNotifications,
                size = 44.dp, badge = unread > 0, selected = destination?.hasRoute(NotificationsRoute::class) == true,
            )
            AkaaIconButton(Icons.Default.Settings, stringResource(R.string.nav_settings), { navigator.openSettings() }, size = 44.dp, selected = destination?.hasRoute(SettingsRoute::class) == true)
            AkaaIconButton(Icons.Default.Person, stringResource(R.string.nav_account), navigator::openAccount, size = 44.dp, selected = destination?.hasRoute(AccountRoute::class) == true)
        }
    }
}
