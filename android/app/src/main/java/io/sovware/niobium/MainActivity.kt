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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val hub: NiobiumEngineHub by viewModels()
    override fun onStart() { super.onStart(); PixelArtist.get(applicationContext).resume(); hub.resumeConnections() }
    override fun onStop() { PixelArtist.get(applicationContext).pausePolling(); super.onStop() }
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        android.webkit.WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        Nb.appearance = getSharedPreferences("niobium", MODE_PRIVATE)
            .getString("niobium.appearance", "system") ?: "system"
        val demo = intent.getBooleanExtra("demo", false) || intent.getBooleanExtra("NIOBIUM_DEMO", false)
        val bot = intent.getStringExtra("bot")
        val tab = intent.getStringExtra("tab")
        val gateway = intent.getStringExtra("gateway")
        val token = intent.getStringExtra("token")
        val explicit = if (!demo && !gateway.isNullOrBlank() && !token.isNullOrBlank()) Connection("Live test",gateway,token) else null
        hub.bootstrap(demo, bot, explicit)
        val vm = hub.active
        intent.getStringExtra("room")?.let { roomName ->
            vm.state.groups.firstOrNull { it.roomId == roomName || it.name == roomName }?.let(vm::openGroup)
        }
        if (demo && intent.getBooleanExtra("demoTool", false)) {
            vm.runDemoAction()
        }
        setContent {
            Nb.systemDark = isSystemInDarkTheme()
            LaunchedEffect(Nb.appearance, Nb.systemDark) {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = Nb.light
                controller.isAppearanceLightNavigationBars = Nb.light
            }
            NiobiumTheme {
                NiobiumApp(hub.active, tab, intent.getBooleanExtra("tools", false))
            }
        }
    }
}

@Composable
fun NiobiumTheme(content: @Composable () -> Unit) {
    val scheme = if (Nb.light) {
        lightColorScheme(
            primary = Nb.Ion,
            onPrimary = Nb.OnIon,
            background = Nb.Void,
            onBackground = Nb.Text,
            surface = Nb.Surface,
            onSurface = Nb.Text,
            secondary = Nb.Violet,
            error = Nb.Rose
        )
    } else {
        darkColorScheme(
            primary = Nb.Ion,
            onPrimary = Nb.OnIon,
            background = Nb.Void,
            onBackground = Nb.Text,
            surface = Nb.Surface,
            onSurface = Nb.Text,
            secondary = Nb.Violet,
            error = Nb.Rose
        )
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
