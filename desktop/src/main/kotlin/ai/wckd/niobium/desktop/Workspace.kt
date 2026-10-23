package ai.wckd.niobium.desktop

import io.sovware.niobium.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable fun Portrait(look: PixelLook, size: Int = 96) {
    Image(PixelImages.portrait(look), "${look.entry.label} portrait", Modifier.size(size.dp), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
}
@Composable fun Familiar(kind: String, art: PixelArt? = null, size: Int = 48) {
    Image(if (art == null) PixelImages.companion(kind) else PixelImages.art(art), "$kind companion", Modifier.size(size.dp), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
}

@Composable fun NiobiumApp(hermes: DesktopAgent, dsh: DesktopAgent, runtime: LocalEngine, credentials: DesktopCredentials) {
    val remote by hermes.state.collectAsState(); val local by dsh.state.collectAsState(); val status by runtime.state.collectAsState()
    var localSelected by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf("Pixels") }
    var creating by remember { mutableStateOf(false) }
    val creatorFocus = remember { FocusRequester() }
    var creatorOpener by remember { mutableStateOf<FocusRequester?>(null) }
    var creatorPage by remember { mutableStateOf("Pixels") }
    var restoreCreatorFocus by remember { mutableStateOf(false) }
    var initialLook by remember { mutableStateOf<PixelLook?>(null) }
    var createRoom by remember { mutableStateOf(false) }
    val agent = if (localSelected) dsh else hermes
    val state = if (localSelected) local else remote
    val scope = rememberCoroutineScope()
    val artist = remember { DesktopArtist(scope) }
    LaunchedEffect(creating) {
        if (!creating && restoreCreatorFocus) {
            restoreCreatorFocus = false
            if (page in listOf("Pixels", "Wardrobe")) (creatorOpener?.takeIf { page == creatorPage } ?: creatorFocus).requestFocus()
        }
    }
    Row(Modifier.fillMaxSize().background(Ink.Paper)) {
        Column(Modifier.width(204.dp).fillMaxHeight().background(Ink.Panel).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource("niobium.png"), "Niobium lowercase n logo", Modifier.size(36.dp))
                Text("niobium", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink.Text)
            }
            Spacer(Modifier.height(22.dp))
            Eyebrow("Workspace")
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Pixels" to "pixels", "Rooms" to "rooms", "Wardrobe" to "spark", "Engines" to "engine").forEach { (label, icon) ->
                Row(Modifier.fillMaxWidth().clip(PixelShape).background(if (page == label) Ink.Pale else Ink.Panel)
                    .testTag("nav.$label").pixelAction(selected = page == label, role = Role.Tab) { page = label }.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(icon, if (page == label) Ink.Violet else Ink.Muted, 16.dp)
                    Text(label, fontSize = 13.sp, color = if (page == label) Ink.Violet else Ink.Muted, fontWeight = FontWeight.SemiBold)
                }
            } }
            Spacer(Modifier.weight(1f))
            Eyebrow("Agent engines")
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) { listOf(false to "Hermes", true to "Local DSH").forEach { (isLocal, name) ->
                val online = if (isLocal) local.connected else remote.connected
                Column(Modifier.fillMaxWidth().clip(PixelShape).background(if (localSelected == isLocal) Ink.Pale else Ink.Paper)
                    .testTag("engine.$name").semantics { stateDescription = if (online) "Connected" else if (isLocal) status.phase else "Disconnected" }
                    .pixelAction(selected = localSelected == isLocal, role = Role.RadioButton) { localSelected = isLocal }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatusDot(online); Text(name, fontSize = 12.sp, color = Ink.Text, fontWeight = FontWeight.SemiBold) }
                    Text(if (online) "Connected" else if (isLocal) status.phase else "Remote host", fontSize = 10.sp, color = Ink.Muted)
                }
            } }
            Text("2.1 · desktop preview", Modifier.padding(top = 12.dp), color = Ink.Muted, fontSize = 10.sp)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Ink.Line))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 90.dp).padding(horizontal = 28.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Eyebrow(if (localSelected) "On this computer" else "Your remote crew")
                    Text(page, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink.Text)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.connecting) Text("Connecting…", color = Ink.Muted, fontSize = 12.sp)
                    if (page == "Rooms") PixelButton("New room", secondary = true, enabled = state.connected && "groups.create" in state.groupMethods && state.bots.size >= 2, icon = "add") { createRoom = true }
                    if (page in listOf("Pixels", "Wardrobe")) PixelButton("New Pixel", Modifier.focusRequester(creatorFocus), icon = "add") { initialLook = null; creatorOpener = creatorFocus; creatorPage = page; creating = true }
                }
            }
            HorizontalDivider(color = Ink.Line)
            state.error?.let { message -> Row(Modifier.fillMaxWidth().background(Ink.Pale).padding(horizontal = 26.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), color = Ink.Red, fontSize = 12.sp)
                TextButton(onClick = agent::dismissError) { Text("Dismiss") }
            } }
            when (page) {
                "Engines" -> EnginesPanel(hermes, dsh, runtime, credentials, onLocal = { localSelected = true; page = "Pixels" })
                "Wardrobe" -> Wardrobe { look, opener -> initialLook = look; creatorOpener = opener; creatorPage = page; creating = true }
                "Rooms" -> Row(Modifier.weight(1f)) {
                    Column(Modifier.width(255.dp).fillMaxHeight().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.groups.isEmpty()) EmptyHint("A room for the whole crew.", if (state.connected && state.groupsSupported) "Choose two to six Pixels to begin." else "Connect an engine with room support.")
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) { items(state.groups, key = { it.roomId }) { room ->
                            Column(Modifier.fillMaxWidth().clip(PixelShape).background(if (state.room?.roomId == room.roomId) Ink.Pale else Ink.Panel).pixelAction(selected = state.room?.roomId == room.roomId, role = Role.Tab) { agent.openRoom(room) }.padding(14.dp)) {
                                Text(room.name, color = Ink.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("${room.members.size} Pixels", color = Ink.Muted, fontSize = 11.sp)
                            }
                        } }
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(Ink.Line))
                    ChatPanel(agent, state, Modifier.weight(1f), roomMode = true)
                }
                else -> if (!state.connected && !state.connecting) {
                    Welcome(localSelected, onEngines = { page = "Engines" }, onWardrobe = { page = "Wardrobe" })
                } else Row(Modifier.weight(1f)) {
                    Column(Modifier.width(255.dp).fillMaxHeight().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Eyebrow("${state.bots.size} Pixels")
                            TextButton(onClick = agent::refresh) { Text("Refresh", fontSize = 11.sp) }
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(state.bots, key = { it.id }) { bot ->
                            val look = state.specs[bot.name]?.character?.look ?: PixelLook.generated(if (bot.name == "default") "rook" else bot.name, "man", bot.name)
                            Row(Modifier.fillMaxWidth().clip(PixelShape).background(if (state.bot?.id == bot.id) Ink.Pale else Ink.Panel).pixelAction(enabled = !state.loading, selected = state.bot?.id == bot.id, role = Role.Tab) { agent.open(bot) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Portrait(look, 48)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(bot.title, color = Ink.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(bot.role.ifBlank { if (localSelected) "Local Pixel" else "Hermes Pixel" }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Ink.Muted, fontSize = 10.sp)
                                }
                                if (bot.isLive) StatusDot(true)
                            }
                        } }
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(Ink.Line))
                    ChatPanel(agent, state, Modifier.weight(1f))
                }
            }
        }
    }
    if (creating) PixelCreator(agent, artist, credentials, connected = state.connected, initialLook = initialLook,
        onCreated = { creating = false; page = "Pixels"; restoreCreatorFocus = true },
        onClose = { creating = false; restoreCreatorFocus = true })
    if (createRoom) RoomCreator(agent, state.bots) { createRoom = false }
    // Each engine owns its dialog. Switching engines cannot answer the other one.
    state.prompts.firstOrNull()?.let { prompt -> PromptDialog(agent, prompt) }
}

