package io.sovware.niobium

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
            Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.045f)).border(1.dp, Nb.Line, CircleShape)
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
fun PixelsScreen(vm: NiobiumViewModel, state: NbState, onNewPixel: () -> Unit) {
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
                            BotAvatar(pixel.title, pixel.accent, 54.dp, true)
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
fun RoomsScreen(vm: NiobiumViewModel, state: NbState, onNewGroup: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp)) {
        item { TopBar(state, trailing = { NbIconButton("+", onNewGroup, tint = Nb.Ion, size = 38) }) }
        item {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 28.dp)) {
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
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.028f))
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
            Text("Rooms run on your gateway, not the phone. They keep thinking when the app is closed.",
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
                            .background(Brush.linearGradient(listOf(Nb.Ion.copy(alpha = 0.14f), Color.White.copy(alpha = 0.025f))))
                            .border(1.dp, Nb.Ion.copy(alpha = 0.22f), RoundedCornerShape(17.dp))
                            .clickable { vm.openBot(pixel) }.padding(13.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        BotAvatar(pixel.title, pixel.accent, 42.dp, true)
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
                    .background(Color.White.copy(alpha = 0.025f)).border(1.dp, Nb.Line, RoundedCornerShape(18.dp))
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
                    .background(if (toolset.enabled) Nb.Ion else Color.White.copy(alpha = 0.10f))
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
                        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.04f)).padding(horizontal = 7.dp, vertical = 3.dp))
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
    var confirmForget by remember { mutableStateOf(false) }
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
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.045f))
                        .clickable { if (state.demo) vm.leaveDemoToConnect() else vm.reconnect() }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center) {
                        Text(if (state.demo) "Connect a gateway" else "Reconnect", color = Nb.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    if (state.demo) {
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.03f))
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
                    Text("v1.0 (1) · Nb 41", color = Nb.Tertiary, style = NbMono, fontSize = 8.5.sp)
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
