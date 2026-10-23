package io.sovware.niobium

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.sovware.niobium.runtime.LocalRuntime
import io.sovware.niobium.runtime.RuntimePhase

@Composable
fun LocalRuntimeScreen(vm: NiobiumViewModel? = null, onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    val runtime by LocalRuntime.state.collectAsStateWithLifecycle()
    val state = vm?.state
    var workspace by rememberSaveable { mutableStateOf(false) }
    var remote by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var provider by rememberSaveable { mutableStateOf("DeepSeek") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (vm != null) vm.useLocal() else LocalRuntime.start(context)
    }
    fun start() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if (vm != null) vm.useLocal() else LocalRuntime.start(context)
    }
    BackHandler(enabled = workspace || remote || onClose != null) {
        when { workspace -> workspace = false; remote -> remote = false; else -> onClose?.invoke() }
    }
    if (remote) { RemoteHarnessScreen { remote = false }; return }
    Column(Modifier.fillMaxSize().background(Nb.Void).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (workspace || onClose != null) {
                Box(Modifier.size(44.dp).clickable { if (workspace) workspace = false else onClose?.invoke() }
                    .semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) {
                    PixelIcon(PixelGlyph.BACK, Nb.Secondary)
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(if (workspace) "Advanced DSH" else "Engines", color = Nb.Text, fontSize = 30.sp, style = NbTitle)
                if (!workspace) Text("A home for every Pixel.", color = Nb.Secondary, fontSize = 13.sp)
            }
            PixelIcon(PixelGlyph.TERMINAL, Nb.Ion, 26.dp)
        }
        if (workspace && runtime.phase == RuntimePhase.RUNNING && runtime.origin != null && runtime.cookie != null) {
            LocalWorkspace(runtime.origin!!, runtime.cookie!!, Modifier.weight(1f))
        } else {
            Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Choose an engine. Your dashboard, Pixels and chats work the same way.", color = Nb.Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                Column(Modifier.fillMaxWidth().pixelPanel(accented = state?.engine == AgentEngine.DSH).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelIcon(PixelGlyph.PIXELS)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("On device", color = Nb.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("DSH · private Linux workspace", color = Nb.Tertiary, fontSize = 11.sp)
                        }
                        NbPill(if (state?.engine == AgentEngine.DSH) "Selected" else if (runtime.phase == RuntimePhase.RUNNING) "Running" else "Local",
                            if (runtime.phase == RuntimePhase.RUNNING) Nb.Mint else Nb.Ion)
                    }
                    Text("Run your Pixels here with your own model API key. Keep using Niobium while the engine works in the background.",
                        color = Nb.Secondary, fontSize = 12.sp, lineHeight = 19.sp)
                    if (runtime.busy) {
                        Text(when (runtime.phase) {
                            RuntimePhase.INSTALLING -> "Preparing Linux · " + runtime.progress + "%"
                            RuntimePhase.STARTING -> "Starting the Pixel engine…"
                            else -> "Stopping local tasks…"
                        }, color = Nb.Ion, fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(16) { index -> Box(Modifier.weight(1f).height(5.dp).background(
                                if (index < runtime.progress * 16 / 100) Nb.Ion else Nb.FillStrong)) }
                        }
                    }
                    runtime.message?.let { Text(it, color = Nb.Rose, fontSize = 12.sp) }
                    if (state?.engine != AgentEngine.DSH || runtime.phase in listOf(RuntimePhase.OFF, RuntimePhase.ERROR)) {
                        PixelButton(if (runtime.phase == RuntimePhase.RUNNING) "Use on-device Pixels" else "Use on-device DSH",
                            PixelGlyph.PLAY, Modifier.fillMaxWidth(), enabled = !runtime.busy) { start() }
                    } else if (state.connecting) Text("Connecting your Pixels…", color = Nb.Ion, fontSize = 12.sp)
                    else if (state.connected) Text("Ready · chats and tasks run in Niobium", color = Nb.Mint, fontSize = 12.sp)
                    else if (runtime.phase == RuntimePhase.RUNNING) PixelButton("Reconnect local Pixels", PixelGlyph.LINK, Modifier.fillMaxWidth()) { vm?.reconnect() }
                }
                Column(Modifier.fillMaxWidth().pixelPanel(accented = state?.engine == AgentEngine.HERMES).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelIcon(PixelGlyph.LINK)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Hermes", color = Nb.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("Your computer or VPS", color = Nb.Tertiary, fontSize = 11.sp)
                        }
                        if (state?.engine == AgentEngine.HERMES) NbPill("Selected", Nb.Mint)
                    }
                    val remote = vm?.engineHub?.hermes?.state
                    if (remote?.connected == true && !remote.demo) Text("Connected", color = Nb.Mint, fontSize = 12.sp)
                    Text("Connect to your existing agents, tools and group rooms. Your local engine can stay running alongside Hermes.",
                        color = Nb.Secondary, fontSize = 12.sp, lineHeight = 19.sp)
                    if (state?.engine != AgentEngine.HERMES) PixelButton("Use Hermes", PixelGlyph.LINK, Modifier.fillMaxWidth(), secondary = true) { vm?.useHermes() }
                }
                if (state?.engine == AgentEngine.DSH && state.connected) {
                    Column(Modifier.fillMaxWidth().pixelPanel().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Model provider", color = Nb.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("DeepSeek", "OpenAI", "Anthropic").forEach { name ->
                                Text(name, color = if (name == provider) Nb.Ion else Nb.Secondary, fontSize = 11.sp,
                                    modifier = Modifier.clickable { provider = name; apiKey = "" }.background(if (name == provider) Nb.FillStrong else Nb.FillSoft).padding(8.dp))
                            }
                        }
                        androidx.compose.material3.OutlinedTextField(value = apiKey, onValueChange = { apiKey = it },
                            label = { Text(provider + " API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        PixelButton("Save API key", PixelGlyph.SHIELD, Modifier.fillMaxWidth(), secondary = true, enabled = apiKey.isNotBlank()) {
                            val ref = when (provider) { "OpenAI" -> "OPENAI_API_KEY"; "Anthropic" -> "ANTHROPIC_API_KEY"; else -> "DEEPSEEK_API_KEY" }
                            vm?.saveLocalCredential(ref, apiKey) { apiKey = "" }
                        }
                        Text("Keys stay in DSH app storage. Choose a model from the model picker in your Pixel’s chat.",
                            color = Nb.Tertiary, fontSize = 11.sp, lineHeight = 17.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { advanced = !advanced }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon(PixelGlyph.SETTINGS, Nb.Tertiary, 18.dp)
                    Text("Advanced controls", color = Nb.Secondary, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    Text(if (advanced) "−" else "+", color = Nb.Ion, fontSize = 18.sp)
                }
                if (advanced) {
                    if (runtime.phase == RuntimePhase.RUNNING) {
                        PixelButton("Advanced DSH workspace", PixelGlyph.TERMINAL, Modifier.fillMaxWidth(), secondary = true) { workspace = true }
                        PixelButton("Stop local engine", PixelGlyph.STOP, Modifier.fillMaxWidth(), secondary = true, destructive = true) {
                            workspace = false; if (vm != null) vm.stopLocal() else LocalRuntime.stop(context)
                        }
                    } else if (runtime.busy) PixelButton("Cancel startup", PixelGlyph.STOP, Modifier.fillMaxWidth(), secondary = true) { if (vm != null) vm.stopLocal() else LocalRuntime.stop(context) }
                    PixelButton("Remote DSH workspace", PixelGlyph.LINK, Modifier.fillMaxWidth(), secondary = true) { remote = true }
                    Text("First launch installs about 450 MB. Local Pixels share a Linux workspace; tool choices limit their available tools, while workspace scopes are operating instructions. Local rooms run on this device. Scheduled jobs require Hermes.",
                        color = Nb.Tertiary, fontSize = 11.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LocalWorkspace(origin: String, token: String, modifier: Modifier) {
    var error by remember(origin) { mutableStateOf<String?>(null) }
    var revision by remember(origin) { mutableIntStateOf(0) }
    Column(modifier.imePadding()) {
        error?.let { message ->
            Row(Modifier.fillMaxWidth().background(Nb.Surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message, color = Nb.Rose, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("Retry", color = Nb.Ion, modifier = Modifier.clickable { error = null; revision++ }.padding(8.dp))
            }
        }
        key(origin, revision) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                WebView(context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    fun owned(url: Uri) = url.scheme == "http" && url.host == "127.0.0.1" &&
                        url.port == Uri.parse(origin).port && url.userInfo == null
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                            request == null || !owned(request.url)
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            if (request == null || owned(request.url) || request.url.scheme in listOf("data", "blob")) return null
                            return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))
                        }
                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, failure: android.webkit.WebResourceError?) {
                            if (request?.isForMainFrame == true) error = "Local workspace disconnected. Return to the engine controls to restart it."
                        }
                        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
                            if (request?.isForMainFrame == true) error = "Local workspace returned HTTP " + response?.statusCode
                        }
                    }
                    CookieManager.getInstance().setCookie(origin, "niobium_session=" + token + "; Path=/; HttpOnly; SameSite=Strict") { accepted ->
                        if (accepted) loadUrl(origin) else error = "Could not open a private workspace session."
                    }
                }
            }, onRelease = { web -> web.stopLoading(); web.loadUrl("about:blank"); web.removeAllViews(); web.destroy() })
        }
    }
}

