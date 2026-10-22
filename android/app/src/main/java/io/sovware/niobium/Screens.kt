package io.sovware.niobium

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun TopBar(state: NbState, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp).padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Wordmark()
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(CircleShape).background(Nb.FillSoft).border(1.dp, Nb.Line, CircleShape)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiveDot(if (state.connected) Nb.Mint else Nb.Tertiary)
            Spacer(Modifier.width(7.dp))
            Text(state.connection?.name ?: "Offline", color = Nb.Secondary, style = NbMono, fontSize = 9.5.sp, maxLines = 1)
        }
        trailing?.let { Spacer(Modifier.width(8.dp)); it() }
    }
}

@Composable
fun PixelsScreen(vm: NiobiumViewModel, state: NbState, onNewPixel: () -> Unit, onBuildAI: () -> Unit = {}, onOpenRooms: () -> Unit = {}) {
    val active = state.bots.filter { it.isLive }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp)) {
        item { TopBar(state) }
        item {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pixels", color = Nb.Text, style = NbDisplay, fontSize = 44.sp, letterSpacing = (-1.8).sp)
                        Spacer(Modifier.height(5.dp))
                        Text("Your agents, close at hand.", color = Nb.Secondary, fontSize = 14.sp)
                    }
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Nb.FillSoft)
                        .border(1.dp, Nb.Line, CircleShape).clickable { onOpenRooms() }, contentAlignment = Alignment.Center) {
                        Text("◎", color = Nb.Secondary, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(7.dp))
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Nb.FillSoft)
                        .border(1.dp, Nb.Line, CircleShape).clickable { onBuildAI() }, contentAlignment = Alignment.Center) {
                        Text("✦", color = Nb.Ion, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(7.dp))
                    NbIconButton("+", onNewPixel, tint = Nb.Ion, size = 42)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("▦", color = Nb.Ion, fontSize = 11.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("${state.bots.size} pixels", color = Nb.Secondary, style = NbMono, fontSize = 10.5.sp)
                    if (active.isNotEmpty()) {
                        Spacer(Modifier.width(14.dp))
                        Box(Modifier.size(5.dp).clip(CircleShape).background(Nb.Mint))
                        Spacer(Modifier.width(5.dp))
                        Text("${active.size} here now", color = Nb.Secondary, style = NbMono, fontSize = 10.5.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    if (state.refreshing) Text("syncing", color = Nb.Tertiary, fontSize = 10.sp)
                }
            }
        }
        if (!state.connected && state.connection != null) {
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp).padding(top = 14.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)).background(Nb.Rose.copy(alpha = 0.08f))
                        .border(1.dp, Nb.Rose.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                        .clickable { vm.reconnect() }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("↻", color = Nb.Text, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Reconnect to ${state.connection?.name ?: "gateway"}", color = Nb.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Text("offline", color = Nb.Rose, fontSize = 10.sp)
                }
            }
        }
        if (active.isNotEmpty()) {
            item {
                LazyRow(
                    Modifier.fillMaxWidth().padding(top = 24.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(active, key = { it.id }) { pixel ->
                        Column(
                            Modifier.width(68.dp).clickable { vm.openBot(pixel) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BotAvatar(pixel.title, pixel.accent, 54.dp, true, profileName = pixel.name)
                            Spacer(Modifier.height(7.dp))
                            Text(pixel.title, color = Nb.Secondary, fontSize = 10.5.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(30.dp))
            SectionHeader("All pixels", "${state.bots.size}", "New pixel", onNewPixel, Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(10.dp))
        }
        if (state.bots.isEmpty()) {
            item {
                EmptyState("▦", "Make your first pixel",
                    "A pixel is a Hermes profile with its own memory, model and tools. Give it a name and it stays with you.",
                    "Create a pixel", onNewPixel, Modifier.padding(horizontal = 20.dp))
            }
        } else {
            items(state.bots, key = { it.id }) { pixel ->
                Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 7.dp)) {
                    BotRow(pixel) { vm.openBot(pixel) }
                }
            }
        }
        item {
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AtomicMark(15.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.demo) "Demo workspace" else "Connected to ${state.connection?.name ?: "your gateway"}",
                    color = Nb.Tertiary.copy(alpha = 0.75f), fontSize = 10.5.sp
                )
            }
        }
    }
}

