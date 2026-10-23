package io.sovware.niobium

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class GatewayException(message: String) : IOException(message)

class GatewayClient(private val listener: Listener, private val webSocketFactory: WebSocket.Factory? = null) {
    interface Listener {
        fun onReady(payload: JSONObject)
        fun onNotification(type: String, sessionId: String, payload: JSONObject)
        fun onServerRequest(id: Any, method: String, params: JSONObject)
        fun onClosed(reason: String)
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    @Volatile private var socket: WebSocket? = null
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    @Volatile private var closedByUs = false

    @Volatile var connected: Boolean = false
        private set

    @Synchronized fun connect(baseUrl: String, token: String, cookie: String? = null) {
        disconnect(silent = true)
        closedByUs = false
        val httpUrl = baseUrl.trimEnd('/').toHttpUrlOrNull() ?: throw GatewayException("That gateway address isn’t valid.")
        val wsUrl = httpUrl.newBuilder()
            .addPathSegments("api/ws")
            .apply { if (token.isNotBlank()) addQueryParameter("token", token) }
            .build()
        val request = Request.Builder().url(wsUrl).apply { cookie?.let { header("Cookie", "niobium_session=" + it) } }.build()
        // A loopback handshake can finish before newWebSocket returns. Publish
        // its owner before callbacks inspect it, including the first ready frame.
        socket = (webSocketFactory ?: http).newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(this@GatewayClient) { if (socket === webSocket) connected = true }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                synchronized(this@GatewayClient) { if (socket === webSocket) handle(text) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                synchronized(this@GatewayClient) {
                if (socket !== webSocket) return
                connected = false
                failPending("Connection closed${if (reason.isNotBlank()) ": $reason" else ""}")
                if (!closedByUs) listener.onClosed(reason.ifBlank { "Gateway closed the connection" })
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                synchronized(this@GatewayClient) {
                if (socket !== webSocket) return
                connected = false
                val detail = when {
                    response?.code == 401 || response?.code == 403 -> "The gateway rejected this access token."
                    response != null -> "Gateway refused the WebSocket (HTTP ${response.code})."
                    else -> t.message ?: "Could not reach the gateway."
                }
                failPending(detail)
                if (!closedByUs) listener.onClosed(detail)
                }
            }
        })
    }

    private fun handle(text: String) {
        val frame = runCatching { JSONObject(text) }.getOrNull() ?: return
        val method = frame.s("method")
        if (frame.has("id") && method != null && method != "event") {
            listener.onServerRequest(frame.get("id"), method, frame.o("params") ?: JSONObject())
            return
        }
        if (frame.has("id") && method == null) {
            val id = frame.optInt("id", -1)
            val deferred = pending.remove(id) ?: return
            val error = frame.o("error")
            if (error != null) {
                deferred.completeExceptionally(GatewayException(error.s("message") ?: "Hermes error"))
            } else {
                val result = frame.opt("result")
                deferred.complete(when (result) {
                    is JSONObject -> result
                    null -> JSONObject()
                    else -> JSONObject().put("value", result)
                })
            }
            return
        }
        if (method == "event") {
            val params = frame.o("params") ?: JSONObject()
            val type = params.s("type") ?: return
            val sid = params.s("session_id") ?: ""
            val payload = params.o("payload") ?: JSONObject()
            if (type == "gateway.ready") {
                listener.onReady(payload)
            } else {
                listener.onNotification(type, sid, payload)
            }
        }
    }

    suspend fun request(method: String, params: JSONObject = JSONObject(), timeoutMs: Long = 60_000): JSONObject {
        val ws = socket ?: throw GatewayException("Not connected to a Hermes gateway.")
        if (!connected) throw GatewayException("The gateway connection is still opening.")
        val id = nextId.getAndIncrement()
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred
        val frame = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", params)
        if (!ws.send(frame.toString())) {
            pending.remove(id)
            throw GatewayException("Could not send that request to the gateway.")
        }
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } finally {
            pending.remove(id)
        }
    }

    fun respond(id: Any, result: JSONObject) {
        val ws = socket ?: return
        ws.send(JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result).toString())
    }

    fun respondError(id: Any, message: String, code: Int = -32601) {
        val ws = socket ?: return
        ws.send(JSONObject().put("jsonrpc", "2.0").put("id", id)
            .put("error", JSONObject().put("code", code).put("message", message)).toString())
    }

    @Synchronized fun disconnect(silent: Boolean = false) {
        closedByUs = silent
        socket?.close(1000, "Niobium closed")
        socket = null
        connected = false
        failPending("Disconnected")
    }

    private fun failPending(reason: String) {
        val all = pending.values.toList()
        pending.clear()
        all.forEach { it.completeExceptionally(GatewayException(reason)) }
    }

    companion object {
        suspend fun probe(baseUrl: String, token: String, cookie: String? = null): JSONObject {
            val url = baseUrl.trimEnd('/').toHttpUrlOrNull()?.newBuilder()?.addPathSegments("api/status")?.build()
                ?: throw GatewayException("That gateway address isn’t valid.")
            val request = Request.Builder().url(url)
                .apply {
                    cookie?.let { header("Cookie", "niobium_session=" + it) }
                    if (token.isNotBlank()) {
                        header("X-Hermes-Session-Token", token)
                        header("Authorization", "Bearer $token")
                    }
                }.build()
            val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) throw GatewayException("That access token was rejected.")
                if (!response.isSuccessful) throw GatewayException("Gateway returned HTTP ${response.code}.")
                return JSONObject(response.body.string())
            }
        }
    }
}
