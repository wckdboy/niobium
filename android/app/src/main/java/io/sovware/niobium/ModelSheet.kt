package io.sovware.niobium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(vm: NiobiumViewModel, bot: BotProfile, onDismiss: () -> Unit) {
    val choices = vm.state.modelChoices
    var showReasoning by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nb.Ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Model", color = Nb.Text, style = NbDisplay, fontSize = 30.sp)
                Text("The next turn runs on this provider and model. The session revision is fenced.",
                    color = Nb.Secondary, fontSize = 11.sp, lineHeight = 16.sp)
                Box {
                    Text("Reasoning: " + (vm.state.sessionReasoning ?: "profile default") + " ▾", color = Nb.Ion, fontSize = 12.sp,
                        modifier = Modifier.clickable(enabled = !vm.state.demo && !vm.state.turnActive) { showReasoning = true }.padding(vertical = 14.dp))
                    DropdownMenu(expanded = showReasoning, onDismissRequest = { showReasoning = false }) {
                        listOf(null, "minimal", "low", "medium", "high", "xhigh", "max", "ultra").forEach { effort ->
                            DropdownMenuItem(text = { Text(effort ?: "Profile default") }, onClick = { showReasoning = false; vm.setReasoning(effort) })
                        }
                    }
                }
            }
            if (choices.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No model catalog", color = Nb.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("This gateway did not return model.options for the session. The profile default stays in use.",
                            color = Nb.Tertiary, fontSize = 11.sp, lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                        Text("Refresh", color = Nb.Ion, fontSize = 12.sp,
                            modifier = Modifier.clickable { vm.loadModels() }.padding(8.dp))
                    }
                }
            } else {
                choices.groupBy { it.providerName }.forEach { (provider, rows) ->
                    item {
                        Text(provider.uppercase(), color = Nb.Tertiary, style = NbLabel, fontSize = 9.sp, letterSpacing = 1.2.sp)
                    }
                    items(rows.size) { index ->
                        val choice = rows[index]
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (choice.current) Nb.Ion.copy(alpha = 0.10f) else Nb.FillSoft)
                                .border(1.dp, if (choice.current) Nb.Ion.copy(alpha = 0.35f) else Nb.Line, RoundedCornerShape(12.dp))
                                .clickable { vm.switchModel(choice); onDismiss() }.padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(choice.label, color = Nb.Text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                Text(choice.model, color = Nb.Tertiary, style = NbMono, fontSize = 8.sp, maxLines = 1)
                            }
                            if (choice.current) Text("✓", color = Nb.Ion, fontSize = 15.sp)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}
