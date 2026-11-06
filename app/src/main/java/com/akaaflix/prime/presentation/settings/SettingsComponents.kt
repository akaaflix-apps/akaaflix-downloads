package com.akaaflix.prime.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akaaflix.prime.presentation.components.AkaaButton
import com.akaaflix.prime.presentation.components.AkaaCard
import com.akaaflix.prime.presentation.theme.AkaaTheme

/** A titled group of settings rows. */
@Composable
fun SettingsGroup(title: String?, content: @Composable ColumnScope.() -> Unit) {
    val c = AkaaTheme.colors
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.accent, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 6.dp))
        }
        Column(
            Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(16.dp)).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
private fun RowShell(
    onClick: () -> Unit,
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    titleColor: Color = AkaaTheme.colors.text,
    trailing: @Composable () -> Unit,
) {
    val c = AkaaTheme.colors
    AkaaCard(onClick = { if (enabled) onClick() }, modifier = Modifier.fillMaxWidth(), focusScale = 1.015f, focusRequester = focusRequester, shape = RoundedCornerShape(12.dp)) { focused ->
        Row(
            Modifier.fillMaxWidth().background(if (focused) c.accent.copy(alpha = 0.18f) else Color.Transparent).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = if (enabled) c.textSecondary else c.textSecondary.copy(alpha = 0.4f), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor.copy(alpha = if (enabled) 1f else 0.45f))
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null, icon: ImageVector? = null, enabled: Boolean = true, focusRequester: FocusRequester? = null) {
    val c = AkaaTheme.colors
    RowShell({ onChange(!checked) }, icon, title, subtitle, enabled, focusRequester) {
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = Color.White, uncheckedTrackColor = c.surfaceHigh),
        )
    }
}

@Composable
fun ValueRow(title: String, value: String, onClick: () -> Unit, subtitle: String? = null, icon: ImageVector? = null, enabled: Boolean = true, focusRequester: FocusRequester? = null) {
    val c = AkaaTheme.colors
    RowShell(onClick, icon, title, subtitle, enabled, focusRequester) {
        Text(value, style = MaterialTheme.typography.labelLarge, color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 4.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textSecondary)
    }
}

@Composable
fun ActionRow(title: String, onClick: () -> Unit, subtitle: String? = null, icon: ImageVector? = null, destructive: Boolean = false, enabled: Boolean = true, focusRequester: FocusRequester? = null) {
    val c = AkaaTheme.colors
    RowShell(onClick, icon, title, subtitle, enabled, focusRequester, if (destructive) c.red else c.text) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textSecondary)
    }
}

@Composable
fun StepRow(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, subtitle: String? = null, icon: ImageVector? = null) {
    val c = AkaaTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        AkaaButton("−", onMinus, compact = true)
        Text(value, style = MaterialTheme.typography.titleSmall, color = c.accent, modifier = Modifier.padding(horizontal = 10.dp).width(70.dp), maxLines = 1)
        AkaaButton("+", onPlus, compact = true)
    }
}

/** A generic single-choice request rendered by the settings screen. */
data class ChoiceRequest(val title: String, val options: List<String>, val selectedIndex: Int, val enabled: List<Boolean>? = null, val onSelect: (Int) -> Unit)
