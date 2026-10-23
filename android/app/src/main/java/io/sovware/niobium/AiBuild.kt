package io.sovware.niobium

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIBuildSheet(vm: NiobiumViewModel, preview: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var refining by remember { mutableStateOf(false) }
    var refinementId by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<PixelDraft?>(null) }
    val latest by rememberUpdatedState(draft)
    var designJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var section by remember { mutableStateOf("Look") }
    var showSoul by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showAccess by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(listOf<PixelLook>()) }
    val scroll = rememberScrollState()
    fun cancelAI() { designJob?.cancel(); refinementId++; refining = false }
    fun quickPreview() {
        error = null; history = emptyList(); section = "Look"
        draft = PixelBuilder.preview(prompt.trim())
    }
    LaunchedEffect(preview) {
        if (preview && vm.state.demo) {
            prompt = "A sharp-tongued security witch for VPS logs, with a bat"
            quickPreview()
        }
    }
    LaunchedEffect(section) { scroll.scrollTo(0) }
    DisposableEffect(Unit) { onDispose { designJob?.cancel() } }
    val ideas = listOf(
        Triple("Security", "bat", "A sharp-tongued security witch for my VPS logs, with a tiny bat"),
        Triple("Creative", "cat", "A mischievous cyber creative director who makes images and moodboards"),
        Triple("Builder", "drone", "A calm release engineer with a wonky drone; asks before deploying"),
        Triple("Research", "slime", "A scholarly forest mage with a pocket slime who reads papers and keeps notes")
    )
    ModalBottomSheet(onDismissRequest = { if (!working) { cancelAI(); onDismiss() } }, containerColor = Nb.Void,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { !working || it != SheetValue.Hidden })) {
        val current = draft
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp)) {
                TextButton(enabled = !working, modifier = Modifier.align(Alignment.CenterStart), onClick = {
                    cancelAI()
                    if (draft != null) { draft = null; error = null; history = emptyList() } else onDismiss()
                }) {
                    PixelIcon(PixelGlyph.BACK, Nb.Secondary, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(if (current == null) "Close" else "Back", color = Nb.Secondary, fontSize = 13.sp)
                }
                Text(if (current == null) "New Pixel" else "Your Pixel", color = Nb.Text, style = NbTitle,
                    fontSize = 17.sp, modifier = Modifier.align(Alignment.Center))
                Box(Modifier.align(Alignment.CenterEnd)) { PixelIcon(PixelGlyph.SPARK, size = 20.dp) }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (current == null) {
                    Text("Give them a job.", color = Nb.Text, style = NbDisplay, fontSize = 28.sp)
                    OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Describe your Pixel") },
                        placeholder = { Text("A creative witch for my next project…") }, minLines = 3, maxLines = 6,
                        shape = PixelButtonShape, colors = builderFieldColors(), modifier = Modifier.fillMaxWidth())
                    ideas.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { idea ->
                                TextButton(onClick = { prompt = idea.third }, modifier = Modifier.weight(1f).pixelPanel()) {
                                    FamiliarSprite(idea.second, 24.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(idea.first, color = Nb.Text, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    val spec = current.spec
                    val base = Cast.all.first { it.id == spec.character.archetype }
                    val member = base.copy(companion = spec.companion.kind, companionName = spec.companion.name,
                        recipe = spec.character.recipe, look = spec.character.look, companionArt = spec.companion.art)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CastPortrait(member, 128.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            NbPill(if (current.usedModel) "AI persona" else "Local template")
                            BasicTextField(value = spec.title, onValueChange = { draft = current.copy(spec = spec.copy(title = it)) },
                                singleLine = true, enabled = !working, textStyle = NbTitle.copy(fontSize = 23.sp, color = Nb.Text),
                                cursorBrush = SolidColor(Nb.Ion),
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Pixel name" }.testTag("builder.name"))
                            Text(spec.role, color = Nb.Secondary, fontSize = 12.sp, maxLines = 2)
                            Text(spec.character.age.toString() + " · adult", color = Nb.Tertiary, style = NbMono, fontSize = 10.sp)
                            Text(spec.companion.name + " · " + (if (spec.companion.art == null) spec.companion.kind else "custom creature"), color = Nb.Secondary, fontSize = 11.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Look", "Creature", "Persona", "Access").forEach { name ->
                            TextButton(onClick = { section = name }, modifier = Modifier.weight(1f)
                                .background(if (section == name) Nb.Ion.copy(alpha = 0.12f) else Nb.FillSoft, PixelButtonShape)
                                .testTag("builder.tab." + name.lowercase()).semantics { selected = section == name }) {
                                Text(name, color = if (section == name) Nb.Ion else Nb.Secondary, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                    when (section) {
                        "Look" -> PixelLookEditor(spec.character.look ?: PixelLook(spec.character.archetype), history, !working,
                            onHistoryChange = { history = it }) { look ->
                            draft = current.copy(spec = spec.copy(character = spec.character.copy(look = look)))
                        }
                        "Creature" -> {
                            FamiliarPicker(spec.companion.kind, !working) {
                                draft = current.copy(spec = spec.copy(companion = spec.companion.copy(kind = it, trait = PixelBuilder.companionTrait(it), art = null)))
                            }
                            if (!working) CompanionArtEditor(spec.companion.kind, spec.companion.art) { art ->
                                draft = latest?.let { it.copy(spec = it.spec.copy(companion = it.spec.companion.copy(art = art))) }
                            }
                            OutlinedTextField(value = spec.companion.name, onValueChange = { draft = current.copy(spec = spec.copy(companion = spec.companion.copy(name = it))) },
                                label = { Text("Creature name") }, enabled = !working, singleLine = true,
                                shape = PixelButtonShape, colors = builderFieldColors(), modifier = Modifier.fillMaxWidth())
                            Text(spec.companion.trait, color = Nb.Secondary, fontSize = 12.sp)
                        }
                        "Persona" -> {
                            OutlinedTextField(value = spec.role, onValueChange = { draft = current.copy(spec = spec.copy(role = it)) },
                                label = { Text("Role") }, enabled = !working, shape = PixelButtonShape, colors = builderFieldColors(), modifier = Modifier.fillMaxWidth())
                            Text(spec.personality.joinToString(" · "), color = Nb.Secondary, fontSize = 12.sp)
                            TextButton(onClick = { showSoul = !showSoul }) { Text(if (showSoul) "Hide briefing" else "Briefing", color = Nb.Ion) }
                            if (showSoul) Text(spec.soul, color = Nb.Secondary, fontSize = 12.sp, lineHeight = 18.sp)
                            PixelButton(if (refining) "Cancel AI" else "Refine persona with AI", PixelGlyph.SPARK,
                                Modifier.fillMaxWidth(), secondary = true, enabled = !working) {
                                if (refining) cancelAI() else {
                                    refining = true; error = null
                                    val start = current
                                    val requestId = ++refinementId
                                    designJob = vm.draftPixel(context, prompt, onDraft = { result ->
                                        if (refinementId == requestId) {
                                            latest?.let { value ->
                                                val old = value.spec
                                                draft = value.copy(spec = old.copy(
                                                    title = if (old.title == start.spec.title) result.spec.title else old.title,
                                                    role = if (old.role == start.spec.role) result.spec.role else old.role,
                                                    soul = if (old.soul == start.spec.soul) result.spec.soul else old.soul,
                                                    personality = if (old.personality == start.spec.personality) result.spec.personality else old.personality,
                                                    toolsets = if (old.toolsets == start.spec.toolsets) result.spec.toolsets else old.toolsets,
                                                    skills = if (old.skills == start.spec.skills) result.spec.skills else old.skills,
                                                    connectors = if (old.connectors == start.spec.connectors) result.spec.connectors else old.connectors),
                                                    availableToolsets = result.availableToolsets, availableSkills = result.availableSkills,
                                                    usedModel = result.usedModel, notes = result.notes)
                                            }
                                            refining = false
                                        }
                                    }, onError = { if (refinementId == requestId) { error = it; refining = false } })
                                }
                            }
                            if (current.notes.isNotEmpty()) {
                                TextButton(onClick = { showDetails = !showDetails }) { Text("Details", color = Nb.Secondary, fontSize = 12.sp) }
                                if (showDetails) current.notes.forEach { Text(it, color = Nb.Secondary, fontSize = 11.sp) }
                            }
                        }
                        else -> {
                            Column(Modifier.pixelPanel().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                AccessToggle("Network", spec.permissions.network, !working) { draft = current.copy(spec = spec.copy(permissions = spec.permissions.copy(network = it))) }
                                AccessToggle("Shell", spec.permissions.shell, !working) { draft = current.copy(spec = spec.copy(permissions = spec.permissions.copy(shell = it))) }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("none", "workspace", "unrestricted").forEach { files ->
                                        FilterChip(selected = spec.permissions.filesystem == files, enabled = !working,
                                            onClick = { draft = current.copy(spec = spec.copy(permissions = spec.permissions.copy(filesystem = files))) },
                                            label = { Text(if (files == "unrestricted") "Full" else files.replaceFirstChar { it.uppercase() }, fontSize = 11.sp) })
                                    }
                                }
                            }
                            Text("Tools · " + current.enabledToolsets.joinToString(), color = Nb.Secondary, fontSize = 11.sp)
                            if (spec.skills.isNotEmpty()) Text("Skills · " + spec.skills.joinToString(), color = Nb.Secondary, fontSize = 11.sp)
                            if (spec.connectors.isNotEmpty()) Text("Requested · " + spec.connectors.joinToString(), color = Nb.Secondary, fontSize = 11.sp)
                            TextButton(onClick = { showAccess = !showAccess }) { Text("Access details", color = Nb.Secondary, fontSize = 12.sp) }
                            if (showAccess) {
                                Text("Tool choices are applied to the engine. Workspace scope is an instruction; the engine enforces sandboxing. Connections need separate account authorization.",
                                    color = Nb.Secondary, fontSize = 11.sp, lineHeight = 16.sp)
                                OutlinedTextField(value = spec.name, onValueChange = { draft = current.copy(spec = spec.copy(name = it)) },
                                    label = { Text("Profile ID") }, enabled = !working, singleLine = true,
                                    shape = PixelButtonShape, colors = builderFieldColors(), modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                error?.let { Text(it, color = Nb.Rose, fontSize = 12.sp) }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current == null) {
                    PixelButton("Preview Pixel", PixelGlyph.SPARK, Modifier.fillMaxWidth(), enabled = prompt.isNotBlank() && prompt.length <= 4000) { quickPreview() }
                } else {
                    val spec = current.spec
                    val files = when (spec.permissions.filesystem) { "workspace" -> "Workspace"; "none" -> "No files"; else -> "Full files" }
                    Text(listOf(if (spec.permissions.network) "Network" else "No network", files, if (spec.permissions.shell) "Shell" else "No shell").joinToString(" · "),
                        color = Nb.Secondary, fontSize = 10.sp)
                    PixelButton(if (working) "Creating…" else "Create " + spec.title, PixelGlyph.PIXELS, Modifier.fillMaxWidth(),
                        enabled = !working && spec.validationError == null) {
                        cancelAI(); working = true; error = null
                        vm.buildPixel(context, current, onCreated = { onDismiss() }, onError = { error = it; working = false })
                    }
                    spec.validationError?.let { Text(it, color = Nb.Rose, fontSize = 11.sp) }
                }
            }
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
    focusedContainerColor = Nb.Surface, unfocusedContainerColor = Nb.Surface)