@Composable
fun LocalToolsScreen(state: NbState, onClose: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().background(Nb.Void).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (onClose != null) PixelButton("Back to Pixel", PixelGlyph.BACK, Modifier.fillMaxWidth(), secondary = true, onClick = onClose)
        PixelIcon(PixelGlyph.SHIELD, size = 30.dp)
        Text("Local tool access", color = Nb.Text, style = NbTitle, fontSize = 28.sp)
        Text("Your Pixel’s reviewed preset decides which tools it can use. Create a new Pixel to choose a different set.",
            color = Nb.Secondary, fontSize = 13.sp, lineHeight = 20.sp)
        state.activeBot?.let { bot ->
            Column(Modifier.fillMaxWidth().pixelPanel().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(bot.title, color = Nb.Text, fontWeight = FontWeight.SemiBold)
                if (bot.enabledToolsets.isEmpty()) Text("No optional toolsets selected.", color = Nb.Tertiary, fontSize = 12.sp)
                bot.enabledToolsets.forEach { tool ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelIcon(PixelGlyph.SPARK, Nb.Ion, 16.dp)
                        Text(tool.replaceFirstChar { it.uppercase() }, color = Nb.Secondary, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        }
        Text("Skills share the local workspace. Model keys and advanced DSH configuration are under Engines. Tool selection controls the preset’s catalog; local Pixels share app storage.",
            color = Nb.Tertiary, fontSize = 12.sp, lineHeight = 19.sp)
    }
}
