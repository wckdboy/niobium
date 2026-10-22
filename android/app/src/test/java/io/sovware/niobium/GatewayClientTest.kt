package io.sovware.niobium

import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class GatewayClientTest {
    private lateinit var fixture: Process
    private lateinit var client: GatewayClient
    private lateinit var base: String
    private val ready = CompletableDeferred<Unit>()
    private val requests = LinkedBlockingQueue<Pair<Any, String>>()
    private val events = LinkedBlockingQueue<Pair<String, JSONObject>>()
    @Before fun start() {
        val source = listOf("../tests/fixtures/gateway.mjs", "../../tests/fixtures/gateway.mjs").map { File(it).canonicalFile }.first { it.isFile }
        fixture = ProcessBuilder("node", source.path).redirectErrorStream(true).start()
        val line = CompletableFuture.supplyAsync { fixture.inputStream.bufferedReader().readLine() }.get(10, TimeUnit.SECONDS)
        check(line.startsWith("PORT ")) { "Fixture failed: $line" }
        base = "http://127.0.0.1:" + line.removePrefix("PORT ") + "/proxy"
        client = GatewayClient(object : GatewayClient.Listener {
            override fun onReady(payload: JSONObject) { ready.complete(Unit) }
            override fun onNotification(type: String, sessionId: String, payload: JSONObject) { events.offer(type to payload) }
            override fun onServerRequest(id: Any, method: String, params: JSONObject) { requests.offer(id to method) }
            override fun onClosed(reason: String) {}
        })
        client.connect(base, "fixture-token+&?")
        runBlocking { withTimeout(5000) { ready.await() } }
    }
    @After fun stop() {
        if (::client.isInitialized) client.disconnect(silent = true)
        if (::fixture.isInitialized) { fixture.destroy(); if (!fixture.waitFor(3, TimeUnit.SECONDS)) fixture.destroyForcibly() }
    }
    @Test fun probeUsesHeaderAuthenticationAndReverseProxyPath() { runBlocking {
        assertEquals("fixture", GatewayClient.probe(base, "fixture-token+&?").getString("version"))
    } }
    @Test fun requestRoundTripPreservesStructuredData() { runBlocking {
        val result = client.request("echo", JSONObject().put("session_id", "sid").put("execution_generation", 7))
        assertEquals("sid", result.getString("session_id")); assertEquals(7, result.getInt("execution_generation"))
    } }
    @Test fun concurrentResponsesAreCorrelatedById() { runBlocking {
        val slow = async { client.request("delayed", JSONObject().put("label", "slow").put("delay", 80)) }
        val fast = async { client.request("delayed", JSONObject().put("label", "fast").put("delay", 1)) }
        assertEquals("fast", fast.await().getString("label")); assertEquals("slow", slow.await().getString("label"))
    } }
    @Test fun serverErrorsReachTheCaller() { runBlocking {
        try { client.request("fail"); fail("Expected server rejection") }
        catch (error: GatewayException) { assertEquals("Fixture rejection", error.message) }
    } }
    @Test fun timedOutAndCancelledRequestsDoNotPoisonTheNextCall() { runBlocking {
        try { client.request("wait", timeoutMs = 100); fail("Expected timeout") }
        catch (_: TimeoutCancellationException) {}
        val pending = launch { client.request("wait") }; delay(30); pending.cancelAndJoin()
        assertEquals("alive", client.request("echo", JSONObject().put("value", "alive")).getString("value"))
    } }
    @Test fun serverRequestIdsKeepTheirTypeAndAnswersReturnToTheServer() { runBlocking {
        client.request("request_approval")
        val request = requests.poll(5, TimeUnit.SECONDS)
        assertNotNull(request); assertEquals("approval-7", request.first); assertEquals("approval", request.second)
        client.respond(request.first, JSONObject().put("choice", "deny"))
        val event = events.poll(5, TimeUnit.SECONDS)
        assertNotNull(event); assertEquals("approval.answer", event.first); assertEquals("deny", event.second.getString("choice"))
    } }
    @Test fun disconnectFailsAnInFlightRequestPromptly() { runBlocking {
        val pending = async { runCatching { client.request("wait") }.exceptionOrNull() }
        delay(30); client.disconnect(silent = true)
        assertTrue(withTimeout(2000) { pending.await() } is GatewayException)
    } }

    @Test fun provisioningAppliesOnlyReviewedToolsSkillsAndNoMcpServers() { runBlocking {
        val spec = PixelBuilder.fallback("security").copy(skills = listOf("audit"))
        val draft = PixelDraft(spec, false, setOf("file", "delegation"), setOf("audit", "extra"), emptyList())
        var created = false
        PixelProvisioner.provision(draft, "#A78BFA", client::request) { created = true }
        assertTrue(created)
        val calls = client.request("fixture.state").objects("calls")
        assertEquals(listOf("profiles.create", "config.set", "profiles.describe", "profiles.configure", "config.set"), calls.map { it.getString("method") })
        val creation = calls[0].getJSONObject("params")
        assertEquals(setOf("name", "description", "soul", "clone_from", "clone_all", "no_skills", "clone_channels"), creation.keys().asSequence().toSet())
        assertEquals("default", creation.getString("clone_from"))
        assertFalse(creation.getBoolean("clone_all")); assertFalse(creation.getBoolean("clone_channels"))
        assertFalse(creation.getBoolean("no_skills"))
        assertTrue(creation.getString("soul").contains(spec.soul))
        val approval = calls[1].getJSONObject("params")
        assertEquals("approvals.mode", approval.getString("key")); assertEquals("manual", approval.getString("value"))
        val applied = calls[3].getJSONObject("params")
        assertEquals(listOf("file", "delegation"), applied.getJSONArray("enabled_toolsets").let { a -> (0 until a.length()).map { a.getString(it) } })
        assertEquals(0, applied.getJSONArray("enabled_mcp_servers").length())
        assertEquals("[\"extra\"]", applied.getJSONArray("disabled_skills").toString())
        val saved = applied.getJSONObject("ui_meta").getJSONObject("niobium")
        assertEquals(2, saved.getInt("schema_version"))
        assertEquals(spec.companion.name, saved.getJSONObject("pixel_spec").getJSONObject("companion").getString("name"))
        assertEquals(spec.reasoning, calls[4].getJSONObject("params").getString("value"))
    } }
    @Test fun failureAfterCreationDoesNotCreateAgainOrApplyLaterSettings() { runBlocking {
        client.request("fixture.configure", JSONObject().put("fail_method", "profiles.configure"))
        val spec = PixelBuilder.fallback("security")
        val draft = PixelDraft(spec, false, setOf("file", "delegation"), emptySet(), emptyList())
        var created = false
        try { PixelProvisioner.provision(draft, "#A78BFA", client::request) { created = true }; fail("Expected failure") }
        catch (error: GatewayException) { assertEquals("Fixture setup failure", error.message) }
        assertTrue(created)
        val calls = client.request("fixture.state").objects("calls")
        assertEquals(1, calls.count { it.getString("method") == "profiles.create" })
        assertEquals("profiles.configure", calls.last().getString("method"))
        assertEquals(1, client.request("fixture.state").getJSONArray("profiles").length())
    } }
}
