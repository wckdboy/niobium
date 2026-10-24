package io.sovware.niobium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsSheet(vm: NiobiumViewModel, bot: BotProfile, onDismiss: () -> Unit) {
    LaunchedEffect(vm, bot.id) { vm.loadSessions(bot) }
    val state = vm.state
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nb.Ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ChatBrowser(bot.castKey, bot.title, state.engine == AgentEngine.DSH, state.sessions,
            state.sessionsLoading, state.sessionsError, state.sessionId,
            canOpen = state.connected && !state.loadingChat && !state.sendingChat,
            onRefresh = {vm.loadSessions(bot)}, onOpen = {vm.resumeSession(it,bot);onDismiss()},
            modifier = Modifier.fillMaxHeight(.85f).imePadding())
    }
}

@Composable
internal fun ChatBrowser(owner: String, title: String, local: Boolean, rows: List<SessionRow>,
                         loading: Boolean, error: String?, activeSession: String?, canOpen: Boolean,
                         onRefresh: () -> Unit, onOpen: (SessionRow) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable(owner) {mutableStateOf("")}
    var running by rememberSaveable(owner) {mutableStateOf(false)}
    val keyboard = LocalSoftwareKeyboardController.current
    val matches = remember(rows, query, running) {
        val term = query.trim()
        rows.filter { (!running || it.running) && (term.isEmpty() ||
            it.title.contains(term,ignoreCase=true) || it.model.contains(term,ignoreCase=true)) }
    }
    Column(modifier.fillMaxWidth().padding(horizontal=20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Chats",color=Nb.Text,style=NbTitle,fontSize=26.sp)
                Text("$title · ${if(local) "On device" else "Hermes"}",color=Nb.Secondary,fontSize=12.sp)
            }
            NbPill(if(!local && rows.size>=60) "${rows.size} recent" else "${rows.size}",Nb.Violet)
        }
        OutlinedTextField(query,{query=it.take(200)},singleLine=true,label={Text("Find a chat")},
            trailingIcon={if(query.isNotEmpty()) TextButton(onClick={query=""}) {Text("Clear")}},
            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
            keyboardActions=KeyboardActions(onSearch={keyboard?.hide()}),
            modifier=Modifier.fillMaxWidth().testTag("sessions.search"))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            PixelButton("Working",PixelGlyph.ACTIVITY,Modifier.weight(1f).testTag("sessions.working").semantics {selected=running},
                secondary=!running) {running=!running}
            PixelButton("Refresh",PixelGlyph.REFRESH,Modifier.weight(1f),secondary=true,enabled=!loading,onClick=onRefresh)
        }
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp),color=Nb.Ion)
        error?.let {Text(it,color=Nb.Rose,fontSize=12.sp,modifier=Modifier.testTag("sessions.error"))}
        LazyColumn(Modifier.weight(1f,fill=false).fillMaxWidth().testTag("sessions.list"),
            contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(matches.isEmpty()) item {
                Text(when {
                    loading && rows.isEmpty() -> "Loading chats…"
                    error!=null && rows.isEmpty() -> "Use Refresh to try again."
                    rows.isEmpty() -> "Your conversations will appear here."
                    else -> "No matching chats."
                },color=Nb.Secondary,fontSize=13.sp,modifier=Modifier.padding(vertical=28.dp))
            }
            items(matches,key={it.sessionId}) {row ->
                val current=row.sessionId==activeSession
                Row(Modifier.fillMaxWidth().testTag("sessions.row.${row.sessionId}").heightIn(min=72.dp).clip(PixelButtonShape)
                    .background(if(current) Nb.Ion.copy(alpha=.08f) else Nb.FillSoft)
                    .border(1.dp,if(current) Nb.Ion.copy(alpha=.35f) else Nb.Line,PixelButtonShape)
                    .semantics {selected=current}.clickable(enabled=canOpen,role=Role.Button) {onOpen(row)}.padding(14.dp),
                    horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(row.title,color=Nb.Text,fontSize=15.sp,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(listOfNotNull(row.messageCount?.let {"$it messages"},row.model.takeIf {it.isNotBlank()},
                            (row.lastActiveAt?:row.startedAt)?.let(::formatRelative)).joinToString(" · ").ifBlank {"Stored chat"},
                            color=Nb.Secondary,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    if(row.running) NbPill("Working",Nb.Mint) else if(current) NbPill("Current",Nb.Violet)
                    PixelIcon(PixelGlyph.PLAY,Nb.Tertiary,14.dp)
                }
            }
        }
    }
}