@Composable
fun RoomsScreen(vm: NiobiumViewModel, state: NbState, onNewGroup: () -> Unit, onClose: () -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp)) {
        item { TopBar(state, trailing = { NbIconButton("+", onNewGroup, tint = Nb.Ion, size = 38) }) }
        item {
            Text("‹ Home", color = Nb.Secondary, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp).clickable { onClose() })
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 18.dp)) {
                Text("Rooms", color = Nb.Text, style = NbDisplay, fontSize = 44.sp, letterSpacing = (-1.8).sp)
                Spacer(Modifier.height(5.dp))
                Text("A few pixels, thinking together.", color = Nb.Secondary, fontSize = 14.sp)
            }
        }
        if (state.groups.isEmpty()) {
            item {
                EmptyState("◎", "No rooms yet",
                    "Put two to six pixels in a room and let them work a problem together.",
                    "Create a room", onNewGroup, Modifier.padding(horizontal = 20.dp))
            }
        } else {
            items(state.groups, key = { it.id }) { room ->
                Box(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp)) { GroupRow(room) { vm.openGroup(room) } }
            }
        }
        item {
            Box(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Nb.FillSoft)
                        .border(1.dp, Nb.Ion.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                        .clickable { onNewGroup() }.padding(15.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("+", color = Nb.Ion, fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("New room", color = Nb.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Text("2–6 pixels", color = Nb.Tertiary, fontSize = 11.sp)
                }
            }
        }
        item {
            Text(if (state.demo) "Demo rooms show how Pixels collaborate. Connect your gateway for real conversations." else if (state.groupsHaveDriver) "Your gateway runs these rooms. They can continue when the app disconnects." else "Reopen a room to sync its latest messages. Background discussion depends on your gateway.",
                color = Nb.Tertiary, fontSize = 11.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 22.dp).padding(top = 20.dp))
        }
    }
}

