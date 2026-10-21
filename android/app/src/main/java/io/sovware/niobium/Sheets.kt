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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun SheetField(label: String, value: String, placeholder: String, multiline: Boolean = false, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Nb.Tertiary, style = NbMono, fontSize = 8.5.sp, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange,
            placeholder = { Text(placeholder, color = Nb.Tertiary, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(color = Nb.Text, fontSize = 14.sp),
            minLines = if (multiline) 2 else 1,
            maxLines = if (multiline) 4 else 1,
            shape = RoundedCornerShape(11.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Nb.Surface, unfocusedContainerColor = Nb.Surface,
                focusedBorderColor = Nb.Ion.copy(alpha = 0.4f), unfocusedBorderColor = Nb.Line,
                cursorColor = Nb.Ion
            )
        )
    }
}

@Composable
fun NewBotSheet(vm: NiobiumViewModel, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("#A78BFA") }
    val colors = listOf("#A78BFA", "#F0ABFC", "#5EEAD4", "#F5C97A", "#FDA4AF", "#C4B5FD")
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text("New pixel", color = Nb.Text, style = NbDisplay, fontSize = 30.sp)
        Text("A pixel is a Hermes profile with its own memory, model and tools.",
            color = Nb.Secondary, fontSize = 12.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BotAvatar(title.ifBlank { name.ifBlank { "New Pixel" } }, Nb.colorFor(color), 72.dp, false,
                showStatus = false, profileName = name.ifBlank { "niobium" })
            Spacer(Modifier.height(9.dp))
            Text(title.ifBlank { "New Pixel" }, color = Nb.Text, style = NbTitle, fontSize = 20.sp)
            Text(role.ifBlank { "A new specialist on your Hermes team." }, color = Nb.Secondary, fontSize = 11.5.sp)
        }
        Spacer(Modifier.height(18.dp))
        SheetField("PIXEL NAME", name, "athena") { name = it.filter { c -> c.isLetterOrDigit() || c == '-' }.lowercase() }
        Spacer(Modifier.height(12.dp))
        SheetField("DISPLAY TITLE", title, "Athena") { title = it }
        Spacer(Modifier.height(12.dp))
        SheetField("ROLE", role, "Research, strategy and long-horizon thinking.", multiline = true) { role = it }
        Spacer(Modifier.height(16.dp))
        Text("SIGNAL COLOUR", color = Nb.Tertiary, style = NbMono, fontSize = 8.5.sp, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.forEach { hex ->
                Box(
                    Modifier.size(31.dp).clip(CircleShape).background(Nb.colorFor(hex))
                        .border(if (color == hex) 2.dp else 1.dp,
                            if (color == hex) Color.White.copy(alpha = 0.9f) else Nb.LineStrong, CircleShape)
                        .clickable { color = hex }
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.fillMaxWidth().height(50.dp).clip(CircleShape)
                .background(if (name.isBlank()) Nb.Ion.copy(alpha = 0.3f) else Nb.Ion)
                .clickable(enabled = name.isNotBlank()) {
                    vm.createBot(name, title, role, color) { onDone() }
                }, contentAlignment = Alignment.Center
        ) {
            Text("Create pixel", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun NewGroupSheet(vm: NiobiumViewModel, state: NbState, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text("New room", color = Nb.Text, style = NbDisplay, fontSize = 30.sp)
        Text("Pick two to six pixels. They share one visible conversation and can @mention each other.",
            color = Nb.Secondary, fontSize = 12.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(18.dp))
        SheetField("ROOM NAME", name, "Launch Room") { name = it }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PIXELS", color = Nb.Tertiary, style = NbMono, fontSize = 8.5.sp, letterSpacing = 1.2.sp)
            Spacer(Modifier.weight(1f))
            Text("${selected.size} / 6", color = if (selected.size >= 2) Nb.Mint else Nb.Tertiary, style = NbMono, fontSize = 9.sp)
        }
        Spacer(Modifier.height(8.dp))
        state.bots.forEach { bot ->
            val isSelected = bot.name in selected
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) bot.accent.copy(alpha = 0.08f) else Nb.Surface.copy(alpha = 0.65f))
                    .border(1.dp, if (isSelected) bot.accent.copy(alpha = 0.25f) else Nb.Line, RoundedCornerShape(14.dp))
                    .clickable {
                        selected = if (isSelected) selected - bot.name
                        else if (selected.size < 6) selected + bot.name else selected
                    }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BotAvatar(bot.title, bot.accent, 38.dp, false, showStatus = false, profileName = bot.name)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(bot.title, color = Nb.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(bot.role, color = Nb.Secondary, fontSize = 11.sp, maxLines = 1)
                }
                Text(if (isSelected) "●" else "○", color = if (isSelected) bot.accent else Nb.Tertiary, fontSize = 16.sp)
            }
        }
        if (state.bots.size < 2) {
            Text("Create at least two pixels before opening a room.", color = Nb.Amber, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(50.dp).clip(CircleShape)
                .background(if (name.isBlank() || selected.size < 2) Nb.Violet.copy(alpha = 0.3f) else Nb.Violet)
                .clickable(enabled = name.isNotBlank() && selected.size >= 2) {
                    vm.createGroup(name, state.bots.filter { it.name in selected }) { onDone() }
                }, contentAlignment = Alignment.Center
        ) {
            Text("Create room", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
    }
}