@Composable private fun EmptyHint(title: String, detail: String) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Ink.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = Ink.Muted, fontSize = 12.sp)
    }
}
@Composable private fun Welcome(local: Boolean, onEngines: () -> Unit, onWardrobe: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().clip(PixelShape).background(Ink.Pale)) {
            Canvas(Modifier.matchParentSize()) { pixelDust() }
            if (maxWidth >= 820.dp * LocalDensity.current.fontScale) {
                Row(Modifier.padding(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    WelcomeCopy(local, onEngines, Modifier.weight(1f))
                    WelcomeCast()
                }
            } else Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                WelcomeCopy(local, onEngines, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { WelcomeCast() }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 640.dp * LocalDensity.current.fontScale) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                WelcomeEngine(Modifier.fillMaxWidth())
                WelcomeWardrobe(Modifier.fillMaxWidth(), onWardrobe)
            } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                WelcomeEngine(Modifier.weight(1f))
                WelcomeWardrobe(Modifier.weight(1f), onWardrobe)
            }
        }
    }
}

@Composable private fun WelcomeCopy(local: Boolean, onEngines: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow("Your pixels. Everywhere.", Ink.Violet)
        Text(if (local) "A little local magic." else "Good company.\nSerious capability.", color = Ink.Text, fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold)
        PixelButton(if (local) "Set up local DSH" else "Connect an engine", icon = "engine", onClick = onEngines)
    }
}
@Composable private fun WelcomeCast() {
    Row(verticalAlignment = Alignment.Bottom) { Portrait(PixelLook("lilith"), 144); Familiar("imp", size = 48); Portrait(PixelLook("zero"), 144) }
}
@Composable private fun WelcomeEngine(modifier: Modifier) {
    Panel(modifier) { Glyph("engine", Ink.Violet, 24.dp); Text("Two engines. One home.", color = Ink.Text, fontWeight = FontWeight.SemiBold); Text("Connect to Hermes, run DSH here, or keep both ready.", color = Ink.Muted, fontSize = 12.sp) }
}
@Composable private fun WelcomeWardrobe(modifier: Modifier, onWardrobe: () -> Unit) {
    Panel(modifier) { Glyph("spark", Ink.Violet, 24.dp); Text("A crew with character.", color = Ink.Text, fontWeight = FontWeight.SemiBold); Text("32 looks. Twelve little familiars. A role for every Pixel.", color = Ink.Muted, fontSize = 12.sp); TextButton(onClick = onWardrobe) { Text("Explore the wardrobe") } }
}