@Composable
fun ActivityScreen(vm: NiobiumViewModel, state: NbState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp)) {
        item { TopBar(state) }
        item {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 28.dp)) {
                Text("Activity", color = Nb.Text, style = NbDisplay, fontSize = 44.sp, letterSpacing = (-1.8).sp)
                Spacer(Modifier.height(5.dp))
                Text("What your pixels are doing.", color = Nb.Secondary, fontSize = 14.sp)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    ActivityStat("${state.bots.count { it.isLive }}", "working", Nb.Mint)
                    ActivityStat("${state.approvals.size}", "approvals", Nb.Amber)
                    ActivityStat("${state.bots.size}", "pixels", Nb.Ion)
                }
            }
        }
        state.bots.firstOrNull { it.isLive }?.let { pixel ->
            item {
                Box(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                            .background(Brush.linearGradient(listOf(Nb.Ion.copy(alpha = 0.14f), Nb.FillSoft)))
                            .border(1.dp, Nb.Ion.copy(alpha = 0.22f), RoundedCornerShape(17.dp))
                            .clickable { vm.openBot(pixel) }.padding(13.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        BotAvatar(pixel.title, pixel.accent, 42.dp, true, profileName = pixel.name)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pixel.title, color = Nb.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(pixel.preview.ifBlank { "Working on a turn" }, color = Nb.Secondary, fontSize = 12.sp, maxLines = 1)
                        }
                        TypingDots()
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(28.dp))
            SectionHeader("Recent", "${state.activities.size}", modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(9.dp))
            Column(
                Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp))
                    .background(Nb.FillSoft).border(1.dp, Nb.Line, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp)
            ) {
                if (state.activities.isEmpty()) {
                    EmptyState("⌁", "Quiet right now", "Tool calls, approvals and pixel replies will show up here.")
                } else {
                    state.activities.take(30).forEachIndexed { index, item ->
                        ActivityRow(item)
                        if (index < minOf(29, state.activities.size - 1)) Box(Modifier.fillMaxWidth().height(1.dp).background(Nb.Line))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityStat(value: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(value, color = Nb.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Text(label, color = Nb.Tertiary, fontSize = 11.5.sp)
    }
}

@Composable
private fun ActivityRow(item: ActivityItem) {
    val color = when (item.kind) {
        "tool" -> Nb.Mint
        "message" -> Nb.Ion
        "approval" -> Nb.Amber
        "bot" -> Nb.Violet
        else -> Nb.Tertiary
    }
    val glyph = when (item.kind) {
        "tool" -> "⚒"
        "message" -> "●"
        "approval" -> "✋"
        "bot" -> "▦"
        else -> "·"
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Text(glyph, color = color, fontSize = 11.sp)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(item.title, color = Nb.Text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(formatRelative(if (item.date < 10_000_000_000L) item.date else item.date / 1000), color = Nb.Tertiary, fontSize = 10.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(item.detail, color = Nb.Secondary, fontSize = 11.5.sp, maxLines = 2)
        }
    }
}

@Composable
fun ToolsScreen(vm: NiobiumViewModel, state: NbState, onClose: (() -> Unit)? = null) {
    var query by remember { mutableStateOf("") }
    var showSkills by remember { mutableStateOf(false) }
    var showMCP by remember { mutableStateOf(false) }
    if (showSkills) {
        SkillsSheet(vm) { showSkills = false }
        return
    }
    if (showMCP) {
        McpSheet(vm) { showMCP = false }
        return
    }
    var onlyEnabled by remember { mutableStateOf(false) }
    val filtered = state.toolsets.filter {
        (!onlyEnabled || it.enabled) && (query.isBlank() || it.name.contains(query, true)
            || it.description.contains(query, true) || it.tools.any { t -> t.contains(query, true) })
    }
    val totalTools = state.toolsets.sumOf { it.tools.size }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onClose != null) {
                    NbIconButton("‹", onClose, size = 36)
                    Spacer(Modifier.width(10.dp))
                }
                Wordmark()
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Nb.FillSoft)
                    .border(1.dp, Nb.Line, RoundedCornerShape(10.dp)).clickable { showSkills = true },
                    contentAlignment = Alignment.Center) { Text("✦", color = Nb.Ion, fontSize = 13.sp) }
                Spacer(Modifier.width(5.dp))
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Nb.FillSoft)
                    .border(1.dp, Nb.Line, RoundedCornerShape(10.dp)).clickable { showMCP = true },
                    contentAlignment = Alignment.Center) { Text("◈", color = Nb.Violet, fontSize = 13.sp) }
                Spacer(Modifier.width(7.dp))
                NbIconButton("↻", { vm.refreshAllAsync() }, size = 38)
            }
        }
        item { PageHeader("Everything the agent can touch", "Toolbox") }
        item {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 10.dp)) {
                Text("$totalTools", color = Nb.Text, style = NbDisplay, fontSize = 52.sp)
                Text("tools across ${state.toolsets.size} toolsets on this gateway", color = Nb.Secondary, fontSize = 12.5.sp)
            }
        }
        if (!state.demo && state.sessionId == null) {
            item {
                Text("Open a pixel chat to enable or disable a toolset for that live session.",
                    color = Nb.Amber.copy(alpha = 0.9f), fontSize = 11.sp, lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp)).background(Nb.Amber.copy(alpha = 0.07f)).padding(11.dp))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search the toolbox", color = Nb.Tertiary, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Nb.Text, fontSize = 13.sp),
                    shape = RoundedCornerShape(11.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Nb.Surface, unfocusedContainerColor = Nb.Surface,
                        focusedBorderColor = Nb.Ion.copy(alpha = 0.4f), unfocusedBorderColor = Nb.Line,
                        cursorColor = Nb.Ion
                    )
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)).background(Nb.Surface)
                        .border(1.dp, if (onlyEnabled) Nb.Ion.copy(alpha = 0.4f) else Nb.Line, RoundedCornerShape(11.dp))
                        .clickable { onlyEnabled = !onlyEnabled }, contentAlignment = Alignment.Center
                ) { Text("≡", color = if (onlyEnabled) Nb.Ion else Nb.Tertiary, fontSize = 17.sp) }
            }
        }
        items(filtered, key = { it.id }) { toolset ->
            Box(Modifier.padding(horizontal = 20.dp).padding(top = 9.dp)) {
                ToolsetRow(toolset) { vm.toggleToolset(toolset) }
            }
        }
    }
}

