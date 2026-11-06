package com.akaaflix.prime.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akaaflix.prime.R
import com.akaaflix.prime.common.AppError
import com.akaaflix.prime.presentation.theme.AkaaTheme

@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String? = null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedPlaySymbol(size = 72.dp)
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(color = AkaaTheme.colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            if (message != null) {
                Spacer(Modifier.height(12.dp))
                Text(message, color = AkaaTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun ErrorState(error: AppError, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    MessageState(
        icon = Icons.Default.CloudOff,
        title = stringResource(R.string.error_title),
        message = stringResource(error.messageRes),
        actionLabel = if (onRetry != null) stringResource(R.string.action_retry) else null,
        actionIcon = Icons.Default.Refresh,
        onAction = onRetry,
        modifier = modifier,
    )
}

@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = 460.dp)) {
            Icon(icon, null, tint = AkaaTheme.colors.textSecondary, modifier = Modifier.size(56.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = AkaaTheme.colors.text, textAlign = TextAlign.Center)
            if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium, color = AkaaTheme.colors.textSecondary, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(6.dp))
                AkaaButton(actionLabel, onAction, icon = actionIcon, variant = ButtonVariant.ACCENT)
            }
        }
    }
}
