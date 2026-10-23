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

fun main() {
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
