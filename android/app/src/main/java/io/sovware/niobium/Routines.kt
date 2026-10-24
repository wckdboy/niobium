package io.sovware.niobium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesSheet(vm: NiobiumViewModel, bot: BotProfile, onDismiss: () -> Unit) {
    val owner = vm.chatContext
    val state = vm.state
    val load = state.routinesLoad.copy(loading = state.routinesLoad.loading || state.routinesUpdating)
    val canChange = state.canChangeRoutines && state.activeBot?.id == bot.id
    var creating by remember(owner) { mutableStateOf(false) }
    LaunchedEffect(vm, owner, bot.id) { vm.loadRoutines(bot) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nb.Ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Routines", color = Nb.Text, style = NbTitle, fontSize = 26.sp)
                        Text(bot.title, color = Nb.Secondary, fontSize = 12.sp)
                    }
                    NbPill("${state.routines.size}", Nb.Violet)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton("Refresh", PixelGlyph.REFRESH, Modifier.weight(1f), secondary = true, enabled = !load.loading && state.connected) { vm.refreshRoutines(bot) }
                    PixelButton("New", PixelGlyph.SPARK, Modifier.weight(1f), enabled = canChange) { creating = true }
                }
            }
            item { InventoryFeedback(load, state.routines.isEmpty(), "No routines yet. Give your Pixel a rhythm.") { vm.refreshRoutines(bot) } }
            items(state.routines, key = { it.id }) { routine ->
                Column(Modifier.fillMaxWidth().clip(PixelButtonShape).background(Nb.FillSoft)
                    .border(1.dp, Nb.Line, PixelButtonShape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(routine.name, color = Nb.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        NbPill(if (routine.enabled) "On" else "Paused", if (routine.enabled) Nb.Mint else Nb.Tertiary)
                    }
                    Text(routine.prompt, color = Nb.Secondary, fontSize = 13.sp, maxLines = 3)
                    Text(routine.schedule, color = Nb.Secondary, fontSize = 12.sp)
                    if (routine.nextRun.isNotBlank()) Text("Next · ${routine.nextRun}", color = Nb.Tertiary, fontSize = 11.sp)
                    PixelButton("Delete", PixelGlyph.STOP, secondary = true, destructive = true, enabled = canChange) { vm.deleteRoutine(routine, bot, owner) }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
    if (creating) NewRoutineDialog(state.routinesUpdating, state.routinesLoad.error, canChange,
        onDismiss = { creating = false }) { name, schedule, prompt ->
        vm.createRoutine(name, schedule, prompt, bot, owner) { creating = false }
    }
}

@Composable
internal fun NewRoutineDialog(busy: Boolean, error: String?, canCreate: Boolean, onDismiss: () -> Unit, onCreate: (String, String, String) -> Unit) {
    var panelBounds by remember { mutableStateOf<Rect?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Apply insets before constraining the form so its actions remain above the keyboard.
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(20.dp)
            .pointerInput(onDismiss) { detectTapGestures { point -> if (panelBounds?.contains(point) == false) onDismiss() } },
            contentAlignment = Alignment.Center) {
            RoutineEditorPanel(busy, error, canCreate, onDismiss, onCreate,
                Modifier.widthIn(max = 480.dp).onGloballyPositioned { panelBounds = it.boundsInParent() })
        }
    }
}

@Composable
internal fun RoutineEditorPanel(busy: Boolean, error: String?, canCreate: Boolean, onDismiss: () -> Unit,
                                onCreate: (String, String, String) -> Unit, modifier: Modifier = Modifier) {
    var name by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf("Every day at 08:00") }
    var prompt by remember { mutableStateOf("") }
    Column(modifier.fillMaxWidth().clip(PixelButtonShape).background(Nb.Raised).border(1.dp, Nb.Line, PixelButtonShape)
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("New routine", color = Nb.Text, style = NbTitle, fontSize = 26.sp)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it.take(120) }, label = { Text("Name") }, singleLine = true, enabled = !busy,
                shape = PixelButtonShape, modifier = Modifier.fillMaxWidth().testTag("routine.name"))
            OutlinedTextField(schedule, { schedule = it.take(200) }, label = { Text("Schedule") }, singleLine = true, enabled = !busy,
                shape = PixelButtonShape, modifier = Modifier.fillMaxWidth().testTag("routine.schedule"))
            OutlinedTextField(prompt, { prompt = it.take(32_000) }, label = { Text("Prompt") }, minLines = 2, maxLines = 5, enabled = !busy,
                shape = PixelButtonShape, modifier = Modifier.fillMaxWidth().testTag("routine.prompt"))
            error?.let { Text(it, color = Nb.Rose, fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton(if (busy) "Close" else "Cancel", null, Modifier.weight(1f), secondary = true, onClick = onDismiss)
            PixelButton(if (busy) "Creating…" else "Create", null, Modifier.weight(1f).testTag("routine.create"),
                enabled = canCreate && name.isNotBlank() && schedule.isNotBlank() && prompt.isNotBlank()) { onCreate(name, schedule, prompt) }
        }
    }
}
