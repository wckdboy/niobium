package io.sovware.niobium

import androidx.compose.animation.AnimatedVisibility
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp

@Composable
fun ChatScreen(vm: NiobiumViewModel, bot: BotProfile, onOpenTools: () -> Unit = {}) {
    val state = vm.state
    var draft by remember { mutableStateOf("") }
    var showCast by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var showChats by remember { mutableStateOf(false) }
    var showRoutines by remember { mutableStateOf(false) }
    var attachment by remember { mutableStateOf<Pair<String, String>?>(null) }
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            attachment = Base64.encodeToString(bytes, Base64.NO_WRAP) to mime
        }
    }
    val dictation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) draft = if (draft.isBlank()) spoken else "$draft $spoken"
    }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(Modifier.fillMaxSize().background(Nb.Void).statusBarsPadding().imePadding()) {
        ChatHeader(bot, state, onOpenTools = onOpenTools, onOpenMascot = { showCast = true },
            onOpenRoutines = { showRoutines = true }, onOpenModels = { showModels = true },
            onOpenChats = { showChats = true }) { vm.closeBot() }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Nb.Line))
        Box(Modifier.weight(1f)) {
            if (state.messages.isEmpty() && state.loadingChat) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    AtomicMark(56.dp, animated = true)
                    Spacer(Modifier.height(12.dp))
                    Text("Attaching to ${bot.title}…", color = Nb.Secondary, style = NbMono, fontSize = 10.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    if (state.messages.isEmpty() && !state.loadingChat) {
                        item { ChatPreamble(bot, state) }
                        item { StarterPrompts(bot) { vm.send(it) } }
                    }
                    items(state.messages, key = { it.id }) { message ->
                        MessageRow(bot, message)
                    }
                    if (state.turnActive) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(5.dp).clip(CircleShape).background(bot.accent))
                                Spacer(Modifier.width(8.dp))
                                Text(state.turnStatus.ifBlank { "${bot.title} is thinking" }, color = Nb.Tertiary, style = NbMono, fontSize = 9.sp)
                                Spacer(Modifier.width(8.dp))
                                TypingDots()
                            }
                        }
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }
            }
        }
        AnimatedVisibility(visible = state.approvals.isNotEmpty()) {
            state.approvals.firstOrNull()?.let { approval ->
                ApprovalCard(approval) { choice -> vm.resolveApproval(approval, choice) }
            }
        }
        Composer(
            value = draft,
            onValueChange = { draft = it },
            placeholder = "Message ${bot.title}…",
            turnActive = state.turnActive,
            onSend = {
                val text = draft
                val image = attachment
                draft = ""
                attachment = null
                vm.send(text, image?.first, image?.second ?: "image/jpeg")
            },
            onStop = { vm.stopTurn() },
            onAttach = { photoPicker.launch("image/*") },
            onDictate = {
                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak to ${bot.title}")
                }
                runCatching { dictation.launch(intent) }
            },
            hasAttachment = attachment != null,
            onClearAttachment = { attachment = null }
        )
    }

    state.clarify?.let { request ->
        ClarifyDialog(request) { answers -> vm.resolveClarify(answers) }
    }
    if (showChats) {
        ChatsSheet(vm, bot) { showChats = false }
    }
    if (showModels) {
        ModelSheet(vm, bot) { showModels = false }
    }
    if (showRoutines) {
        RoutinesSheet(vm, bot) { showRoutines = false }
    }
    if (showCast) {
        CastStudioSheet(profile = bot.name, title = bot.title) { showCast = false }
    }
}

