package io.sovware.niobium

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageSheet(vm: NiobiumViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadUsage() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nb.Ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Usage", color = Nb.Text, style = NbTitle, fontSize = 24.sp)
            val usage = vm.state.usage
            if (usage == null) {
                Text("No usage yet. Start a turn first.", color = Nb.Tertiary, fontSize = 12.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(64.dp)) {
                            drawCircle(Nb.Line, style = Stroke(6.dp.toPx()))
                            drawArc(Nb.Ion, -90f, 360f * usage.contextFraction, false, style = Stroke(6.dp.toPx()))
                        }
                        Text(if (usage.contextLength == null) "—" else "${(usage.contextFraction * 100).toInt()}%", color = Nb.Text, style = NbMono, fontSize = 10.sp)
                    }
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Column {
                        Text("Context", color = Nb.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("${usage.contextTokens} / ${usage.contextLength ?: "—"} tokens", color = Nb.Tertiary, style = NbMono, fontSize = 8.sp)
                        Text(usage.apiCalls.toString() + if (vm.state.engine == AgentEngine.HERMES) " calls · " + usage.compressionCount + " compressions" else " model steps",
                            color = Nb.Tertiary, style = NbMono, fontSize = 8.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("${usage.inputTokens}", "in")
                    Stat("${usage.outputTokens}", "out")
                    Stat("${usage.cacheRead}", "cache")
                    Stat(usage.avgLatency?.let { String.format("%.1fs", it) } ?: "—", "latency")
                    Stat("${usage.activeSubagents}", "agents")
                }
                Text(usage.model.ifBlank { "model" }, color = Nb.Tertiary, style = NbMono, fontSize = 8.sp)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Nb.Text, style = NbMono, fontSize = 10.sp)
        Text(label, color = Nb.Tertiary, fontSize = 8.sp)
    }
}
