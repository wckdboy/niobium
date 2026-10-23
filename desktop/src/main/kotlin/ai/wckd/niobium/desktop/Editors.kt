package ai.wckd.niobium.desktop

import io.sovware.niobium.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PixelCreator(agent: DesktopAgent, artist: DesktopArtist, credentials: DesktopCredentials, connected: Boolean, initialLook: PixelLook? = null, onCreated: (() -> Unit)? = null, onClose: () -> Unit) {
    var brief by remember { mutableStateOf("A clever creative partner with a mischievous edge") }
    var draft by remember(agent, initialLook) { mutableStateOf(PixelBuilder.preview(brief).let { base ->
        if (initialLook == null) base else base.copy(spec = base.spec.copy(character = base.spec.character.copy(
            archetype = initialLook.entry.archetype, recipe = base.spec.character.recipe.copy(body = initialLook.body), look = initialLook)))
    }) }
    var tab by remember { mutableStateOf("Look") }
    var atelier by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val spec = draft.spec
    val look = spec.character.look ?: PixelLook(spec.character.archetype)
    BasicAlertDialog(onDismissRequest = { if (!busy) onClose() }) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().heightIn(max = 740.dp).clip(PixelShape).background(Ink.Paper).border(1.dp, Ink.Line, PixelShape)
            .onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && !busy) { onClose(); true } else false }
            .verticalScroll(rememberScrollState()).padding(26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Eyebrow("Pixel workshop"); Text("Make good company.", color = Ink.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onClose, enabled = !busy) { Text("Close") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.semantics { contentDescription = "Selected look: ${look.entry.label}" }.width(208.dp).height(130.dp).clip(PixelShape).background(Ink.Pale), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) { Portrait(look, 120); Familiar(spec.companion.kind, spec.companion.art, 48) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(spec.title, { draft = draft.copy(spec = spec.copy(title = it.take(24))) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    Text(spec.role, color = Ink.Muted, fontSize = 12.sp)
                    Text(spec.personality.joinToString(" · "), color = Ink.Violet, fontSize = 11.sp)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Look", "Companion", "Persona", "Access").forEach { name -> FilterChip(selected = tab == name, onClick = { tab = name }, label = { Text(name) }, shape = PixelShape, enabled = !busy) } }
            Box(Modifier.heightIn(min = 180.dp)) {
                when (tab) {
                    "Look" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { draft = draft.copy(spec = spec.copy(character = spec.character.copy(look = look.remixed(java.util.UUID.randomUUID().toString())))) }, enabled = !busy) { Text("Remix colors") }
                            TextButton(onClick = { atelier = "pixel" }, enabled = !busy) { Text("Design with AI") }
                            if (look.customArt != null) TextButton(onClick = { draft = draft.copy(spec = spec.copy(character = spec.character.copy(look = look.copy(customArt = null)))) }, enabled = !busy) { Text("Use wardrobe look") }
                        }
                        LazyVerticalGrid(GridCells.Adaptive(68.dp * LocalDensity.current.fontScale), modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(PixelWardrobe.entries, key = { it.id }) { entry ->
                                Column(Modifier.clip(PixelShape).background(if (look.portrait == entry.id) Ink.Pale else Ink.Panel).border(1.dp, if (look.portrait == entry.id) Ink.Violet else Ink.Line, PixelShape).pixelAction(enabled = !busy, selected = look.portrait == entry.id, role = Role.RadioButton) {
                                    draft = draft.copy(spec = spec.copy(character = spec.character.copy(archetype = entry.archetype, recipe = spec.character.recipe.copy(body = entry.body), look = PixelLook(entry.id))))
                                }.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) { Portrait(PixelLook(entry.id), 48); Text(entry.label, color = Ink.Muted, fontSize = 9.sp) }
                            }
                        }
                    }
                    "Companion" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LazyVerticalGrid(GridCells.Adaptive(84.dp * LocalDensity.current.fontScale), modifier = Modifier.height(240.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PixelBuilder.companions) { kind -> Column(Modifier.clip(PixelShape).background(if (spec.companion.kind == kind) Ink.Pale else Ink.Panel).pixelAction(enabled = !busy, selected = spec.companion.kind == kind, role = Role.RadioButton) {
                                val index = PixelBuilder.companions.indexOf(kind)
                                draft = draft.copy(spec = spec.copy(companion = PixelCompanion(kind, PixelBuilder.companionNames[index], PixelBuilder.companionTrait(kind))))
                            }.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Familiar(kind); Text(kind.replaceFirstChar { it.uppercase() }, color = Ink.Muted, fontSize = 10.sp) } }
                        }
                        TextButton(onClick = { atelier = "companion" }, enabled = !busy) { Text("Design a creature with AI") }
                        Text("A little strange. A lot of help. Transparent sprites, ready for real delegation.", color = Ink.Muted, fontSize = 11.sp)
                    }
                    "Persona" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(brief, { brief = it.take(4000) }, Modifier.fillMaxWidth(), label = { Text("What should this Pixel do?") }, minLines = 2, maxLines = 4, enabled = !busy)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton("Instant persona", secondary = true, enabled = brief.isNotBlank() && !busy) {
                                val next = PixelBuilder.preview(brief)
                                draft = next.copy(spec = next.spec.copy(character = spec.character, companion = spec.companion, permissions = spec.permissions, toolsets = spec.toolsets, skills = spec.skills, connectors = spec.connectors))
                            }
                            PixelButton(if (busy) "Designing…" else "Refine with AI", icon = "spark", enabled = connected && brief.isNotBlank() && !busy) {
                                busy = true; error = null
                                scope.launch { try { val next = agent.draft(brief); draft = next.copy(spec = next.spec.copy(character = spec.character, companion = spec.companion, permissions = spec.permissions, toolsets = spec.toolsets, skills = spec.skills, connectors = spec.connectors)) }
                                catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message } finally { busy = false } }
                            }
                        }
                        Text("AI refinement uses the selected engine’s model and API usage.", color = Ink.Muted, fontSize = 10.sp)
                        Text(spec.soul, color = Ink.Text, fontSize = 12.sp, lineHeight = 19.sp)
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Review what this Pixel may use.", color = Ink.Text, fontWeight = FontWeight.SemiBold)
                        AccessSwitch("Network", spec.permissions.network, !busy) { draft = draft.copy(spec = spec.copy(permissions = spec.permissions.copy(network = it))) }
                        AccessSwitch("Workspace files", spec.permissions.filesystem != "none", !busy) { draft = draft.copy(spec = spec.copy(permissions = spec.permissions.copy(filesystem = if (it) "workspace" else "none"))) }
                        AccessSwitch("Shell commands", spec.permissions.shell, !busy) { draft = draft.copy(spec = spec.copy(permissions = spec.permissions.copy(shell = it))) }
                        draft.availableToolsets.sorted().forEach { name -> AccessSwitch(name, name in draft.enabledToolsets, !busy && name in spec.copy(toolsets = (spec.toolsets + name).distinct()).permittedToolsets) { checked -> draft = draft.copy(spec = spec.copy(toolsets = if (checked) (spec.toolsets + name).distinct() else spec.toolsets - name)) } }
                        Text("Tools follow host approvals and your account permissions. Workspace scope is an agent rule, not an OS sandbox.", color = Ink.Muted, fontSize = 11.sp)
                        OutlinedTextField(spec.name, { draft = draft.copy(spec = spec.copy(name = it.take(31))) }, label = { Text("Profile ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    }
                }
            }
            draft.notes.forEach { Text(it, color = Ink.Muted, fontSize = 11.sp) }
            error?.let { Text(it, color = Ink.Red, fontSize = 12.sp) }
            if (!connected) Text("Connect Hermes or start local DSH to create this Pixel.", color = Ink.Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Adult cast · ${spec.character.age}+", color = Ink.Muted, fontSize = 10.sp)
                PixelButton(if (busy) "Working…" else "Create Pixel", icon = "add", enabled = connected && !busy && spec.validationError == null) {
                    busy = true; error = null
                    scope.launch { try { agent.createPixel(draft); if (onCreated != null) onCreated() else onClose() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message } finally { busy = false } }
                }
            }
        }
    }
    atelier?.let { target -> AtelierDialog(artist, credentials, target, reference = if (target == "pixel") PixelImages.reference(look) else PixelImages.companionReference(spec.companion.kind, spec.companion.art), onClose = { atelier = null }) { art ->
        draft = if (target == "pixel") draft.copy(spec = spec.copy(character = spec.character.copy(look = look.copy(customArt = art))))
        else draft.copy(spec = spec.copy(companion = spec.companion.copy(art = art)))
        atelier = null
    } }
}

