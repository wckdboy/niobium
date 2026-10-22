package io.sovware.niobium

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class NbTab(val title: String, val glyph: String) {
    HOME("Home", "⌂"),
    PIXELS("Pixels", "▦"),
    LOCAL("Engines", "▣"),
    ACTIVITY("Activity", "⌁"),
    SETTINGS("Settings", "≡")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NiobiumApp(vm: NiobiumViewModel, initialTab: String? = null, initialTools: Boolean = false) {
    val state = vm.state
    var tab by rememberSaveable { mutableStateOf(initialTab?.let { name -> NbTab.entries.firstOrNull { it.name.equals(name, true) } } ?: NbTab.HOME) }
    var newBot by remember { mutableStateOf(initialTab in listOf("workshop", "pixel-review")) }
    var newGroup by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(initialTools) }
    var showAI by remember { mutableStateOf(false) }
    var showRooms by remember { mutableStateOf(false) }

    BackHandler(enabled = showTools) { showTools = false }
    BackHandler(enabled = state.activeBot != null && !showTools) { vm.closeBot() }
    BackHandler(enabled = state.activeGroup != null && !showTools) { vm.closeGroup() }

    when {
        state.needsConnection -> OnboardingScreen(state, { url, token, name -> vm.connect(url, token, name) }, { vm.enterDemo() }, vm::useLocal)
        state.activeBot != null -> ChatScreen(vm, state.activeBot!!, onOpenTools = { showTools = true })
        state.activeGroup != null -> GroupChatScreen(vm, state.activeGroup!!)
        else -> {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = { NbTabBar(tab) { tab = it } }
            ) { inner ->
                NbBackground(intensity = 0.7f) {
                    Box(Modifier.fillMaxSize().padding(bottom = inner.calculateBottomPadding())) {
                        when (tab) {
                            NbTab.HOME -> HomeScreen(vm, state, onNewPixel = { newBot = true }, onShowPixels = { tab = NbTab.PIXELS }, onOpenEngines = { tab = NbTab.LOCAL })
                            NbTab.PIXELS -> PixelsScreen(vm, state, onNewPixel = { newBot = true },
                                onBuildAI = { showAI = true }, onOpenRooms = { showRooms = true })
                            NbTab.LOCAL -> LocalScreen(vm, state)
                            NbTab.ACTIVITY -> ActivityScreen(vm, state)
                            NbTab.SETTINGS -> SettingsScreen(vm, state, onOpenTools = { showTools = true })
                        }
                    }
                }
            }
        }
    }

    if (showRooms && state.activeGroup == null) {
        BackHandler { showRooms = false }
        Box(Modifier.fillMaxSize().background(Nb.Void).statusBarsPadding()) {
            RoomsScreen(vm, state, onNewGroup = { newGroup = true }, onClose = { showRooms = false })
        }
    }

    if (showTools && !state.needsConnection) {
        Box(Modifier.fillMaxSize().background(Nb.Void).navigationBarsPadding()) {
            ToolsScreen(vm, state, onClose = { showTools = false })
        }
    }

    if (newBot) { AIBuildSheet(vm, preview = initialTab == "pixel-review") { newBot = false } }
    if (showAI) {
        AIBuildSheet(vm) { showAI = false }
    }
    state.credentialRequests.firstOrNull()?.let { CredentialPrompt(vm, it) }
    if (newGroup) {
        ModalBottomSheet(onDismissRequest = { newGroup = false }, containerColor = Nb.Ink,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            NewGroupSheet(vm, state) { newGroup = false }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.error != null || state.toast != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
        ) {
            if (state.error != null) NbToast(state.error!!, isError = true) else NbToast(state.toast ?: "")
        }
    }
}

@Composable
private fun NbTabBar(selected: NbTab, onSelect: (NbTab) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Nb.Ink.copy(alpha = 0.97f)).navigationBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Nb.Line))
        Row(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 6.dp, start = 8.dp, end = 8.dp)) {
            NbTab.entries.forEach { tab ->
                val active = tab == selected
                Column(
                    Modifier.weight(1f).clickable { onSelect(tab) }.padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(width = 45.dp, height = 29.dp).background(
                            if (active) Nb.Ion.copy(alpha = 0.13f) else Color.Transparent,
                            RoundedCornerShape(11.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        PixelIcon(when (tab) {
                            NbTab.HOME -> PixelGlyph.HOME
                            NbTab.PIXELS -> PixelGlyph.PIXELS
                            NbTab.LOCAL -> PixelGlyph.TERMINAL
                            NbTab.ACTIVITY -> PixelGlyph.ACTIVITY
                            NbTab.SETTINGS -> PixelGlyph.SETTINGS
                        }, if (active) Nb.Ion else Nb.Tertiary, 22.dp)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(tab.title, color = if (active) Nb.Text else Nb.Tertiary, fontSize = 9.5.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}
