package com.akaaflix.prime.presentation.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.akaaflix.prime.R
import com.akaaflix.prime.data.SessionManager
import com.akaaflix.prime.domain.repository.PlaylistRepository
import com.akaaflix.prime.navigation.LocalNavigator
import com.akaaflix.prime.presentation.components.AkaaLogo
import com.akaaflix.prime.presentation.components.AnimatedPlaySymbol
import com.akaaflix.prime.presentation.components.akaaViewModel
import com.akaaflix.prime.presentation.theme.AkaaPalette
import com.akaaflix.prime.presentation.theme.AkaaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class StartDestination { ADD_PLAYLIST, HOME, SELECT_PLAYLIST }

class SplashViewModel(
    private val playlists: PlaylistRepository,
    private val session: SessionManager,
) : ViewModel() {
    private val _destination = MutableStateFlow<StartDestination?>(null)
    val destination: StateFlow<StartDestination?> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val all = playlists.observePlaylists().first()
            val active = all.firstOrNull { it.isActive }
            val dest = when {
                all.isEmpty() -> StartDestination.ADD_PLAYLIST
                active != null -> { session.refreshIfStale(active); StartDestination.HOME }
                all.size == 1 -> {
                    playlists.setActive(all.first().id)
                    session.refreshIfStale(all.first())
                    StartDestination.HOME
                }
                else -> StartDestination.SELECT_PLAYLIST
            }
            val elapsed = System.currentTimeMillis() - started
            delay((MIN_SPLASH_MS - elapsed).coerceAtLeast(0))
            _destination.value = dest
        }
    }

    private companion object { const val MIN_SPLASH_MS = 1_800L }
}

/** Cinematic startup screen: logo, animated red play symbol, blue/red light effects and a loader. */
@Composable
fun SplashScreen() {
    val vm = akaaViewModel { c, _ -> SplashViewModel(c.playlistRepository, c.sessionManager) }
    val destination by vm.destination.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val isTv = AkaaTheme.isTv

    LaunchedEffect(destination) {
        when (destination) {
            StartDestination.ADD_PLAYLIST -> navigator.addPlaylist(firstRun = true)
            StartDestination.HOME -> navigator.goHomeClearingStack()
            StartDestination.SELECT_PLAYLIST -> navigator.goToPlaylists(selectMode = true, clearStack = true)
            null -> Unit
        }
    }

    val logoAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { logoAnim.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }

    Box(Modifier.fillMaxSize().background(AkaaPalette.Background), contentAlignment = Alignment.Center) {
        // Subtle blue and red light effects.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(AkaaPalette.PrimaryBlue.copy(alpha = 0.35f), AkaaPalette.Background.copy(alpha = 0f)), center = Offset(size.width * 0.15f, size.height * 0.85f), radius = size.minDimension * 0.75f),
                radius = size.minDimension * 0.75f, center = Offset(size.width * 0.15f, size.height * 0.85f),
            )
            drawCircle(
                Brush.radialGradient(listOf(AkaaPalette.AccentRed.copy(alpha = 0.35f), AkaaPalette.Background.copy(alpha = 0f)), center = Offset(size.width * 0.85f, size.height * 0.15f), radius = size.minDimension * 0.7f),
                radius = size.minDimension * 0.7f, center = Offset(size.width * 0.85f, size.height * 0.15f),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            AnimatedPlaySymbol(size = if (isTv) 120.dp else 96.dp)
            Spacer(Modifier.height(18.dp))
            AkaaLogo(
                height = if (isTv) 150.dp else 104.dp,
                modifier = Modifier.alpha(logoAnim.value).graphicsLayer {
                    val s = 0.85f + 0.15f * logoAnim.value
                    scaleX = s; scaleY = s
                },
            )
            Spacer(Modifier.height(28.dp))
            LinearProgressIndicator(
                color = AkaaPalette.AccentRed,
                trackColor = AkaaPalette.PrimaryBlue.copy(alpha = 0.25f),
                modifier = Modifier.width(if (isTv) 280.dp else 200.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.splash_loading), style = MaterialTheme.typography.bodyMedium, color = AkaaPalette.SecondaryText)
        }
        Text(
            stringResource(R.string.splash_tagline),
            style = MaterialTheme.typography.labelMedium,
            color = AkaaPalette.SecondaryText.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp).fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
