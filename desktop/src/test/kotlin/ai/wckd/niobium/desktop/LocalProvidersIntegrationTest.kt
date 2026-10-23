package ai.wckd.niobium.desktop

import com.sun.net.httpserver.HttpServer
import io.sovware.niobium.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.ConcurrentLinkedQueue

/** Real pinned DSH, with provider HTTP streams confined to a temporary loopback fixture. */
class LocalProvidersIntegrationTest {
    private fun obj(vararg fields: Pair<String, Any?>) = JSONObject(mapOf(*fields))
    private fun responseEvents(model: String, text: String): List<JSONObject> {
        val part = obj("type" to "output_text", "text" to text, "annotations" to emptyList<Any>(), "logprobs" to emptyList<Any>())
        val item = obj("id" to "msg_fixture", "type" to "message", "role" to "assistant", "status" to "completed", "content" to listOf(part))
        val response = obj("id" to "resp_fixture", "object" to "response", "created_at" to 1700000000, "status" to "completed", "model" to model,
            "output" to listOf(item), "usage" to obj("input_tokens" to 3, "output_tokens" to 5, "total_tokens" to 8,
                "input_tokens_details" to obj("cached_tokens" to 0), "output_tokens_details" to obj("reasoning_tokens" to 0)))
        return listOf(
            obj("type" to "response.created", "response" to obj("id" to "resp_fixture", "status" to "in_progress", "output" to emptyList<Any>())),
            obj("type" to "response.output_item.added", "output_index" to 0, "item" to obj("id" to "msg_fixture", "type" to "message", "role" to "assistant", "content" to emptyList<Any>())),
            obj("type" to "response.content_part.added", "item_id" to "msg_fixture", "output_index" to 0, "content_index" to 0, "part" to obj("type" to "output_text", "text" to "", "annotations" to emptyList<Any>())),
            obj("type" to "response.output_text.delta", "item_id" to "msg_fixture", "output_index" to 0, "content_index" to 0, "delta" to text),
            obj("type" to "response.output_text.done", "item_id" to "msg_fixture", "output_index" to 0, "content_index" to 0, "text" to text),
            obj("type" to "response.content_part.done", "item_id" to "msg_fixture", "output_index" to 0, "content_index" to 0, "part" to part),
            obj("type" to "response.output_item.done", "output_index" to 0, "item" to item), obj("type" to "response.completed", "response" to response)
        ).mapIndexed { index, event -> event.put("sequence_number", index) }
    }
    private fun anthropicEvents(model: String, text: String) = listOf(
        obj("type" to "message_start", "message" to obj("id" to "msg_fixture", "type" to "message", "role" to "assistant", "content" to emptyList<Any>(), "model" to model, "stop_reason" to null, "stop_sequence" to null, "usage" to obj("input_tokens" to 3, "output_tokens" to 0))),
        obj("type" to "content_block_start", "index" to 0, "content_block" to obj("type" to "text", "text" to "")),
        obj("type" to "content_block_delta", "index" to 0, "delta" to obj("type" to "text_delta", "text" to text)),
        obj("type" to "content_block_stop", "index" to 0),
        obj("type" to "message_delta", "delta" to obj("stop_reason" to "end_turn", "stop_sequence" to null), "usage" to obj("output_tokens" to 5)), obj("type" to "message_stop")
    )
    @Test fun nativeSetupActivatesEveryProviderAndPersistsModelsWithoutChangingExistingChats() = runBlocking {
        val directory = Files.createTempDirectory("nb-native-providers-")
        val problems = ConcurrentLinkedQueue<String>()
        val seen = ConcurrentLinkedQueue<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 8)
        server.createContext("/") { exchange ->
            val input = JSONObject(exchange.requestBody.bufferedReader().readText())
            val route = exchange.requestURI.path
            val kind = when {
                route == "/deepseek/chat/completions" -> "DEEPSEEK"
                route == "/openai/v1/responses" -> "OPENAI"
                route == "/anthropic/v1/messages" -> "ANTHROPIC"
                route == "/compatible/v1/chat/completions" -> "COMPATIBLE"
                else -> "UNEXPECTED"
            }
            val authorization = exchange.requestHeaders.getFirst("Authorization")
            val actualKey = if (kind == "ANTHROPIC") exchange.requestHeaders.getFirst("x-api-key") else authorization?.removePrefix("Bearer ")
            if (kind != "COMPATIBLE" && actualKey != "fixture-${kind.lowercase()}-only") problems.add("Incorrect credential for $kind")
            if (kind == "COMPATIBLE" && actualKey != "niobium-no-auth") problems.add("Compatible endpoint inherited a real credential")
            if (kind == "UNEXPECTED") problems.add("Unexpected provider request path")
            seen.add(kind)
            val model = input.getString("model"); val text = "$kind-LOCAL"
            val stream = input.optBoolean("stream")
            val body = when (kind) {
                "OPENAI" -> if (stream) responseEvents(model, text).joinToString("") { "event: ${it.getString("type")}\ndata: $it\n\n" } else responseEvents(model, text).last().getJSONObject("response").toString()
                "ANTHROPIC" -> if (stream) anthropicEvents(model, text).joinToString("") { "event: ${it.getString("type")}\ndata: $it\n\n" } else obj("id" to "msg_fixture", "type" to "message", "role" to "assistant", "content" to listOf(obj("type" to "text", "text" to text)), "model" to model, "stop_reason" to "end_turn", "usage" to obj("input_tokens" to 3, "output_tokens" to 5)).toString()
                else -> if (stream) "data: ${obj("id" to "chat_fixture", "object" to "chat.completion.chunk", "created" to 1700000000, "model" to model, "choices" to listOf(obj("index" to 0, "delta" to obj("content" to text), "finish_reason" to "stop")))}\n\ndata: [DONE]\n\n"
                    else obj("id" to "chat_fixture", "object" to "chat.completion", "choices" to listOf(obj("index" to 0, "message" to obj("role" to "assistant", "content" to text), "finish_reason" to "stop"))).toString()
            }
            val bytes = body.toByteArray(); exchange.responseHeaders.add("Content-Type", if (stream) "text/event-stream" else "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }; exchange.close()
        }
        server.start()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val engine = LocalEngine(scope, directory.resolve("dsh"), directory.resolve("workspace"))
        val agent = DesktopAgent(true, scope)
        val origin = "http://127.0.0.1:${server.address.port}"
        suspend fun connect() {
            withContext(Dispatchers.Main) { engine.start { agent.connect(it.origin, it.token) } }
            withTimeout(60_000) {
                while (agent.state.value.localProviders == null) {
                    engine.state.value.error?.let { error(it) }; agent.state.value.error?.let { error(it) }; agent.state.value.providerError?.let { error(it) }; delay(25)
                }
            }
        }
        suspend fun configure(id: String, model: String, endpoint: String, key: String? = null) {
            val catalog = agent.state.value.localProviders!!; val row = catalog.providers.first { it.id == id }
            val params = obj("provider" to id, "base_url" to endpoint, "model" to model, "make_default" to true,
                "expected_revision" to row.revision, "expected_default_revision" to catalog.defaultRevision)
            if (key != null) params.put("key", key)
            if (row.custom) params.put("context_window", 8192).put("max_tokens", 1024)
            val saved = CompletableDeferred<Unit>()
            withContext(Dispatchers.Main) { agent.configureProvider(params) { saved.complete(Unit) } }
            withTimeout(25_000) { while (!saved.isCompleted) { agent.state.value.providerError?.let { error(it) }; delay(25) } }
            assertEquals(id, agent.state.value.localProviders!!.defaultProvider)
            assertEquals(model, agent.state.value.localProviders!!.defaultModel)
        }
        suspend fun turn(id: String, expected: String, model: String): BotProfile {
            val base = PixelBuilder.preview("A precise adult research partner")
            val draft = base.copy(spec = base.spec.copy(name = "check-$id", title = "Fixture $id"))
            withContext(Dispatchers.Main) { agent.createPixel(draft) }
            withTimeout(20_000) { agent.state.first { it.bot?.name == draft.spec.name && !it.loading && it.model == model } }
            withContext(Dispatchers.Main) { agent.send("Check the $id provider route") }
            withTimeout(25_000) { while (agent.state.value.working || agent.state.value.messages.none { it.role == "assistant" && it.text == expected }) {
                agent.state.value.error?.let { error(it) }; delay(25)
            } }
            return agent.state.value.bot!!
        }
        try {
            connect()
            assertFalse(agent.state.value.localProviders!!.providers.first { it.id == "openai" }.active)
            configure("deepseek-official", "deepseek-v4-flash", "$origin/deepseek", "fixture-deepseek-only")
            val original = turn("deepseek", "DEEPSEEK-LOCAL", "deepseek-v4-flash")
            configure("openai", "gpt-4.1-mini", "$origin/openai/v1", "fixture-openai-only")
            assertEquals("deepseek-v4-flash", agent.state.value.model)
            turn("openai", "OPENAI-LOCAL", "gpt-4.1-mini")
            configure("anthropic", "claude-haiku-4-5", "$origin/anthropic", "fixture-anthropic-only")
            turn("anthropic", "ANTHROPIC-LOCAL", "claude-haiku-4-5")
            configure("niobium-compatible", "fixture-local", "$origin/compatible/v1")
            turn("compatible", "COMPATIBLE-LOCAL", "fixture-local")
            withContext(Dispatchers.Main) { agent.disconnect(); engine.close() }
            connect()
            val restored = agent.state.value.localProviders!!
            assertTrue(restored.providers.all { it.configured }); assertEquals("fixture-local", restored.defaultModel)
            val settings = Files.readString(directory.resolve("dsh/settings.yaml"))
            for (key in listOf("fixture-deepseek-only", "fixture-openai-only", "fixture-anthropic-only")) assertFalse(settings.contains(key))
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(directory.resolve("dsh/.credentials.yaml")))
            withContext(Dispatchers.Main) { agent.open(agent.state.value.bots.first { it.name == original.name }) }
            withTimeout(20_000) { agent.state.first { it.session != null && !it.loading && it.model == "deepseek-v4-flash" } }
            assertTrue(agent.state.value.messages.any { it.text == "DEEPSEEK-LOCAL" })
            assertTrue(seen.containsAll(listOf("DEEPSEEK", "OPENAI", "ANTHROPIC", "COMPATIBLE")))
            assertTrue(problems.joinToString(), problems.isEmpty())
        } finally {
            withContext(Dispatchers.Main) { agent.disconnect(); engine.close(); scope.cancel() }
            server.stop(0)
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }
}
