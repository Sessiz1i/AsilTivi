package com.omer.belestiviclone.sharedui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omer.belestiviclone.shared.DebugShellInfo

class DebugShellState {
    var status by mutableStateOf("Ready")
        private set
    var panelOpen by mutableStateOf(false)
        private set

    fun selectFocus(name: String) { status = "$name selected" }
    fun openPanel() { panelOpen = true }
    fun closePanel() { panelOpen = false }
}

@Composable
fun rememberDebugShellState(): DebugShellState = remember { DebugShellState() }

@Composable
fun DebugShell(state: DebugShellState, modifier: Modifier = Modifier) {
    val firstTarget = remember { FocusRequester() }
    val panelOpener = remember { FocusRequester() }
    val panelBack = remember { FocusRequester() }
    var panelWasOpened by remember { mutableStateOf(false) }

    LaunchedEffect(state.panelOpen) {
        if (state.panelOpen) {
            panelWasOpened = true
            panelBack.requestFocus()
        } else if (panelWasOpened) {
            panelOpener.requestFocus()
        } else {
            firstTarget.requestFocus()
        }
    }

    Box(modifier.fillMaxSize().background(Color(0xFF101318)).safeDrawingPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ShellText(DebugShellInfo.title, 26)
            ShellText("Statik ürün verisi yok.")
            ShellText(state.status, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            ShellAction("Focus A", "Debug focus target A", !state.panelOpen,
                Modifier.focusRequester(firstTarget)) { state.selectFocus("Focus A") }
            ShellAction("Focus B", "Debug focus target B", !state.panelOpen) {
                state.selectFocus("Focus B")
            }
            ShellAction("Open test panel", "Open debug test panel", !state.panelOpen,
                Modifier.focusRequester(panelOpener), state::openPanel)
        }
        if (state.panelOpen) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.padding(24.dp).sizeIn(maxWidth = 360.dp).fillMaxWidth()
                        .background(Color(0xFF252B34)).padding(24.dp)
                        .focusGroup().semantics { paneTitle = "Debug test panel" },
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ShellText("Test panel", 22)
                    ShellAction("Back", "Close debug test panel", modifier = Modifier.focusRequester(panelBack),
                        onClick = state::closePanel)
                }
            }
        }
    }
}

@Composable
private fun ShellText(text: String, size: Int = 18, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = TextStyle(color = Color.White, fontSize = size.sp))
}

@Composable
private fun ShellAction(
    label: String,
    accessibilityName: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.fillMaxWidth().sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .onFocusChanged { focused = it.isFocused }
            .background(Color(0xFF252B34))
            .then(if (focused) Modifier.border(2.dp, Color.White) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = accessibilityName }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) { ShellText(label) }
}
