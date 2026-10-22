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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.sovware.niobium.runtime.LocalRuntime
import io.sovware.niobium.runtime.RuntimePhase

@Composable
fun LocalRuntimeScreen(onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    val runtime by LocalRuntime.state.collectAsStateWithLifecycle()
    var workspace by rememberSaveable { mutableStateOf(false) }
    var remote by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { LocalRuntime.start(context) }
    fun start() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else LocalRuntime.start(context)
    }
    BackHandler(enabled = workspace || remote || onClose != null) {
        when { workspace -> workspace = false; remote -> remote = false; else -> onClose?.invoke() }
    }
    if (remote) { RemoteHarnessScreen { remote = false }; return }
    Column(Modifier.fillMaxSize().background(Nb.Void).statusBarsPadding()
        .then(if (onClose != null) Modifier.navigationBarsPadding() else Modifier)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (workspace || onClose != null) {
                Box(Modifier.size(44.dp).clickable { if (workspace) workspace = false else onClose?.invoke() },
                    contentAlignment = Alignment.Center) { PixelIcon(PixelGlyph.BACK, Nb.Secondary) }
                Spacer(Modifier.width(6.dp))
            } else AtomicMark(36.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(if (workspace) "Pixel workspace" else "On your device", color = Nb.Text, fontSize = 18.sp, style = NbTitle)
                Text("LOCAL DSH · ARM64", color = Nb.Tertiary, fontSize = 9.sp, style = NbMono)
            }
            NbPill(when (runtime.phase) {
                RuntimePhase.RUNNING -> "Online"
                RuntimePhase.OFF -> "Offline"
                RuntimePhase.ERROR -> "Retry"
                else -> "Preparing"
            }, if (runtime.phase == RuntimePhase.RUNNING) Nb.Mint else Nb.Ion)
        }
        if (workspace && runtime.phase == RuntimePhase.RUNNING && runtime.origin != null && runtime.cookie != null) {
            LocalWorkspace(runtime.origin!!, runtime.cookie!!, Modifier.weight(1f))
        } else {
            if (workspace) workspace = false
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.fillMaxWidth().pixelPanel(22.dp, accented = true).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Small pixels.\nBig possibilities.", color = Nb.Text, style = NbDisplay,
                                fontSize = 30.sp, lineHeight = 33.sp)
                            Spacer(Modifier.height(10.dp))
                            Text("Your own little Linux lab.", color = Nb.Ion, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        CastPortrait(CastStore.get(context, "athena"), 92.dp, showCompanion = true)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Run Pixels here with DSH and your provider API keys. Connect to Hermes whenever you want.",
                        color = Nb.Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NbPill("Alpine Linux")
                        NbPill("No root")
                        NbPill("Your keys")
                    }
                }

                Column(Modifier.fillMaxWidth().pixelPanel().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelIcon(PixelGlyph.TERMINAL)
                        Spacer(Modifier.width(10.dp))
                        Text("Local engine", color = Nb.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("DSH 0.1.0-rc.7", color = Nb.Tertiary, fontSize = 9.sp, style = NbMono)
                    }
                    when (runtime.phase) {
                        RuntimePhase.INSTALLING, RuntimePhase.STARTING, RuntimePhase.STOPPING -> {
                            Text(when (runtime.phase) {
                                RuntimePhase.INSTALLING -> "Unpacking your little world…"
                                RuntimePhase.STARTING -> "Waking the Pixels…"
                                else -> "Stopping tasks and closing Linux…"
                            }, color = Nb.Secondary, fontSize = 13.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(16) { index ->
                                    Box(Modifier.weight(1f).height(8.dp).background(
                                        if (runtime.phase == RuntimePhase.STOPPING || index < runtime.progress * 16 / 100) Nb.Ion else Nb.FillStrong))
                                }
                            }
                            PixelButton("Cancel startup", PixelGlyph.STOP, Modifier.fillMaxWidth(),
                                secondary = true, enabled = runtime.phase != RuntimePhase.STOPPING) { LocalRuntime.stop(context) }
                        }
                        RuntimePhase.RUNNING -> {
                            Text("Linux is running. Add your provider in DSH settings, then create a session for your Pixel.",
                                color = Nb.Secondary, fontSize = 13.sp, lineHeight = 19.sp)
                            PixelButton("Open Pixel workspace", PixelGlyph.TERMINAL, Modifier.fillMaxWidth()) { workspace = true }
                            PixelButton("Stop local Pixels", PixelGlyph.STOP, Modifier.fillMaxWidth(), secondary = true, destructive = true) {
                                workspace = false; LocalRuntime.stop(context)
                            }
                        }
                        else -> {
                            runtime.message?.let { Text(it, color = Nb.Rose, fontSize = 12.sp, lineHeight = 18.sp) }
                            Text("First launch installs about 350 MB. Your workspace stays on this device when you stop or update the engine.",
                                color = Nb.Secondary, fontSize = 12.sp, lineHeight = 18.sp)
                            PixelButton(if (runtime.phase == RuntimePhase.ERROR) "Try starting again" else "Start local Pixels",
                                PixelGlyph.PLAY, Modifier.fillMaxWidth()) { start() }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().pixelPanel().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PixelIcon(PixelGlyph.SPARK, Nb.Mint)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Give your Pixel a personality", color = Nb.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Ask DSH to use the bot-build skill in your workspace. It designs an adult persona, a look, permissions, and a companion for review.",
                            color = Nb.Secondary, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
                Text("Provider requests use the internet. DSH files and settings stay in app storage. Local Pixels share this Linux workspace; keep permissions intentional.",
                    color = Nb.Tertiary, fontSize = 10.5.sp, lineHeight = 16.sp)
                PixelButton("Open remote DSH", PixelGlyph.LINK, Modifier.fillMaxWidth(), secondary = true) { remote = true }
                if (onClose != null) PixelButton("Connect to Hermes", PixelGlyph.BACK, Modifier.fillMaxWidth(), secondary = true, onClick = onClose)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LocalWorkspace(origin: String, token: String, modifier: Modifier) {
    var error by remember(origin) { mutableStateOf<String?>(null) }
    var revision by remember(origin) { mutableIntStateOf(0) }
    Column(modifier) {
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
