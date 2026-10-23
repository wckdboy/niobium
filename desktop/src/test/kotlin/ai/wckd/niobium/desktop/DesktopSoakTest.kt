package ai.wckd.niobium.desktop

import com.sun.net.httpserver.HttpServer
import io.sovware.niobium.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Bounded real-DSH stress acceptance, with fake provider responses and measured resources. */
class DesktopSoakTest {
    @Test fun repeatedDshCyclesPreserveChatsAndRoomReceiptsAndLeaveNoOwnedProcesses() = runBlocking {
        val directory = Files.createTempDirectory("niobium-dsh-soak-")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val streams = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 8)
        val executor = Executors.newFixedThreadPool(2)
        server.executor = executor
        server.createContext("/chat/completions") { exchange ->
            val input = JSONObject(exchange.requestBody.bufferedReader().readText())
            val streaming = input.optBoolean("stream")
            exchange.responseHeaders.add("Content-Type", if (streaming) "text/event-stream" else "application/json")
            if (streaming) {
                streams.incrementAndGet()
                val marker = Regex("SOAK-(?:DM|ROOM)-[0-9]+-[0-9]+").findAll(input.toString()).lastOrNull()?.value ?: "SOAK-READY"
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use { output ->
                    for (part in listOf("SOAK-REPLY:", marker, " completed")) {
                        val chunk = JSONObject().put("id", "soak-fixture").put("object", "chat.completion.chunk").put("created", 1700000000)
                            .put("model", "deepseek-v4-flash").put("choices", JSONArray().put(JSONObject().put("index", 0).put("delta", JSONObject().put("content", part)).put("finish_reason", JSONObject.NULL)))
                        output.write(("data: $chunk\n\n").toByteArray()); output.flush(); Thread.sleep(100)
                    }
                    output.write("data: {\"id\":\"soak-fixture\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n".toByteArray())
                }
            } else {
                val data = """{"id":"soak-title","choices":[{"index":0,"message":{"role":"assistant","content":"Soak conversation"},"finish_reason":"stop"}]}""".toByteArray()
                exchange.sendResponseHeaders(200, data.size.toLong()); exchange.responseBody.use { it.write(data) }
            }
            exchange.close()
        }
        val home = directory.resolve("home")
        val facade = directory.resolve("runtime")
        val original = AppPaths.runtime().toAbsolutePath()
        val engine = LocalEngine(scope, home, directory.resolve("workspace")) { facade }
        val agent = DesktopAgent(true, scope, RoomOutbox(directory.resolve("outbox.json")))
        val first = PixelBuilder.preview("A security witch with a velvet bat")
        val second = PixelBuilder.preview("A clever cyborg engineer")
        val cycles = JSONArray(); val samples = JSONArray(); val owned = mutableSetOf<ProcessHandle>()
        val start = System.nanoTime()
        var sampling: Job? = null
        try {
            server.start()
            AppPaths.privateDirectory(home)
            Files.writeString(home.resolve("settings.yaml"), JSONObject().put("llm-deepseek", JSONObject().put("baseURL", "http://127.0.0.1:${server.address.port}").put("thinking", "disabled")).toString())
            val key = home.resolve(".credentials.yaml"); Files.writeString(key, "DEEPSEEK_API_KEY: niobium-soak-fixture-only\n")
            Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("rw-------"))
            Files.createDirectory(facade)
            for (name in listOf("desktop-host.cjs", "niobium-local.cjs", "niobium-agent.cjs", "niobium-messaging.cjs")) Files.copy(original.resolve(name), facade.resolve(name))
            for (name in listOf("node", "node_modules")) Files.createSymbolicLink(facade.resolve(name), original.resolve(name))
            withTimeout(180_000) { repeat(3) { cycle ->
                withContext(Dispatchers.Main) { engine.start { agent.connect(it.origin, it.token) } }
                withTimeout(60_000) { agent.state.first { it.connected && it.bots.isNotEmpty() } }
                val host = ProcessHandle.current().descendants().use { handles -> handles.filter {
                    it.info().commandLine().orElse("").contains(facade.resolve("desktop-host.cjs").toString())
                }.findFirst().orElseThrow() }
                fun family() = host.descendants().use { it.toList() } + host
                owned.addAll(family())
                val cycleStart = System.nanoTime()
                sampling = launch(Dispatchers.IO) { while (isActive) {
                    val processes = family().filter { it.isAlive }
                    if (processes.isNotEmpty()) {
                        val ps = ProcessBuilder("/bin/ps", "-o", "rss=", "-o", "time=", "-p", processes.joinToString(",") { it.pid().toString() }).start()
                        check(ps.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) { ps.destroyForcibly(); "Process sampler did not finish" }
                        check(ps.exitValue() == 0) { "Process sampler failed" }
                        val measured = ps.inputStream.bufferedReader().readLines().filter { it.isNotBlank() }.map { line ->
                            val fields = line.trim().split(Regex("\\s+")); check(fields.size == 2)
                            val cpu = fields[1]; val days = if ('-' in cpu) cpu.substringBefore('-').toDouble() else 0.0
                            val seconds = cpu.substringAfter('-').split(':').fold(0.0) { total, part -> total * 60 + part.toDouble() } + days * 86400
                            fields[0].toLong() to (seconds * 1000).toLong()
                        }
                        val rss = measured.sumOf { it.first }
                        samples.put(JSONObject().put("cycle", cycle + 1).put("elapsed_ms", (System.nanoTime() - start) / 1_000_000)
                            .put("process_count", processes.size).put("rss_kib", rss)
                            .put("cpu_ms", measured.sumOf { it.second }))
                    }
                    delay(500)
                } }
                if (cycle == 0) {
                    withContext(Dispatchers.Main) { agent.createPixel(first) }
                    withTimeout(20_000) { agent.state.first { it.bot?.name == first.spec.name && !it.loading } }
                    withContext(Dispatchers.Main) { agent.createPixel(second) }
                    withTimeout(20_000) { agent.state.first { it.bot?.name == second.spec.name && !it.loading } }
                    val created = CompletableDeferred<Unit>()
                    withContext(Dispatchers.Main) { agent.createRoom("Soak workshop", agent.state.value.bots.filterNot { it.isDefault }) { created.complete(Unit) } }
                    withTimeout(20_000) { created.await() }
                } else {
                    withTimeout(20_000) { agent.state.first { it.groups.size == 1 } }
                    assertEquals(3, agent.state.value.bots.size)
                    assertEquals(first.spec.character.look, agent.state.value.specs[first.spec.name]?.character?.look)
                }
                withContext(Dispatchers.Main) { agent.open(agent.state.value.bots.first { it.name == first.spec.name }) }
                withTimeout(20_000) { agent.state.first { it.session != null && !it.loading } }
                if (cycle > 0) assertTrue(agent.state.value.messages.any { it.text.contains("SOAK-DM-${cycle - 1}-2") })
                repeat(3) { turn ->
                    val marker = "SOAK-DM-$cycle-$turn"
                    withContext(Dispatchers.Main) { agent.send(marker) }
                    withTimeout(20_000) { agent.state.first { !it.sending && !it.working && it.messages.any { row -> row.role == "assistant" && row.text.contains("SOAK-REPLY:$marker") } } }
                }
                withContext(Dispatchers.Main) { agent.openRoom(agent.state.value.groups.single()) }
                if (cycle > 0) withTimeout(20_000) { agent.state.first { it.events.count { row -> row.kind == "message.user" } == cycle } }
                val marker = "SOAK-ROOM-$cycle-0"
                val accepted = CompletableDeferred<Unit>()
                withContext(Dispatchers.Main) { agent.sendRoom("@${first.spec.name} $marker") { accepted.complete(Unit) } }
                withTimeout(20_000) { accepted.await() }
                val room = withTimeout(20_000) { agent.state.first { it.events.any { row -> row.kind == "message.member" && row.text.contains("SOAK-REPLY:$marker") } } }
                assertEquals(cycle + 1, room.events.count { it.kind == "message.user" })
                assertEquals(cycle + 1, room.events.count { it.kind == "message.member" })
                assertTrue(room.events.none { it.kind in listOf("turn.failed", "delivery.failed") })
                sampling?.cancelAndJoin(); sampling = null
                owned.addAll(family())
                withContext(Dispatchers.Main) { agent.disconnect(); engine.close() }
                withTimeout(10_000) { while (owned.any { it.isAlive }) delay(50) }
                assertEquals("Stopped", engine.state.value.phase)
                cycles.put(JSONObject().put("cycle", cycle + 1).put("elapsed_ms", (System.nanoTime() - cycleStart) / 1_000_000)
                    .put("dm_turns", 3).put("room_user_events", cycle + 1).put("room_member_events", cycle + 1).put("owned_processes_remaining", 0))
            } }
            assertEquals(12, streams.get())
            assertTrue(samples.length() > 0)
            val evidence = JSONObject().put("schema_version", 1).put("platform", System.getProperty("os.name")).put("arch", System.getProperty("os.arch"))
                .put("java", System.getProperty("java.version")).put("elapsed_ms", (System.nanoTime() - start) / 1_000_000)
                .put("cycles", cycles).put("streamed_turns", streams.get()).put("stream_parts_per_turn", 3).put("process_samples", samples)
                .put("owned_processes_remaining", 0).put("process_scope", "Local host and its live descendants; excludes UI JVM and unsampled transient children").put("cpu_source", "ps cumulative TIME for sampled live owned processes").put("provider", "isolated fake HTTP/SSE; no external account")
                .put("scope", "Three-cycle acceptance baseline; not a multi-hour, physical-device or real-account soak")
            val evidenceFile = Path.of("build/evidence/desktop-soak.json"); Files.createDirectories(evidenceFile.parent)
            Files.writeString(evidenceFile, evidence.toString(2) + "\n")
            Unit
        } finally { withContext(NonCancellable) {
            sampling?.cancelAndJoin()
            try { withContext(Dispatchers.Main) { agent.disconnect(); engine.close(); scope.cancel() } }
            finally {
                server.stop(0); executor.shutdownNow()
                Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
            }
        } }
    }
}
