package io.sovware.niobium

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val vm: NiobiumViewModel by viewModels()
        val demo = intent.getBooleanExtra("demo", false) || intent.getBooleanExtra("NIOBIUM_DEMO", false)
        val bot = intent.getStringExtra("bot")
        val tab = intent.getStringExtra("tab")
        vm.bootstrap(demo, bot)
        intent.getStringExtra("room")?.let { roomName ->
            vm.state.groups.firstOrNull { it.roomId == roomName || it.name == roomName }?.let(vm::openGroup)
        }
        if (demo && intent.getBooleanExtra("demoTool", false)) {
            vm.runDemoAction()
        }
        if (!demo) {
            val gateway = intent.getStringExtra("gateway")
            val token = intent.getStringExtra("token")
            if (!gateway.isNullOrBlank() && !token.isNullOrBlank()) {
                vm.connect(gateway, token, "Live test")
            }
        }
        setContent {
            NiobiumTheme {
                NiobiumApp(vm, tab, intent.getBooleanExtra("tools", false))
            }
        }
    }
}

@Composable
fun NiobiumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Nb.Ion,
            onPrimary = androidx.compose.ui.graphics.Color(0xFF090C13),
            background = Nb.Void,
            onBackground = Nb.Text,
            surface = Nb.Surface,
            onSurface = Nb.Text,
            secondary = Nb.Violet,
            error = Nb.Rose
        ),
        typography = Typography(),
        content = content
    )
}
