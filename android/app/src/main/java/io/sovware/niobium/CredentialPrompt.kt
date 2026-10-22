package io.sovware.niobium

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import org.json.JSONObject

data class CredentialRequest(val rpcId: Any, val sessionId: String, val method: String,
                             val title: String, val detail: String, val login: Boolean, val transportEpoch: Int = 0) {
    val id get() = transportEpoch.toString() + ":" + rpcId.toString()
}
@Composable
fun CredentialPrompt(vm: NiobiumViewModel, request: CredentialRequest) {
    var value by remember(request.id) { mutableStateOf("") }
    var identifier by remember(request.id) { mutableStateOf("") }
    fun finish(answer: String) { vm.resolveCredential(request, answer); value = ""; identifier = "" }
    AlertDialog(onDismissRequest = { finish("") }, containerColor = Nb.Ink,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(request.title, color = Nb.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(request.detail, color = Nb.Secondary, fontSize = 13.sp)
                if (request.login) OutlinedTextField(identifier, { identifier = it }, label = { Text("Login identifier") }, singleLine = true)
                OutlinedTextField(value, { value = it }, label = { Text(if (request.login) "Password" else "Value") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrectEnabled = false))
                Text("Sent to your connected Hermes host for this request. Niobium does not save this value or add it to chat.",
                    color = Nb.Tertiary, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = value.isNotEmpty() && (!request.login || identifier.isNotEmpty()), onClick = {
                finish(if (request.login) JSONObject().put("identifier", identifier).put("password", value).toString() else value)
            }) { Text("Send to host", color = Nb.Ion) }
        },
        dismissButton = { TextButton(onClick = { finish("") }) { Text("Skip", color = Nb.Secondary) } })
    DisposableEffect(request.id) { onDispose { value = ""; identifier = "" } }
}
