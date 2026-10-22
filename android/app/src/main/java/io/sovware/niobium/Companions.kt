package io.sovware.niobium

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

data class CompanionTask(val id: String, val goal: String, val status: String, val lastTool: String,
                         val toolCount: Int, val canSteer: Boolean, val failed: Boolean = false) {
    companion object {
        fun parse(j: JSONObject): CompanionTask? {
            val id = j.s("subagent_id") ?: return null
            return CompanionTask(id, j.s("goal") ?: "Delegated task", j.s("status") ?: "working",
                j.s("last_tool") ?: "", j.optInt("tool_count"), j.optBoolean("accepting_steer"))
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionsSheet(vm: NiobiumViewModel, bot: BotProfile, onDismiss: () -> Unit) {
    val state = vm.state
    val context = androidx.compose.ui.platform.LocalContext.current
    val member = CastStore.get(context, bot.name)
    var task by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<CompanionTask?>(null) }
    var transcript by remember { mutableStateOf("") }
    var steer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var detailEpoch by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.sessionId) {
        while (true) { vm.loadCompanions(); delay(3000) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nb.Ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(companionDrawable(member.companion)), member.companionName, modifier = Modifier.size(60.dp))
                    Column(Modifier.weight(1f)) {
                        Text(member.companionName + " & friends", color = Nb.Text, style = NbTitle, fontSize = 23.sp)
                        Text("Small creatures. Proper teamwork.", color = Nb.Secondary, fontSize = 12.sp)
                    }
                    TextButton(onClick = onDismiss) { Text("Done", color = Nb.Ion) }
                }
            }
            item { Text("Your Pixel delegates bounded tasks through Hermes. Follow the real work, send a correction, or stop a helper.",
                color = Nb.Secondary, fontSize = 12.sp, lineHeight = 18.sp) }
            item {
                OutlinedTextField(task, { task = it }, label = { Text("A task to split with companions…") },
                    minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    vm.send("Work on this task: " + task.trim() + "\nIf it benefits from delegation, use delegate_task to give your companions bounded subtasks with concrete outputs and the same or narrower permissions. Integrate and verify their results. If delegation is unavailable or unnecessary, explain briefly and do the work yourself.")
                    onDismiss()
                }, enabled = task.isNotBlank() && !state.loadingChat, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Nb.Ion, contentColor = Nb.OnIon)) { Text("Ask your Pixel") }
            }
            item {
                SectionHeader("Companion work", state.companionTasks.size.toString() + " tasks")
                if (state.demo) Text("Demo · live work appears on a connected gateway", color = Nb.Ion, fontSize = 11.sp)
                state.companionError?.let { Text(it, color = Nb.Rose, fontSize = 12.sp) }
                if (state.companionTasks.isEmpty() && state.companionError == null) {
                    Text(if (state.companionsLoading) "Checking the gateway…" else "No delegated tasks in this chat. Helpers appear when your Pixel calls them.",
                        color = Nb.Tertiary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp))
                }
            }
            items(state.companionTasks, key = { it.id }) { child ->
                Row(Modifier.fillMaxWidth().background(Nb.Surface, RoundedCornerShape(14.dp)).clickable {
                    detailEpoch++
                    val epoch = detailEpoch
                    selected = child; transcript = ""; steer = ""; error = null; working = false
                    if (child.failed) transcript = child.lastTool else {
                        working = true
                        scope.launch {
                            try {
                                val text = vm.companionTranscript(child.id)
                                if (detailEpoch == epoch && selected?.id == child.id) transcript = text
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (e: Exception) { if (detailEpoch == epoch && selected?.id == child.id) error = e.message }
                            finally { if (detailEpoch == epoch && selected?.id == child.id) working = false }
                        }
                    }
                }.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(companionDrawable(member.companion)), null, modifier = Modifier.size(38.dp))
                    Column(Modifier.weight(1f)) {
                        Text(child.goal, color = Nb.Text, fontSize = 13.sp, maxLines = 3)
                        Text(child.status + " · " + child.toolCount + " tools", color = if (child.failed) Nb.Rose else Nb.Secondary, style = NbMono, fontSize = 9.sp)
                    }
                    Text("›", color = Nb.Tertiary)
                }
            }
        }
    }
    selected?.let { child ->
        AlertDialog(onDismissRequest = { detailEpoch++; selected = null; working = false }, containerColor = Nb.Ink,
            title = { Text(child.goal, color = Nb.Text, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(child.status, color = Nb.Secondary, style = NbMono, fontSize = 11.sp)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(transcript.ifBlank { "No live transcript is available yet." }, color = Nb.Secondary,
                            style = NbMono, fontSize = 10.sp, maxLines = 16)
                    }
                    if (!child.failed) {
                        OutlinedTextField(steer, { steer = it }, label = { Text("A correction for this helper…") }, maxLines = 3)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !working && child.canSteer && steer.isNotBlank(), onClick = {
                                val epoch = detailEpoch
                                val text = steer
                                working = true
                                scope.launch {
                                    try { vm.controlCompanion(child.id, "steer", text); if (detailEpoch == epoch && selected?.id == child.id) steer = "" }
                                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                    catch (e: Exception) { if (detailEpoch == epoch && selected?.id == child.id) error = e.message }
                                    finally { if (detailEpoch == epoch && selected?.id == child.id) working = false }
                                }
                            }) { Text("Send correction", color = Nb.Ion) }
                            TextButton(enabled = !working, onClick = {
                                val epoch = detailEpoch
                                val text = steer
                                working = true
                                scope.launch {
                                    try { vm.controlCompanion(child.id, "interrupt", "") }
                                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                    catch (e: Exception) { if (detailEpoch == epoch && selected?.id == child.id) error = e.message }
                                    finally { if (detailEpoch == epoch && selected?.id == child.id) working = false }
                                }
                            }) { Text("Stop helper", color = Nb.Rose) }
                        }
                    }
                    error?.let { Text(it, color = Nb.Rose, fontSize = 11.sp) }
                }
            },
            confirmButton = { TextButton(onClick = { detailEpoch++; selected = null; working = false }) { Text("Done", color = Nb.Ion) } })
    }
}
private fun companionDrawable(kind: String) = when (kind) {
    "owl" -> R.drawable.comp_owl; "cat" -> R.drawable.comp_cat; "drone" -> R.drawable.comp_drone
    "slime" -> R.drawable.comp_slime; "bat" -> R.drawable.comp_bat; "bunny" -> R.drawable.comp_bunny
    "moth" -> R.drawable.comp_moth; else -> R.drawable.comp_hawk
}
