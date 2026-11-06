package com.akaaflix.prime.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.akaaflix.prime.R
import com.akaaflix.prime.domain.model.ContentType
import com.akaaflix.prime.presentation.theme.AkaaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

data class HeroItem(
    val playlistId: Long,
    val type: ContentType,
    val id: Long,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val year: Int?,
    val rating: Double?,
    val genre: String?,
    val quality: String?,
    val isFavorite: Boolean,
    val categoryId: String?,
)

/** Cinematic auto-advancing hero banner with Watch Now / Favorite / Add to Group actions. */
@Composable
fun HeroBanner(
    items: List<HeroItem>,
    onWatch: (HeroItem) -> Unit,
    onOpen: (HeroItem) -> Unit,
    onToggleFavorite: (HeroItem) -> Unit,
    onAddToGroup: (HeroItem) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    if (items.isEmpty()) return
    val c = AkaaTheme.colors
    val isTv = AkaaTheme.isTv
    val pager = rememberPagerState { items.size }
    val scope = rememberCoroutineScope()
    var hasFocus by remember { mutableStateOf(false) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    LaunchedEffect(pager, items.size, hasFocus) {
        while (!hasFocus && items.size > 1) {
            delay(8_000)
            pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(AkaaTheme.dimens.heroHeight)
            .onFocusChanged { hasFocus = it.hasFocus },
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), userScrollEnabled = !isTv) { page ->
            val item = items[page]
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(if (isTv) 18.dp else 16.dp))) {
                RemoteImage(item.imageUrl, item.title, Modifier.fillMaxSize(), placeholderText = null)
                // Readability gradients (start side + bottom).
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            if (rtl) listOf(Color.Transparent, c.background.copy(alpha = 0.55f), c.background.copy(alpha = 0.95f))
                            else listOf(c.background.copy(alpha = 0.95f), c.background.copy(alpha = 0.55f), Color.Transparent),
                        ),
                    ),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to c.background)))
            }
        }

        val item = items[pager.currentPage.coerceIn(0, items.lastIndex)]
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .padding(start = if (isTv) 56.dp else 18.dp, end = 18.dp, top = 16.dp, bottom = 26.dp)
                .widthIn(max = if (isTv) 620.dp else 560.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (label != null) {
                Text(label.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelLarge, color = c.textSecondary, maxLines = 1)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                item.title,
                style = if (isTv) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.quality?.let { QualityBadge(it) }
                item.rating?.let {
                    Icon(Icons.Default.Star, null, tint = c.gold, modifier = Modifier.size(16.dp))
                    Text(String.format(Locale.US, "%.1f", it), style = MaterialTheme.typography.labelLarge, color = c.text)
                }
                val meta = listOfNotNull(item.year?.toString(), item.genre).joinToString(" • ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelLarge, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!item.description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(item.description, style = MaterialTheme.typography.bodyMedium, color = c.text.copy(alpha = 0.88f), maxLines = if (isTv) 3 else 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AkaaButton(stringResource(R.string.action_watch_now), { onWatch(item) }, icon = Icons.Default.PlayArrow, variant = ButtonVariant.PRIMARY, compact = !isTv)
                AkaaButton(
                    stringResource(if (item.isFavorite) R.string.action_remove_favorite_short else R.string.action_add_favorite_short),
                    { onToggleFavorite(item) },
                    icon = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    variant = ButtonVariant.SECONDARY, compact = !isTv,
                )
                if (isTv) {
                    AkaaButton(stringResource(R.string.action_add_to_group), { onAddToGroup(item) }, icon = Icons.Default.Add, variant = ButtonVariant.PURPLE)
                    AkaaButton(stringResource(R.string.action_details), { onOpen(item) }, variant = ButtonVariant.GHOST)
                } else {
                    AkaaIconButton(Icons.Default.Add, stringResource(R.string.action_add_to_group), { onAddToGroup(item) }, size = 38.dp)
                }
            }
        }

        // Page indicator + arrows
        if (items.size > 1) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items.indices.forEach { i ->
                    val w by animateDpAsState(if (i == pager.currentPage) 22.dp else 8.dp, label = "dot")
                    Box(Modifier.height(6.dp).width(w).background(if (i == pager.currentPage) c.accent else c.textSecondary.copy(alpha = 0.5f), CircleShape))
                }
            }
            if (isTv) {
                Row(Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AkaaIconButton(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.action_previous),
                        { scope.launch { pager.animateScrollToPage((pager.currentPage - 1 + items.size) % items.size) } }, size = 40.dp,
                    )
                    AkaaIconButton(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.action_next),
                        { scope.launch { pager.animateScrollToPage((pager.currentPage + 1) % items.size) } }, size = 40.dp,
                    )
                }
            }
        }
    }
}
