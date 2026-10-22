package io.sovware.niobium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
    LaunchedEffect(Unit) { vm.loadMCP() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nb.Ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item {
                Text("MCP servers", color = Nb.Text, style = NbDisplay, fontSize = 28.sp)
                Text("${vm.state.mcpServers.size} configured · cached status", color = Nb.Tertiary, fontSize = 10.sp)
            }
            if (vm.state.mcpServers.isEmpty()) {
                item { Text("No MCP servers configured for this profile.", color = Nb.Tertiary, fontSize = 12.sp) }
            } else {
                items(vm.state.mcpServers.size) { i ->
                    val server = vm.state.mcpServers[i]
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nb.FillSoft)
                        .border(1.dp, Nb.Line, RoundedCornerShape(12.dp)).padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.size(7.dp).clip(CircleShape)
                            .background(if (server.connected) Nb.Mint else if (server.enabled) Nb.Amber else Nb.Tertiary)) {}
                        Spacer(Modifier.padding(horizontal = 5.dp))
                        Column(Modifier.weight(1f)) {
                            Text(server.name, color = Nb.Text, style = NbMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("${server.transport} · ${server.tools} tools · ${server.status}", color = Nb.Tertiary, style = NbMono, fontSize = 8.sp)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
