package io.sovware.niobium

import okhttp3.*
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GatewayOpeningRaceTest {
    @Test fun readyCannotArriveBeforeTheSocketOwnerIsPublished() {
        val ready = CountDownLatch(1)
        val listener = object : GatewayClient.Listener {
            override fun onReady(payload: JSONObject) { ready.countDown() }
            override fun onNotification(type: String, sessionId: String, payload: JSONObject) {}
            override fun onServerRequest(id: Any, method: String, params: JSONObject) {}
            override fun onClosed(reason: String) {}
        }
        val factory = object : WebSocket.Factory {
            override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
                val handshake = request
                val socket = object : WebSocket {
                    override fun request() = handshake
                    override fun queueSize() = 0L
                    override fun send(text: String) = true
                    override fun send(bytes: ByteString) = true
                    override fun close(code: Int, reason: String?) = true
                    override fun cancel() {}
                }
                val started = CountDownLatch(1)
                val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(101).message("Switching Protocols").build()
                val worker = Thread {
                    started.countDown()
                    listener.onOpen(socket, response)
                    listener.onMessage(socket, """{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")
                }.apply { isDaemon = true; start() }
                check(started.await(1, TimeUnit.SECONDS))
                // The fixed callback blocks on the publication monitor. The old
                // implementation terminates after dropping the ready frame.
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
                val checkpoints = setOf(Thread.State.BLOCKED, Thread.State.TERMINATED)
                while (worker.state !in checkpoints && System.nanoTime() < deadline) java.util.concurrent.locks.LockSupport.parkNanos(100_000)
                check(worker.state in checkpoints) { "The opening callback did not reach its publication checkpoint." }
                return socket
            }
        }
        val client = GatewayClient(listener, factory)
        try {
            client.connect("http://127.0.0.1", "fixture")
            assertTrue("The first ready frame was dropped", ready.await(2, TimeUnit.SECONDS))
            assertTrue(client.connected)
        } finally { client.disconnect(true) }
    }
}