@Composable internal fun EnginesPanel(hermes: DesktopAgent, dsh: DesktopAgent, runtime: LocalEngine, credentials: DesktopCredentials, onLocal: () -> Unit) {
    var stopConfirm by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
        if (maxWidth < 780.dp * LocalDensity.current.fontScale) {
            Column(Modifier.fillMaxWidth().testTag("engines.stacked"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                HermesSetup(hermes, credentials, Modifier.fillMaxWidth())
                LocalSetup(dsh, runtime, Modifier.fillMaxWidth(), onLocal) { stopConfirm = true }
            }
        } else Row(Modifier.fillMaxWidth().testTag("engines.columns"), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            HermesSetup(hermes, credentials, Modifier.weight(1f))
            LocalSetup(dsh, runtime, Modifier.weight(1f), onLocal) { stopConfirm = true }
        }
    }
    if (stopConfirm) AlertDialog(onDismissRequest = { stopConfirm = false }, title = { Text("Stop local DSH?") }, text = { Text("Running local tasks will stop. Your Pixels and chat history stay saved.") },
        confirmButton = { TextButton(onClick = { dsh.disconnect(); runtime.stop(); stopConfirm = false }) { Text("Stop DSH") } }, dismissButton = { TextButton(onClick = { stopConfirm = false }) { Text("Keep running") } }, shape = PixelShape)
}

@Composable private fun HermesSetup(hermes: DesktopAgent, credentials: DesktopCredentials, modifier: Modifier) {
    val remote by hermes.state.collectAsState()
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Glyph("engine", Ink.Violet); Text("Hermes", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink.Text); StatusDot(remote.connected) }
        Text("Your agent on a PC or VPS.", color = Ink.Muted, fontSize = 12.sp)
        GatewayCredentials(hermes, credentials)
    }
}