@Composable private fun AccessSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(PixelShape).pixelToggle(checked, enabled, Role.Switch, onChange).heightIn(min = 48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f), color = Ink.Text, fontSize = 12.sp)
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable fun AtelierDialog(artist: DesktopArtist, credentials: DesktopCredentials, target: String, reference: ByteArray, onClose: () -> Unit, onApply: (PixelArt) -> Unit) {
    val job by artist.state.collectAsState()
    val auth by artist.credentials.collectAsState()
    var brief by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    val keyReady = auth.ready
    AlertDialog(onDismissRequest = onClose, title = { Text(if (target == "companion") "A creature of your own." else "Something a little wicked.") }, text = {
        Column(Modifier.width(500.dp).heightIn(max = 550.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ImageCredentials(artist, credentials)
            OutlinedTextField(brief, { brief = it.take(2000) }, Modifier.fillMaxWidth(), minLines = 3, maxLines = 5, label = { Text(if (target == "companion") "Describe your creature" else "New clothes, details, or style") })
            Text("Sends your brief and reference art to OpenAI. Provider charges apply. The provider stores the design so you can check it later; its receipt stays on this computer.", color = Ink.Muted, fontSize = 11.sp)
            if (auth.ready && !auth.receiptMatches) Text(if (auth.legacyReceipt) "This saved design predates account tracking. Confirm the account before checking it." else "This key differs from the saved design’s key. Confirm its account before checking it.", color = Ink.Muted, fontSize = 11.sp)
            if (auth.ready && !auth.receiptMatches && job?.responseId != null) TextButton(onClick = { artist.resume(confirmAccount = true) }) { Text("This is the design’s account · check status") }
            if (artist.busy) {
                Text(if (job?.phase == "submitting") "Starting design…" else "Designing… You can close this window.", color = Ink.Violet, fontSize = 12.sp)
                if (job?.responseId != null && keyReady && auth.receiptMatches) Row { TextButton(onClick = { artist.resume() }) { Text("Check status") }; TextButton(onClick = artist::cancel) { Text("Cancel design") } }
            } else PixelButton("Generate design", icon = "spark", enabled = keyReady && brief.isNotBlank() && job?.phase != "unknown") {
                try { artist.generate(brief, target, reference); error = null } catch (e: Exception) { error = e.message }
            }
            if (job?.phase == "unknown") TextButton(onClick = artist::clearUnconfirmed) { Text("I checked provider usage; start over") }
            val art = job?.takeIf { it.target == target }?.candidate
            if (art != null) {
                Row(Modifier.fillMaxWidth().background(Ink.Pale).padding(16.dp), horizontalArrangement = Arrangement.Center) {
                    if (target == "companion") Familiar("wisp", art, 128) else Portrait(PixelLook("nova", customArt = art), 192)
                }
                PixelButton("Use this design", Modifier.fillMaxWidth()) { onApply(art) }
            }
            job?.message?.let { Text(it, color = Ink.Muted, fontSize = 12.sp) }
            error?.let { Text(it, color = Ink.Red, fontSize = 12.sp) }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } }, shape = PixelShape)
}

@Composable fun RoomCreator(agent: DesktopAgent, bots: List<BotProfile>, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }; var members by remember { mutableStateOf(emptySet<String>()) }
    AlertDialog(onDismissRequest = onClose, title = { Text("A room for your Pixels") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Room name") }, singleLine = true)
            Text("Choose two to six Pixels.", color = Ink.Muted, fontSize = 12.sp)
            bots.forEach { bot -> LabeledCheckbox(bot.title, bot.name in members, bot.name in members || members.size < 6) { selected -> members = if (selected) members + bot.name else members - bot.name } }
        }
    }, confirmButton = { TextButton(onClick = { agent.createRoom(name, bots.filter { it.name in members }, onClose) }, enabled = name.isNotBlank() && members.size in 2..6) { Text("Create room") } }, dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }, shape = PixelShape)
}

