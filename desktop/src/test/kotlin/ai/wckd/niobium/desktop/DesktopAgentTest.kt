package ai.wckd.niobium.desktop

import io.sovware.niobium.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class DesktopAgentTest {
    private lateinit var fixture: Process
    private lateinit var scope: CoroutineScope
    private lateinit var agent: DesktopAgent
    private lateinit var base: String
    @Before fun start() {
        val file = File("../tests/fixtures/gateway.mjs").canonicalFile
        fixture = ProcessBuilder("node", file.path).redirectErrorStream(true).start()
        val line = CompletableFuture.supplyAsync { fixture.inputStream.bufferedReader().readLine() }.get(10, TimeUnit.SECONDS)
        check(line.startsWith("PORT ")) { line }
        base = "http://127.0.0.1:" + line.removePrefix("PORT ") + "/proxy"
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        agent = DesktopAgent(false, scope)
    }
    @After fun stop() = runBlocking {
        withContext(Dispatchers.Main) { agent.disconnect(); scope.cancel() }
        fixture.destroy(); if (!fixture.waitFor(3, TimeUnit.SECONDS)) fixture.destroyForcibly()
    }
    private suspend fun connected(): DesktopState {
        withContext(Dispatchers.Main) { agent.connect(base, "fixture-token+&?") }
        return withTimeout(15_000) { agent.state.first { it.connected && it.bots.isNotEmpty() } }
    }
    @Test fun connectsAndRestoresTheCanonicalChat() = runBlocking {
        val ready = connected()
        assertEquals("remote-fixture", ready.bots.single().name)
        withContext(Dispatchers.Main) { agent.open(ready.bots.single()) }
        val chat = withTimeout(10_000) { agent.state.first { it.session != null && !it.loading } }
        assertEquals("fixture-session", chat.session)
        assertTrue(chat.messages.isEmpty())
    }
    @Test fun rejectedCredentialsNeverExposeAReadyRoster() = runBlocking {
        withContext(Dispatchers.Main) { agent.connect(base, "wrong") }
        val rejected = withTimeout(10_000) { agent.state.first { it.error != null } }
        assertFalse(rejected.connected); assertFalse(rejected.connecting); assertTrue(rejected.bots.isEmpty())
    }
    @Test fun disconnectInvalidatesPendingConnectionWork() = runBlocking {
        withContext(Dispatchers.Main) { agent.connect(base, "fixture-token+&?"); agent.disconnect() }
        delay(250)
        assertEquals(DesktopState(), agent.state.value)
    }
    @Test fun twoEngineStoresKeepIndependentConnectionsAndSelection() = runBlocking {
        val ready = connected()
        val other = DesktopAgent(true, scope)
        withContext(Dispatchers.Main) { other.connect(base, "fixture-token+&?"); agent.open(ready.bots.single()) }
        withTimeout(10_000) { other.state.first { it.connected && it.bots.isNotEmpty() } }
        withTimeout(10_000) { agent.state.first { it.session != null && !it.loading } }
        withContext(Dispatchers.Main) { other.disconnect() }
        assertTrue(agent.state.value.connected); assertEquals("fixture-session", agent.state.value.session)
        assertEquals(DesktopState(), other.state.value)
    }
}