@Composable
private fun ChatHeader(bot: BotProfile, state: NbState, onOpenTools: () -> Unit,
                       onOpenMascot: () -> Unit, onOpenRoutines: () -> Unit,
                       onOpenModels: () -> Unit, onOpenChats: () -> Unit, onBack: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Row(Modifier.fillMaxWidth().background(Nb.Ink.copy(alpha = 0.92f)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        NbIconButton("‹", onBack, size = 34)
        Spacer(Modifier.width(11.dp))
        BotAvatar(bot.title, bot.accent, 36.dp, bot.isLive, showStatus = false, profileName = bot.name)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bot.title, color = Nb.Text, style = NbTitle, fontSize = 16.sp)
                if (state.demo) {
                    Spacer(Modifier.width(6.dp))
                    NbPill("demo", Nb.Violet)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(4.5.dp).clip(CircleShape).background(if (state.turnActive) Nb.Mint else Nb.Tertiary))
                Spacer(Modifier.width(5.dp))
                Text(if (state.turnActive) "Working" else (bot.model.ifBlank { "Ready" }), color = Nb.Tertiary,
                    style = NbMono, fontSize = 8.5.sp, maxLines = 1,
                    modifier = Modifier.clickable { onOpenModels() })
            }
        }
        Box {
            Text("•••", color = Nb.Secondary, fontSize = 14.sp,
                modifier = Modifier.clickable { menuOpen = true }.padding(8.dp))
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = Nb.Raised
            ) {
                DropdownMenuItem(
                    text = { Text("Toolbox", color = Nb.Text, fontSize = 13.sp) },
                    onClick = { menuOpen = false; onOpenTools() }
                )
                DropdownMenuItem(
                    text = { Text("All chats", color = Nb.Text, fontSize = 13.sp) },
                    onClick = { menuOpen = false; onOpenChats() }
                )
                DropdownMenuItem(
                    text = { Text("Routines", color = Nb.Text, fontSize = 13.sp) },
                    onClick = { menuOpen = false; onOpenRoutines() }
                )
                DropdownMenuItem(
                    text = { Text("Mascot studio", color = Nb.Text, fontSize = 13.sp) },
                    onClick = { menuOpen = false; onOpenMascot() }
                )
                DropdownMenuItem(
                    text = { Text("Copy pixel details", color = Nb.Text, fontSize = 13.sp) },
                    onClick = {
                        menuOpen = false
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString("${bot.title} · ${bot.name}\n${bot.role}"))
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatPreamble(bot: BotProfile, state: NbState) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BotAvatar(bot.title, bot.accent, 60.dp, false, showStatus = false, profileName = bot.name)
        Spacer(Modifier.height(10.dp))
        Text(bot.title, color = Nb.Text, style = NbTitle, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(bot.subtitle, color = Nb.Secondary, fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp))
    }
}

@Composable
private fun StarterPrompts(bot: BotProfile, onSend: (String) -> Unit) {
    val prompts = when (bot.name) {
        "athena" -> listOf("What needs me today?", "Research the competitive landscape", "Turn this into a decision memo")
        "forge" -> listOf("Run a clean build and report blockers", "Review the working tree", "Ship the release checklist")
        "scout" -> listOf("Anything I should know from the overnight sweep?", "Watch our competitors", "Summarize the web")
        else -> listOf("What can you do with your current tools?", "Give me a status update", "Start a research thread")
    }
    Column(Modifier.fillMaxWidth()) {
        Text("START A THREAD", color = Nb.Tertiary, style = NbLabel, fontSize = 9.sp)
        Spacer(Modifier.height(9.dp))
        prompts.forEach { prompt ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 7.dp).clip(RoundedCornerShape(14.dp))
                    .background(Nb.Surface.copy(alpha = 0.65f)).border(1.dp, Nb.Line, RoundedCornerShape(14.dp))
                    .clickable { onSend(prompt) }.padding(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(prompt, color = Nb.Secondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("↗", color = Nb.Ion, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MessageRow(bot: BotProfile, message: ChatMessage) {
    when (message.role) {
        "user" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(message.text, color = Color.White, fontSize = 15.sp, lineHeight = 21.sp,
                modifier = Modifier.widthIn(max = 330.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Nb.Ion, Nb.IonDeep)))
                    .padding(horizontal = 14.dp, vertical = 11.dp))
        }
        "tool" -> ToolCallCard(message)
        "system" -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(Nb.Line))
            Text(message.text, color = Nb.Tertiary, style = NbMono, fontSize = 8.sp, maxLines = 2,
                modifier = Modifier.padding(horizontal = 9.dp).widthIn(max = 280.dp))
            Box(Modifier.weight(1f).height(1.dp).background(Nb.Line))
        }
        else -> Column(Modifier.fillMaxWidth()) {
            message.reasoning?.takeIf { it.isNotBlank() }?.let { reasoning ->
                var open by remember { mutableStateOf(false) }
                Column(Modifier.padding(bottom = 7.dp)) {
                    Text(if (open) "⌃ Hide reasoning" else "✦ Reasoning", color = Nb.Violet.copy(alpha = 0.9f),
                        style = NbMono, fontSize = 9.sp, modifier = Modifier.clickable { open = !open })
                    if (open) {
                        Spacer(Modifier.height(5.dp))
                        Text(reasoning, color = Nb.Secondary, fontSize = 12.5.sp, lineHeight = 18.sp,
                            modifier = Modifier.padding(start = 10.dp).border(width = 0.dp, color = Color.Transparent))
                    }
                }
            }
            if (message.text.isBlank() && message.streaming) {
                TypingDots()
            } else {
                Markdown(message.text)
                if (message.streaming) {
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(7.dp).height(15.dp).background(bot.accent.copy(alpha = 0.8f)))
                }
            }
        }
    }
}