@Composable fun PromptDialog(agent: DesktopAgent, prompt: AgentPrompt) {
    var answers by remember(prompt.id) { mutableStateOf(emptyMap<String, Set<String>>()) }
    var custom by remember(prompt.id) { mutableStateOf(emptyMap<String, String>()) }
    var secret by remember(prompt.id) { mutableStateOf("") }
    val questions = prompt.params.objects("questions")
    val choices = prompt.params.strings("choices").ifEmpty { listOf("deny") }
    AlertDialog(onDismissRequest = {}, title = { Text(when (prompt.method) { "approval" -> "Permission needed"; "clarify" -> "A quick question"; else -> "Host credential request" }) }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (prompt.method == "clarify") questions.forEach { question ->
                val id = question.getString("qid"); val options = question.strings("choices")
                Text(question.s("question").orEmpty(), color = Ink.Text)
                options.forEach { option -> LabeledCheckbox(option, option in answers[id].orEmpty()) { checked -> val selected = answers[id].orEmpty(); answers = answers + (id to if (question.optBoolean("multi_select")) { if (checked) selected + option else selected - option } else if (checked) setOf(option) else emptySet()); custom = custom - id } }
                OutlinedTextField(custom[id].orEmpty(), { custom = custom + (id to it); answers = answers - id }, label = { Text("Your answer") }, modifier = Modifier.fillMaxWidth())
            } else {
                Text(prompt.params.s("description") ?: prompt.params.s("prompt") ?: prompt.params.s("hint") ?: "Your host is waiting for this value.", color = Ink.Muted, fontSize = 13.sp)
                prompt.params.s("command")?.let { Text(it, color = Ink.Text, fontSize = 13.sp) }
                if (prompt.method != "approval") OutlinedTextField(secret, { secret = it }, label = { Text("Value") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            }
        }
    }, confirmButton = {
        if (prompt.method == "approval") Row { choices.forEach { choice -> TextButton(onClick = { agent.answer(prompt, JSONObject().put("choice", choice)) }) { Text(when (choice) { "once" -> "Allow once"; "deny" -> "Deny"; else -> choice }) } } }
        else TextButton(onClick = {
            val value = if (prompt.method == "clarify") JSONObject().put("outcome", "submitted").put("answers", JSONObject().apply {
                questions.forEach { question -> val id = question.getString("qid"); val selected = answers[id].orEmpty(); put(id, if (selected.isEmpty()) custom[id].orEmpty() else if (question.optBoolean("multi_select") && agent.local) JSONArray(selected.toList()) else selected.joinToString(", ")) }
            }) else JSONObject().put("value", secret)
            agent.answer(prompt, value); secret = ""
        }, enabled = if (prompt.method == "clarify") questions.all { answers[it.getString("qid")].orEmpty().isNotEmpty() || custom[it.getString("qid")].orEmpty().isNotBlank() } else secret.isNotBlank()) { Text("Send answer") }
    }, dismissButton = { if (prompt.method != "approval") TextButton(onClick = { if (prompt.params.optBoolean("_recovered")) agent.defer(prompt) else agent.answer(prompt, if (prompt.method == "clarify") JSONObject().put("outcome", "cancelled").put("answers", JSONObject()) else JSONObject().put("value", "")) }) { Text(if (prompt.params.optBoolean("_recovered")) "Leave waiting" else "Cancel") } }, shape = PixelShape)
}
