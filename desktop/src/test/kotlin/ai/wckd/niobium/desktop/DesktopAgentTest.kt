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
    @Test fun aTimedOutHandshakeClearsTheConnectingState() = runBlocking {
        val release = java.util.concurrent.CountDownLatch(1)
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 4)
        server.createContext("/api/status") { exchange ->
            val bytes = "{}".toByteArray(); exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }; exchange.close()
        }
        server.createContext("/api/ws") { exchange ->
            release.await(5, TimeUnit.SECONDS); exchange.sendResponseHeaders(401, -1); exchange.close()
        }
        server.start()
        val waiting = DesktopAgent(false, scope, handshakeTimeoutMillis = 200)
        try {
            withContext(Dispatchers.Main) { waiting.connect("http://127.0.0.1:${server.address.port}", "fixture-token") }
            val stopped = withTimeout(5000) { waiting.state.first { it.error != null } }
            assertFalse(stopped.connecting); assertFalse(stopped.connected)
            assertTrue(stopped.error!!.contains("timed out"))
        } finally { withContext(Dispatchers.Main) { waiting.disconnect() }; release.countDown(); server.stop(0) }
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
    @Test fun failedToolsUseBothDshAndHermesErrorFlags() = runBlocking {
        val ready = connected()
        withContext(Dispatchers.Main) { agent.open(ready.bots.single()) }
        withTimeout(10_000) { agent.state.first { it.session != null && !it.loading } }
        withContext(Dispatchers.Main) { agent.send("fixture tool results") }
        val result = withTimeout(10_000) { agent.state.first { it.messages.count { message -> message.isTool } == 3 && !it.working } }
        assertEquals(ToolStatus.FAILED, result.messages.first { it.id == "local-failed" }.toolStatus)
        assertEquals(ToolStatus.FAILED, result.messages.first { it.id == "remote-failed" }.toolStatus)
        assertEquals(ToolStatus.DONE, result.messages.first { it.id == "completed" }.toolStatus)
    }
    @Test fun listOnlyRoomsNeverOfferUnsupportedStopOrCreation() = runBlocking {
        connected()
        val ready = withTimeout(10_000) { agent.state.first { it.groupsSupported } }
        assertEquals(setOf("groups.list"), ready.groupMethods)
        withContext(Dispatchers.Main) { agent.openRoom(GroupRoom("room-fixture", "Room", emptyList(), null, 0)); agent.stopTurn() }
        delay(150)
        assertNull(agent.state.value.error)
    }
}
