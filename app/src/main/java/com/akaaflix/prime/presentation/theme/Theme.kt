package com.akaaflix.prime.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akaaflix.prime.common.DeviceType
import com.akaaflix.prime.data.settings.AccentColor
import com.akaaflix.prime.data.settings.ThemeMode

/** Brand palette (Akaa Flix Prime design system). */
object AkaaPalette {
    val Background = Color(0xFF030812)
    val BackgroundSecondary = Color(0xFF071426)
    val PrimaryBlue = Color(0xFF0878FF)
    val NeonPurple = Color(0xFF9B19FF)
    val AccentRed = Color(0xFFF01835)
    val TextWhite = Color(0xFFFFFFFF)
    val SecondaryText = Color(0xFFAAB7CA)
    val Gold = Color(0xFFFFC928)
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFFFA726)
}

@Immutable
data class AkaaColors(
    val isDark: Boolean,
    val background: Color,
    val backgroundSecondary: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val accent: Color,
    val purple: Color,
    val red: Color,
    val gold: Color,
    val text: Color,
    val textSecondary: Color,
    val success: Color,
    val warning: Color,
) {
    val screenGradient: Brush get() = Brush.verticalGradient(listOf(backgroundSecondary, background, background))
    val accentGradient: Brush get() = Brush.horizontalGradient(listOf(accent, purple))
}

fun akaaDarkColors(accent: Color) = AkaaColors(
    isDark = true,
    background = AkaaPalette.Background,
    backgroundSecondary = AkaaPalette.BackgroundSecondary,
    surface = Color(0xFF0B1A30),
    surfaceHigh = Color(0xFF12243F),
    border = Color(0x33AAB7CA),
    accent = accent,
    purple = AkaaPalette.NeonPurple,
    red = AkaaPalette.AccentRed,
    gold = AkaaPalette.Gold,
    text = AkaaPalette.TextWhite,
    textSecondary = AkaaPalette.SecondaryText,
    success = AkaaPalette.Success,
    warning = AkaaPalette.Warning,
)

fun akaaLightColors(accent: Color) = AkaaColors(
    isDark = false,
    background = Color(0xFFF3F6FB),
    backgroundSecondary = Color(0xFFE6EDF8),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEFF3FA),
    border = Color(0x1F0A1424),
    accent = accent,
    purple = AkaaPalette.NeonPurple,
    red = AkaaPalette.AccentRed,
    gold = Color(0xFFB98900),
    text = Color(0xFF0A1424),
    textSecondary = Color(0xFF4A5A72),
    success = Color(0xFF15803D),
    warning = Color(0xFFB45309),
)

/** Spacing and sizing that differ between phone and TV (10-foot) layouts. */
@Immutable
data class AkaaDimens(
    val gutter: Dp,
    val sectionSpacing: Dp,
    val itemSpacing: Dp,
    val posterWidth: Dp,
    val landscapeWidth: Dp,
    val channelCardWidth: Dp,
    val cornerRadius: Dp,
    val heroHeight: Dp,
    val focusBorder: Dp,
    val iconSize: Dp,
    val sidebarWidth: Dp,
)

val MobileDimens = AkaaDimens(
    gutter = 16.dp, sectionSpacing = 20.dp, itemSpacing = 10.dp, posterWidth = 112.dp, landscapeWidth = 240.dp,
    channelCardWidth = 120.dp, cornerRadius = 12.dp, heroHeight = 240.dp, focusBorder = 2.dp, iconSize = 24.dp, sidebarWidth = 0.dp,
)

val TvDimens = AkaaDimens(
    gutter = 48.dp, sectionSpacing = 28.dp, itemSpacing = 16.dp, posterWidth = 150.dp, landscapeWidth = 300.dp,
    channelCardWidth = 168.dp, cornerRadius = 14.dp, heroHeight = 340.dp, focusBorder = 3.dp, iconSize = 28.dp, sidebarWidth = 280.dp,
)

val LocalAkaaColors = staticCompositionLocalOf { akaaDarkColors(AkaaPalette.PrimaryBlue) }
val LocalAkaaDimens = staticCompositionLocalOf { MobileDimens }
val LocalDeviceType = staticCompositionLocalOf { DeviceType.MOBILE }

