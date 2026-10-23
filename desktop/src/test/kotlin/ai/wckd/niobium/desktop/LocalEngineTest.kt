package ai.wckd.niobium.desktop

import io.sovware.niobium.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files
import java.net.InetSocketAddress
import java.nio.file.attribute.PosixFilePermissions
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class LocalEngineTest {
    @Test fun bundledEngineCreatesRealPixelsRestartsAndPreservesTheirAppearance() = runBlocking {
        val temporary = Files.createTempDirectory("niobium-desktop-test-")
        val sawMessaging = AtomicBoolean(false)
        val model = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 8)
        model.createContext("/chat/completions") { exchange ->
            val input = JSONObject(exchange.requestBody.bufferedReader().readText())
            if (input.objects("tools").any { it.o("function")?.s("name") == "message_agent" }) sawMessaging.set(true)
            val stream = input.optBoolean("stream")
            val response = if (stream) """data: {"id":"desktop-fixture","object":"chat.completion.chunk","created":1700000000,"model":"deepseek-v4-flash","choices":[{"index":0,"delta":{"content":"DESKTOP-DSH-REPLY"},"finish_reason":"stop"}]}""" + "\n\ndata: [DONE]\n\n"
                else """{"id":"desktop-title","object":"chat.completion","choices":[{"index":0,"message":{"role":"assistant","content":"Local test"},"finish_reason":"stop"}]}"""
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", if (stream) "text/event-stream" else "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }; exchange.close()
        }
        model.start()
        AppPaths.privateDirectory(temporary.resolve("dsh"))
        Files.writeString(temporary.resolve("dsh/settings.yaml"), JSONObject().put("llm-deepseek", JSONObject().put("baseURL", "http://127.0.0.1:${model.address.port}").put("thinking", "disabled")).toString())
        val credentials = temporary.resolve("dsh/.credentials.yaml")
        Files.writeString(credentials, "DEEPSEEK_API_KEY: niobium-fixture-only\n")
        Files.setPosixFilePermissions(credentials, PosixFilePermissions.fromString("rw-------"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val engine = LocalEngine(scope, temporary.resolve("dsh"), temporary.resolve("workspace"))
        val agent = DesktopAgent(true, scope)
        suspend fun connect() {
            withContext(Dispatchers.Main) { engine.start { agent.connect(it.origin, it.token) } }
            try { withTimeout(60_000) {
                while (!agent.state.value.connected || agent.state.value.bots.isEmpty()) {
                    engine.state.value.error?.let { error(it) }; agent.state.value.error?.let { error(it) }; delay(50)
                }
            } }
            catch (e: Exception) { throw AssertionError("Local connection failed. Engine=${engine.state.value}; gateway=${agent.state.value.error}", e) }
        }
        try {
            connect()
            assertEquals("default", agent.state.value.bots.single().name)
            val first = PixelBuilder.preview("A security witch with a velvet bat")
            val second = PixelBuilder.preview("A clever cyborg engineer")
            withContext(Dispatchers.Main) { agent.createPixel(first) }
            withTimeout(20_000) { agent.state.first { it.session != null && !it.loading } }
            assertEquals(first.spec.character.look, agent.state.value.specs[first.spec.name]?.character?.look)
            withContext(Dispatchers.Main) { agent.send("A controlled local model turn") }
            withTimeout(20_000) { agent.state.first { state -> !state.working && state.messages.any { it.role == "assistant" && it.text == "DESKTOP-DSH-REPLY" } } }
            assertTrue("The real desktop preset did not expose its reviewed messaging plugin", sawMessaging.get())
            withContext(Dispatchers.Main) { agent.createPixel(second) }
            withTimeout(20_000) { agent.state.first { it.bot?.name == second.spec.name && !it.loading } }
            val room = CompletableDeferred<Unit>()
            withContext(Dispatchers.Main) { agent.createRoom("Local workshop", agent.state.value.bots.filterNot { it.isDefault }) { room.complete(Unit) } }
            withTimeout(20_000) { room.await() }
            assertEquals(2, agent.state.value.room?.members?.size)
            withContext(Dispatchers.Main) { agent.disconnect(); engine.close() }
            assertEquals("Stopped", engine.state.value.phase)
            connect()
            assertEquals(3, agent.state.value.bots.size)
            assertEquals(first.spec.companion, agent.state.value.specs[first.spec.name]?.companion)
            val restored = withTimeout(10_000) { agent.state.first { it.groups.isNotEmpty() } }
            assertEquals("Local workshop", restored.groups.single().name)
            withContext(Dispatchers.Main) { agent.open(restored.bots.first { it.name == first.spec.name }) }
            val history = withTimeout(20_000) { agent.state.first { it.session != null && !it.loading } }
            assertTrue(history.messages.any { it.text == "DESKTOP-DSH-REPLY" })
            val composition = Files.readString(temporary.resolve("dsh/.agent-presets/${first.spec.name}/agent.cordis.yml"))
            assertTrue(composition.contains("niobium-agent.cjs"))
            assertFalse(composition.contains("/usr/local/lib/niobium-agent.cjs"))
        } finally {
            withContext(Dispatchers.Main) { agent.disconnect(); engine.close(); scope.cancel() }
            model.stop(0)
            Files.walk(temporary).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }
}
