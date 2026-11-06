package com.akaaflix.prime.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akaaflix.prime.R
import com.akaaflix.prime.presentation.theme.AkaaPalette

/** The supplied Akaa Flix Prime logo. Height-driven; width follows the artwork's aspect ratio (never stretched). */
@Composable
fun AkaaLogo(modifier: Modifier = Modifier, height: Dp = 40.dp) {
    Image(
        painter = painterResource(R.drawable.akaa_logo),
        contentDescription = stringResource(R.string.app_name),
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height),
    )
}

/** Animated red play symbol with a pulsing glow, used on the splash screen and loaders. */
@Composable
fun AnimatedPlaySymbol(modifier: Modifier = Modifier, size: Dp = 96.dp, animate: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "play")
    val pulse by transition.animateFloat(
        initialValue = 0.92f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    val sweep by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "sweep",
    )
    val s = if (animate) pulse else 1f
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        // Glow
        drawCircle(
            brush = Brush.radialGradient(listOf(AkaaPalette.AccentRed.copy(alpha = 0.45f), Color.Transparent), center = center, radius = w * 0.62f),
            radius = w * 0.62f,
        )
        // Rotating light arc
        if (animate) {
            rotate(sweep) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, AkaaPalette.PrimaryBlue.copy(alpha = 0.8f), Color.Transparent)),
                    startAngle = 0f, sweepAngle = 120f, useCenter = false,
                    style = Stroke(width = w * 0.025f),
                    topLeft = Offset(w * 0.06f, h * 0.06f),
                    size = androidx.compose.ui.geometry.Size(w * 0.88f, h * 0.88f),
                )
            }
        }
        scale(s) {
            val path = Path().apply {
                moveTo(w * 0.32f, h * 0.22f)
                lineTo(w * 0.80f, h * 0.50f)
                lineTo(w * 0.32f, h * 0.78f)
                close()
            }
            drawPath(path, Brush.linearGradient(listOf(Color(0xFFFF4D63), AkaaPalette.AccentRed, Color(0xFF9E0A20)), start = Offset(w * 0.3f, h * 0.2f), end = Offset(w * 0.8f, h * 0.8f)))
            drawPath(path, Color.White.copy(alpha = 0.18f), style = Stroke(width = w * 0.02f, join = StrokeJoin.Round))
            val inner = Path().apply {
                moveTo(w * 0.42f, h * 0.38f)
                lineTo(w * 0.64f, h * 0.50f)
                lineTo(w * 0.42f, h * 0.62f)
                close()
            }
            drawPath(inner, Color(0xFF2A0008).copy(alpha = 0.85f))
        }
    }
}
