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
