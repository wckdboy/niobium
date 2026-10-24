package io.sovware.niobium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsSheet(vm: NiobiumViewModel, onDismiss: () -> Unit) {
    val owner = vm.chatContext
    val state = vm.state
    val load = state.skillsLoad.copy(loading = state.skillsLoad.loading || state.skillsUpdating)
    LaunchedEffect(vm, owner) { vm.loadSkills() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nb.Ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Skills", color = Nb.Text, style = NbTitle, fontSize = 26.sp)
                        Text(state.activeBot?.title ?: "Gateway", color = Nb.Secondary, fontSize = 12.sp)
                    }
                    NbPill("${state.skills.values.sumOf { it.size }}", Nb.Violet)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton("Refresh", PixelGlyph.REFRESH, Modifier.weight(1f), secondary = true, enabled = !load.loading && state.connected) { vm.loadSkills() }
                    PixelButton("Reload", PixelGlyph.SPARK, Modifier.weight(1f), secondary = true,
                        enabled = !load.loading && load.error == null && state.connected && !state.demo) { vm.reloadSkills(owner) }
                }
            }
            item { InventoryFeedback(load, state.skills.isEmpty(), "No skills available.", vm::loadSkills) }
            state.skills.keys.sorted().forEach { category ->
                item(key = "category:$category") { Text(category, color = Nb.Secondary, style = NbLabel, fontSize = 12.sp) }
                items(state.skills[category].orEmpty(), key = { "skill:${category.length}:$category:$it" }) { skill ->
                    Text(skill, color = Nb.Text, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clip(PixelButtonShape)
                        .background(Nb.FillSoft).padding(14.dp))
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}