@Composable
private fun ApprovalCard(approval: Approval, onChoice: (String) -> Unit) {
    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Nb.Raised).border(1.dp, Nb.Amber.copy(alpha = 0.32f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✋ APPROVAL REQUIRED", color = Nb.Amber, style = NbMono, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
            Spacer(Modifier.weight(1f))
            Text(approval.toolName, color = Nb.Tertiary, style = NbMono, fontSize = 8.sp)
        }
        Spacer(Modifier.height(9.dp))
        Text(approval.command.ifBlank { approval.description }, color = Nb.Text, style = NbMono, fontSize = 11.5.sp,
            lineHeight = 16.sp, maxLines = 4,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Nb.Shade).padding(10.dp))
        if (approval.description.isNotBlank() && approval.description != approval.command) {
            Spacer(Modifier.height(7.dp))
            Text(approval.description, color = Nb.Secondary, fontSize = 11.sp, maxLines = 2)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            if ("once" in approval.choices) ChoiceButton("Allow once", Nb.Amber, true) { onChoice("once") }
            if ("session" in approval.choices) ChoiceButton("Session", Nb.Secondary, false) { onChoice("session") }
            if ("always" in approval.choices) ChoiceButton("Always", Nb.Secondary, false) { onChoice("always") }
            Spacer(Modifier.weight(1f))
            Text("Deny", color = Nb.Rose, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onChoice("deny") }.padding(horizontal = 6.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun ChoiceButton(label: String, tint: Color, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape)
            .background(if (filled) tint else Nb.Fill)
            .border(1.dp, if (filled) Color.Transparent else Nb.Line, CircleShape)
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (filled) Color(0xFF0A0D12) else Nb.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    turnActive: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    accent: Color = Nb.Ion,
    onAttach: () -> Unit = {},
    onDictate: () -> Unit = {},
    hasAttachment: Boolean = false,
    onClearAttachment: () -> Unit = {}
) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp).padding(bottom = 8.dp, top = 4.dp)) {
        if (hasAttachment) {
            Row(Modifier.padding(bottom = 6.dp).clip(CircleShape).background(Nb.Ion.copy(alpha = 0.10f))
                .clickable { onClearAttachment() }.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("▣ photo attached", color = Nb.Ion, fontSize = 10.sp)
                Spacer(Modifier.width(7.dp))
                Text("×", color = Nb.Tertiary, fontSize = 11.sp)
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Nb.Raised.copy(alpha = 0.96f))
                .border(1.dp, Nb.Line, RoundedCornerShape(22.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Nb.Fill)
                .border(1.dp, Nb.Line, CircleShape).clickable { onAttach() }, contentAlignment = Alignment.Center) {
                Text("+", color = Nb.Secondary, fontSize = 17.sp)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = value, onValueChange = onValueChange,
                placeholder = { Text(placeholder, color = Nb.Tertiary, fontSize = 15.sp) },
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(color = Nb.Text, fontSize = 15.sp),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                shape = RoundedCornerShape(0.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    cursorColor = accent
                )
            )
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(33.dp).clip(CircleShape).clickable { onDictate() }, contentAlignment = Alignment.Center) {
                Text("◉", color = Nb.Tertiary, fontSize = 14.sp)
            }
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.size(33.dp).clip(CircleShape)
                    .background(if (turnActive) Nb.Text else if (value.isNotBlank()) accent else Nb.Fill)
                    .clickable { if (turnActive) onStop() else onSend() },
                contentAlignment = Alignment.Center
            ) {
                if (turnActive) Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Nb.Void))
                else Text("↑", color = if (value.isNotBlank()) Color(0xFF090C13) else Nb.Tertiary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun GroupChatScreen(vm: NiobiumViewModel, room: GroupRoom) {
    val state = vm.state
    var draft by remember { mutableStateOf("") }
    var showCast by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var showChats by remember { mutableStateOf(false) }
    var showRoutines by remember { mutableStateOf(false) }
    var attachment by remember { mutableStateOf<Pair<String, String>?>(null) }
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            attachment = Base64.encodeToString(bytes, Base64.NO_WRAP) to mime
        }
    }
    val dictation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) draft = if (draft.isBlank()) spoken else "$draft $spoken"
    }
    val listState = rememberLazyListState()
    LaunchedEffect(state.roomEvents.size) {
        if (state.roomEvents.isNotEmpty()) listState.animateScrollToItem(state.roomEvents.size + 1)
    }

    Column(Modifier.fillMaxSize().background(Nb.Void).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().background(Nb.Ink.copy(alpha = 0.92f)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            NbIconButton("‹", { vm.closeGroup() }, size = 34)
            Spacer(Modifier.width(11.dp))
            GroupAvatar(room.members, 39)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(room.name, color = Nb.Text, style = NbTitle, fontSize = 16.sp)
                Text("${room.members.size} PIXELS · ROOM", color = Nb.Tertiary, style = NbMono, fontSize = 8.sp, letterSpacing = 0.8.sp)
            }
            NbIconButton("↻", { vm.refreshGroup() }, size = 34)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Nb.Line))
        LazyColumn(
            state = listState, modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    GroupAvatar(room.members, 58)
                    Spacer(Modifier.height(9.dp))
                    Text(room.name, color = Nb.Text, style = NbTitle, fontSize = 19.sp)
                    Text(room.members.joinToString(", ") { it.name }, color = Nb.Tertiary, style = NbMono, fontSize = 8.sp)
                    Spacer(Modifier.height(12.dp))
                }
            }
            items(state.roomEvents, key = { it.id }) { event ->
                when {
                    event.isMessage -> GroupEventRow(event, room)
                    event.isNotice -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(1.dp).background(Nb.Line))
                        Text(event.reason ?: event.kind.replace("_", " "), color = Nb.Tertiary, style = NbMono, fontSize = 8.sp,
                            modifier = Modifier.padding(horizontal = 9.dp))
                        Box(Modifier.weight(1f).height(1.dp).background(Nb.Line))
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
        Composer(draft, { draft = it }, "Message the room…", false,
            onSend = { val t = draft; draft = ""; vm.sendGroup(t) }, onStop = {}, accent = Nb.Violet)
    }
}

