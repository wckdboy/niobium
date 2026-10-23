package io.sovware.niobium

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GatewayTransportSecurityTest {
    private fun listener(closed: CompletableDeferred<String>? = null) = object : GatewayClient.Listener {
        override fun onReady(payload: JSONObject) {}
        override fun onNotification(type: String, sessionId: String, payload: JSONObject) {}
        override fun onServerRequest(id: Any, method: String, params: JSONObject) {}
        override fun onClosed(reason: String) { closed?.complete(reason) }
    }

    @Test fun cleartextRemoteAddressesAreRejectedBeforeProbeOrSocketCreation() = runBlocking {
        val factory = object : WebSocket.Factory {
            override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket = error("Must not send a token")
        }
        for (url in listOf("http://example.invalid", "http://192.168.1.9", "http://localhost", "http://127.0.0.1.evil.invalid")) {
            assertThrows(GatewayException::class.java) { GatewayClient(listener(), factory).connect(url, "private-token") }
            try { GatewayClient.probe(url, "private-token"); fail("The probe accepted cleartext remote credentials") }
            catch (error: GatewayException) { assertTrue(error.message!!.contains("HTTPS")) }
        }
    }

    private suspend fun rejectsRedirect(socket: Boolean) {
        val hits = AtomicInteger()
        val target = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 4)
        target.createContext("/") { exchange -> hits.incrementAndGet(); exchange.sendResponseHeaders(401, -1); exchange.close() }
        target.start()
        val source = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 4)
        source.createContext("/") { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${target.address.port}/api/status")
            exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        source.start()
        val closed = CompletableDeferred<String>()
        val client = GatewayClient(listener(closed))
        try {
            val url = "http://127.0.0.1:${source.address.port}"
            if (socket) {
                client.connect(url, "private-token")
                assertTrue(withTimeout(5000) { closed.await() }.contains("302"))
            } else {
                try { GatewayClient.probe(url, "private-token"); fail("The probe followed a redirect") }
                catch (error: GatewayException) { assertTrue(error.message!!.contains("302")) }
            }
            assertEquals("A redirect destination received a credential-bearing request", 0, hits.get())
        } finally { client.disconnect(true); source.stop(0); target.stop(0) }
    }

    @Test fun statusProbeDoesNotFollowRedirects() = runBlocking { rejectsRedirect(false) }
    @Test fun websocketHandshakeDoesNotFollowRedirects() = runBlocking { rejectsRedirect(true) }
}
