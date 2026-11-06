package com.akaaflix.prime.presentation.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akaaflix.prime.R
import com.akaaflix.prime.common.CategoryKind
import com.akaaflix.prime.domain.model.SortOrder
import com.akaaflix.prime.presentation.components.AkaaCard
import com.akaaflix.prime.presentation.theme.AkaaTheme

/** What the browse grid is currently showing. */
sealed interface BrowseSelection {
    data object All : BrowseSelection
    data object Latest : BrowseSelection
    data object MostWatched : BrowseSelection
    data object Favorites : BrowseSelection
    data class Category(val id: String, val name: String) : BrowseSelection
    data class Group(val id: Long, val name: String) : BrowseSelection
    data class Country(val code: String, val categoryIds: List<String>) : BrowseSelection
    data class Kind(val kind: CategoryKind, val categoryIds: List<String>) : BrowseSelection
}

@Composable
fun BrowseSelection.title(): String = when (this) {
    BrowseSelection.All -> stringResource(R.string.filter_all)
    BrowseSelection.Latest -> stringResource(R.string.filter_latest)
    BrowseSelection.MostWatched -> stringResource(R.string.filter_most_watched)
    BrowseSelection.Favorites -> stringResource(R.string.nav_favorites)
    is BrowseSelection.Category -> name
    is BrowseSelection.Group -> name
    is BrowseSelection.Country -> code
    is BrowseSelection.Kind -> kind.label()
}

@Composable
fun CategoryKind.label(): String = stringResource(
    when (this) {
        CategoryKind.ARABIC -> R.string.kind_arabic
        CategoryKind.INTERNATIONAL -> R.string.kind_international
        CategoryKind.MOVIES -> R.string.kind_movies
        CategoryKind.SERIES -> R.string.kind_series
        CategoryKind.KIDS -> R.string.kind_kids
        CategoryKind.DOCUMENTARY -> R.string.kind_documentary
        CategoryKind.NEWS -> R.string.kind_news
        CategoryKind.SPORTS -> R.string.kind_sports
        CategoryKind.ADULT -> R.string.kind_adult
        CategoryKind.OTHER -> R.string.kind_other
    },
)

@Composable
fun SortOrder.label(): String = stringResource(
    when (this) {
        SortOrder.DEFAULT -> R.string.sort_default
        SortOrder.LATEST -> R.string.sort_latest
        SortOrder.NAME_ASC -> R.string.sort_name_asc
        SortOrder.NAME_DESC -> R.string.sort_name_desc
        SortOrder.RATING -> R.string.sort_rating
        SortOrder.YEAR -> R.string.sort_year
    },
)

/** Sidebar / list row with optional icon and count, used for categories and filters (TV & tablet). */
@Composable
fun FilterRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    count: Int? = null,
    locked: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    val c = AkaaTheme.colors
    AkaaCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        focusRequester = focusRequester,
        containerColor = if (selected) c.accent.copy(alpha = 0.28f) else Color.Transparent,
        focusScale = 1.03f,
        shape = RoundedCornerShape(12.dp),
    ) { focused ->
        if (selected) Box(Modifier.matchParentSize().background(c.accentGradient, RoundedCornerShape(12.dp)).padding(1.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = if (selected || focused) Color.White else c.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                label, style = MaterialTheme.typography.titleSmall, color = if (selected) Color.White else c.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (locked) Icon(Icons.Default.Lock, null, tint = c.gold, modifier = Modifier.size(16.dp).padding(end = 2.dp))
            if (count != null) {
                Text(
                    String.format(java.util.Locale.US, "%,d", count),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Color.White else c.textSecondary,
                    modifier = Modifier.background(c.background.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Horizontal chip used on phones. */
@Composable
fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, count: Int? = null) {
    val c = AkaaTheme.colors
    AkaaCard(
        onClick = onClick, onLongClick = onLongClick,
        shape = RoundedCornerShape(50),
        containerColor = if (selected) c.accent else c.surface,
        focusScale = 1.05f,
    ) { _ ->
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else c.text, maxLines = 1)
            if (count != null) Text(String.format(java.util.Locale.US, "%,d", count), style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White.copy(alpha = 0.8f) else c.textSecondary)
        }
    }
}

/** Long-press actions for a category: hide it, or lock / unlock it with the parental PIN. */
@Composable
fun CategoryActionsDialog(
    category: com.akaaflix.prime.domain.model.Category,
    onHide: () -> Unit,
    onToggleLock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = androidx.compose.runtime.remember { FocusRequester() }
    com.akaaflix.prime.presentation.components.AkaaDialog(onDismiss = onDismiss, title = category.name) {
        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            com.akaaflix.prime.presentation.components.MenuRow(
                androidx.compose.material.icons.Icons.Default.FolderOff, stringResource(R.string.action_hide_category, category.name), first,
            ) { onDismiss(); onHide() }
            com.akaaflix.prime.presentation.components.MenuRow(
                if (category.isLocked) androidx.compose.material.icons.Icons.Default.LockOpen else Icons.Default.Lock,
                stringResource(if (category.isLocked) R.string.action_unlock_category else R.string.action_lock_category),
                null,
            ) { onDismiss(); onToggleLock() }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
