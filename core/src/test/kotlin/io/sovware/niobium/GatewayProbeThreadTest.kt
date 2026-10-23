package io.sovware.niobium

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class GatewayProbeThreadTest {
    @Test fun aSlowProbeDoesNotBlockItsCallersSingleThreadDispatcher() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 4)
        server.createContext("/api/status") { exchange ->
            entered.countDown(); release.await(5, TimeUnit.SECONDS)
            val body = "{\"version\":\"slow-fixture\"}".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong()); exchange.responseBody.use { it.write(body) }; exchange.close()
        }
        server.start()
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val probe = scope.async { GatewayClient.probe("http://127.0.0.1:${server.address.port}", "") }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            val responsive = scope.async { "UI work ran" }
            assertEquals("UI work ran", withTimeout(1000) { responsive.await() })
            release.countDown()
            assertEquals("slow-fixture", withTimeout(3000) { probe.await() }.getString("version"))
        } finally { release.countDown(); scope.cancel(); dispatcher.close(); server.stop(0) }
    }
}