object AkaaTheme {
    val colors: AkaaColors @Composable get() = LocalAkaaColors.current
    val dimens: AkaaDimens @Composable get() = LocalAkaaDimens.current
    val isTv: Boolean @Composable get() = LocalDeviceType.current == DeviceType.TV
}

private fun typography(scale: Float, colors: AkaaColors): Typography {
    fun s(size: Int): TextUnit = (size * scale).sp
    val base = TextStyle(fontFamily = FontFamily.SansSerif, color = colors.text)
    return Typography(
        displayLarge = base.copy(fontSize = s(44), fontWeight = FontWeight.ExtraBold, lineHeight = s(50)),
        displayMedium = base.copy(fontSize = s(36), fontWeight = FontWeight.ExtraBold, lineHeight = s(42)),
        displaySmall = base.copy(fontSize = s(30), fontWeight = FontWeight.Bold, lineHeight = s(36)),
        headlineLarge = base.copy(fontSize = s(28), fontWeight = FontWeight.Bold, lineHeight = s(34)),
        headlineMedium = base.copy(fontSize = s(24), fontWeight = FontWeight.Bold, lineHeight = s(30)),
        headlineSmall = base.copy(fontSize = s(20), fontWeight = FontWeight.Bold, lineHeight = s(26)),
        titleLarge = base.copy(fontSize = s(19), fontWeight = FontWeight.Bold, lineHeight = s(25)),
        titleMedium = base.copy(fontSize = s(16), fontWeight = FontWeight.SemiBold, lineHeight = s(22)),
        titleSmall = base.copy(fontSize = s(14), fontWeight = FontWeight.SemiBold, lineHeight = s(20)),
        bodyLarge = base.copy(fontSize = s(16), fontWeight = FontWeight.Normal, lineHeight = s(24)),
        bodyMedium = base.copy(fontSize = s(14), fontWeight = FontWeight.Normal, lineHeight = s(20)),
        bodySmall = base.copy(fontSize = s(12), fontWeight = FontWeight.Normal, lineHeight = s(16)),
        labelLarge = base.copy(fontSize = s(14), fontWeight = FontWeight.SemiBold, lineHeight = s(20)),
        labelMedium = base.copy(fontSize = s(12), fontWeight = FontWeight.SemiBold, lineHeight = s(16)),
        labelSmall = base.copy(fontSize = s(11), fontWeight = FontWeight.SemiBold, lineHeight = s(14)),
    )
}

@Composable
fun AkaaFlixTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accent: AccentColor = AccentColor.BLUE,
    deviceType: DeviceType = DeviceType.MOBILE,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val accentColor = Color(accent.argb)
    val colors = if (dark) akaaDarkColors(accentColor) else akaaLightColors(accentColor)
    val scheme = if (dark) {
        darkColorScheme(
            primary = accentColor, onPrimary = Color.White, secondary = colors.purple, onSecondary = Color.White,
            tertiary = colors.red, background = colors.background, onBackground = colors.text, surface = colors.surface,
            onSurface = colors.text, surfaceVariant = colors.surfaceHigh, onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surfaceHigh, surfaceContainerHighest = colors.surfaceHigh,
            surfaceContainerLow = colors.backgroundSecondary, outline = colors.border, error = colors.red,
        )
    } else {
        lightColorScheme(
            primary = accentColor, onPrimary = Color.White, secondary = colors.purple, onSecondary = Color.White,
            tertiary = colors.red, background = colors.background, onBackground = colors.text, surface = colors.surface,
            onSurface = colors.text, surfaceVariant = colors.surfaceHigh, onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surfaceHigh, surfaceContainerHighest = colors.surfaceHigh,
            surfaceContainerLow = colors.backgroundSecondary, outline = colors.border, error = colors.red,
        )
    }
    val isTv = deviceType == DeviceType.TV
    CompositionLocalProvider(
        LocalAkaaColors provides colors,
        LocalAkaaDimens provides if (isTv) TvDimens else MobileDimens,
        LocalDeviceType provides deviceType,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography(if (isTv) 1.12f else 1f, colors), content = content)
    }
}
