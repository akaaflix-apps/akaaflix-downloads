package com.akaaflix.prime.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.akaaflix.prime.presentation.theme.AkaaTheme
import java.util.Locale

/** Remote artwork with a branded placeholder (gradient + title initials) when missing or failing. */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderText: String? = null,
) {
    val c = AkaaTheme.colors
    val placeholder: @Composable () -> Unit = {
        Box(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c.surfaceHigh, c.backgroundSecondary, c.purple.copy(alpha = 0.35f)))),
            contentAlignment = Alignment.Center,
        ) {
            if (!placeholderText.isNullOrBlank()) {
                Text(
                    placeholderText,
                    color = c.text.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
    if (url.isNullOrBlank()) {
        Box(modifier) { placeholder() }
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = { placeholder() },
        error = { placeholder() },
    )
}

@Composable
fun QualityBadge(text: String, modifier: Modifier = Modifier) {
    val c = AkaaTheme.colors
    Text(
        text.uppercase(Locale.ROOT),
        color = Color(0xFF1A1300),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
        modifier = modifier
            .background(c.gold, RoundedCornerShape(5.dp))
            .border(1.dp, Color(0x66000000), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun OutlineBadge(text: String, modifier: Modifier = Modifier, color: Color = AkaaTheme.colors.text) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        modifier = modifier.border(1.5.dp, color.copy(alpha = 0.8f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Color(0xAA000000), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Star, null, tint = AkaaTheme.colors.gold, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(2.dp))
        Text(String.format(Locale.US, "%.1f", rating), color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

/** 2:3 poster card for movies and series. */
@Composable
fun PosterCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = AkaaTheme.dimens.posterWidth,
    subtitle: String? = null,
    quality: String? = null,
    rating: Double? = null,
    isFavorite: Boolean = false,
    locked: Boolean = false,
    showTitle: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
) {
    val c = AkaaTheme.colors
    Column(modifier.then(if (width != null) Modifier.width(width) else Modifier)) {
        AkaaCard(onClick = onClick, onLongClick = onLongClick, onFocus = onFocus, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) { focused ->
            RemoteImage(imageUrl, title, Modifier.fillMaxSize(), placeholderText = title)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color(0x99000000))))
            if (isFavorite) {
                Icon(
                    Icons.Default.Favorite, null, tint = c.red,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0x99000000), CircleShape).padding(4.dp).size(14.dp),
                )
            }
            if (locked) {
                Icon(
                    Icons.Default.Lock, null, tint = c.gold,
                    modifier = Modifier.align(Alignment.Center).background(Color(0xAA000000), CircleShape).padding(10.dp).size(22.dp),
                )
            }
            if (rating != null && rating > 0) RatingBadge(rating, Modifier.align(Alignment.TopEnd).padding(6.dp))
            if (quality != null) QualityBadge(quality, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            if (focused) {
                Icon(
                    Icons.Default.PlayArrow, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).background(c.accent, CircleShape).padding(4.dp).size(16.dp),
                )
            }
        }
        if (showTitle) {
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Landscape card with a progress bar (Continue Watching). */
@Composable
fun ContinueWatchingCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val c = AkaaTheme.colors
    AkaaCard(onClick = onClick, onLongClick = onLongClick, modifier = modifier.width(AkaaTheme.dimens.landscapeWidth), containerColor = c.surface) { focused ->
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteImage(imageUrl, title, Modifier.width(76.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)), placeholderText = title.take(12))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = c.red,
                    trackColor = c.border,
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.PlayArrow, null, tint = if (focused) Color.White else c.text,
                modifier = Modifier.size(36.dp).border(2.dp, if (focused) Color.White else c.textSecondary, CircleShape).padding(6.dp),
            )
        }
    }
}

/** Channel tile: logo, name and (optionally) the current EPG programme. */
@Composable
fun ChannelCard(
    name: String,
    logo: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Long? = null,
    nowPlaying: String? = null,
    selected: Boolean = false,
    isFavorite: Boolean = false,
    locked: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
) {
    val c = AkaaTheme.colors
    AkaaCard(
        onClick = onClick, onLongClick = onLongClick, onFocus = onFocus,
        modifier = modifier,
        containerColor = if (selected) c.accent.copy(alpha = 0.22f) else c.surface,
        focusScale = 1.04f,
    ) { focused ->
        if (selected) Box(Modifier.matchParentSize().border(2.dp, c.red, RoundedCornerShape(AkaaTheme.dimens.cornerRadius)))
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                RemoteImage(logo, name, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit, placeholderText = initials(name))
                if (locked) Icon(Icons.Default.Lock, null, tint = c.gold, modifier = Modifier.align(Alignment.Center).background(Color(0xAA000000), CircleShape).padding(6.dp).size(18.dp))
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (number != null && number > 0) {
                    Text("$number ", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                }
                Text(name, style = MaterialTheme.typography.labelLarge, color = if (focused) Color.White else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (isFavorite) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Star, null, tint = c.gold, modifier = Modifier.size(14.dp))
                }
            }
            if (nowPlaying != null) {
                Text(nowPlaying, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Large gradient tile used for the Live TV / Movies / Series shortcuts. */
@Composable
fun SectionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    AkaaCard(onClick = onClick, modifier = modifier, focusScale = 1.04f) { _ ->
        Box(Modifier.matchParentSize().background(Brush.linearGradient(gradient)))
        Box(Modifier.matchParentSize().border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(AkaaTheme.dimens.cornerRadius)))
        if (!AkaaTheme.isTv) {
            Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(4.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
            }
            return@AkaaCard
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                if (count != null && count > 0) Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
            }
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(if (AkaaTheme.isTv) 52.dp else 40.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val c = AkaaTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(20.dp).background(c.accentGradient, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.text)
        }
        if (actionLabel != null && onAction != null) {
            AkaaCard(onClick = onAction, shape = RoundedCornerShape(50), focusScale = 1.05f) { _ ->
                Row(Modifier.border(1.dp, c.border, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Up to two initials for logo placeholders ("Al News HD" -> "AN"). */
fun initials(name: String): String =
    name.split(' ', '|', '-', '_').filter { it.isNotBlank() && it.first().isLetterOrDigit() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { name.take(2) }
