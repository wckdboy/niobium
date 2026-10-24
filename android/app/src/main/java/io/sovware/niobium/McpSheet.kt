package io.sovware.niobium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpSheet(vm: NiobiumViewModel, onDismiss: () -> Unit) {
    val owner = vm.chatContext
    val state = vm.state
    LaunchedEffect(vm, owner) { vm.loadMCP() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nb.Ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MCP servers", color = Nb.Text, style = NbTitle, fontSize = 26.sp)
                        Text(state.activeBot?.title ?: "Gateway", color = Nb.Secondary, fontSize = 12.sp)
                    }
                    NbPill("${state.mcpServers.size}", Nb.Violet)
                }
            }
            item { PixelButton("Refresh", PixelGlyph.LINK, secondary = true, enabled = !state.mcpLoad.loading && state.connected) { vm.loadMCP() } }
            item { InventoryFeedback(state.mcpLoad, state.mcpServers.isEmpty(), "No MCP servers configured.", vm::loadMCP) }
            items(state.mcpServers, key = { it.name }) { server ->
                Row(Modifier.fillMaxWidth().clip(PixelButtonShape).background(Nb.FillSoft)
                    .border(1.dp, Nb.Line, PixelButtonShape).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon(PixelGlyph.LINK, if (server.connected) Nb.Mint else if (server.enabled) Nb.Amber else Nb.Tertiary, 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(server.name, color = Nb.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("${server.transport} · ${server.tools} tools · ${server.status}", color = Nb.Secondary, fontSize = 11.sp)
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}
