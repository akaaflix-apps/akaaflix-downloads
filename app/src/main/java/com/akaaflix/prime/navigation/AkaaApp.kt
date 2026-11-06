package com.akaaflix.prime.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.akaaflix.prime.common.DeviceType
import com.akaaflix.prime.data.settings.AppSettings
import com.akaaflix.prime.data.settings.InterfaceMode
import com.akaaflix.prime.di.AppContainer
import com.akaaflix.prime.mobile.MobileShell
import com.akaaflix.prime.player.PlayerScreen
import com.akaaflix.prime.presentation.account.AccountScreen
import com.akaaflix.prime.presentation.catalog.CatalogScreen
import com.akaaflix.prime.presentation.components.ContentActions
import com.akaaflix.prime.presentation.components.LocalAppContainer
import com.akaaflix.prime.presentation.components.LocalContentActions
import com.akaaflix.prime.presentation.components.LocalMessenger
import com.akaaflix.prime.presentation.components.LocalPinGate
import com.akaaflix.prime.presentation.components.Messenger
import com.akaaflix.prime.presentation.components.PinGate
import com.akaaflix.prime.presentation.details.MovieDetailsScreen
import com.akaaflix.prime.presentation.details.SeriesDetailsScreen
import com.akaaflix.prime.presentation.home.HomeScreen
import com.akaaflix.prime.presentation.library.GroupScreen
import com.akaaflix.prime.presentation.library.MyListScreen
import com.akaaflix.prime.presentation.livetv.LiveTvScreen
import com.akaaflix.prime.presentation.notifications.NotificationsScreen
import com.akaaflix.prime.presentation.playlist.EditPlaylistScreen
import com.akaaflix.prime.presentation.playlist.PlaylistsScreen
import com.akaaflix.prime.presentation.search.SearchScreen
import com.akaaflix.prime.presentation.settings.SettingsScreen
import com.akaaflix.prime.presentation.splash.SplashScreen
import com.akaaflix.prime.presentation.theme.AkaaFlixTheme
import com.akaaflix.prime.presentation.theme.AkaaTheme
import com.akaaflix.prime.tv.TvShell
import com.akaaflix.prime.domain.model.ContentType

/** Top-level sections shown in the TV top bar and mobile bottom bar. */
enum class TopSection { HOME, LIVE, MOVIES, SERIES, MY_LIST }

fun NavDestination?.topSection(): TopSection? = when {
    this == null -> null
    hasRoute(HomeRoute::class) -> TopSection.HOME
    hasRoute(LiveTvRoute::class) -> TopSection.LIVE
    hasRoute(MoviesRoute::class) -> TopSection.MOVIES
    hasRoute(SeriesRoute::class) -> TopSection.SERIES
    hasRoute(MyListRoute::class) || hasRoute(GroupRoute::class) -> TopSection.MY_LIST
    else -> null
}

/** Destinations drawn edge-to-edge without the app chrome. */
fun NavDestination?.isFullScreen(): Boolean = this == null ||
    hasRoute(SplashRoute::class) || hasRoute(PlayerRoute::class) || hasRoute(EditPlaylistRoute::class) ||
    hasRoute(PlaylistsRoute::class) || hasRoute(MovieDetailsRoute::class) || hasRoute(SeriesDetailsRoute::class)

fun visibleSections(s: AppSettings): List<TopSection> = TopSection.entries.filter {
    when (it) {
        TopSection.LIVE -> !s.hideLiveSection
        TopSection.MOVIES -> !s.hideMoviesSection
        TopSection.SERIES -> !s.hideSeriesSection
        else -> true
    }
}

fun TopSection.route(): Any = when (this) {
    TopSection.HOME -> HomeRoute
    TopSection.LIVE -> LiveTvRoute()
    TopSection.MOVIES -> MoviesRoute()
    TopSection.SERIES -> SeriesRoute()
    TopSection.MY_LIST -> MyListRoute()
}

@Composable
fun AkaaApp(container: AppContainer, detectedDevice: DeviceType) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = settings ?: AppSettings()
    val deviceType = when (s.interfaceMode) {
        InterfaceMode.AUTO -> detectedDevice
        InterfaceMode.MOBILE -> DeviceType.MOBILE
        InterfaceMode.TV -> DeviceType.TV
    }
    AkaaFlixTheme(themeMode = s.theme, accent = s.accent, deviceType = deviceType) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val navController = rememberNavController()
        val messenger = remember { Messenger(context, scope) }
        val pinGate = remember { PinGate(container, scope) }
        val actions = remember { ContentActions(container, scope, messenger, pinGate) }
        val navigator = remember(navController) { AppNavigator(navController, container, scope, pinGate, messenger) }

        CompositionLocalProvider(
            LocalAppContainer provides container,
            LocalMessenger provides messenger,
            LocalPinGate provides pinGate,
            LocalContentActions provides actions,
            LocalNavigator provides navigator,
        ) {
            val backStack by navController.currentBackStackEntryAsState()
            val destination = backStack?.destination
            Box(Modifier.fillMaxSize().background(AkaaTheme.colors.background)) {
                val host: @Composable (Modifier) -> Unit = { modifier -> AppNavHost(navigator, modifier) }
                if (deviceType == DeviceType.TV) {
                    TvShell(destination = destination, settings = s, content = host)
                } else {
                    MobileShell(destination = destination, settings = s, content = host)
                }
                pinGate.Host()
                actions.Host()
                SnackbarHost(
                    messenger.snackbar,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (deviceType == DeviceType.TV) 24.dp else 88.dp),
                ) { data ->
                    Snackbar(containerColor = AkaaTheme.colors.surfaceHigh, contentColor = AkaaTheme.colors.text) { Text(data.visuals.message) }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(navigator: AppNavigator, modifier: Modifier) {
    NavHost(navController = navigator.nav, startDestination = SplashRoute, modifier = modifier) {
        composable<SplashRoute> { SplashScreen() }
        composable<PlaylistsRoute> { PlaylistsScreen() }
        composable<EditPlaylistRoute> { EditPlaylistScreen() }
        composable<HomeRoute> { HomeScreen() }
        composable<LiveTvRoute> { LiveTvScreen() }
        composable<MoviesRoute> { CatalogScreen(ContentType.MOVIE) }
        composable<SeriesRoute> { CatalogScreen(ContentType.SERIES) }
        composable<MyListRoute> { MyListScreen() }
        composable<GroupRoute> { GroupScreen() }
        composable<MovieDetailsRoute> { MovieDetailsScreen() }
        composable<SeriesDetailsRoute> { SeriesDetailsScreen() }
        composable<PlayerRoute> { PlayerScreen() }
        composable<SearchRoute> { SearchScreen() }
        composable<AccountRoute> { AccountScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<NotificationsRoute> { NotificationsScreen() }
    }
}
