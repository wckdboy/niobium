package ai.wckd.niobium.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.sovware.niobium.*
import org.json.JSONObject

data class ProviderModel(val id: String, val name: String)
data class LocalProvider(
    val id: String, val name: String, val active: Boolean, val supported: Boolean,
    val keyConfigured: Boolean, val keyWritable: Boolean, val requiresKey: Boolean, val credentialReady: Boolean,
    val advanced: Boolean, val baseUrl: String, val revision: Long, val models: List<ProviderModel>,
    val customModel: String, val contextWindow: Int, val maxTokens: Int
) {
    val custom get() = id == "niobium-compatible"
    val configured get() = active && models.isNotEmpty() && credentialReady
}
data class LocalProviders(val providers: List<LocalProvider>, val defaultProvider: String, val defaultModel: String,
                          val defaultRevision: Long, val writable: Boolean) {
    companion object {
        fun parse(value: JSONObject): LocalProviders {
            val selected = value.getJSONObject("default")
            return LocalProviders(value.objects("providers").map { row ->
                LocalProvider(row.getString("id"), row.getString("name"), row.getBoolean("active"), row.getBoolean("supported"),
                    row.getBoolean("key_configured"), row.getBoolean("key_writable"), row.getBoolean("requires_key"), row.getBoolean("credential_ready"),
                    row.getBoolean("advanced"), row.getString("base_url"), row.getLong("revision"),
                    row.objects("models").map { ProviderModel(it.getString("id"), it.getString("name")) },
                    row.getString("custom_model"), row.getInt("context_window"), row.getInt("max_tokens"))
            }, selected.getString("provider"), selected.getString("model"), selected.getLong("revision"), value.getBoolean("writable"))
        }
    }
}

@Composable fun LocalProviderPanel(state: DesktopState, agent: DesktopAgent) {
    var editing by remember { mutableStateOf<String?>(null) }
    val catalog = state.localProviders
    Eyebrow("Model providers")
    if (!state.providerSetupSupported) {
        Text("Update the local runtime to set up providers here.", color = Ink.Muted, fontSize = 12.sp)
        return
    }
    if (catalog == null) {
        Text(if (state.providerLoading) "Loading providers…" else "Provider setup is unavailable.", color = Ink.Muted, fontSize = 12.sp)
    } else {
        catalog.providers.forEach { provider ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(provider.configured)
                Column(Modifier.weight(1f)) {
                    Text(provider.name, color = Ink.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (provider.advanced) "Managed in DSH settings" else if (provider.configured) "Configured" else "Set up",
                        color = Ink.Muted, fontSize = 10.sp)
                }
                PixelButton(if (provider.configured) "Edit" else "Add", secondary = true,
                    modifier = Modifier.testTag("provider.edit.${provider.id}"),
                    enabled = catalog.writable && provider.supported && !provider.advanced && !state.providerSaving) { agent.dismissProviderError(); editing = provider.id }
            }
        }
        Text("New chats · ${catalog.defaultModel}", color = Ink.Muted, fontSize = 11.sp)
    }
    if (editing == null) state.providerError?.let { Text(it, color = Ink.Red, fontSize = 11.sp) }
    TextButton(onClick = agent::refreshProviders, enabled = !state.providerLoading && !state.providerSaving) { Text("Refresh providers") }
    val row = catalog?.providers?.firstOrNull { it.id == editing }
    if (row != null) ProviderEditor(row, catalog, state.providerSaving, state.providerError,
        onClose = { editing = null }, onReload = { editing = null; agent.refreshProviders() },
        onSave = { params -> agent.configureProvider(params) { editing = null } })
}

