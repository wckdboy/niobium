package ai.wckd.niobium.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.*
import java.awt.Dimension
import io.sovware.niobium.*
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import org.json.JSONObject

fun main(args: Array<String>) {
    if (args.contentEquals(arrayOf("--check-runtime"))) {
        try { checkPackagedRuntime(); kotlin.system.exitProcess(0) }
        catch (error: Exception) { error.printStackTrace(); kotlin.system.exitProcess(1) }
    }
    val lease = AppLease.acquire()
    if (lease == null) {
        javax.swing.JOptionPane.showMessageDialog(null, "Niobium is already open. Use its existing window.", "Niobium", javax.swing.JOptionPane.INFORMATION_MESSAGE)
        return
    }
    lease.use { application {
    val scope = rememberCoroutineScope()
    val local = remember { LocalEngine(scope) }
    val hermes = remember { DesktopAgent(false, scope) }
    val dsh = remember { DesktopAgent(true, scope) }
    val runtime by local.state.collectAsState()
    val remoteState by hermes.state.collectAsState()
    val localState by dsh.state.collectAsState()
    var confirmQuit by remember { mutableStateOf(false) }
    var quitting by remember { mutableStateOf(false) }
    fun quit() {
        if (quitting) return
        quitting = true
        hermes.disconnect(); dsh.disconnect()
        scope.launch { local.close(); exitApplication() }
    }
    Window(onCloseRequest = {
        if (runtime.phase in listOf("Running", "Starting") || localState.working || remoteState.working) confirmQuit = true else quit()
    }, title = "Niobium", state = rememberWindowState(width = 1220.dp, height = 830.dp), icon = painterResource("niobium.png")) {
        LaunchedEffect(Unit) { window.minimumSize = Dimension(960, 650) }
        DesktopTheme {
            NiobiumApp(hermes, dsh, local)
            if (confirmQuit) AlertDialog(onDismissRequest = { confirmQuit = false }, title = { Text("Close Niobium?") },
                text = { Text("Local DSH and its running tasks will stop. Hermes continues on its host.") },
                confirmButton = { TextButton(onClick = ::quit, enabled = !quitting) { Text("Quit") } },
                dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Keep open") } }, shape = PixelShape)
        }
    }
    } }
}

/** Exercises the installed launcher/resources in isolation, without provider calls. */
private fun checkPackagedRuntime() = runBlocking {
    val temporary = Files.createTempDirectory("niobium-package-check-")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val engine = LocalEngine(scope, temporary.resolve("dsh"), temporary.resolve("workspace"))
    var client: GatewayClient? = null
    try {
        val endpoint = CompletableDeferred<LocalEndpoint>()
        val watch = scope.launch { val failed = engine.state.first { it.error != null }; endpoint.completeExceptionally(IllegalStateException(failed.error)) }
        engine.start { endpoint.complete(it) }
        val local = withTimeout(55_000) { endpoint.await() }
        watch.cancel()
        GatewayClient.probe(local.origin, local.token)
        val ready = CompletableDeferred<Unit>()
        client = GatewayClient(object : GatewayClient.Listener {
            override fun onReady(payload: JSONObject) { ready.complete(Unit) }
            override fun onNotification(type: String, sessionId: String, payload: JSONObject) {}
            override fun onServerRequest(id: Any, method: String, params: JSONObject) {}
            override fun onClosed(reason: String) { ready.completeExceptionally(IllegalStateException(reason)) }
        })
        client.connect(local.origin, local.token)
        withTimeout(15_000) { ready.await() }
        val profiles = client.request("profiles.list", JSONObject().put("include_sessions", true), 15_000)
        check(profiles.objects("profiles").any { it.s("name") == "default" }) { "The bundled DSH profile directory is unavailable." }
        println("NIOBIUM_RUNTIME_OK")
    } finally {
        client?.disconnect(true)
        engine.close(); scope.cancel()
        Files.walk(temporary).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }
}
