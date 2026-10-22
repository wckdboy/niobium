package io.sovware.niobium

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIBuildSheet(vm: NiobiumViewModel, preview: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<PixelDraft?>(null) }
    var designJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showSoul by remember { mutableStateOf(false) }
    LaunchedEffect(preview) {
        if (preview && vm.state.demo) draft = PixelBuilder.draft(context, null, "A sharp-tongued security witch for VPS logs, with a bat")
    }
    DisposableEffect(Unit) { onDispose { designJob?.cancel() } }
    val ideas = listOf(
        "A sharp-tongued night-shift security witch for my VPS logs, with a tiny bat",
        "A mischievous cyber creative director who makes images and moodboards",
        "A calm release engineer with a wonky drone; asks before deploying",
        "A scholarly forest mage with a pocket slime who reads papers and keeps notes"
    )
    ModalBottomSheet(onDismissRequest = { if (!working || draft == null) { designJob?.cancel(); onDismiss() } }, containerColor = Nb.Ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (draft == null) "Pixel workshop" else "Review your Pixel",
                        color = Nb.Text, style = NbTitle, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    TextButton(enabled = !working || draft == null, onClick = { if (draft != null) { draft = null; error = null } else { designJob?.cancel(); onDismiss() } }) {
                        Text(if (draft == null) "Cancel" else "Back", color = Nb.Secondary)
                    }
                }
            }
            val current = draft
            if (current == null) {
                item {
                    Text("Meet your next Pixel.", color = Nb.Text, style = NbDisplay, fontSize = 30.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Give them a purpose. The bot-build skill designs their voice, adult character, companion, skills and access.",
                        color = Nb.Secondary, fontSize = 13.sp, lineHeight = 19.sp)
                }
                item {
                    OutlinedTextField(value = prompt, onValueChange = { prompt = it }, enabled = !working,
                        label = { Text("Describe your Pixel") }, minLines = 4, maxLines = 8,
                        colors = builderFieldColors(), modifier = Modifier.fillMaxWidth())
                }
                ideas.forEach { idea ->
                    item {
                        Text(idea, color = Nb.Secondary, fontSize = 12.sp, lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth().background(Nb.FillSoft, RoundedCornerShape(12.dp))
                                .clickable(enabled = !working) { prompt = idea }.padding(12.dp))
                    }
                }
                item {
                    Button(onClick = {
                        working = true; error = null
                        designJob = vm.draftPixel(context, prompt, onDraft = { draft = it; working = false },
                            onError = { error = it; working = false })
                    }, enabled = !working && prompt.isNotBlank(), modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Nb.Ion, contentColor = Nb.OnIon)) {
                        Text(if (working) "Designing your Pixel…" else "Design a Pixel", Modifier.padding(6.dp))
                    }
                    Text("Review before creation. If the model is unavailable, the preview is labelled as a local template.",
                        color = Nb.Tertiary, fontSize = 11.sp, lineHeight = 16.sp)
                }
            } else {
                val spec = current.spec
                val base = Cast.all.first { it.id == spec.character.archetype }
                val member = base.copy(companion = spec.companion.kind, companionName = spec.companion.name, recipe = spec.character.recipe)
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CastPortrait(member, 106.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            NbPill(if (current.usedModel) "AI designed" else "Local template", Nb.Ion)
                            Text(spec.title, color = Nb.Text, style = NbTitle, fontSize = 23.sp)
                            Text(spec.role, color = Nb.Secondary, fontSize = 12.sp)
                            Text(spec.character.age.toString() + " · " + spec.personality.joinToString(" · "),
                                color = Nb.Tertiary, fontSize = 10.sp)
                        }
                    }
                }
                item {
                    OutlinedTextField(value = spec.name, onValueChange = { draft = current.copy(spec = spec.copy(name = it)) },
                        label = { Text("Profile name") }, singleLine = true, enabled = !working,
                        colors = builderFieldColors(), modifier = Modifier.fillMaxWidth())
                }
                item {
                    Text(spec.companion.name + " · " + spec.companion.kind, color = Nb.Text, style = NbTitle, fontSize = 15.sp)
                    Text(spec.companion.trait, color = Nb.Secondary, fontSize = 12.sp)
                }
                item {
                    Column(Modifier.background(Nb.Surface, RoundedCornerShape(16.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader("Access", "review before creation")
                        AccessToggle("Network tools", spec.permissions.network, !working) {
                            draft = current.copy(spec = spec.copy(permissions = spec.permissions.copy(network = it)))
                        }
                        AccessToggle("Shell & code execution", spec.permissions.shell, !working) {
                            draft = current.copy(spec = spec.copy(permissions = spec.permissions.copy(shell = it)))
                        }
                        Text("Files", color = Nb.Secondary, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("none", "workspace", "unrestricted").forEach { value ->
                                FilterChip(selected = spec.permissions.filesystem == value, enabled = !working,
                                    onClick = { draft = current.copy(spec = spec.copy(permissions = spec.permissions.copy(filesystem = value))) },
                                    label = { Text(value, fontSize = 9.sp) })
                            }
                        }
                        Text("Tool choices are applied to the selected engine. Workspace scopes are operating instructions; the engine owns sandbox enforcement. Your Pixel uses the model credentials configured for that engine.",
                            color = Nb.Tertiary, fontSize = 10.sp, lineHeight = 15.sp)
                        Text("Tools: " + current.enabledToolsets.joinToString().ifBlank { "none" }, color = Nb.Secondary, style = NbMono, fontSize = 11.sp)
                        Text("Skills: " + spec.skills.joinToString().ifBlank { "none" }, color = Nb.Secondary, fontSize = 12.sp)
                        Text("Requested connections: " + spec.connectors.joinToString().ifBlank { "none" }, color = Nb.Secondary, fontSize = 12.sp)
                    }
                }
                item {
                    TextButton(onClick = { showSoul = !showSoul }) { Text(if (showSoul) "Hide briefing ↑" else "Operator briefing ↓", color = Nb.Ion) }
                    if (showSoul) Text(spec.soul, color = Nb.Secondary, fontSize = 12.sp, lineHeight = 18.sp)
                }
                current.notes.forEach { note -> item { Text(note, color = Nb.Secondary, fontSize = 11.sp) } }
                item {
                    Button(onClick = {
                        working = true; error = null
                        vm.buildPixel(context, current, onCreated = { onDismiss() }, onError = { error = it; working = false })
                    }, enabled = !working && spec.validationError == null, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Nb.Ion, contentColor = Nb.OnIon)) {
                        Text(if (working) "Creating…" else "Create " + spec.title, Modifier.padding(6.dp))
                    }
                    spec.validationError?.let { Text(it, color = Nb.Rose, fontSize = 11.sp) }
                }
            }
            error?.let { message -> item { Text(message, color = Nb.Rose, fontSize = 12.sp) } }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
@Composable
private fun AccessToggle(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Nb.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
@Composable
private fun builderFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Nb.Text, unfocusedTextColor = Nb.Text,
    focusedBorderColor = Nb.Ion, unfocusedBorderColor = Nb.LineStrong,
    focusedContainerColor = Nb.FillSoft, unfocusedContainerColor = Nb.FillSoft)