@Composable internal fun ProviderEditor(provider: LocalProvider, catalog: LocalProviders, busy: Boolean, error: String?,
                                      onClose: () -> Unit, onReload: () -> Unit, onSave: (JSONObject) -> Unit) {
    val openedRevision = remember(provider.id) { provider.revision }
    val defaultRevision = remember(provider.id) { catalog.defaultRevision }
    var key by remember(provider.id) { mutableStateOf("") }
    var baseUrl by remember(provider.id) { mutableStateOf(provider.baseUrl) }
    var advanced by remember(provider.id) { mutableStateOf(provider.custom) }
    var modelMenu by remember { mutableStateOf(false) }
    var makeDefault by remember(provider.id) { mutableStateOf(catalog.providers.none { it.id == catalog.defaultProvider && it.configured && it.models.any { model -> model.id == catalog.defaultModel } }) }
    var model by remember(provider.id) { mutableStateOf(if (provider.custom) provider.customModel else if (catalog.defaultProvider == provider.id) catalog.defaultModel else "") }
    var context by remember(provider.id) { mutableStateOf(provider.contextWindow.toString()) }
    var output by remember(provider.id) { mutableStateOf(provider.maxTokens.toString()) }
    val hasKey = key.isNotBlank() || provider.keyConfigured || (provider.custom && !provider.requiresKey)
    val modelValid = if (provider.custom) model.isNotBlank() && baseUrl.isNotBlank() && context.toIntOrNull()?.let { it in 512..2000000 } == true && output.toIntOrNull()?.let { it > 0 && it <= (context.toIntOrNull() ?: 0) } == true
        else !makeDefault || provider.models.any { it.id == model }
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, shape = PixelShape,
        title = { Text(provider.name, color = Ink.Text) },
        text = {
            Column(Modifier.width(460.dp).heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth().testTag("provider.key"), singleLine = true,
                    label = { Text(if (provider.custom) "API key (optional)" else "API key") },
                    placeholder = { Text(if (provider.keyConfigured) "Saved · leave empty to keep" else "Paste your key") },
                    visualTransformation = PasswordVisualTransformation(), enabled = !busy && provider.keyWritable, shape = PixelShape)
                Text(if (!provider.keyWritable) "Key managed outside Niobium." else "Stored in DSH’s private credential file.", color = Ink.Muted, fontSize = 10.sp)
                if (!provider.custom) TextButton(onClick = { advanced = !advanced }, enabled = !busy) { Text("Connection options") }
                if (advanced) {
                    OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth().testTag("provider.endpoint"), singleLine = true,
                        label = { Text("Endpoint URL") }, placeholder = { Text("Provider default") }, enabled = !busy, shape = PixelShape)
                    Text("HTTPS, or loopback HTTP for a local server. Your key is used with this endpoint.", color = Ink.Muted, fontSize = 10.sp)
                }
                if (provider.custom) {
                    OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth().testTag("provider.custom-model"), label = { Text("Model ID") }, singleLine = true, enabled = !busy, shape = PixelShape)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(context, { context = it }, Modifier.weight(1f), label = { Text("Context window") }, singleLine = true, enabled = !busy, shape = PixelShape)
                        OutlinedTextField(output, { output = it }, Modifier.weight(1f), label = { Text("Output limit") }, singleLine = true, enabled = !busy, shape = PixelShape)
                    }
                    Text("OpenAI-compatible chat endpoint. Set limits for the model your server runs.", color = Ink.Muted, fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(makeDefault, { makeDefault = it }, enabled = !busy, modifier = Modifier.testTag("provider.default"))
                    Text("Use for new chats", color = Ink.Text, fontSize = 12.sp)
                }
                if (makeDefault && !provider.custom) Box {
                    PixelButton(provider.models.firstOrNull { it.id == model }?.name ?: "Choose a model", secondary = true,
                        enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("provider.model")) { modelMenu = true }
                    DropdownMenu(modelMenu, { modelMenu = false }, Modifier.heightIn(max = 240.dp)) {
                        provider.models.forEach { item -> DropdownMenuItem(text = { Text(item.name, fontSize = 12.sp) }, onClick = { model = item.id; modelMenu = false }) }
                    }
                }
                if (makeDefault) Text("Existing conversations keep their model.", color = Ink.Muted, fontSize = 10.sp)
                error?.let { Text(it, color = Ink.Red, fontSize = 11.sp); TextButton(onClick = onReload, enabled = !busy) { Text("Reload settings") } }
            }
        },
        confirmButton = {
            PixelButton(if (busy) "Saving…" else "Save provider", enabled = !busy && hasKey && modelValid, modifier = Modifier.testTag("provider.save")) {
                val params = JSONObject().put("provider", provider.id).put("expected_revision", openedRevision).put("make_default", makeDefault)
                if (key.isNotBlank()) params.put("key", key)
                if (baseUrl != provider.baseUrl || provider.custom) params.put("base_url", baseUrl)
                if (makeDefault || provider.custom) params.put("model", model)
                if (makeDefault) params.put("expected_default_revision", defaultRevision)
                if (provider.custom) params.put("context_window", context.toIntOrNull() ?: 0).put("max_tokens", output.toIntOrNull() ?: 0)
                onSave(params)
            }
        }, dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("Cancel") } })
}