@Composable private fun LocalSetup(dsh: DesktopAgent, runtime: LocalEngine, modifier: Modifier, onLocal: () -> Unit, onStop: () -> Unit) {
    val local by dsh.state.collectAsState(); val status by runtime.state.collectAsState()
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Glyph("engine", Ink.Violet); Text("Local DSH", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink.Text); StatusDot(status.phase == "Running") }
        Text("Your Pixels run on this computer.", color = Ink.Muted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().background(Ink.Paper).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Engine", color = Ink.Muted, fontSize = 12.sp); Text(status.phase, color = Ink.Text, fontSize = 12.sp) }
        Text("Uses provider API keys. Tools run with your user account; access choices are agent rules, not an OS sandbox.", color = Ink.Muted, fontSize = 12.sp)
        Text("Workspace: ${runtime.workspace}", color = Ink.Muted, fontSize = 10.sp)
        status.error?.let { Text(it, color = Ink.Red, fontSize = 12.sp) }
        if (status.phase == "Stopped") PixelButton("Start local DSH", icon = "engine") { runtime.start { endpoint -> dsh.connect(endpoint.origin, endpoint.token); onLocal() } }
        else PixelButton(if (status.phase == "Running") "Stop local DSH" else status.phase + "…", secondary = true, enabled = status.phase == "Running", icon = "stop", onClick = onStop)
        if (local.connected) {
            HorizontalDivider(color = Ink.Line)
            LocalProviderPanel(local, dsh)
        }
    }
}

