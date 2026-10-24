package io.sovware.niobium

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RemoteHarnessScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SecurePrefs(context.applicationContext) }
    var url by remember { mutableStateOf(runCatching { prefs.loadHarnessUrl() }.getOrDefault("")) }
    var reloadID by remember { mutableStateOf(0) }
    var active by remember { mutableStateOf(normalizeAppUrl(url, keepRoute = true)) }
    var error by remember { mutableStateOf(if (url.isNotBlank() && active == null) "This saved address needs HTTPS. Update it before connecting." else null) }

    Column(Modifier.fillMaxSize().background(Nb.Void).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Nb.Ink.copy(alpha = 0.94f)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Nb.Secondary, fontSize = 22.sp,
                modifier = Modifier.clip(CircleShape).clickable { onClose() }.padding(horizontal = 8.dp, vertical = 2.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("DeepSeek Harness", color = Nb.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(if (active == null) "remote · computer or VPS" else "remote · harness",
                    color = Nb.Tertiary, style = NbMono, fontSize = 8.5.sp)
            }
            if (active != null) {
                Text("Change", color = Nb.Ion, fontSize = 11.sp,
                    modifier = Modifier.clickable { active = null; error = null }.padding(6.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Nb.Line))
        if (active == null) {
            Column(Modifier.fillMaxSize().padding(22.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Open the harness from your computer or VPS.", color = Nb.Text, style = NbTitle, fontSize = 24.sp)
                Text("Enter the HTTPS address of your DSH web workspace. HTTP is available only on literal loopback.",
                    color = Nb.Secondary, fontSize = 12.5.sp, lineHeight = 18.sp)
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    placeholder = { Text("https://dsh.example.com", color = Nb.Tertiary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Nb.Text, unfocusedTextColor = Nb.Text,
                        focusedBorderColor = Nb.Ion, unfocusedBorderColor = Nb.LineStrong,
                        focusedContainerColor = Nb.FillSoft, unfocusedContainerColor = Nb.FillSoft
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(Modifier.fillMaxWidth().clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Nb.Ion, Nb.IonDeep)))
                    .clickable {
                        val normalized = normalizeAppUrl(url, keepRoute = true)
                        if (normalized == null) {
                            error = "Use HTTPS, or HTTP on literal loopback."
                        } else {
                            url = normalized
                            runCatching { prefs.saveHarnessUrl(normalized) }.onSuccess { active = normalized; error = null }.onFailure { error = "Could not save the harness address securely." }
                        }
                    }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Text("Open harness", color = Nb.OnIon, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                error?.let { Text(it, color = Nb.Rose, fontSize = 11.sp) }
                Spacer(Modifier.height(4.dp))
                Text("For on-device DSH, return to Local. Hermes gateway access is in the Pixels tab.",
                    color = Nb.Tertiary, fontSize = 10.5.sp, lineHeight = 16.sp)
            }
        } else {
            error?.let { message ->
                Row(Modifier.fillMaxWidth().background(Nb.Surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, color = Nb.Rose, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("Retry", color = Nb.Ion, fontSize = 12.sp, modifier = Modifier.clickable { error = null; reloadID++ }.padding(8.dp))
                }
            }
            androidx.compose.runtime.key(active, reloadID) { AndroidView(
                modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                if (normalizeAppUrl(request?.url?.toString().orEmpty(), keepRoute = true) != null) return false
                                if (request?.isForMainFrame == true) error = "This navigation needs HTTPS."
                                return true
                            }
                            override fun onPageFinished(view: WebView?, url: String?) { error = null }
                            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, failure: android.webkit.WebResourceError?) {
                                if (request?.isForMainFrame == true) error = "Could not load harness: " + failure?.description
                            }
                            override fun onReceivedHttpError(view: WebView?, request: android.webkit.WebResourceRequest?, response: android.webkit.WebResourceResponse?) {
                                if (request?.isForMainFrame == true) error = "Harness returned HTTP " + response?.statusCode
                            }
                        }
                        loadUrl(active!!)
                    }
                },
                update = { },
                onRelease = { web -> web.stopLoading(); web.loadUrl("about:blank"); web.removeAllViews(); web.destroy() }
            ) }
        }
    }
}