@Composable
private fun ToolsetRow(toolset: Toolset, onToggle: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .background(if (toolset.enabled) Nb.Surface.copy(alpha = 0.8f) else Nb.Surface.copy(alpha = 0.4f))
            .border(1.dp, if (toolset.enabled) Nb.Ion.copy(alpha = 0.14f) else Nb.Line, RoundedCornerShape(17.dp))
            .padding(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                .background((if (toolset.enabled) Nb.Ion else Nb.Tertiary).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Text(toolGlyph(toolset.name), color = if (toolset.enabled) Nb.Ion else Nb.Tertiary, fontSize = 13.sp)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(toolset.name, color = Nb.Text, style = NbMono, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text("${toolset.tools.size}", color = Nb.Tertiary, style = NbMono, fontSize = 8.sp)
                }
                Spacer(Modifier.height(2.dp))
                Text(toolset.description, color = Nb.Secondary, fontSize = 11.5.sp, lineHeight = 15.sp, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.width(38.dp).height(22.dp).clip(CircleShape)
                    .background(if (toolset.enabled) Nb.Ion else Nb.FillStrong)
                    .clickable { onToggle() },
                contentAlignment = if (toolset.enabled) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(Modifier.padding(horizontal = 2.5.dp).size(17.dp).clip(CircleShape)
                    .background(if (toolset.enabled) Color(0xFF0A0D14) else Nb.Tertiary))
            }
        }
        if (toolset.tools.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            Row(Modifier.padding(start = 47.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                toolset.tools.take(4).forEach {
                    Text(it, color = if (toolset.enabled) Nb.Secondary else Nb.Tertiary, style = NbMono, fontSize = 7.5.sp,
                        modifier = Modifier.clip(CircleShape).background(Nb.FillSoft).padding(horizontal = 7.dp, vertical = 3.dp))
                }
                if (toolset.tools.size > 4) {
                    Text("+${toolset.tools.size - 4}", color = Nb.Tertiary, style = NbMono, fontSize = 7.5.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp))
                }
            }
        }
    }
}

private fun toolGlyph(name: String): String = when (name) {
    "terminal", "coding" -> ">_"
    "web", "browser" -> "◎"
    "memory", "skills" -> "✦"
    "cronjob" -> "◷"
    "delegation" -> "⑃"
    "vision" -> "◉"
    "image_gen" -> "✧"
    "bot_room" -> "◎"
    else -> "⚒"
}