@Composable private fun Wardrobe(onCreate: (PixelLook, FocusRequester) -> Unit) {
    var family by remember { mutableStateOf("All") }
    Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("All", "Arcane", "Cyber", "Wild", "Dark").forEach { name -> FilterChip(modifier = Modifier.testTag("wardrobe.filter.$name"), selected = family == name, onClick = { family = name }, label = { Text(name) }, shape = PixelShape) }
            Spacer(Modifier.weight(1f)); Text("48 × 48 · adult cast", color = Ink.Muted, fontSize = 11.sp)
        }
        LazyVerticalGrid(GridCells.Adaptive(140.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(PixelWardrobe.entries.filter { family == "All" || it.family == family }, key = { it.id }) { entry ->
                val opener = remember(entry.id) { FocusRequester() }
                Column(Modifier.clip(PixelShape).background(Ink.Panel).border(1.dp, Ink.Line, PixelShape).focusRequester(opener).pixelAction { onCreate(PixelLook(entry.id), opener) }.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Portrait(PixelLook(entry.id), 96)
                    Text(entry.label, color = Ink.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(entry.family, color = Ink.Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable internal fun ChatPanel(agent: DesktopAgent, state: DesktopState, modifier: Modifier, roomMode: Boolean = false) {
    val title = if (roomMode) state.room?.name else state.bot?.title
    var text by remember(agent, state.session, state.room?.roomId) { mutableStateOf("") }
    var models by remember { mutableStateOf(false) }
    val rows = if (roomMode) state.events.filter { it.isMessage || it.isNotice }.map { ChatMessage(it.id, if (it.actorId == "user") "user" else "assistant", if (it.isMessage) it.text else it.reason ?: it.status ?: it.kind, toolName = if (!it.isMessage) "room" else null, toolPreview = it.displayName) } else state.messages
    fun send() { val submitted = text; if (roomMode) agent.sendRoom(submitted) { if (text == submitted) text = "" } else agent.send(submitted) { if (text == submitted) text = "" } }
    Column(modifier.fillMaxHeight()) {
        if (title == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) { Familiar("wisp", size = 64); Text(if (roomMode) "Choose a room." else "Choose your company.", color = Ink.Muted, fontSize = 16.sp) } }; return }
        Row(Modifier.fillMaxWidth().heightIn(min = 78.dp).background(Ink.Panel).padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.bot != null) Portrait(state.specs[state.bot.name]?.character?.look ?: PixelLook("rook"), 48) else Glyph("rooms", Ink.Violet, 24.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, color = Ink.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text(if (roomMode) state.room!!.members.joinToString(" · ") { it.name } else state.model.ifEmpty { if (agent.local) "Local DSH" else "Hermes" }, color = Ink.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (state.modelChoices.isNotEmpty()) Box { TextButton(onClick = { models = true }) { Text("Model", fontSize = 11.sp) }; DropdownMenu(models, { models = false }) { state.modelChoices.forEach { choice -> DropdownMenuItem(text = { Text((if (choice.current) "✓ " else "") + choice.label) }, onClick = { agent.selectModel(choice); models = false }) } } }
            if ((!roomMode && state.working) || (roomMode && "groups.stop" in state.groupMethods)) PixelButton("Stop", secondary = true, icon = "stop", onClick = agent::stopTurn)
        }
        HorizontalDivider(color = Ink.Line)
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Ink.Violet)
        key(agent, state.session, state.room?.roomId) {
            ChatTimeline(rows, title, roomMode, state.loading, Modifier.weight(1f).fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth().background(Ink.Panel).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f).testTag("chat.input").onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key in listOf(Key.Enter, Key.NumPadEnter) && !event.isShiftPressed && !event.isCtrlPressed && !event.isMetaPressed && !event.isAltPressed) { if (state.connected && text.isNotBlank() && !state.sending && !state.loading) send(); true } else false
            }.semantics { contentDescription = if (roomMode) "Message room $title" else "Message $title" }, placeholder = { Text(if (roomMode) "Message the room…" else "Message $title…") }, maxLines = 6, enabled = state.connected && !state.loading, shape = PixelShape)
            PixelButton("Send", enabled = state.connected && !state.loading && !state.sending && text.isNotBlank(), icon = "send", onClick = ::send)
        }
    }
}

/** Streaming follows the bottom until the reader deliberately scrolls away. */
private data class ReadingPosition(val index: Int, val offset: Int, val scrolling: Boolean, val forward: Boolean, val owned: Boolean)

@Composable internal fun ChatTimeline(rows: List<ChatMessage>, title: String, roomMode: Boolean, loading: Boolean, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    var following by remember { mutableStateOf(true) }
    var ownedScroll by remember { mutableStateOf(false) }
    val measuredTail by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.let { it.index to it.size } } }
    LaunchedEffect(listState) {
        var previous: ReadingPosition? = null
        snapshotFlow { ReadingPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, listState.isScrollInProgress, listState.canScrollForward, ownedScroll) }.collect { position ->
            val before = previous
            val movedBack = before != null && (position.index < before.index || position.index == before.index && position.offset < before.offset)
            if (!position.owned) when {
                !position.forward && !position.scrolling -> following = true
                movedBack || position.scrolling && position.forward -> following = false
                !position.forward -> following = true
            }
            previous = position
        }
    }
    LaunchedEffect(rows.lastOrNull()?.id, rows.size, following, measuredTail) {
        if (following && rows.isNotEmpty()) {
            ownedScroll = true
            try {
                val visible = listState.layoutInfo.visibleItemsInfo.lastOrNull { it.index == rows.lastIndex }
                listState.scrollToItem(rows.lastIndex, visible?.size ?: 0)
                if (listState.canScrollForward) {
                    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull { it.index == rows.lastIndex }
                    listState.scrollToItem(rows.lastIndex, (last?.size ?: 0) + listState.layoutInfo.afterContentPadding)
                }
            } finally { ownedScroll = false }
        }
    }
    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize().testTag("chat.history").padding(horizontal = 24.dp), state = listState,
            contentPadding = PaddingValues(top = 24.dp, bottom = if (following) 24.dp else 64.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (rows.isEmpty() && !loading) item { EmptyHint(if (roomMode) "Bring everyone in." else "A fresh conversation.", if (roomMode) "Use @mentions to give a Pixel the floor." else "Send a task, a question, or a wonderfully odd idea.") }
            items(rows, key = { it.id }) { message ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.role == "user") Alignment.End else Alignment.Start) {
                    Eyebrow(if (message.isTool) "${message.toolName ?: "Tool"} · ${when (message.toolStatus) { ToolStatus.RUNNING -> "Running"; ToolStatus.FAILED -> "Failed"; ToolStatus.DONE -> "Done" }}"
                        else if (message.role == "user") "You" else if (roomMode) message.toolPreview ?: "Pixel" else title,
                        if (message.isTool && message.toolStatus == ToolStatus.FAILED) Ink.Red else Ink.Muted)
                    Spacer(Modifier.height(7.dp))
                    SelectionContainer {
                        Column(Modifier.widthIn(max = 640.dp).clip(PixelShape).background(if (message.role == "user") Ink.Pale else Ink.Panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (message.isTool) {
                                Text(message.toolSummary, color = Ink.Muted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                if (!message.toolResult.isNullOrBlank()) Text(message.toolResult.orEmpty().take(6000), color = Ink.Text, fontSize = 12.sp)
                            } else Text(message.text.ifEmpty { if (message.streaming) "Thinking…" else "" }, color = Ink.Text, fontSize = 14.sp, lineHeight = 22.sp)
                        }
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight().semantics { contentDescription = "Conversation scrollbar" })
        if (!following) PixelButton("Latest messages", Modifier.align(Alignment.BottomCenter).padding(8.dp).testTag("chat.latest"), secondary = true) { following = true }
    }
}
