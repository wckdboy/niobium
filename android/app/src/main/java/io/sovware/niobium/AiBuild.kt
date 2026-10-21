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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIBuildSheet(vm: NiobiumViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ideas = listOf(
        "A night-shift security pixel for my VPS logs, with a bat",
        "A creative director who makes image prompts and moodboards",
        "A release engineer who runs builds and asks before deploys",
        "A research partner who reads papers and keeps notes"
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nb.Ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Build a Pixel", color = Nb.Text, style = NbDisplay, fontSize = 30.sp)
            Text("One sentence. The bot-build skill writes personality, permissions, toolsets, connectors, character and companion.",
                color = Nb.Secondary, fontSize = 12.sp, lineHeight = 18.sp)
            OutlinedTextField(
                value = prompt, onValueChange = { prompt = it },
                placeholder = { Text("A calm research partner who reads papers and keeps notes…", color = Nb.Tertiary) },
                minLines = 3, maxLines = 6,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Nb.Text, unfocusedTextColor = Nb.Text,
                    focusedBorderColor = Nb.Ion, unfocusedBorderColor = Nb.LineStrong,
                    focusedContainerColor = Nb.FillSoft, unfocusedContainerColor = Nb.FillSoft
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Text("TRY", color = Nb.Tertiary, style = NbLabel, fontSize = 9.sp)
            ideas.forEach { item ->
                Text(item, color = Nb.Secondary, fontSize = 10.5.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Nb.FillSoft)
                        .clickable { prompt = item }.padding(9.dp))
            }
            error?.let { Text(it, color = Nb.Rose, fontSize = 11.sp) }
            Box(Modifier.fillMaxWidth().clip(CircleShape)
                .background(Brush.linearGradient(listOf(Nb.Ion, Nb.IonDeep)))
                .clickable {
                    if (working || prompt.isBlank()) return@clickable
                    working = true; error = null
                    vm.buildPixel(context, prompt, onCreated = { onDismiss() }, onError = { error = it; working = false })
                }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                Text(if (working) "Designing…" else "Build with AI", color = Nb.OnIon, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("If no model is configured, a local template is used and labelled. Nothing is created until you tap.",
                color = Nb.Tertiary, fontSize = 9.5.sp, lineHeight = 14.sp)
        }
    }
}
