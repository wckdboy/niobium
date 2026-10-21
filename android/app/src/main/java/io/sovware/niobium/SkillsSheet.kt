package io.sovware.niobium

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsSheet(vm: NiobiumViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadSkills() }
    val skills = vm.state.skills
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nb.Ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Skills", color = Nb.Text, style = NbDisplay, fontSize = 28.sp)
                        Text("${skills.values.sumOf { it.size }} skills across ${skills.size} categories", color = Nb.Tertiary, fontSize = 10.sp)
                    }
                    Text("Reload", color = Nb.Ion, fontSize = 11.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Nb.Ion.copy(alpha = 0.10f))
                            .clickable { vm.reloadSkills() }.padding(horizontal = 9.dp, vertical = 6.dp))
                }
            }
            if (skills.isEmpty()) {
                item { Text("No skills returned for this profile.", color = Nb.Tertiary, fontSize = 12.sp) }
            } else {
                skills.keys.sorted().forEach { category ->
                    item {
                        Text(category.uppercase(), color = Nb.Tertiary, style = NbLabel, fontSize = 9.sp, letterSpacing = 1.2.sp)
                    }
                    items((skills[category] ?: emptyList()).size) { i ->
                        Text(skills[category]!![i], color = Nb.Secondary, style = NbMono, fontSize = 10.5.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Nb.FillSoft)
                                .padding(horizontal = 10.dp, vertical = 7.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
