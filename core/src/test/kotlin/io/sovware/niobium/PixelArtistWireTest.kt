package io.sovware.niobium

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PixelArtistWireTest {
    @Test fun backgroundDesignRequestsAreTransparentStoredAndReferenceAware() {
        val body = PixelArtistWire.body("violet cloak", "pixel", byteArrayOf(1,2,3))
        assertTrue(body.getBoolean("background")); assertTrue(body.getBoolean("store"))
        val tool = body.getJSONArray("tools").getJSONObject(0)
        assertEquals("transparent", tool.getString("background")); assertEquals("png", tool.getString("output_format"))
        assertEquals(2, body.getJSONArray("input").getJSONObject(0).getJSONArray("content").length())
        assertTrue(PixelArtistWire.validId("resp_valid-123")); assertFalse(PixelArtistWire.validId("resp_x/../../secrets"))
    }
    @Test fun transportUsesOneSubmissionThenGetAndExplicitCancellation() = runBlocking {
        val seen = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/v1/responses") { exchange ->
            seen.add(exchange.requestMethod + " " + exchange.requestURI.path)
            val state = if (exchange.requestURI.path.endsWith("/cancel")) "cancelled" else if (exchange.requestMethod == "POST") "queued" else "in_progress"
            val raw = JSONObject().put("id", "resp_fixture").put("status", state).toString().toByteArray()
            exchange.sendResponseHeaders(200,raw.size.toLong()); exchange.responseBody.use { it.write(raw) }
        }
        server.start()
        try {
            val wire = PixelArtistWire(endpoint = "http://127.0.0.1:${server.address.port}/v1/responses")
            val receipt = wire.request("fixture-no-real-key", "POST", "", PixelArtistWire.body("a cloak","pixel",null))
            val id = receipt.getString("id")
            wire.request("fixture-no-real-key", "GET", "/$id")
            wire.request("fixture-no-real-key", "POST", "/$id/cancel", JSONObject())
            assertEquals(listOf("POST /v1/responses", "GET /v1/responses/resp_fixture", "POST /v1/responses/resp_fixture/cancel"),seen.toList())
            val saved = PixelArtJob("pixel","queued",id).toJson()
            assertFalse(saved.toString().contains("fixture-no-real-key"))
            assertEquals(id,PixelArtJob.parse(saved).responseId)
        } finally { server.stop(0) }
    }
    @Test fun rejectedAndRedirectedSubmissionsAreNeverAutomaticallyRetried() = runBlocking {
        val seen = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/v1/responses") { exchange ->
            seen.add(exchange.requestMethod)
            exchange.responseHeaders.add("Location", "/unexpected")
            exchange.sendResponseHeaders(307,-1); exchange.close()
        }
        server.start()
        try {
            val wire = PixelArtistWire(endpoint = "http://127.0.0.1:${server.address.port}/v1/responses")
            val error = runCatching { wire.request("fixture-no-real-key", "POST", "", JSONObject()) }.exceptionOrNull()
            assertTrue(error is ArtistHttpError); assertEquals(307,(error as ArtistHttpError).code)
            assertEquals(listOf("POST"),seen.toList())
            assertFalse(error.message!!.contains("fixture-no-real-key"))
        } finally { server.stop(0) }
    }
}
