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
fun ChatsSheet(vm: NiobiumViewModel, bot: BotProfile, onDismiss: () -> Unit) {
    LaunchedEffect(bot.name) { vm.loadSessions(bot) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nb.Ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Chats", color = Nb.Text, style = NbTitle, fontSize = 24.sp)
                Text("Stored on your gateway. Open one to resume it.", color = Nb.Tertiary, fontSize = 11.sp)
            }
            if (vm.state.sessions.isEmpty()) {
                item {
                    Text("No stored chats yet.", color = Nb.Tertiary, fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 30.dp))
                }
            } else {
                items(vm.state.sessions.size) { index ->
                    val row = vm.state.sessions[index]
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Nb.FillSoft)
                            .border(1.dp, Nb.Line, RoundedCornerShape(13.dp))
                            .clickable { vm.resumeSession(row, bot); onDismiss() }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(row.title, color = Nb.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text((row.messageCount?.let { it.toString() + " messages · " } ?: "") + row.model.ifBlank { "model" } + ((row.lastActiveAt ?: row.startedAt)?.let { " · " + formatRelative(it) } ?: ""),
                                color = Nb.Tertiary, style = NbMono, fontSize = 8.sp, maxLines = 1)
                        }
                        Text("›", color = Nb.Tertiary, fontSize = 14.sp)
                    }
                }
            }
            item { Spacer(Modifier.padding(bottom = 20.dp)) }
        }
    }
}
