package ai.wckd.niobium.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun VaultControls(credentials: DesktopCredentials, kind: CredentialKind,
                              onUse: (SavedCredential, String) -> Unit, onDeleteSession: (SavedCredential) -> Unit = {}) {
    val state by credentials.state.collectAsState()
    var removing by remember { mutableStateOf<SavedCredential?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(credentials.vaultName, color = Ink.Muted, fontSize = 11.sp)
            if (state.busy) TextButton(onClick = credentials::cancel) { Text("Cancel", fontSize = 11.sp) }
            else TextButton(onClick = credentials::refresh) { Text("Refresh", fontSize = 11.sp) }
        }
        if (state.status != VaultStatus.READY) Text(when(state.status) {
            VaultStatus.CHECKING -> "Checking vault…"
            VaultStatus.LOCKED -> "Unlock your OS vault, then refresh."
            else -> "Vault unavailable. You can use credentials for this session."
        }, color = Ink.Muted, fontSize = 11.sp)
        state.entries.filter { it.kind == kind }.forEach { entry ->
            Row(Modifier.fillMaxWidth().testTag("saved.${entry.id}"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(entry.name, color = Ink.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (kind == CredentialKind.GATEWAY) Text(entry.address, color = Ink.Muted, fontSize = 10.sp)
                    if (!entry.confirmed) Text("Last save unconfirmed", color = Ink.Muted, fontSize = 10.sp)
                }
                TextButton(onClick = { credentials.use(entry.id, onUse) }, enabled = !state.busy) { Text("Use", fontSize = 11.sp) }
                TextButton(onClick = { removing = entry }, enabled = !state.busy) { Text("Delete", color = Ink.Red, fontSize = 11.sp) }
            }
        }
        state.message?.let { message ->
            Text(message, color = Ink.Muted, fontSize = 11.sp)
            TextButton(onClick = credentials::dismissMessage) { Text("Dismiss", fontSize = 10.sp) }
        }
    }
    removing?.let { entry -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("Delete ${entry.name}?") },
        text = { Text("Ends this saved session and removes its credential from ${credentials.vaultName}.") },
        confirmButton = { TextButton(onClick = { onDeleteSession(entry); credentials.delete(entry.id); removing = null }, modifier = Modifier.testTag("vault.confirm-delete")) { Text("Delete", color = Ink.Red) } },
        dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } }, shape = PixelShape) }
}

@Composable fun GatewayCredentials(hermes: DesktopAgent, credentials: DesktopCredentials) {
    val remote by hermes.state.collectAsState(); val storage by credentials.state.collectAsState()
    var address by remember { mutableStateOf("") }; var token by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }; var rememberKey by remember { mutableStateOf(false) }
    DisposableEffect(credentials) { onDispose { if (credentials.state.value.busy) credentials.cancel() } }
    val editable = !storage.busy || storage.checking
    OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth().testTag("hermes.address"), label = { Text("Gateway URL") }, placeholder = { Text("https://hermes.example.com") }, singleLine = true, shape = PixelShape, enabled = editable)
    OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth().testTag("hermes.token"), label = { Text("Access token") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = PixelShape, enabled = editable)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(rememberKey, { rememberKey = it }, enabled = editable && storage.indexWritable)
        Text("Save in ${credentials.vaultName}", color = Ink.Muted, fontSize = 11.sp)
    }
    if (rememberKey) OutlinedTextField(name, { name = it.take(64) }, Modifier.fillMaxWidth().testTag("hermes.saved-name"), label = { Text("Connection name") }, singleLine = true, shape = PixelShape, enabled = editable)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelButton(if (remote.connecting) "Connecting…" else if (rememberKey) "Save & connect" else "Connect", enabled = address.isNotBlank() && !remote.connecting && (!storage.busy || !rememberKey && storage.checking) && (!rememberKey || name.isNotBlank() && token.isNotBlank())) {
            val submittedAddress = address; val submittedDraft = token; val submittedToken = submittedDraft.trim(); val submittedName = name
            if (rememberKey) credentials.save(CredentialKind.GATEWAY, submittedName, submittedAddress, submittedToken) { entry ->
                hermes.connect(entry.address, submittedToken, entry.id)
                if (token == submittedDraft) token = ""
            } else { if (storage.busy) credentials.cancel(); hermes.connect(submittedAddress, submittedToken); token = "" }
        }
        if (remote.connected || remote.connecting) PixelButton("Disconnect", secondary = true) { if (storage.busy) credentials.cancel(); hermes.disconnect(); token = "" }
    }
    VaultControls(credentials, CredentialKind.GATEWAY, onUse = { entry, value ->
        address = entry.address; name = entry.name; token = ""
        hermes.connect(entry.address, value, entry.id)
    }, onDeleteSession = { entry -> if (hermes.state.value.savedCredentialId == entry.id) hermes.disconnect(); token = "" })
}

@Composable fun ImageCredentials(artist: DesktopArtist, credentials: DesktopCredentials) {
    val auth by artist.credentials.collectAsState(); val storage by credentials.state.collectAsState()
    var key by remember { mutableStateOf("") }; var name by remember { mutableStateOf("OpenAI images") }
    var rememberKey by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val editable = !storage.busy || storage.checking
    DisposableEffect(credentials) { onDispose { if (credentials.state.value.busy) credentials.cancel() } }
    if (auth.ready) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Image session ready", color = Ink.Violet, fontSize = 11.sp)
            TextButton(onClick = { if (storage.busy) credentials.cancel(); artist.clearKey(); key = "" }) { Text("Clear session", fontSize = 11.sp) }
        }
    } else {
        OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth().testTag("images.key"), label = { Text("OpenAI image API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = PixelShape, enabled = editable)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(rememberKey, { rememberKey = it }, enabled = editable && storage.indexWritable)
            Text("Save in ${credentials.vaultName}", color = Ink.Muted, fontSize = 11.sp)
        }
        if (rememberKey) OutlinedTextField(name, { name = it.take(64) }, Modifier.fillMaxWidth(), label = { Text("Image account name") }, singleLine = true, shape = PixelShape, enabled = editable)
        TextButton(onClick = {
            val submittedDraft = key; val submittedKey = submittedDraft.trim()
            if (rememberKey) credentials.save(CredentialKind.IMAGES, name, "", submittedKey) { entry ->
                try { artist.setKey(submittedKey, entry.id); if (key == submittedDraft) key = ""; artist.resume(); error = null }
                catch (e: Exception) { error = e.message }
            } else try { if (storage.busy) credentials.cancel(); artist.setKey(submittedKey); key = ""; artist.resume(); error = null }
                   catch (e: Exception) { error = e.message }
        }, enabled = key.isNotBlank() && (!storage.busy || !rememberKey && storage.checking) && (!rememberKey || name.isNotBlank())) { Text(if (rememberKey) "Save & use key" else "Use for this session") }
    }
    VaultControls(credentials, CredentialKind.IMAGES, onUse = { entry, value ->
        try { artist.setKey(value, entry.id); key = ""; name = entry.name; artist.resume(); error = null }
        catch (e: Exception) { error = e.message }
    }, onDeleteSession = { entry -> if (artist.credentials.value.savedId == entry.id) artist.clearKey(); key = "" })
    error?.let { Text(it, color = Ink.Red, fontSize = 11.sp) }
}
