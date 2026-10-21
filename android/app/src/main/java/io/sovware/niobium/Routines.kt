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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesSheet(vm: NiobiumViewModel, bot: BotProfile, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(bot.name) { vm.loadRoutines(bot) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nb.Ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Routines", color = Nb.Text, style = NbTitle, fontSize = 22.sp)
                        Text("Recurring work that lands in ${bot.title}'s chat.", color = Nb.Secondary, fontSize = 12.sp)
                    }
                    Box(Modifier.clip(CircleShape).background(Nb.Ion.copy(alpha = 0.12f))
                        .clickable { creating = true }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("+ New", color = Nb.Ion, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (vm.state.routines.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("◷", color = Nb.Ion, fontSize = 28.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("No routines yet", color = Nb.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("A morning brief, a nightly sweep, a weekly review. Give ${bot.title} a rhythm.",
                            color = Nb.Tertiary, fontSize = 11.5.sp, lineHeight = 17.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                    }
                }
            } else {
                items(vm.state.routines.size) { index ->
                    val routine = vm.state.routines[index]
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Nb.Surface.copy(alpha = 0.7f))
                        .border(1.dp, Nb.Line, RoundedCornerShape(18.dp)).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(routine.name, color = Nb.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.weight(1f))
                            Text(if (routine.enabled) "ON" else "PAUSED", color = if (routine.enabled) Nb.Mint else Nb.Tertiary,
                                style = NbMono, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(routine.prompt, color = Nb.Secondary, fontSize = 12.sp, maxLines = 2)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("↻ ${routine.schedule}", color = Nb.Tertiary, style = NbMono, fontSize = 8.5.sp)
                            Spacer(Modifier.weight(1f))
                            if (routine.nextRun.isNotBlank()) {
                                Text("next · ${routine.nextRun}", color = Nb.Tertiary, style = NbMono, fontSize = 8.sp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Delete", color = Nb.Rose.copy(alpha = 0.8f), fontSize = 10.sp,
                                modifier = Modifier.clickable { vm.deleteRoutine(routine, bot) })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
    if (creating) {
        NewRoutineDialog(bot, onDismiss = { creating = false }) { name, schedule, prompt ->
            vm.createRoutine(name, schedule, prompt, bot) { creating = false }
        }
    }
}

@Composable
private fun NewRoutineDialog(bot: BotProfile, onDismiss: () -> Unit, onCreate: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf("Every day at 08:00") }
    var prompt by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nb.Raised,
        title = { Text("New routine", color = Nb.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                routineField("Name", name) { name = it }
                routineField("Schedule", schedule) { schedule = it }
                routineField("Prompt", prompt) { prompt = it }
            }
        },
        confirmButton = {
            Text("Create", color = Nb.Ion, modifier = Modifier.clickable {
                if (name.isNotBlank() && prompt.isNotBlank()) onCreate(name, schedule, prompt)
            }.padding(8.dp))
        },
        dismissButton = { Text("Cancel", color = Nb.Secondary, modifier = Modifier.clickable { onDismiss() }.padding(8.dp)) }
    )
}

@Composable
private fun routineField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        colors = fieldColors(), modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Nb.Text, unfocusedTextColor = Nb.Text,
    focusedBorderColor = Nb.Ion, unfocusedBorderColor = Nb.LineStrong,
    focusedLabelColor = Nb.Ion, unfocusedLabelColor = Nb.Tertiary,
    focusedContainerColor = Nb.FillSoft, unfocusedContainerColor = Nb.FillSoft
)
