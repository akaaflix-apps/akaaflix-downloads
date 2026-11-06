package com.akaaflix.prime.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.akaaflix.prime.R
import com.akaaflix.prime.presentation.theme.AkaaTheme

/** Branded dialog container (works with touch and D-pad). */
@Composable
fun AkaaDialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable () -> Unit,
) {
    val c = AkaaTheme.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier
                .padding(20.dp)
                .widthIn(min = 280.dp, max = if (AkaaTheme.isTv) 620.dp else 520.dp)
                .background(c.surface, RoundedCornerShape(20.dp))
                .border(1.dp, c.border, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = c.accent, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(title, style = MaterialTheme.typography.titleLarge, color = c.text)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    AkaaDialog(onDismiss = onDismiss, title = title) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = AkaaTheme.colors.textSecondary)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            AkaaButton(stringResource(R.string.action_cancel), onDismiss, variant = ButtonVariant.SECONDARY, focusRequester = focus)
            AkaaButton(confirmLabel, { onConfirm(); onDismiss() }, variant = if (destructive) ButtonVariant.DANGER else ButtonVariant.ACCENT)
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
    maxLength: Int = 40,
) {
    var value by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    AkaaDialog(onDismiss = onDismiss, title = title) {
        AkaaTextField(
            value = value,
            onValueChange = { if (it.length <= maxLength) value = it },
            label = label,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            imeAction = ImeAction.Done,
            onImeAction = { if (value.isNotBlank()) onConfirm(value.trim()) },
            error = error,
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            AkaaButton(stringResource(R.string.action_cancel), onDismiss, variant = ButtonVariant.SECONDARY)
            AkaaButton(confirmLabel, { if (value.isNotBlank()) onConfirm(value.trim()) }, variant = ButtonVariant.ACCENT, enabled = value.isNotBlank())
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Single-choice list dialog used by settings. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    enabled: (T) -> Boolean = { true },
    description: @Composable (T) -> String? = { null },
) {
    val focus = remember { FocusRequester() }
    AkaaDialog(onDismiss = onDismiss, title = title) {
        LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(options) { index, option ->
                val isSelected = option == selected
                val isEnabled = enabled(option)
                AkaaCard(
                    onClick = { if (isEnabled) { onSelect(option); onDismiss() } },
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = if (isSelected || (selected == null && index == 0)) focus else null,
                    containerColor = if (isSelected) AkaaTheme.colors.accent.copy(alpha = 0.18f) else AkaaTheme.colors.surfaceHigh,
                    focusScale = 1.02f,
                ) { _ ->
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, null,
                            tint = if (isSelected) AkaaTheme.colors.accent else AkaaTheme.colors.textSecondary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label(option), style = MaterialTheme.typography.titleSmall, color = AkaaTheme.colors.text.copy(alpha = if (isEnabled) 1f else 0.4f))
                            description(option)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AkaaTheme.colors.textSecondary) }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
fun AkaaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    error: String? = null,
    singleLine: Boolean = true,
) {
    val c = AkaaTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingIcon = leadingIcon?.let { { Icon(it, null) } },
        trailingIcon = trailing,
        singleLine = singleLine,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.border,
            focusedContainerColor = c.surfaceHigh,
            unfocusedContainerColor = c.surface,
            cursorColor = c.accent,
            focusedLabelColor = c.accent,
            unfocusedLabelColor = c.textSecondary,
            focusedTextColor = c.text,
            unfocusedTextColor = c.text,
        ),
        modifier = modifier,
    )
}

/**
 * Parental PIN entry. Uses an on-screen keypad so it is fully usable with a TV remote;
 * hardware number keys and the phone keyboard also work.
 */
@Composable
fun PinDialog(
    title: String,
    subtitle: String?,
    error: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    minLength: Int = 4,
    maxLength: Int = 8,
) {
    var pin by remember { mutableStateOf("") }
    val firstKey = remember { FocusRequester() }
    val c = AkaaTheme.colors
    LaunchedEffect(error) { if (error != null) pin = "" }
    AkaaDialog(
        onDismiss = onDismiss,
        title = title,
        icon = Icons.Default.Lock,
        modifier = Modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
            val digit = when (e.key) {
                Key.Zero, Key.NumPad0 -> '0'; Key.One, Key.NumPad1 -> '1'; Key.Two, Key.NumPad2 -> '2'
                Key.Three, Key.NumPad3 -> '3'; Key.Four, Key.NumPad4 -> '4'; Key.Five, Key.NumPad5 -> '5'
                Key.Six, Key.NumPad6 -> '6'; Key.Seven, Key.NumPad7 -> '7'; Key.Eight, Key.NumPad8 -> '8'
                Key.Nine, Key.NumPad9 -> '9'; else -> null
            }
            when {
                digit != null -> { if (pin.length < maxLength) pin += digit; true }
                e.key == Key.Backspace || e.key == Key.Delete -> { pin = pin.dropLast(1); true }
                else -> false
            }
        },
    ) {
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(maxOf(minLength, pin.length)) { i ->
                Box(
                    Modifier.padding(horizontal = 6.dp).size(16.dp)
                        .background(if (i < pin.length) c.accent else Color.Transparent, CircleShape)
                        .border(2.dp, c.textSecondary, CircleShape),
                )
            }
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = c.red, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(14.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            keys.chunked(3).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { colIndex, k ->
                        AkaaCard(
                            onClick = {
                                when (k) {
                                    "⌫" -> pin = pin.dropLast(1)
                                    "✓" -> if (pin.length >= minLength) onSubmit(pin)
                                    else -> if (pin.length < maxLength) pin += k
                                }
                            },
                            modifier = Modifier.size(width = 76.dp, height = 52.dp),
                            focusRequester = if (rowIndex == 0 && colIndex == 0) firstKey else null,
                            containerColor = if (k == "✓") c.accent else c.surfaceHigh,
                            focusScale = 1.08f,
                        ) { _ ->
                            when (k) {
                                "⌫" -> Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.action_delete), tint = c.text, modifier = Modifier.align(Alignment.Center))
                                "✓" -> Icon(Icons.Default.Check, stringResource(R.string.action_confirm), tint = Color.White, modifier = Modifier.align(Alignment.Center))
                                else -> Text(k, style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.align(Alignment.Center))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AkaaButton(stringResource(R.string.action_cancel), onDismiss, variant = ButtonVariant.GHOST)
        }
    }
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }
}
