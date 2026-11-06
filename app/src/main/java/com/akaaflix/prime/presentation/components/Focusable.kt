package com.akaaflix.prime.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.akaaflix.prime.presentation.theme.AkaaTheme

/** Applies [block] only when [condition] is true. */
inline fun Modifier.thenIf(condition: Boolean, block: Modifier.() -> Modifier): Modifier = if (condition) block() else this

/**
 * Base clickable surface used everywhere. On Android TV it is D-pad focusable, scales up and
 * shows a clear white focus ring with an accent glow; the remote's MENU key triggers
 * [onLongClick]. On touch devices it behaves like a normal clickable card.
 */
@Composable
fun AkaaCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(AkaaTheme.dimens.cornerRadius),
    focusScale: Float = 1.06f,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocus: (() -> Unit)? = null,
    containerColor: Color = Color.Transparent,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) focusScale else 1f, label = "focusScale")
    val colors = AkaaTheme.colors
    val ring = AkaaTheme.dimens.focusBorder
    LaunchedEffect(focused) { if (focused) onFocus?.invoke() }
    Box(
        modifier
            .thenIf(focusRequester != null) { focusRequester(focusRequester!!) }
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .thenIf(focused) { shadow(18.dp, shape, ambientColor = colors.accent, spotColor = colors.accent) }
            .clip(shape)
            .background(containerColor)
            .thenIf(focused) { border(ring, if (colors.isDark) Color.White else colors.accent, shape) }
            .onPreviewKeyEvent { e ->
                if (onLongClick != null && e.type == KeyEventType.KeyUp && e.key == Key.Menu) {
                    onLongClick(); true
                } else false
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    ) { content(focused) }
}

enum class ButtonVariant { PRIMARY, ACCENT, PURPLE, SECONDARY, DANGER, GHOST }

@Composable
fun AkaaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    variant: ButtonVariant = ButtonVariant.SECONDARY,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    compact: Boolean = false,
) {
    val c = AkaaTheme.colors
    val shape = RoundedCornerShape(if (compact) 10.dp else 12.dp)
    AkaaCard(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = if (compact) 38.dp else if (AkaaTheme.isTv) 50.dp else 46.dp),
        shape = shape,
        focusScale = 1.05f,
        enabled = enabled,
        focusRequester = focusRequester,
    ) { focused ->
        val (bg, fg) = when (variant) {
            ButtonVariant.PRIMARY -> Brush.linearGradient(listOf(Color.White, Color(0xFFE8EEF8))) to Color(0xFF071426)
            ButtonVariant.ACCENT -> Brush.horizontalGradient(listOf(c.accent, c.accent.copy(alpha = 0.85f))) to Color.White
            ButtonVariant.PURPLE -> Brush.horizontalGradient(listOf(c.purple, Color(0xFF6A11CB))) to Color.White
            ButtonVariant.DANGER -> Brush.horizontalGradient(listOf(c.red, Color(0xFFB00F2A))) to Color.White
            ButtonVariant.SECONDARY -> Brush.linearGradient(listOf(c.surfaceHigh, c.surface)) to c.text
            ButtonVariant.GHOST -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)) to c.text
        }
        val alpha = if (enabled) 1f else 0.45f
        Box(
            Modifier
                .matchParentSize()
                .background(bg, shape)
                .thenIf(variant == ButtonVariant.SECONDARY || variant == ButtonVariant.GHOST) { border(1.dp, c.border, shape) }
                .thenIf(focused && variant == ButtonVariant.SECONDARY) { background(c.accent.copy(alpha = 0.35f), shape) },
        )
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg.copy(alpha = alpha), modifier = Modifier.size(if (compact) 18.dp else 22.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                color = fg.copy(alpha = alpha),
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Round icon button that is focusable on TV. */
@Composable
fun AkaaIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = if (AkaaTheme.isTv) 48.dp else 42.dp,
    tint: Color = AkaaTheme.colors.text,
    selected: Boolean = false,
    badge: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val c = AkaaTheme.colors
    AkaaCard(onClick = onClick, modifier = modifier.size(size), shape = CircleShape, focusScale = 1.12f, focusRequester = focusRequester) { focused ->
        Box(
            Modifier.matchParentSize().background(
                when {
                    selected -> c.accent
                    focused -> c.accent.copy(alpha = 0.4f)
                    else -> c.surface.copy(alpha = 0.6f)
                },
                CircleShape,
            ),
        )
        Icon(icon, contentDescription, tint = if (selected) Color.White else tint, modifier = Modifier.align(Alignment.Center).size(size * 0.5f))
        if (badge) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = size * 0.18f, end = size * 0.2f).size(9.dp).background(c.red, CircleShape))
        }
    }
}
