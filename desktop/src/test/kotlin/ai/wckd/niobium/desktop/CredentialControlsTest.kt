package ai.wckd.niobium.desktop

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.nio.file.Files
import javax.imageio.ImageIO
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties

class CredentialControlsTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val directory = Files.createTempDirectory("niobium-vault-ui-")
    private val vault = FakeVault()
    private val credentials = DesktopCredentials(scope, vault, directory.resolve("connections.json"))
    @After fun close() = runBlocking { credentials.close(); scope.cancel(); Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    @Test fun gatewayPersistenceIsOptInAndTheCredentialIsMasked() = runBlocking {
        withTimeout(5000) { credentials.state.first { !it.busy } }
        val agent = DesktopAgent(false, scope)
        val dsh = DesktopAgent(true, scope); val runtime = LocalEngine(scope)
        compose.setContent { DesktopTheme { NiobiumApp(agent, dsh, runtime, credentials) } }
        compose.onNodeWithText("Engines", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("hermes.saved-name").assertDoesNotExist()
        compose.onNodeWithTag("hermes.address").performTextInput("https://hermes.example/proxy")
        compose.onNodeWithTag("hermes.token").performTextInput("fixture-private-gateway-only")
        compose.onNodeWithTag("hermes.token").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithTag("hermes.token").assert(SemanticsMatcher("displayed token is masked") {
            it.config[SemanticsProperties.EditableText].text == "•".repeat("fixture-private-gateway-only".length)
        })
        compose.onNodeWithText("Save in Test vault").assertExists()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithTag("hermes.saved-name").assertExists()
        compose.onNodeWithText("Save & connect").assertIsNotEnabled()
        compose.onNodeWithTag("hermes.saved-name").performTextInput("Home")
        compose.onNodeWithText("Save & connect").assertIsEnabled()
        assertTrue(vault.values.isEmpty())
        Files.createDirectories(java.nio.file.Path.of("build/evidence"))
        check(ImageIO.write(compose.onRoot().captureToImage().toAwtImage(), "png", java.io.File("build/evidence/gateway-vault-controls.png")))
    }
    @Test fun deletingASavedImageSessionRequiresAConfirmation() = runBlocking {
        withTimeout(5000) { credentials.state.first { !it.busy } }
        credentials.save(CredentialKind.IMAGES, "Art account", "", "fixture-image-key-private-only")
        withTimeout(5000) { credentials.state.first { !it.busy } }
        val entry = credentials.state.value.entries.single()
        val artist = DesktopArtist(scope, receipt = directory.resolve("receipt.json")); artist.setKey("fixture-image-key-private-only", entry.id)
        compose.setContent { DesktopTheme { androidx.compose.foundation.layout.Column { ImageCredentials(artist, credentials) } } }
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete Art account?").assertIsDisplayed()
        assertTrue(artist.credentials.value.ready); assertEquals(1, vault.values.size)
        compose.onNodeWithTag("vault.confirm-delete").performClick()
        compose.waitUntil(5000) { !credentials.state.value.busy && credentials.state.value.entries.isEmpty() }
        assertFalse(artist.credentials.value.ready); assertTrue(vault.values.isEmpty())
    }
    @Test fun disconnectCancelsAPendingSavedCredentialBeforeItCanReconnect() = runBlocking {
        withTimeout(5000) { credentials.state.first { !it.busy } }
        val release = java.util.concurrent.CountDownLatch(1)
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 4)
        server.createContext("/api/status") { e -> val body = "{}".toByteArray(); e.sendResponseHeaders(200, body.size.toLong()); e.responseBody.use { it.write(body) }; e.close() }
        server.createContext("/api/ws") { e -> release.await(10, java.util.concurrent.TimeUnit.SECONDS); e.sendResponseHeaders(401, -1); e.close() }
        server.start()
        val address = "http://127.0.0.1:${server.address.port}"
        val agent = DesktopAgent(false, scope)
        try {
            credentials.save(CredentialKind.GATEWAY, "Home", address, "fixture-private-gateway-only")
            withTimeout(5000) { credentials.state.first { !it.busy } }
            withContext(Dispatchers.Main) { agent.connect(address, "fixture-private-gateway-only") }
            compose.setContent { DesktopTheme { androidx.compose.foundation.layout.Column { GatewayCredentials(agent, credentials) } } }
            vault.gate = CompletableDeferred()
            compose.onNodeWithText("Use").performClick()
            withTimeout(5000) { vault.entered.await() }
            compose.onNodeWithText("Disconnect").performClick()
            vault.gate!!.complete(Unit)
            compose.waitUntil(5000) { !credentials.state.value.busy }
            assertFalse(agent.state.value.connected); assertFalse(agent.state.value.connecting)
            assertNull(agent.state.value.savedCredentialId)
        } finally { vault.gate?.complete(Unit); withContext(Dispatchers.Main) { agent.disconnect() }; release.countDown(); server.stop(0) }
    }
    @Test fun clearingAnImageSessionDiscardsALateSavedKey() = runBlocking {
        withTimeout(5000) { credentials.state.first { !it.busy } }
        credentials.save(CredentialKind.IMAGES, "Art account", "", "fixture-image-saved-private-only")
        withTimeout(5000) { credentials.state.first { !it.busy } }
        val artist = DesktopArtist(scope, receipt = directory.resolve("receipt.json")); artist.setKey("fixture-image-current-private-only")
        compose.setContent { DesktopTheme { androidx.compose.foundation.layout.Column { ImageCredentials(artist, credentials) } } }
        vault.gate = CompletableDeferred()
        compose.onNodeWithText("Use").performClick()
        withTimeout(5000) { vault.entered.await() }
        compose.onNodeWithText("Clear session").performClick()
        vault.gate!!.complete(Unit)
        compose.waitUntil(5000) { !credentials.state.value.busy }
        assertFalse(artist.credentials.value.ready)
    }
}
