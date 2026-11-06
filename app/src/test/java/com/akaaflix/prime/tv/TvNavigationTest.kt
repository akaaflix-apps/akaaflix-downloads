package com.akaaflix.prime.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akaaflix.prime.common.DeviceType
import com.akaaflix.prime.presentation.components.AkaaCard
import com.akaaflix.prime.presentation.components.PinDialog
import com.akaaflix.prime.presentation.theme.AkaaFlixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** D-pad / remote-control behaviour of the shared focusable components in the TV layout. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvNavigationTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var inputMode: InputModeManager

    private fun setGrid(onClick: (String) -> Unit = {}, onLongClick: (String) -> Unit = {}) {
        rule.setContent {
            inputMode = LocalInputModeManager.current
            AkaaFlixTheme(deviceType = DeviceType.TV) {
                Column {
                    listOf("r0", "r1").forEach { r ->
                        Row {
                            listOf("c0", "c1", "c2").forEach { c ->
                                val tag = "$r$c"
                                AkaaCard(onClick = { onClick(tag) }, onLongClick = { onLongClick(tag) }, modifier = Modifier.size(120.dp, 80.dp).testTag(tag)) { _ -> Text(tag) }
                            }
                        }
                    }
                }
            }
        }
        rule.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
    }

    @Test fun dpadMovesFocusAcrossGrid() {
        setGrid()
        rule.onNodeWithTag("r0c0").requestFocus()
        rule.onNodeWithTag("r0c0").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithTag("r0c1").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithTag("r0c2").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag("r1c2").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        rule.onNodeWithTag("r1c1").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        rule.onNodeWithTag("r0c1").assertIsFocused()
    }

    @Test fun centerClicks_menuOpensContextActions() {
        var clicked = ""
        var longClicked = ""
        setGrid(onClick = { clicked = it }, onLongClick = { longClicked = it })
        rule.onNodeWithTag("r1c0").requestFocus()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals("r1c0", clicked) }
        rule.onRoot().performKeyInput { pressKey(Key.Menu) }
        rule.runOnIdle { assertEquals("r1c0", longClicked) }
    }

    @Test fun pinDialog_acceptsRemoteNumberKeys() {
        var submitted = ""
        var shown by mutableStateOf(true)
        rule.setContent {
            inputMode = LocalInputModeManager.current
            AkaaFlixTheme(deviceType = DeviceType.TV) {
                if (shown) PinDialog(title = "PIN", subtitle = null, error = null, onSubmit = { submitted = it; shown = false }, onDismiss = { shown = false })
            }
        }
        rule.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        rule.waitForIdle()
        // Number keys on the remote type digits; the focused "1" key + DPAD navigation reaches OK.
        val keys = listOf(Key.Two, Key.Zero, Key.Two, Key.Six)
        rule.onAllDialogsRoot().performKeyInput { keys.forEach { pressKey(it) } }
        // Move to the ✓ key (row 4, column 3) from "1" (row 1, column 1) and press it.
        rule.onAllDialogsRoot().performKeyInput {
            repeat(3) { pressKey(Key.DirectionDown) }
            repeat(2) { pressKey(Key.DirectionRight) }
            pressKey(Key.DirectionCenter)
        }
        rule.runOnIdle { assertEquals("2026", submitted) }
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllDialogsRoot() =
    onAllNodes(androidx.compose.ui.test.isRoot()).let { roots -> roots[roots.fetchSemanticsNodes().size - 1] }