@Composable
fun SettingsScreen(vm: NiobiumViewModel, state: NbState, onOpenTools: () -> Unit) {
    val context = LocalContext.current
    var confirmForget by remember { mutableStateOf(false) }
    var showHarness by remember { mutableStateOf(false) }
    if (confirmForget) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget this gateway?", color = Nb.Text) },
            text = { Text("The access token is removed from the Android Keystore. Your Hermes instance is not changed.", color = Nb.Secondary, fontSize = 13.sp) },
            confirmButton = {
                Text("Forget", color = Nb.Rose, modifier = Modifier.clickable { confirmForget = false; vm.disconnect(forget = true) }.padding(10.dp))
            },
            dismissButton = { Text("Cancel", color = Nb.Secondary, modifier = Modifier.clickable { confirmForget = false }.padding(10.dp)) },
            containerColor = Nb.Raised
        )
    }
    if (showHarness) {
        RemoteHarnessScreen { showHarness = false }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 36.dp)) {
        item { TopBar(state) }
        item { PageHeader("Niobium · element 41", "Settings") }
        item {
            Spacer(Modifier.height(22.dp))
            SectionHeader("Connection", state.connection?.let { if (state.connected) "CONNECTED" else "OFFLINE" }, modifier = Modifier.padding(horizontal = 20.dp))
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp).clip(RoundedCornerShape(18.dp))
                .background(Nb.Surface.copy(alpha = 0.65f)).border(1.dp, Nb.Line, RoundedCornerShape(18.dp))) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Nb.Ion.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Text(if (state.demo) "✦" else "⌁", color = if (state.connected) Nb.Mint else Nb.Rose, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.connection?.name ?: "No gateway", color = Nb.Text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (state.demo) "Demo workspace · local" else state.connection?.url ?: "—",
                            color = Nb.Tertiary, style = NbMono, fontSize = 9.sp, maxLines = 1)
                    }
                    LiveDot(if (state.connected) Nb.Mint else Nb.Rose)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Nb.Line))
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(Nb.FillSoft)
                        .clickable { if (state.demo) vm.leaveDemoToConnect() else vm.reconnect() }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center) {
                        Text(if (state.demo) "Connect a gateway" else "Reconnect", color = Nb.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    if (state.demo) {
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(Nb.FillSoft)
                            .clickable { vm.enterDemo() }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text("Reset demo", color = Nb.Secondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(26.dp))
            SectionHeader("Toolbox", modifier = Modifier.padding(horizontal = 20.dp))
            Row(
                Modifier.padding(horizontal = 20.dp).padding(top = 12.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp)).background(Nb.Surface.copy(alpha = 0.65f))
                    .border(1.dp, Nb.Line, RoundedCornerShape(18.dp)).clickable { onOpenTools() }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Nb.Ion.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                    Text("⚒", color = Nb.Ion, fontSize = 13.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Every toolset", color = Nb.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Inspect and toggle the tools available to your pixels", color = Nb.Tertiary, fontSize = 10.5.sp)
                }
                Text("›", color = Nb.Tertiary, fontSize = 16.sp)
            }
        }
        item {
            Spacer(Modifier.height(26.dp))
            SectionHeader("Harness", modifier = Modifier.padding(horizontal = 20.dp))
            Row(
                Modifier.padding(horizontal = 20.dp).padding(top = 12.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp)).background(Nb.Surface.copy(alpha = 0.65f))
                    .border(1.dp, Nb.Line, RoundedCornerShape(18.dp)).clickable { showHarness = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Nb.Ion.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                    Text(">_", color = Nb.Ion, style = NbMono, fontSize = 11.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Remote DeepSeek Harness", color = Nb.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Open DSH on your computer or VPS", color = Nb.Tertiary, fontSize = 10.5.sp)
                }
                Text("›", color = Nb.Tertiary, fontSize = 16.sp)
            }
        }
        item {
            Spacer(Modifier.height(26.dp))
            SectionHeader("Experience", modifier = Modifier.padding(horizontal = 20.dp))
            Column(
                Modifier.padding(horizontal = 20.dp).padding(top = 12.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp)).background(Nb.Surface.copy(alpha = 0.65f))
                    .border(1.dp, Nb.Line, RoundedCornerShape(18.dp)).padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("◐", color = Nb.Violet, fontSize = 13.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Appearance", color = Nb.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("System, light or dark", color = Nb.Tertiary, fontSize = 10.5.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                        val active = Nb.appearance == key
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (active) Nb.Ion.copy(alpha = 0.14f) else Nb.FillSoft)
                                .border(1.dp, if (active) Nb.Ion.copy(alpha = 0.35f) else Nb.Line, RoundedCornerShape(10.dp))
                                .clickable {
                                    Nb.appearance = key
                                    context.getSharedPreferences("niobium", android.content.Context.MODE_PRIVATE)
                                        .edit().putString("niobium.appearance", key).apply()
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (active) Nb.Ion else Nb.Secondary, fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(26.dp))
            SectionHeader("Privacy", modifier = Modifier.padding(horizontal = 20.dp))
            Row(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Nb.Surface.copy(alpha = 0.65f)).border(1.dp, Nb.Line, RoundedCornerShape(18.dp)).padding(14.dp),
                verticalAlignment = Alignment.Top) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Nb.Mint.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Text("▣", color = Nb.Mint, fontSize = 12.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Your gateway. No middleman.", color = Nb.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Messages stream directly from this device to the Hermes instance you entered. Niobium has no account, no backend, no analytics and no telemetry.",
                        color = Nb.Secondary, fontSize = 11.5.sp, lineHeight = 17.sp)
                }
            }
        }
        item {
            Spacer(Modifier.height(26.dp))
            SectionHeader("About", modifier = Modifier.padding(horizontal = 20.dp))
            Row(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Nb.Surface.copy(alpha = 0.65f)).border(1.dp, Nb.Line, RoundedCornerShape(18.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                AtomicMark(54.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Niobium", color = Nb.Text, style = NbTitle, fontSize = 18.sp)
                    Text("The pocket command deck for Hermes Agent.", color = Nb.Secondary, fontSize = 11.5.sp)
                    Text("v2.0 (4) · Nb 41", color = Nb.Tertiary, style = NbMono, fontSize = 8.5.sp)
                }
            }
        }
        item {
            Box(Modifier.padding(horizontal = 20.dp).padding(top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(13.dp))
                .background(Nb.Rose.copy(alpha = 0.06f)).border(1.dp, Nb.Rose.copy(alpha = 0.15f), RoundedCornerShape(13.dp))
                .clickable { confirmForget = true }.padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                Text("Forget this gateway", color = Nb.Rose, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun HomeScreen(vm: NiobiumViewModel, state: NbState, onNewPixel: () -> Unit, onShowPixels: () -> Unit) {
    val context = LocalContext.current
    var showHarness by remember { mutableStateOf(false) }
    var showInstance by remember { mutableStateOf(false) }
    if (showHarness) { RemoteHarnessScreen { showHarness = false }; return }
    if (showInstance) {
        androidx.compose.material3.AlertDialog(onDismissRequest = { showInstance = false }, containerColor = Nb.Ink,
            title = { Text("Hermes instance", color = Nb.Text) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.connection?.url ?: "No gateway connected", color = Nb.Secondary, style = NbMono, fontSize = 12.sp)
                Text("Access is enforced by your Hermes host.", color = Nb.Secondary, fontSize = 12.sp)
                state.gatewayLatencyMs?.let { Text("Last round trip: " + it + " ms", color = Nb.Secondary, fontSize = 12.sp) }
                androidx.compose.material3.TextButton(onClick = { vm.refreshAllAsync() }) { Text("Refresh gateway", color = Nb.Ion) }
            } }, confirmButton = { androidx.compose.material3.TextButton(onClick = { showInstance = false }) { Text("Done", color = Nb.Ion) } })
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(152.dp)) {
                Image(painterResource(R.drawable.terrace_hero), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                    Nb.Void.copy(alpha = 0.90f), Nb.Void.copy(alpha = 0.18f), androidx.compose.ui.graphics.Color.Transparent, Nb.Void))))
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp).padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AtomicMark(36.dp)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Niobium", color = Nb.Text, style = NbDisplay, fontSize = 24.sp)
                        Text("YOUR PIXELS, EVERYWHERE.", color = Nb.Secondary, style = NbMono, fontSize = 7.sp, letterSpacing = 1.sp)
                    }
                    Row(Modifier.clip(CircleShape).background(Nb.Ink.copy(alpha = 0.8f))
                        .border(1.dp, Nb.Ion.copy(alpha = 0.22f), CircleShape).clickable { showInstance = true }
                        .padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        LiveDot(if (state.connected) Nb.Mint else Nb.Tertiary)
                        Spacer(Modifier.width(5.dp))
                        Text(if (state.demo) "Demo" else if (state.connected) "Connected" else "Offline", color = Nb.Secondary, fontSize = 10.sp)
                        Text(" ›", color = Nb.Tertiary, fontSize = 12.sp)
                    }
                }
            }
        }
        item { Box(Modifier.padding(horizontal = 16.dp).clickable { showInstance = true }) { InstanceCard(state) } }
        item {
            Row(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your Pixels", color = Nb.Text, style = NbTitle, fontSize = 21.sp)
                    Text("A little personality. A lot of purpose.", color = Nb.Tertiary, fontSize = 11.sp)
                }
                androidx.compose.material3.Button(onClick = onNewPixel,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Nb.Ion, contentColor = Nb.OnIon),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                    Text("+ New Pixel", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (state.bots.isEmpty()) item {
            Text("Give your first Pixel a purpose. Create one here or refresh your gateway roster.", color = Nb.Secondary, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp).pixelPanel().padding(16.dp))
        }
        items(state.bots.take(6).chunked(2), key = { row -> row.first().name }) { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { bot -> HomePixelCard(context, bot, Modifier.weight(1f)) { vm.openBot(bot) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (state.bots.size > 6) item {
            Text("All " + state.bots.size + " Pixels  →", color = Nb.Secondary, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { onShowPixels() }.padding(vertical = 4.dp))
        }
        item { Box(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp)) { LocalDSHCard(state) { showHarness = true } } }
    }
}

@Composable
private fun InstanceCard(state: NbState) {
    Column(Modifier.fillMaxWidth().pixelPanel(20.dp, accented = true).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Nb.Ion.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Text("▤", color = Nb.Ion, fontSize = 18.sp)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Hermes Instance", color = Nb.Text, style = NbTitle, fontSize = 16.sp)
                Text(state.connection?.url?.removePrefix("http://")?.removePrefix("https://") ?: "self-hosted",
                    color = Nb.Secondary, style = NbMono, fontSize = 9.sp, maxLines = 1)
                Text(if (state.demo) "Demo workspace" else "Your computer or VPS", color = Nb.Tertiary, style = NbMono, fontSize = 8.5.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(if (state.connected) Nb.Mint else Nb.Tertiary)
                    Spacer(Modifier.width(5.dp))
                    Text(if (state.connected) "Online" else "Offline", color = if (state.connected) Nb.Mint else Nb.Tertiary, fontSize = 10.sp)
                }
                Text(if (state.demo) "preview" else "gateway", color = Nb.Tertiary, style = NbMono, fontSize = 8.5.sp)
                Spacer(Modifier.width(2.dp))
                Text("›", color = Nb.Tertiary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(11.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Nb.Line))
        Spacer(Modifier.height(11.dp))
        Row {
            HomeStat("${state.toolsets.size}", "Toolsets", "◈", Modifier.weight(1f))
            HomeStat(if (state.demo) "Demo" else "Host", "Policy", "◇", Modifier.weight(1f))
            HomeStat(state.gatewayLatencyMs?.let { "$it ms" } ?: "—", "Latency", "⌁", Modifier.weight(1f))
            HomeStat("${state.bots.count { it.isLive }}/${state.bots.size}", "Working", "▦", Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeStat(value: String, label: String, glyph: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(glyph, color = Nb.Ion, fontSize = 12.sp)
            Text(value, color = Nb.Text, style = NbTitle, fontSize = 13.sp)
        }
        Text(label, color = Nb.Tertiary, fontSize = 9.sp)
    }
}

@Composable
private fun HomePixelCard(context: android.content.Context, bot: BotProfile, modifier: Modifier = Modifier, onTap: () -> Unit) {
    val member = CastStore.get(context, bot.name)
    Row(modifier.pixelPanel(16.dp).clickable { onTap() }.padding(8.dp).heightIn(min = 70.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        CastPortrait(member, 64.dp, showCompanion = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(bot.title, color = Nb.Text, style = NbTitle, fontSize = 13.sp, maxLines = 1)
            Text(bot.subtitle, color = Nb.Secondary, fontSize = 10.sp, maxLines = 2, lineHeight = 13.sp)
            Row(Modifier.background((if (bot.isLive) Nb.Mint else Nb.Secondary).copy(alpha = 0.08f), CircleShape)
                .padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                LiveDot(if (bot.isLive) Nb.Mint else Nb.Tertiary)
                Spacer(Modifier.width(4.dp))
                Text(if (bot.isLive) "Working" else "Ready", color = if (bot.isLive) Nb.Mint else Nb.Tertiary, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun LocalDSHCard(state: NbState, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().pixelPanel(20.dp, accented = true).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Remote DSH", color = Nb.Text, style = NbTitle, fontSize = 18.sp)
                Text("Your harness on a computer or VPS.", color = Nb.Tertiary, fontSize = 10.sp)
            }
            Box(Modifier.clip(CircleShape).background(Nb.Ion.copy(alpha = 0.16f))
                .border(1.dp, Nb.Ion.copy(alpha = 0.35f), CircleShape).clickable { onOpen() }
                .padding(horizontal = 11.dp, vertical = 7.dp)) {
                Text(">_ Open DSH", color = Nb.Ion, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nb.FillSoft).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("◈", color = Nb.Ion, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Harness runtime", color = Nb.Text, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                Text("Remote · computer or VPS", color = Nb.Tertiary, style = NbMono, fontSize = 8.sp)
            }
            Text("Open to check", color = Nb.Tertiary, style = NbMono, fontSize = 8.sp)
        }
        Spacer(Modifier.height(9.dp))
        Text("DSH is separate from Hermes. The local guest is iOS-only; remote works everywhere.",
            color = Nb.Tertiary, fontSize = 8.5.sp, lineHeight = 12.sp)
    }
}

@Composable
fun LocalScreen(vm: NiobiumViewModel, state: NbState) {
    var showHarness by remember { mutableStateOf(false) }
    if (showHarness) {
        RemoteHarnessScreen { showHarness = false }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 28.dp, 20.dp, 32.dp)) {
        item {
            Text("Harness · computer · VPS", color = Nb.Tertiary, style = NbLabel, fontSize = 9.sp, letterSpacing = 1.2.sp)
            Text("Local", color = Nb.Text, style = NbDisplay, fontSize = 38.sp)
            Spacer(Modifier.height(6.dp))
            Text("Run a full harness next to your Pixels. The iOS full build embeds Alpine + iSH; both platforms can open DSH on a computer or VPS.",
                color = Nb.Secondary, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().clip(CircleShape).background(Brush.linearGradient(listOf(Nb.Ion, Nb.IonDeep)))
                .clickable { showHarness = true }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                Text(">_ Open DSH", color = Nb.OnIon, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().clip(CircleShape).background(Nb.FillSoft)
                .clickable { showHarness = true }.padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                Text("Connect to remote DSH", color = Nb.Text, fontSize = 13.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text("Pixels available to the harness", color = Nb.Text, style = NbTitle, fontSize = 18.sp)
        }
        items(state.bots.take(4).size) { index ->
            val bot = state.bots[index]
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(Nb.FillSoft)
                .padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CastPortrait(CastStore.get(LocalContext.current, bot.name), 38.dp, showCompanion = false)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(bot.title, color = Nb.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("Hermes pixel · ${bot.model.ifBlank { "inherited model" }}", color = Nb.Tertiary, style = NbMono, fontSize = 8.sp)
                }
                Text(if (bot.isLive) "Running" else "Available", color = if (bot.isLive) Nb.Mint else Nb.Tertiary, style = NbMono, fontSize = 8.sp)
            }
        }
    }
}
