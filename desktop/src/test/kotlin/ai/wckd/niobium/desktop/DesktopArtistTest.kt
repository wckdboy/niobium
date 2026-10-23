package ai.wckd.niobium.desktop

import com.sun.net.httpserver.HttpServer
import io.sovware.niobium.PixelArtistWire
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class DesktopArtistTest {
    private fun digest(value: String) = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    @Test fun aDifferentKeyRequiresConfirmationAndBackendAcceptanceBeforeResuming() = runBlocking {
        val directory = Files.createTempDirectory("niobium-artist-account-")
        val receipt = directory.resolve("receipt.json")
        val owner = "fixture-image-owner-private-only"; val other = "fixture-image-other-private-only"
        Files.writeString(receipt, """{"target":"pixel","phase":"queued","response_id":"resp_owned","credential_scope":"${digest(owner)}"}""")
        val calls = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 4)
        server.createContext("/responses/resp_owned") { e ->
            calls.add(e.requestHeaders.getFirst("Authorization")); val bytes = """{"id":"resp_owned","status":"cancelled"}""".toByteArray()
            e.sendResponseHeaders(200, bytes.size.toLong()); e.responseBody.use { it.write(bytes) }; e.close()
        }
        server.start()
        val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val artist = DesktopArtist(scope, PixelArtistWire(endpoint = "http://127.0.0.1:${server.address.port}/responses"), receipt)
            withContext(dispatcher) { artist.setKey(other); artist.resume(); artist.cancel(); artist.generate("A new adult outfit", "pixel", byteArrayOf()) }
            delay(150); assertTrue(calls.isEmpty()); assertFalse(artist.credentials.value.receiptMatches)
            withContext(dispatcher) { artist.resume(confirmAccount = true) }
            withTimeout(10_000) { artist.state.first { it?.phase == "cancelled" } }
            assertEquals(listOf("Bearer $other"), calls.toList()); assertTrue(artist.credentials.value.receiptMatches)
            val saved = withContext(dispatcher) { Files.readString(receipt) }; assertFalse(saved.contains(owner)); assertFalse(saved.contains(other))
            assertEquals(digest(other), org.json.JSONObject(saved).getString("credential_scope"))
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin(); dispatcher.close(); server.stop(0)
            Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
    @Test fun legacyReceiptsRequireAnExplicitAccountConfirmationBeforeAnyRequest() = runBlocking {
        val directory = Files.createTempDirectory("niobium-artist-legacy-")
        val receipt = directory.resolve("receipt.json"); Files.writeString(receipt, """{"target":"pixel","phase":"queued","response_id":"resp_legacy"}""")
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 4)
        server.createContext("/responses/resp_legacy") { e ->
            calls.incrementAndGet(); val bytes = """{"id":"resp_legacy","status":"cancelled"}""".toByteArray()
            e.sendResponseHeaders(200, bytes.size.toLong()); e.responseBody.use { it.write(bytes) }; e.close()
        }; server.start()
        val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val artist = DesktopArtist(scope, PixelArtistWire(endpoint = "http://127.0.0.1:${server.address.port}/responses"), receipt)
            withContext(dispatcher) { artist.setKey("fixture-legacy-image-owner-only"); artist.resume(); artist.cancel() }
            delay(150); assertEquals(0, calls.get()); assertTrue(artist.credentials.value.legacyReceipt)
            withContext(dispatcher) { artist.resume(confirmAccount = true) }
            withTimeout(10_000) { artist.state.first { it?.phase == "cancelled" } }
            assertEquals(1, calls.get()); assertFalse(artist.credentials.value.legacyReceipt)
            val saved = withContext(dispatcher) { Files.readString(receipt) }
            assertEquals(digest("fixture-legacy-image-owner-only"), org.json.JSONObject(saved).getString("credential_scope"))
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin(); dispatcher.close(); server.stop(0)
            Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
    @Test fun clearingAKeyDuringSubmissionKeepsUnknownAcceptanceAndNeverReplaysThePost() = runBlocking {
        val directory = Files.createTempDirectory("niobium-artist-clear-")
        val calls = AtomicInteger(); val entered = CompletableDeferred<Unit>(); val release = java.util.concurrent.CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 4)
        server.createContext("/responses") { e ->
            e.requestBody.use { it.readBytes() }; calls.incrementAndGet(); entered.complete(Unit); release.await(5, java.util.concurrent.TimeUnit.SECONDS)
            val bytes = """{"id":"resp_inflight","status":"queued"}""".toByteArray()
            runCatching { e.sendResponseHeaders(200, bytes.size.toLong()); e.responseBody.use { it.write(bytes) } }; e.close()
        }; server.start()
        val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val artist = DesktopArtist(scope, PixelArtistWire(endpoint = "http://127.0.0.1:${server.address.port}/responses"), directory.resolve("receipt.json"))
            withContext(dispatcher) { artist.setKey("fixture-first-image-account-only"); artist.generate("Adult cyborg", "pixel", byteArrayOf()) }
            withTimeout(3000) { entered.await() }
            withContext(dispatcher) { artist.clearKey(); artist.setKey("fixture-new-image-account-only"); artist.generate("Another design", "pixel", byteArrayOf()) }
            release.countDown(); delay(200)
            assertEquals("unknown", artist.state.value?.phase); assertNull(artist.state.value?.responseId); assertEquals(1, calls.get())
        } finally {
            release.countDown(); scope.coroutineContext[Job]!!.cancelAndJoin(); dispatcher.close(); server.stop(0)
            Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
    @Test fun anUnusableCompletedImageIsTerminalAndANewDesignRequiresAnExplicitRequest() = runBlocking {
        val directory = Files.createTempDirectory("nb-artist-test-")
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 4)
        server.createContext("/responses") { exchange ->
            exchange.requestBody.use { it.readBytes() }
            val id = calls.incrementAndGet()
            val bytes = """{"id":"resp_fixture_$id","status":"completed","output":[{"type":"image_generation_call","result":"Zm9v"}]}""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }; exchange.close()
        }
        server.start()
        val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val artist = DesktopArtist(scope, PixelArtistWire(endpoint = "http://127.0.0.1:${server.address.port}/responses"), directory.resolve("receipt.json"))
            withContext(dispatcher) { artist.setKey("fixture-key-for-images-only"); artist.generate("An adult cyber mage", "pixel", byteArrayOf()) }
            withTimeout(5000) { artist.state.first { it?.phase == "failed" } }
            withTimeout(3000) { while (scope.coroutineContext[Job]!!.children.any { it.isActive }) delay(5) }
            assertFalse(artist.busy); assertNull(artist.state.value?.candidate)
            assertEquals("resp_fixture_1", artist.state.value?.responseId)
            assertEquals(1, calls.get())
            withContext(dispatcher) { artist.generate("Another outfit", "pixel", byteArrayOf()) }
            withTimeout(5000) { artist.state.first { it?.phase == "failed" && it.responseId == "resp_fixture_2" } }
            assertEquals(2, calls.get())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin(); dispatcher.close(); server.stop(0)
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }
}