@Composable
private fun GroupEventRow(event: RoomEvent, room: GroupRoom) {
    val isUser = event.actorKind == "user" || event.actorId == "user"
    val member = room.members.firstOrNull { it.id == event.actorId || it.profile == event.actorId }
    val accent = Nb.colorFor(event.actorId)
    if (isUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 330.dp)) {
                Text("You · ${event.timestamp?.let { clockTime(it * 1000) } ?: ""}", color = Nb.Secondary, style = NbMono, fontSize = 8.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFAFC3FF), Nb.Ion)))
                        .padding(horizontal = 13.dp, vertical = 10.dp)
                ) {
                    Markdown(event.text, Color(0xFF0A0D14), 14)
                }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            PixelTile(accent = accent, size = 26.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.widthIn(max = 330.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(member?.name ?: event.displayName, color = accent, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text(event.timestamp?.let { clockTime(it * 1000) } ?: "", color = Nb.Tertiary, style = NbMono, fontSize = 7.5.sp)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Nb.Surface.copy(alpha = 0.8f))
                    .border(1.dp, accent.copy(alpha = 0.14f), RoundedCornerShape(16.dp)).padding(horizontal = 13.dp, vertical = 10.dp)) {
                    Markdown(event.text, Nb.Text, 14)
                }
            }
        }
    }
}

@Composable
private fun ClarifyDialog(request: ClarifyRequest, onSubmit: (Map<String, String>) -> Unit) {
    val answers = remember { mutableStateMapOf<String, String>() }
    val complete = request.questions.all { answers[it.id]?.isNotBlank() == true }
    AlertDialog(
        onDismissRequest = { onSubmit(emptyMap()) },
        title = { Text("A quick question", color = Nb.Text, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                request.questions.forEach { question ->
                    Text(question.question, color = Nb.Text, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    if (question.choices.isEmpty()) {
                        OutlinedTextField(
                            value = answers[question.id] ?: "",
                            onValueChange = { answers[question.id] = it },
                            placeholder = { Text("Your answer", color = Nb.Tertiary, fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Nb.Text, fontSize = 13.sp),
                            minLines = 2,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Nb.Surface, unfocusedContainerColor = Nb.Surface,
                                focusedBorderColor = Nb.Ion.copy(alpha = 0.4f), unfocusedBorderColor = Nb.Line,
                                cursorColor = Nb.Ion
                            )
                        )
                    } else {
                        question.choices.forEach { choice ->
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 5.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (answers[question.id] == choice) Nb.Ion.copy(alpha = 0.12f) else Nb.Surface)
                                    .clickable { answers[question.id] = choice }.padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(choice, color = Nb.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text(if (answers[question.id] == choice) "●" else "○",
                                    color = if (answers[question.id] == choice) Nb.Ion else Nb.Tertiary)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        },
        confirmButton = {
            Text("Send", color = if (complete) Nb.Ion else Nb.Tertiary,
                modifier = Modifier.clickable(enabled = complete) {
                    onSubmit(answers.toMap())
                }.padding(10.dp))
        },
        dismissButton = {
            Text("Cancel", color = Nb.Secondary, modifier = Modifier.clickable { onSubmit(emptyMap()) }.padding(10.dp))
        },
        containerColor = Nb.Raised
    )
}
